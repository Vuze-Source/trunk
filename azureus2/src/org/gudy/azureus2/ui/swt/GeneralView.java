/*
 * Created on 2 juil. 2003
 *
 */
package org.gudy.azureus2.ui.swt;

import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.gudy.azureus2.core.ByteFormater;
import org.gudy.azureus2.core.ConfigurationManager;
import org.gudy.azureus2.core.DownloadManager;
import org.gudy.azureus2.core.PeerStats;

/**
 * @author Olivier
 * 
 */
public class GeneralView implements IView {

  private Display display;
  private DownloadManager manager;
  boolean pieces[];
  int overall;
  int loopFactor;

  Composite genComposite;
  Group gFile;
  Label piecesInfo;
  Canvas piecesImage;
  Image pImage;
  Label piecesPercent;
  Label fileInfo;
  Canvas fileImage;
  Image fImage;
  Label filePercent;
  Group gAvailability;
  Label availabilityInfo;
  Canvas availabilityImage;
  Image aImage;
  Label availabilityPercent;
  Group gTransfer;
  Label timeElapsed;
  Label timeRemaining;
  Label download;
  Label downloadSpeed;
  Label upload;
  Label uploadSpeed;
  Combo maxUploads;
  Label totalSpeed;
  int maxUploadsValue;
  Combo maxSpeed;
  Label seeds;
  Label peers;
  Group gInfo;
  Label fileName;
  Label fileSize;
  Label saveIn;
  Label hash;
  Label tracker;
  Label trackerUpdateIn;
  Label pieceNumber;
  Label pieceSize;

  Color colorGrey;
  Color[] blues;

