/*
 * Created on 9 juin 2003
 *
 */
package org.gudy.azureus2.core3.peer.impl.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.peer.PEPeer;

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
public class PEPeerTransportSpeedLimiter {

  //The instance of the speed Limiter
  private static PEPeerTransportSpeedLimiter limiter;

  //The limit in bytes per second
  private int limit;

  //The List of current uploaders
  private List uploaders;

  
  //The last computation time
  private long lastComputationTime;
  
  //Cache expiration in ms
  private static final long CACHE_EXPIRES = 50;
  
  //The limit per peer
  private Map peerToLimit;
  
  static private class UploaderInfo implements Comparable{
    int priorityLevel;
    int maxUpload;
    PEPeer peer;
    
    public UploaderInfo(PEPeer peer) {
      this.peer = peer;
      this.priorityLevel = peer.getDownloadPriority();
      this.maxUpload = peer.getMaxUpload();
    }
    
    public int compareTo(Object otherUploader) {
      if(otherUploader == null)
        return 0;
      if(!(otherUploader instanceof UploaderInfo))
        return 0;
      int otherMaxUpload = ((UploaderInfo)otherUploader).maxUpload;
      int otherPriority = ((UploaderInfo)otherUploader).priorityLevel;
      if(priorityLevel == otherPriority)
        return otherMaxUpload - maxUpload;
      else
        return otherPriority - priorityLevel;
    }
  }

  /**
   * Private constructor for SpeedLimiter
   *
   */
  private PEPeerTransportSpeedLimiter() {
    //limit = ConfigurationManager.getInstance().getIntParameter("Max Upload Speed", 0);
    uploaders = new ArrayList();
    peerToLimit = new HashMap();
    lastComputationTime = 0;
  }

  /**
   * The way to get the singleton
   * @return the SpeedLimiter instance
   */
  public synchronized static PEPeerTransportSpeedLimiter getLimiter() {
    if (limiter == null)
      limiter = new PEPeerTransportSpeedLimiter();
    return limiter;
  }

  /**
   * Uploaders will have to tell the SpeedLimiter that they upload data.
   * In order to achieve this, they call this method that increase the speedLimiter
   * count of uploads.
   *
   */
  public void addUploader(PEPeer wt) {
    synchronized (uploaders) {
      if (!uploaders.contains(wt))
        uploaders.add(wt);
    }
  }

  /**
   * Same as addUploader, but to tell that an upload is ended. 
   */
  public void removeUploader(PEPeer peer) {
    synchronized (uploaders) {
      while (uploaders.contains(peer))
        uploaders.remove(peer);
    }
    synchronized(peerToLimit) {
      peerToLimit.remove(peer);
    }
  }

  /**
   * Method used to know if there is a limitation or not.
   * @return true if speed is limited
   */
  public boolean isLimited(PEPeer wt) {
    this.limit = COConfigurationManager.getIntParameter("Max Upload Speed", 0);
    return (this.limit != 0 && uploaders.contains(wt));
  }

  /**
   * This method returns the amount of data a thread may upload
   * over a period of 100ms.
   * @return number of bytes allowed for 100 ms
   */
  public synchronized int getLimitPer100ms(PEPeer peer) {
    
    long currentTime = System.currentTimeMillis();
    if(currentTime - lastComputationTime > CACHE_EXPIRES) {
      computeAllocation();
      lastComputationTime = currentTime;
    }
    Integer limit = null;
    synchronized(peerToLimit) {
      limit = (Integer) peerToLimit.get(peer);
    }
    if(limit == null)
      return 0;
    return limit.intValue();
  }

  
  private void computeAllocation() {    
    synchronized(peerToLimit) {
      peerToLimit.clear();
      
      if (this.uploaders.size() == 0) {        
        return;
      }
      
      //1. sort out all the uploaders by max speed / priority
      //1.1 construct a list of UploaderInfo
      List sortedList = null;
      int nbUploadersLowPriority = 0;
      int nbUploadersHighPriority = 0;
      synchronized(uploaders) {	      
        sortedList = new ArrayList(uploaders.size());
	      Iterator iter = uploaders.iterator();
	      while(iter.hasNext()) {
	        UploaderInfo ui = new UploaderInfo((PEPeer) iter.next());
	        sortedList.add(ui);
	        if(ui.priorityLevel == DownloadManager.HIGH_PRIORITY) {
	          nbUploadersHighPriority++;
	        } else {
	          nbUploadersLowPriority++;
	        }
	      }	      
      }
      //1.2 Sort it (should be done from slowest to fastest , min to max priority)
      Collections.sort(sortedList);
      
      //2. Allocate bandwith for each levels
      this.limit = COConfigurationManager.getIntParameter("Max Upload Speed",0);
      int toBeAllowedTotal = limit / 10;      
      //2.1 LOW level uploaders get 50% less than high
      int toBeAllowed = (nbUploadersLowPriority * toBeAllowedTotal) / (nbUploadersLowPriority + 2 * nbUploadersHighPriority );
      //left in the total bandwith
      toBeAllowedTotal -= toBeAllowed;
      
      Iterator iter = sortedList.iterator();
      while(iter.hasNext()) {        
        UploaderInfo ui = (UploaderInfo) iter.next();
        if(ui.priorityLevel != DownloadManager.LOW_PRIORITY)
          continue;
        int maxAllocation = toBeAllowed / nbUploadersLowPriority;
        int allocation = min(maxAllocation,ui.maxUpload);
        peerToLimit.put(ui.peer,new Integer(allocation));
        toBeAllowed -= allocation;
        nbUploadersLowPriority--;
      }
      
      //2.2 HIGH level all bandwith available
      //We add back the 70% to what is left from the 30%
      toBeAllowed += toBeAllowedTotal;
      iter = sortedList.iterator();
      while(iter.hasNext()) {
        UploaderInfo ui = (UploaderInfo) iter.next();
        if(ui.priorityLevel != DownloadManager.HIGH_PRIORITY)
          continue;
        int maxAllocation = toBeAllowed / nbUploadersHighPriority;
        int allocation = min(maxAllocation,ui.maxUpload);
        peerToLimit.put(ui.peer,new Integer(allocation));
        toBeAllowed -= allocation;
        nbUploadersHighPriority--;
      }
      
      //2.3 3rd pass in case some bandwith is left
      if(toBeAllowed > 0) {
        int toBeAllowedMorePerPeer = toBeAllowed / peerToLimit.size();
        if(toBeAllowedMorePerPeer > 0) {
	        iter = peerToLimit.keySet().iterator();
	        while(iter.hasNext()) {
	          PEPeer key = (PEPeer) iter.next();
	          Integer oldValue = (Integer) peerToLimit.get(key);
	          peerToLimit.put(key,new Integer(oldValue.intValue() + toBeAllowedMorePerPeer));
	        }
        }
      }      
    }        
  }

 

  static private int min(int a, int b) {
    return a < b ? a : b;
  }
}
