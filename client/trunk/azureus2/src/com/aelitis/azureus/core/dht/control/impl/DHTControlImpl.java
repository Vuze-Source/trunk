/*
 * Created on 12-Jan-2005
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

package com.aelitis.azureus.core.dht.control.impl;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;

import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.AESemaphore;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.HashWrapper;
import org.gudy.azureus2.core3.util.SHA1Hasher;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.ThreadPool;
import org.gudy.azureus2.plugins.logging.LoggerChannel;

import com.aelitis.azureus.core.dht.DHTOperationListener;
import com.aelitis.azureus.core.dht.impl.*;
import com.aelitis.azureus.core.dht.control.*;
import com.aelitis.azureus.core.dht.db.*;
import com.aelitis.azureus.core.dht.router.*;
import com.aelitis.azureus.core.dht.transport.*;

/**
 * @author parg
 *
 */

public class 
DHTControlImpl 
	implements DHTControl, DHTTransportRequestHandler
{
	private static final int EXTERNAL_LOOKUP_CONCURRENCY	= 3;
	private static final int EXTERNAL_PUT_CONCURRENCY		= 3;
	
	private DHTTransport			transport;
	private DHTTransportContact		local_contact;
	
	private DHTRouter		router;
	
	private DHTDB			database;
	
	private DHTControlStats	stats;
	
	private LoggerChannel	logger;
	
	private	int			node_id_byte_count;
	private int			search_concurrency;
	private int			lookup_concurrency;
	private int			cache_at_closest_n;
	private int			K;
	private int			B;
	private int			max_rep_per_node;
	
	
	private ThreadPool	internal_lookup_pool;
	private ThreadPool	external_lookup_pool;
	private ThreadPool	put_pool;
	
	private Map			imported_state	= new HashMap();
	
	public
	DHTControlImpl(
		DHTTransport	_transport,
		int				_K,
		int				_B,
		int				_max_rep_per_node,
		int				_search_concurrency,
		int				_lookup_concurrency,
		int				_original_republish_interval,
		int				_cache_republish_interval,
		int				_cache_at_closest_n,
		int				_max_values_stored,
		LoggerChannel	_logger )
	{
		transport	= _transport;
		logger		= _logger;
		
		K								= _K;
		B								= _B;
		max_rep_per_node				= _max_rep_per_node;
		search_concurrency				= _search_concurrency;
		lookup_concurrency				= _lookup_concurrency;
		cache_at_closest_n				= _cache_at_closest_n;
		
		database = DHTDBFactory.create( 
						_original_republish_interval,
						_cache_republish_interval,
						_max_values_stored,
						logger );
		
		if ( lookup_concurrency > 1 ){
			
			internal_lookup_pool = new ThreadPool("DHTControl:internallookups", lookup_concurrency );
		}
		
		external_lookup_pool 	= new ThreadPool("DHTControl:externallookups", EXTERNAL_LOOKUP_CONCURRENCY );
		put_pool 				= new ThreadPool("DHTControl:puts", EXTERNAL_PUT_CONCURRENCY );

		createRouter( transport.getLocalContact());

		node_id_byte_count	= router.getID().length;

		stats = new DHTControlStats( this );
		
		transport.setRequestHandler( this );
	
		transport.addListener(
			new DHTTransportListener()
			{
				public void
				localContactChanged(
					DHTTransportContact	new_local_contact )
				{
					logger.log( "Transport ID changed, recreating router" );
					
					List	contacts = router.findBestContacts( 0 );
					
					DHTRouter	old_router = router;
					
					createRouter( new_local_contact );
				
					for (int i=0;i<contacts.size();i++){
						
						DHTRouterContact	contact = (DHTRouterContact)contacts.get(i);
					
						if ( !old_router.isID( contact.getID())){
							
							if ( contact.isAlive()){
								
								router.contactAlive( contact.getID(), contact.getAttachment());
								
							}else{
								
								router.contactKnown( contact.getID(), contact.getAttachment());
							}
						}
						
					}
					
					seed();
				}
			});
		

	}
	
	protected void
	createRouter(
		DHTTransportContact		_local_contact)
	{		
		local_contact	= _local_contact;
		
		router	= DHTRouterFactory.create( 
					K, B, max_rep_per_node,
					local_contact.getID(), 
					new DHTControlContactImpl( local_contact ),
					logger);
		
		router.setAdapter( 
			new DHTRouterAdapter()
			{
				public void
				requestPing(
					DHTRouterContact	contact )
				{
					DHTControlImpl.this.requestPing( contact );
				}
				
				public void
				requestLookup(
					byte[]		id )
				{
					lookup( internal_lookup_pool, 
							id, 
							false, 
							0, 
							search_concurrency, 
							1,
							new lookupResultHandler()
							{
								public void
								searching(
									DHTTransportContact	contact,
									int					level,
									int					active_searches )
								{
								}
								
								public void
								read(
									DHTTransportContact	contact,
									DHTTransportValue	value )
								{
								}
								
								public void
								wrote(
									DHTTransportContact	contact,
									DHTTransportValue	value )
								{
								}
								public void
								complete(
									boolean				timeout )
								{
								}
								
								public void
								closest(
									List		res )
								{
								}						
							});
				}
				
				public void
				requestAdd(
					DHTRouterContact	contact )
				{
					nodeAddedToRouter( contact );
				}
			});	
		
		database.setControl( this );
	}
	
	public DHTTransport
	getTransport()
	{
		return( transport );
	}
	
	public DHTRouter
	getRouter()
	{
		return( router );
	}
	
	public DHTDB
	getDataBase()
	{
		return( database );
	}
	
	public void
	contactImported(
		DHTTransportContact	contact )
	{
		byte[]	id = contact.getID();
		
		router.contactKnown( id, new DHTControlContactImpl(contact));
	}
	
	public void
	exportState(
		DataOutputStream	daos,
		int					max )
	
		throws IOException
	{
			/*
			 * We need to be a bit smart about exporting state to deal with the situation where a
			 * DHT is started (with good import state) and then stopped before the goodness of the
			 * state can be re-established. So we remember what we imported and take account of this
			 * on a re-export
			 */
		
			// get all the contacts
		
		List	contacts = router.findBestContacts( 0 );
		
			// give priority to any that were alive before and are alive now
		
		List	to_save 	= new ArrayList();
		List	reserves	= new ArrayList();
		
		//System.out.println( "Exporting" );
		
		for (int i=0;i<contacts.size();i++){
		
			DHTRouterContact	contact = (DHTRouterContact)contacts.get(i);
			
			Object[]	imported = (Object[])imported_state.get( new HashWrapper( contact.getID()));
			
			if ( imported != null ){

				if ( contact.isAlive()){
					
						// definitely want to keep this one
					
					to_save.add( contact );
					
				}else if ( !contact.isFailing()){
					
						// dunno if its still good or not, however its got to be better than any
						// new ones that we didn't import who aren't known to be alive
					
					reserves.add( contact );
				}
			}
		}
		
		//System.out.println( "    initial to_save = " + to_save.size() + ", reserves = " + reserves.size());
		
			// now pull out any live ones
		
		for (int i=0;i<contacts.size();i++){
			
			DHTRouterContact	contact = (DHTRouterContact)contacts.get(i);
		
			if ( contact.isAlive() && !to_save.contains( contact )){
				
				to_save.add( contact );
			}
		}
		
		//System.out.println( "    after adding live ones = " + to_save.size());
		
			// now add any reserve ones
		
		for (int i=0;i<reserves.size();i++){
			
			DHTRouterContact	contact = (DHTRouterContact)reserves.get(i);
		
			if ( !to_save.contains( contact )){
				
				to_save.add( contact );
			}
		}
		
		//System.out.println( "    after adding reserves = " + to_save.size());

			// now add in the rest!
		
		for (int i=0;i<contacts.size();i++){
			
			DHTRouterContact	contact = (DHTRouterContact)contacts.get(i);
		
			if (!to_save.contains( contact )){
				
				to_save.add( contact );
			}
		}	
		
		//System.out.println( "    finally = " + to_save.size());

		int	num_to_write = Math.min( max, to_save.size());
		
		daos.writeInt( num_to_write );
				
		for (int i=0;i<num_to_write;i++){
			
			DHTRouterContact	contact = (DHTRouterContact)to_save.get(i);
			
			//System.out.println( "export:" + contact.getString());
			
			daos.writeLong( contact.getTimeAlive());
			
			DHTTransportContact	t_contact = ((DHTControlContactImpl)contact.getAttachment()).getContact();
			
			try{
				
				t_contact.exportContact( daos );
				
			}catch( DHTTransportException e ){
				
					// shouldn't fail as for a contact to make it to the router 
					// it should be valid...
				
				Debug.printStackTrace( e );
				
				throw( new IOException( e.getMessage()));
			}
		}
		
		daos.flush();
	}
		
	public void
	importState(
		DataInputStream		dais )
		
		throws IOException
	{
		int	num = dais.readInt();
		
		for (int i=0;i<num;i++){
			
			try{
				
				long	time_alive = dais.readLong();
				
				DHTTransportContact	contact = transport.importContact( dais );
								
				imported_state.put( new HashWrapper( contact.getID()), new Object[]{ new Long( time_alive ), contact });
				
			}catch( DHTTransportException e ){
				
				Debug.printStackTrace( e );
			}
		}
	}
	
	public void
	seed()
	{
		final AESemaphore	sem = new AESemaphore( "DHTControl:seed" );
		
		lookup( internal_lookup_pool,
				router.getID(), 
				false, 
				0,
				search_concurrency*4,
				1,
				new lookupResultHandler()
				{
					public void
					searching(
						DHTTransportContact	contact,
						int					level,
						int					active_searches )
					{
						// System.out.println( "Seed: searching " + level + ", active = " + active_searches + ":" + contact.getString());
					}
					
					public void
					read(
						DHTTransportContact	contact,
						DHTTransportValue	value )
					{
					}
					
					public void
					wrote(
						DHTTransportContact	contact,
						DHTTransportValue	value )
					{
					}
					
					public void
					complete(
						boolean				timeout )
					{
					}
					
					public void
					closest(
						List		res )
					{
						try{
							router.seed();
							
						}finally{
							
							sem.release();
						}
					}
				});
		
		sem.reserve();
	}
	
	public void
	put(
		final byte[]		_unencoded_key,
		final byte[]		_value )
	{
		put( _unencoded_key, _value, null );
	}

	public void
	put(
		byte[]					_unencoded_key,
		byte[]					_value,
		DHTOperationListener	_listener )
	{
		byte[]	encoded_key = encodeKey( _unencoded_key );
		
		DHTLog.log( "put for " + DHTLog.getString( encoded_key ));
		
		DHTDBValue	value = database.store( new HashWrapper( encoded_key ), _value );
		
		put( encoded_key, value, 0, _listener );		
	}
	
	public void
	put(
		final byte[]			encoded_key,
		final DHTTransportValue	value,
		final long				timeout )
	{
		put( encoded_key, value, timeout, null );
	}
	
	
	protected void
	put(
		final byte[]				encoded_key,
		final DHTTransportValue		value,
		final long					timeout,
		final DHTOperationListener	listener )
	{
		lookup( put_pool,
				encoded_key, 
				false, 
				timeout,
				search_concurrency,
				1,
				new lookupResultHandler()
				{
					public void
					searching(
						DHTTransportContact	_contact,
						int					_level,
						int					_active_searches )
					{	
						if ( listener != null ){
							
							listener.searching( _contact, _level, _active_searches );
						}
					}
					
					public void
					read(
						DHTTransportContact	_contact,
						DHTTransportValue	_value )
					{	
						if ( listener != null ){
							
							listener.read( _contact, _value );
						}
					}
					
					public void
					wrote(
						DHTTransportContact	_contact,
						DHTTransportValue	_value )
					{	
						if ( listener != null ){
							
							listener.wrote( _contact, _value );
						}
					}
					
					public void
					complete(
						boolean				_timeout )
					{	
						if ( listener != null ){
							
							listener.complete( _timeout );
						}
					}
					
					public void
					closest(
						List				_closest )
					{
						put( new byte[][]{ encoded_key }, new DHTTransportValue[][]{{ value }}, _closest, listener );		
					}
				});
	}
	
	public void
	put(
		byte[][]				encoded_keys,
		DHTTransportValue[][]	value_sets,
		List					contacts )
	{
		put( encoded_keys, value_sets, contacts, null );
	}
		
	public void
	put(
		byte[][]				encoded_keys,
		DHTTransportValue[][]	value_sets,
		List					contacts,
		DHTOperationListener	listener )
	{
		for (int i=0;i<contacts.size();i++){
		
			DHTTransportContact	contact = (DHTTransportContact)contacts.get(i);
			
			if ( !router.isID( contact.getID())){
					
				if ( listener != null ){
					
					for (int j=0;j<value_sets.length;j++){
						for (int k=0;k<value_sets[j].length;k++){
							listener.wrote( contact, value_sets[j][k] );
						}
					}
				}
				
				contact.sendStore( 
					new DHTTransportReplyHandlerAdapter()
					{
						public void
						storeReply(
							DHTTransportContact _contact )
						{
							DHTLog.log( "store ok" );
							
							router.contactAlive( _contact.getID(), new DHTControlContactImpl(_contact));
						}	
						
						public void
						failed(
							DHTTransportContact 	_contact,
							Throwable 				_error )
						{
							DHTLog.log( "store failed" );
														
							router.contactDead( _contact.getID(), new DHTControlContactImpl(_contact));
						}
					},
					encoded_keys, 
					value_sets );
			}
		}
	}
	
	public byte[]
	get(
		byte[]		unencoded_key,
		long		timeout )
	{
		final AESemaphore			sem		= new AESemaphore( "DHTControl:get" );

		final byte[][]	res 	= new byte[1][];
		
		get(
			unencoded_key,
			1,
			timeout,
			new DHTOperationListener()
			{
				public void
				searching(
					DHTTransportContact	contact,
					int					level,
					int					active_searches )
				{
				}
				
				public void
				read(
					DHTTransportContact	contact,
					DHTTransportValue	value )
				{
					res[0]	= value.getValue();
				}
				
				public void
				wrote(
					DHTTransportContact	contact,
					DHTTransportValue	value )
				{
				}
				
				public void
				complete(
					boolean				_timeout_occurred )
				{
					sem.release();
				}
			});
		
		sem.reserve( timeout );
		
		return( res[0] );
	}
	
	public void
	get(
		byte[]					unencoded_key,
		int						max_values,
		long					timeout,
		final DHTOperationListener	get_listener )
	{
		final byte[]	encoded_key = encodeKey( unencoded_key );

		DHTLog.log( "get for " + DHTLog.getString( encoded_key ));
		
		lookup( external_lookup_pool,
				encoded_key, 
				true, 
				timeout,
				search_concurrency,
				max_values,
				new lookupResultHandler()
				{
					private List	found_values	= new ArrayList();
					
					public void
					searching(
						DHTTransportContact	contact,
						int					level,
						int					active_searches )
					{	
						get_listener.searching( contact, level, active_searches );
					}
					
					public void
					read(
						DHTTransportContact	contact,
						DHTTransportValue	value )
					{	
						found_values.add( value );
						
						get_listener.read( contact, value );
					}
					
					public void
					wrote(
						DHTTransportContact	contact,
						DHTTransportValue	value )
					{	
						get_listener.wrote( contact, value );
					}
					
					public void
					complete(
						boolean				timeout_occurred )
					{	
						get_listener.complete( timeout_occurred );
					}
	
					public void
					closest(
						List	closest )
					{
							// TODO: multiple values!!!!
							// TODO: also, make sure we don't store values at the same
							// place they came from!
						
						if ( found_values.size() > 0 ){
								
							DHTTransportValue	value = (DHTTransportValue)found_values.get(0);
							
								// cache the value at the 'n' closest seen locations
							
							for (int i=0;i<Math.min(cache_at_closest_n,closest.size());i++){
								
								DHTTransportContact	contact = (DHTTransportContact)(DHTTransportContact)closest.get(i);
								
								wrote( contact, value );
								
								contact.sendStore( 
										new DHTTransportReplyHandlerAdapter()
										{
											public void
											storeReply(
												DHTTransportContact _contact )
											{
												DHTLog.log( "cache store ok" );
												
												router.contactAlive( _contact.getID(), new DHTControlContactImpl(_contact));
											}	
											
											public void
											failed(
												DHTTransportContact 	_contact,
												Throwable 				_error )
											{
												DHTLog.log( "cache store failed" );
												
												router.contactDead( _contact.getID(), new DHTControlContactImpl(_contact));
											}
										},
										new byte[][]{ encoded_key }, 
										new DHTTransportValue[][]{{ value }});
							}
						}
					}
				});
	}
	
	public byte[]
	remove(
		byte[]		unencoded_key )
	{
		return( remove( unencoded_key, null ));
	}
	
	public byte[]
	remove(
		byte[]					unencoded_key,
		DHTOperationListener	listener )
	{
		
			// TODO: push the deletion out rather than letting values timeout
		
		final byte[]	encoded_key = encodeKey( unencoded_key );

		DHTLog.log( "remove for " + DHTLog.getString( encoded_key ));

		DHTDBValue	res = database.remove( local_contact, new HashWrapper( encoded_key ));
		
		if ( res == null ){
			
			return( null );
			
		}else{
			
			return( res.getValue());
		}
	}
	
		/**
		 * The lookup method returns up to K closest nodes to the target
		 * @param lookup_id
		 * @return
		 */
	
	protected void
	lookup(
		ThreadPool					thread_pool,
		final byte[]				lookup_id,
		final boolean				value_search,
		final long					timeout,
		final int					concurrency,
		final int					max_values,
		final lookupResultHandler	handler )
	{
		if ( thread_pool == null ){
			
			try{
				lookupSupport( lookup_id, value_search, timeout, concurrency, max_values, handler );
	
			}catch( Throwable e ){
				
				Debug.printStackTrace(e);
				
				if ( handler != null ){
					
					handler.complete( true );
				}
			}
		}else{
			
			thread_pool.run(
				new AERunnable()
				{
					public void
					runSupport()
					{
						try{
							lookupSupport( lookup_id, value_search, timeout, concurrency, max_values, handler );
							
						}catch( Throwable e ){
							
							Debug.printStackTrace(e);
							
							if ( handler != null ){
								
								handler.complete( true );
							}
						}
					}
				});
		}
	}
	
	protected void
	lookupSupport(
		final byte[]				lookup_id,
		boolean						value_search,
		long						timeout,
		int							concurrency,
		int							max_values,
		final lookupResultHandler	result_handler )
	{
		boolean		timeout_occurred	= false;
		
		final byte	flags = 0;
		
		try{
			DHTLog.log( "lookup for " + DHTLog.getString( lookup_id ));
			
				// keep querying successively closer nodes until we have got responses from the K
				// closest nodes that we've seen. We might get a bunch of closer nodes that then
				// fail to respond, which means we have reconsider further away nodes
			
				// we keep a list of nodes that we have queried to avoid re-querying them
			
				// we keep a list of nodes discovered that we have yet to query
			
				// we have a parallel search limit of A. For each A we effectively loop grabbing
				// the currently closest unqueried node, querying it and adding the results to the
				// yet-to-query-set (unless already queried)
			
				// we terminate when we have received responses from the K closest nodes we know
				// about (excluding failed ones)
			
				// Note that we never widen the root of our search beyond the initial K closest
				// that we know about - this could be relaxed
			
						
				// contacts remaining to query
				// closest at front
	
			final Set		contacts_to_query	= getClosestContactsSet( lookup_id, false );
			
			final AEMonitor	contacts_to_query_mon	= new AEMonitor( "DHTControl:ctq" );

			final Map	level_map			= new HashMap();
			
			Iterator	it = contacts_to_query.iterator();
			
			while( it.hasNext()){
				
				level_map.put( it.next(), new Integer(0));
			}
			
				// record the set of contacts we've queried to avoid re-queries
			
			final Map			contacts_queried = new HashMap();
			
				// record the set of contacts that we've had a reply from
				// furthest away at front
			
			final Set			ok_contacts = new sortedContactSet( lookup_id, false ).getSet(); 
			
	
				// this handles the search concurrency
			
			final AESemaphore	search_sem = new AESemaphore( "DHTControl:search", concurrency );
				
			final int[]	idle_searches	= { 0 };
			final int[]	active_searches	= { 0 };
				
			final int[]	values_found	= { 0 };
			final int[]	value_replies	= { 0 };
			final Set	values_found_set	= new HashSet();
			
			long	start = SystemTime.getCurrentTime();
	
			while( true ){
				
				if ( timeout > 0 ){
					
					long	now = SystemTime.getCurrentTime();
					
					long remaining = timeout - ( now - start );
						
					if ( remaining <= 0 ){
						
						DHTLog.log( "lookup: terminates - timeout" );
	
						timeout_occurred	= true;
						
						break;
						
					}
						// get permission to kick off another search
					
					if ( !search_sem.reserve( remaining )){
						
						DHTLog.log( "lookup: terminates - timeout" );
	
						timeout_occurred	= true;
						
						break;
					}
				}else{
					
					search_sem.reserve();
				}
					
				try{
					contacts_to_query_mon.enter();
			
					if ( 	values_found[0] >= max_values ||
							value_replies[0]>= 3 ){	// all hits should have the same values anyway...	
							
						break;
					}						

						// if nothing pending then we need to wait for the results of a previous
						// search to arrive. Of course, if there are no searches active then
						// we've run out of things to do
					
					if ( contacts_to_query.size() == 0 ){
						
						if ( active_searches[0] == 0 ){
							
							DHTLog.log( "lookup: terminates - no contacts left to query" );
							
							break;
						}
						
						idle_searches[0]++;
						
						continue;
					}
				
						// select the next contact to search
					
					DHTTransportContact	closest	= (DHTTransportContact)contacts_to_query.iterator().next();			
				
						// if the next closest is further away than the furthest successful hit so 
						// far and we have K hits, we're done
					
					if ( ok_contacts.size() == router.getK()){
						
						DHTTransportContact	furthest_ok = (DHTTransportContact)ok_contacts.iterator().next();
						
						byte[]	furthest_ok_distance 	= computeDistance( furthest_ok.getID(), lookup_id );
						byte[]	closest_distance		= computeDistance( closest.getID(), lookup_id );
						
						if ( compareDistances( furthest_ok_distance, closest_distance) <= 0 ){
							
							DHTLog.log( "lookup: terminates - we've searched the closest K contacts" );
	
							break;
						}
					}
					
					contacts_to_query.remove( closest );
	
					contacts_queried.put( new HashWrapper( closest.getID()), "" );
								
						// never search ourselves!
					
					if ( router.isID( closest.getID())){
						
						search_sem.release();
						
						continue;
					}
	
					final int	search_level = ((Integer)level_map.get(closest)).intValue();

					active_searches[0]++;				
					
					result_handler.searching( closest, search_level, active_searches[0] );
					
					DHTTransportReplyHandlerAdapter	handler = 
						new DHTTransportReplyHandlerAdapter()
						{
							public void
							findNodeReply(
								DHTTransportContact 	target_contact,
								DHTTransportContact[]	reply_contacts )
							{
								try{
									DHTLog.log( "findNodeReply: " + DHTLog.getString( reply_contacts ));
							
									router.contactAlive( target_contact.getID(), new DHTControlContactImpl(target_contact));
									
									for (int i=0;i<reply_contacts.length;i++){
										
										DHTTransportContact	contact = reply_contacts[i];
										
											// ignore responses that are ourselves
										
										if ( compareDistances( router.getID(), contact.getID()) == 0 ){
											
											continue;
										}
										
											// dunno if its alive or not, however record its existance
										
										router.contactKnown( contact.getID(), new DHTControlContactImpl(contact));
									}
									
									try{
										contacts_to_query_mon.enter();
												
										ok_contacts.add( target_contact );
										
										if ( ok_contacts.size() > router.getK()){
											
												// delete the furthest away
											
											Iterator ok_it = ok_contacts.iterator();
											
											ok_it.next();
											
											ok_it.remove();
										}
										
										for (int i=0;i<reply_contacts.length;i++){
											
											DHTTransportContact	contact = reply_contacts[i];
											
												// ignore responses that are ourselves
											
											if ( compareDistances( router.getID(), contact.getID()) == 0 ){
												
												continue;
											}
																						
											if (	contacts_queried.get( new HashWrapper( contact.getID())) == null &&
													!contacts_to_query.contains( contact )){
												
												DHTLog.log( "    new contact for query: " + DHTLog.getString( contact ));
												
												contacts_to_query.add( contact );
												
												level_map.put( contact, new Integer( search_level+1));
				
												if ( idle_searches[0] > 0 ){
													
													idle_searches[0]--;
													
													search_sem.release();
												}
											}else{
												
												// DHTLog.log( "    already queried: " + DHTLog.getString( contact ));
											}
										}
									}finally{
										
										contacts_to_query_mon.exit();
									}
								}finally{
									
									try{
										contacts_to_query_mon.enter();

										active_searches[0]--;
										
									}finally{
										
										contacts_to_query_mon.exit();
									}
		
									search_sem.release();
								}
							}
							
							public void
							findValueReply(
								DHTTransportContact 	contact,
								DHTTransportValue[]		values )
							{
								try{
									DHTLog.log( "findValueReply: " + DHTLog.getString( values ));
									
									router.contactAlive( contact.getID(), new DHTControlContactImpl(contact));
									
									int	new_values = 0;
									
									for (int i=0;i<values.length;i++){
										
										DHTTransportValue	value = values[i];
										
										DHTTransportContact	originator = value.getOriginator();
										
										if ( !values_found_set.contains( originator.getAddress())){
											
											new_values++;
											
											values_found_set.add( originator.getAddress());
											
											result_handler.read( originator, values[i] );
										}
									}
									
										// TODO: remove duplicates 
									
									try{
										contacts_to_query_mon.enter();

										value_replies[0]++;
										
										values_found[0] += new_values;
										
									}finally{
										
										contacts_to_query_mon.exit();
									}
		
								}finally{
									
									try{
										contacts_to_query_mon.enter();

										active_searches[0]--;
										
									}finally{
										
										contacts_to_query_mon.exit();
									}
									
									search_sem.release();
								}						
							}
							
							public void
							findValueReply(
								DHTTransportContact 	contact,
								DHTTransportContact[]	contacts )
							{
								findNodeReply( contact, contacts );
							}
							
							public void
							failed(
								DHTTransportContact 	target_contact,
								Throwable 				error )
							{
								try{
									DHTLog.log( "Reply: findNode/findValue -> failed" );
									
									router.contactDead( target_contact.getID(), new DHTControlContactImpl(target_contact));
		
								}finally{
									
									try{
										contacts_to_query_mon.enter();

										active_searches[0]--;
										
									}finally{
										
										contacts_to_query_mon.exit();
									}
									
									search_sem.release();
								}
							}
						};
						
					router.recordLookup( lookup_id );
					
					if ( value_search ){
						
						int	rem = max_values - values_found[0];
						
						if ( rem <= 0 ){
							
							Debug.out( "eh?" );
							
							rem = 1;
						}
						
						closest.sendFindValue( handler, lookup_id, rem, flags );
						
					}else{
						
						closest.sendFindNode( handler, lookup_id );
					}
				}finally{
					
					contacts_to_query_mon.exit();
				}
			}
			
				// maybe unterminated searches still going on so protect ourselves
				// against concurrent modification of result set
			
			List	closest_res;
			
			try{
				contacts_to_query_mon.enter();
	
				DHTLog.log( "lookup complete for " + DHTLog.getString( lookup_id ));
				
				DHTLog.log( "    queried = " + DHTLog.getString( contacts_queried ));
				DHTLog.log( "    to query = " + DHTLog.getString( contacts_to_query ));
				DHTLog.log( "    ok = " + DHTLog.getString( ok_contacts ));
				
				closest_res	= new ArrayList( ok_contacts );
				
					// we need to reverse the list as currently closest is at
					// the end
			
				Collections.reverse( closest_res );
				
			}finally{
				
				contacts_to_query_mon.exit();
			}
			
			result_handler.closest( closest_res );
			
		}finally{
			
			result_handler.complete( timeout_occurred );
		}
	}
	
	
		// Request methods
	
	public void
	pingRequest(
		DHTTransportContact originating_contact )
	{
		DHTLog.log( "pingRequest from " + DHTLog.getString( originating_contact.getID()));
			
		router.contactAlive( originating_contact.getID(), new DHTControlContactImpl(originating_contact));
	}
		
	public void
	storeRequest(
		DHTTransportContact 	originating_contact, 
		byte[][]				keys,
		DHTTransportValue[][]	value_sets )
	{
		router.contactAlive( originating_contact.getID(), new DHTControlContactImpl(originating_contact));

		for (int i=0;i<keys.length;i++){
			
			HashWrapper			key		= new HashWrapper( keys[i] );
			
			DHTTransportValue[]	values 	= value_sets[i];
		
			DHTLog.log( "storeRequest from " + DHTLog.getString( originating_contact.getID())+ ":key=" + DHTLog.getString(key) + ", value=" + DHTLog.getString(values));

			for (int j=0;j<values.length;j++){
			
				database.store( originating_contact, key, values[j] );
			}
		}
	}
	
	public DHTTransportContact[]
	findNodeRequest(
		DHTTransportContact originating_contact, 
		byte[]				id )
	{
		DHTLog.log( "findNodeRequest from " + DHTLog.getString( originating_contact.getID()));

		router.contactAlive( originating_contact.getID(), new DHTControlContactImpl(originating_contact));
		
		List	l = getClosestKContactsList( id, false );
		
		DHTTransportContact[]	res = new DHTTransportContact[l.size()];
		
		l.toArray( res );
		
		return( res );
	}
	
	public Object
	findValueRequest(
		DHTTransportContact originating_contact, 
		byte[]				key,
		int					max_values,
		byte				flags )
	{
		DHTLog.log( "findValueRequest from " + DHTLog.getString( originating_contact.getID()));

		DHTDBValue[]	values	= database.get( new HashWrapper( key ), max_values);
					
		if ( values.length > 0 ){
			
			router.contactAlive( originating_contact.getID(), new DHTControlContactImpl(originating_contact));

			return( values );
			
		}else{
			
			return( findNodeRequest( originating_contact, key ));
		}
	}
	
	public DHTTransportFullStats
	statsRequest(
		DHTTransportContact	contact )
	{
		return( stats );
	}
	
	protected void
	requestPing(
		DHTRouterContact	contact )
	{
		((DHTControlContactImpl)contact.getAttachment()).getContact().sendPing(
				new DHTTransportReplyHandlerAdapter()
				{
					public void
					pingReply(
						DHTTransportContact _contact )
					{
						DHTLog.log( "ping ok" );
						
						router.contactAlive( _contact.getID(), new DHTControlContactImpl(_contact));
					}	
					
					public void
					failed(
						DHTTransportContact 	_contact,
						Throwable				_error )
					{
						DHTLog.log( "ping failed" );
						
						router.contactDead( _contact.getID(), new DHTControlContactImpl(_contact));
					}
				});
	}
	
	protected void
	nodeAddedToRouter(
		DHTRouterContact	new_contact )
	{		
		// when a new node is added we must check to see if we need to transfer
		// any of our values to it.
		
		Map	values_to_store	= new HashMap();
		
		if( database.isEmpty()){
							
			// nothing to do, ping it if it isn't known to be alive
				
			if ( !new_contact.hasBeenAlive()){
					
				requestPing( new_contact );
			}
				
			return;
		}
			
			// see if we're one of the K closest to the new node
		
		List	closest_contacts = getClosestKContactsList( new_contact.getID(), false );
		
		boolean	close	= false;
		
		for (int i=0;i<closest_contacts.size();i++){
			
			if ( router.isID(((DHTTransportContact)closest_contacts.get(i)).getID())){
				
				close	= true;
				
				break;
			}
		}
		
		if ( !close ){
			
			if ( !new_contact.hasBeenAlive()){
				
				requestPing( new_contact );
			}

			return;
		}
			
			// ok, we're close enough to worry about transferring values				
		
		Iterator	it = database.getKeys();
		
		while( it.hasNext()){
							
			HashWrapper	key		= (HashWrapper)it.next();
			
			byte[]	encoded_key		= key.getHash();
			
			DHTDBValue[]	values	= database.get( key, 0 );
			
			if ( values.length == 0 ){
				
					// deleted in the meantime
				
				continue;
			}
			
				// TODO: store multiple values at once
			
			for (int i=0;i<values.length;i++){
				
				DHTDBValue	value = values[i];
		
				// we don't consider any cached further away than the initial location, for transfer
				// however, we *do* include ones we originate as, if we're the closest, we have to
				// take responsibility for xfer (as others won't)
			
				if ( value.getCacheDistance() > 1 ){
					
					continue;
				}
				
				List		sorted_contacts	= getClosestKContactsList( encoded_key, false ); 
				
					// if we're closest to the key, or the new node is closest and
					// we're second closest, then we take responsibility for storing
					// the value
				
				boolean	store_it	= false;
				
				if ( sorted_contacts.size() > 0 ){
					
					DHTTransportContact	first = (DHTTransportContact)sorted_contacts.get(0);
					
					if ( router.isID( first.getID())){
						
						store_it = true;
						
					}else if ( Arrays.equals( first.getID(), new_contact.getID()) && sorted_contacts.size() > 1 ){
						
						store_it = router.isID(((DHTTransportContact)sorted_contacts.get(1)).getID());
						
					}
				}
				
				if ( store_it ){
		
					values_to_store.put( key, value );
				}
			}
		}
		
		if ( values_to_store.size() > 0 ){
			
			it = values_to_store.entrySet().iterator();
			
			DHTTransportContact	t_contact = ((DHTControlContactImpl)new_contact.getAttachment()).getContact();
	
			boolean	first_value	= true;
			
			while( it.hasNext()){
				
				Map.Entry	entry = (Map.Entry)it.next();
				
				HashWrapper	key		= (HashWrapper)entry.getKey();
				
				DHTDBValue	value	= (DHTDBValue)entry.getValue();
					
				final boolean	ping_replacement = 
					first_value && !new_contact.hasBeenAlive();
				
				first_value	= false;
				
					// TODO: multiple values
				
				t_contact.sendStore( 
						new DHTTransportReplyHandlerAdapter()
						{
							public void
							storeReply(
								DHTTransportContact _contact )
							{
								DHTLog.log( "add store ok" );
								
								router.contactAlive( _contact.getID(), new DHTControlContactImpl(_contact));
							}	
							
							public void
							failed(
								DHTTransportContact 	_contact,
								Throwable				_error )
							{
									// we ignore failures when propagating values as there might be
									// a lot to propagate and one failure would remove the newly added
									// node. given that the node has just appeared, and can therefore be
									// assumed to be alive, this is acceptable behaviour. 
									// If we don't do this then we can get a node appearing and disappearing
									// and taking up loads of resources
								
									// however, for contacts that aren't known to be alive
									// we use one of the stores in place of a ping to ascertain liveness
								
								if ( ping_replacement ){
									
									DHTLog.log( "add store failed" );
									
									router.contactDead( _contact.getID(), new DHTControlContactImpl(_contact));
								}
							}
						},
						new byte[][]{ key.getHash()}, 
						new DHTTransportValue[][]{{ value.getValueForRelay( local_contact )}});
						
			}
		}else{
			
			if ( !new_contact.hasBeenAlive()){
				
				requestPing( new_contact );
			}
		}
	}
	
	protected Set
	getClosestContactsSet(
		byte[]		id,
		boolean		live_only )
	{
		List	l = router.findClosestContacts( id, live_only );
		
		Set	sorted_set	= new sortedContactSet( id, true ).getSet(); 

		for (int i=0;i<l.size();i++){
			
			sorted_set.add(((DHTControlContactImpl)((DHTRouterContact)l.get(i)).getAttachment()).getContact());
		}
		
		return( sorted_set );
	}
	
	public List
	getClosestKContactsList(
		byte[]		id,
		boolean		live_only )
	{
		Set	sorted_set	= getClosestContactsSet( id, live_only );
					
		List	res = new ArrayList(K);
		
		Iterator	it = sorted_set.iterator();
		
		while( it.hasNext() && res.size() < K ){
			
			res.add( it.next());
		}
		
		return( res );
	}
	
	protected byte[]
	encodeKey(
		byte[]		key )
	{
		byte[]	temp = new SHA1Hasher().calculateHash( key );
		
		byte[]	result =  new byte[node_id_byte_count];
		
		System.arraycopy( temp, 0, result, 0, node_id_byte_count );
		
		return( result );
	}
	
	protected static byte[]
	computeDistance(
		byte[]		n1,
		byte[]		n2 )
	{
		byte[]	res = new byte[n1.length];
		
		for (int i=0;i<res.length;i++){
			
			res[i] = (byte)( n1[i] ^ n2[i] );
		}
		
		return( res );
	}
	
		/**
		 * -ve -> n1 < n2
		 * @param n1
		 * @param n2
		 * @return
		 */
	
	protected static int
	compareDistances(
		byte[]		n1,
		byte[]		n2 )
	{
		for (int i=0;i<n1.length;i++){
			
			int diff = (n1[i]&0xff) - (n2[i]&0xff);
			
			if ( diff != 0 ){
				
				return( diff );
			}
		}
		
		return( 0 );
	}
	
	public void
	print()
	{
		router.print();
		
		database.print();
	}
	
	
	
	protected class
	sortedContactSet
	{
		private TreeSet	tree_set;
		
		private byte[]	pivot;
		private boolean	ascending;
		
		protected
		sortedContactSet(
			byte[]		_pivot,
			boolean		_ascending )
		{
			pivot		= _pivot;
			ascending	= _ascending;
			
			tree_set = new TreeSet(
				new Comparator()
				{
					public int
					compare(
						Object	o1,
						Object	o2 )
					{
							// this comparator ensures that the closest to the key
							// is first in the iterator traversal
					
						DHTTransportContact	t1 = (DHTTransportContact)o1;
						DHTTransportContact t2 = (DHTTransportContact)o2;
						
						byte[] d1 = computeDistance( t1.getID(), pivot);
						byte[] d2 = computeDistance( t2.getID(), pivot);
						
						int	distance = compareDistances( d1, d2 );
						
						if ( ascending ){
							
							return( distance );
							
						}else{
							
							return( -distance );
						}
					}
				});
		}
		
		public Set
		getSet()
		{
			return( tree_set );
		}
	}
	
	interface
	lookupResultHandler
		extends DHTOperationListener
	{
		public void
		closest(
			List		res );
	}
}
