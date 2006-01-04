/*
 * Created on 20-Dec-2005
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

package com.aelitis.azureus.core.instancemanager.impl;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.*;

import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.AESemaphore;
import org.gudy.azureus2.core3.util.AEThread;
import org.gudy.azureus2.core3.util.ByteFormatter;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SHA1Simple;
import org.gudy.azureus2.core3.util.SimpleTimer;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.TimerEvent;
import org.gudy.azureus2.core3.util.TimerEventPerformer;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.download.Download;
import org.gudy.azureus2.plugins.utils.UTTimer;
import org.gudy.azureus2.pluginsimpl.local.download.DownloadManagerImpl;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreLifecycleAdapter;
import com.aelitis.azureus.core.instancemanager.AZInstance;
import com.aelitis.azureus.core.instancemanager.AZInstanceManager;
import com.aelitis.azureus.core.instancemanager.AZInstanceManagerListener;
import com.aelitis.net.upnp.UPnPFactory;
import com.aelitis.net.upnp.UPnPSSDP;
import com.aelitis.net.upnp.UPnPSSDPAdapter;
import com.aelitis.net.upnp.UPnPSSDPListener;

public class 
AZInstanceManagerImpl 
	implements AZInstanceManager, UPnPSSDPListener
{
	private static final LogIDs LOGID = LogIDs.NET;
	
	private String				SSDP_GROUP_ADDRESS 	= "239.255.068.250";	// 239.255.000.000-239.255.255.255 
	private int					SSDP_GROUP_PORT		= 16680;				//
	private int					SSDP_CONTROL_PORT	= 16679;

	private static final long	ALIVE_PERIOD	= 30*60*1000;
	
	private static AZInstanceManagerImpl	singleton;
	
	private List	listeners	= new ArrayList();
	
	private static AEMonitor	class_mon = new AEMonitor( "AZInstanceManager:class" );
	
	public static AZInstanceManager
	getSingleton(
		AzureusCore	core )
	{
		try{
			class_mon.enter();
			
			if ( singleton == null ){
				
				singleton = new AZInstanceManagerImpl( core );
			}
		}finally{
			
			class_mon.exit();
		}
		
		return( singleton );
	}
	
	private AzureusCore	core;
	private UPnPSSDP 	ssdp;
	private long		search_id_next;
	private List		requests = new ArrayList();
	
	private AZMyInstanceImpl		my_instance;
	private Map						other_instances	= new HashMap();
	
	private Map			tcp_lan_to_ext	= new HashMap();
	private Map			udp_lan_to_ext	= new HashMap();
	private Map			tcp_ext_to_lan	= new HashMap();
	private Map			udp_ext_to_lan	= new HashMap();
	
	private AESemaphore	initial_search_sem	= new AESemaphore( "AZInstanceManager:initialSearch" );
	
	private AEMonitor	this_mon = new AEMonitor( "AZInstanceManager" );

	protected
	AZInstanceManagerImpl(
		AzureusCore	_core )
	{
		core			= _core;
		
		my_instance	= new AZMyInstanceImpl( core, this );
		
		new AZPortClashHandler( this );
	}
	
	public void
	initialize()
	{
		final PluginInterface	pi = core.getPluginManager().getDefaultPluginInterface();
		
		try{
			ssdp = 
				UPnPFactory.getSSDP( 
					new UPnPSSDPAdapter()
					{
						public UTTimer
						createTimer(
							String	name )
						{
							return( pi.getUtilities().createTimer( name ));
						}
		
						public void
						createThread(
							String		name,
							AERunnable	runnable )
						{
							pi.getUtilities().createThread( name, runnable );
						}
						
						public void
						trace(
							Throwable	e )
						{
							Debug.printStackTrace( e );
							
							Logger.log(new LogEvent(LOGID, "SSDP: failed ", e)); 

						}
						
						public void
						trace(
							String	str )
						{
							if ( Logger.isEnabled()){
								
								Logger.log(new LogEvent( LOGID, str )); 
							}
						}
					},
					SSDP_GROUP_ADDRESS,
					SSDP_GROUP_PORT,
					SSDP_CONTROL_PORT );
			
			ssdp.addListener( this );
		
			core.addLifecycleListener(
				new AzureusCoreLifecycleAdapter()
				{
					public void
					stopping(
						AzureusCore		core )
					{
						if ( other_instances.size() > 0 ){
							
							ssdp.notify( my_instance.encode(), "ssdp:byebye" );
						}
					}
				});
			
			SimpleTimer.addPeriodicEvent(
				ALIVE_PERIOD,
				new TimerEventPerformer()
				{
					public void
					perform(
						TimerEvent	event )
					{
						checkTimeouts();
						
						if ( other_instances.size() > 0 ){
							
							sendAlive();
						}				
					}
				});
		
		}catch( Throwable e ){
			
			initial_search_sem.releaseForever();
			
			Debug.printStackTrace(e);
		}
		
		new AEThread( "AZInstanceManager:initialSearch", true )
		{
			public void
			runSupport()
			{
				try{
					search();
					
						// pick up our own details as soon as we can
					
					addAddresses( my_instance );
					
				}finally{
					
					initial_search_sem.releaseForever();
				}
			}
		}.start();
	}
	
	public boolean
	isInitialized()
	{
		return( initial_search_sem.isReleasedForever());
	}
	
	protected void
	sendAlive()
	{
		ssdp.notify( my_instance.encode(), "ssdp:alive" );
	}
	
	public void
	receivedResult(
		NetworkInterface	network_interface,
		InetAddress			local_address,
		InetAddress			originator,
		URL					location,
		String				ST,
		String				AL )
	{
		// System.out.println( "received result: " + ST + "/" + AL );

		if ( ST.startsWith("azureus:") && AL != null ){
			
			StringTokenizer	tok = new StringTokenizer( ST, ":" );
			
			tok.nextToken();	// az
			
			tok.nextToken();	// command 

			String	az_id = tok.nextToken();	// az id
			
			if ( az_id.equals( my_instance.getID())){
			
				long search_id = Long.parseLong( tok.nextToken());	// search id
				
				try{
					this_mon.enter();
					
					for (int i=0;i<requests.size();i++){
						
						request	req = (request)requests.get(i);
						
						if ( req.getID() == search_id ){
							
							req.addReply( originator, AL );
						}
					}
				}finally{
					
					this_mon.exit();
				}
			}
		}
	}
	
	public String
	receivedSearch(
		NetworkInterface	network_interface,
		InetAddress			local_address,
		InetAddress			originator,
		String				user_agent,
		String				ST )
	{
		// System.out.println( "received search: " + user_agent + "/" + ST );
		
		AZOtherInstanceImpl	caller = null;
		
		if ( user_agent.startsWith("azureus:")){
			
			caller = AZOtherInstanceImpl.decode( originator, user_agent );
			
			if ( caller != null ){
				
				caller = checkAdd( caller );
			}
		}
		
		if ( ST.startsWith("azureus:")){
			
			StringTokenizer	tok = new StringTokenizer( ST, ":" );
			
			tok.nextToken();	// az
			
			String	command = tok.nextToken();

			String az_id = tok.nextToken();	// az id
			
			if ( !az_id.equals( my_instance.getID())){
							
				tok.nextToken();	// req id

				String	reply = my_instance.encode();
	
				if ( command.equals("btih")){
					
					String	hash_str = tok.nextToken();
					
					byte[]	hash = ByteFormatter.decodeString( hash_str );
					
					boolean	seed = tok.nextToken().equals( "true" );
					
					List	dms = core.getGlobalManager().getDownloadManagers();
					
					Iterator	it = dms.iterator();
					
					DownloadManager	matching_dm = null;
					
					try{
						while( it.hasNext()){
							
							DownloadManager	dm = (DownloadManager)it.next();
							
							byte[]	sha1_hash = (byte[])dm.getData( "AZInstanceManager::sha1_hash" );
							
							if ( sha1_hash == null ){			
	
								sha1_hash	= new SHA1Simple().calculateHash( dm.getTorrent().getHash());
								
								dm.setData( "AZInstanceManager::sha1_hash", sha1_hash );
							}
							
							if ( Arrays.equals( hash, sha1_hash )){
								
								matching_dm	= dm;
								
								break;
							}
						}
					}catch( Throwable e ){
						
						Debug.printStackTrace(e);
					}
					
					if ( matching_dm == null ){
						
						return( null );
					}
					
					int	dm_state = matching_dm.getState();
					
					if ( dm_state == DownloadManager.STATE_ERROR || dm_state == DownloadManager.STATE_STOPPED ){
						
						return( null );
					}
					
					if ( caller != null ){
					
						try{
							caller.setProperty( AZInstance.PR_DOWNLOAD, DownloadManagerImpl.getDownloadStatic( matching_dm ));
							caller.setProperty( AZInstance.PR_SEED, new Boolean( seed ));
							
							informTracked( caller );
							
						}catch( Throwable e ){
							
							Debug.printStackTrace(e);
						}
					}
					
					reply += ":" + (matching_dm.isDownloadComplete()?"true":"false");
					
					return( reply );
					
				}else if ( command.equals( "instance" )){
					
					return( reply );
				}
			}
		}
		
		return( null );
	}
	
	public void
	receivedNotify(
		NetworkInterface	network_interface,
		InetAddress			local_address,
		InetAddress			originator,
		URL					location,
		String				NT,
		String				NTS )
	{
		// System.out.println( "received notify: " + NT + "/" + NTS );

		if ( NT.startsWith("azureus:")){
			
			AZOtherInstanceImpl	inst = AZOtherInstanceImpl.decode( originator, NT );

			if ( inst != null ){
			
				if ( NTS.indexOf("alive") != -1 ){

					checkAdd( inst );
					
				}else if ( NTS.indexOf("byebye") != -1 ){

					checkRemove( inst );
				}
			}
		}
	}

	protected AZOtherInstanceImpl
	checkAdd(
		AZOtherInstanceImpl	inst )
	{
		if ( inst.getID().equals( my_instance.getID())){
			
			return( inst );
		}
		
		boolean	added 	= false;
		boolean	changed	= false;
		
		try{
			this_mon.enter();
			
			AZOtherInstanceImpl	existing = (AZOtherInstanceImpl)other_instances.get( inst.getID());
			
			if ( existing == null ){
				
				added	= true;
			
				other_instances.put( inst.getID(), inst );
								
			}else{
								
				changed = existing.update( inst );

				inst	= existing;
			}
		}finally{
			
			this_mon.exit();
		}
		
		if ( added ){
			
			informAdded( inst );
			
		}else if ( changed ){
			
			informChanged( inst );
		}
		
		return( inst );
	}
	
	protected void
	checkRemove(
		AZOtherInstanceImpl	inst )
	{
		if ( inst.getID().equals( my_instance.getID())){
			
			return;
		}
		
		boolean	removed = false;
		
		try{
			this_mon.enter();
			
			removed = other_instances.remove( inst.getID()) != null;
			
		}finally{
			
			this_mon.exit();
		}
		
		if ( removed ){
			
			informRemoved( inst );
		}
	}
	
	public AZInstance
	getMyInstance()
	{
		return( my_instance );
	}
	
	protected void
	search()
	{
		request req = sendRequest( "instance" );
		
		req.getReply();
	}
	
	public AZInstance[]
	getOtherInstances()
	{
		initial_search_sem.reserve();
		
		try{
			this_mon.enter();

			return((AZInstance[])other_instances.values().toArray( new AZInstance[other_instances.size()]));
			
		}finally{
			
			this_mon.exit();
		}
	}
	
	protected void
	addAddresses(
		AZInstance	inst )
	{
		InetAddress	internal_address 	= inst.getInternalAddress();
		InetAddress	external_address	= inst.getExternalAddress();
		int			tcp					= inst.getTrackerClientPort();
		int			udp					= inst.getDHTPort();
		
		modifyAddresses( internal_address, external_address, tcp, udp, true );
	}
	
	protected void
	removeAddresses(
		AZOtherInstanceImpl	inst )
	{
		List		internal_addresses 	= inst.getInternalAddresses();
		InetAddress	external_address	= inst.getExternalAddress();
		int			tcp					= inst.getTrackerClientPort();
		int			udp					= inst.getDHTPort();
		
		for (int i=0;i<internal_addresses.size();i++){
			
			modifyAddresses( (InetAddress)internal_addresses.get(i), external_address, tcp, udp, false );
		}
	}
	
	protected void
	modifyAddresses(
		InetAddress		internal_address,
		InetAddress		external_address,
		int				tcp,
		int				udp,
		boolean			add )	
	{
		if ( internal_address.isAnyLocalAddress()){
			
			try{
				internal_address = InetAddress.getLocalHost();
				
			}catch( Throwable e ){
				
				Debug.printStackTrace(e);
			}
		}
		
		try{
			this_mon.enter();
 
			InetSocketAddress	int_tcp = new InetSocketAddress(internal_address, tcp);
			InetSocketAddress	ext_tcp = new InetSocketAddress(external_address, tcp);
			InetSocketAddress	int_udp = new InetSocketAddress(internal_address, udp);
			InetSocketAddress	ext_udp = new InetSocketAddress(external_address, udp);
			
				// not the most efficient code in the world this... will need rev
			
			tcp_ext_to_lan = modifyAddress( tcp_ext_to_lan, ext_tcp, int_tcp, add );
			tcp_lan_to_ext = modifyAddress( tcp_lan_to_ext, int_tcp, ext_tcp, add );
			udp_ext_to_lan = modifyAddress( udp_ext_to_lan, ext_udp, int_udp, add );
			udp_lan_to_ext = modifyAddress( udp_lan_to_ext, int_udp, ext_udp, add );
	
		}finally{
			
			this_mon.exit();
		}
	}
		
	protected Map
	modifyAddress(
		Map					map,
		InetSocketAddress	key,
		InetSocketAddress	value,
		boolean				add )
	{
		// System.out.println( "ModAddress: " + key + " -> " + value + " - " + (add?"add":"remove"));
		
		InetSocketAddress	old_value = (InetSocketAddress)map.get(key);

		boolean	same = old_value != null && old_value.equals( value );
		
		Map	new_map = map;
		
		if ( add ){
			
			if ( !same ){
				
				new_map	= new HashMap( map );
	
				new_map.put( key, value );
			}
		}else{
			
			if ( same ){
				
				new_map	= new HashMap( map );
				
				new_map.remove( key );
			}
		}	
		
		return( new_map );
	}
	
	public InetSocketAddress
	getLANAddress(
		InetSocketAddress	external_address,
		boolean				is_tcp )
	{
		Map	map = is_tcp?tcp_ext_to_lan:udp_ext_to_lan;
		
		if ( map.size() == 0 ){
			
			return( null );
		}
		
		return((InetSocketAddress)map.get( external_address ));
	}
	
	public InetSocketAddress
	getExternalAddress(
		InetSocketAddress	lan_address,
		boolean				is_tcp )
	{
		Map	map = is_tcp?tcp_lan_to_ext:udp_lan_to_ext;
		
		if ( map.size() == 0 ){
			
			return( null );
		}
		
		return((InetSocketAddress)map.get( lan_address ));	
	}
	
	public AZInstance[]
	track(
		Download		download )
	{
		if ( ssdp == null || download.getTorrent() == null  ){
			
			return( new AZInstance[0]);
		}
		
		request req = 
			sendRequest( 
				"btih", 
				new String[]{
					ByteFormatter.encodeString( new SHA1Simple().calculateHash(download.getTorrent().getHash())),
					download.isComplete()?"true":"false"
				});
		
		AZInstance[]	replies = req.getReply();
		
		List	res = new ArrayList();
		
		for (int i=0;i<replies.length;i++){
			
			AZInstance	_reply = replies[i];
			
			if ( _reply instanceof AZOtherInstanceImpl ){
				
				AZOtherInstanceImpl	reply = (AZOtherInstanceImpl)_reply;
				
				List	args = reply.getExtraArgs();
				
				if ( args.size() >= 1 ){
					
					boolean	seed = ((String)args.get(0)).equals("true");
					
					reply.setProperty( AZInstance.PR_SEED, new Boolean( seed ));
					
					reply.setProperty(AZInstance.PR_DOWNLOAD, download );
					
					res.add( reply );
				}
			}
		}
		
		return( (AZInstance[])res.toArray( new AZInstance[res.size()]));
	}
	
	protected void
	checkTimeouts()
	{
		long	now = SystemTime.getCurrentTime();
	
		List	removed = new ArrayList();
		
		try{
			this_mon.enter();

			Iterator	it = other_instances.values().iterator();
			
			while( it.hasNext()){
				
				AZOtherInstanceImpl	inst = (AZOtherInstanceImpl)it.next();
	
				if ( now - inst.getAliveTime() > ALIVE_PERIOD * 2.5 ){
					
					removed.add( inst );
					
					it.remove();
				}
			}
		}finally{
			
			this_mon.exit();
		}
		
		for (int i=0;i<removed.size();i++){
			
			AZOtherInstanceImpl	inst = (AZOtherInstanceImpl)removed.get(i);
			
			informRemoved( inst );
		}
	}
	
	protected void
	informRemoved(
		AZOtherInstanceImpl	inst )
	{
		removeAddresses( inst );
		
		for (int i=0;i<listeners.size();i++){
			
			try{
				((AZInstanceManagerListener)listeners.get(i)).instanceLost( inst );
				
			}catch( Throwable e ){
				
				Debug.printStackTrace(e);
			}
		}
	}
	
	protected void
	informAdded(
		AZInstance	inst )
	{
		addAddresses( inst );

		for (int i=0;i<listeners.size();i++){
			
			try{
				((AZInstanceManagerListener)listeners.get(i)).instanceFound( inst );
				
			}catch( Throwable e ){
				
				Debug.printStackTrace(e);
			}
		}
	}
	
	protected void
	informChanged(
		AZInstance	inst )
	{
		addAddresses( inst );
		
		if ( inst == my_instance ){
			
			sendAlive();
		}
		
		for (int i=0;i<listeners.size();i++){
			
			try{
				((AZInstanceManagerListener)listeners.get(i)).instanceChanged( inst );
				
			}catch( Throwable e ){
				
				Debug.printStackTrace(e);
			}
		}
	}
	
	protected void
	informTracked(
		AZInstance	inst )
	{
		for (int i=0;i<listeners.size();i++){
			
			try{
				((AZInstanceManagerListener)listeners.get(i)).instanceTracked( inst );
				
			}catch( Throwable e ){
				
				Debug.printStackTrace(e);
			}
		}
	}
	
	protected request
	sendRequest(
		String	type )
	{
		return( new request( type, new String[0] ));
	}
	
	protected request
	sendRequest(
		String	type,
		String	[]args )
	{
		return( new request( type, args ));
	}
	
	protected class
	request
	{
		private long	id;
		
		private List	replies = new ArrayList();
		
		protected
		request(
			String		_type,
			String[]	_args )
		{
			try{
				this_mon.enter();

				id	= search_id_next++;
						
				requests.add( this );
	
			}finally{
				
				this_mon.exit();
			}
			
			String	st = "azureus:" + _type + ":" + my_instance.getID() + ":" + id;
			
			for (int i=0;i<_args.length;i++){
				
				st += ":" + _args[i];
			}
			
			ssdp.search( my_instance.encode(), st );
		}
		
		protected long
		getID()
		{
			return( id );
		}
		
		protected void
		addReply(
			InetAddress	internal_address,
			String		AL )
		{
			AZOtherInstanceImpl	inst = AZOtherInstanceImpl.decode( internal_address, AL );
			
			if ( inst != null ){
												
				try{
					this_mon.enter();
				
					boolean	duplicate_reply	= false;
					
					for (int i=0;i<replies.size();i++){
						
						AZInstance	rep = (AZInstance)replies.get(i);
						
						if ( rep.getID().equals( inst.getID())){
							
							duplicate_reply	= true;
							
							break;
						}
					}
					
					if ( !duplicate_reply ){
						
						replies.add( inst );
					}								
				}finally{
					
					this_mon.exit();
				}
				
				checkAdd( inst );
			}
		}
		
		protected AZInstance[]
		getReply()
		{
			try{
				Thread.sleep( 2500 );
				
			}catch( Throwable e ){
				
			}
			
			try{
				this_mon.enter();

				requests.remove( this );
				
				return(( AZInstance[])replies.toArray(new AZInstance[replies.size()]));			
				
			}finally{
				
				this_mon.exit();
			}
		}
	}

	public void
	addListener(
		AZInstanceManagerListener	l )
	{
		listeners.add( l );
	}
	
	public void
	removeListener(
		AZInstanceManagerListener	l )
	{
		listeners.remove( l );
	}
}
