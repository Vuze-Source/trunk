/*
 * Created on Sep 9, 2007
 * Created by Paul Gardner
 * Copyright (C) 2007 Aelitis, All Rights Reserved.
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
 * AELITIS, SAS au capital de 63.529,40 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */


package com.aelitis.azureus.core.download;

import java.util.*;

import org.gudy.azureus2.core3.disk.DiskManagerFileInfo;
import org.gudy.azureus2.core3.util.Debug;

public class 
EnhancedDownloadManagerFile 
{
	private DiskManagerFileInfo		file;
	private int						header_size;
	
	private int[][]					speeds;
	
	protected
	EnhancedDownloadManagerFile(
		DiskManagerFileInfo		_file,
		Map						_map )
	{
		file		= _file;
		
		try{
			if ( _map != null ){
				
				Long	l_header_size = (Long)_map.get( "header_size" );
				
				if ( l_header_size != null ){
					
					header_size = l_header_size.intValue();
				}
				
				List 	stream_info = (List)_map.get( "stream_info" );
				
				if ( stream_info != null ){
					
					speeds = new int[stream_info.size()][];
					
					for (int i=0;i<stream_info.size();i++){
						
						List	entry = (List)stream_info.get(i);
						
						
						int		speed 			= ((Long)entry.get(0)).intValue();
						int		buffer			= ((Long)entry.get(1)).intValue();
						int		worst_buffer	= ((Long)entry.get(2)).intValue();
						
						speeds[i] = new int[]{ speed, buffer, worst_buffer };
					}
				}
			}
		}catch( Throwable e ){
			
			Debug.printStackTrace(e);
		}
	}
	
	public DiskManagerFileInfo
	getFile()
	{
		return( file );
	}
	
	public int
	getHeaderSize()
	{
		return( header_size );
	}
	
	public int
	getInitialBufferBytes(
		long		rate )
	{
		int	buffer_size = 0;
		
		if ( speeds != null ){
			
			int	k_rate = (int)rate/1024;
		
			int	hit = -1;
			
			for (int i=0;i<speeds.length;i++){
				
				if ( k_rate > speeds[i][0] ){
					
					hit = i-1;
					
					break;
				}
			}
			
			if ( hit >= 0 ){
				
				buffer_size = speeds[hit][1] * 1024;
			}
		}
		
		// System.out.println( "getInitialBufferBytes(" + rate + ") -> " + header_size + "/" + buffer_size );
		
		return( header_size + buffer_size );
	}
	
	public String
	getString()
	{
		return( file.getFile(true).getName()+ ",header=" + header_size );
	}
}
