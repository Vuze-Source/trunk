/*
 * Created on Dec 2, 2016
 * Created by Paul Gardner
 * 
 * Copyright 2016 Azureus Software, Inc.  All rights reserved.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or 
 * (at your option) any later version.
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


package com.aelitis.azureus.ui.swt.subscriptions;

import java.util.*;

import org.gudy.azureus2.core3.util.LightHashMap;
import org.gudy.azureus2.plugins.utils.search.SearchResult;

import com.aelitis.azureus.core.subs.Subscription;
import com.aelitis.azureus.core.subs.SubscriptionResult;
import com.aelitis.azureus.ui.swt.utils.SearchSubsResultBase;

public class 
SBC_SubscriptionResult 
	implements SearchSubsResultBase
{
	private final Subscription		subs;
	private final String			result_id;
	
	private final String			name;
	private final byte[]			hash;
	private final int				content_type;
	private final long				size;
	private final long				seeds_peers_sort;
	private final String			seeds_peers;
	private final long				votes_comments_sort;
	private final String			votes_comments;
	private final int				rank;
	private final long				time;
	private final String			torrent_link;
	private final String			details_link;
	private final String			category;
	
	private LightHashMap<Object,Object>	user_data;
	
	protected
	SBC_SubscriptionResult(
		Subscription		_subs,
		SubscriptionResult	_result )
	{
		subs		= _subs;
		result_id	= _result.getID();
		
		Map<Integer,Object>	properties = _result.toPropertyMap();
		
		name = (String)properties.get( SearchResult.PR_NAME );
		
		hash = (byte[])properties.get( SearchResult.PR_HASH );
		
		String type = (String)properties.get( SearchResult.PR_CONTENT_TYPE );
		
		if ( type == null || type.length() == 0 ){
			content_type = 0;
		}else{
			char c = type.charAt(0);
			
			if ( c == 'v' ){
				content_type = 1;
			}else if ( c == 'a' ){
				content_type = 2;
			}else if ( c == 'g' ){
				content_type = 3;
			}else{
				content_type = 0;
			}
		}
		
		size = (Long)properties.get( SearchResult.PR_SIZE );
		
		Date pub_date = (Date)properties.get( SearchResult.PR_PUB_DATE );
		
		if ( pub_date == null ){
			
			time = _result.getTimeFound();
			
		}else{
			
			long pt = pub_date.getTime();
			
			if ( pt <= 0 ){
				
				time = _result.getTimeFound();
				
			}else{
			
				time = pt;
			};
		}
		
		torrent_link = (String)properties.get( SearchResult.PR_TORRENT_LINK );
		details_link = (String)properties.get( SearchResult.PR_DETAILS_LINK );
		
		long seeds 		= (Long)properties.get( SearchResult.PR_SEED_COUNT );
		long leechers 	= (Long)properties.get( SearchResult.PR_LEECHER_COUNT );
		
		seeds_peers = (seeds<0?"--":String.valueOf(seeds)) + "/" + (leechers<0?"--":String.valueOf(leechers));
				
		if ( seeds < 0 ){
			seeds = 0;
		}else{
			seeds++;
		}
		
		if ( leechers < 0 ){
			leechers = 0;
		}else{
			leechers++;
		}
		
		seeds_peers_sort = ((seeds&0x7fffffff)<<32) | ( leechers & 0xffffffff );
			
		long votes		= (Long)properties.get( SearchResult.PR_VOTES );
		long comments 	= (Long)properties.get( SearchResult.PR_COMMENTS );

		if ( votes < 0 && comments < 0 ){
			
			votes_comments_sort = 0;
			votes_comments		= null;
			
		}else{

			votes_comments = (votes<0?"--":String.valueOf(votes)) + "/" + (comments<0?"--":String.valueOf(comments));

			if ( votes < 0 ){
				votes= 0;
			}else{
				votes++;
			}
			if ( comments < 0 ){
				comments= 0;
			}else{
				comments++;
			}
			
			votes_comments_sort = ((votes&0x7fffffff)<<32) | ( comments & 0xffffffff );
		}
		
		rank	 	= ((Long)properties.get( SearchResult.PR_RANK )).intValue();
		
		category = (String)properties.get( SearchResult.PR_CATEGORY );
	}
	
	public Subscription
	getSubscription()
	{
		return( subs );
	}
	
	public String
	getID()
	{
		return( result_id );
	}
	
	public final String
	getName()
	{
		return( name );
	}
	
	public byte[]
	getHash()
	{
		return( hash );
	}
	
	public int
	getContentType()
	{
		return( content_type );
	}
	
	public long
	getSize()
	{
		return( size );
	}
	
	public String
	getSeedsPeers()
	{
		return( seeds_peers );
	}
	
	public long
	getSeedsPeersSortValue()
	{
		return( seeds_peers_sort );
	}
	
	public String
	getVotesComments()
	{
		return( votes_comments );
	}
	
	public long
	getVotesCommentsSortValue()
	{
		return( votes_comments_sort );
	}
	
	public int
	getRank()
	{
		return( rank );
	}
	
	public String
	getTorrentLink()
	{
		return( torrent_link );
	}
	
	public String
	getDetailsLink()
	{
		return( details_link );
	}
	
	public String
	getCategory()
	{
		return( category );
	}
	
	public long
	getTime()
	{
		return( time );
	}
	
	public boolean
	getRead()
	{
		SubscriptionResult result = subs.getHistory().getResult( result_id );
		
		if ( result != null ){
			
			return( result.getRead());
		}
		
		return( true );
	}
	
	public void
	setRead(
		boolean		read )
	{
		SubscriptionResult result = subs.getHistory().getResult( result_id );
		
		if ( result != null ){
			
			result.setRead( read );
		}
	}
	
	public void
	delete()
	{
		SubscriptionResult result = subs.getHistory().getResult( result_id );
		
		if ( result != null ){
			
			result.delete();
		}
	}
	
	public void
	setUserData(
		Object	key,
		Object	data )
	{
		synchronized( this ){
			if ( user_data == null ){
				user_data = new LightHashMap<Object,Object>();
			}
			user_data.put( key, data );
		}
	}
	
	public Object
	getUserData(
		Object	key )
	{
		synchronized( this ){
			if ( user_data == null ){
				return( null );
			}
			return( user_data.get( key ));
		}
	}
}