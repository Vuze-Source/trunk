/*
 * Created on 02-Dec-2005
 * Created by Paul Gardner
 * Copyright (C) 2005, 2006 Aelitis, All Rights Reserved.
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
 * AELITIS, SAS au capital de 40,000 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package com.aelitis.azureus.core.diskmanager.access.impl;

import java.util.*;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.util.DirectByteBuffer;

import com.aelitis.azureus.core.diskmanager.access.DiskAccessController;
import com.aelitis.azureus.core.diskmanager.access.DiskAccessRequest;
import com.aelitis.azureus.core.diskmanager.access.DiskAccessRequestListener;
import com.aelitis.azureus.core.diskmanager.cache.CacheFile;
import com.aelitis.azureus.core.stats.AzureusCoreStats;
import com.aelitis.azureus.core.stats.AzureusCoreStatsProvider;

public class 
DiskAccessControllerImpl
	implements DiskAccessController, AzureusCoreStatsProvider
{
	private	DiskAccessControllerInstance	read_dispatcher;
	private	DiskAccessControllerInstance	write_dispatcher;
	
	public
	DiskAccessControllerImpl(
		int		_max_read_threads,
		int		_max_read_mb,
		int 	_max_write_threads,
		int		_max_write_mb )
	{		
		boolean	enable_read_aggregation 	= COConfigurationManager.getBooleanParameter( "diskmanager.perf.read.aggregate.enable", false );
		boolean	enable_write_aggregation 	= COConfigurationManager.getBooleanParameter( "diskmanager.perf.write.aggregate.enable", false );
		
		read_dispatcher 	= new DiskAccessControllerInstance( "read", enable_read_aggregation, _max_read_threads, _max_read_mb );
		write_dispatcher 	= new DiskAccessControllerInstance( "write", enable_write_aggregation, _max_write_threads, _max_write_mb );
		
		Set	types = new HashSet();
		
		types.add( AzureusCoreStats.ST_DISK_READ_QUEUE_LENGTH );
		types.add( AzureusCoreStats.ST_DISK_READ_QUEUE_BYTES );
		types.add( AzureusCoreStats.ST_DISK_READ_REQUEST_COUNT );
		types.add( AzureusCoreStats.ST_DISK_READ_REQUEST_SINGLE );
		types.add( AzureusCoreStats.ST_DISK_READ_REQUEST_MULTIPLE );
		types.add( AzureusCoreStats.ST_DISK_READ_REQUEST_BLOCKS );
		types.add( AzureusCoreStats.ST_DISK_READ_BYTES_TOTAL );
		types.add( AzureusCoreStats.ST_DISK_READ_BYTES_SINGLE );
		types.add( AzureusCoreStats.ST_DISK_READ_BYTES_MULTIPLE );
		types.add( AzureusCoreStats.ST_DISK_READ_IO_TIME );
		
		types.add( AzureusCoreStats.ST_DISK_WRITE_QUEUE_LENGTH );
		types.add( AzureusCoreStats.ST_DISK_WRITE_QUEUE_BYTES );
		types.add( AzureusCoreStats.ST_DISK_WRITE_REQUEST_COUNT );
		types.add( AzureusCoreStats.ST_DISK_WRITE_REQUEST_BLOCKS );
		types.add( AzureusCoreStats.ST_DISK_WRITE_BYTES_TOTAL );
		types.add( AzureusCoreStats.ST_DISK_WRITE_BYTES_SINGLE );
		types.add( AzureusCoreStats.ST_DISK_WRITE_BYTES_MULTIPLE );
		types.add( AzureusCoreStats.ST_DISK_WRITE_IO_TIME );

		AzureusCoreStats.registerProvider( types, this );
	}
	
	public void
	updateStats(
		Set		types,
		Map		values )
	{
			//read
		
		if ( types.contains( AzureusCoreStats.ST_DISK_READ_QUEUE_LENGTH )){
			
			values.put( AzureusCoreStats.ST_DISK_READ_QUEUE_LENGTH, new Long( read_dispatcher.getQueueSize()));
		}
		
		if ( types.contains( AzureusCoreStats.ST_DISK_READ_QUEUE_BYTES )){
			
			values.put( AzureusCoreStats.ST_DISK_READ_QUEUE_BYTES, new Long( read_dispatcher.getQueuedBytes()));
		}
		
		if ( types.contains( AzureusCoreStats.ST_DISK_READ_REQUEST_COUNT )){
			
			values.put( AzureusCoreStats.ST_DISK_READ_REQUEST_COUNT, new Long( read_dispatcher.getTotalRequests()));
		}
		
		if ( types.contains( AzureusCoreStats.ST_DISK_READ_REQUEST_SINGLE )){
			
			values.put( AzureusCoreStats.ST_DISK_READ_REQUEST_SINGLE, new Long( read_dispatcher.getTotalSingleRequests()));
		}
		
		if ( types.contains( AzureusCoreStats.ST_DISK_READ_REQUEST_MULTIPLE )){
			
			values.put( AzureusCoreStats.ST_DISK_READ_REQUEST_MULTIPLE, new Long( read_dispatcher.getTotalAggregatedRequests()));
		}
		
		if ( types.contains( AzureusCoreStats.ST_DISK_READ_REQUEST_BLOCKS )){
			
			values.put( AzureusCoreStats.ST_DISK_READ_REQUEST_BLOCKS, new Long( read_dispatcher.getBlockCount()));
		}
		
		if ( types.contains( AzureusCoreStats.ST_DISK_READ_BYTES_TOTAL )){
			
			values.put( AzureusCoreStats.ST_DISK_READ_BYTES_TOTAL, new Long( read_dispatcher.getTotalBytes()));
		}

		if ( types.contains( AzureusCoreStats.ST_DISK_READ_BYTES_SINGLE )){
			
			values.put( AzureusCoreStats.ST_DISK_READ_BYTES_SINGLE, new Long( read_dispatcher.getTotalSingleBytes()));
		}

		if ( types.contains( AzureusCoreStats.ST_DISK_READ_BYTES_MULTIPLE )){
			
			values.put( AzureusCoreStats.ST_DISK_READ_BYTES_MULTIPLE, new Long( read_dispatcher.getTotalAggregatedBytes()));
		}

		if ( types.contains( AzureusCoreStats.ST_DISK_READ_IO_TIME )){
			
			values.put( AzureusCoreStats.ST_DISK_READ_IO_TIME, new Long( read_dispatcher.getIOTime()));
		}

			// write
		
		if ( types.contains( AzureusCoreStats.ST_DISK_WRITE_QUEUE_LENGTH )){
			
			values.put( AzureusCoreStats.ST_DISK_WRITE_QUEUE_LENGTH, new Long( write_dispatcher.getQueueSize()));
		}
		
		if ( types.contains( AzureusCoreStats.ST_DISK_WRITE_QUEUE_BYTES )){
			
			values.put( AzureusCoreStats.ST_DISK_WRITE_QUEUE_BYTES, new Long( write_dispatcher.getQueuedBytes()));
		}
		
		if ( types.contains( AzureusCoreStats.ST_DISK_WRITE_REQUEST_COUNT )){
			
			values.put( AzureusCoreStats.ST_DISK_WRITE_REQUEST_COUNT, new Long( write_dispatcher.getTotalRequests()));
		}
		
		if ( types.contains( AzureusCoreStats.ST_DISK_WRITE_REQUEST_BLOCKS )){
			
			values.put( AzureusCoreStats.ST_DISK_WRITE_REQUEST_BLOCKS, new Long( write_dispatcher.getBlockCount()));
		}
		
		if ( types.contains( AzureusCoreStats.ST_DISK_WRITE_BYTES_TOTAL )){
			
			values.put( AzureusCoreStats.ST_DISK_WRITE_BYTES_TOTAL, new Long( write_dispatcher.getTotalBytes()));
		}

		if ( types.contains( AzureusCoreStats.ST_DISK_WRITE_BYTES_SINGLE )){
			
			values.put( AzureusCoreStats.ST_DISK_WRITE_BYTES_SINGLE, new Long( write_dispatcher.getTotalSingleBytes()));
		}

		if ( types.contains( AzureusCoreStats.ST_DISK_WRITE_BYTES_MULTIPLE )){
			
			values.put( AzureusCoreStats.ST_DISK_WRITE_BYTES_MULTIPLE, new Long( write_dispatcher.getTotalAggregatedBytes()));
		}
		
	if ( types.contains( AzureusCoreStats.ST_DISK_WRITE_IO_TIME )){
			
			values.put( AzureusCoreStats.ST_DISK_WRITE_IO_TIME, new Long( write_dispatcher.getIOTime()));
		}
	}
	
	public DiskAccessRequest
	queueReadRequest(
		CacheFile					file,
		long						offset,
		DirectByteBuffer			buffer,
		short						cache_policy,
		DiskAccessRequestListener	listener )
	{
		DiskAccessRequestImpl	request = 
			new DiskAccessRequestImpl( 
					file, 
					offset, 
					buffer, 
					listener, 
					DiskAccessRequestImpl.OP_READ,
					cache_policy );

		read_dispatcher.queueRequest( request );
		
		return( request );
	}
	
	public DiskAccessRequest
	queueWriteRequest(
		CacheFile					file,
		long						offset,
		DirectByteBuffer			buffer,
		boolean						free_buffer,
		DiskAccessRequestListener	listener )
	{
		// System.out.println( "write request: " + offset );
		
		DiskAccessRequestImpl	request = 
			new DiskAccessRequestImpl( 
					file, 
					offset, 
					buffer, 
					listener, 
					free_buffer?DiskAccessRequestImpl.OP_WRITE_AND_FREE:DiskAccessRequestImpl.OP_WRITE,
					CacheFile.CP_NONE );
	
		write_dispatcher.queueRequest( request );
		
		return( request );	
	}
}
