/*
 * Created on 31-Jan-2005
 * Created by Paul Gardner
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

package com.aelitis.azureus.plugins.tracker.dht;

import java.net.InetSocketAddress;
import java.net.URL;
import java.util.*;

import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.AEThread;
import org.gudy.azureus2.core3.util.ByteFormatter;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.plugins.Plugin;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.PluginListener;
import org.gudy.azureus2.plugins.download.Download;
import org.gudy.azureus2.plugins.download.DownloadAnnounceResult;
import org.gudy.azureus2.plugins.download.DownloadAnnounceResultPeer;
import org.gudy.azureus2.plugins.download.DownloadListener;
import org.gudy.azureus2.plugins.download.DownloadManagerListener;
import org.gudy.azureus2.plugins.download.DownloadScrapeResult;
import org.gudy.azureus2.plugins.logging.LoggerChannel;
import org.gudy.azureus2.plugins.logging.LoggerChannelListener;
import org.gudy.azureus2.plugins.torrent.TorrentAttribute;
import org.gudy.azureus2.plugins.ui.UIManager;
import org.gudy.azureus2.plugins.ui.config.ActionParameter;
import org.gudy.azureus2.plugins.ui.config.BooleanParameter;
import org.gudy.azureus2.plugins.ui.config.Parameter;
import org.gudy.azureus2.plugins.ui.config.ParameterListener;
import org.gudy.azureus2.plugins.ui.config.StringParameter;
import org.gudy.azureus2.plugins.ui.model.BasicPluginConfigModel;
import org.gudy.azureus2.plugins.ui.model.BasicPluginViewModel;
import org.gudy.azureus2.plugins.utils.UTTimerEvent;
import org.gudy.azureus2.plugins.utils.UTTimerEventPerformer;

import com.aelitis.azureus.plugins.dht.DHTPlugin;
import com.aelitis.azureus.plugins.dht.DHTPluginOperationListener;

/**
 * @author parg
 *
 */

