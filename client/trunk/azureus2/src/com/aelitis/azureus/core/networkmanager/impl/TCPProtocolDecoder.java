/*
 * Created on 18-Jan-2006
 * Created by Paul Gardner
 * Copyright (C) 2006 Aelitis, All Rights Reserved.
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

import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.AEThread;
import org.gudy.azureus2.core3.util.SystemTime;

public abstract class 
TCPProtocolDecoder 
{
	private static final LogIDs LOGID = LogIDs.NWMAN;

	private static final int	TIMEOUT_CHECK		= 5000;
	private static final int	LOG_TICKS			= 60000 / TIMEOUT_CHECK;
		
	private static List			decoders	= new ArrayList();
	
	private static AEMonitor	class_mon 	= new AEMonitor( "TCPProtocolDecoder:class" );
	
	static{
		new AEThread("TCPProtocolDecoder:timer", true )
		{
			public void
			runSupport()
			{
				int	loop = 0;
				
				while( true ){
					
					loop++;
					
					long	now = SystemTime.getCurrentTime();
					
					try{
						class_mon.enter();
					
						if ( loop % LOG_TICKS == 0 ){
							
					     	if (Logger.isEnabled()){
					     		
				        		Logger.log(	new LogEvent(LOGID, "Active protocol decoders = " + decoders.size()));
					     	}
						}
						
						Iterator	it = decoders.iterator();
						
						while( it.hasNext()){
							
							TCPProtocolDecoder	decoder = (TCPProtocolDecoder)it.next();
							
							if ( decoder.isComplete( now )){
								
								it.remove();
							}
						}
						
					}finally{
						
						class_mon.exit();
					}
					
					try{
						Thread.sleep( TIMEOUT_CHECK );
						
					}catch( Throwable e ){
					}
				}
			}
		}.start();
	}
	
	protected
	TCPProtocolDecoder(
		boolean	run_timer )
	{
		if ( run_timer ){
			
			try{
				class_mon.enter();
				
				decoders.add( this );
				
			}finally{
				
				class_mon.exit();
			}
		}
	}
	
	public abstract boolean
	isComplete(
		long	now );
	
	public abstract TCPTransportHelperFilter
	getFilter();
}
