/*
 * File    : TRTrackerServerProcessor.java
 * Created : 5 Oct. 2003
 * By      : Parg 
 * 
 * Azureus - a Java Bittorrent client
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.gudy.azureus2.core3.tracker.server.impl.tcp;


import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import org.gudy.azureus2.core3.tracker.server.*;
import org.gudy.azureus2.core3.tracker.server.impl.*;
import org.gudy.azureus2.core3.util.*;

import org.bouncycastle.util.encoders.Base64;

import com.aelitis.azureus.core.dht.netcoords.DHTNetworkPosition;
import com.aelitis.azureus.core.dht.netcoords.DHTNetworkPositionManager;
import com.aelitis.azureus.core.util.HTTPUtils;

public abstract class 
TRTrackerServerProcessorTCP
	extends 	TRTrackerServerProcessor
{
	protected static final int SOCKET_TIMEOUT				= 5000;

	protected static final char		CR			= '\015';
	protected static final char		FF			= '\012';
	protected static final String	NL			= "\015\012";

	private static final String	lc_azureus_name = Constants.AZUREUS_NAME.toLowerCase();

	protected static final byte[]	HTTP_RESPONSE_START = (
		"HTTP/1.1 200 OK" + NL + 
		"Content-Type: text/html" + NL +
		"Server: " + Constants.AZUREUS_NAME + " " + Constants.AZUREUS_VERSION + NL +
		"Connection: close" + NL +
		"Content-Length: ").getBytes();
	
	protected static final byte[]	HTTP_RESPONSE_XML_START = (
			"HTTP/1.1 200 OK" + NL + 
			"Content-Type: text/xml; charset=\"utf-8\"" + NL +
			"Server: " + Constants.AZUREUS_NAME + " " + Constants.AZUREUS_VERSION + NL +
			"Connection: close" + NL +
			"Content-Length: ").getBytes();

	protected static final byte[]	HTTP_RESPONSE_END_GZIP 		= (NL + "Content-Encoding: gzip" + NL + NL).getBytes();
	protected static final byte[]	HTTP_RESPONSE_END_NOGZIP 	= (NL + NL).getBytes();
	
	private TRTrackerServerTCP	server;
	private String				server_url;
	
	private boolean			disable_timeouts 	= false;

	
	protected
	TRTrackerServerProcessorTCP(
		TRTrackerServerTCP		_server )
	{
		server	= _server;
		
		server_url = (server.isSSL()?"https":"http") + "://" + UrlUtils.convertIPV6Host(server.getHost()) + ":" + server.getPort();
	}	

	protected boolean
	areTimeoutsDisabled()
	{
		return( disable_timeouts );
	}
	
	protected void
	setTimeoutsDisabled(
		boolean	d )
	{
		disable_timeouts	= d;
	}
	
	protected TRTrackerServerTCP
	getServer()
	{
		return( server );
	}
	
	protected void
	processRequest(
		String				input_header,
		String				lowercase_input_header,
		String				url_path,
		InetSocketAddress	client_address,
		boolean				announce_and_scrape_only,
		InputStream			is,
		OutputStream		os,
		AsyncController		async )
		
		throws IOException
	{
		String	str = url_path;
			
		int	request_type	= TRTrackerServerRequest.RT_UNKNOWN;

		try{
			Map	root = null;
				
			TRTrackerServerTorrentImpl	specific_torrent	= null;
			
			boolean	gzip_reply = false;
			
			boolean		xml_output		= false;
			
			
			try{
				if ( str.startsWith( "/announce?" )){
					
					request_type	= TRTrackerServerRequest.RT_ANNOUNCE;
					
					str = str.substring(10);
					
				}else if ( str.startsWith( "/scrape?" )){
					
					request_type	= TRTrackerServerRequest.RT_SCRAPE;
					
					str = str.substring(8);
				
				}else if ( str.equals( "/scrape" )){
					
					request_type	= TRTrackerServerRequest.RT_FULL_SCRAPE;
					
					str = "";
				
				}else if ( str.startsWith( "/query?" )){

					request_type	= TRTrackerServerRequest.RT_QUERY;
					
					str = str.substring(7);

				}else{
					
					String	redirect = TRTrackerServerImpl.redirect_on_not_found;
					
					if ( announce_and_scrape_only ){
						
						if ( redirect.length() == 0 ){
							
							throw( new Exception( "Tracker only supports announce and scrape functions" ));
						}
					}else{
						
						setTaskState( "external request" );
	
						disable_timeouts	= true;
						
							// check non-tracker authentication
							
						String user = doAuthentication( url_path, input_header, os, false );
						
						if ( user == null ){
							
							return;
						}
						
						if ( handleExternalRequest( client_address, user, str, input_header, is, os, async )){
						
							return;
						}
					}
					
					if ( redirect.length() > 0 ){
						
						os.write( ("HTTP/1.1 301 Moved Permanently" + NL + "Location: " + redirect + NL + NL).getBytes() );
						
					}else{
						
						os.write( ("HTTP/1.1 404 Not Found" + NL + NL + "Page not found." + NL).getBytes() );
					}
					
					os.flush();

					return; // throw( new Exception( "Unsupported Request Type"));
				}
				
					// OK, here its an announce, scrape or full scrape
				
					// check tracker authentication
					
				if ( doAuthentication( url_path, input_header, os, true ) == null ){
					
					return;
				}
				
				
				int	enc_pos = lowercase_input_header.indexOf( "accept-encoding:");
				
				if ( enc_pos != -1 ){
					
					int	e_pos = input_header.indexOf( NL, enc_pos );
					
					if ( e_pos != -1 ){
						
							// check we've not found X-Accept-Encoding (for example)
						
						if ( enc_pos > 0 ){
							
							char	c = lowercase_input_header.charAt(enc_pos-1);
							
							if ( c != FF && c != ' ' ){
								
								enc_pos	= -1;
							}
						}
						
						if ( enc_pos != -1 ){
							
							String	accept_encoding = lowercase_input_header.substring(enc_pos+16,e_pos);
						
							gzip_reply = HTTPUtils.canGZIP( accept_encoding );
						}
					}
				}
								
				setTaskState( "decoding announce/scrape" );

				int	pos = 0;
					
				byte[]		hash		= null;
				List		hash_list	= null;
				String		link		= null;
				
				HashWrapper	peer_id		= null;
				int			tcp_port	= 0;
				String		event		= null;
					
				long		uploaded		= 0;
				long		downloaded		= 0;
				long		left			= 0;
				int			num_want		= -1;
				boolean		no_peer_id		= false;
				byte		compact_mode	= TRTrackerServerTorrentImpl.COMPACT_MODE_NONE;
				String		key				= null;
				byte		crypto_level 	= TRTrackerServerPeer.CRYPTO_NONE;
				int			crypto_port		= 0;
				int			udp_port		= 0;
				int			http_port		= 0;
				int			az_ver			= 0;
				boolean		stop_to_queue	= false;
				String		scrape_flags	= null;
				int			up_speed		= 0;
				boolean		hide			= false;
				
				DHTNetworkPosition	network_position = null;
				
				String		real_ip_address		= client_address.getAddress().getHostAddress();
				String		client_ip_address	= real_ip_address;
				
				while(pos < str.length()){
						
					int	p1 = str.indexOf( '&', pos );
						
					String	token;
						
					if ( p1 == -1 ){
							
						token = str.substring( pos );
							
					}else{
							
						token = str.substring( pos, p1 );
							
						pos = p1+1;
					}
					
					int	p2 = token.indexOf('=');
						
					if ( p2 == -1 ){
							
						throw( new Exception( "format invalid" ));
					}
						
					String	lhs = token.substring( 0, p2 ).toLowerCase();
					String	rhs = URLDecoder.decode(token.substring( p2+1 ), Constants.BYTE_ENCODING );
						
					// System.out.println( "param:" + lhs + " = " + rhs );
						
					if ( lhs.equals( "info_hash" )){
							
						byte[] b = rhs.getBytes(Constants.BYTE_ENCODING);
						
						if ( hash == null ){
							
							hash = b;
							
						}else{
							
							if ( hash_list == null ){
								
								hash_list = new ArrayList();
								
								hash_list.add( hash );
							}
							
							hash_list.add( b );
						}
							
					}else if ( lhs.equals( "peer_id" )){
						
						peer_id	= new HashWrapper(rhs.getBytes(Constants.BYTE_ENCODING));
						
					}else if ( lhs.equals( "no_peer_id" )){
						
						no_peer_id = rhs.equals("1");
						
					}else if ( lhs.equals( "compact" )){
						
						if ( server.isCompactEnabled()){
							
							if ( rhs.equals("1") && compact_mode == TRTrackerServerTorrentImpl.COMPACT_MODE_NONE ){
								
								compact_mode = TRTrackerServerTorrentImpl.COMPACT_MODE_NORMAL;
							}
						}
					}else if ( lhs.equals( "key" )){
						
						if ( server.isKeyEnabled()){
							
							key = rhs;
						}
						
					}else if ( lhs.equals( "port" )){
							
						tcp_port = Integer.parseInt( rhs );
						
					}else if ( lhs.equals( "event" )){
							
						event = rhs;
							
					}else if ( lhs.equals( "ip" )){
							
						// System.out.println( "override: " + real_ip_address + " -> " + rhs + " [" + input_header + "]" );
						
						if ( !HostNameToIPResolver.isNonDNSName( rhs )){
							
							for (int i=0;i<rhs.length();i++){
								
								char	c = rhs.charAt(i);
								
								if ( c != '.' && c != ':' && !Character.isDigit( c )){
									
									throw( new Exception( "IP override address must be resolved by the client" ));
								}	
							}
							
							try{
								rhs	= HostNameToIPResolver.syncResolve( rhs ).getHostAddress();
							
							}catch( UnknownHostException e ){
								
								throw( new Exception( "IP override address must be resolved by the client" ));
							}
						}
						
						client_ip_address = rhs;
						
					}else if ( lhs.equals( "uploaded" )){
							
						uploaded = Long.parseLong( rhs );
						
					}else if ( lhs.equals( "downloaded" )){
							
						downloaded = Long.parseLong( rhs );
						
					}else if ( lhs.equals( "left" )){
							
						left = Long.parseLong( rhs );
												
					}else if ( lhs.equals( "numwant" )){
						
						num_want = Integer.parseInt( rhs );
						
					}else if ( lhs.equals( "azudp" )){
						
						udp_port 	= Integer.parseInt( rhs );
						
							// implicit compact mode for 2500 indicated by presence of udp port
						
						compact_mode = TRTrackerServerTorrentImpl.COMPACT_MODE_AZ;
						
					}else if ( lhs.equals( "azhttp" )){
						
						http_port 	= Integer.parseInt( rhs );

					}else if ( lhs.equals( "azver" )){
						
						az_ver 	= Integer.parseInt( rhs );
												
					}else if ( lhs.equals( "supportcrypto" )){
						
						if ( crypto_level == TRTrackerServerPeer.CRYPTO_NONE ){
						
							crypto_level	= TRTrackerServerPeer.CRYPTO_SUPPORTED;
						}
						
					}else if ( lhs.equals( "requirecrypto" )){
												
						crypto_level	= TRTrackerServerPeer.CRYPTO_REQUIRED;
					
					}else if ( lhs.equals( "cryptoport" )){

						crypto_port = Integer.parseInt( rhs );
						
					}else if ( lhs.equals( "azq" )){
					
						stop_to_queue	= true;
												
					}else if ( lhs.equals( "azsf" )){
						
						scrape_flags = rhs;
						
					}else if ( lhs.equals( "link" )){
						
						link = rhs;
					
					}else if ( lhs.equals( "outform" )){

						if ( rhs.equals( "xml" )){
							
							xml_output	= true;
						}
						
					}else if ( lhs.equals( "hide" )){
						
						hide 	= Integer.parseInt( rhs ) == 1;

					}else if ( TRTrackerServerImpl.supportsExtensions()){
						
						if ( lhs.equals( "aznp" )){

							try{
								network_position = DHTNetworkPositionManager.deserialisePosition( client_address.getAddress(), Base32.decode( rhs ));
																
							}catch( Throwable e ){
								
							}
						}else if ( lhs.equals( "azup" )){
	
							up_speed = Integer.parseInt( rhs );
						}
					}
					
					if ( p1 == -1 ){
							
						break;
					}
				}
				
					// let them hide!
					// this is also useful if an az client wants to just hide themselves on 
					// particular torrents (to prevent inward connections) as they can just
					// add a tracker-extension to append this option
				
				if ( hide ){
					
					tcp_port 	= 0;
					crypto_port	= 0;
					http_port	= 0;
					udp_port	= 0;
				}
				
				if ( crypto_level == TRTrackerServerPeer.CRYPTO_REQUIRED ){
					
					if ( crypto_port != 0 ){
						
						tcp_port = crypto_port;
					}
				}
				
				byte[][]	hashes = null;
				
				if ( hash_list != null ){
						
					hashes = new byte[hash_list.size()][];
						
					hash_list.toArray( hashes );
						
				}else if ( hash != null ){
					
					hashes = new byte[][]{ hash };
				}
				
					// >= so that if this tracker is "old" and sees a version 3+ it replies with the
					// best it can - version 2
				
				if ( xml_output ){
					
					compact_mode = TRTrackerServerTorrentImpl.COMPACT_MODE_XML;
					
				}else if ( az_ver >= 2 ){
					
					compact_mode = TRTrackerServerTorrentImpl.COMPACT_MODE_AZ_2;
				}
				
				Map[]						root_out = new Map[1];
				TRTrackerServerPeerImpl[]	peer_out = new TRTrackerServerPeerImpl[1];
				
				specific_torrent = 
						processTrackerRequest( 
							server, str,
							root_out, peer_out,
							request_type,
							hashes, link, scrape_flags,
							peer_id, no_peer_id, compact_mode, key, 
							event, stop_to_queue,
							tcp_port&0xffff, udp_port&0xffff, http_port&0xffff,
							real_ip_address,
							client_ip_address,
							downloaded, uploaded, left,
							num_want,
							crypto_level,
							(byte)az_ver,
							up_speed,
							network_position );
				
				root	= root_out[0];

				if ( request_type == TRTrackerServerRequest.RT_SCRAPE ){
					
						// add in tracker type for az clients so they know this is an AZ tracker
					
					if ( lowercase_input_header.indexOf( lc_azureus_name ) != -1 ){
				
						root.put( "aztracker", new Long(1));
					}
				}

					// only post-process if this isn't a cached entry
				
				if ( root.get( "_data" ) == null ){
	
					TRTrackerServerPeer	post_process_peer = peer_out[0];
					
					if ( post_process_peer == null ){
						
						post_process_peer = new lightweightPeer( client_ip_address, tcp_port, peer_id );
					}
					
					server.postProcess( post_process_peer, specific_torrent, request_type, str, root );
				}
				
			}catch( Exception e ){
				
				String	warning_message = null;
				
				Map	error_entries		= null;
				
				if ( e instanceof TRTrackerServerException ){
					
					TRTrackerServerException	tr_excep = (TRTrackerServerException)e;
					
					int	reason = tr_excep.getResponseCode();
					
					error_entries = tr_excep.getErrorEntries();
					
					if ( reason != -1 ){
						
						String	resp = "HTTP/1.1 " + reason + " " + tr_excep.getResponseText() + NL;
						
						Map	headers = tr_excep.getResponseHeaders();
						
						Iterator	it = headers.entrySet().iterator();
						
						while( it.hasNext()){
							
							Map.Entry	entry = (Map.Entry)it.next();
							
							String	key 	= (String)entry.getKey();
							String	value 	= (String)entry.getValue();
							
							resp += key + ": " + value + NL;
						}

						byte[]	payload = null;
						
						if ( error_entries != null ){
							
							payload = BEncoder.encode( error_entries );
							
							resp += "Content-Length: " + payload.length + NL;
						}
						
						resp += NL;

						os.write( resp.getBytes());
						
						if ( payload != null ){
							
							os.write( payload );
						}
						
						os.flush();

						return;
					}
					
					if ( tr_excep.isUserMessage()){
						
						warning_message = tr_excep.getMessage();
					}					
				}else if ( e instanceof NullPointerException ){
					
					e.printStackTrace();
				}
				
				String	message = e.getMessage();
				
				// e.printStackTrace();
				
				if ( message == null || message.length() == 0 ){

					// e.printStackTrace();
								
					message = e.toString();
				}
					
				root	= new HashMap();
								
				root.put( "failure reason", message );
				
				if ( warning_message != null ){
					
					root.put( "warning message", warning_message );
				}
				
				if ( error_entries != null ){
					
					root.putAll( error_entries );
				}
			}
		
			setTaskState( "writing response" );

			byte[]	data;
			byte[]	header_start;
			
			if ( xml_output ){
				
				StringBuffer	xml = new StringBuffer( "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" );

				xml.append( "<RESULT>" );
				
				if ( specific_torrent != null ){
					
					xml.append( "<BTIH>" );
					xml.append( ByteFormatter.encodeString( specific_torrent.getHash().getBytes()));
					xml.append( "</BTIH>" );
					
					xml.append( BEncoder.encodeToXML( root, true ));
				}
				
				xml.append( "</RESULT>" );
				
				data			= xml.toString().getBytes("UTF-8" );
				
				header_start = HTTP_RESPONSE_XML_START;

			}else{
					// cache both plain and gzip encoded data for possible reuse
				
				data 		= (byte[])root.get( "_data" );
						
				if ( data == null ){
					
					data = BEncoder.encode( root );
					
					if ( data.length > 1000000 ){
						
						File	dump = new File( "bdecoder.dump" );
						
						synchronized( TRTrackerServerProcessorTCP.class ){
							
							try{
								Debug.out( "Output is too large, saving diagnostics to " + dump.toString());
								
								PrintWriter	pw = new PrintWriter( new FileWriter( dump ));
								
								BDecoder.print( pw, root );
								
								pw.close();
								
							}catch( Throwable e ){
								
							}
						}
					}
					
					root.put( "_data", data );
				}
				
				header_start = HTTP_RESPONSE_START;
			}	
			
			if ( gzip_reply ){
				
				byte[]	gzip_data = (byte[])root.get( "_gzipdata");
				
				if ( gzip_data == null ){
						
					ByteArrayOutputStream tos = new ByteArrayOutputStream(data.length);
					
					GZIPOutputStream gos = new GZIPOutputStream( tos );
					
					gos.write( data );
					
					gos.close();
					
					gzip_data = tos.toByteArray();
					
					root.put( "_gzipdata", gzip_data );
				}
				
				data	= gzip_data;
			}
			
				// System.out.println( "TRTrackerServerProcessor::reply: sending " + new String(data));
							
				// write the response
			
			setTaskState( "writing header" );

			os.write( header_start );
			
			byte[]	length_bytes = String.valueOf(data.length).getBytes();
			
			os.write( length_bytes );

			int	header_len = header_start.length + length_bytes.length;
			
			setTaskState( "writing content" );

			if ( gzip_reply ){
				
				os.write( HTTP_RESPONSE_END_GZIP );
			
				header_len += HTTP_RESPONSE_END_GZIP.length; 
			}else{
				
				os.write( HTTP_RESPONSE_END_NOGZIP );

				header_len += HTTP_RESPONSE_END_NOGZIP.length; 
			}
					
			os.write( data );
			
			server.updateStats( request_type, specific_torrent, input_header.length(), header_len+data.length );
							
		}finally{
			
			setTaskState( "final os flush" );

			os.flush();
		}
	}
	
	protected String
	doAuthentication(
		String			url_path,
		String			header,
		OutputStream	os,
		boolean			tracker )
		
		throws IOException
	{
		// System.out.println( "doAuth: " + server.isTrackerPasswordEnabled() + "/" + server.isWebPasswordEnabled());
		
		boolean	apply_web_password 		= (!tracker) && server.isWebPasswordEnabled();
		boolean apply_torrent_password	= tracker && server.isTrackerPasswordEnabled();
		
		if ( 	apply_web_password &&
				server.isWebPasswordHTTPSOnly() &&
				!server.isSSL()){
			
			os.write( ("HTTP/1.1 403 BAD\r\n\r\nAccess Denied\r\n").getBytes() );
			
			os.flush();
				
			return( null );

		}else if (	apply_torrent_password ||
					apply_web_password ){
			
			int	x = header.indexOf( "Authorization:" );
			
			if ( x == -1 ){
				
					// auth missing. however, if we have external auth defined
					// and external auth is happy with junk then allow it through
				
				if ( server.hasExternalAuthorisation()){
					
					try{
						String	resource_str = 
							( server.isSSL()?"https":"http" ) + "://" +
								UrlUtils.convertIPV6Host(server.getHost()) + ":" + server.getPort() + url_path;
						
						URL	resource = new URL( resource_str );
					
						if ( server.performExternalAuthorisation( resource, "", "" )){
							
							return( "" );
						}
					}catch( MalformedURLException e ){
						
						Debug.printStackTrace( e );
					}
				}
			}else{
															
					//			Authorization: Basic dG9tY2F0OnRvbWNhdA==
		
				int	p1 = header.indexOf(' ', x );
				int p2 = header.indexOf(' ', p1+1 );
				
				String	body = header.substring( p2, header.indexOf( '\r', p2 )).trim();
				
				String decoded=new String( Base64.decode(body));

					// username:password
									
				int	cp = decoded.indexOf(':');
				
				String	user = decoded.substring(0,cp);
				String  pw	 = decoded.substring(cp+1);
				
				boolean	auth_failed	= false;
				
				if ( server.hasExternalAuthorisation()){
					
					try{
						String	resource_str = 
							( server.isSSL()?"https":"http" ) + "://" +
							UrlUtils.convertIPV6Host(server.getHost()) + ":" + server.getPort() + url_path;
						
						URL	resource = new URL( resource_str );
					
						if ( server.performExternalAuthorisation( resource, user, pw )){
							
							return( user );
						}
					}catch( MalformedURLException e ){
						
						Debug.printStackTrace( e );
					}
					
					auth_failed	= true;
				}
				
				if ( server.hasInternalAuthorisation() && !auth_failed ){
					
					try{
				
						SHA1Hasher hasher = new SHA1Hasher();
						
						byte[] password = pw.getBytes();
						
						byte[] encoded;
						
						if( password.length > 0){
						
							encoded = hasher.calculateHash(password);
							
						}else{
							
							encoded = new byte[0];
						}
						
						if ( user.equals( "<internal>")){
							
							byte[] internal_pw = Base64.decode(pw);
	
							if ( Arrays.equals( internal_pw, server.getPassword())){
								
								return( user );
							}
						}else if ( 	user.equalsIgnoreCase(server.getUsername()) &&
									Arrays.equals(encoded, server.getPassword())){
							 	
							 return( user );			 	
						}
					}catch( Exception e ){
						
						Debug.printStackTrace( e );
					}
				}
			}
			
			os.write( ("HTTP/1.1 401 BAD\r\nWWW-Authenticate: Basic realm=\"" + server.getName() + "\"\r\n\r\nAccess Denied\r\n").getBytes() );
			
			os.flush();
				
			return( null );

		}else{
		
			return( "" );
		}
	}
		
	protected boolean
	handleExternalRequest(
		InetSocketAddress	client_address,
		String				user,
		String				url,
		String				header,
		InputStream			is,
		OutputStream		os,
		AsyncController		async )
		
		throws IOException
	{
		URL	absolute_url = new URL( server_url + (url.startsWith("/")?url:("/"+url)));
			
		return( server.handleExternalRequest(client_address,user,url,absolute_url,header, is, os, async));
	}
}