public class 
DHTTrackerPlugin 
	implements Plugin, DownloadListener
{
	private static final int	ANNOUNCE_TIMEOUT	= 2*60*1000;
	private static final int	ANNOUNCE_MIN		= 2*60*1000;
	private static final int	ANNOUNCE_MAX		= 60*60*1000;
	
	private static final int	NUM_WANT			= 35;	// Limit to ensure replies fit in 1 packet
	
	private PluginInterface		plugin_interface;
	
	private DHTPlugin			dht;
	
	private Set					running_downloads 		= new HashSet();
	private Set					registered_downloads 	= new HashSet();
	
	private Map					query_map			 	= new HashMap();
	
	private LoggerChannel		log;
	
	private AEMonitor	this_mon	= new AEMonitor( "DHTTrackerPlugin" );

	public void
	initialize(
		PluginInterface 	_plugin_interface )
	{
		plugin_interface	= _plugin_interface;
				
		plugin_interface.getPluginProperties().setProperty( "plugin.name", "DHT Tracker" );

		log = plugin_interface.getLogger().getTimeStampedChannel("DHT Tracker");

		UIManager	ui_manager = plugin_interface.getUIManager();

		final BasicPluginViewModel model = 
			ui_manager.createBasicPluginViewModel( "DHT Tracker");
		
		BasicPluginConfigModel	config = 
			ui_manager.createBasicPluginConfigModel( "Plugins", "DHT Tracker" );
			
		model.getActivity().setVisible( false );
		model.getProgress().setVisible( false );
		
		log.addListener(
				new LoggerChannelListener()
				{
					public void
					messageLogged(
						int		type,
						String	message )
					{
						model.getLogArea().appendText( message+"\n");
					}
					
					public void
					messageLogged(
						String		str,
						Throwable	error )
					{
						model.getLogArea().appendText( error.toString()+"\n");
					}
				});

		model.getStatus().setText( "Initialising" );
		
		log.log( "Waiting for DHT initialisation" );
		
		plugin_interface.addListener(
			new PluginListener()
			{
				public void
				initializationComplete()
				{
					final PluginInterface dht_pi = 
						plugin_interface.getPluginManager().getPluginInterfaceByClass(
									DHTPlugin.class );
					
					if ( dht_pi != null ){
						
						Thread	t = 
							new AEThread( "DHTTrackerPlugin:init" )
							{
								public void
								runSupport()
								{
									try{
										dht = (DHTPlugin)dht_pi.getPlugin();
									
										if ( dht != null && dht.isEnabled()){
										
											model.getStatus().setText( "Running" );
											
											initialise();
											
										}else{
											
											model.getStatus().setText( "Disabled, DHT not available" );
										}
									}catch( Throwable e ){
										
										model.getStatus().setText( "Failed" );
									}
								}
							};
							
						t.setDaemon( true );
						
						t.start();
					}
				}
				
				public void
				closedownInitiated()
				{
					
				}
				
				public void
				closedownComplete()
				{
					
				}
			});
	}
	
	protected void
	initialise()
	{
		final TorrentAttribute ta = plugin_interface.getTorrentManager().getAttribute( TorrentAttribute.TA_NETWORKS );
	
		plugin_interface.getDownloadManager().addListener(
				new DownloadManagerListener()
				{
					public void
					downloadAdded(
						Download	download )
					{
						String[]	networks = download.getListAttribute( ta );
						
						if ( networks != null ){
							
							for (int i=0;i<networks.length;i++){
								
								if ( networks[i].equalsIgnoreCase( "Public" )){
							
									if ( download.getTorrent() != null ){
									
										registerDownload( download );
									}
								}
							}
						}
					}
					
					public void
					downloadRemoved(
						Download	download )
					{
						unregisterDownload( download );
					}
				});
		
		plugin_interface.getUtilities().createTimer("DHT Tracker").addPeriodicEvent(
			15000,
			new UTTimerEventPerformer()
			{
				public void 
				perform(
					UTTimerEvent event) 
				{
					processRegistrations();
				}
			});
	}
	
	protected void
	processRegistrations()
	{
		ArrayList	rds;
	
		try{
			this_mon.enter();

			rds = new ArrayList(running_downloads);
			
		}finally{
			
			this_mon.exit();
		}

		long	 now = SystemTime.getCurrentTime();
		
		Iterator	it = rds.iterator();
		
		while( it.hasNext()){
			
			final Download	dl = (Download)it.next();
			
			if ( !registered_downloads.contains( dl )){
				
				log.log( "Registering download '" + dl.getName() + "'" );
				
				final 	long	start = SystemTime.getCurrentTime();
				
				registered_downloads.add( dl );
				
				try{ 
					this_mon.enter();

					query_map.put( dl, new Long( now ));
					
				}finally{
					
					this_mon.exit();
				}
				
				int	port = plugin_interface.getPluginconfig().getIntParameter( "TCP.Listen.Port" );

				dht.put( 
						dl.getTorrent().getHash(), 
						String.valueOf( port ).getBytes(), 
						new DHTPluginOperationListener()
						{
							public void
							valueFound(
								InetSocketAddress	originator,
								byte[]				value )
							{
								
							}
							
							public void
							complete(
								boolean	timeout_occurred )
							{
								log.log( "Registration of '" + dl.getName() + "' completed (elapsed=" + (SystemTime.getCurrentTime()-start) + ")");
							}
						});
			}
		}
		
		it = registered_downloads.iterator();
		
		while( it.hasNext()){
			
			final Download	dl = (Download)it.next();

			boolean	unregister;
			
			try{ 
				this_mon.enter();

				unregister = !running_downloads.contains( dl );
				
			}finally{
				
				this_mon.exit();
			}
			
			if ( unregister ){
				
				log.log( "Unregistering download '" + dl.getName() + "'" );
				
				final long	start = SystemTime.getCurrentTime();
				
				it.remove();
				
				try{
					this_mon.enter();

					query_map.remove( dl );
					
				}finally{
					
					this_mon.exit();
				}
				
				dht.remove( 
						dl.getTorrent().getHash(),
						new DHTPluginOperationListener()
						{
							public void
							valueFound(
								InetSocketAddress	originator,
								byte[]				value )
							{
								
							}
							
							public void
							complete(
								boolean	timeout_occurred )
							{
								log.log( "Unregistration of '" + dl.getName() + "' completed (elapsed=" + (SystemTime.getCurrentTime()-start) + ")");
							}
						});
			}
		}
		
		it = rds.iterator();
		
		while( it.hasNext()){
			
			final Download	dl = (Download)it.next();
			
			Long	next_time;
			
			try{
				this_mon.enter();
	
				next_time = (Long)query_map.get( dl );
				
			}finally{
				
				this_mon.exit();
			}
			
			if ( next_time != null && now >= next_time.longValue()){
			
				try{
					this_mon.enter();
		
					query_map.remove( dl );
					
				}finally{
					
					this_mon.exit();
				}
				
				final long	start = SystemTime.getCurrentTime();
								
				dht.get(dl.getTorrent().getHash(), 
						(byte)0,
						NUM_WANT, 
						ANNOUNCE_TIMEOUT,
						new DHTPluginOperationListener()
						{
							List	addresses 	= new ArrayList();
							List	ports		= new ArrayList();
							
							public void
							valueFound(
								InetSocketAddress	originator,
								byte[]				value )
							{
								String	str_val = new String(value);
								
								try{
									int	port = Integer.parseInt( str_val );
								
									addresses.add( originator.getAddress().getHostAddress());
									
									ports.add(new Integer(port));
									
								}catch( Throwable e ){
									
								}
							}
							
							public void
							complete(
								boolean	timeout_occurred )
							{
								log.log( "Get of '" + dl.getName() + "' completed (elapsed=" + (SystemTime.getCurrentTime()-start) + "), addresses = " + addresses.size());
																
								final DownloadAnnounceResultPeer[]	peers = new
									DownloadAnnounceResultPeer[addresses.size()];
								
								final long	retry = ANNOUNCE_MIN + peers.length*(ANNOUNCE_MAX-ANNOUNCE_MIN)/NUM_WANT;
								
								try{
									this_mon.enter();
								
									if ( running_downloads.contains( dl )){
										
										query_map.put( dl, new Long( SystemTime.getCurrentTime() + retry ));
									}
									
								}finally{
									
									this_mon.exit();
								}
								
								for (int i=0;i<peers.length;i++){
									
									final int f_i = i;
									
									peers[i] = 
										new DownloadAnnounceResultPeer()
										{
											public String
											getAddress()
											{
												return((String)addresses.get(f_i));
											}
											
											public int
											getPort()
											{
												return(((Integer)ports.get(f_i)).intValue());
											}
											
											public byte[]
											getPeerID()
											{
												return( null );
											}
										};
									
								}
								
									// TODO: do this properly
								
								if ( 	dl.getState() == Download.ST_DOWNLOADING ||
										dl.getState() == Download.ST_SEEDING ){
								
									dl.setAnnounceResult(
											new DownloadAnnounceResult()
											{
												public Download
												getDownload()
												{
													return( dl );
												}
																							
												public int
												getResponseType()
												{
													return( DownloadAnnounceResult.RT_SUCCESS );
												}
																						
												public int
												getReportedPeerCount()
												{
													return( peers.length);
												}
												
											
												public int
												getSeedCount()
												{
													return( 0 );	// TODO:
												}
												
												public int
												getNonSeedCount()
												{
													return( 0 );	// TODO:
												}
												
												public String
												getError()
												{
													return( null );
												}
																							
												public URL
												getURL()
												{
													try{
														return( new URL( "dht://" + ByteFormatter.encodeString( dl.getTorrent().getHash()) + "/" ));
														
													}catch( Throwable e ){
														
														Debug.printStackTrace(e);
														
														return( null );
													}
												}
												
												public DownloadAnnounceResultPeer[]
												getPeers()
												{
													return( peers );
												}
												
												public long
												getTimeToWait()
												{
													return( retry );
												}
											});
								}else{
									
									dl.setScrapeResult(
										new DownloadScrapeResult()
										{
											public Download
											getDownload()
											{
												return( dl );
											}
											
											public int
											getResponseType()
											{
												return( RT_SUCCESS );
											}
											
											public int
											getSeedCount()
											{
												return( peers.length/2 );	// !!!! TODO:
											}
											
											public int
											getNonSeedCount()
											{
												return( peers.length/2 );	// TODO:
											}

											public long
											getScrapeStartTime()
											{
												return( start );
											}
												
											public void 
											setNextScrapeStartTime(
												long nextScrapeStartTime)
											{
												
											}
												  
											public String
											getStatus()
											{
												return( "OK" );
											}
	
											public URL
											getURL()
											{
												try{
													return( new URL( "dht://" + ByteFormatter.encodeString( dl.getTorrent().getHash()) + "/" ));
													
												}catch( Throwable e ){
													
													Debug.printStackTrace(e);
													
													return( null );
												}
											}
										});
								}
							}
						});
			}
		}
	}
	
	protected void
	registerDownload(
		Download	download )
	{
		log.log( "Tracking starts for download ' " + download.getName() + "'" );
		
		download.addListener( this );
		
			// pick up initial state
		
		stateChanged( download, download.getState(), download.getState());
	}
	
	protected void
	unregisterDownload(
		Download	download )
	{
		log.log( "Tracking stops for download ' " + download.getName() + "'" );

		download.removeListener( this );
		
		try{
			this_mon.enter();

			running_downloads.remove( download );
			
		}finally{
			
			this_mon.exit();
		}
	}
	
	public void
	stateChanged(
		Download		download,
		int				old_state,
		int				new_state )
	{
		int	state = download.getState();
		
		try{
			this_mon.enter();

			if ( 	state == Download.ST_DOWNLOADING ||
					state == Download.ST_SEEDING ||
					state == Download.ST_QUEUED ){
				
				running_downloads.add( download );
				
			}else{
				
				running_downloads.remove( download );
			}
		}finally{
			
			this_mon.exit();
		}
	}
 
	public void
	positionChanged(
		Download		download, 
		int 			oldPosition,
		int 			newPosition )
	{
		
	}
}
