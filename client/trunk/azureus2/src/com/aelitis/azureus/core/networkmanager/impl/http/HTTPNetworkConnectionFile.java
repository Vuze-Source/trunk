/*
 * Created on 4 Oct 2006
 * Created by Paul Gardner
 * Copyright (C) 2006 Aelitis, All Rights Reserved.
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

package com.aelitis.azureus.core.networkmanager.impl.http;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import org.gudy.azureus2.core3.disk.DiskManager;
import org.gudy.azureus2.core3.disk.DiskManagerFileInfo;
import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.peer.impl.PEPeerTransport;
import org.gudy.azureus2.core3.torrent.TOTorrentFile;
import org.gudy.azureus2.core3.util.Debug;

import com.aelitis.azureus.core.networkmanager.NetworkConnection;
import com.aelitis.azureus.core.networkmanager.impl.http.HTTPNetworkConnection.httpRequest;


public class 
HTTPNetworkConnectionFile
	extends HTTPNetworkConnection
{
	protected
	HTTPNetworkConnectionFile(
		HTTPNetworkManager		_manager,
		NetworkConnection		_connection,
		PEPeerTransport			_peer,
		String					_url )
	{
		super( _manager, _connection, _peer, _url );
	}
	
	protected void
	decodeHeader(
		String		header )
	
		throws IOException
	{
		if ( !isSeed()){
			
			return;
		}
		
			// note that if we allow keep-alive in the future we'd need to validate it was the
			// same torrent...
		
		
		DiskManager	dm = getPeerControl().getDiskManager();
		
		if ( dm == null ){
			
			Debug.out( "Disk manager is null" );
			
			throw( new IOException( "Disk manager unavailable" ));
		}
				
		char[]	chars = header.toCharArray();
		
		int	last_pos 	= 0;
		int	line_num	= 0;
		
		String				target_str	= null;
		
		DiskManagerFileInfo	target_file = null;
		
		long	file_offset	= 0;
		
		List	ranges = new ArrayList();
		
		for (int i=1;i<chars.length;i++){
			
			if ( chars[i-1] == '\r' && chars[i] == '\n' ){
				
				String	line = new String( chars, last_pos, i - last_pos ).trim();
				
				last_pos = i;
				
				line_num++;
				
				// System.out.println( "line " + line_num + " -> " + line );
				
				if ( line_num == 1 ){
					
					line = line.substring( line.indexOf( "files/" ) + 6 );
					line = line.substring( line.indexOf( "/" ) + 1 );
					
					line = line.substring( 0, line.lastIndexOf( ' ' ));
					
					String	file = line;
					
					target_str	= file;
					
					StringTokenizer	tok = new StringTokenizer( file, "/" );
					
					List	bits = new ArrayList();
					
					while( tok.hasMoreTokens()){
						
						byte[]	bit = URLDecoder.decode( tok.nextToken(), "ISO-8859-1" ).getBytes( "ISO-8859-1" );
						
						bits.add( bit );
					}
					
					DiskManagerFileInfo[]	files = dm.getFiles();
					
					file_offset	= 0;
					
					for (int j=0;j<files.length;j++){
						
						TOTorrentFile	torrent_file = files[j].getTorrentFile();
						
						byte[][]	comps = torrent_file.getPathComponents();
						
						if ( comps.length == bits.size()){
							
							boolean	match = true;
							
							for (int k=0;k<comps.length;k++){
								
								byte[]	bit = (byte[])bits.get(k);
								
								if ( !Arrays.equals( bit, comps[k] )){
									
									match	= false;
									
									break;
								}
							}
							
							if ( match ){ 
								
								target_file 	= files[j];
																
								break;
							}
						}
						
						file_offset += torrent_file.getLength();
					}	
				}else{
					
					line = line.toLowerCase();
					
					if ( line.startsWith( "range" ) && target_file != null ){
						
						line = line.substring(5).trim();
						
						if ( line.startsWith(":" )){
									
							String	range_str = line.substring(1).trim();
							
							if ( range_str.startsWith( "bytes=" )){
								
								long	file_length = target_file.getLength();
								
								StringTokenizer tok2 = new StringTokenizer( range_str.substring( 6 ), "," );
								
								while( tok2.hasMoreTokens()){
									
									String	range = tok2.nextToken();
									
									try{
										int	pos = range.indexOf('-');
										
										if ( pos != -1 ){
											
											String	lhs = range.substring( 0,pos);
											String	rhs = range.substring(pos+1);
											
											long	start;
											long	end;
											
											if ( lhs.length() == 0 ){
												
													// -222 is last 222 bytes of file
												
												end 	= file_length - 1;
												start 	= file_length - Long.parseLong( rhs );
												
											}else if ( rhs.length() == 0 ){
												
												end 	= file_length - 1;
												start 	= Long.parseLong( lhs );
												
											}else{
												
												start 	= Long.parseLong( lhs );
												end 	= Long.parseLong( rhs );
											}	
											
											ranges.add( new long[]{ start, end });
										}
									}catch( Throwable e ){
									}
								}
							}
							
							if ( ranges.size() == 0 ){
							
			  					log( "Invalid range specification: '" + line + "'" );					

								sendAndClose( getManager().getRangeNotSatisfiable());
								
								return;
							}
						}
					}
				}
			}
		}

		if ( target_file == null ){
			
			log( "Failed to find file '" + target_str + "'" );

			sendAndClose( getManager().getNotFound());
			
			return;
		}
		
		long	file_length = target_file.getLength();
		
		boolean	partial_content =  ranges.size() > 0;
		
		if ( !partial_content ){
			
			ranges.add( new long[]{ 0, file_length - 1});
		}
		
		long[]	offsets	= new long[ranges.size()];
		long[]	lengths	= new long[ranges.size()];
				
		for (int i=0;i<ranges.size();i++){
			
			long[]	range = (long[])ranges.get(i);
			
			long	start 	= range[0];
			long end		= range[1];
			
			if ( 	start < 0 || start >= file_length ||
					end < 0 || end >= file_length ||
					start > end ){
				
				log( "Invalid range specification: '" + start + "-" + end + "'" );					

				sendAndClose( getManager().getRangeNotSatisfiable());
				
				return;
			}
			
			offsets[i] 	= file_offset + start;
			lengths[i]	= ( end - start ) + 1; 
		}
		
		addRequest( new httpRequest( offsets, lengths, partial_content ));	
	}
}
