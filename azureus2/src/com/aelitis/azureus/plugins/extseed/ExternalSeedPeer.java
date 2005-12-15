/*
 * Created on 15-Dec-2005
 * Created by Paul Gardner
 * Copyright (C) 2005 Aelitis, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * AELITIS, SAS au capital de 46,603.30 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package com.aelitis.azureus.plugins.extseed;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.gudy.azureus2.plugins.messaging.Message;
import org.gudy.azureus2.plugins.messaging.MessageStreamDecoder;
import org.gudy.azureus2.plugins.messaging.MessageStreamEncoder;
import org.gudy.azureus2.plugins.network.Connection;
import org.gudy.azureus2.plugins.network.ConnectionListener;
import org.gudy.azureus2.plugins.network.IncomingMessageQueue;
import org.gudy.azureus2.plugins.network.IncomingMessageQueueListener;
import org.gudy.azureus2.plugins.network.OutgoingMessageQueue;
import org.gudy.azureus2.plugins.network.OutgoingMessageQueueListener;
import org.gudy.azureus2.plugins.peers.PeerReadRequest;
import org.gudy.azureus2.plugins.peers.Peer;
import org.gudy.azureus2.plugins.peers.PeerListener;
import org.gudy.azureus2.plugins.peers.PeerManager;
import org.gudy.azureus2.plugins.peers.PeerStats;
import org.gudy.azureus2.plugins.torrent.Torrent;
import org.gudy.azureus2.plugins.utils.Monitor;
import org.gudy.azureus2.plugins.utils.PooledByteBuffer;

public class 
ExternalSeedPeer
	implements Peer, ExternalSeedReaderListener
{
	private ExternalSeedPlugin		plugin;
	
	private PeerManager				manager;
	private PeerStats				stats;
	
	private	ExternalSeedReader		reader;			
	
	private byte[]					peer_id;
	private boolean[]				available;
	private boolean					snubbed;
	private boolean					is_optimistic;
	
	private Monitor					connection_mon;
	private boolean					peer_added;
	
	private esConnection			connection = new esConnection();
	
	protected
	ExternalSeedPeer(
		ExternalSeedPlugin		_plugin,
		ExternalSeedReader		_reader )
	{
		plugin	= _plugin;
		reader	= _reader;
				
		connection_mon	= plugin.getPluginInterface().getUtilities().getMonitor();
		
		Torrent	torrent = reader.getTorrent();
				
		available	= new boolean[(int)torrent.getPieceCount()];
		
		Arrays.fill( available, true );
		
		peer_id	= new byte[20];
		
		new Random().nextBytes( peer_id );
		
		peer_id[0]='E';
		peer_id[1]='x';
		peer_id[2]='t';
		peer_id[3]=' ';
		
		_reader.addListener( this );
	}
	
	protected void
	setManager(
		PeerManager	_manager )
	{
		try{
			connection_mon.enter();

			manager	= _manager;
			
			if ( manager != null ){
				
				stats = manager.createPeerStats();
			}
			
			checkConnection();
			
		}finally{
			
			connection_mon.exit();
		}
	}
	
	public PeerManager
	getManager()
	{
		return( manager );
	}
	
	protected void
	checkConnection()
	{
		try{
			connection_mon.enter();
			
			boolean	active = reader.checkConnection( manager );
			
			if ( manager != null && active != peer_added ){
			
				if ( active ){
					
					plugin.log( "Activating peer '" + reader.getName() + "'" );
					
					manager.addPeer( this );
					
				}else{
					
					plugin.log( "Deactivating peer '" + reader.getName() + "'" );

					manager.removePeer( this );
				}
				
				peer_added	= active;
			}
		}finally{
			
		
			connection_mon.exit();
		}
	}
	
	public void
	requestComplete(
		PeerReadRequest		request,
		PooledByteBuffer	data )
	{
		if ( request.isCancelled()){
	
			data.returnToPool();
			
		}else{
			
			manager.getDiskManager().writeBlock( request, data, this );
		}	
	}
	
	public void
	readerFailed()
	{
		try{
			connection_mon.enter();
			
			plugin.log( "Peer failed '" + reader.getName() + "' - " + reader.getStatus());

			if ( peer_added && manager != null ){
				
				manager.removePeer( this );
			
				peer_added	= false;
			}
		}finally{
			
			connection_mon.exit();
		}
	}
	
	public int 
	getState()
	{
		return( Peer.TRANSFERING );
	}

	public byte[] 
	getId()
	{
		return( peer_id );
	}
  
	public String 
	getIp()
	{
		return( reader.getIP());
	}
  
	public int 
	getTCPListenPort()
	{
		return( 0 );
	}
  
	public int 
	getUDPListenPort()
	{
		return( 0 );
	}
  
 
	public int 
	getPort()
	{
		return( reader.getPort());
	}
	
	
	public boolean[] 
	getAvailable()
	{
		return( available );
	}
   
	public boolean
	isTransferAvailable()
	{
		return( reader.isActive());
	}
	
	public boolean 
	isChoked()
	{
		return( false );
	}

	public boolean 
	isChoking()
	{
		return( false );
	}

	public boolean 
	isInterested()
	{
		return( false );
	}

	public boolean 
	isInteresting()
	{
		return( true );
	}

	public boolean 
	isSeed()
	{
		return( true );
	}
 
	public boolean 
	isSnubbed()
	{
		return( snubbed );
	}
 
	public void 
	setSnubbed( 
		boolean _snubbed )
	{
		snubbed	= _snubbed;
	}
	
	public boolean 
	isOptimisticUnchoke()
	{
		return( is_optimistic );
	}
	  
	public void 
	setOptimisticUnchoke( 
		boolean _is_optimistic )
	{
		is_optimistic	= _is_optimistic;
	}

	public PeerStats 
	getStats()
	{
		return( stats );
	}
 	
	public boolean 
	isIncoming()
	{
		return( false );
	}
	
	public int 
	getPercentDone()
	{
		return( 1000 );
	}

	public int 
	getPercentDoneInThousandNotation()
	{
		return( 1000 );
	}
	
	public String 
	getClient()
	{
		return( reader.getName());
	}

	
	public void
	initialize()
	{
		System.out.println( "External seed: initialise" );
	}
	
	public List
	getExpiredRequests()
	{
		return( reader.getExpiredRequests());
		
	}
  		
	public int
	getNumberOfRequests()
	{
		return( reader.getRequestCount());
	}

	public void
	cancelRequest(
		PeerReadRequest	request )
	{
		reader.cancelRequest( request );
	}

	public boolean 
	addRequest(
		PeerReadRequest	request )
	{		
		reader.addRequest( request );
			
		return( true );
	}
	
	public void
	close(
		String 		reason,
		boolean 	closedOnError,
		boolean 	attemptReconnect )
	{
		System.out.println( "External peer closed: " + reason + "/" + closedOnError + "/" + attemptReconnect );
		
		reader.cancelAllRequests();
	}
	

	public void	
	addListener( 
		PeerListener	listener )
	{
	}
	
	public void 
	removeListener(	
		PeerListener listener )
	{	
	}
  
	public Connection 
	getConnection()
	{
		return( connection );
	}
  
  
	public boolean 
	supportsMessaging()
	{
		return( false );
	}
  
	public Message[] 
	getSupportedMessages()
	{
		return( new Message[0] );
	}
	
	public int
	getPercentDoneOfCurrentIncomingRequest()
	{
		return( 0 );
	}
		  
	public int
	getPercentDoneOfCurrentOutgoingRequest()
	{
		return( 0 );
	}
	
	public Map
	getProperties()
	{
		return( new HashMap());
	}
	
	protected class
	esConnection
		implements Connection
	{
		private esOutQ		outq  	= new esOutQ();
		private esInQ		inq  	= new esInQ();
		
		public void 
		connect( 
			ConnectionListener listener )
		{	  
		}
		 
		public void 
		close()
		{	  
		}
		  
		public OutgoingMessageQueue 
		getOutgoingMessageQueue()
		{
			return( outq );
		}
		  
		public IncomingMessageQueue 
		getIncomingMessageQueue()
		{
			return( inq );
		}
		  
		public void 
		startMessageProcessing()
		{	  
		}
	}
	
	protected class
	esOutQ
		implements OutgoingMessageQueue
	{
		public void 
		setEncoder( 
			MessageStreamEncoder encoder )
		{
		}

		public void 
		sendMessage( 
			Message message )
		{	
		}
		  
		public void 
		registerListener( 
			OutgoingMessageQueueListener listener )
		{	
		}
		  
		public void 
		deregisterListener( 
			OutgoingMessageQueueListener listener )
		{
		}
		  
		public void 
		notifyOfExternalSend( Message message )
		{
		}
		  
		public int 
		getPercentDoneOfCurrentMessage()
		{
			return( 0 );
		}
	}
	
	protected class
	esInQ
		implements IncomingMessageQueue
	{
		public void 
		setDecoder( 
			MessageStreamDecoder stream_decoder )
		{
		}
		 	
		public void 
		registerListener( 
			IncomingMessageQueueListener listener )
		{
		}
		  
	
		public void 
		deregisterListener( 
			IncomingMessageQueueListener listener )
		{
		}  
	
		public void 
		notifyOfExternalReceive( 
			Message message )
		{
		}
		  
		public int 
		getPercentDoneOfCurrentMessage()
		{
			return( 0 );
		} 
	}
}
