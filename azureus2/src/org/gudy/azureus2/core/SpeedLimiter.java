/*
 * Created on 9 juin 2003
 *
 */
package org.gudy.azureus2.core;

import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.Vector;

import org.gudy.azureus2.core2.PeerSocket;

/**
 * 
 * This class defines a singleton used to do speed limitation.
 * 
 * Speed Limitation is at the moment quite simple,
 * but really efficient in the way it won't use more bandwith than allowed.
 * On the other hand, it may not use all the allowed bandwith.
 * 
 * @author Olivier
 *
 */
public class SpeedLimiter {

  //The instance of the speed Limiter
  private static SpeedLimiter limiter;

  //The limit in bytes per second
  private int limit;

  //The List of current uploaders
  private List uploaders;

  /**
   * Private constructor for SpeedLimiter
   *
   */
  private SpeedLimiter() {
    //limit = ConfigurationManager.getInstance().getIntParameter("Max Upload Speed", 0);
    uploaders = new Vector();
  }

  /**
   * The way to get the singleton
   * @return the SpeedLimiter instance
   */
  public synchronized static SpeedLimiter getLimiter() {
    if (limiter == null)
      limiter = new SpeedLimiter();
    return limiter;
  }

  /**
   * Public method use to change the limit
   * @param limit the upload limit in bytes per second
   */
  public void setLimit(int limit) {
    this.limit = limit;
  }

  /**
   * Uploaders will have to tell the SpeedLimiter that they upload data.
   * In order to achieve this, they call this method that increase the speedLimiter
   * count of uploads.
   *
   */
  public void addUploader(PeerSocket wt) {
    synchronized (uploaders) {
      uploaders.add(wt);
    }
  }

  /**
   * Same as incUploads, but to tell that an upload is ended. 
   */
  public void removeUploader(PeerSocket wt) {
    synchronized (uploaders) {
      uploaders.remove(wt);
    }
  }

  /**
   * Method used to know is there is a limitation or not.
   * @return true if speed is limited
   */
  public boolean isLimited(PeerSocket wt) {
    limit = ConfigurationManager.getInstance().getIntParameter("Max Upload Speed", 0);
    return (this.limit != 0 && uploaders.contains(wt));
  }

  /**
   * This method returns the amount of data a thread may upload
   * over a period of 100ms.
   * @return number of bytes allowed for 100 ms
   */
  public int getLimitPer100ms(PeerSocket wt) {

    if (this.uploaders.size() == 0)
      return 0;

    //We construct a TreeMap to sort all writeThread according to their up speed.
    TreeMap sortedUploadersHighPriority = new TreeMap();
    TreeMap sortedUploadersLowPriority = new TreeMap();
    synchronized (uploaders) {
      for (int i = 0; i < uploaders.size(); i++) {
        TreeMap sortedUploaders;
        PeerSocket wti = (PeerSocket) uploaders.get(i);
        if (wti.getDownloadPriority() == DownloadManager.HIGH_PRIORITY) {
          sortedUploaders = sortedUploadersHighPriority;
        }
        else {
          sortedUploaders = sortedUploadersLowPriority;
        }
        int maxUpload = wti.getMaxUpload();
        while (sortedUploaders.containsKey(new Integer(maxUpload)))
          maxUpload++;
        sortedUploaders.put(new Integer(maxUpload), wti);
      }
    }

    int toBeAllocated = this.limit / 10;
    int peersToBeAllocated = sortedUploadersHighPriority.size();
    Iterator iter = sortedUploadersHighPriority.keySet().iterator();
    boolean found = false;
    while (iter.hasNext()) {
      Integer key = (Integer) iter.next();
      PeerSocket wti = (PeerSocket) sortedUploadersHighPriority.get(key);
      if (wti == wt) {
        found = true;
        break;
      }
      int allowed = toBeAllocated / peersToBeAllocated;
      int maxUpload = wti.getMaxUpload();
      toBeAllocated -= allowed > maxUpload ? maxUpload : allowed;
      peersToBeAllocated--;
    }
    if (!found) {
      peersToBeAllocated = sortedUploadersLowPriority.size();
      iter = sortedUploadersLowPriority.keySet().iterator();
      while (iter.hasNext()) {
        Integer key = (Integer) iter.next();
        PeerSocket wti = (PeerSocket) sortedUploadersLowPriority.get(key);
        if (wti == wt) {
          break;
        }
        int allowed = toBeAllocated / peersToBeAllocated;
        int maxUpload = wti.getMaxUpload();
        toBeAllocated -= allowed > maxUpload ? maxUpload : allowed;
        peersToBeAllocated--;
      }
    }

    int allowed = toBeAllocated / peersToBeAllocated;
    int maxUpload = wt.getMaxUpload();
    int result = allowed > maxUpload ? maxUpload : allowed;
    //Logger.getLogger().log(0,0,Logger.ERROR,"Allocated for 100ms :" + result);
    return result;
  }

}
