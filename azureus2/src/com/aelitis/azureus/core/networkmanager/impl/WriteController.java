/*
 * Created on Sep 27, 2004
 * Created by Alon Rohter
 * Copyright (C) 2004, 2005, 2006 Aelitis, All Rights Reserved.
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

package com.aelitis.azureus.core.networkmanager.impl;

import java.util.*;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.util.*;

import com.aelitis.azureus.core.networkmanager.EventWaiter;
import com.aelitis.azureus.core.stats.AzureusCoreStats;
import com.aelitis.azureus.core.stats.AzureusCoreStatsProvider;


/**
 * Processes writes of write-entities and handles the write selector.
 */
public class WriteController implements AzureusCoreStatsProvider{
  
	private static int IDLE_SLEEP_TIME  = 50;
	   
	static{
		COConfigurationManager.addAndFireParameterListener(
			"network.control.write.idle.time",
			new ParameterListener()
			{
				public void 
				parameterChanged(
					String name )
				{
					IDLE_SLEEP_TIME 	= COConfigurationManager.getIntParameter( name );
				}
			});
	}
	
  private volatile ArrayList normal_priority_entities = new ArrayList();  //copied-on-write
  private volatile ArrayList high_priority_entities = new ArrayList();  //copied-on-write
  private final AEMonitor entities_mon = new AEMonitor( "WriteController:EM" );
  private int next_normal_position = 0;
  private int next_high_position = 0;
  
   private long	wait_count;
  
  private EventWaiter 	write_waiter = new EventWaiter();
  
  /**
   * Create a new write controller.
   */
  public WriteController() {
    
    //start write handler processing
    Thread write_processor_thread = new AEThread( "WriteController:WriteProcessor" ) {
      public void runSupport() {
        writeProcessorLoop();
      }
    };
    write_processor_thread.setDaemon( true );
    write_processor_thread.setPriority( Thread.MAX_PRIORITY - 1 );
    write_processor_thread.start();
    
    Set	types = new HashSet();
    
    types.add( AzureusCoreStats.ST_NET_WRITE_CONTROL_WAIT_COUNT );
    types.add( AzureusCoreStats.ST_NET_WRITE_CONTROL_ENTITY_COUNT );
    types.add( AzureusCoreStats.ST_NET_WRITE_CONTROL_CON_COUNT );
    types.add( AzureusCoreStats.ST_NET_WRITE_CONTROL_READY_CON_COUNT );
    types.add( AzureusCoreStats.ST_NET_WRITE_CONTROL_READY_BYTE_COUNT );
       
    AzureusCoreStats.registerProvider(
    	types,
    	this );
  }
  
  public void
  updateStats(
		  Set		types,
		  Map		values )
  {
	  if ( types.contains( AzureusCoreStats.ST_NET_WRITE_CONTROL_WAIT_COUNT )){

		  values.put( AzureusCoreStats.ST_NET_WRITE_CONTROL_WAIT_COUNT, new Long( wait_count ));
	  }
	  
	  if ( types.contains( AzureusCoreStats.ST_NET_WRITE_CONTROL_ENTITY_COUNT )){

		  values.put( AzureusCoreStats.ST_NET_WRITE_CONTROL_ENTITY_COUNT, new Long( high_priority_entities.size() + normal_priority_entities.size()));
	  }
	  
	  if ( 	types.contains( AzureusCoreStats.ST_NET_WRITE_CONTROL_CON_COUNT ) ||
			types.contains( AzureusCoreStats.ST_NET_WRITE_CONTROL_READY_CON_COUNT ) ||
			types.contains( AzureusCoreStats.ST_NET_WRITE_CONTROL_READY_BYTE_COUNT )){
			   
		  ArrayList ref = normal_priority_entities;
		    
		  long	ready_bytes			= 0;
		  int	ready_connections	= 0;
		  int	connections			= 0;
		  
		  for (int i=0;i<ref.size();i++){
			  
		      RateControlledEntity entity = (RateControlledEntity)ref.get( i );
		      
		      connections 		+= entity.getConnectionCount();
		      
		      ready_connections += entity.getReadyConnectionCount( write_waiter );
		      
		      ready_bytes		+= entity.getBytesReadyToWrite();
		  }
		  
		  values.put( AzureusCoreStats.ST_NET_WRITE_CONTROL_CON_COUNT, new Long( connections ));
		  values.put( AzureusCoreStats.ST_NET_WRITE_CONTROL_READY_CON_COUNT, new Long( ready_connections ));
		  values.put( AzureusCoreStats.ST_NET_WRITE_CONTROL_READY_BYTE_COUNT, new Long( ready_bytes ));
	  }
  }
  
