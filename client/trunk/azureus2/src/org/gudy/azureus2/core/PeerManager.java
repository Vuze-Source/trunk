package org.gudy.azureus2.core;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.gudy.azureus2.core2.DataQueueItem;
import org.gudy.azureus2.core2.PeerSocket;

public class PeerManager extends Thread {
  private static final int BLOCK_SIZE = 32768;
  private static final int MAX_REQUESTS = 5;
  private static final boolean DEBUG = false;

  private int[] _availability;
  private boolean _bContinue;
  private List _clients; //:: Try to define by the interface, 
  //makes the code more portable by forcing you to interfaces -Tyler  							
  private List _connections;
  private DiskManager _diskManager;
  private boolean[] _downloaded;
  private boolean[] _downloading;
  private boolean _finished;
  private byte[] _hash;
  private int _loopFactor;
  private byte[] _myPeerId;
  private int _nbPieces;
  private Piece[] _pieces;
  private Server _server;
  private PeerStats _stats;
  private long _timeLastUpdate;
  private int _timeToWait;
  private TrackerConnection _tracker;
  private String _trackerStatus;
  private int _maxUploads;
  private int _trackerState;
  private final int TRACKER_START = 1;
  private final int TRACKER_UPDATE = 2;
  private int _seeds, _peers;
  private long _timeStarted;
  private Average _averageReceptionSpeed;
  private PeerSocket currentOptimisticUnchoke;

  private DownloadManager _manager;

  private PeerUpdater peerUpdater;

  public PeerManager(
    DownloadManager manager,
    byte[] hash,
    Server server,
    TrackerConnection tracker,
    DiskManager diskManager) {
    super("Peer Manager");
    this._manager = manager;
    //This torrent Hash
    _hash = hash;

    //The connection to the tracker
    _tracker = tracker;
    _tracker.setManager(this);
    _myPeerId = _tracker.getPeerId();

    //The peer connections
    _connections = new Vector();

    //The Server that handle incoming connections
    _server = server;
    _server.setManager(this);

    this._diskManager = diskManager;
    _diskManager.setManager(this);

    //BtManager is threaded, this variable represents the
    // current loop iteration. It's used by some components only called
    // at some specific times.
    _loopFactor = 0;

    //The current tracker state
    //this could be start or update
    _trackerState = TRACKER_START;
    _trackerStatus = "...";
    _timeToWait = 120;

    _averageReceptionSpeed = Average.getInstance(1000, 30);

    setDiskManager(diskManager);

    peerUpdater = new PeerUpdater();
    peerUpdater.start();

  }

  private class PeerUpdater extends Thread {
    private boolean bContinue = true;
    public PeerUpdater() {
      super("Peer Updater");
    }

