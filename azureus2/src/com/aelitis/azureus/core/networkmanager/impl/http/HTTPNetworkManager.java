/*
 * Created on 2 Oct 2006
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

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;

import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.peer.impl.PEPeerTransport;

import com.aelitis.azureus.core.networkmanager.NetworkConnection;
import com.aelitis.azureus.core.networkmanager.NetworkManager;
import com.aelitis.azureus.core.networkmanager.impl.TransportHelper;
import com.aelitis.azureus.core.networkmanager.impl.tcp.IncomingSocketChannelManager;
import com.aelitis.azureus.core.peermanager.PeerManager;
import com.aelitis.azureus.core.peermanager.PeerManagerRegistration;
import com.aelitis.azureus.core.peermanager.PeerManagerRoutingListener;
import com.aelitis.azureus.core.peermanager.messaging.MessageStreamDecoder;
import com.aelitis.azureus.core.peermanager.messaging.MessageStreamEncoder;
import com.aelitis.azureus.core.peermanager.messaging.MessageStreamFactory;

public class 
HTTPNetworkManager 
{
	private static final String	NL			= "\r\n";

	private static final LogIDs LOGID = LogIDs.NWMAN;

	private static final HTTPNetworkManager instance = new HTTPNetworkManager();

	public static HTTPNetworkManager getSingleton(){ return( instance ); }

	
	private final IncomingSocketChannelManager http_incoming_manager;

	private 
	HTTPNetworkManager()
	{	
		/*
		try{
			System.out.println( "/webseed?info_hash=" + URLEncoder.encode( new String( ByteFormatter.decodeString("C9C04D96F11FB5C5ECC99D418D3575FBFC2208B0"), "ISO-8859-1"), "ISO-8859-1" ));
			
		}catch( Throwable e ){
			
			e.printStackTrace();
		}
		*/
		
		http_incoming_manager = new IncomingSocketChannelManager( "HTTP.Data.Listen.Port", "HTTP.Data.Listen.Port.Enable" );
		
		NetworkManager.ByteMatcher matcher =
		   	new NetworkManager.ByteMatcher() 
		    {
		    	public int size() {  return 256;  }
		    	public int minSize() { return 3; }

		    	public Object
		    	matches( 
		    		TransportHelper		transport,
		    		ByteBuffer 			to_compare, 
		    		int 				port ) 
		    	{ 
		    		InetSocketAddress	address = transport.getAddress();
		    		
		    		int old_limit 		= to_compare.limit();
		    		int old_position 	= to_compare.position();

		    		try{
			    		byte[]	head = new byte[3];
			    		
			    		to_compare.get( head );
			    		
			    			// note duplication of this in min-matches below
			    		
			    		if (head[0] != 'G' || head[1] != 'E' || head[2] != 'T' ){
			    			
			    			return( null );
			    		}
			    		
			    		byte[]	line_bytes = new byte[to_compare.remaining()];
			    		
			    		to_compare.get( line_bytes );
			    		
			    		try{
			    				// format is GET url HTTPblah
			    			
				    		String	url = new String( line_bytes, "ISO-8859-1" );
				    		
				    		int	space = url.indexOf(' ');
				    		
				    		if ( space == -1 ){
				    			
				    			return( null );
				    		}
				    		
				    		url = url.substring( space + 1 );
				    		
				    		int	end_line_pos = url.indexOf( NL );
				    		
				    		if ( end_line_pos == -1 ){
				    			
				    			return( null );
				    		}
				    		
				    		url = url.substring( 0, end_line_pos );
				    		
				    		int	end_url_pos = url.lastIndexOf( ' ' );
				    		
				    		if ( end_url_pos == -1 ){
				    			
				    			return( null );
				    		}
				    		
				    		url = url.substring( 0, end_url_pos ).trim();
				    		
				    		int	ws_pos = url.indexOf( "?info_hash=" );
				    		
				    		if ( ws_pos != -1 ){
				    							    			
				    			int	hash_start = ws_pos + 11;
				    			
				    			int	hash_end = url.indexOf( '&', ws_pos );
				    			
				    			String	hash_str;
				    			
				    			if ( hash_end == -1 ){
				    				
				    				hash_str = url.substring( hash_start );
				    				
				    			}else{
				    				
				    				hash_str = url.substring( hash_start, hash_end );
				    			}
				    			
				    			if ( hash_end != -1 ){
				    				
				    				byte[]	hash = URLDecoder.decode( hash_str, "ISO-8859-1" ).getBytes( "ISO-8859-1" );
				    								    				
				    				PeerManagerRegistration reg_data = PeerManager.getSingleton().manualMatchHash( address, hash );
				    				
				    				if ( reg_data != null ){
				    					
				    					return( new Object[]{ url, reg_data });
				    				}
				    			}
				    		}
			    		
		   					if (Logger.isEnabled()){
	    						Logger.log(new LogEvent(LOGID, "HTTP decode from " + address + " failed: no match for " + url ));
	    					}
		   					
				    		return( new Object[]{ transport, "wibble wobble" });
				    		
			    		}catch( Throwable e ){
			    			
		   					if (Logger.isEnabled()){
	    						Logger.log(new LogEvent(LOGID, "HTTP decode from " + address + " failed, " + e.getMessage()));
	    					}
	
		   					return( null );
			    		}
		    		}finally{
		    		
			    			//	restore buffer structure
		    			
			    		to_compare.limit( old_limit );
			    		to_compare.position( old_position );
		    		}
		    	}
		    	
		    	public Object 
		    	minMatches( 
		    		TransportHelper		transport,
		    		ByteBuffer 			to_compare, 
		    		int 				port ) 
		    	{ 
		    		byte[]	head = new byte[3];
		    		
		    		to_compare.get( head );
		    		
		    		if (head[0] != 'G' || head[1] != 'E' || head[2] != 'T' ){
		    			
		    			return( null );
		    		}

		    		return( "" );
		    	}

		    	public byte[] 
		    	getSharedSecret()
		    	{
		    		return( null );	
		    	}
		    	
		    	 public int 
		    	 getSpecificPort()
		    	 {
		    		 return( http_incoming_manager.getTCPListeningPortNumber());
		    	 }
		    };
		    
	    // register for incoming connection routing
	    NetworkManager.getSingleton().requestIncomingConnectionRouting(
	        matcher,
	        new NetworkManager.RoutingListener() 
	        {
	        	public void 
	        	connectionRouted( 
	        		final NetworkConnection 	connection, 
	        		Object 						_routing_data ) 
	        	{
	        		Object[]	x = (Object[])_routing_data;
	        		
	        		if ( x[0] instanceof TransportHelper ){
	        			
	        				// routed on failure
	        			
	        			writeReply(connection, (TransportHelper)x[0], (String)x[1]);
	        			
	        			return;
	        		}
	        		
	        		final String					url 			= (String)x[0];
	        		final PeerManagerRegistration	routing_data 	= (PeerManagerRegistration)x[1];
	        		
   					if (Logger.isEnabled()){
						Logger.log(new LogEvent(LOGID, "HTTP connection from " + connection.getEndpoint().getNotionalAddress() + " routed successfully on '" + url + "'" ));
   					}   					
   					   					
	        		PeerManager.getSingleton().manualRoute(
	        				routing_data, 
	        				connection,
	        				new PeerManagerRoutingListener()
	        				{
	        					public void
	        					routed(
	        						PEPeerTransport		peer )
	        					{
	        						if ( url.indexOf( "/webseed" ) != -1 ){
	        							
	        							new HTTPNetworkConnectionWebSeed( connection, peer );
	        							
	        						}else if ( url.indexOf( "/ping" ) != -1 ){
	        			
	        			
	        						}else{
	        							
	        							connection.close();
	        						}
	        					}
	        				});
	        	}
	        	
	        	public boolean
	      	  	autoCryptoFallback()
	        	{
	        		return( false );
	        	}
	        	},
	        new MessageStreamFactory() {
	          public MessageStreamEncoder createEncoder() {  return new HTTPMessageEncoder();  }
	          public MessageStreamDecoder createDecoder() {  return new HTTPMessageDecoder();  }
	        });
	}
	
	protected void
	writeReply(
		final NetworkConnection		connection,
		final TransportHelper		transport,
		final String				data )
	{
		byte[]	bytes = data.getBytes();
				
		final ByteBuffer bb = ByteBuffer.wrap( bytes );
		
		try{
			transport.write( bb, false );
			
			if ( bb.remaining() > 0 ){
				
				transport.registerForWriteSelects(
					new TransportHelper.selectListener()
					{
					  	public boolean 
				    	selectSuccess(
				    		TransportHelper	helper, 
				    		Object 			attachment )
					  	{
					  		try{
					  			int written = helper.write( bb, false );
					  			
					  			if ( bb.remaining() > 0 ){
					  			
					  				helper.registerForWriteSelects( this, null );
					  				
					  			}else{
					  				
				  					if (Logger.isEnabled()){
										Logger.log(new LogEvent(LOGID, "HTTP connection from " + connection.getEndpoint().getNotionalAddress() + " closed with error '" + data + "'" ));
				   					}   					

									connection.close();
					  			}
					  			
					  			return( written > 0 );
					  			
					  		}catch( Throwable e ){
					  			
					  			helper.cancelWriteSelects();
					  			
			  					if (Logger.isEnabled()){
									Logger.log(new LogEvent(LOGID, "HTTP connection from " + connection.getEndpoint().getNotionalAddress() + " failed to write error '" + data + "'" ));
			   					}   					

					  			connection.close();
					  			
					  			return( false );
					  		}
					  	}

				        public void 
				        selectFailure(
				        	TransportHelper	helper,
				        	Object 			attachment, 
				        	Throwable 		msg)
				        {
				        	helper.cancelWriteSelects();
				        	
		  					if (Logger.isEnabled()){
								Logger.log(new LogEvent(LOGID, "HTTP connection from " + connection.getEndpoint().getNotionalAddress() + " failed to write error '" + data + "'" ));
		   					}   					

				        	connection.close();
				        }
					},
					null );
			}else{

				if (Logger.isEnabled()){
					Logger.log(new LogEvent(LOGID, "HTTP connection from " + connection.getEndpoint().getNotionalAddress() + " closed with error '" + data + "'" ));
   				}   					

				connection.close();
			}
		}catch( Throwable e ){
			
			if (Logger.isEnabled()){
				Logger.log(new LogEvent(LOGID, "HTTP connection from " + connection.getEndpoint().getNotionalAddress() + " failed to write error '" + data + "'" ));
			}   					

			connection.close();
		}
	}
}
