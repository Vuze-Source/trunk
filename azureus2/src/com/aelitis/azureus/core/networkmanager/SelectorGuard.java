/*
 * Created on Jul 28, 2004
 * Created by Alon Rohter
 * Copyright (C) 2004 Aelitis, All Rights Reserved.
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
 * AELITIS, SARL au capital de 30,000 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */
package com.aelitis.azureus.core.networkmanager;


import java.util.*;
import java.nio.channels.*;

import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.logging.LGLogger;
import org.gudy.azureus2.core3.util.*;


/**
 * Temp class designed to help detect Selector anomalies and cleanly re-open if necessary.
 * 
 * NOTE:
 * As of JVM 1.4.2_03, after network connection disconnect/reconnect, usually-blocking
 * select() and select(long) calls no longer block, and will instead return immediately.
 * This can cause selector spinning and 100% cpu usage.
 * See:
 *   http://forum.java.sun.com/thread.jsp?forum=4&thread=293213
 *   http://developer.java.sun.com/developer/bugParade/bugs/4850373.html
 *   http://developer.java.sun.com/developer/bugParade/bugs/4881228.html
 * Fixed in JVM 1.4.2_05+ and 1.5b2+
 */
public class SelectorGuard {
  
  private final int countThreshold;
  private boolean marked = false;
  private int consecutiveZeroSelects = 0;
  private long beforeSelectTime;
  private long afterSelectTime;
  
  private static final boolean DISABLED = false;//System.getProperty("java.version").startsWith("1.5") ? true : false;
  
  private HashMap conseq_keys = new HashMap();
  private static final int CONSEQ_SELECT_THRESHOLD = 25;
  
  
  
  
  /**
   * Create a new SelectorGuard with the given failed count threshold.
   */
  public SelectorGuard( int _count_threshold ) {
    this.countThreshold = _count_threshold;    
  }
  
  
  /**
   * Run this method right before the select() operation to
   * mark the start time.
   */
  public void markPreSelectTime() {
    beforeSelectTime = SystemTime.getCurrentTime();
    marked = true;
  }
  
  
  /**
   * Checks whether selector is still OK, and not spinning.
   */
  public boolean isSelectorOK(final int _num_keys_ready, final long _time_threshold ) {
    if (_num_keys_ready > 0) {
      //non-zero select, so OK
      consecutiveZeroSelects = 0;
      return true;
    }
    
    if (marked) marked = false;
    else Debug.out("Error: You must run markPreSelectTime() before calling isSelectorOK");
    
    afterSelectTime = SystemTime.getCurrentTime();
    long elapsedTime = afterSelectTime - beforeSelectTime;
    
    if (elapsedTime > _time_threshold) {
      //zero-select, but over the time threshold, so OK
      consecutiveZeroSelects = 0;
      return true;
    }
    
    //if we've gotten here, then we have a potential selector anomalie
    consecutiveZeroSelects++;
    
    if (consecutiveZeroSelects > countThreshold) {
      //we're over the threshold: reset stats and report error
      consecutiveZeroSelects = 0;
      
      if( DISABLED ) {  //this bug should not happen when running under 1.5 JRE
        LGLogger.log( "WARNING: It looks like the socket selector is spinning, even though you are running JRE 1.5 series." );
        return true;
      }
      
      return false;
    }
    
    //not yet over the count threshold
    return true;
  }
  

  
  /**
   * Cleanup bad selector and return a fresh new one.
   */
  public Selector repairSelector( Selector _bad_selector ) {
    String msg = "Likely network disconnect/reconnect: Repairing 1 selector, " +_bad_selector.keys().size()+ " keys. [" +System.getProperty("java.version")+"]\n";
    msg += MessageText.getString( "SelectorGuard.repairmessage" );
    Debug.out( msg );
    LGLogger.logUnrepeatableAlert( LGLogger.AT_WARNING, msg );
    
    try {
      //sleep a bit to allow underlying network recovery
      Thread.sleep(5000);
        
    	//open new
    	Selector newSelector = Selector.open();
      
    	//register old selector's keyset with new selector
      for (Iterator i = _bad_selector.keys().iterator(); i.hasNext();) {
        SelectionKey key = (SelectionKey)i.next();
        i.remove();
        SelectableChannel channel = key.channel();
        channel.register(newSelector, key.interestOps(), key.attachment());
      }
        
    	//close old
    	_bad_selector.close();
        
      Thread.sleep(2000);
        
    	//return new
    	return newSelector;
        
    } catch (Exception e) { Debug.out(e.getMessage()); }
      
    Debug.out("Unable to repair bad selector; returning original as still-bad");
    return _bad_selector;
  }
  
  
  
  /**
   * Detect if any selection keys seem to be select-spinning.
   * @param selected_keys for the latest select op
   * @return true if spinning has been detected, false if ok
   */
  public boolean detectSpinningKeys( Set selected_keys ) {
    HashMap new_keys = new HashMap();
    boolean spin_detected = false;
    
    for( Iterator i = selected_keys.iterator(); i.hasNext(); ) {
      Object key = i.next();
      
      Integer count = (Integer)conseq_keys.get( key );
      
      if( count == null ) {
        new_keys.put( key, new Integer(1) );
      }
      else {
        int conseq_selects = count.intValue() + 1;
        
        new_keys.put( key, new Integer( conseq_selects ) );
        
        if( conseq_selects >= CONSEQ_SELECT_THRESHOLD && conseq_selects % 25 == 0 ) {
          spin_detected = true;
        }
      }
    }
    
    conseq_keys = new_keys;    
    
    return spin_detected;
  }
  
  
  
  /**
   * Get a report of any detected spinning keys.
   * @return spin report
   */
  public String getSpinningKeyReport() {
    String report = "Channels with more than " +CONSEQ_SELECT_THRESHOLD+ " consecutive selects: ";
    
    for( Iterator i = conseq_keys.entrySet().iterator(); i.hasNext(); ) {
      Map.Entry entry = (Map.Entry)i.next();
      
      Integer count = (Integer)entry.getValue();
      
      if( count.intValue() >= CONSEQ_SELECT_THRESHOLD ) {
        SelectionKey key = (SelectionKey)entry.getKey();
        
        report += "[" +count.intValue()+ "X, " +key.channel()+ "] ";
      }
    }
    
    return report;
  }
  
}
