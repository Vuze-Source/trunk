/*
 * Created on Jul 11, 2008
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


package com.aelitis.azureus.core.subs.impl;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.KeyPair;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.bouncycastle.util.encoders.Base64;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.torrent.TOTorrent;
import org.gudy.azureus2.core3.torrent.TOTorrentFactory;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.download.*;
import org.gudy.azureus2.plugins.peers.PeerManager;
import org.gudy.azureus2.plugins.torrent.Torrent;
import org.gudy.azureus2.plugins.torrent.TorrentAttribute;
import org.gudy.azureus2.plugins.torrent.TorrentManager;
import org.gudy.azureus2.plugins.ui.UIManager;
import org.gudy.azureus2.plugins.ui.UIManagerEvent;
import org.gudy.azureus2.plugins.utils.DelayedTask;
import org.gudy.azureus2.plugins.utils.StaticUtilities;
import org.gudy.azureus2.pluginsimpl.local.PluginInitializer;
import org.gudy.azureus2.pluginsimpl.local.torrent.TorrentImpl;
import org.gudy.azureus2.pluginsimpl.local.utils.UtilitiesImpl;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreRunningListener;
import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.custom.Customization;
import com.aelitis.azureus.core.custom.CustomizationManager;
import com.aelitis.azureus.core.custom.CustomizationManagerFactory;
import com.aelitis.azureus.core.dht.DHT;
import com.aelitis.azureus.core.lws.LightWeightSeed;
import com.aelitis.azureus.core.lws.LightWeightSeedManager;
import com.aelitis.azureus.core.messenger.config.PlatformSubscriptionsMessenger;
import com.aelitis.azureus.core.metasearch.Engine;
import com.aelitis.azureus.core.metasearch.MetaSearchListener;
import com.aelitis.azureus.core.metasearch.MetaSearchManagerFactory;
import com.aelitis.azureus.core.metasearch.impl.web.WebEngine;
import com.aelitis.azureus.core.security.CryptoECCUtils;
import com.aelitis.azureus.core.subs.*;
import com.aelitis.azureus.core.torrent.PlatformTorrentUtils;
import com.aelitis.azureus.core.util.CopyOnWriteList;
import com.aelitis.azureus.core.vuzefile.*;
import com.aelitis.azureus.plugins.dht.*;
import com.aelitis.azureus.plugins.magnet.MagnetPlugin;
import com.aelitis.azureus.plugins.magnet.MagnetPluginProgressListener;
import com.aelitis.azureus.util.ImportExportUtils;
import com.aelitis.azureus.util.UrlFilter;


public class 
SubscriptionManagerImpl 
	implements SubscriptionManager, AEDiagnosticsEvidenceGenerator
{	
	private static final String	CONFIG_FILE = "subscriptions.config";
	private static final String	LOGGER_NAME = "Subscriptions";

	private static final String CONFIG_MAX_RESULTS 			= "subscriptions.max.non.deleted.results";
	private static final String CONFIG_AUTO_START_DLS 		= "subscriptions.auto.start.downloads";
	private static final String CONFIG_AUTO_START_MIN_MB 	= "subscriptions.auto.start.min.mb";
	private static final String CONFIG_AUTO_START_MAX_MB 	= "subscriptions.auto.start.max.mb";
	
	private static final String	RSS_ENABLE_CONFIG_KEY		= "subscriptions.config.rss_enable";

	
	private static SubscriptionManagerImpl		singleton;
	private static boolean						pre_initialised;
	
	private static final int random_seed = RandomUtils.nextInt( 256 );
	
	public static void
	preInitialise()
	{
		synchronized( SubscriptionManagerImpl.class ){
			
			if ( pre_initialised ){
				
				return;
			}
			
			pre_initialised = true;
		}
		
		VuzeFileHandler.getSingleton().addProcessor(
			new VuzeFileProcessor()
			{
				public void
				process(
					VuzeFile[]		files,
					int				expected_types )
				{
					for (int i=0;i<files.length;i++){
						
						VuzeFile	vf = files[i];
						
						VuzeFileComponent[] comps = vf.getComponents();
						
						for (int j=0;j<comps.length;j++){
							
							VuzeFileComponent comp = comps[j];
							
							int	type = comp.getType();
							
							if ( 	type == VuzeFileComponent.COMP_TYPE_SUBSCRIPTION ||
									type == VuzeFileComponent.COMP_TYPE_SUBSCRIPTION_SINGLETON ){
								
								try{
									((SubscriptionManagerImpl)getSingleton( false )).importSubscription(
											type,
											comp.getContent(),
											( expected_types & 
												( VuzeFileComponent.COMP_TYPE_SUBSCRIPTION | VuzeFileComponent.COMP_TYPE_SUBSCRIPTION_SINGLETON )) == 0 );
									
									comp.setProcessed();
									
								}catch( Throwable e ){
									
									Debug.printStackTrace(e);
								}
							}
						}
					}
				}
			});		
	}
		
	public static SubscriptionManager
	getSingleton(
		boolean		stand_alone )
	{
		preInitialise();
		
		synchronized( SubscriptionManagerImpl.class ){
			
			if ( singleton != null ){
			
				return( singleton );
			}
			
			singleton = new SubscriptionManagerImpl( stand_alone );
		}
		
			// saw deadlock here when adding core listener while synced on class - rework
			// to avoid 
		
		if ( !stand_alone ){
			
			singleton.initialise();
		}
		
		return( singleton );
	}
	
	
	private boolean		started;
		
	private static final int	TIMER_PERIOD		= 30*1000;
	
	private static final int	ASSOC_CHECK_PERIOD	= 5*60*1000;
	private static final int	ASSOC_CHECK_TICKS	= ASSOC_CHECK_PERIOD/TIMER_PERIOD;
	
	private static final int	SERVER_PUB_CHECK_PERIOD	= 10*60*1000;
	private static final int	SERVER_PUB_CHECK_TICKS	= SERVER_PUB_CHECK_PERIOD/TIMER_PERIOD;
	
	private static final int	TIDY_POT_ASSOC_PERIOD	= 30*60*1000;
	private static final int	TIDY_POT_ASSOC_TICKS	= TIDY_POT_ASSOC_PERIOD/TIMER_PERIOD;

	private static final int	SET_SELECTED_PERIOD		= 23*60*60*1000;
	private static final int	SET_SELECTED_FIRST_TICK	= 3*60*1000 /TIMER_PERIOD;
	private static final int	SET_SELECTED_TICKS		= SET_SELECTED_PERIOD/TIMER_PERIOD;

	private static final Object	SP_LAST_ATTEMPTED	= new Object();
	private static final Object	SP_CONSEC_FAIL		= new Object();
	
	
	private volatile DHTPlugin	dht_plugin;
	
	private List		subscriptions	= new ArrayList();
	
	private boolean	config_dirty;
	
	private static final int PUB_ASSOC_CONC_MAX	= 3;
	
	private int		publish_associations_active;
	
	private boolean publish_subscription_active;
	
	private TorrentAttribute		ta_subs_download;
	private TorrentAttribute		ta_subs_download_rd;
	private TorrentAttribute		ta_subscription_info;
	private TorrentAttribute		ta_category;
	
	private boolean					periodic_lookup_in_progress;
	private int						priority_lookup_pending;
	
	private CopyOnWriteList			listeners = new CopyOnWriteList();
	
	private SubscriptionSchedulerImpl	scheduler;
	
	private List					potential_associations	= new ArrayList();
	private Map						potential_associations2	= new HashMap();
	
	private boolean					meta_search_listener_added;
	
	private Pattern					exclusion_pattern = Pattern.compile( "azdev[0-9]+\\.azureus\\.com" );
	
	private SubscriptionRSSFeed		rss_publisher;
	
	private AEDiagnosticsLogger		logger;
	
	
	protected
	SubscriptionManagerImpl(
		boolean	stand_alone )
	{
		if ( !stand_alone ){
			
			loadConfig();
	
			AEDiagnostics.addEvidenceGenerator( this );
			
			CustomizationManager cust_man = CustomizationManagerFactory.getSingleton();
			
			Customization cust = cust_man.getActiveCustomization();
			
			if ( cust != null ){
				
				String cust_name 	= COConfigurationManager.getStringParameter( "subscriptions.custom.name", "" );
				String cust_version = COConfigurationManager.getStringParameter( "subscriptions.custom.version", "0" );
				
				boolean	new_name 	= !cust_name.equals( cust.getName());
				boolean	new_version = org.gudy.azureus2.core3.util.Constants.compareVersions( cust_version, cust.getVersion() ) < 0;
				
				if ( new_name || new_version ){

					log( "Customization: checking templates for " + cust.getName() + "/" + cust.getVersion());
					
					try{
						InputStream[] streams = cust.getResources( Customization.RT_SUBSCRIPTIONS );
						
						for (int i=0;i<streams.length;i++){
							
							InputStream is = streams[i];
							
							try{
								VuzeFile vf = VuzeFileHandler.getSingleton().loadVuzeFile(is);
								
								if ( vf != null ){
									
									VuzeFileComponent[] comps = vf.getComponents();
									
									for (int j=0;j<comps.length;j++){
										
										VuzeFileComponent comp = comps[j];
										
										int type = comp.getType();
										
										if ( 	type == VuzeFileComponent.COMP_TYPE_SUBSCRIPTION ||
												type == VuzeFileComponent.COMP_TYPE_SUBSCRIPTION_SINGLETON	){
											
											try{
												importSubscription(
														type,
														comp.getContent(),
														false );
												
												comp.setProcessed();
												
											}catch( Throwable e ){
												
												Debug.printStackTrace(e);
											}
										}
									}
								}
							}finally{
								
								try{
									is.close();
									
								}catch( Throwable e ){
								}
							}
						}
					}finally{
						
						COConfigurationManager.setParameter( "subscriptions.custom.name", cust.getName());
						COConfigurationManager.setParameter( "subscriptions.custom.version", cust.getVersion());
					}
				}
			}
			
			scheduler = new SubscriptionSchedulerImpl( this );
		}
	}
	
	protected void
	initialise()
	{
		AzureusCoreFactory.addCoreRunningListener(new AzureusCoreRunningListener() {
			public void azureusCoreRunning(AzureusCore core) {
				initWithCore(core);
			}
		});
	}

	protected void
	initWithCore(
		AzureusCore 	core )
	{
		synchronized( this ){
			
			if ( started ){
				
				return;
			}
			
			started	= true;
		}

		final PluginInterface default_pi = PluginInitializer.getDefaultInterface();

		rss_publisher = new SubscriptionRSSFeed( this, default_pi );
		
		TorrentManager  tm = default_pi.getTorrentManager();
		
		ta_subs_download 		= tm.getPluginAttribute( "azsubs.subs_dl" );
		ta_subs_download_rd 	= tm.getPluginAttribute( "azsubs.subs_dl_rd" );
		ta_subscription_info 	= tm.getPluginAttribute( "azsubs.subs_info" );
		ta_category				= tm.getAttribute( TorrentAttribute.TA_CATEGORY );
		
		PluginInterface  dht_plugin_pi  = AzureusCoreFactory.getSingleton().getPluginManager().getPluginInterfaceByClass( DHTPlugin.class );
				
		if ( dht_plugin_pi != null ){
			
			dht_plugin = (DHTPlugin)dht_plugin_pi.getPlugin();

			/*
			if ( Constants.isCVSVersion()){
				
				addListener(
						new SubscriptionManagerListener()
						{
							public void 
							subscriptionAdded(
								Subscription subscription ) 
							{
							}
				
							public void
							subscriptionChanged(
								Subscription		subscription )
							{
							}
							
							public void 
							subscriptionRemoved(
								Subscription subscription ) 
							{
							}
							
							public void 
							associationsChanged(
								byte[] hash )
							{
								System.out.println( "Subscriptions changed: " + ByteFormatter.encodeString( hash ));
								
								Subscription[] subs = getKnownSubscriptions( hash );
							
								for (int i=0;i<subs.length;i++){
									
									System.out.println( "    " + subs[i].getString());
								}
							}
						});	
			}
			*/
			
			default_pi.getDownloadManager().addListener(
				new DownloadManagerListener()
				{
					public void
					downloadAdded(
						Download	download )
					{
						Torrent	torrent = download.getTorrent();
						
						if ( torrent != null ){
							
							byte[]	hash = torrent.getHash();
							
							Object[] entry;
							
							synchronized( potential_associations2 ){
								
								entry = (Object[])potential_associations2.remove( new HashWrapper( hash ));
							}
							
							if ( entry != null ){
								
								SubscriptionImpl[] subs = (SubscriptionImpl[])entry[0];
								
								String	subs_str = "";
								for (int i=0;i<subs.length;i++){
									subs_str += (i==0?"":",") + subs[i].getName();
								}
								
								log( "Applying deferred asocciation for " + ByteFormatter.encodeString( hash ) + " -> " + subs_str );
								
								recordAssociationsSupport(
									hash,
									subs,
									((Boolean)entry[1]).booleanValue());
							}
						}
					}
					
					public void
					downloadRemoved(
						Download	download )
					{	
					}
				},
				false );
			
			TorrentUtils.addTorrentAttributeListener(
				new TorrentUtils.torrentAttributeListener()
				{
					public void 
					attributeSet(
						TOTorrent 	torrent,
						String 		attribute, 
						Object 		value )
					{
						if ( attribute == TorrentUtils.TORRENT_AZ_PROP_OBTAINED_FROM ){
							
							try{
								checkPotentialAssociations( torrent.getHash(), (String)value );
								
							}catch( Throwable e ){
								
								Debug.printStackTrace(e);
							}
						}
					}
				});
				
			DelayedTask delayed_task = UtilitiesImpl.addDelayedTask( "Subscriptions", 
					new Runnable()
					{
						public void 
						run() 
						{
							new AEThread2( "Subscriptions:delayInit", true )
							{
								public void
								run()
								{
									asyncInit();
								}
							}.start();
							
						}
						
						protected void
						asyncInit()
						{
							Download[] downloads = default_pi.getDownloadManager().getDownloads();
									
							for (int i=0;i<downloads.length;i++){
								
								Download download = downloads[i];
								
								if ( download.getBooleanAttribute( ta_subs_download )){
									
									Map	rd = download.getMapAttribute( ta_subs_download_rd );
									
									boolean	delete_it;
									
									if ( rd == null ){
										
										delete_it = true;
										
									}else{
										
										delete_it = !recoverSubscriptionUpdate( download, rd );
									}
									
									if ( delete_it ){
										
										removeDownload( download, true );
									}
								}
							}
								
							default_pi.getDownloadManager().addListener(
								new DownloadManagerListener()
								{
									public void
									downloadAdded(
										final Download	download )
									{
											// if ever changed to handle non-persistent then you need to fix init deadlock
											// potential with share-hoster plugin
										
										if ( download.isPersistent()){
											
											if ( !dht_plugin.isInitialising()){
								
													// if new download then we want to check out its subscription status 
												
												lookupAssociations( download.getMapAttribute( ta_subscription_info ) == null );
												
											}else{
												
												new AEThread2( "Subscriptions:delayInit", true )
												{
													public void
													run()
													{
														lookupAssociations( download.getMapAttribute( ta_subscription_info ) == null );
													}
												}.start();
											}
										}
									}
									
									public void
									downloadRemoved(
										Download	download )
									{
									}
								},
								false );
							
							for (int i=0;i<PUB_ASSOC_CONC_MAX;i++){
							
								if ( publishAssociations()){
									
									break;
								}
							}
							
							publishSubscriptions();
							
							COConfigurationManager.addParameterListener(
									CONFIG_MAX_RESULTS,
									new ParameterListener()
									{
										public void 
										parameterChanged(
											String	 name )
										{
											final int	max_results = COConfigurationManager.getIntParameter( CONFIG_MAX_RESULTS );
											
											new AEThread2( "Subs:max results changer", true )
											{
												public void
												run()
												{
													checkMaxResults( max_results );
												}
											}.start();
										}
									});
							
							SimpleTimer.addPeriodicEvent(
									"SubscriptionChecker",
									TIMER_PERIOD,
									new TimerEventPerformer()
									{
										private int	ticks;
										
										public void 
										perform(
											TimerEvent event )
										{
											ticks++;
											
											checkStuff( ticks );
										}
									});
						}
					});
		
			delayed_task.queue();
		}
	}

	protected void
	checkMaxResults(
		int		max )
	{
		Subscription[] subs = getSubscriptions();
		
		for (int i=0;i<subs.length;i++){
			
			((SubscriptionHistoryImpl)subs[i].getHistory()).checkMaxResults( max );
		}
	}
	
	public SubscriptionScheduler
	getScheduler()
	{
		return( scheduler );
	}
	
	public boolean
	isRSSPublishEnabled()
	{
		return( COConfigurationManager.getBooleanParameter( RSS_ENABLE_CONFIG_KEY, false ));
	}
	
	public void
	setRSSPublishEnabled(
		boolean		enabled )
	{
		COConfigurationManager.setParameter( RSS_ENABLE_CONFIG_KEY, enabled );
	}
	
	public String
	getRSSLink()
	{
		return( rss_publisher.getFeedURL());
	}
	
	public Subscription 
	create(
		String			name,
		boolean			public_subs,
		String			json )
	
		throws SubscriptionException 
	{
		name = getUniqueName( name );
		
		SubscriptionImpl subs = new SubscriptionImpl( this, name, public_subs, null, json, SubscriptionImpl.ADD_TYPE_CREATE );
		
		log( "Created new subscription: " + subs.getString());
		
		if ( subs.isPublic()){
			
			updatePublicSubscription( subs );
		}
		
		return( addSubscription( subs ));
	}
	
	public Subscription
	createSingletonRSS(
		String		name,
		URL			url,
		int			check_interval_mins )
		
		throws SubscriptionException
	{
		return( createSingletonRSSSupport( name, url, true, check_interval_mins, SubscriptionImpl.ADD_TYPE_CREATE, true ));
	}
	
	protected SubscriptionImpl
	lookupSingletonRSS(
		String		name,
		URL			url,
		boolean		is_public,
		int			check_interval_mins )
		
		throws SubscriptionException
	{
		checkURL( url );
		
		Map	singleton_details = getSingletonMap(name, url, is_public, check_interval_mins);
		
		byte[] sid = SubscriptionBodyImpl.deriveSingletonShortID(singleton_details);
		
		return( getSubscriptionFromSID( sid ));
	}
	
			
	protected Subscription
	createSingletonRSSSupport(
		String		name,
		URL			url,
		boolean		is_public,
		int			check_interval_mins,
		int			add_type,
		boolean		subscribe )
		
		throws SubscriptionException
	{
		checkURL( url );
		
		try{
			Subscription existing = lookupSingletonRSS( name, url, is_public, check_interval_mins );
			
			if ( existing != null ){
				
				return( existing );
			}
			
			Engine engine = MetaSearchManagerFactory.getSingleton().getMetaSearch().createRSSEngine( name, url );
			
			String	json = SubscriptionImpl.getSkeletonJSON( engine, check_interval_mins );
			
			Map	singleton_details = getSingletonMap(name, url, is_public, check_interval_mins);
			
			SubscriptionImpl subs = new SubscriptionImpl( this, name, is_public, singleton_details, json, add_type );
			
			subs.setSubscribed( subscribe );
			
			log( "Created new singleton subscription: " + subs.getString());
							
			subs = addSubscription( subs );
			
			return( subs );
			
		}catch( SubscriptionException e ){
			
			throw((SubscriptionException)e);
			
		}catch( Throwable e ){
			
			throw( new SubscriptionException( "Failed to create subscription", e ));
		}
	}
	
	protected String
	getUniqueName(
		String	name )
	{
		for ( int i=0;i<1024;i++){
			
			String	test_name = name + (i==0?"":(" (" + i + ")"));
			
			if ( getSubscriptionFromName( test_name ) == null ){

				return( test_name );
			}
		}
		
		return( name );
	}
	
	protected Map
	getSingletonMap(
		String		name,
		URL			url,
		boolean		is_public,
		int			check_interval_mins )
	
		throws SubscriptionException
	{
		try{
			Map	singleton_details = new HashMap();
			
			singleton_details.put( "key", url.toExternalForm().getBytes( "UTF-8" ));
						
			String	name2 = name.length() > 64?name.substring(0,64):name;
			
			singleton_details.put( "name", name2 );
			
			if ( check_interval_mins != SubscriptionHistoryImpl.DEFAULT_CHECK_INTERVAL_MINS ){
				
				singleton_details.put( "ci", new Long( check_interval_mins ));
			}
			
			return( singleton_details );
		
		}catch( Throwable e ){
			
			throw( new SubscriptionException( "Failed to create subscription", e ));
		}
	}
	
	protected SubscriptionImpl
	createSingletonSubscription(
		Map			singleton_details,
		int			add_type,
		boolean		subscribe )
	
		throws SubscriptionException 
	{
		try{
			String name = ImportExportUtils.importString( singleton_details, "name", "(Anonymous)" );
			
			URL	url = new URL( ImportExportUtils.importString( singleton_details, "key" ));
			
			int	check_interval_mins = (int)ImportExportUtils.importLong( singleton_details, "ci", SubscriptionHistoryImpl.DEFAULT_CHECK_INTERVAL_MINS );
			
				// only defined type is singleton rss
			
			SubscriptionImpl s = (SubscriptionImpl)createSingletonRSSSupport( name, url, true, check_interval_mins, add_type, subscribe );
			
			return( s );
			
		}catch( Throwable e ){
			
			log( "Creation of singleton from " + singleton_details + " failed", e );
			
			throw( new SubscriptionException( "Creation of singleton from " + singleton_details + " failed", e ));
		}
	}
	
	public Subscription 
	createRSS(
		String		name,
		URL			url,
		int			check_interval_mins,
		Map			user_data )
	
		throws SubscriptionException 
	{
		checkURL( url );
		
		try{
			name = getUniqueName(name);
		
			Engine engine = MetaSearchManagerFactory.getSingleton().getMetaSearch().createRSSEngine( name, url );
			
			String	json = SubscriptionImpl.getSkeletonJSON( engine, check_interval_mins );
			
				// engine name may have been modified so re-read it for subscription default
			
			SubscriptionImpl subs = new SubscriptionImpl( this, engine.getName(), engine.isPublic(), null, json, SubscriptionImpl.ADD_TYPE_CREATE );
			
			if ( user_data != null ){
				
				Iterator it = user_data.entrySet().iterator();
				
				while( it.hasNext()){
					
					Map.Entry entry = (Map.Entry)it.next();
					
					subs.setUserData( entry.getKey(), entry.getValue());
				}
			}
			
			log( "Created new subscription: " + subs.getString());
					
			subs = addSubscription( subs );
			
			if ( subs.isPublic()){
			
				updatePublicSubscription( subs );
			}
			
			return( subs );
			
		}catch( Throwable e ){
			
			throw( new SubscriptionException( "Failed to create subscription", e ));
		}
	}

	protected void
	checkURL(
		URL		url )
	
		throws SubscriptionException
	{
		if ( url.getHost().trim().length() == 0 ){
			
			String protocol = url.getProtocol().toLowerCase();
			
			if ( ! ( protocol.equals( "azplug" ) || protocol.equals( "file" ))){
			
				throw( new SubscriptionException( "Invalid URL '" + url + "'" ));
			}
		}
	}
	
	protected SubscriptionImpl
	addSubscription(
		SubscriptionImpl		subs )
	{
		SubscriptionImpl existing;
		
		synchronized( this ){
			
			existing = getSubscriptionFromSID( subs.getShortID());
			
			if ( existing == null ){

				subscriptions.add( subs );
			
				saveConfig();
			}
		}
		
		if ( existing != null ){
			
			log( "Attempted to add subscription when already present: " + subs.getString());
			
			subs.destroy();
			
			return( existing );
		}
		
		if ( subs.isMine()){
			
			addMetaSearchListener();
		}
		
		if ( subs.getCachedPopularity() == -1 ){
			
			try{
				subs.getPopularity(
					new SubscriptionPopularityListener()
					{
						public void
						gotPopularity(
							long						popularity )
						{
						}
						
						public void
						failed(
							SubscriptionException		error )
						{
						}
					});
				
			}catch( Throwable e ){
				
				log( "", e );
			}
		}
		
		Iterator it = listeners.iterator();
		
		while( it.hasNext()){
			
			try{
				((SubscriptionManagerListener)it.next()).subscriptionAdded( subs );
				
			}catch( Throwable e ){
				
				Debug.printStackTrace(e);
			}
		}
		
		if ( subs.isSubscribed() && subs.isPublic()){
			
			setSelected( subs );
		}
		
		if ( dht_plugin != null ){
				
			new AEThread2( "Publish check", true )
			{
				public void
				run()
				{
					publishSubscriptions();
				}
			}.start();
		}
		
		return( subs );
	}
	
	protected void
	addMetaSearchListener()
	{
		synchronized( this ){
			
			if ( meta_search_listener_added ){
				
				return;
			}
			
			meta_search_listener_added = true;
		}
		
		MetaSearchManagerFactory.getSingleton().getMetaSearch().addListener(
			new MetaSearchListener()
			{
				public void
				engineAdded(
					Engine		engine )
				{
					
				}
				
				public void
				engineUpdated(
					Engine		engine )
				{
					synchronized( this ){
						
						for (int i=0;i<subscriptions.size();i++){
							
							SubscriptionImpl	subs = (SubscriptionImpl)subscriptions.get(i);
							
							if ( subs.isMine()){
								
								subs.engineUpdated( engine );
							}
						}
					}
				}
				
				public void
				engineRemoved(
					Engine		engine )
				{
					
				}
			});
	}
	
	protected void
	changeSubscription(
		SubscriptionImpl		subs )
	{
		if ( !subs.isRemoved()){
			
			Iterator it = listeners.iterator();
			
			while( it.hasNext()){
				
				try{
					((SubscriptionManagerListener)it.next()).subscriptionChanged( subs );
					
				}catch( Throwable e ){
					
					Debug.printStackTrace(e);
				}
			}
		}
	}
	
	protected void
	selectSubscription(
		SubscriptionImpl		subs )
	{
		if ( !subs.isRemoved()){

			Iterator it = listeners.iterator();
			
			while( it.hasNext()){
				
				try{
					((SubscriptionManagerListener)it.next()).subscriptionSelected( subs );
					
				}catch( Throwable e ){
					
					Debug.printStackTrace(e);
				}
			}
		}
	}
	
	protected void
	removeSubscription(
		SubscriptionImpl		subs )
	{
		synchronized( this ){
			
			if ( subscriptions.remove( subs )){
			
				saveConfig();
				
				try{
					getResultsFile( subs ).delete();
					
				}catch( Throwable e ){
					
					log( "Failed to delete results file", e );
				}
			}else{
			
				return;
			}
		}
		
		try{
			Engine engine = subs.getEngine( true );
			
			if ( engine.getType() == Engine.ENGINE_TYPE_RSS ){
				
				engine.delete();
				
				log( "Removed engine " + engine.getName() + " due to subscription removal" );
			}
			
		}catch( Throwable e ){
			
			log( "Failed to check for engine deletion", e );
		}
		
		Iterator it = listeners.iterator();
		
		while( it.hasNext()){
			
			try{
				((SubscriptionManagerListener)it.next()).subscriptionRemoved( subs );
				
			}catch( Throwable e ){
				
				Debug.printStackTrace(e);
			}
		}
	}
	
	protected void
	updatePublicSubscription(
		SubscriptionImpl		subs )
	{		
		if ( subs.isSingleton()){
			
				// never update singletons
			
			subs.setServerPublished();
			
		}else{
			
			Long	l_last_pub 	= (Long)subs.getUserData( SP_LAST_ATTEMPTED );
			Long	l_consec_fail = (Long)subs.getUserData( SP_CONSEC_FAIL );
			
			if ( l_last_pub != null && l_consec_fail != null ){
				
				long	delay = SERVER_PUB_CHECK_PERIOD;
				
				for (int i=0;i<l_consec_fail.longValue();i++){
					
					delay <<= 1;
					
					if ( delay > 24*60*60*1000 ){
						
						break;
					}
				}
				
				if ( l_last_pub.longValue() + delay > SystemTime.getMonotonousTime()){
					
					return;
				}
			}
			
			try{		
				File vf = getVuzeFile( subs );
	
				byte[] bytes = FileUtil.readFileAsByteArray( vf );
				
				byte[]	encoded_subs = Base64.encode( bytes );
	
				PlatformSubscriptionsMessenger.updateSubscription(
						!subs.getServerPublished(),
						subs.getName(),
						subs.getPublicKey(),
						subs.getPrivateKey(),
						subs.getShortID(),
						subs.getVersion(),
						new String( encoded_subs ));
				
				subs.setUserData( SP_LAST_ATTEMPTED, null );
				subs.setUserData( SP_CONSEC_FAIL, null );

				subs.setServerPublished();
				
				log( "    Updated public subscription " + subs.getString());
				
			}catch( Throwable e ){
				
				log( "    Failed to update public subscription " + subs.getString(), e );
				
				subs.setUserData( SP_LAST_ATTEMPTED, new Long( SystemTime.getMonotonousTime()));
				
				subs.setUserData( SP_CONSEC_FAIL, new Long( l_consec_fail==null?1:(l_consec_fail.longValue()+1)));

				subs.setServerPublicationOutstanding();
			}
		}
	}
	
	protected void
	checkSingletonPublish(
		SubscriptionImpl		subs )
	
		throws SubscriptionException
	{
		if ( subs.getSingletonPublishAttempted()){
			
			throw( new SubscriptionException( "Singleton publish already attempted" ));
		}
		
		subs.setSingletonPublishAttempted();
		
		try{
			File vf = getVuzeFile( subs );
	
			byte[] bytes = FileUtil.readFileAsByteArray( vf );
			
			byte[]	encoded_subs = Base64.encode( bytes );
			
				// use a transient key-pair as we won't have the private key in general
			
			KeyPair	kp = CryptoECCUtils.createKeys();
			
			byte[] public_key 		= CryptoECCUtils.keyToRawdata( kp.getPublic());
			byte[] private_key 		= CryptoECCUtils.keyToRawdata( kp.getPrivate());
	
			PlatformSubscriptionsMessenger.updateSubscription(
					true,
					subs.getName(),
					public_key,
					private_key,
					subs.getShortID(),
					1,
					new String( encoded_subs ));
			
			log( "    created singleton public subscription " + subs.getString());
			
		}catch( Throwable e ){
			
			throw( new SubscriptionException( "Failed to publish singleton", e ));
		}
	}
	
	protected void
	checkServerPublications(
		List		subs )
	{
		for (int i=0;i<subs.size();i++){
			
			SubscriptionImpl	sub = (SubscriptionImpl)subs.get(i);
			
			if ( sub.getServerPublicationOutstanding()){
				
				updatePublicSubscription( sub );
			}
		}
	}
	
	protected void
	checkStuff(
		int		ticks )
	{
		List subs;
		
		synchronized( this ){
			
			subs = new ArrayList( subscriptions );
		}
		
		for (int i=0;i<subs.size();i++){
			
			((SubscriptionImpl)subs.get(i)).checkPublish();
		}
		
		if ( ticks % ASSOC_CHECK_TICKS == 0 ){
			
			lookupAssociations( false );
		}
		
		if ( ticks % SERVER_PUB_CHECK_TICKS == 0 ){
			
			checkServerPublications( subs );
		}
		
		if ( ticks % TIDY_POT_ASSOC_TICKS == 0 ){
			
			tidyPotentialAssociations();
		}
		
		if ( 	ticks == SET_SELECTED_FIRST_TICK ||
				ticks % SET_SELECTED_TICKS == 0 ){
			
			setSelected( subs );
		}
	}
	
	public Subscription
	importSubscription(
		int			type,
		Map			map,
		boolean		warn_user )
	
		throws SubscriptionException
	{
		boolean	log_errors = true;
		
		try{
			try{
				if ( type == VuzeFileComponent.COMP_TYPE_SUBSCRIPTION_SINGLETON ){
					
					String	name = new String((byte[])map.get( "name" ), "UTF-8" );
					
					URL	url = new URL( new String((byte[])map.get( "url" ), "UTF-8" ));
					
					Long	l_interval = (Long)map.get( "check_interval_mins" );
					
					int	check_interval_mins = l_interval==null?SubscriptionHistoryImpl.DEFAULT_CHECK_INTERVAL_MINS:l_interval.intValue();
					
					Long	l_public = (Long)map.get( "public" );
					
					boolean is_public = l_public==null?true:l_public.longValue()==1;
					
					SubscriptionImpl existing = lookupSingletonRSS(name, url, is_public, check_interval_mins );
					
					if ( UrlFilter.getInstance().urlCanRPC( url.toExternalForm())){
						
						warn_user = false;
					}
					
					if ( existing != null && existing.isSubscribed()){
						
						if ( warn_user ){
							
							UIManager ui_manager = StaticUtilities.getUIManager( 120*1000 );
							
							String details = MessageText.getString(
									"subscript.add.dup.desc",
									new String[]{ existing.getName()});
							
							ui_manager.showMessageBox(
									"subscript.add.dup.title",
									"!" + details + "!",
									UIManagerEvent.MT_OK );
						}
						
						selectSubscription( existing );
						
						return( existing );
						
					}else{
						
						if ( warn_user ){
							
							UIManager ui_manager = StaticUtilities.getUIManager( 120*1000 );
				
							String details = MessageText.getString(
									"subscript.add.desc",
									new String[]{ name });
							
							long res = ui_manager.showMessageBox(
									"subscript.add.title",
									"!" + details + "!",
									UIManagerEvent.MT_YES | UIManagerEvent.MT_NO );
							
							if ( res != UIManagerEvent.MT_YES ){	
							
								log_errors = false;
								
								throw( new SubscriptionException( "User declined addition" ));
							}
						}
						
						if ( existing == null ){
					
							SubscriptionImpl new_subs = (SubscriptionImpl)createSingletonRSSSupport( name, url, is_public, check_interval_mins, SubscriptionImpl.ADD_TYPE_IMPORT, true );
																	
							log( "Imported new singleton subscription: " + new_subs.getString());
									
							return( new_subs );
							
						}else{
							
							existing.setSubscribed( true );
							
							selectSubscription( existing );
							
							return( existing );
						}
					}
				}else{
					
					SubscriptionBodyImpl body = new SubscriptionBodyImpl( this, map );
							
					SubscriptionImpl existing = getSubscriptionFromSID( body.getShortID());
					
					if ( existing != null && existing.isSubscribed()){
					
						if ( existing.getVersion() >= body.getVersion()){
							
							log( "Not upgrading subscription: " + existing.getString() + " as supplied (" +  body.getVersion() + ") is not more recent than existing (" + existing.getVersion() + ")");
							
							if ( warn_user ){
								
								UIManager ui_manager = StaticUtilities.getUIManager( 120*1000 );
								
								String details = MessageText.getString(
										"subscript.add.dup.desc",
										new String[]{ existing.getName()});
								
								ui_manager.showMessageBox(
										"subscript.add.dup.title",
										"!" + details + "!",
										UIManagerEvent.MT_OK );
							}
								// we have a newer one, ignore
							
							selectSubscription( existing );
							
							return( existing );
							
						}else{
							
							if ( warn_user ){
								
								UIManager ui_manager = StaticUtilities.getUIManager( 120*1000 );
				
								String details = MessageText.getString(
										"subscript.add.upgrade.desc",
										new String[]{ existing.getName()});
								
								long res = ui_manager.showMessageBox(
										"subscript.add.upgrade.title",
										"!" + details + "!",
										UIManagerEvent.MT_YES | UIManagerEvent.MT_NO );
								
								if ( res != UIManagerEvent.MT_YES ){	
								
									throw( new SubscriptionException( "User declined upgrade" ));
								}
							}
							
							log( "Upgrading subscription: " + existing.getString());
			
							existing.upgrade( body );
							
							saveConfig();
							
							subscriptionUpdated();
							
							return( existing );
						}
					}else{
						
						SubscriptionImpl new_subs = new SubscriptionImpl( this, body, SubscriptionImpl.ADD_TYPE_IMPORT, true );
			
						if ( warn_user ){
							
							UIManager ui_manager = StaticUtilities.getUIManager( 120*1000 );
				
							String details = MessageText.getString(
									"subscript.add.desc",
									new String[]{ new_subs.getName()});
							
							long res = ui_manager.showMessageBox(
									"subscript.add.title",
									"!" + details + "!",
									UIManagerEvent.MT_YES | UIManagerEvent.MT_NO );
							
							if ( res != UIManagerEvent.MT_YES ){	
							
								throw( new SubscriptionException( "User declined addition" ));
							}
						}
						
						log( "Imported new subscription: " + new_subs.getString());
						
						if ( existing != null ){
							
							existing.remove();
						}
						
						new_subs = addSubscription( new_subs );
						
						return( new_subs );
					}
				}
			}catch( Throwable e ){
				
				throw( new SubscriptionException( "Subscription import failed", e ));
			}
		}catch( SubscriptionException e ){
			
			if ( warn_user && log_errors ){
				
				UIManager ui_manager = StaticUtilities.getUIManager( 120*1000 );
				
				String details = MessageText.getString(
						"subscript.import.fail.desc",
						new String[]{ Debug.getNestedExceptionMessage(e)});
				
				ui_manager.showMessageBox(
						"subscript.import.fail.title",
						"!" + details + "!",
						UIManagerEvent.MT_OK );
			}
			
			throw( e );
		}
	}
	
	public Subscription[]
	getSubscriptions()
	{
		synchronized( this ){
			
			return((SubscriptionImpl[])subscriptions.toArray( new SubscriptionImpl[subscriptions.size()]));
		}
	}
	
	public Subscription[]
	getSubscriptions(
		boolean	subscribed_only )
	{
		if ( !subscribed_only ){
			
			return( getSubscriptions());
		}
		
		List	result = new ArrayList();
		
		synchronized( this ){
			
			for (int i=0;i<subscriptions.size();i++){
				
				SubscriptionImpl subs = (SubscriptionImpl)subscriptions.get(i);
				
				if ( subs.isSubscribed()){
					
					result.add( subs );
				}
			}
		}
		
		return((SubscriptionImpl[])result.toArray( new SubscriptionImpl[result.size()]));
	}
	
	protected SubscriptionImpl
	getSubscriptionFromName(
		String		name )
	{
		synchronized( this ){
			
			for (int i=0;i<subscriptions.size();i++){
				
				SubscriptionImpl s = (SubscriptionImpl)subscriptions.get(i);
				
				if ( s.getName().equalsIgnoreCase( name )){
					
					return( s );
				}
			}
		}
		
		return( null );
	}
	
	public Subscription
	getSubscriptionByID(
		String		id )
	{
		return( getSubscriptionFromSID( Base32.decode( id )));
	}
	
	protected SubscriptionImpl
	getSubscriptionFromSID(
		byte[]		sid )
	{
		synchronized( this ){
			
			for (int i=0;i<subscriptions.size();i++){
				
				SubscriptionImpl s = (SubscriptionImpl)subscriptions.get(i);
				
				if ( Arrays.equals( s.getShortID(), sid )){
					
					return( s );
				}
			}
		}
		
		return( null );
	}
	
	protected File
	getSubsDir()
	
		throws IOException
	{
		File dir = new File(SystemProperties.getUserPath());

		dir = new File( dir, "subs" );
 		
 		if ( !dir.exists()){
 			
 			if ( !dir.mkdirs()){
 				
 				throw( new IOException( "Failed to create '" + dir + "'" ));
 			}
 		}	
 		
 		return( dir );
	}
	
	protected File
	getVuzeFile(
		SubscriptionImpl 		subs )
	
		throws IOException
	{
 		File dir = getSubsDir();
 		
 		return( new File( dir, ByteFormatter.encodeString( subs.getShortID()) + ".vuze" ));
	}
	
	protected File
	getResultsFile(
		SubscriptionImpl 		subs )
	
		throws IOException
	{
 		File dir = getSubsDir();
 		
 		return( new File( dir, ByteFormatter.encodeString( subs.getShortID()) + ".results" ));
	}
	
	public Subscription[]
	getKnownSubscriptions(
		byte[]						hash )
	{
		PluginInterface pi = PluginInitializer.getDefaultInterface();

		try{
			Download download = pi.getDownloadManager().getDownload( hash );
			
			if ( download != null ){
				
				Map	m = download.getMapAttribute( ta_subscription_info );
				
				if ( m != null ){
					
					List s = (List)m.get("s");
					
					if ( s != null && s.size() > 0 ){
						
						List	result = new ArrayList( s.size());
						
						for (int i=0;i<s.size();i++){
							
							byte[]	sid = (byte[])s.get(i);
							
							SubscriptionImpl subs = getSubscriptionFromSID(sid);
							
							if ( subs != null ){
								
								if ( isVisible( subs )){
								
									result.add( subs );
								}
							}
						}
						
						return((Subscription[])result.toArray( new Subscription[result.size()]));
					}
				}
			}
		}catch( Throwable e ){
			
			log( "Failed to get known subscriptions", e );
		}
		
		return( new Subscription[0] );
	}
	
	protected boolean
	subscriptionExists(
		Download			download,
		SubscriptionImpl	subs )
	{
		byte[]	sid = subs.getShortID();
	
		Map	m = download.getMapAttribute( ta_subscription_info );
		
		if ( m != null ){
			
			List s = (List)m.get("s");
			
			if ( s != null && s.size() > 0 ){
								
				for (int i=0;i<s.size();i++){
					
					byte[]	x = (byte[])s.get(i);
					
					if ( Arrays.equals( x, sid )){
						
						return( true );
					}
				}
			}
		}
		
		return( false );
	}
	
	protected boolean
	isVisible(
		SubscriptionImpl		subs )
	{
			// to avoid development links polluting production we filter out such subscriptions
		
		if ( Constants.isCVSVersion() || subs.isSubscribed()){
			
			return( true );
		}
		
		try{
			Engine engine = subs.getEngine( true );
			
			if ( engine instanceof WebEngine ){
				
				String url = ((WebEngine)engine).getSearchUrl();
				
				try{
					String host = new URL( url ).getHost();
					
					return( !exclusion_pattern.matcher( host ).matches());
					
				}catch( Throwable e ){
				}
			}
			
			return( true );
			
		}catch( Throwable e ){
			
			log( "isVisible failed for " + subs.getString(), e );
			
			return( false );
		}
	}
		
	public Subscription[]
	getLinkedSubscriptions(
		byte[]						hash )
	{
		PluginInterface pi = PluginInitializer.getDefaultInterface();

		try{
			Download download = pi.getDownloadManager().getDownload( hash );
			
			if ( download != null ){
				
				Map	m = download.getMapAttribute( ta_subscription_info );
				
				if ( m != null ){
					
					List s = (List)m.get("s");
					
					if ( s != null && s.size() > 0 ){
						
						List	result = new ArrayList( s.size());
						
						for (int i=0;i<s.size();i++){
							
							byte[]	sid = (byte[])s.get(i);
							
							SubscriptionImpl subs = getSubscriptionFromSID(sid);
							
							if ( subs != null ){
								
								if ( subs.hasAssociation( hash )){
								
									result.add( subs );
								}
							}
						}
						
						return((Subscription[])result.toArray( new Subscription[result.size()]));
					}
				}
			}
		}catch( Throwable e ){
			
			log( "Failed to get known subscriptions", e );
		}
		
		return( new Subscription[0] );
	}
	
	protected void
	lookupAssociations(
		boolean		high_priority )
	{
		synchronized( this ){
			
			if ( periodic_lookup_in_progress ){
				
				if ( high_priority ){
				
					priority_lookup_pending++;
				}
				
				return;
			}
			
			periodic_lookup_in_progress  = true;
		}
		
		try{
			PluginInterface pi = PluginInitializer.getDefaultInterface();
			
			Download[] downloads = pi.getDownloadManager().getDownloads();
					
			long	now = SystemTime.getCurrentTime();
			
			long		newest_time		= 0;
			Download	newest_download	= null;
			
			
			for( int i=0;i<downloads.length;i++){
				
				Download	download = downloads[i];
				
				if ( download.getTorrent() == null || !download.isPersistent()){
					
					continue;
				}
				
				Map	map = download.getMapAttribute( ta_subscription_info );
				
				if ( map == null ){
					
					map = new LightHashMap();
					
				}else{
					
					map = new LightHashMap( map );
				}	
				
				Long	l_last_check = (Long)map.get( "lc" );
				
				long	last_check = l_last_check==null?0:l_last_check.longValue();
				
				if ( last_check > now ){
					
					last_check = now;
					
					map.put( "lc", new Long( last_check ));
					
					download.setMapAttribute( ta_subscription_info, map );
				}
				
				List	subs = (List)map.get( "s" );
				
				int	sub_count = subs==null?0:subs.size();
				
				if ( sub_count > 8 ){
					
					continue;
				}
				
				long	create_time = download.getCreationTime();
				
				int	time_between_checks = (sub_count + 1) * 24*60*60*1000 + (int)(create_time%4*60*60*1000);
				
				if ( now - last_check >= time_between_checks ){
					
					if ( create_time > newest_time ){
						
						newest_time		= create_time;
						newest_download	= download;
					}
				}
			}
			
			if ( newest_download != null ){
			
				byte[] hash = newest_download.getTorrent().getHash();
				
				log( "Association lookup starts for " + newest_download.getName() + "/" + ByteFormatter.encodeString( hash ));

				lookupAssociationsSupport( 
					hash,
					new	SubscriptionLookupListener()
					{
						public void
						found(
							byte[]					hash,
							Subscription			subscription )
						{							
						}
						
						public void
						failed(
							byte[]					hash,
							SubscriptionException	error )
						{
							log( "Association lookup failed for " + ByteFormatter.encodeString( hash ), error );

							associationLookupComplete();
						}
						
						public void 
						complete(
							byte[] 			hash,
							Subscription[]	subs )
						{
							log( "Association lookup complete for " + ByteFormatter.encodeString( hash ));
							
							associationLookupComplete();
						}
					});
						
			}else{
				
				associationLookupComplete();
			}
		}catch( Throwable e ){
			
			log( "Association lookup check failed", e );

			associationLookupComplete();			
		}
	}
	
	protected void
	associationLookupComplete()
	{
		boolean	recheck;
		
		synchronized( SubscriptionManagerImpl.this ){
			
			periodic_lookup_in_progress = false;
			
			recheck = priority_lookup_pending > 0;
				
			if ( recheck ){
				
				priority_lookup_pending--;
			}
		}
		
		if ( recheck ){
			
			new AEThread2( "SM:priAssLookup", true )
			{
				public void run() 
				{
					lookupAssociations( false );
				}
			}.start();
		}
	}
	
	protected void
	setSelected(
		List		subs )
	{
		List	sids 		= new ArrayList();
		List	used_subs	= new ArrayList();
		
		for (int i=0;i<subs.size();i++){
			
			SubscriptionImpl	sub = (SubscriptionImpl)subs.get(i);
			
			if ( sub.isSubscribed()){
				
				if ( sub.isPublic()){
				
					used_subs.add( sub );
				
					sids.add( sub.getShortID());
					
				}else{
					
					checkInitialDownload( sub );
				}
			}
		}
		
		if ( sids.size() > 0 ){
			
			try{
				List[] result = PlatformSubscriptionsMessenger.setSelected( sids );
				
				List	versions 		= result[0];
				List	popularities	= result[1];
				
				log( "Popularity update: updated " + sids.size());
				
				final List dht_pops = new ArrayList();
				
				for (int i=0;i<sids.size();i++){
					
					SubscriptionImpl sub = (SubscriptionImpl)used_subs.get(i);
					
					int	latest_version = ((Long)versions.get(i)).intValue();
					
					if ( latest_version > sub.getVersion()){
						
						updateSubscription( sub, latest_version );
						
					}else{
						
						checkInitialDownload( sub );
					}
					
					if ( latest_version > 0 ){
						
						try{
							long	pop = ((Long)popularities.get(i)).longValue();
							
							if ( pop >= 0 && pop != sub.getCachedPopularity()){
								
								sub.setCachedPopularity( pop );
							}
						}catch( Throwable e ){
							
							log( "Popularity update: Failed to extract popularity", e );
						}
					}else{
						
						dht_pops.add( sub );
					}
				}
				
				if ( dht_pops.size() <= 3 ){
					
					for (int i=0;i<dht_pops.size();i++){
							
						updatePopularityFromDHT((SubscriptionImpl)dht_pops.get(i), false );
					}
				}else{
					
					new AEThread2( "SM:asyncPop", true )
					{
						public void
						run()
						{
							for (int i=0;i<dht_pops.size();i++){
								
								updatePopularityFromDHT((SubscriptionImpl)dht_pops.get(i), true );
							}
						}
					}.start();
				}
			}catch( Throwable e ){
				
				log( "Popularity update: Failed to record selected subscriptions", e );
			}
		}else{
			
			log( "Popularity update: No selected, public subscriptions" );
		}
	}
	
	protected void
	checkUpgrade(
		SubscriptionImpl		sub )
	{
		setSelected( sub );
	}
	
	protected void
	setSelected(
		final SubscriptionImpl	sub )
	{
		if ( sub.isSubscribed()){
			
			if ( sub.isPublic()){
			
				new DelayedEvent( 
					"SM:setSelected",
					0,
					new AERunnable()
					{
						public void
						runSupport()
						{
							try{
								List	sids = new ArrayList();
								
								sids.add( sub.getShortID());
							
								List[] result = PlatformSubscriptionsMessenger.setSelected( sids );
								
								log( "setSelected: " + sub.getName());
								
								int	latest_version = ((Long)result[0].get(0)).intValue();
								
								if ( latest_version == 0 ){
									
									if ( sub.isSingleton()){
									
										checkSingletonPublish( sub );
									}
								}else if ( latest_version > sub.getVersion()){
									
									updateSubscription( sub, latest_version );
									
								}else{
									
									checkInitialDownload( sub );
								}
								
								if ( latest_version > 0 ){
									
									try{
										long	pop = ((Long)result[1].get(0)).longValue();
										
										if ( pop >= 0 && pop != sub.getCachedPopularity()){
											
											sub.setCachedPopularity( pop );
										}
									}catch( Throwable e ){
										
										log( "Popularity update: Failed to extract popularity", e );
									}
								}else{
									
									updatePopularityFromDHT( sub, true );
								}
							}catch( Throwable e ){
								
								log( "setSelected: failed for " + sub.getName(), e );
							}
						}
					});
			}else{
				
				checkInitialDownload( sub );
			}
		}
	}
	
	protected void
	checkInitialDownload(
		SubscriptionImpl		subs )
	{
		if ( subs.getHistory().getLastScanTime() == 0 ){
			
			scheduler.download( 
				subs, 
				true,
				new SubscriptionDownloadListener()
				{
					public void
					complete(
						Subscription		subs )
					{
						log( "Initial download of " + subs.getName() + " complete" );
					}
					
					public void
					failed(
						Subscription			subs,
						SubscriptionException	error )
					{
						log( "Initial download of " + subs.getName() + " failed", error );
					}
				});
		}
	}
	
	public SubscriptionAssociationLookup
	lookupAssociations(
		final byte[] 							hash,
		final SubscriptionLookupListener		listener )
	
		throws SubscriptionException
	{
		if ( dht_plugin != null && !dht_plugin.isInitialising()){
			
			return( lookupAssociationsSupport( hash, listener ));
		}
		
		final boolean[]	cancelled = { false };
		
		final SubscriptionAssociationLookup[]	actual_res = { null };
		
		final SubscriptionAssociationLookup res = 
			new SubscriptionAssociationLookup()
			{
				public void 
				cancel() 
				{
					log( "    Association lookup cancelled" );
	
					synchronized( actual_res ){
						
						cancelled[0] = true;
						
						if ( actual_res[0] != null ){
							
							actual_res[0].cancel();
						}
					}
				}
			};
			
		new AEThread2( "SM:initwait", true )
		{
			public void
			run()
			{
				try{
					SubscriptionAssociationLookup x = lookupAssociationsSupport( hash, listener );
					
					synchronized( actual_res ){

						actual_res[0] = x;
						
						if ( cancelled[0] ){
							
							x.cancel();
						}
					}
					
				}catch( SubscriptionException e ){
					
					listener.failed( hash, e );
				}
				
			}
		}.start();
		
		return( res );
	}
	
	protected SubscriptionAssociationLookup
	lookupAssociationsSupport(
		final byte[] 							hash,
		final SubscriptionLookupListener		listener )
	
		throws SubscriptionException 
	{
		log( "Looking up associations for '" + ByteFormatter.encodeString( hash ));
		
		final String	key = "subscription:assoc:" + ByteFormatter.encodeString( hash ); 
			
		final boolean[]	cancelled = { false };
		
		dht_plugin.get(
			key.getBytes(),
			"Subscription association read: " + ByteFormatter.encodeString( hash ),
			DHTPlugin.FLAG_SINGLE_VALUE,
			30,
			60*1000,
			true,
			true,
			new DHTPluginOperationListener()
			{
				private Map<HashWrapper,Integer>	hits 					= new HashMap<HashWrapper, Integer>();
				private AESemaphore					hits_sem				= new AESemaphore( "Subs:lookup" );
				private List<Subscription>			found_subscriptions 	= new ArrayList<Subscription>();
				
				private boolean	complete;
				
				public void
				diversified()
				{
				}
				
				public void 
				starts(
					byte[] 				key ) 
				{
				}
				
				public void
				valueRead(
					DHTPluginContact	originator,
					DHTPluginValue		value )
				{
					if ( isCancelled2()){
						
						return;
					}
					
					byte[]	val = value.getValue();
					
					if ( val.length > 4 ){
						
						int	ver = ((val[0]<<16)&0xff0000) | ((val[1]<<8)&0xff00) | (val[2]&0xff);

						byte[]	sid = new byte[ val.length - 4 ];
						
						System.arraycopy( val, 4, sid, 0, sid.length );
												
						HashWrapper hw = new HashWrapper( sid );
						
						boolean	new_sid = false;
						
						synchronized( this ){
							
							if ( complete ){
								
								return;
							}
							
							Integer v = (Integer)hits.get(hw);
							
							if ( v != null ){
								
								if ( ver > v.intValue()){
									
									hits.put( hw, new Integer( ver ));								
								}
							}else{
								
								new_sid = true;
																
								hits.put( hw, new Integer( ver ));
							}
						}
						
						if ( new_sid ){
							
							log( "    Found subscription " + ByteFormatter.encodeString( sid ) + " version " + ver );

								// check if already subscribed
							
							SubscriptionImpl subs = getSubscriptionFromSID( sid );
							
							if ( subs != null ){
								
								found_subscriptions.add( subs );
								
								try{
									listener.found( hash, subs );
									
								}catch( Throwable e ){
									
									Debug.printStackTrace(e);
								}
																
								hits_sem.release();
								
							}else{
								
								lookupSubscription( 
									hash, 
									sid, 
									ver,
									new subsLookupListener()
									{
										private boolean sem_done = false;
										
										public void
										found(
											byte[]					hash,
											Subscription			subscription )
										{
										}
										
										public void
										complete(
											byte[]					hash,
											Subscription[]			subscriptions )
										{
											done( subscriptions );
										}
										
										public void
										failed(
											byte[]					hash,
											SubscriptionException	error )
										{
											done( new Subscription[0]);
										}
										
										protected void
										done(
											Subscription[]			subs )
										{
											if ( isCancelled()){
												
												return;
											}
											
											synchronized( this ){
												
												if ( sem_done ){
													
													return;
												}
												
												sem_done = true;
											}
											
											if ( subs.length > 0 ){
												
												found_subscriptions.add( subs[0] );
												
												try{
													listener.found( hash, subs[0] );
													
												}catch( Throwable e ){
													
													Debug.printStackTrace(e);
												}
											}
																						
											hits_sem.release();
										}
										
										public boolean 
										isCancelled() 
										{
											return( isCancelled2());
										}
									});
							}
						}
					}
				}
				
				public void
				valueWritten(
					DHTPluginContact	target,
					DHTPluginValue		value )
				{
				}
				
				public void
				complete(
					byte[]				original_key,
					boolean				timeout_occurred )
				{
					new AEThread2( "Subs:lookup wait", true )
					{
						public void
						run()
						{
							int	num_hits;
							
							synchronized( this ){
								
								if ( complete ){
									
									return;
								}
								
								complete = true;
								
								num_hits = hits.size();
							}
							
							
							for (int i=0;i<num_hits;i++){
								
								if ( isCancelled2()){
									
									return;
								}

								hits_sem.reserve();
							}

							if ( isCancelled2()){
								
								return;
							}

							SubscriptionImpl[] s;
							
							synchronized( this ){
								
								s = (SubscriptionImpl[])found_subscriptions.toArray( new SubscriptionImpl[ found_subscriptions.size() ]);
							}
							
							log( "    Association lookup complete - " + s.length + " found" );

							try{	
									// record zero assoc here for completeness
								
								recordAssociations( hash, s, true );
								
							}finally{
								
								listener.complete( hash, s );
							}
						}
					}.start();
				}
				
				protected boolean
				isCancelled2()
				{
					synchronized( cancelled ){
						
						return( cancelled[0] );
					}
				}
			});
		
		return( 
			new SubscriptionAssociationLookup()
			{
				public void 
				cancel() 
				{
					log( "    Association lookup cancelled" );

					synchronized( cancelled ){
						
						cancelled[0] = true;
					}
				}
			});
	}
	
	interface 
	subsLookupListener
		extends SubscriptionLookupListener
	{
		public boolean
		isCancelled();
	}
	
	protected void
	getPopularity(
		final SubscriptionImpl					subs,
		final SubscriptionPopularityListener	listener )
	
		throws SubscriptionException
	{
		try{
			long pop = PlatformSubscriptionsMessenger.getPopularityBySID( subs.getShortID());

			if ( pop >= 0 ){	
				
				log( "Got popularity of " + subs.getName() + " from platform: " + pop );
				
				listener.gotPopularity( pop );

				return;
				
			}else{
				
					// unknown sid - if singleton try to register for popularity tracking purposes
				
				if ( subs.isSingleton()){
					
					try{
						checkSingletonPublish( subs );
						
						listener.gotPopularity( subs.isSubscribed()?1:0 );
						
						return;
						
					}catch( Throwable e ){
						
						log( "    failed to create singleton public subscription " + subs.getString(), e );
					}
				}
			}
			
		}catch( Throwable e ){
			
			log( "Subscription lookup via platform failed", e );
		}
		
		getPopularityFromDHT( subs, listener, true );
	}
	
	protected void
	getPopularityFromDHT(
		final SubscriptionImpl					subs,
		final SubscriptionPopularityListener	listener,
		final boolean							sync )

	{
		if ( dht_plugin != null && !dht_plugin.isInitialising()){

			getPopularitySupport( subs, listener, sync );
			
		}else{
			
			new AEThread2( "SM:popwait", true )
			{
				public void
				run()
				{
					getPopularitySupport( subs, listener, sync );
				}
			}.start();
		}
	}
	
	protected void
	updatePopularityFromDHT(
		final SubscriptionImpl		subs,
		boolean						sync )
	{
		getPopularityFromDHT(
			subs,
			new SubscriptionPopularityListener()
			{
				public void
				gotPopularity(
					long						popularity )
				{
					subs.setCachedPopularity( popularity );
				}
				
				public void
				failed(
					SubscriptionException		error )
				{
					log( "Failed to update subscription popularity from DHT", error );
				}
			},
			sync );
	}
	
	protected void
	getPopularitySupport(
		final SubscriptionImpl					subs,
		final SubscriptionPopularityListener	listener,
		final boolean							sync )
	{
		log( "Getting popularity of " + subs.getName() + " from DHT" );

		byte[]	hash = subs.getPublicationHash();
		
		final AESemaphore sem = new AESemaphore( "SM:pop" );
		
		final long[] result = { -1 };
		
		final int timeout = 15*1000;
		
		dht_plugin.get(
				hash,
				"Popularity lookup for subscription " + subs.getName(),
				DHT.FLAG_STATS,
				5,
				timeout,
				false,
				true,
				new DHTPluginOperationListener()
				{
					private boolean	diversified;
					
					private int	hits = 0;
					
					public void
					diversified()
					{
						diversified = true;
					}
					
					public void 
					starts(
						byte[] 				key ) 
					{
					}
					
					public void
					valueRead(
						DHTPluginContact	originator,
						DHTPluginValue		value )
					{
						DHTPluginKeyStats stats = dht_plugin.decodeStats( value );
						
						result[0] = Math.max( result[0], stats.getEntryCount());
						
						hits++;
						
						if ( hits >= 3 ){
							
							done();
						}
					}
					
					public void
					valueWritten(
						DHTPluginContact	target,
						DHTPluginValue		value )
					{
						
					}
					
					public void
					complete(
						byte[]				key,
						boolean				timeout_occurred )
					{
						if ( diversified ){
							
								// TODO: fix?
							
							result[0] *= 11;
							
							if ( result[0] == 0 ){
								
								result[0] = 10;
							}
						}
						
						done();
					}
					
					protected void
					done()
					{						
						if ( sync ){
			
							sem.release();

						}else{
							
							if ( result[0] == -1 ){
								
								log( "Failed to get popularity of " + subs.getName() + " from DHT" );
					
								listener.failed( new SubscriptionException( "Timeout" ));
								
							}else{
							
								log( "Get popularity of " + subs.getName() + " from DHT: " + result[0] );
					
								listener.gotPopularity( result[0] );
							}
						}
					}
				});
		
		if ( sync ){
			
			sem.reserve( timeout );
			
			if ( result[0] == -1 ){
				
				log( "Failed to get popularity of " + subs.getName() + " from DHT" );
	
				listener.failed( new SubscriptionException( "Timeout" ));
				
			}else{
			
				log( "Get popularity of " + subs.getName() + " from DHT: " + result[0] );
	
				listener.gotPopularity( result[0] );
			}
		}
	}
	
	protected void
	lookupSubscription(
		final byte[]						association_hash,
		final byte[]						sid,
		final int							version,
		final subsLookupListener			listener )
	{
		try{
			SubscriptionImpl subs = getSubscriptionFromPlatform( sid, SubscriptionImpl.ADD_TYPE_LOOKUP );

			log( "Added temporary subscription: " + subs.getString());
			
			subs = addSubscription( subs );
			
			listener.complete( association_hash, new Subscription[]{ subs });
			
			return;
			
		}catch( Throwable e ){
			
			if ( listener.isCancelled()){
				
				return;
			}
			
			final String sid_str = ByteFormatter.encodeString( sid );
			
			log( "Subscription lookup via platform for " + sid_str + " failed", e );
			
			if ( getSubscriptionDownloadCount() > 8 ){
				
				log( "Too many existing subscription downloads" );
				
				listener.complete( association_hash, new Subscription[0]);

				return;
			}
			
				// fall back to DHT
			
			log( "Subscription lookup via DHT starts for " + sid_str );

			final String	key = "subscription:publish:" + ByteFormatter.encodeString( sid ) + ":" + version; 
			
			dht_plugin.get(
				key.getBytes(),
				"Subscription lookup read: " + ByteFormatter.encodeString( sid ) + ":" + version,
				DHTPlugin.FLAG_SINGLE_VALUE,
				12,
				60*1000,
				false,
				true,
				new DHTPluginOperationListener()
				{
					private boolean listener_handled;
					
					public void
					diversified()
					{
					}
					
					public void 
					starts(
						byte[] 				key ) 
					{
					}
					
					public void
					valueRead(
						DHTPluginContact	originator,
						DHTPluginValue		value )
					{
						byte[]	data = value.getValue();
								
						try{
							final Map	details = decodeSubscriptionDetails( data );
							
							if ( SubscriptionImpl.getPublicationVersion( details ) == version ){
																
								Map	singleton_details = (Map)details.get( "x" );
								
								if ( singleton_details == null ){
									
									synchronized( this ){
										
										if ( listener_handled  ){
											
											return;
										}
										
										listener_handled = true;
									}
									
									log( "    found " + sid_str + ", non-singleton" );

									new AEThread2( "Subs:lookup download", true )
									{
										public void
										run()
										{
											downloadSubscription( 
												association_hash,
												SubscriptionImpl.getPublicationHash( details ),
												sid,
												version,
												SubscriptionImpl.getPublicationSize( details ),
												listener );
										}
									}.start();
									
								}else{
																			
									synchronized( this ){
											
										if ( listener_handled  ){
												
											return;
										}
											
										listener_handled = true;
									}
									
									log( "    found " + sid_str + ", singleton" );

									try{
										SubscriptionImpl subs = createSingletonSubscription( singleton_details, SubscriptionImpl.ADD_TYPE_LOOKUP, false );
																			
										listener.complete( association_hash, new Subscription[]{ subs });
																			
									}catch( Throwable e ){
																					
										listener.failed( association_hash, new SubscriptionException( "Subscription creation failed", e ));
									}
								}
							}else{
								
								log( "    found " + sid_str + " but version mismatch" );

							}
						}catch( Throwable e ){
							
							log( "    found " + sid_str + " but verification failed", e );

						}
					}
					
					public void
					valueWritten(
						DHTPluginContact	target,
						DHTPluginValue		value )
					{
					}
					
					public void
					complete(
						byte[]				original_key,
						boolean				timeout_occurred )
					{
						if ( listener.isCancelled()){
							
							return;
						}
						
						log( "    " + sid_str + " complete" );
						
						synchronized( this ){
							
							if ( !listener_handled ){
						
								listener_handled = true;
								
								listener.complete( association_hash, new Subscription[0] );
							}
						}
					}
				});
		}
	}
	
	protected SubscriptionImpl
	getSubscriptionFromPlatform(
		byte[]		sid,
		int			add_type )
	
		throws SubscriptionException
	{
		try{
			PlatformSubscriptionsMessenger.subscriptionDetails details = PlatformSubscriptionsMessenger.getSubscriptionBySID( sid );
			
			SubscriptionImpl res = getSubscriptionFromVuzeFileContent( sid, add_type, details.getContent());
			
			int	pop = details.getPopularity();
			
			if ( pop >= 0 ){
				
				res.setCachedPopularity( pop );
			}
			
			return( res );
			
		}catch( SubscriptionException e ){
			
			throw( e );
			
		}catch( Throwable e ){
			
			throw( new SubscriptionException( "Failed to read subscription from platform", e ));
		}
	}
	
	protected SubscriptionImpl
	getSubscriptionFromVuzeFile(
		byte[]		sid,
		int			add_type,
		File		file )
	
		throws SubscriptionException
	{
		VuzeFileHandler vfh = VuzeFileHandler.getSingleton();
		
		String	file_str = file.getAbsolutePath();
		
		VuzeFile vf = vfh.loadVuzeFile( file_str );

		if ( vf == null ){
			
			log( "Failed to load vuze file from " + file_str );
			
			throw( new SubscriptionException( "Failed to load vuze file from " + file_str ));
		}
		
		return( getSubscriptionFromVuzeFile( sid, add_type, vf ));
	}
	
	protected SubscriptionImpl
	getSubscriptionFromVuzeFileContent(
		byte[]		sid,
		int			add_type,
		String		content )
	
		throws SubscriptionException
	{
		VuzeFileHandler vfh = VuzeFileHandler.getSingleton();
		
		VuzeFile vf = vfh.loadVuzeFile( Base64.decode( content ));

		if ( vf == null ){
			
			log( "Failed to load vuze file from " + content );
			
			throw( new SubscriptionException( "Failed to load vuze file from content" ));
		}
	
		return( getSubscriptionFromVuzeFile( sid, add_type, vf ));
	}
	
	protected SubscriptionImpl
	getSubscriptionFromVuzeFile(
		byte[]		sid,
		int			add_type,
		VuzeFile	vf )
	
		throws SubscriptionException
	{
		VuzeFileComponent[] comps = vf.getComponents();
		
		for (int j=0;j<comps.length;j++){
			
			VuzeFileComponent comp = comps[j];
			
			if ( comp.getType() == VuzeFileComponent.COMP_TYPE_SUBSCRIPTION ){
				
				Map map = comp.getContent();
				
				try{
					SubscriptionBodyImpl body = new SubscriptionBodyImpl( SubscriptionManagerImpl.this, map );
												
					SubscriptionImpl new_subs = new SubscriptionImpl( SubscriptionManagerImpl.this, body, add_type, false );
							
					if ( Arrays.equals( new_subs.getShortID(), sid )){
													
						return( new_subs );
					}
				}catch( Throwable e ){
					
					log( "Subscription decode failed", e );
				}
			}
		}
		
		throw( new SubscriptionException( "Subscription not found" ));
	}
	
	protected void 
	downloadSubscription(
		final byte[]						association_hash,
		byte[]								torrent_hash,
		final byte[]						sid,
		int									version,
		int									size,
		final subsLookupListener		 	listener )
	{
		try{
			Object[] res = downloadTorrent( torrent_hash, size );
			
			if ( listener.isCancelled()){
				
				return;
			}
			
			if ( res == null ){
				
				listener.complete( association_hash, new Subscription[0] );
				
				return;
			}

			downloadSubscription(
				(TOTorrent)res[0], 
				(InetSocketAddress)res[1],
				sid,
				version,
				"Subscription " + ByteFormatter.encodeString( sid ) + " for " + ByteFormatter.encodeString( association_hash ),
				new downloadListener()
				{
					public void
					complete(
						File		data_file )
					{
						boolean	reported = false;
						
						try{
							if ( listener.isCancelled()){
								
								return;
							}
							
							SubscriptionImpl subs = getSubscriptionFromVuzeFile( sid, SubscriptionImpl.ADD_TYPE_LOOKUP, data_file );
						
							log( "Added temporary subscription: " + subs.getString());
							
							subs = addSubscription( subs );
							
							listener.complete( association_hash, new Subscription[]{ subs });
							
							reported = true;
	
						}catch( Throwable e ){
							
							log( "Subscription decode failed", e );
							
						}finally{
														
							if ( !reported ){
								
								listener.complete( association_hash, new Subscription[0] );
							}
						}
					}
					
					public void
					complete(
						Download	download,	
						File		torrent_file )
					{
						File	data_file = new File( download.getSavePath());
						
						try{
							removeDownload( download, false );

							complete( data_file );
							
						}catch( Throwable e ){
							
							log( "Failed to remove download", e );
							
							listener.complete( association_hash, new Subscription[0] );
							
						}finally{
							
							torrent_file.delete();
							
							data_file.delete();
						}
					}
						
					public void
					failed(
						Throwable	error )
					{
						listener.complete( association_hash, new Subscription[0] );
					}
					
					public Map
					getRecoveryData()
					{
						return( null );
					}
					
					public boolean
					isCancelled()
					{
						return( listener.isCancelled());
					}
				});
				
		}catch( Throwable e ){
			
			log( "Subscription download failed",e );
			
			listener.complete( association_hash, new Subscription[0] );
		}
	}
	
	protected int
	getSubscriptionDownloadCount()
	{
		PluginInterface pi = PluginInitializer.getDefaultInterface();
		
		Download[] downloads = pi.getDownloadManager().getDownloads();
		
		int	res = 0;
		
		for( int i=0;i<downloads.length;i++){
			
			Download	download = downloads[i];
			
			if ( download.getBooleanAttribute( ta_subs_download )){
				
				res++;
			}
		}
		
		return( res );
	}
	
	protected void
	associationAdded(
		SubscriptionImpl			subscription,
		byte[]						association_hash )
	{
		recordAssociations( association_hash, new SubscriptionImpl[]{ subscription }, false );

		if ( dht_plugin != null ){
			
			publishAssociations();
		}
	}
	
	protected void
	addPotentialAssociation(
		SubscriptionImpl			subs,
		String						result_id,
		String						key )
	{
		if ( key == null ){
			
			Debug.out( "Attempt to add null key!" );
			
			return;
		}
		
		log( "Added potential association: " + subs.getName() + "/" + result_id + " -> " + key );
		
		synchronized( potential_associations ){
			
			potential_associations.add( new Object[]{ subs, result_id, key, new Long( System.currentTimeMillis())} );
			
			if ( potential_associations.size() > 512 ){
				
				potential_associations.remove(0);
			}
		}
	}
	
	protected void
	checkPotentialAssociations(
		byte[]				hash,
		String				key )
	{
		log( "Checking potential association: " + key + " -> " + ByteFormatter.encodeString( hash ));
		
		SubscriptionImpl 	subs 		= null;
		String				result_id	= null;
		
		synchronized( potential_associations ){

			Iterator it = potential_associations.iterator();
			
			while( it.hasNext()){
				
				Object[]	entry = (Object[])it.next();
				
				String	this_key = (String)entry[2];
				
					// startswith as actual URL may have had additional parameters added such as azid
				
				if ( key.startsWith( this_key )){
					
					subs		= (SubscriptionImpl)entry[0];
					result_id	= (String)entry[1];
					
					log( "    key matched to subscription " + subs.getName() + "/" + result_id);

					it.remove();
					
					break;
				}
			}
		}
		
		if ( subs == null ){
			
			log( "    no potential associations found" );
			
		}else{
			
			SubscriptionResult	result = subs.getHistory().getResult( result_id );
			
			if ( result != null ){
				
				log( "    result found, marking as read" );

				result.setRead( true );
				
			}else{
				
				log( "    result not found" );
			}
			
			log( "    adding association" );
			
			subs.addAssociation( hash );
		}
	}
	
	protected void
	tidyPotentialAssociations()
	{
		long	now = SystemTime.getCurrentTime();
		
		synchronized( potential_associations ){
			
			Iterator it = potential_associations.iterator();
			
			while( it.hasNext() && potential_associations.size() > 16 ){
				
				Object[]	entry = (Object[])it.next();
				
				long	created = ((Long)entry[3]).longValue();
				
				if ( created > now ){
					
					entry[3] = new Long( now );
					
				}else if ( now - created > 60*60*1000 ){
					
					SubscriptionImpl 	subs = (SubscriptionImpl)entry[0];

					String	result_id	= (String)entry[1];
					String	key			= (String)entry[2];

					log( "Removing expired potential association: " + subs.getName() + "/" + result_id + " -> " + key );
					
					it.remove();
				}
			}
		}
		
		synchronized( potential_associations2 ){
			
			Iterator it = potential_associations2.entrySet().iterator();
			
			while( it.hasNext() && potential_associations2.size() > 16 ){
				
				Map.Entry	map_entry = (Map.Entry)it.next();
				
				byte[]		hash = ((HashWrapper)map_entry.getKey()).getBytes();
				
				Object[]	entry = (Object[])map_entry.getValue();
				
				long	created = ((Long)entry[2]).longValue();
				
				if ( created > now ){
					
					entry[2] = new Long( now );
					
				}else if ( now - created > 60*60*1000 ){
					
					SubscriptionImpl[] 	subs = (SubscriptionImpl[])entry[0];

					String	subs_str = "";
					
					for (int i=0;i<subs.length;i++){
						subs_str += (i==0?"":",") + subs[i].getName();
					}
					
					log( "Removing expired potential association: " + ByteFormatter.encodeString(hash) + " -> " + subs_str );
					
					it.remove();
				}
			}
		}
	}
	
	protected void
	recordAssociations(
		byte[]						association_hash,
		SubscriptionImpl[]			subscriptions,
		boolean						full_lookup )
	{
		HashWrapper	hw = new HashWrapper( association_hash );
		
		synchronized( potential_associations2 ){
			
			potential_associations2.put( hw, new Object[]{ subscriptions, new Boolean( full_lookup ), new Long( SystemTime.getCurrentTime())});
		}
			
		if ( recordAssociationsSupport( association_hash, subscriptions, full_lookup )){
			
			synchronized( potential_associations2 ){

				potential_associations2.remove( hw );
			}
		}else{
			
			log( "Deferring association for " + ByteFormatter.encodeString( association_hash ));
		}
	}
	
	protected boolean
	recordAssociationsSupport(
		byte[]						association_hash,
		SubscriptionImpl[]			subscriptions,
		boolean						full_lookup )
	{
		PluginInterface pi = PluginInitializer.getDefaultInterface();

		boolean	download_found	= false;
		boolean	changed 		= false;
		
		try{
			Download download = pi.getDownloadManager().getDownload( association_hash );
			
			if ( download != null ){
					
				if ( subscriptions.length > 0 ){
					
					String	category = subscriptions[0].getCategory();
					
					if ( category != null ){
						
						String existing = download.getAttribute( ta_category );
								
						if ( existing == null ){
									
							download.setAttribute( ta_category, category );
						}
					}
				}
				
				download_found = true;
				
				Map	map = download.getMapAttribute( ta_subscription_info );
				
				if ( map == null ){
					
					map = new LightHashMap();
					
				}else{
					
					map = new LightHashMap( map );
				}
				
				List	s = (List)map.get( "s" );
				
				for (int i=0;i<subscriptions.length;i++){
				
					byte[]	sid = subscriptions[i].getShortID();
					
					if ( s == null ){
						
						s = new ArrayList();
						
						s.add( sid );
						
						changed	= true;
						
						map.put( "s", s );
						
					}else{
						
						boolean found = false;
						
						for (int j=0;j<s.size();j++){
							
							byte[]	existing = (byte[])s.get(j);
							
							if ( Arrays.equals( sid, existing )){
								
								found = true;
								
								break;
							}
						}
						
						if ( !found ){
						
							s.add( sid );
								
							changed	= true;
						}
					}
				}
				
				if ( full_lookup ){
				
					map.put( "lc", new Long( SystemTime.getCurrentTime()));
					
					changed	= true;
				}
				
				if ( changed ){
				
					download.setMapAttribute( ta_subscription_info, map );
				}
			}
		}catch( Throwable e ){
			
			log( "Failed to record associations", e );
		}
		
		if ( changed ){
			
			Iterator it = listeners.iterator();
			
			while( it.hasNext()){
				
				try{
					((SubscriptionManagerListener)it.next()).associationsChanged( association_hash );
					
				}catch( Throwable e ){
					
					Debug.printStackTrace(e);
				}
			}
		}
		
		return( download_found );
	}
	
	protected boolean
	publishAssociations()
	{
		SubscriptionImpl 				subs_to_publish		= null;
		SubscriptionImpl.association	assoc_to_publish 	= null;

		synchronized( this ){
			
			if ( publish_associations_active >= PUB_ASSOC_CONC_MAX ){
				
				return( false );
			}			
			
			publish_associations_active++;
			
			List shuffled_subs = new ArrayList( subscriptions );

			Collections.shuffle( shuffled_subs );
							
			for (int i=0;i<shuffled_subs.size();i++){
					
				SubscriptionImpl sub = (SubscriptionImpl)shuffled_subs.get( i );
					
				if ( sub.isSubscribed() && sub.isPublic()){
					
					assoc_to_publish 	= sub.getAssociationForPublish();
						
					if ( assoc_to_publish != null ){
							
						subs_to_publish		= sub;

						break;
					}
				}
			}
		}
		
		if ( assoc_to_publish != null ){
		
			publishAssociation( subs_to_publish, assoc_to_publish );
			
			return( false );
		}else{
					
			log( "Publishing Associations Complete" );
					
			synchronized( this ){

				publish_associations_active--;
			}
			
			return( true );
		}
	}
	
	protected void
	publishAssociation(
		final SubscriptionImpl					subs,
		final SubscriptionImpl.association		assoc )
	{
		log( "Checking association '" + subs.getString() + "' -> '" + assoc.getString() + "'" );
		
		byte[]	sub_id 		= subs.getShortID();
		int		sub_version	= subs.getVersion();
		
		byte[]	assoc_hash	= assoc.getHash();
		
		final String	key = "subscription:assoc:" + ByteFormatter.encodeString( assoc_hash ); 
				
		final byte[]	put_value = new byte[sub_id.length + 4];
		
		System.arraycopy( sub_id, 0, put_value, 4, sub_id.length );
		
		put_value[0]	= (byte)(sub_version>>16);
		put_value[1]	= (byte)(sub_version>>8);
		put_value[2]	= (byte)sub_version;
		put_value[3]	= (byte)subs.getFixedRandom();
		
		dht_plugin.get(
			key.getBytes(),
			"Subscription association read: " + ByteFormatter.encodeString( assoc_hash ),
			DHTPlugin.FLAG_SINGLE_VALUE,
			30,
			60*1000,
			false,
			false,
			new DHTPluginOperationListener()
			{
				private int			hits;
				private boolean		diversified;
				private int			max_ver;
				
				public void
				diversified()
				{
					diversified = true;
				}
				
				public void 
				starts(
					byte[] 				key ) 
				{
				}
				
				public void
				valueRead(
					DHTPluginContact	originator,
					DHTPluginValue		value )
				{
					byte[]	val = value.getValue();
					
					if ( val.length == put_value.length ){
						
						boolean	diff = false;
						
						for (int i=4;i<val.length;i++){
							
							if ( val[i] != put_value[i] ){
								
								diff = true;
								
								break;
							}
						}
						
						if ( !diff ){
							
							hits++;
							
							int	ver = ((val[0]<<16)&0xff0000) | ((val[1]<<8)&0xff00) | (val[2]&0xff);
							
							if ( ver > max_ver ){
								
								max_ver = ver;
							}
						}
					}
				}
				
				public void
				valueWritten(
					DHTPluginContact	target,
					DHTPluginValue		value )
				{
				}
				
				public void
				complete(
					byte[]				original_key,
					boolean				timeout_occurred )
				{
					log( "Checked association '" + subs.getString() + "' -> '" + assoc.getString() + "' - max_ver=" + max_ver + ",hits=" + hits + ",div=" + diversified );

					if ( max_ver > subs.getVersion()){
						
						if ( !subs.isMine()){
						
							updateSubscription( subs, max_ver );
						}
					}
					
					if (( hits == 0 && diversified ) || ( hits < 6 && !diversified )){			
			
						log( "    Publishing association '" + subs.getString() + "' -> '" + assoc.getString() + "', existing=" + hits );

						byte flags = DHTPlugin.FLAG_ANON;
						
						if ( hits < 3 && !diversified ){
							
							flags |= DHTPlugin.FLAG_PRECIOUS;
						}
						
						dht_plugin.put(
							key.getBytes(),
							"Subscription association write: " + ByteFormatter.encodeString( assoc.getHash()) + " -> " + ByteFormatter.encodeString( subs.getShortID() ) + ":" + subs.getVersion(),
							put_value,
							flags,
							new DHTPluginOperationListener()
							{
								public void
								diversified()
								{
								}
								
								public void 
								starts(
									byte[] 				key ) 
								{
								}
								
								public void
								valueRead(
									DHTPluginContact	originator,
									DHTPluginValue		value )
								{
								}
								
								public void
								valueWritten(
									DHTPluginContact	target,
									DHTPluginValue		value )
								{
								}
								
								public void
								complete(
									byte[]				key,
									boolean				timeout_occurred )
								{
									log( "        completed '" + subs.getString() + "' -> '" + assoc.getString() + "'" );
				
									publishNext();
								}
							});
					}else{
						
						log( "    Not publishing association '" + subs.getString() + "' -> '" + assoc.getString() + "', existing =" + hits );

						publishNext();
					}
				}
				
				protected void
				publishNext()
				{
					synchronized( this ){
						
						publish_associations_active--;
					}
					
					publishAssociations();
				}
			});
	}
	
	protected void
	subscriptionUpdated()
	{
		if ( dht_plugin != null ){
			
			publishSubscriptions();
		}
	}
	
	protected void
	publishSubscriptions()
	{
		List	 shuffled_subs;

		synchronized( this ){
			
			if ( publish_subscription_active ){
				
				return;
			}			
			
			shuffled_subs = new ArrayList( subscriptions );

			publish_subscription_active = true;
		}
	
		boolean	publish_initiated = false;
		
		try{
			Collections.shuffle( shuffled_subs );
						
			for (int i=0;i<shuffled_subs.size();i++){
				
				SubscriptionImpl sub = (SubscriptionImpl)shuffled_subs.get( i );
				
				if ( sub.isSubscribed() && sub.isPublic() && !sub.getPublished()){
									
					sub.setPublished( true );
					
					publishSubscription( sub );
					
					publish_initiated = true;
					
					break;
				}
			}
		}finally{
			
			if ( !publish_initiated ){
				
				log( "Publishing Subscriptions Complete" );
				
				synchronized( this ){
	
					publish_subscription_active = false;
				}
			}
		}
	}
	
	protected void
	publishSubscription(
		final SubscriptionImpl					subs )
	{
		log( "Checking subscription publication '" + subs.getString() + "'" );
		
		byte[]	sub_id 		= subs.getShortID();
		int		sub_version	= subs.getVersion();
				
		final String	key = "subscription:publish:" + ByteFormatter.encodeString( sub_id ) + ":" + sub_version; 
						
		dht_plugin.get(
			key.getBytes(),
			"Subscription presence read: " + ByteFormatter.encodeString( sub_id ) + ":" + sub_version,
			DHTPlugin.FLAG_SINGLE_VALUE,
			24,
			60*1000,
			false,
			false,
			new DHTPluginOperationListener()
			{
				private int		hits;
				private boolean	diversified;
				
				public void
				diversified()
				{					
					diversified = true;
				}
				
				public void 
				starts(
					byte[] 				key ) 
				{
				}
				
				public void
				valueRead(
					DHTPluginContact	originator,
					DHTPluginValue		value )
				{					
					byte[]	data = value.getValue();
						
					try{
						Map	details = decodeSubscriptionDetails( data );

						if ( subs.getVerifiedPublicationVersion( details ) == subs.getVersion()){
						
							hits++;
						}
					}catch( Throwable e ){
						
					}
				}
				
				public void
				valueWritten(
					DHTPluginContact	target,
					DHTPluginValue		value )
				{
				}
				
				public void
				complete(
					byte[]				original_key,
					boolean				timeout_occurred )
				{
					log( "Checked subscription publication '" + subs.getString() + "' - hits=" + hits + ",div=" + diversified );

					if (( hits == 0 && diversified ) || ( hits < 6 && !diversified )){			
			
						log( "    Publishing subscription '" + subs.getString() + ", existing=" + hits );

						try{
							byte[]	put_value = encodeSubscriptionDetails( subs );						
							
							byte	flags = DHTPlugin.FLAG_SINGLE_VALUE;
							
							if ( hits < 3 && !diversified ){
								
								flags |= DHTPlugin.FLAG_PRECIOUS;
							}
							
							dht_plugin.put(
								key.getBytes(),
								"Subscription presence write: " + ByteFormatter.encodeString( subs.getShortID() ) + ":" + subs.getVersion(),
								put_value,
								flags,
								new DHTPluginOperationListener()
								{
									public void
									diversified()
									{
									}
									
									public void 
									starts(
										byte[] 				key ) 
									{
									}
									
									public void
									valueRead(
										DHTPluginContact	originator,
										DHTPluginValue		value )
									{
									}
									
									public void
									valueWritten(
										DHTPluginContact	target,
										DHTPluginValue		value )
									{
									}
									
									public void
									complete(
										byte[]				key,
										boolean				timeout_occurred )
									{
										log( "        completed '" + subs.getString() + "'" );
					
										publishNext();
									}
								});
							
						}catch( Throwable e ){
							
							Debug.printStackTrace( e );
							
							publishNext();
						}
						
					}else{
						
						log( "    Not publishing subscription '" + subs.getString() + "', existing =" + hits );

						publishNext();
					}
				}
				
				protected void
				publishNext()
				{
					synchronized( this ){
						
						publish_subscription_active = false;
					}
					
					publishSubscriptions();
				}
			});
	}
	
	protected void
	updateSubscription(
		final SubscriptionImpl		subs,
		final int					new_version )
	{
		log( "Subscription " + subs.getString() + " - higher version found: " + new_version );
		
		if ( !subs.canAutoUpgradeCheck()){
			
			log( "    Checked too recently or not updateable, ignoring" );
			
			return;
		}
		
		if ( subs.getHighestUserPromptedVersion() >= new_version ){
			
			log( "    User has already been prompted for version " + new_version + " so ignoring" );
			
			return;
		}
					
		byte[]	sub_id 		= subs.getShortID();
			
		try{
			PlatformSubscriptionsMessenger.subscriptionDetails details = PlatformSubscriptionsMessenger.getSubscriptionBySID( sub_id );
			
			if ( !askIfCanUpgrade( subs, new_version )){
				
				return;
			}
			
			VuzeFileHandler vfh = VuzeFileHandler.getSingleton();
			
			VuzeFile vf = vfh.loadVuzeFile( Base64.decode( details.getContent()));
							
			vfh.handleFiles( new VuzeFile[]{ vf }, VuzeFileComponent.COMP_TYPE_SUBSCRIPTION );
			
			return;
			
		}catch( Throwable e ){
			
			log( "Failed to read subscription from platform, trying DHT" );
		}
		
		log( "Checking subscription '" + subs.getString() + "' upgrade to version " + new_version );

		final String	key = "subscription:publish:" + ByteFormatter.encodeString( sub_id ) + ":" + new_version; 
						
		dht_plugin.get(
			key.getBytes(),
			"Subscription update read: " + ByteFormatter.encodeString( sub_id ) + ":" + new_version,
			DHTPlugin.FLAG_SINGLE_VALUE,
			12,
			60*1000,
			false,
			false,
			new DHTPluginOperationListener()
			{
				private byte[]	verified_hash;
				private int		verified_size;
				
				public void
				diversified()
				{
				}
				
				public void 
				starts(
					byte[] 				key ) 
				{
				}
				
				public void
				valueRead(
					DHTPluginContact	originator,
					DHTPluginValue		value )
				{
					byte[]	data = value.getValue();
							
					try{
						Map	details = decodeSubscriptionDetails( data );
						
						if ( 	verified_hash == null && 
								subs.getVerifiedPublicationVersion( details ) == new_version ){
							
							verified_hash 	= SubscriptionImpl.getPublicationHash( details );
							verified_size	= SubscriptionImpl.getPublicationSize( details );
						}
						
					}catch( Throwable e ){
						
					}
				}
				
				public void
				valueWritten(
					DHTPluginContact	target,
					DHTPluginValue		value )
				{
				}
				
				public void
				complete(
					byte[]				original_key,
					boolean				timeout_occurred )
				{
					if ( verified_hash != null ){			
			
						log( "    Subscription '" + subs.getString() + " upgrade verified as authentic" );

						updateSubscription( subs, new_version, verified_hash, verified_size );
						
					}else{
						
						log( "    Subscription '" + subs.getString() + " upgrade not verified" );
					}
				}
			});
	}
	
	protected byte[]
	encodeSubscriptionDetails(
		SubscriptionImpl		subs )
	
		throws IOException
	{
		Map		details = subs.getPublicationDetails();					
		
			// inject a random element so we can count occurrences properly (as the DHT logic
			// removes duplicates)
		
		details.put( "!", new Long( random_seed ));
		
		byte[] encoded = BEncoder.encode( details );
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
				
		GZIPOutputStream os = new GZIPOutputStream( baos );

		os.write( encoded );
		
		os.close();
		
		byte[] compressed = baos.toByteArray();
		
		byte	header;
		byte[]	data;
		
		if ( compressed.length < encoded.length ){
			
			header 	= 1;
			data	= compressed; 
		}else{
			
			header	= 0;
			data	= encoded;
		}
				
		byte[] result = new byte[data.length+1];
		
		result[0] = header;
		
		System.arraycopy( data, 0, result, 1, data.length );
		
		return( result );
	}
	
	protected Map
	decodeSubscriptionDetails(
		byte[]			data )
	
		throws IOException
	{
		byte[]	to_decode;
		
		if ( data[0] == 0 ){
			
			to_decode = new byte[ data.length-1 ];
			
			System.arraycopy( data, 1, to_decode, 0, data.length - 1 );
			
		}else{
			
			GZIPInputStream is = new GZIPInputStream(new ByteArrayInputStream( data, 1, data.length - 1 ));
			
			to_decode = FileUtil.readInputStreamAsByteArray( is );
			
			is.close();
		}
		
		Map res = BDecoder.decode( to_decode );
		
			// remove any injected random seed
		
		res.remove( "!" );
		
		return( res );
	}
	
	protected void
	updateSubscription(
		final SubscriptionImpl			subs,
		final int						update_version,
		final byte[]					update_hash,
		final int						update_size )
	{
		log( "Subscription " + subs.getString() + " - update hash=" + ByteFormatter.encodeString( update_hash ) + ", size=" + update_size );

		new AEThread2( "SubsUpdate", true )
		{
			public void
			run()
			{
				try{
					Object[] res = downloadTorrent( update_hash, update_size );
					
					if ( res != null ){
					
						updateSubscription( subs, update_version, (TOTorrent)res[0], (InetSocketAddress)res[1] );
					}
				}catch( Throwable e ){
					
					log( "    update failed", e );
				}
			}
		}.start();
	}
	
	protected Object[]
	downloadTorrent(
		byte[]		hash,
		int			update_size )
	{		
		final MagnetPlugin	magnet_plugin = getMagnetPlugin();
	
		if ( magnet_plugin == null ){
		
			log( "    Can't download, no magnet plugin" );
		
			return( null );
		}

		try{
			final InetSocketAddress[] sender = { null };
			
			byte[] torrent_data = magnet_plugin.download(
				new MagnetPluginProgressListener()
				{
					public void
					reportSize(
						long	size )
					{
					}
					
					public void
					reportActivity(
						String	str )
					{
						if ( 	str.indexOf( "found " ) == -1 &&
								str.indexOf( " is dead" ) == -1 &&
								str.indexOf( " is alive" ) == -1){
			
							log( "    MagnetDownload: " + str );
						}
					}
					
					public void
					reportCompleteness(
						int		percent )
					{
					}
					
					public void
					reportContributor(
						InetSocketAddress	address )
					{
						synchronized( sender ){
						
							sender[0] = address;
						}
					}
				},
				hash,
				new InetSocketAddress[0],
				300*1000 );
			
			if ( torrent_data == null ){
				
				log( "    download failed - timeout" );
				
				return( null );
			}
			
			log( "Subscription torrent downloaded" );
			
			TOTorrent torrent = TOTorrentFactory.deserialiseFromBEncodedByteArray( torrent_data );
		
				// update size is just that of signed content, torrent itself is .vuze file
				// so take this into account
			
			if ( torrent.getSize() > update_size + 10*1024 ){
			
				log( "Subscription download abandoned, torrent size is " + torrent.getSize() + ", underlying data size is " + update_size );
				
				return( null );
			}
			
			if ( torrent.getSize() > 4*1024*1024 ){
				
				log( "Subscription download abandoned, torrent size is too large (" + torrent.getSize() + ")" );
				
				return( null );
			}
			
			synchronized( sender ){
			
				return( new Object[]{ torrent, sender[0] });
			}
			
		}catch( Throwable e ){
			
			log( "    download failed", e );
			
			return( null );
		}
	}
	
	protected void
	downloadSubscription(
		final TOTorrent			torrent,
		final InetSocketAddress	peer,
		byte[]					subs_id,
		int						version,
		String					name,
		final downloadListener	listener )
	{
		try{
				// testing purposes, see if local exists
			
			LightWeightSeed lws = LightWeightSeedManager.getSingleton().get( new HashWrapper( torrent.getHash()));
	
			if ( lws != null ){
				
				log( "Light weight seed found" );
				
				listener.complete( lws.getDataLocation());
				
			}else{
				String	sid = ByteFormatter.encodeString( subs_id );
				
				File	dir = getSubsDir();
				
				dir = new File( dir, "temp" );
				
				if ( !dir.exists()){
					
					if ( !dir.mkdirs()){
						
						throw( new IOException( "Failed to create dir '" + dir + "'" ));
					}
				}
				
				final File	torrent_file 	= new File( dir, sid + "_" + version + ".torrent" );
				final File	data_file 		= new File( dir, sid + "_" + version + ".vuze" );
	
				PluginInterface pi = PluginInitializer.getDefaultInterface();
			
				final DownloadManager dm = pi.getDownloadManager();
				
				Download download = dm.getDownload( torrent.getHash());
				
				if ( download == null ){
					
					log( "Adding download for subscription '" + new String(torrent.getName()) + "'" );
					
					boolean is_update = getSubscriptionFromSID( subs_id ) != null;
					
					PlatformTorrentUtils.setContentTitle(torrent, (is_update?"Update":"Download") + " for subscription '" + name + "'" );
					
						// PlatformTorrentUtils.setContentThumbnail(torrent, thumbnail);
						
					TorrentUtils.setFlag( torrent, TorrentUtils.TORRENT_FLAG_LOW_NOISE, true );
					
					Torrent t = new TorrentImpl( torrent );
					
					t.setDefaultEncoding();
					
					t.writeToFile( torrent_file );
					
					download = dm.addDownload( t, torrent_file, data_file );
					
					download.setFlag( Download.FLAG_DISABLE_AUTO_FILE_MOVE, true );

					download.setBooleanAttribute( ta_subs_download, true );
					
					Map rd = listener.getRecoveryData();
					
					if ( rd != null ){
						
						download.setMapAttribute( ta_subs_download_rd, rd );
					}
				}else{
					
					log( "Existing download found for subscription '" + new String(torrent.getName()) + "'" );
				}
				
				final Download f_download = download;
				
				final TimerEventPeriodic[] event = { null };
				
				event[0] = 
					SimpleTimer.addPeriodicEvent(
						"SM:cancelTimer",
						10*1000,
						new TimerEventPerformer()
						{
							private long	start_time = SystemTime.getMonotonousTime();
							
							public void 
							perform(
								TimerEvent ev ) 
							{
								boolean	kill = false;
								
								try{	
									Download download = dm.getDownload( torrent.getHash());
									
									if ( listener.isCancelled() || download == null ){
										
										kill = true;
										
									}else{
										
										int	state = download.getState();
										
										if ( state == Download.ST_ERROR ){
											
											log( "Download entered error state, removing" );
											
											kill = true;
											
										}else{
											
											long	now = SystemTime.getMonotonousTime();
											
											long	running_for = now - start_time;
											
											if ( running_for > 2*60*1000 ){
												
												DownloadScrapeResult scrape = download.getLastScrapeResult();
												
												if ( scrape == null || scrape.getSeedCount() <= 0 ){
													
													log( "Download has no seeds, removing" );
													
													kill = true;
												}
											}else if ( running_for > 4*60*1000 ){
												
												if ( download.getStats().getDownloaded() == 0 ){
													
													log( "Download has zero downloaded, removing" );
													
													kill = true;
												}
											}else if ( running_for > 10*60*1000 ){
												
												log( "Download hasn't completed in permitted time, removing" );
												
												kill = true;
											}
										}
									}
								}catch( Throwable e ){
									
									log( "Download failed", e );

									kill = true;
								}
								
								if ( kill && event[0] != null ){
									
									try{
										event[0].cancel();
										
										if ( !listener.isCancelled()){
																					
											listener.failed( new SubscriptionException( "Download abandoned" ));
										}
									}finally{
										
										removeDownload( f_download, true );
									
										torrent_file.delete();
									}
								}
							}
						});
				
				download.addCompletionListener(
					new DownloadCompletionListener()
					{
						public void 
						onCompletion(
							Download d ) 
						{
							listener.complete( d, torrent_file );
						}
					});
				
				if ( download.isComplete()){
					
					listener.complete( download, torrent_file  );
					
				}else{
								
					download.setForceStart( true );
					
					if ( peer != null ){
					
						download.addPeerListener(
							new DownloadPeerListener()
							{
								public void
								peerManagerAdded(
									Download		download,
									PeerManager		peer_manager )
								{									
									InetSocketAddress tcp = AddressUtils.adjustTCPAddress( peer, true );
									InetSocketAddress udp = AddressUtils.adjustUDPAddress( peer, true );
									
									log( "    Injecting peer into download: " + tcp );

									peer_manager.addPeer( tcp.getAddress().getHostAddress(), tcp.getPort(), udp.getPort(), true );
								}
								
								public void
								peerManagerRemoved(
									Download		download,
									PeerManager		peer_manager )
								{							
								}
							});
					}
				}
			}
		}catch( Throwable e ){
			
			log( "Failed to add download", e );
			
			listener.failed( e );
		}
	}
	
	protected interface
	downloadListener
	{
		public void
		complete(
			File		data_file );
		
		public void
		complete(
			Download	download,	
			File		torrent_file );
			
		public void
		failed(
			Throwable	error );
		
		public Map
		getRecoveryData();
		
		public boolean
		isCancelled();
	}
	
	protected void
	updateSubscription(
		final SubscriptionImpl		subs,
		final int					new_version,
		TOTorrent					torrent,
		InetSocketAddress			peer )
	{
		log( "Subscription " + subs.getString() + " - update torrent: " + new String( torrent.getName()));

		if ( !askIfCanUpgrade( subs, new_version )){
			
			return;
		}
			
		downloadSubscription(
			torrent,
			peer,
			subs.getShortID(),
			new_version,
			subs.getName(),
			new downloadListener()
			{
				public void
				complete(
					File		data_file )
				{
					updateSubscription( subs, data_file );
				}
				
				public void
				complete(
					Download	download,	
					File		torrent_file )
				{
					updateSubscription( subs, download, torrent_file, new File( download.getSavePath()));
				}
					
				public void
				failed(
					Throwable	error )
				{
					log( "Failed to download subscription", error );
				}
				
				public Map
				getRecoveryData()
				{
					Map	rd = new HashMap();
					
					rd.put( "sid", subs.getShortID());
					rd.put( "ver", new Long( new_version ));
					
					return( rd );
				}
				
				public boolean
				isCancelled()
				{
					return( false );
				}
			});
	}

	protected boolean
	askIfCanUpgrade(
		SubscriptionImpl		subs,
		int						new_version )
	{
		subs.setHighestUserPromptedVersion( new_version );
		
		UIManager ui_manager = StaticUtilities.getUIManager( 120*1000 );
		
		String details = MessageText.getString(
				"subscript.add.upgradeto.desc",
				new String[]{ String.valueOf(new_version), subs.getName()});
		
		long res = ui_manager.showMessageBox(
				"subscript.add.upgrade.title",
				"!" + details + "!",
				UIManagerEvent.MT_YES | UIManagerEvent.MT_NO );
		
		if ( res != UIManagerEvent.MT_YES ){	
		
			log( "    User declined upgrade" );
			
			return( false );
		}
		
		return( true );
	}
	
	protected boolean
	recoverSubscriptionUpdate(
		Download				download,
		final Map				rd )
	{
		byte[]	sid 	= (byte[])rd.get( "sid" );
		int		version = ((Long)rd.get( "ver" )).intValue();
		
		final SubscriptionImpl subs = getSubscriptionFromSID( sid );
		
		if ( subs == null ){
		
			log( "Can't recover '" + download.getName() + "' - subscription " + ByteFormatter.encodeString( sid ) +  " not found" );
			
			return( false );
		}
		
		downloadSubscription(
				((TorrentImpl)download.getTorrent()).getTorrent(),
				null,
				subs.getShortID(),
				version,
				subs.getName(),
				new downloadListener()
				{
					public void
					complete(
						File		data_file )
					{
						updateSubscription( subs, data_file );
					}
					
					public void
					complete(
						Download	download,	
						File		torrent_file )
					{
						updateSubscription( subs, download, torrent_file, new File( download.getSavePath()));
					}
						
					public void
					failed(
						Throwable	error )
					{
						log( "Failed to download subscription", error );
					}
					
					public Map
					getRecoveryData()
					{
						return( rd );
					}
					
					public boolean
					isCancelled()
					{
						return( false );
					}
				});
		
		return( true );
	}
	
	protected void
	updateSubscription(
		SubscriptionImpl		subs,
		Download				download,
		File					torrent_file,
		File					data_file )
	{
		try{
			removeDownload( download, false );
		
			try{				
				updateSubscription( subs, data_file );
											
			}finally{
				
				if ( !data_file.delete()){
					
					log( "Failed to delete update file '" + data_file + "'" );
				}
				
				if ( !torrent_file.delete()){
					
					log( "Failed to delete update torrent '" + torrent_file + "'" );
				}
			}
		}catch( Throwable e ){
			
			log( "Failed to remove update download", e );
		}
	}
	
	protected void
	removeDownload(
		Download		download,
		boolean			remove_data )
	{
		try{
			download.stop();
			
		}catch( Throwable e ){
		}
		
		try{
			download.remove( true, remove_data );
			
			log( "Removed download '" + download.getName() + "'" );
			
		}catch( Throwable e ){
			
			log( "Failed to remove download '" + download.getName() + "'", e );
		}
	}
	
	protected void
	updateSubscription(
		SubscriptionImpl		subs,
		File					data_location )
	{
		log( "Updating subscription '" + subs.getString() + " using '" + data_location + "'" );
		
		VuzeFileHandler vfh = VuzeFileHandler.getSingleton();
			
		VuzeFile vf = vfh.loadVuzeFile( data_location.getAbsolutePath());
						
		vfh.handleFiles( new VuzeFile[]{ vf }, VuzeFileComponent.COMP_TYPE_SUBSCRIPTION );
	}
	
	protected MagnetPlugin
	getMagnetPlugin()
	{
		PluginInterface  pi  = AzureusCoreFactory.getSingleton().getPluginManager().getPluginInterfaceByClass( MagnetPlugin.class );
	
		if ( pi == null ){
			
			return( null );
		}
		
		return((MagnetPlugin)pi.getPlugin());
	}
	
	protected Engine
	getEngine(
		SubscriptionImpl		subs,
		Map						json_map,
		boolean					local_only )
	
		throws SubscriptionException
	{
		long id = ((Long)json_map.get( "engine_id" )).longValue();
		
		Engine engine = MetaSearchManagerFactory.getSingleton().getMetaSearch().getEngine( id );
		
		if ( engine != null ){
			
			return( engine );
		}

		if ( !local_only ){
			
			try{
				if ( id >= 0 && id < Integer.MAX_VALUE ){
								
					log( "Engine " + id + " not present, loading" );
						
						// vuze template but user hasn't yet loaded it
						
					try{
						engine = MetaSearchManagerFactory.getSingleton().getMetaSearch().addEngine( id );
									
						return( engine );
						
					}catch( Throwable e ){
					
						throw( new SubscriptionException( "Failed to load engine '" + id + "'", e ));
					}
				}
			}catch( Throwable e ){
				
				log( "Failed to load search template", e );
			}
		}
		
		engine = subs.extractEngine( json_map, id );
		
		if ( engine != null ){
			
			return( engine );
		}
		
		throw( new SubscriptionException( "Failed to extract engine id " + id ));
	}
	
	protected SubscriptionResultImpl[]
	loadResults(
		SubscriptionImpl			subs )
	{
		List	results = new ArrayList();
		
		try{
			File	f = getResultsFile( subs );
			
			Map	map = FileUtil.readResilientFile( f );
			
			List	list = (List)map.get( "results" );
			
			if ( list != null ){
			
				SubscriptionHistoryImpl	history = (SubscriptionHistoryImpl)subs.getHistory();
				
				for (int i=0;i<list.size();i++){
					
					Map	result_map =(Map)list.get(i);
					
					try{
						SubscriptionResultImpl result = new SubscriptionResultImpl( history, result_map );
						
						results.add( result );
						
					}catch( Throwable e ){
						
						log( "Failed to decode result '" + result_map + "'", e );
					}
				}
			}
			
		}catch( Throwable e ){
			
			log( "Failed to load results for '" + subs.getName() + "' - continuing with empty result set", e );
		}
		
		return((SubscriptionResultImpl[])results.toArray( new SubscriptionResultImpl[results.size()] ));
	}
	
	protected void
  	setCategoryOnExisting(
  		SubscriptionImpl	subscription,
  		String				old_category,
  		String				new_category )
  	{
		PluginInterface default_pi = PluginInitializer.getDefaultInterface();

  		Download[] downloads 	= default_pi.getDownloadManager().getDownloads();
  		 		 		
  		for ( Download d: downloads ){
  			 			
  			if ( subscriptionExists( d, subscription )){
  					
				String existing = d.getAttribute( ta_category );

				if ( existing == null || existing.equals( old_category )){
					
					d.setAttribute( ta_category, new_category );
				}
  			}
  		}
  	}
	
	public int
	getMaxNonDeletedResults()
	{
		return( COConfigurationManager.getIntParameter( CONFIG_MAX_RESULTS ));
	}
	
	public void
	setMaxNonDeletedResults(
		int		max )
	{
		if ( max != getMaxNonDeletedResults()){
			
			COConfigurationManager.setParameter( CONFIG_MAX_RESULTS, max );
		}
	}
	
	public boolean
	getAutoStartDownloads()
	{
		return( COConfigurationManager.getBooleanParameter( CONFIG_AUTO_START_DLS ));		
	}
	
	public void
	setAutoStartDownloads(
		boolean		auto_start )
	{
		if ( auto_start != getAutoStartDownloads()){
			
			COConfigurationManager.setParameter( CONFIG_AUTO_START_DLS, auto_start );
		}		
	}
	
	public int
	getAutoStartMinMB()
	{
		return( COConfigurationManager.getIntParameter( CONFIG_AUTO_START_MIN_MB ));
	}
	
	public void
	setAutoStartMinMB(
		int			mb )
	{
		if ( mb != getAutoStartMinMB()){
			
			COConfigurationManager.setParameter( CONFIG_AUTO_START_MIN_MB, mb );
		}
	}

	public int
	getAutoStartMaxMB()
	{
		return( COConfigurationManager.getIntParameter( CONFIG_AUTO_START_MAX_MB ));
	}
	
	public void
	setAutoStartMaxMB(
		int			mb )
	{
		if ( mb != getAutoStartMaxMB()){
			
			COConfigurationManager.setParameter( CONFIG_AUTO_START_MAX_MB, mb );
		}
	}
	
	protected boolean
	shouldAutoStart(
		Torrent		torrent )
	{
		if ( getAutoStartDownloads()){
			
			long	min = getAutoStartMinMB()*1024*1024L;
			long	max = getAutoStartMaxMB()*1024*1024L;
			
			if ( min <= 0 && max <= 0 ){
				
				return( true );
			}
			
			long size = torrent.getSize();
			
			if ( min > 0 && size < min ){
				
				return( false );
			}
			
			if ( max > 0 && size > max ){
				
				return( false );
			}
			
			return( true );
			
		}else{
			
			return( false );
		}
	}
	
	protected void
 	saveResults(
 		SubscriptionImpl			subs,
 		SubscriptionResultImpl[]	results )
 	{
		try{
			File	f = getResultsFile( subs );
	
			Map	map = new HashMap();
			
			List	list = new ArrayList( results.length );
			
			map.put( "results", list );
			
			for (int i=0;i<results.length;i++){
				
				list.add( results[i].toBEncodedMap());
			}
			
			FileUtil.writeResilientFile( f, map );
			
		}catch( Throwable e ){
			
			log( "Failed to save results for '" + subs.getName(), e );
		}
 	}
	
	protected void
	loadConfig()
	{
		if ( !FileUtil.resilientConfigFileExists( CONFIG_FILE )){
			
			return;
		}
		
		log( "Loading configuration" );
		
		boolean	some_are_mine = false;
		
		synchronized( this ){
			
			Map map = FileUtil.readResilientConfigFile( CONFIG_FILE );
			
			List	l_subs = (List)map.get( "subs" );
			
			if ( l_subs != null ){
				
				for (int i=0;i<l_subs.size();i++){
					
					Map	m = (Map)l_subs.get(i);
					
					try{
						SubscriptionImpl sub = new SubscriptionImpl( this, m );
						
						subscriptions.add( sub );
						
						if ( sub.isMine()){
							
							some_are_mine = true;
						}
						
						log( "    loaded " + sub.getString());
						
					}catch( Throwable e ){
						
						log( "Failed to import subscription from " + m, e );
					}
				}
			}
		}
		
		if ( some_are_mine ){
							
			addMetaSearchListener();
		}
	}
	
	protected void
	configDirty(
		SubscriptionImpl		subs )
	{
		changeSubscription( subs );
		
		configDirty();
	}
	
	protected void
	configDirty()
	{
		synchronized( this ){
			
			if ( config_dirty ){
				
				return;
			}
			
			config_dirty = true;
		
			new DelayedEvent( 
				"Subscriptions:save", 5000,
				new AERunnable()
				{
					public void 
					runSupport() 
					{
						synchronized( this ){
							
							if ( !config_dirty ){

								return;
							}
							
							saveConfig();
						}	
					}
				});
		}
	}
	
	protected void
	saveConfig()
	{
		log( "Saving configuration" );
		
		synchronized( this ){
			
			config_dirty = false;
			
			if ( subscriptions.size() == 0 ){
				
				FileUtil.deleteResilientConfigFile( CONFIG_FILE );
				
			}else{
				
				Map map = new HashMap();
				
				List	l_subs = new ArrayList();
				
				map.put( "subs", l_subs );
				
				Iterator	it = subscriptions.iterator();
				
				while( it.hasNext()){
					
					SubscriptionImpl sub = (SubscriptionImpl)it.next();
						
					try{
						l_subs.add( sub.toMap());
						
					}catch( Throwable e ){
						
						log( "Failed to save subscription " + sub.getString(), e );
					}
				}
				
				FileUtil.writeResilientConfigFile( CONFIG_FILE, map );
			}
		}
	}
	
	protected synchronized AEDiagnosticsLogger
	getLogger()
	{
		if ( logger == null ){
			
			logger = AEDiagnostics.getLogger( LOGGER_NAME );
		}
		
		return( logger );
	}
	
	public void 
	log(
		String 		s,
		Throwable 	e )
	{
		AEDiagnosticsLogger diag_logger = getLogger();
		
		diag_logger.log( s );
		diag_logger.log( e );
	}
	
	public void 
	log(
		String 	s )
	{
		AEDiagnosticsLogger diag_logger = getLogger();
		
		diag_logger.log( s );
	}
	
	public void
	addListener(
		SubscriptionManagerListener	listener )
	{
		listeners.add( listener );
	}
	
	public void
	removeListener(
		SubscriptionManagerListener	listener )
	{
		listeners.remove( listener );
	}
	
	public void
	generate(
		IndentWriter		writer )
	{
		writer.println( "Subscriptions" );
			
		try{
			writer.indent();

			Subscription[] subs = getSubscriptions();
			
			for (int i=0;i<subs.length;i++){
				
				SubscriptionImpl sub = (SubscriptionImpl)subs[i];
				
				sub.generate( writer );
			}
			
		}finally{
			
			writer.exdent();
		}
	}
	
	public static void
	main(
		String[]	args )
	{
		final String 	NAME 	= "lalalal";
		final String	URL_STR	= "http://www.vuze.com/feed/publisher/ALL/1";
		
		try{
			//AzureusCoreFactory.create();
			/*
			Subscription subs = 
				getSingleton(true).createSingletonRSS(
						NAME,
						new URL( URL_STR ),
						240 );
			
			subs.getVuzeFile().write( new File( "C:\\temp\\srss.vuze" ));
			
			subs.remove();
			*/
			
			VuzeFile	vf = VuzeFileHandler.getSingleton().create();
			
			Map	map = new HashMap();
			
			map.put( "name", NAME );
			map.put( "url", URL_STR );
			map.put( "public", new Long( 0 ));
			map.put( "check_interval_mins", new Long( 345 ));
			
			vf.addComponent( VuzeFileComponent.COMP_TYPE_SUBSCRIPTION_SINGLETON, map );
			
			vf.write( new File( "C:\\temp\\srss_2.vuze" ) );

		}catch( Throwable e ){
			
			e.printStackTrace();
		}
	}
}
