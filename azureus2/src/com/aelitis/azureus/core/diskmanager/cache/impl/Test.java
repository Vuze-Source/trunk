/*
 * Created on 04-Aug-2004
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

package com.aelitis.azureus.core.diskmanager.cache.impl;

import java.io.*;

import org.gudy.azureus2.core3.torrent.TOTorrentFile;
import org.gudy.azureus2.core3.util.*;

import com.aelitis.azureus.core.diskmanager.cache.*;

/**
 * @author parg
 *
 */


public class 
Test 
{
	public static void
	main(
		String	[]args )
	{
		try{
			CacheFileManager	manager = CacheFileManagerFactory.getSingleton();
			
			CacheFile[]	files = new CacheFile[3];
			
			byte[][]	file_data	= new byte[3][];
			
			for (int i=0;i<files.length;i++){
				
				final	int f_i = i;
			
				file_data[i] = new byte[randomInt(200000)];
				
				files[i] = manager.createFile(
					new CacheFileOwner()
					{
						public String
						getCacheFileOwnerName()
						{
							return( "file" + f_i );
						}
						
						public TOTorrentFile
						getCacheFileTorrentFile()
						{
							return( null );
						}
					},
					new File( "C:\\temp\\cachetest" + i + ".dat" ));
				
				files[i].setAccessMode( CacheFile.CF_WRITE );
				
				DirectByteBuffer bb = DirectByteBufferPool.getBuffer(file_data[i].length);
				
				bb.put( file_data[i]);
				
				bb.position(0);
				
				files[i].write(bb,0);
			}
			
			while( true ){
				
				CacheFile	fc = files[randomInt(files.length)];
			}
			/*
			DirectByteBuffer	write_buffer1 = DirectByteBufferPool.getBuffer(512);
			DirectByteBuffer	write_buffer2 = DirectByteBufferPool.getBuffer(512);
			
			cf.writeAndHandoverBuffer( write_buffer1, 0 );
			cf.writeAndHandoverBuffer( write_buffer2, 512 );
			
			DirectByteBuffer	read_buffer = DirectByteBufferPool.getBuffer(10);

			cf.read( read_buffer, 503 );
			read_buffer.position(5);
			cf.read( read_buffer, 503 );
			read_buffer.position(0);
			cf.read( read_buffer, 503 );
			*/
			
			//cf.close();
			
		}catch( Throwable e ){
			
			e.printStackTrace();
		}
	}
	
	static int
	randomInt(
		int	num )
	{
		return( (int)(Math.random()*num ));
	}
}