  private void writeProcessorLoop() {
    boolean check_high_first = true;
    
    while( true ) {
      try {
        if( check_high_first ) {
          check_high_first = false;
          if( !doHighPriorityWrite() ) {
            if( !doNormalPriorityWrite() ) {
              if ( write_waiter.waitForEvent( IDLE_SLEEP_TIME )){
            	  wait_count++;
              }
            }
          }
        }
        else {
          check_high_first = true;
          if( !doNormalPriorityWrite() ) {
            if( !doHighPriorityWrite() ) {
            	if ( write_waiter.waitForEvent( IDLE_SLEEP_TIME )){
            		wait_count++;
            	}
            }
          }
        }
      }
      catch( Throwable t ) {
        Debug.out( "writeProcessorLoop() EXCEPTION: ", t );
      }
    }
  }
  
  
  private boolean doNormalPriorityWrite() {
    RateControlledEntity ready_entity = getNextReadyNormalPriorityEntity();
    if( ready_entity != null && ready_entity.doProcessing( write_waiter ) ) {
      return true;
    }
    return false;
  }
  
  private boolean doHighPriorityWrite() {
    RateControlledEntity ready_entity = getNextReadyHighPriorityEntity();
    if( ready_entity != null && ready_entity.doProcessing( write_waiter ) ) {
      return true;
    }
    return false;
  }
  
  
  private RateControlledEntity getNextReadyNormalPriorityEntity() {
    ArrayList ref = normal_priority_entities;
    
    int size = ref.size();
    int num_checked = 0;

    while( num_checked < size ) {
      next_normal_position = next_normal_position >= size ? 0 : next_normal_position;  //make circular
      RateControlledEntity entity = (RateControlledEntity)ref.get( next_normal_position );
      next_normal_position++;
      num_checked++;
      if( entity.canProcess( write_waiter ) ) {  //is ready
        return entity;
      }
    }

    return null;  //none found ready
  }
  
  
  private RateControlledEntity getNextReadyHighPriorityEntity() {
    ArrayList ref = high_priority_entities;
    
    int size = ref.size();
    int num_checked = 0;

    while( num_checked < size ) {
      next_high_position = next_high_position >= size ? 0 : next_high_position;  //make circular
      RateControlledEntity entity = (RateControlledEntity)ref.get( next_high_position );
      next_high_position++;
      num_checked++;
      if( entity.canProcess( write_waiter ) ) {  //is ready
        return entity;
      }
    }

    return null;  //none found ready
  }
  
  
  
  /**
   * Add the given entity to the controller for write processing.
   * @param entity to process writes for
   */
  public void addWriteEntity( RateControlledEntity entity ) {
    try {  entities_mon.enter();
      if( entity.getPriority() == RateControlledEntity.PRIORITY_HIGH ) {
        //copy-on-write
        ArrayList high_new = new ArrayList( high_priority_entities.size() + 1 );
        high_new.addAll( high_priority_entities );
        high_new.add( entity );
        high_priority_entities = high_new;
      }
      else {
        //copy-on-write
        ArrayList norm_new = new ArrayList( normal_priority_entities.size() + 1 );
        norm_new.addAll( normal_priority_entities );
        norm_new.add( entity );
        normal_priority_entities = norm_new;
      }
    }
    finally {  entities_mon.exit();  }
  }
  
  
  /**
   * Remove the given entity from the controller.
   * @param entity to remove from write processing
   */
  public void removeWriteEntity( RateControlledEntity entity ) {
    try {  entities_mon.enter();
      if( entity.getPriority() == RateControlledEntity.PRIORITY_HIGH ) {
        //copy-on-write
        ArrayList high_new = new ArrayList( high_priority_entities );
        high_new.remove( entity );
        high_priority_entities = high_new;
      }
      else {
        //copy-on-write
        ArrayList norm_new = new ArrayList( normal_priority_entities );
        norm_new.remove( entity );
        normal_priority_entities = norm_new;
      }
    }
    finally {  entities_mon.exit();  }
  }
}
