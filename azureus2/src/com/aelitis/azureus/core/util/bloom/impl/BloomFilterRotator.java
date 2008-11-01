/*
 * Created on Oct 29, 2008
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


package com.aelitis.azureus.core.util.bloom.impl;

import com.aelitis.azureus.core.util.bloom.BloomFilter;

public class 
BloomFilterRotator
	implements BloomFilter
{
	private volatile BloomFilter 	current_filter;
	private int						current_filter_index;
	
	private final BloomFilter[]	filters;
	
	public
	BloomFilterRotator(
		BloomFilter		_target,
		int				_num )
	{
		filters = new BloomFilter[_num];
		
		filters[0] = _target;
		
		for (int i=1;i<filters.length;i++){
			
			filters[i] = _target.getReplica();
		}
		
		current_filter 			= _target;
		current_filter_index	= 0;
	}
	
	public int
	add(
		byte[]		value )
	{		
		synchronized( filters ){
					
			int	filter_size 	= current_filter.getSize();
			int	filter_entries	= current_filter.getEntryCount();
			
			int	limit	= filter_size / 8;	// capacity limit 
			
			if ( filter_entries > limit ){
				
				filter_entries = limit;
			}
			
			int	update_chunk = limit / filters.length;
			
			int	num_to_update =  ( filter_entries / update_chunk ) + 1; 
			
			if ( num_to_update > filters.length ){
				
				num_to_update = filters.length;
			}
			
			//System.out.println( "rot_bloom: cur=" + current_filter_index + ", upd=" + num_to_update + ",ent=" + filter_entries );
			
			int	res = 0;
			
			for (int i=current_filter_index;i<current_filter_index+num_to_update;i++){
				
				int	r = filters[i%filters.length].add( value );

				if ( i == current_filter_index ){
					
					res = r;
				}
			}
			
			if ( current_filter.getEntryCount() > limit ){
				
				filters[current_filter_index] = current_filter.getReplica();

				current_filter_index = (current_filter_index+1)%filters.length;
				
				current_filter = filters[ current_filter_index ];
			}
			
			return( res );
		}
	}
	
	public int
	remove(
		byte[]		value )
	{
		int	res = 0;
		
		for (int i=0;i<filters.length;i++){
			
			BloomFilter	filter = filters[i];
			
			int r = filter.remove( value );
			
			if ( filter == current_filter ){
				
				res = r;
			}
		}
		
		return( res );
	}
	
	public boolean
	contains(
		byte[]		value )
	{
		return( current_filter.contains(value));
	}
	
	public int
	count(
		byte[]		value )
	{
		return( current_filter.count( value ));
	}
	
	public int
	getEntryCount()
	{
		return( current_filter.getEntryCount());
	}
	
	public int
	getSize()
	{
		return( current_filter.getSize());
	}
	
	public BloomFilter
	getReplica()
	{
		return( new BloomFilterRotator( current_filter, filters.length ));
	}
	
	public String
	getString()
	{
		return( "ind=" + current_filter_index + ",filt=" + current_filter.getString());
	}
}