    public void run() {
      while (bContinue) {
        long started = System.currentTimeMillis();
        synchronized (_connections) {
          for (int i = 0; i < _connections.size(); i++) {
            PeerSocket ps = (PeerSocket) _connections.get(i);
            if (ps.getState() == PeerSocket.DISCONNECTED) {
              _connections.remove(ps);
              i--;
            }
            else {
              try {
                ps.process();
              }
              catch (Exception e) {
                ps.closeAll();
              }
            }
          }
        }
        try {
          long wait = 10 - (System.currentTimeMillis() - started);
          if (wait < 5)
            wait = 5;
          Thread.sleep(wait);
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
    }

    public void stopIt() {
      bContinue = false;
    }
  }

  //main method
  public void run() {
    _bContinue = true;
    _manager.setState(DownloadManager.STATE_DOWNLOADING);
    _timeStarted = System.currentTimeMillis() / 1000;
    while (_bContinue) //loop until stopAll() kills us
      {
      try {
        long timeStart = System.currentTimeMillis();
        checkTracker(); //check the tracker status, update peers
        checkCompletedPieces();
        //check to see if we've completed anything else
        computeAvailability(); //compute the availablity                   
        updateStats();
        if (!_finished) //if we're not finished
          {
          //::moved check finished to the front -Tyler
          checkFinished(); //see if we've finished
          checkRequests(); //check the requests           		
          checkDLPossible(); //look for downloadable pieces          
        }
        checkSeeds();
        unChoke();
        //prefetchReadOperation();
        //sendReceive(); //Send - Receive data on sockets
        _loopFactor++; //increment the loopFactor
        long timeWait = 100 - (System.currentTimeMillis() - timeStart);
        if (timeWait < 10)
          timeWait = 10;
        Thread.sleep(timeWait); //sleep

      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public void stopAll() {

    //1. Stop the peer updater
    peerUpdater.stopIt();

    //2. Stop itself
    _bContinue = false;

    //3. Stop the server
    _server.stopServer();

    //3.Dump resume Data
    if (_diskManager.getState() == DiskManager.READY)
      _diskManager.dumpResumeDataToDisk();

    //4. Close all clients
    while (_connections.size() > 0) {
      PeerSocket pc = (PeerSocket) _connections.remove(0);
      pc.closeAll();
    }

    //1. Send disconnect to Tracker
    Thread t = new Thread() {
      public void run() {
        _tracker.stop();
      }
    };
    t.start();
  }

  /**
   * A private method that does analysis of bencoded stream sent by the tracker.
   * It will mainly open new connections with peers provided
   * and set the timeToWait variable according to the tracker response.
   * In fact we use 2/3 of what tracker is asking us to wait, in order not to be considered as timed-out by it.
   * @param data a String with bencoded data in it
   */
  private void analyseTrackerResponse(String data) {
    //was any data returned?
    if (data == null) //no data returned
      {
      _trackerStatus = "offline"; //set the status to offline      
      _timeToWait = 60; //retry in 60 seconds
      return; //break
    }
    else //otherwise
      {
      try {
        //parse the metadata
        Map metaData = BDecoder.decode(data.getBytes("ISO-8859-1"));

        //OLD WAY
        //BtDictionary metaData = new BtDecode(data).getDictionary();

        try {
          //set the timeout			
          _timeToWait = (2 * ((Long) metaData.get("interval")).intValue()) / 3;
        }
        catch (Exception e) {
          _trackerStatus = new String((byte[]) metaData.get("failure reason"), "ISO-8859-1");
          _timeToWait = 120;
          return;
        }

        //OLD WAY
        //_timeToWait = ((Integer)metaData.getValue("interval")).intValue();

        if (DEBUG) //print debug info
          {
          System.out.println(_timeToWait);
        }
        //build the list of peers
        List peers = (List) metaData.get("peers");

        //OLD WAY
        //BtList peers = (BtList) metaData.getValue("peers");

        //count the number of peers
        int nbPeers = peers.size();

        //for every peer
        for (int i = 0; i < nbPeers; i++) {
          Map peer = (Map) peers.get(i);
          //build a dictionary object				
          String peerId = new String((byte[]) peer.get("peer id"), "ISO-8859-1");
          //get the peer id
          String ip = new String((byte[]) peer.get("ip"), "ISO-8859-1");
          //get the peer ip address
          int port = ((Long) peer.get("port")).intValue();
          //get the peer port number

          //Test peer IDs to remove the local IP address
          byte[] piBytes = null;
          try { //:: Any reason for this try/catch? -Tyler
            //:: If not we can merge this section and the one below
            //:: Into a single compact statement
            piBytes = peerId.getBytes("ISO-8859-1");
          }
          catch (Exception e) {
            e.printStackTrace();
          }

          if (!Arrays.equals(piBytes, _myPeerId))
            //::this should be quicker -Tyler
            {
            addPeer(piBytes, ip, port);
          }
          //for(int j = 0 ; j < piBytes.length ; j++)
          //{				
          //same = same && (piBytes[j] == _myPeerId[j]);
          //System.out.print(same + " ");
          //}		                   
        }
        _trackerState = TRACKER_UPDATE;
        _trackerStatus = "ok"; //set the status		  
        return; //break						
      }
      catch (Exception e) {
        //TODO:: WE SHOULD CLEAN THIS UP
        //tracker not working		
        System.out.println("Problems with Tracker, will retry in 1 minute");
        _trackerStatus = "offline";
        _timeToWait = 60;
      }
    }
  }

  /**
   * A private method that checks if pieces being downloaded are finished
   * If all blocks from a piece are written to disk, then this process will check the piece
   * and if it passes sha-1 check, it will be marked as downloaded.
   * otherwise, it will unmark it as being downloaded, so blocks can be retreived again.
   */
  private void checkCompletedPieces() {
    //for every piece
    for (int i = 0; i < _nbPieces; i++) {
      Piece currentPiece = _pieces[i]; //get the piece

      //if piece is loaded, and completed
      if (currentPiece != null && currentPiece.isComplete() && !currentPiece.isBeingChecked()) {
        //check the piece from the disk
        _diskManager.aSyncCheckPiece(i);
        currentPiece.setBeingChecked();
      }
    }
  }

  /**
   * This method scans all peers and if downloading from them is possible,
   * it will try to find blocks to download.
   * If the peer is marked as snubbed then only one block will be downloaded
   * otherwise it will queue up 5 requests.
   * 
   */
  private void checkDLPossible() {
    //::updated this method to work with List -Tyler
    //for all peers
    int[] upRates = new int[_connections.size()];
    Arrays.fill(upRates, -1);
    Vector bestUploaders = new Vector();
    for (int i = 0; i < _connections.size(); i++) {
      PeerSocket pc = null;
      try {
        pc = (PeerSocket) _connections.get(i);
      }
      catch (Exception e) {
        break;
      }
      if (pc.transfertAvailable()) {
        int upRate = pc.getStats().getReception();
        testAndSortBest(upRate, upRates, pc, bestUploaders, 0);
      }
    }

    for (int i = 0; i < bestUploaders.size(); i++) {
      //get a connection 
      PeerSocket pc = (PeerSocket) bestUploaders.get(i);
      //can we transfer something?
      if (pc.transfertAvailable()) {
        boolean found = true;
        //If queue is too low, we will enqueue another request
        int maxRequests = MAX_REQUESTS;
        if (pc.isSnubbed())
          maxRequests = 1;
        while ((pc.getState() == PeerSocket.TRANSFERING) && found && (pc.getNbRequests() < maxRequests)) {
          found = findPieceToDownload(pc, pc.isSnubbed());
          //is there anything else to download?
        }
      }
    }
  }

  private void removeDisconnected() {
    for (int i = 0; i < _connections.size(); i++) {
      PeerSocket ps = (PeerSocket) _connections.get(i);
      if (ps.getState() == PeerSocket.DISCONNECTED) {
        _connections.remove(ps);
        i--;
      }
    }
  }

  private void sendReceive() {
    for (int i = 0; i < _connections.size(); i++) {
      PeerSocket ps = (PeerSocket) _connections.get(i);
      if (ps.getState() != PeerSocket.DISCONNECTED) {
        ps.process();
      }
    }
  }

  /**
   * This method checks if the downloading process is finished.
   *
   */
  private void checkFinished() {
    boolean temp = true;
    //for every piece
    for (int i = 0; i < _nbPieces; i++) {
      //:: we should be able to do this better than keeping a bunch of arrays
      //:: possibly adding a status field to the piece object? -Tyler 
      temp = temp && _downloaded[i];

      //::pre-emptive break should save some cycles -Tyler
      if (!temp) {
        break;
      }
    }

    //set finished
    _finished = temp;
    if (_finished) {
      _manager.setState(DownloadManager.STATE_SEEDING);
      _diskManager.changeToReadOnly();
      _diskManager.dumpResumeDataToDisk();
      for (int i = 0; i < _connections.size(); i++) {
        PeerSocket pc = (PeerSocket) _connections.get(i);
        if (pc != null) {
          pc.setSnubbed(false);
        }
      }
      if (DEBUG) {
        System.out.println("!!! FINISHED !!!");
      }
    }
  }

  /**
   * This method will locate any expired request, will cancel it, and mark the peer as snubbed
   *
   */
  private void checkRequests() {
    //for every connection
    for (int i = 0; i < _connections.size(); i++) {
      PeerSocket pc = (PeerSocket) _connections.get(i);
      if (pc.getState() == PeerSocket.TRANSFERING) {
        List expired = pc.getExpiredRequests();
        //::May want to make this an ArrayList unless you
        //::need the synchronization a vector offers -Tyler
        if (expired.size() > 0) {
          pc.setSnubbed(true);
        }
        //for every expired request
        for (int j = 0; j < expired.size(); j++) {
          Request request = (Request) expired.get(j);
          //get the request object
          pc.sendCancel(request); //cancel the request object
          int pieceNumber = request.getPieceNumber();
          //get the piece number
          int pieceOffset = request.getOffset();
          //get the piece offset
          Piece piece = _pieces[pieceNumber]; //get the piece
          if (piece != null)
            piece.unmarkBlock(pieceOffset / BLOCK_SIZE);
          //unmark the block
          _downloading[pieceNumber] = false;
          //set piece to not being downloaded
        }
      }
    }
  }

  /**
   * This method will check the tracker. It creates a new thread so requesting the url won't freeze the program.
   *
   */
  private void checkTracker() {
    //get the time
    long time = System.currentTimeMillis() / 1000;
    //has the timeout expired?
    if ((time - _timeLastUpdate) > _timeToWait) //if so...
      {
      _trackerStatus = "checking...";
      _timeLastUpdate = time; //update last checked time
      Thread t = new Thread("Tracker Checker") {
        public void run() {
          if (_trackerState == TRACKER_UPDATE)
            analyseTrackerResponse(_tracker.update());
          //get the tracker response
          if (_trackerState == TRACKER_START)
            analyseTrackerResponse(_tracker.start());
        }
      };
      t.start(); //start the thread
    }
  }

  /**
   * This methd will compute the overall availability (inluding yourself)
   *
   */
  private void computeAvailability() {
    //reset the availability
    Arrays.fill(_availability, 0); //:: should be faster -Tyler

    //for all clients
    for (int i = 0; i < _connections.size(); i++) //::Possible optimization to break early when you reach 100%
      {
      //get the peer connection
      PeerSocket pc = (PeerSocket) _connections.get(i);

      //get an array of available pieces		
      boolean[] piecesAvailable = pc.getAvailable();
      if (piecesAvailable != null) //if the list is not null
        {
        for (int j = 0; j < _nbPieces; j++) //loop for every piece
          {
          if (piecesAvailable[j]) //set the piece to available
            _availability[j]++;
        }
      }
    }

    //Then adds our own availability.
    for (int i = 0; i < _downloaded.length; i++) {
      if (_downloaded[i])
        _availability[i]++;
    }
  }

  /**
   * This method is the core download manager.
   * It will decide for a given peer, which block it should download from it.
   * Here is the overall algorithm :
   * 1. It will determine a list of rarest pieces.
   * 2. If one is already being requested but not completely, it will continue it
   * 3. If not, it will start a new piece dl based on a randomly choosen piece from least available ones.	 
   * 3. If it can't find a piece then, this means that all pieces are already downloaded/fully requested, and it returns false.
   * 
   * @param pc the PeerConnection we're working on
   * @param snubbed if the peer is snubbed, so requested block won't be mark as requested.
   * @return true if a request was assigned, false otherwise
   */
  private boolean findPieceToDownload(PeerSocket pc, boolean snubbed) {
    //get the rarest pieces list
    boolean[] piecesRarest = getRarestPieces(pc);
    if (piecesRarest == null)
      return false;

    int nbPiecesRarest = 0;
    for (int i = 0; i < piecesRarest.length; i++) {
      if (piecesRarest[i])
        nbPiecesRarest++;
    }

    //If there is no piece to download, return.
    if (nbPiecesRarest == 0)
      return false;

    //Piece number and block number that we should dl
    int pieceNumber = -1;
    int blockNumber = -1;

    //Last completed level (this is for undo purposes)   
    int lastCompleted = -1;

    //For every piece
    for (int i = 0; i < _nbPieces; i++) {
      //If we're not downloading the piece and if it's available from that peer   
      if ((_pieces[i] != null) && !_downloading[i] && piecesRarest[i]) {
        //We get and mark the next block number to dl
        //We will either get -1 if no more blocks need to be requested
        //Or a number >= 0 otherwise
        int tempBlock = _pieces[i].getAndMarkBlock();

        //SO, if there is a block to request in that piece
        if (tempBlock != -1) {
          //Is it a better piece to dl from?
          //A better piece is a piece which is more completed
          //ie more blocks have already been WRITTEN on disk (not requested)
          if (_pieces[i].getCompleted() > lastCompleted) {
            //If we had marked a block previously, we must unmark it
            if (pieceNumber != -1) {
              //So pieceNumber contains the last piece
              //We unmark the last block marked        
              _pieces[pieceNumber].unmarkBlock(blockNumber);
            }
            //Now we change the different variables
            //The new pieceNumber being used is pieceNumber
            pieceNumber = i;
            //The new last block number is block number
            blockNumber = tempBlock;
            //The new completed level
            lastCompleted = _pieces[i].getCompleted();
          }
          else {
            //This piece is not intersting, but we have marked it as
            //being downloaded, we have to unmark it.
            _pieces[i].unmarkBlock(tempBlock);
          }
        }
        else {
          //So ..... we have a piece not marked as being downloaded ...
          //but without any free block to request ...
          //let's correct this situation :p
          _downloading[i] = true;
          piecesRarest[i] = false;
          nbPiecesRarest--;
        }
      }
    }

    //Ok, so we may have found a valid (piece;block) to request    
    if (pieceNumber != -1 && blockNumber != -1) {
      //If the peer is snubbed, we unmark the block as being requested
      if (snubbed)
        _pieces[pieceNumber].unmarkBlock(blockNumber);

      //We really send the request to the peer
      pc.request(pieceNumber, blockNumber * BLOCK_SIZE, _pieces[pieceNumber].getBlockSize(blockNumber));

      //and return true as we have found a block to request
      return true;
    }

    if (nbPiecesRarest == 0)
      return false;

    //If we get to this point we haven't found a block from a piece being downloaded
    //So we'll find a new one		       

    //Otherwhise, vPieces is not null, we'll 'randomly' choose an element from it.    
    int nPiece = (int) (Math.random() * nbPiecesRarest);
    pieceNumber = -1;
    for (int i = 0; i < _nbPieces; i++) {
      if (piecesRarest[i]) {
        if (nPiece == 0) {
          pieceNumber = i;
          break;
        }
        else
          nPiece--;
      }
    }

    if (pieceNumber == -1)
      return false;
    //Now we should have a piece with least presence on network    
    Piece piece = null;

    //We need to know if it's last piece or not when creating the BtPiece Object
    if (pieceNumber < _nbPieces - 1)
      piece = new Piece(this, _diskManager.getPieceLength(), pieceNumber);
    else
      piece = new Piece(this, _diskManager.getLastPieceLength(), pieceNumber);

    pieceAdded(piece);
    //Assign the created piece to the pieces array.
    _pieces[pieceNumber] = piece;

    //We send request ...
    blockNumber = piece.getAndMarkBlock();
    if (snubbed)
      _pieces[pieceNumber].unmarkBlock(blockNumber);

    pc.request(pieceNumber, blockNumber * BLOCK_SIZE, piece.getBlockSize(blockNumber));
    return true;
  }

  private boolean[] getRarestPieces(PeerSocket pc) {
    boolean[] piecesAvailable = pc.getAvailable();
    boolean[] piecesRarest = new boolean[_nbPieces];
    Arrays.fill(piecesRarest, false);

    //This will represent the minimum piece availability level.
    int pieceMinAvailability = -1;

    //1. Scan all pieces to determine min availability
    for (int i = 0; i < _nbPieces; i++) {
      if (!_downloaded[i] && !_downloading[i] && piecesAvailable[i]) {
        if (pieceMinAvailability == -1 || _availability[i] < pieceMinAvailability) {
          pieceMinAvailability = _availability[i];
        }
      }
    }

    //We add a 90 % range
    pieceMinAvailability = (190 * pieceMinAvailability) / 100;
    //For all pieces
    for (int i = 0; i < _nbPieces; i++) {
      //If we're not downloading it, if it's not downloaded, and if it's available from that peer
      if (!_downloaded[i] && !_downloading[i] && piecesAvailable[i]) {
        //null : special case, to ensure we find at least one piece
        //or if the availability level is lower than the old availablility level
        if (_availability[i] <= pieceMinAvailability) {
          piecesRarest[i] = true;
        }
      }
    }
    return piecesRarest;
  }

  /**
  	* private method to add a peerConnection
  	* created by Tyler
  	* @param pc
  	*/
  private synchronized void insertPeerSocket(PeerSocket pc) {
    //Get the max number of connections allowed
    int maxConnections = ConfigurationManager.getInstance().getIntParameter("Max Clients", 0);

    synchronized (_connections) {
      //does our list already contain this PeerConnection?  
      if (!_connections.contains(pc)) //if not
        {
        if (maxConnections == 0 || _connections.size() < maxConnections) {
          _connections.add(pc); //add the connection
        }
      }
      else //our list already contains this connection
        {
        pc = null; //do nothing ...
      }
    }
  }

  private void unChoke() {
    //1. We retreive the current non-choking peers
    Vector nonChoking = new Vector();
    for (int i = 0; i < _connections.size(); i++) {
      PeerSocket pc = null;
      try {
        pc = (PeerSocket) _connections.get(i);
      }
      catch (Exception e) {
        continue;
      }
      if (!pc.isChoking()) {
        nonChoking.add(pc);
      }
    }

    //2. Determine how many uploads we should consider    
    int nbUnchoke = _manager.getMaxUploads();

    //System.out.println(nbUnchoke);

    //3. Then, in any case if we have too many unchoked pple we need to choke some
    while (nbUnchoke < nonChoking.size()) {
      PeerSocket pc = (PeerSocket) nonChoking.remove(0);
      pc.sendChoke();
    }

    //4. If we lack unchoke pple, find new ones ;)
    if (nbUnchoke > nonChoking.size()) {
      //4.1 Determine the N (nbUnchoke best peers)
      //Maybe we'll need some other test when we are a seed ...
      int[] upRates = new int[nbUnchoke - nonChoking.size()];
      Arrays.fill(upRates, 0);

      Vector bestUploaders = new Vector();
      for (int i = 0; i < _connections.size(); i++) {
        PeerSocket pc = null;
        try {
          pc = (PeerSocket) _connections.get(i);
        }
        catch (Exception e) {
          break;
        }
        if (pc.isInteresting() && pc.isChoking()) {
          int upRate = pc.getStats().getReception();
          testAndSortBest(upRate, upRates, pc, bestUploaders, 0);
        }
      }

      for (int i = 0; i < bestUploaders.size(); i++) {
        PeerSocket pc = (PeerSocket) bestUploaders.get(i);
        pc.sendUnChoke();
      }
    }

    //3. Only Choke-Unchoke Every 10 secs
    if ((_loopFactor % 500) != 0)
      return;

    //3. if non unchoke possibilities we should cancel all chokes
    //   and return;              
    /*if (nbUnchoke < 1) {
      for (int i = 0; i < nonChoking.size(); i++) {
        PeerSocket pc = (PeerSocket) nonChoking.get(i);
        pc.sendChoke();
      }
      return;
    }*/

    //4. Determine the N (nbUnchoke best peers)
    //   Maybe we'll need some other test when we are a seed ...
    int[] upRates = new int[nbUnchoke - 1];
    Arrays.fill(upRates, 0);

    Vector bestUploaders = new Vector();
    for (int i = 0; i < _connections.size(); i++) {
      PeerSocket pc = null;
      try {
        pc = (PeerSocket) _connections.get(i);
      }
      catch (Exception e) {
        continue;
      }
      if (pc != currentOptimisticUnchoke && pc.isInteresting()) {
        int upRate = 0;
        if (_finished) {
          upRate = pc.getStats().getStatisticSentRaw();
          if (pc.isSnubbed())
            upRate = -1;
        }
        else
          upRate = pc.getStats().getReception();
        if (upRate > 256)
          testAndSortBest(upRate, upRates, pc, bestUploaders, 0);
      }
    }

    if (!_finished && bestUploaders.size() < upRates.length) {
      for (int i = 0; i < _connections.size(); i++) {
        PeerSocket pc = null;
        try {
          pc = (PeerSocket) _connections.get(i);
        }
        catch (Exception e) {
          break;
        }
        if (pc != currentOptimisticUnchoke
          && pc.isInteresting()
          && pc.isInterested()
          && bestUploaders.size() < upRates.length
          && !pc.isSnubbed()
          && (pc.getStats().getTotalSentRaw() / (pc.getStats().getTotalReceivedRaw() + 16000)) < 10) {
          bestUploaders.add(pc);
        }
      }
    }

    if (bestUploaders.size() < upRates.length) {
      int start = bestUploaders.size();
      for (int i = 0; i < _connections.size(); i++) {
        PeerSocket pc = null;
        try {
          pc = (PeerSocket) _connections.get(i);
        }
        catch (Exception e) {
          continue;
        }
        if (pc != currentOptimisticUnchoke && pc.isInteresting()) {
          int upRate = 0;
          //If peer we'll use the overall uploaded value
          if (!_finished)
            upRate = (int) pc.getStats().getTotalReceivedRaw();
          else {
            upRate = pc.getPercentDone();
            if (pc.isSnubbed())
              upRate = -1;
          }
          testAndSortBest(upRate, upRates, pc, bestUploaders, start);
        }
      }

    }

    //  optimistic unchoke
    if ((_loopFactor % 1500) == 0 || (currentOptimisticUnchoke == null)) {

      int index = 0;
      synchronized (_connections) {
        for (int i = 0; i < _connections.size(); i++) {
          PeerSocket pc = (PeerSocket) _connections.get(i);
          if (pc == currentOptimisticUnchoke)
            index = i + 1;
        }
        if (index >= _connections.size())
          index = 0;

        currentOptimisticUnchoke = null;

        for (int i = index; i < _connections.size() + index; i++) {
          PeerSocket pc = (PeerSocket) _connections.get(i % _connections.size());
          if (!pc.isSeed() && !bestUploaders.contains(pc) && pc.isInteresting() && !pc.isSnubbed()) {
            currentOptimisticUnchoke = pc;
            break;
          }
        }
      }
    }
    if (currentOptimisticUnchoke != null)
      bestUploaders.add(currentOptimisticUnchoke);

    for (int i = 0; i < bestUploaders.size(); i++) {
      PeerSocket pc = (PeerSocket) bestUploaders.get(i);
      if (nonChoking.contains(pc)) {
        nonChoking.remove(pc);
      }
      else {
        pc.sendUnChoke();
      }
    }

    for (int i = 0; i < nonChoking.size(); i++) {
      PeerSocket pc = (PeerSocket) nonChoking.get(i);
      pc.sendChoke();
    }
  }

  private void testAndSortBest(int upRate, int[] upRates, PeerSocket pc, Vector best, int start) {
    int i;
    for (i = start; i < upRates.length; i++) {
      if (upRate >= upRates[i])
        break;
    }
    if (i < upRates.length) {
      best.add(i, pc);
      for (int j = upRates.length - 2; j == i; j--) {
        upRates[j + 1] = upRates[j];
      }
      upRates[i] = upRate;
    }
    if (best.size() > upRates.length)
      best.remove(upRates.length);
  }

  private void testAndSortWeakest(int upRate, int[] upRates, PeerSocket pc, Vector worst) {
    int i;
    for (i = 0; i < upRates.length; i++) {
      if (upRate <= upRates[i])
        break;
    }
    if (i < upRates.length) {
      worst.add(i, pc);
      for (int j = i; j < upRates.length - 1; j++) {
        upRates[j + 1] = upRates[j];
      }
      upRates[i] = upRate;
    }
    if (worst.size() > upRates.length)
      worst.remove(upRates.length);
  }

  //send the have requests out
  private void sendHave(int pieceNumber) {
    //for all clients
    for (int i = 0; i < _connections.size(); i++) {
      //get a peer connection
      PeerSocket pc = (PeerSocket) _connections.get(i);
      //send the have message
      pc.sendHave(pieceNumber);
    }
  }

  //Methods that checks if we are connected to another seed, and if so, disconnect from him.
  private void checkSeeds() {
    //If we are not ourself a seed, return
    if (!_finished || !ConfigurationManager.getInstance().getBooleanParameter("Disconnect Seed",false))
      return;
    for (int i = 0; i < _connections.size(); i++) {
      PeerSocket pc = (PeerSocket) _connections.get(i);
      if (pc != null && pc.getState() == PeerSocket.TRANSFERING && pc.isSeed()) {
        pc.closeAll();
      }
    }
  }

  private void updateStats() {
    _seeds = _peers = 0;
    //calculate seeds vs peers
    for (int i = 0; i < _connections.size(); i++) {
      PeerSocket pc = (PeerSocket) _connections.get(i);
      if (pc.getState() == PeerSocket.TRANSFERING)
        if (pc.isSeed())
          _seeds++;
        else
          _peers++;
    }
  }
  /**
   * The way to unmark a request as being downloaded.
   * Called by Peer connections objects when connection is closed or choked
   * @param request
   */
  public void requestCanceled(Request request) {
    int pieceNumber = request.getPieceNumber(); //get the piece number
    int pieceOffset = request.getOffset(); //get the piece offset    
    Piece piece = _pieces[pieceNumber]; //get the piece
    if (piece != null)
      piece.unmarkBlock(pieceOffset / BLOCK_SIZE);
    //set as not being retrieved
    _downloading[pieceNumber] = false; //mark as not downloading
  }

  /**
   * This method is used by BtServer to add an incoming connection
   * to the list of peer connections.
   * @param sckClient the incoming connection socket
   */
  public void addPeer(SocketChannel sckClient) {
    //create a peer connection and insert it to the list    
    this.insertPeerSocket(new PeerSocket(this, sckClient));
  }

  /**
   * This method is used to add a peer with its id, ip and port
   * @param peerId
   * @param ip
   * @param port
   */
  public void addPeer(byte[] peerId, String ip, int port) {
    //create a peer connection and insert it to the list    
    this.insertPeerSocket(new PeerSocket(this, peerId, ip, port, false));
  }

  /**
   * The way to remove a peer from our peer list.
   * @param pc
   */
  public void removePeer(PeerSocket pc) {
    _connections.remove(pc);
  }

  //get the hash value
  public byte[] getHash() {
    return _hash;
  }

  //get the peer id value
  public byte[] getPeerId() {
    return _myPeerId;
  }

  //get the piece length
  public int getPieceLength() {
    return _diskManager.getPieceLength();
  }

  //get the number of pieces
  public int getPiecesNumber() {
    return _nbPieces;
  }

  //get the status array
  public boolean[] getPiecesStatus() {
    return _downloaded;
  }

  //get the remaining percentage
  public long getRemaining() {
    return _diskManager.getRemaining();
  }

  //:: possibly rename to setRecieved()? -Tyler
  //set recieved bytes
  public void received(int length) {
    if (length > 0) {
      _stats.received(length);
      _averageReceptionSpeed.addValue(length);
    }
    _manager.received(length);
  }

  //::possibly update to setSent() -Tyler
  //set the send value
  public void sent(int length) {
    if (length > 0)
      _stats.sent(length);
    _manager.sent(length);   
  }

  //setup the diskManager
  public void setDiskManager(DiskManager diskManager) {
    //the diskManager that handles read/write operations
    _diskManager = diskManager;
    _downloaded = _diskManager.getPiecesStatus();
    _nbPieces = _diskManager.getPiecesNumber();

    //the bitfield indicating if pieces are currently downloading or not
    _downloading = new boolean[_nbPieces];
    //for(int i = 0 ; i < _nbPieces ; i++) _downloading[i] = false;
    Arrays.fill(_downloading, false);

    //the pieces
    _pieces = new Piece[_nbPieces];

    //the availability level of each piece in the network
    _availability = new int[_nbPieces];

    //the stats
    _stats = new PeerStats(diskManager.getPieceLength());

    _server.start();

    this.start();
  }

  public ByteBuffer getBlock(int pieceNumber, int offset, int length) {
    return _diskManager.readBlock(pieceNumber, offset, length);
  }

  public long downloaded() {
    return _stats.getTotalReceivedRaw();
  }

  public long uploaded() {
    return _stats.getTotalSentRaw();
  }

  public void haveNewPiece() {
    _stats.haveNewPiece();
  }

  public void blockWritten(int pieceNumber, int offset) {
    Piece piece = _pieces[pieceNumber];
    if (piece != null) {
      piece.setWritten(offset / BLOCK_SIZE);
    }
  }

  public void writeBlock(int pieceNumber, int offset, ByteBuffer data) {
    Piece piece = _pieces[pieceNumber];
    int blockNumber = offset / BLOCK_SIZE;
    if (piece != null && !piece.isWritten(blockNumber)) {
      piece.setBloc(blockNumber);
      _diskManager.writeBlock(pieceNumber, offset, data);
    }
  }

  public boolean checkBlock(int pieceNumber, int offset, int length) {
    return _diskManager.checkBlock(pieceNumber, offset, length);
  }

  public boolean checkBlock(int pieceNumber, int offset, ByteBuffer data) {
    return _diskManager.checkBlock(pieceNumber, offset, data);
  }

  public int getAvailability(int pieceNumber) {
    return _availability[pieceNumber];
  }

  public int[] getAvailability() {
    return _availability;
  }

  public void havePiece(int pieceNumber, PeerSocket pcOrigin) {
    int length = getPieceLength(pieceNumber);
    int availability = _availability[pieceNumber];
    if (availability < 4) {
      if (_downloaded[pieceNumber])
        availability--;
      if (availability <= 0)
        return;
      //for all peers
      for (int i = 0; i < _connections.size(); i++) {
        PeerSocket pc = (PeerSocket) _connections.get(i);
        if (pc != null && pc != pcOrigin && pc.getState() == PeerSocket.TRANSFERING) {
          boolean[] peerAvailable = pc.getAvailable();
          if (peerAvailable[pieceNumber])
            pc.getStats().staticticSent(length / availability);
        }
      }
    }
  }

  public int getPieceLength(int pieceNumber) {
    if (pieceNumber == _nbPieces - 1)
      return _diskManager.getLastPieceLength();
    return _diskManager.getPieceLength();
  }

  public synchronized boolean validateHandshaking(PeerSocket pc, byte[] peerId) {
    PeerSocket pcTest = new PeerSocket(this, peerId, pc.getIp(), pc.getPort(), true);
    return !_connections.contains(pcTest);
  }

  public int getNbPeers() {
    return _peers;
  }

  public int getNbSeeds() {
    return _seeds;
  }

  public PeerStats getStats() {
    return _stats;
  }

  public String getTrackerStatus() {
    return _trackerStatus;
  }

  public String getETA() {
    String remaining;
    int writtenNotChecked = 0;
    for (int i = 0; i < _pieces.length; i++) {
      if (_pieces[i] != null) {
        writtenNotChecked += _pieces[i].getCompleted() * BLOCK_SIZE;
      }
    }
    long dataRemaining = _diskManager.getRemaining() - writtenNotChecked;
    int averageSpeed = _averageReceptionSpeed.getAverage();
    if (averageSpeed < 256) {
      remaining = "oo";

    }
    else {

      long timeRemaining = dataRemaining / averageSpeed;
      remaining = TimeFormater.format(timeRemaining);
    }
    if (dataRemaining == 0)
      remaining = "Finished";
    return remaining;
  }

  public void peerAdded(PeerSocket pc) {
    _manager.objectAdded(pc);
  }

  public void peerRemoved(PeerSocket pc) {
    _manager.objectRemoved(pc);
  }

  public void pieceAdded(Piece p) {
    _manager.objectAdded(p);
  }

  public void pieceRemoved(Piece p) {
    _manager.objectRemoved(p);
  }

  public String getElpased() {
    return TimeFormater.format(System.currentTimeMillis() / 1000 - _timeStarted);
  }

  public String getDownloaded() {
    return _stats.getTotalReceived();
  }

  public String getUploaded() {
    return _stats.getTotalSent();
  }

  public String getDownloadSpeed() {
    return _stats.getReceptionSpeed();
  }

  public String getUploadSpeed() {
    return _stats.getSendingSpeed();
  }

  public String getTotalSpeed() {
    return _stats.getOverAllDownloadSpeed();
  }

  public int getTrackerTime() {
    return _timeToWait - (int) (System.currentTimeMillis() / 1000 - _timeLastUpdate);
  }

  public void pieceChecked(int pieceNumber, boolean result) {
    this.pieceRemoved(_pieces[pieceNumber]);
    //  the piece has been written correctly
    if (result) {

      _pieces[pieceNumber].free();
      _pieces[pieceNumber] = null;

      //mark this piece as downloaded
      _downloaded[pieceNumber] = true;

      //send all clients an have message
      sendHave(pieceNumber);

    }
    else {

      _pieces[pieceNumber].free();
      _pieces[pieceNumber] = null;

      //Mark this piece as non downloading
      _downloading[pieceNumber] = false;
    }
  }

  public void enqueueReadRequest(DataQueueItem item) {
    _diskManager.enqueueReadRequest(item);
  }

  /**
   * @return
   */
  public List get_connections() {
    return _connections;
  }

  public Piece[] getPieces() {
    return _pieces;
  }
  
  public int getDownloadPriority() {
    return _manager.getPriority();
  }

}