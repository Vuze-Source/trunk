/*
 * Created on May 27, 2008
 * Created by Paul Gardner
 * 
 * Copyright 2008 Vuze, Inc.  All rights reserved.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License only.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.aelitis.azureus.plugins.net.buddy.tracker;

import java.nio.ByteBuffer;
import java.util.*;

import org.gudy.azureus2.core3.util.HashWrapper;
import org.gudy.azureus2.core3.util.SHA1;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.plugins.download.Download;
import org.gudy.azureus2.plugins.download.DownloadManagerListener;
import org.gudy.azureus2.plugins.torrent.Torrent;

import com.aelitis.azureus.plugins.net.buddy.*;

public class 
BuddyPluginTracker 
	implements BuddyPluginListener, DownloadManagerListener, BuddyPluginAZ2TrackerListener
{
	private static final int	TRACK_CHECK_PERIOD		= 30*1000;
	private static final int	TRACK_CHECK_TICKS		= TRACK_CHECK_PERIOD/BuddyPlugin.TIMER_PERIOD;

	private static final int	SHORT_ID_SIZE			= 4;
	private static final int	FULL_ID_SIZE			= 20;
	
	private static final int	REQUEST_TRACKER_SUMMARY	= 1;
	private static final int	REPLY_TRACKER_SUMMARY	= 2;
	
	private static final int	RETRY_SEND_MIN			= 5*60*1000;
	private static final int	RETRY_SEND_MAX			= 60*60*1000;
	
	private BuddyPlugin		plugin;
	private boolean			plugin_enabled;
	
	private Set				online_buddies 			= new HashSet();
	private Set				tracked_downloads		= new HashSet();
	private int				download_set_id;
	
	private Set				last_processed_download_set;
	private int				last_processed_download_set_id;
	
	private Map				short_id_map	= new HashMap();
	private Map				full_id_map		= new HashMap();
	
	public
	BuddyPluginTracker(
		BuddyPlugin		_plugin )
	{
		plugin		= _plugin;
		
		plugin_enabled = plugin.isEnabled();
		
		List buddies = plugin.getBuddies();
		
		for (int i=0;i<buddies.size();i++){
			
			buddyAdded((BuddyPluginBuddy)buddies.get(i));
		}
		
		plugin.addListener( this );
		
		plugin.getAZ2Handler().addTrackerListener( this );

		plugin.getPluginInterface().getDownloadManager().addListener( this, true );
	}
	
	public void
	tick(
		int		tick_count )
	{
		if ( tick_count % TRACK_CHECK_TICKS == 0 ){
			
			checkTracking();
		}
	}
	
	protected void
	checkTracking()
	{
		if ( !plugin_enabled ){
			
			return;
		}
		
		List	online;
		
		synchronized( online_buddies ){

			online = new ArrayList( online_buddies );
		}
		
		Set			downloads;
		int			downloads_id;
		
		synchronized( tracked_downloads ){
			
			boolean downloads_changed = last_processed_download_set_id != download_set_id;
			
			if ( downloads_changed ){
				
				last_processed_download_set 	= new HashSet( tracked_downloads );
				last_processed_download_set_id	= download_set_id;
			}
			
			downloads 		= last_processed_download_set;
			downloads_id	= last_processed_download_set_id;
		}
		
		Map	diff_map = new HashMap();
		
		for (int i=0;i<online.size();i++){
			
			BuddyPluginBuddy	buddy = (BuddyPluginBuddy)online.get(i);
			
			buddyData buddy_data = (buddyData)buddy.getUserData( BuddyPluginTracker.class );
			
			buddy_data.updateLocal( downloads, downloads_id, diff_map );
		}
	}		
	
	public void
	initialised(
		boolean		available )
	{	
	}
	
	public void
	buddyAdded(
		BuddyPluginBuddy	buddy )
	{
		buddyChanged( buddy );
	}
	
	public void
	buddyRemoved(
		BuddyPluginBuddy	buddy )
	{
		buddyChanged( buddy );
	}

	public void
	buddyChanged(
		BuddyPluginBuddy	buddy )
	{	
		if ( buddy.isOnline()){
			
			addBuddy( buddy );
			
		}else{
			
			removeBuddy( buddy );
		}
	}
	
	
	protected buddyData
	addBuddy(
		BuddyPluginBuddy		buddy )
	{
		synchronized( online_buddies ){
			
			if ( !online_buddies.contains( buddy )){
				
				online_buddies.add( buddy );
			}

			buddyData buddy_data = (buddyData)buddy.getUserData( BuddyPluginTracker.class );

			if ( buddy_data == null ){
				
				buddy_data = new buddyData( buddy );
				
				buddy.setUserData( BuddyPluginTracker.class, buddy_data );
			}
			
			return( buddy_data );
		}
	}
		
	protected void
	removeBuddy(
		BuddyPluginBuddy		buddy )
	{
		synchronized( online_buddies ){

			online_buddies.remove( buddy );
		}
	}
	
	public void
	messageLogged(
		String		str )
	{	
	}
	
	public void
	enabledStateChanged(
		boolean 	_enabled )
	{
		plugin_enabled = _enabled;
	}
	
	public void
	downloadAdded(
		Download	download )
	{
		Torrent t = download.getTorrent();
		
		if ( t == null ){
			
			return;
		}
		
		if ( t.isPrivate()){
			
			return;
		}
		
		synchronized( tracked_downloads ){
			
			if ( tracked_downloads.contains( download )){
				
				return;
			}
							
			downloadData download_data = new downloadData( download );
				
			download.setUserData( BuddyPluginTracker.class, download_data );
			
			HashWrapper	full_id		= download_data.getID();
			
			HashWrapper short_id 	= new HashWrapper( full_id.getHash(), 0, 4 );
			
			full_id_map.put( full_id, download );
			
			List	dls = (List)short_id_map.get( short_id );
			
			if ( dls == null ){
				
				dls = new ArrayList();
				
				short_id_map.put( short_id, dls );
			}
			
			dls.add( download );
			
			tracked_downloads.add( download );
			
			download_set_id++;
		}
	}
	
	public void
	downloadRemoved(
		Download	download )
	{
		synchronized( tracked_downloads ){
			
			downloadData download_data = (downloadData)download.getUserData( BuddyPluginTracker.class );
			
			if ( download_data != null ){
				
				HashWrapper	full_id		= download_data.getID();
				
				full_id_map.remove( full_id );
				
				HashWrapper short_id 	= new HashWrapper( full_id.getHash(), 0, SHORT_ID_SIZE );
				
				List	dls = (List)short_id_map.get( short_id );

				if ( dls != null ){
					
					dls.remove( download );
					
					if ( dls.size() == 0 ){
						
						short_id_map.remove( short_id );
					}
				}
			}
			
			if ( tracked_downloads.remove( download )){
				
				download_set_id++;
			}
		}
	}
	
	protected void
	sendMessage(
		BuddyPluginBuddy	buddy,
		int					type,
		Map					body )
	{
		Map	msg = new HashMap();
		
		msg.put( "type", new Long( type ));
		msg.put( "msg", body );
		
		plugin.getAZ2Handler().sendAZ2TrackerMessage(
				buddy, 
				msg, 
				BuddyPluginTracker.this );
	}
	
	public Map
	messageReceived(
		BuddyPluginBuddy	buddy,
		Map					message )
	{
		buddyData buddy_data = buddyAlive( buddy );
		
		int type = ((Long)message.get( "type" )).intValue();
		
		Map msg = (Map)message.get( "msg" );
		
		return( buddy_data.receiveMessage( type, msg ));
	}
	
	public void
	messageFailed(
		BuddyPluginBuddy	buddy,
		Throwable			cause )
	{
		log( "Failed to send message to " + buddy.getName(), cause );
		
		buddyDead( buddy );
	}
	
	protected buddyData
	buddyAlive(
		BuddyPluginBuddy		buddy )
	{
		buddyData buddy_data = addBuddy( buddy );
		
		buddy_data.setAlive( true );
		
		return( buddy_data );
	}
	
	protected void
	buddyDead(
		BuddyPluginBuddy		buddy )
	{
		buddyData buddy_data = (buddyData)buddy.getUserData( BuddyPluginTracker.class );

		if ( buddy_data != null ){
			
			buddy_data.setAlive( false );
		}
	}
	
	protected void
	log(
		String		str )
	{
		plugin.log( "Tracker: " + str );
	}
	
	protected void
	log(
		String		str,
		Throwable 	e )
	{
		plugin.log( "Tracker: " + str, e );
	}
	
	private class
	buddyData
	{
		private BuddyPluginBuddy		buddy;
		
		private Set	downloads_sent;
		private int	downloads_sent_id;
		
		private int		consecutive_fails;
		private long	last_fail;
		
		protected
		buddyData(
			BuddyPluginBuddy		_buddy )
		{
			buddy	= _buddy;
		}
		
		protected void
		setAlive(
			boolean		alive )
		{
			synchronized( this ){
				
				if ( alive ){
					
					consecutive_fails		= 0;
					last_fail				= 0;

				}else{
					
					consecutive_fails++;
					
					last_fail	= SystemTime.getMonotonousTime();
				}
			}
		}
		
		protected void
		updateLocal(
			Set		downloads,
			int		id,
			Map		diff_map )
		{
			if ( consecutive_fails > 0 ){
				
				long	retry_millis = RETRY_SEND_MIN;
				
				for (int i=0;i<consecutive_fails-1;i++){
					
					retry_millis <<= 2;
					
					if ( retry_millis > RETRY_SEND_MAX ){
						
						retry_millis = RETRY_SEND_MAX;
						
						break;
					}
				}
				
				long	now = SystemTime.getMonotonousTime();
				
				if ( now - last_fail >= retry_millis ){
					
					downloads_sent 		= null;
					downloads_sent_id	= 0;
				}
			}
			
			if ( id == downloads_sent_id ){
				
				return;
			}
			
			Long	key = new Long(((long)id) << 32 | (long)downloads_sent_id);
			
			Object[] map = (Object[])diff_map.get( key );
			
			byte[]	added_bytes;
			byte[]	removed_bytes;
			
			boolean	incremental = downloads_sent != null;
			
			if ( map == null ){
				
				List	added;
				List	removed	= new ArrayList();
				

				if ( downloads_sent == null ){
					
					added 	= new ArrayList( downloads );
					
				}else{
					
					added	= new ArrayList();

					Iterator	it1 = downloads.iterator();
					
					while( it1.hasNext()){
					
						Download download = (Download)it1.next();
						
						if ( !downloads_sent.contains( download )){
							
							added.add( download );
						}
					}
					
					Iterator	it2 = downloads_sent.iterator();
					
					while( it2.hasNext()){
					
						Download download = (Download)it2.next();
						
						if ( !downloads.contains( download )){
							
							removed.add( download );
						}
					}
				}
				
				added_bytes 	= exportShortIDs( added );
				removed_bytes 	= exportShortIDs( removed );
				
				diff_map.put( key, new Object[]{ added_bytes, removed_bytes });
				
			}else{
				
				added_bytes 	= (byte[])map[0];
				removed_bytes	= (byte[])map[1];
			}
				
			downloads_sent 		= downloads;
			downloads_sent_id	= id;
			
			if ( added_bytes.length == 0 && removed_bytes.length == 0 ){
				
				return;
			}
			
			Map	msg = new HashMap();
			
			msg.put( "added", 	added_bytes );
			msg.put( "removed", removed_bytes );
			msg.put( "inc", 	new Long( incremental?1:0 ));
			
			sendMessage( buddy, REQUEST_TRACKER_SUMMARY, msg );
		}	
		
		protected Map
		updateRemote(
			Map		msg )
		{			
			List	added 	= importShortIDs((byte[])msg.get( "added" ));
			List	removed = importShortIDs((byte[])msg.get( "removed" ));
			
			Map	reply = new HashMap();
			
			reply.put( "added", exportFullIDs( added ));
			reply.put( "removed", exportFullIDs( removed ));
			
			return( reply );
		}
		
		protected Map
		receiveMessage(
			int			type,
			Map			msg )
		{
			if ( type == REQUEST_TRACKER_SUMMARY ){
		
				Map	reply = new HashMap();
				
				reply.put( "type", new Long( REPLY_TRACKER_SUMMARY ));

				reply.put( "msg", updateRemote( msg ));
				
				return( reply );
				
			}else if ( type == REPLY_TRACKER_SUMMARY ){
				
					// full hashes on reply
				
				byte[]	possible_matches = (byte[])msg.get( "added" );
				
				if ( possible_matches != null ){
					
					System.out.println( "Possible matches!" );
				}
				
				return( null );
				
			}else{
				
				return( null );
			}
		}
		
		protected byte[]
		exportShortIDs(
			List	downloads )
		{
			byte[]	res = new byte[ SHORT_ID_SIZE * downloads.size() ];
			
			for (int i=0;i<downloads.size();i++ ){
				
				Download download = (Download)downloads.get(i);
				
				downloadData download_data = (downloadData)download.getUserData( BuddyPluginTracker.class );
				
				if ( download_data != null ){

					System.arraycopy(
						download_data.getID().getBytes(),
						0,
						res,
						i * SHORT_ID_SIZE,
						SHORT_ID_SIZE );
				}
			}
			
			return( res );
		}
		
		protected List
		importShortIDs(
			byte[]		ids )
		{
			List	res = new ArrayList();
			
			if ( ids != null ){
				
				synchronized( tracked_downloads ){

					for (int i=0;i<ids.length;i+= SHORT_ID_SIZE ){
					
						List dls = (List)short_id_map.get( new HashWrapper( ids, i, SHORT_ID_SIZE ));
						
						if ( dls != null ){
							
							res.addAll( dls );
						}
					}
				}
			}
			
			return( res );
		}
		
		protected byte[]
   		exportFullIDs(
   			List	downloads )
   		{
   			byte[]	res = new byte[ FULL_ID_SIZE * downloads.size() ];
   			
   			for (int i=0;i<downloads.size();i++ ){
   				
   				Download download = (Download)downloads.get(i);
   				
   				downloadData download_data = (downloadData)download.getUserData( BuddyPluginTracker.class );
   				
   				if ( download_data != null ){

   					System.arraycopy(
   						download_data.getID().getBytes(),
   						0,
   						res,
   						i * FULL_ID_SIZE,
   						FULL_ID_SIZE );
   				}
   			}
   			
   			return( res );
   		}
	}
	
	private static class
	downloadData
	{
		private static final byte[]	IV = {(byte)0x7A, (byte)0x7A, (byte)0xAD, (byte)0xAB, (byte)0x8E, (byte)0xBF, (byte)0xCD, (byte)0x39, (byte)0x87, (byte)0x0, (byte)0xA4, (byte)0xB8, (byte)0xFE, (byte)0x40, (byte)0xA2, (byte)0xE8 }; 
			
		private HashWrapper	id;
		
		protected
		downloadData(
			Download	download )
		{
			Torrent t = download.getTorrent();
			
			if ( t != null ){
				
				byte[]	hash = t.getHash();
				
				SHA1	sha1 = new SHA1();
			
				sha1.update( ByteBuffer.wrap( IV ));
				sha1.update( ByteBuffer.wrap( hash ));
				
				id = new HashWrapper( sha1.digest() );
			}
		}
		
		protected HashWrapper
		getID()
		{
			return( id );
		}
	}
}