  public GeneralView(DownloadManager manager) {
    this.manager = manager;
    pieces = new boolean[manager.getNbPieces()];
  }
  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IView#initialize(org.eclipse.swt.widgets.Composite)
   */
  public void initialize(Composite composite) {
    this.display = composite.getDisplay();
    colorGrey = new Color(display, new RGB(170, 170, 170));
    blues = new Color[5];
    blues[4] = new Color(display, new RGB(0, 128, 255));
    blues[3] = new Color(display, new RGB(64, 160, 255));
    blues[2] = new Color(display, new RGB(128, 192, 255));
    blues[1] = new Color(display, new RGB(192, 224, 255));
    blues[0] = new Color(display, new RGB(255, 255, 255));

    genComposite = new Composite(composite, SWT.NULL);
    GridLayout genLayout = new GridLayout();
    genLayout.numColumns = 1;
    genComposite.setLayout(genLayout);

    gFile = new Group(genComposite, SWT.SHADOW_OUT);
    GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
    gFile.setLayoutData(gridData);
    gFile.setText("Downloaded");
    GridLayout fileLayout = new GridLayout();
    fileLayout.numColumns = 3;
    gFile.setLayout(fileLayout);

    fileInfo = new Label(gFile, SWT.LEFT);
    fileInfo.setText("File Status");
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
    fileInfo.setLayoutData(gridData);

    fileImage = new Canvas(gFile, SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.widthHint = 150;
    gridData.heightHint = 30;
    fileImage.setLayoutData(gridData);

    filePercent = new Label(gFile, SWT.RIGHT);
    filePercent.setText("\t");
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_END);
    filePercent.setLayoutData(gridData);

    piecesInfo = new Label(gFile, SWT.LEFT);
    piecesInfo.setText("Pieces Status");
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
    piecesInfo.setLayoutData(gridData);

    piecesImage = new Canvas(gFile, SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.widthHint = 150;
    gridData.heightHint = 30;
    piecesImage.setLayoutData(gridData);

    piecesPercent = new Label(gFile, SWT.RIGHT);
    piecesPercent.setText("\t");
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_END);
    piecesPercent.setLayoutData(gridData);

    gAvailability = new Group(genComposite, SWT.SHADOW_OUT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gAvailability.setLayoutData(gridData);

    GridLayout availabilityLayout = new GridLayout();
    availabilityLayout.numColumns = 3;
    gAvailability.setText("Availability");
    gAvailability.setLayout(availabilityLayout);

    availabilityInfo = new Label(gAvailability, SWT.LEFT);
    availabilityInfo.setText("Pieces Status");
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
    availabilityInfo.setLayoutData(gridData);

    availabilityImage = new Canvas(gAvailability, SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.widthHint = 150;
    gridData.heightHint = 30;
    availabilityImage.setLayoutData(gridData);

    availabilityPercent = new Label(gAvailability, SWT.RIGHT);
    availabilityPercent.setText("\t");
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_END);
    availabilityPercent.setLayoutData(gridData);

    gTransfer = new Group(genComposite, SWT.SHADOW_OUT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gTransfer.setLayoutData(gridData);

    gTransfer.setText("Transfer");
    GridLayout layoutTransfer = new GridLayout();
    layoutTransfer.numColumns = 6;
    gTransfer.setLayout(layoutTransfer);

    new Label(gTransfer, SWT.LEFT).setText("Time Elapsed : ");
    timeElapsed = new Label(gTransfer, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    timeElapsed.setLayoutData(gridData);
    new Label(gTransfer, SWT.LEFT).setText("   Remaining: ");
    timeRemaining = new Label(gTransfer, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    timeRemaining.setLayoutData(gridData);
    new Label(gTransfer, SWT.LEFT);
    new Label(gTransfer, SWT.LEFT);

    new Label(gTransfer, SWT.LEFT).setText("Downloaded : ");
    download = new Label(gTransfer, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    download.setLayoutData(gridData);
    new Label(gTransfer, SWT.LEFT).setText("   Download Speed: ");
    downloadSpeed = new Label(gTransfer, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    downloadSpeed.setLayoutData(gridData);
    new Label(gTransfer, SWT.LEFT).setText("   Max Uploads : ");
    maxUploads = new Combo(gTransfer, SWT.SINGLE | SWT.READ_ONLY);
    for (int i = 2; i < 101; i++)
      maxUploads.add(" " + i);
    maxUploads.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event e) {
        manager.setMaxUploads(2 + maxUploads.getSelectionIndex());
      }
    });
    maxUploads.select(
      ConfigurationManager.getInstance().getIntParameter("Max Uploads", 4) - 2);

    new Label(gTransfer, SWT.LEFT).setText("Uploaded : ");
    upload = new Label(gTransfer, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    upload.setLayoutData(gridData);
    new Label(gTransfer, SWT.LEFT).setText("   Upload Speed : ");
    uploadSpeed = new Label(gTransfer, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    uploadSpeed.setLayoutData(gridData);
    new Label(gTransfer, SWT.LEFT).setText("");
    new Label(gTransfer, SWT.LEFT).setText("");

    new Label(gTransfer, SWT.LEFT).setText("Seeds : ");
    seeds = new Label(gTransfer, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    seeds.setLayoutData(gridData);

    new Label(gTransfer, SWT.LEFT).setText("   Peers : ");
    peers = new Label(gTransfer, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    peers.setLayoutData(gridData);

    new Label(gTransfer, SWT.LEFT).setText("   Total Speed : ");
    totalSpeed = new Label(gTransfer, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    totalSpeed.setLayoutData(gridData);

    gInfo = new Group(genComposite, SWT.SHADOW_OUT);
    gInfo.setText("Info");
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gInfo.setLayoutData(gridData);

    GridLayout layoutInfo = new GridLayout();
    layoutInfo.numColumns = 4;
    gInfo.setLayout(layoutInfo);

    new Label(gInfo, SWT.LEFT).setText("File Name : ");
    fileName = new Label(gInfo, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    fileName.setLayoutData(gridData);

    new Label(gInfo, SWT.LEFT).setText("   Total Size : ");
    fileSize = new Label(gInfo, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    fileSize.setLayoutData(gridData);

    new Label(gInfo, SWT.LEFT).setText("Save In : ");
    saveIn = new Label(gInfo, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    saveIn.setLayoutData(gridData);

    new Label(gInfo, SWT.LEFT).setText("   Hash : ");
    hash = new Label(gInfo, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    hash.setLayoutData(gridData);

    new Label(gInfo, SWT.LEFT).setText("# of Pieces : ");
    pieceNumber = new Label(gInfo, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    pieceNumber.setLayoutData(gridData);

    new Label(gInfo, SWT.LEFT).setText("   Size : ");
    pieceSize = new Label(gInfo, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    pieceSize.setLayoutData(gridData);

    new Label(gInfo, SWT.LEFT).setText("Tracker : ");
    tracker = new Label(gInfo, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    tracker.setLayoutData(gridData);

    new Label(gInfo, SWT.LEFT).setText("   Update in : ");
    trackerUpdateIn = new Label(gInfo, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    trackerUpdateIn.setLayoutData(gridData);
    genComposite.addListener(SWT.Resize, new Listener() {
      public void handleEvent(Event e) {
        overall = -1;
        Arrays.fill(pieces, false);
      }
    });
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IView#getComposite()
   */
  public Composite getComposite() {
    return genComposite;
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IView#refresh()
   */
  public void refresh() {
    loopFactor++;
    updateAvailability();
    updatePiecesInfo();
    updateOverall();
    setTime(manager.getElapsed(), manager.getETA());
    setStats(
      manager.getDownloaded(),
      manager.getUploaded(),
      manager.getDownloadSpeed(),
      manager.getUploadSpeed(),
      manager.getTotalSpeed(),
      manager.getNbSeeds(),
      manager.getNbPeers());
      setTracker(manager.getTrackerStatus(),manager.getTrackerTime());
    setInfos(manager.getName(),PeerStats.format(manager.getSize()),manager.getSavePath(),ByteFormater.nicePrint(manager.getHash()),manager.getNbPieces(),manager.getPieceLength());
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IView#delete()
   */
  public void delete() {
    if(colorGrey != null && !colorGrey.isDisposed())
      colorGrey.dispose();
    if(blues != null)
    {
      for(int i = 0 ; i < blues.length ; i++) {
        if(blues[i] != null && ! blues[i].isDisposed())
          blues[i].dispose();    
      }
    }
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IView#getShortTitle()
   */
  public String getShortTitle() {
    return "General";
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IView#getFullTitle()
   */
  public String getFullTitle() {
    return "General";
  }

  public synchronized void updateAvailability() {
    if (manager.peerManager == null)
      return;
    final int[] available = manager.peerManager.getAvailability();
    if (display.isDisposed())
      return;

    if (availabilityImage.isDisposed())
      return;
    Rectangle bounds = availabilityImage.getClientArea();
    int width = bounds.width - 5;
    int x0 = bounds.x + 1;
    int y0 = bounds.y + 1;
    int height = bounds.height - 2;
    GC gc = new GC(availabilityImage);
    if (aImage != null && !aImage.isDisposed())
      aImage.dispose();

    aImage = new Image(display, width, height);
    GC gcImage = new GC(aImage);
    int allMin = 0;
    int total = 0;
    String sTotal = "000";
    if (available != null) {
      allMin = available[0];
      int nbPieces = available.length;
      for (int i = 0; i < nbPieces; i++) {
        if (available[i] < allMin)
          allMin = available[i];
      }
      for (int i = 0; i < nbPieces; i++) {
        if (available[i] > allMin)
          total++;
      }
      total = (total * 1000) / nbPieces;
      sTotal = "" + total;
      if (total < 10)
        sTotal = "0" + sTotal;
      if (total < 100)
        sTotal = "0" + sTotal;

      for (int i = 0; i < width; i++) {
        int a0 = (i * nbPieces) / width;
        int a1 = ((i + 1) * nbPieces) / width;
        if (a1 == a0)
          a1++;
        if (a1 > nbPieces)
          a1 = nbPieces;
        int max = 0;
        int min = available[a0];
        int Pi = 1000;
        for (int j = a0; j < a1; j++) {
          if (available[j] > max)
            max = available[j];
          if (available[j] < min)
            min = available[j];
          Pi *= available[j];
          Pi /= (available[j] + 1);
        }
        int pond = Pi;
        if (max == 0)
          pond = 0;
        else {
          int PiM = 1000;
          for (int j = a0; j < a1; j++) {
            PiM *= (max + 1);
            PiM /= max;
          }
          pond *= PiM;
          pond /= 1000;
          pond *= (max - min);
          pond /= 1000;
          pond += min;
        }
        int index = 0;
        if (pond > 0)
          index = 1;
        if (pond > 2)
          index = 2;
        if (pond > 5)
          index = 3;
        if (pond > 10)
          index = 4;
        gcImage.setBackground(blues[index]);
        Rectangle rect = new Rectangle(i, 1, 1, height);
        gcImage.fillRectangle(rect);
      }
    }
    gcImage.dispose();
    if (!availabilityPercent.isDisposed())
      availabilityPercent.setText(allMin + "." + sTotal);
    gc.setForeground(colorGrey);
    gc.drawImage(aImage, x0, y0);
    gc.drawRectangle(new Rectangle(x0, y0, width, height));
    gc.dispose();
  }

  public synchronized void updatePiecesInfo() {

    final boolean[] available = pieces;
    if (display.isDisposed())
      return;

    if (piecesImage.isDisposed())
      return;
    Rectangle bounds = piecesImage.getClientArea();
    int width = bounds.width - 5;
    int x0 = bounds.x + 1;
    int y0 = bounds.y + 1;
    int height = bounds.height - 2;
    GC gc = new GC(piecesImage);
    boolean valid = true;
    boolean newPieces[] = manager.getPiecesStatus();
    if(newPieces == null) return;
    for (int i = 0; i < pieces.length; i++) {
      if (pieces[i] != newPieces[i]) {
        valid = false;
        pieces[i] = newPieces[i];
      }
    }
    if (!valid) {
      if (pImage != null && !pImage.isDisposed())
        pImage.dispose();
      pImage = new Image(display, width, height);
      GC gcImage = new GC(pImage);
      if (available != null) {
        int nbPieces = available.length;
        int total = 0;
        for (int i = 0; i < nbPieces; i++) {
          if (available[i])
            total++;
        }
        for (int i = 0; i < width; i++) {
          int a0 = (i * nbPieces) / width;
          int a1 = ((i + 1) * nbPieces) / width;
          if (a1 == a0)
            a1++;
          if (a1 > nbPieces)
            a1 = nbPieces;
          int nbAvailable = 0;
          for (int j = a0; j < a1; j++) {
            if (available[j]) {
              nbAvailable++;
            }
            int index = (nbAvailable * 4) / (a1 - a0);
            gcImage.setBackground(blues[index]);
            Rectangle rect = new Rectangle(i, 1, 1, height);
            gcImage.fillRectangle(rect);
          }
        }
        gcImage.dispose();
        total = (total * 1000) / nbPieces;
        if (!piecesPercent.isDisposed())
          piecesPercent.setText((total / 10) + "." + (total % 10) + " %");
      }
    }
    if (pImage == null)
      return;
    gc.setForeground(colorGrey);
    gc.drawImage(pImage, x0, y0);
    gc.drawRectangle(new Rectangle(x0, y0, width, height));
    gc.dispose();
  }

  public synchronized void updateOverall() {
    if (display.isDisposed())
      return;
    final int total = manager.getCompleted();
    String percent = (total / 10) + "." + (total % 10) + " %";

    if (fileImage.isDisposed())
      return;
    GC gc = new GC(fileImage);
    Rectangle bounds = fileImage.getClientArea();
    int width = bounds.width - 5;
    int x0 = bounds.x + 1;
    int y0 = bounds.y + 1;
    int height = bounds.height - 2;
    if (overall != total) {
      if (fImage != null && !fImage.isDisposed())
        fImage.dispose();
      fImage = new Image(display, width, height);
      GC gcImage = new GC(fImage);
      int limit = (width * total) / 1000;
      gcImage.setBackground(blues[4]);
      Rectangle rect = new Rectangle(1, 1, limit, height);
      gcImage.fillRectangle(rect);
      gcImage.setBackground(blues[0]);
      rect = new Rectangle(limit, 1, width, height);
      gcImage.fillRectangle(rect);
      gcImage.dispose();
      if (!filePercent.isDisposed())
        filePercent.setText((total / 10) + "." + (total % 10) + " %");
    }
    overall = total;
    if (fImage == null)
      return;
    gc.setForeground(colorGrey);
    gc.drawImage(fImage, x0, y0);
    gc.drawRectangle(new Rectangle(x0, y0, width, height));
    gc.dispose();
  }
  public void setTime(String elapsed, String remaining) {
    if(timeElapsed.isDisposed())
      return;
    timeElapsed.setText(elapsed);
    if(timeRemaining.isDisposed())
      return;
    timeRemaining.setText(remaining);
  }

  public void setStats(
    String _dl,
    String _ul,
    String _dls,
    String _uls,
    String _ts,
    int _s,
    int _p) {
    if (display.isDisposed())
      return;

    final String dls = _dls;
    final String uls = _uls;
    final String dl = _dl;
    final String ul = _ul;
    final String ts = _ts;
    final int s = _s;
    final int p = _p;
    if (download.isDisposed())
      return;
    download.setText(dl);
    if (downloadSpeed.isDisposed())
      return;
    downloadSpeed.setText(dls);
    if (upload.isDisposed())
      return;
    upload.setText(ul);
    if (uploadSpeed.isDisposed())
      return;
    uploadSpeed.setText(uls);
    if (totalSpeed.isDisposed())
      return;
    totalSpeed.setText(ts);
    if (seeds.isDisposed())
      return;
    seeds.setText("" + s);
    if (peers.isDisposed())
      return;
    peers.setText("" + p);
    if (gTransfer.isDisposed())
      return;
  }

  public void setTracker(String _status, int _time) {

    if (display.isDisposed())
      return;
    final String status = _status;
    final int time = _time;
    if (tracker.isDisposed())
      return;
    tracker.setText(status);
    if (trackerUpdateIn.isDisposed())
      return;
    int minutes = time / 60;
    int seconds = time % 60;
    String strSeconds = "" + seconds;
    if (seconds < 10)
      strSeconds = "0" + seconds;
    trackerUpdateIn.setText(minutes + ":" + strSeconds);
  }

  public void setInfos(
    String ifileName,
    String ifileSize,
    String ipath,
    String ihash,
    int ipieceNumber,
    String ipieceLength) {
    if (display.isDisposed())
      return;
    final String _fileName = ifileName;
    final String _fileSize = ifileSize;
    final String _path = ipath;
    final String _hash = ihash;
    final int _pieceNumber = ipieceNumber;
    final String _pieceLength = ipieceLength;
    display.asyncExec(new Runnable() {
      public void run() {
        if (fileName.isDisposed())
          return;
        fileName.setText(_fileName);
        if (fileSize.isDisposed())
          return;
        fileSize.setText(_fileSize);
        if (saveIn.isDisposed())
          return;
        saveIn.setText(_path);
        if (hash.isDisposed())
          return;
        hash.setText(_hash);
        if (pieceNumber.isDisposed())
          return;
        pieceNumber.setText("" + _pieceNumber);
        if (pieceSize.isDisposed())
          return;
        pieceSize.setText(_pieceLength);
        if (gInfo.isDisposed())
          return;
      }
    });
  }

}
