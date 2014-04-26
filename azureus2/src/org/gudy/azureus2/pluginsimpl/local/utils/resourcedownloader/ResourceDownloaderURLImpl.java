/*
 * File    : TorrentDownloader2Impl.java
 * Created : 27-Feb-2004
 * By      : parg
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

package org.gudy.azureus2.pluginsimpl.local.utils.resourcedownloader;

/**
 * @author parg
 *
 */

import com.aelitis.azureus.core.proxy.AEProxyFactory;
import com.aelitis.azureus.core.proxy.AEProxySelectorFactory;
import com.aelitis.azureus.core.proxy.AEProxyFactory.PluginProxy;

import java.io.*;
import java.net.*;

import javax.net.ssl.*;

import java.net.PasswordAuthentication;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

import org.gudy.azureus2.core3.util.AETemporaryFileHandler;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.AddressUtils;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.FileUtil;
import org.gudy.azureus2.core3.util.TorrentUtils;
import org.gudy.azureus2.core3.util.UrlUtils;
import org.gudy.azureus2.core3.util.protocol.magnet.MagnetConnection2;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.security.*;
import org.gudy.azureus2.plugins.utils.resourcedownloader.*;

import com.aelitis.azureus.core.util.DeleteFileOnCloseInputStream;

public class 
ResourceDownloaderURLImpl
	extends 	ResourceDownloaderBaseImpl
	implements 	SEPasswordListener
{
	private static final int BUFFER_SIZE = 32768;
  
	private static final int MAX_IN_MEM_READ_SIZE	= 256*1024;
	
	private URL			original_url;
	private boolean		auth_supplied;
	private String		user_name;
	private String		password;
	
	private InputStream 	input_stream;
	private boolean			cancel_download	= false;
	
	private boolean			download_initiated;
	private long			size		 	= -2;	// -1 -> unknown
	
	private boolean       	force_no_proxy = false;
	private Proxy			force_proxy;
	private boolean			auto_plugin_proxy;
	
	private final byte[] 	post_data;

	public 
	ResourceDownloaderURLImpl(
		ResourceDownloaderBaseImpl	_parent,
		URL							_url )
	{
		this( _parent, _url, false, null, null );
	}
	
	public 
	ResourceDownloaderURLImpl(
		ResourceDownloaderBaseImpl	_parent,
		URL							_url,
		String						_user_name,
		String						_password )
	{
		this( _parent, _url, true, _user_name, _password );
	}
	
	public 
	ResourceDownloaderURLImpl(
		ResourceDownloaderBaseImpl	_parent,
		URL							_url,
		boolean						_auth_supplied,
		String						_user_name,
		String						_password )
	{
		this(_parent, _url, null, _auth_supplied, _user_name, _password);
	}
	
	/**
	 * 
	 * @param _parent
	 * @param _url
	 * @param _data if null, GET will be used, otherwise POST will be used with
	 *              the data supplied
	 * @param _auth_supplied
	 * @param _user_name
	 * @param _password
	 */
	public 
	ResourceDownloaderURLImpl(
		ResourceDownloaderBaseImpl	_parent,
		URL							_url,
		byte[] 						_data,
		boolean						_auth_supplied,
		String						_user_name,
		String						_password )
	{
		super( _parent );
		
		/*
		if ( _url.getHost().equals( "212.159.18.92")){
			try{
				_url = new URL(_url.getProtocol() + "://192.168.0.2:" + _url.getPort() + "/" + _url.getPath());
			}catch( Throwable e ){
				
				e.printStackTrace();
			}
		}
		*/
		
		original_url	= _url;
		post_data 		= _data;
		auth_supplied	= _auth_supplied;
		user_name		= _user_name;
		password		= _password;
	}
	
	protected void setForceNoProxy(boolean force_no_proxy) {
		this.force_no_proxy = force_no_proxy;
	}

	protected void setForceProxy( Proxy proxy ){
		force_proxy = proxy;
	}
	
	protected void setAutoPluginProxy(){
		auto_plugin_proxy = true;
	}
	
	protected URL
	getURL()
	{
		return( original_url );
	}
	
	public String
	getName()
	{
		return( original_url.toString());
	}
	
	public long
	getSize()
	
		throws ResourceDownloaderException
	{
			// only every try getting the size once
		
		if ( size == -2 ){
			
			try{
				ResourceDownloaderURLImpl c = (ResourceDownloaderURLImpl)getClone( this );
				
				addReportListener( c );
				
				size = c.getSizeSupport();
				
				setProperties(  c );
				
			}finally{
				
				if ( size == -2 ){
					
					size = -1;
				}
			}
		}
		
		return( size );
	}
	
	protected void
	setSize(
		long	l )
	{
		size	= l;
	}
	
	public void
	setProperty(
		String	name,
		Object	value )
	
		throws ResourceDownloaderException
	{
		setPropertySupport( name, value );
	}
	
	protected long
	getSizeSupport()
	
		throws ResourceDownloaderException
	{
		// System.out.println("ResourceDownloader:getSize - " + getName());
		
		try{
			String	protocol = original_url.getProtocol().toLowerCase();
			
			if ( protocol.equals( "magnet" ) || protocol.equals( "dht" ) || protocol.equals( "vuze" ) || protocol.equals( "ftp" )){
				
				return( -1 );
				
			}else if ( protocol.equals( "file" )){
				
				return( new File( original_url.toURI()).length());
			}
			
			reportActivity(this, "Getting size of " + original_url );

			try{
				URL	url = new URL( original_url.toString().replaceAll( " ", "%20" ));
			      
				url = AddressUtils.adjustURL( url );

				URL	initial_url = url;
				
				PluginProxy	plugin_proxy;
				
				boolean		ok = false;
				
				if ( auto_plugin_proxy ){
					
					plugin_proxy = AEProxyFactory.getPluginProxy( "downloading resource", url );
	
					if ( plugin_proxy == null ){
						
						throw( new ResourceDownloaderException( this, "No plugin proxy available" ));
					}
					
					url			= plugin_proxy.getURL();
					force_proxy	= plugin_proxy.getProxy();
					
				}else{
					
					plugin_proxy = null;
				}
				
				try{
					if ( force_no_proxy ){
						
						AEProxySelectorFactory.getSelector().startNoProxy();
					}
					
					if ( auth_supplied ){
	
						SESecurityManager.setPasswordHandler( url, this );
					}
	
					for (int i=0;i<2;i++){
						
						try{
							HttpURLConnection	con;
							
							if ( url.getProtocol().equalsIgnoreCase("https")){
						      	
									// see ConfigurationChecker for SSL client defaults
				
								HttpsURLConnection ssl_con = (HttpsURLConnection)openConnection( force_proxy, url);
				
									// allow for certs that contain IP addresses rather than dns names
				  	
								ssl_con.setHostnameVerifier(
										new HostnameVerifier()
										{
											public boolean
											verify(
													String		host,
													SSLSession	session )
											{
												return( true );
											}
										});
				  	
								con = ssl_con;
				  	
							}else{
				  	
								con = (HttpURLConnection)openConnection( force_proxy, url);
				  	
							}
				  
							if ( plugin_proxy != null ){
							
								con.setRequestProperty( "HOST", plugin_proxy.getURLHostRewrite() + (initial_url.getPort()==-1?"":(":" + initial_url.getPort())));
							}
							
							con.setRequestMethod( "HEAD" );
							
							con.setRequestProperty("User-Agent", Constants.AZUREUS_NAME + " " + Constants.AZUREUS_VERSION);     
				  
							setRequestProperties( con, false );
							
							try{
								con.connect();
				
							}catch( AEProxyFactory.UnknownHostException e ){
								
								throw( new UnknownHostException( e.getMessage()));
							}
							
							int response = con.getResponseCode();
							
							setProperty( "URL_HTTP_Response", new Long( response ));

							if ((response != HttpURLConnection.HTTP_ACCEPTED) && (response != HttpURLConnection.HTTP_OK)) {
								
								throw( new ResourceDownloaderException( this, "Error on connect for '" + trimForDisplay( url ) + "': " + Integer.toString(response) + " " + con.getResponseMessage()));    
							}
															
							getRequestProperties( con );
							
							ok = true;
							
							return( UrlUtils.getContentLength( con ));
							
						}catch( SSLException e ){
							
							if ( i == 0 ){
								
								if ( SESecurityManager.installServerCertificates( url ) != null ){
									
										// certificate has been installed
									
									continue;	// retry with new certificate
								}
							}

							throw( e );
							
						}catch( IOException e ){
							
							if ( i == 0 ){
								
					      		URL retry_url = UrlUtils.getIPV4Fallback( url );
				      			
					      		if ( retry_url != null ){
					      				
					      			url = retry_url;
					      			
					      			continue;
					      		}
							}
							
							throw( e );
						}
					}
					
					throw( new ResourceDownloaderException( this, "Should never get here" ));

				}finally{
					
					if ( auth_supplied ){
					
						SESecurityManager.setPasswordHandler( url, null );
					}
					
					if ( force_no_proxy ){
						
						AEProxySelectorFactory.getSelector().endNoProxy();
					}
					
					if ( plugin_proxy != null ){
						
						plugin_proxy.setOK( ok );
						
						force_proxy = null;
					}
				}
			}catch (java.net.MalformedURLException e){
				
				throw( new ResourceDownloaderException( this, "Exception while parsing URL '" + original_url + "':" + e.getMessage(), e));
				
			}catch (java.net.UnknownHostException e){
				
				throw( new ResourceDownloaderException( this, "Exception while initializing download of '" + trimForDisplay( original_url ) + "': Unknown Host '" + e.getMessage() + "'", e));
				
			}catch (java.io.IOException e ){
				
				throw( new ResourceDownloaderException( this, "I/O Exception while downloading '" + trimForDisplay( original_url )+ "'", e ));
			}
		}catch( Throwable e ){
			
			ResourceDownloaderException	rde;
			
			if ( e instanceof ResourceDownloaderException ){
				
				rde = (ResourceDownloaderException)e;
				
			}else{
				
				Debug.out(e);
				
				rde = new ResourceDownloaderException( this, "Unexpected error", e );
			}
						
			throw( rde );
		}		
	}
	
	public ResourceDownloaderBaseImpl
	getClone(
		ResourceDownloaderBaseImpl	parent )
	{
		ResourceDownloaderURLImpl c = new ResourceDownloaderURLImpl( parent, original_url, post_data, auth_supplied, user_name, password );
		
		c.setSize( size );
		
		c.setProperties( this );
		c.setForceNoProxy(force_no_proxy);
		if ( force_proxy != null ){
			c.setForceProxy( force_proxy );
		}
		if ( auto_plugin_proxy){
			c.setAutoPluginProxy();
		}
		return( c );
	}

	public void
	asyncDownload()
	{
		final Object	parent_tls = TorrentUtils.getTLS();

		AEThread2	t = 
			new AEThread2( "ResourceDownloader:asyncDownload - " + trimForDisplay( original_url ), true )
			{
				public void
				run()
				{
					Object	child_tls = TorrentUtils.getTLS();
					
					TorrentUtils.setTLS( parent_tls );
					
					try{
						download();
						
					}catch ( ResourceDownloaderException e ){
						
					}finally{
						
						TorrentUtils.setTLS( child_tls );
					}
					
				}
			};
					
		t.start();
	}

	public InputStream
	download()
	
		throws ResourceDownloaderException
	{
		// System.out.println("ResourceDownloader:download - " + getName());
		
		try{
			reportActivity(this, getLogIndent() + "Downloading: " +trimForDisplay(  original_url ));
			
			try{
				this_mon.enter();
				
				if ( download_initiated ){
					
					throw( new ResourceDownloaderException( this, "Download already initiated"));
				}
				
				download_initiated	= true;
				
			}finally{
				
				this_mon.exit();
			}
			
			try{
				URL	outer_url = new URL( original_url.toString().replaceAll( " ", "%20" ));
			      
					// some authentications screw up without an explicit port number here
				
				String	protocol = outer_url.getProtocol().toLowerCase();
				
				if ( protocol.equals( "vuze" )){
					
					outer_url = original_url;
					
				}else if ( protocol.equals( "file" )){
					
					File file = new File( original_url.toURI());
										
					FileInputStream fis = new FileInputStream( file );
					
					informAmountComplete( file.length());
					
					informPercentDone( 100 );

					informComplete( fis );
					
					return( fis );

				}else if ( 	outer_url.getPort() == -1 && 
							( protocol.equals( "http" ) || protocol.equals( "https" ))){
					
					int	target_port;
					
					if ( protocol.equals( "http" )){
						
						target_port = 80;
						
					}else{
						
						target_port = 443;
					}
					
					try{
						String str = original_url.toString().replaceAll( " ", "%20" );
					
						int	pos = str.indexOf( "://" );
						
						pos = str.indexOf( "/", pos+4 );
						
							// might not have a trailing "/"
						
						if ( pos == -1 ){
							
							outer_url = new URL( str + ":" + target_port + "/" );
							
						}else{
						
							outer_url = new URL( str.substring(0,pos) + ":" + target_port + str.substring(pos));
						}
												
					}catch( Throwable e ){
						
						Debug.printStackTrace( e );
					}
				}
				
				outer_url = AddressUtils.adjustURL( outer_url );
				
				try{
					if ( force_no_proxy ){
						
						AEProxySelectorFactory.getSelector().startNoProxy();
					}
					
					if ( auth_supplied ){
						
						SESecurityManager.setPasswordHandler( outer_url, this );
					}
					
					boolean	use_compression = true;
					
					boolean	follow_redirect = true;
					
					Set<String>	redirect_urls = new HashSet<String>();

					URL		current_url		= outer_url;
					Proxy 	current_proxy 	= force_proxy;
					
					URL	initial_url = current_url;

redirect_label:
					while( follow_redirect ){
						
						follow_redirect = false;
																	
						PluginProxy	plugin_proxy;
						
						boolean		ok = false;
						
						if ( auto_plugin_proxy ){
							
							plugin_proxy = AEProxyFactory.getPluginProxy( "downloading resource", current_url );
			
							if ( plugin_proxy == null ){
								
								throw( new ResourceDownloaderException( this, "No plugin proxy available" ));
							}
							
							current_url		= plugin_proxy.getURL();
							current_proxy	= plugin_proxy.getProxy();
							
						}else{
							
							plugin_proxy = null;
						}
						
						try{
							
							for ( int connect_loop=0;connect_loop<2;connect_loop++ ){
						
								File					temp_file	= null;
		
								try{
									URLConnection	con;
									
									if ( current_url.getProtocol().equalsIgnoreCase("https")){
								      	
											// see ConfigurationChecker for SSL client defaults
						
										HttpsURLConnection ssl_con = (HttpsURLConnection)openConnection( current_proxy, current_url );
						
											// allow for certs that contain IP addresses rather than dns names
						  	
										ssl_con.setHostnameVerifier(
												new HostnameVerifier()
												{
													public boolean
													verify(
															String		host,
															SSLSession	session )
													{
														return( true );
													}
												});
						  	
										con = ssl_con;
						  	
									}else{
						  	
										con = openConnection( current_proxy, current_url );
						  	
									}
									
									PluginProxy current_plugin_proxy = plugin_proxy==null?AEProxyFactory.getPluginProxy( force_proxy ):plugin_proxy;
									
									if ( con instanceof HttpURLConnection ){
										
											// we want this true but some plugins (grrr) set the global default not to follow
											// redirects
										
										if ( current_plugin_proxy != null ){
											
												// need to manually handle redirects as we need to re-proxy
											
											((HttpURLConnection)con).setInstanceFollowRedirects( false );
											
										}else{
											
											((HttpURLConnection)con).setInstanceFollowRedirects( true );
										}
									}
									
										// keep this here, and don't replace with a URL_HOST redirect, as we want it to be overridden
										// by any previously set manual property for the initial invocation
										
									if ( current_plugin_proxy != null ){
										
										con.setRequestProperty( "HOST", current_plugin_proxy.getURLHostRewrite() + (initial_url.getPort()==-1?"":(":" + initial_url.getPort())));
									}

									con.setRequestProperty("User-Agent", Constants.AZUREUS_NAME + " " + Constants.AZUREUS_VERSION);     
						  
									String connection = getStringProperty( "URL_Connection" );
									
									if ( connection != null && connection.equalsIgnoreCase( "Keep-Alive" )){
										
										con.setRequestProperty( "Connection", "Keep-Alive" );
	
											// gah, no idea what the intent behind 'skip' is!
										
									}else if ( connection == null || !connection.equals( "skip" )){
										
											// default is close
										
										con.setRequestProperty( "Connection", "close" );
									}
									
							 		if ( use_compression ){
									
							 			con.addRequestProperty( "Accept-Encoding", "gzip" );
							 		}
							 		
									setRequestProperties( con, use_compression );
									
									if ( post_data != null && con instanceof HttpURLConnection ){
										
										con.setDoOutput(true);
										
										String verb = (String)getStringProperty( "URL_HTTP_VERB" );
										
										if ( verb == null ){
											
											verb = "POST";
										}
										
										((HttpURLConnection)con).setRequestMethod( verb );
										
										if ( post_data.length > 0 ){
											
											OutputStream os = con.getOutputStream();
											
											os.write( post_data );
											
											os.flush();
										}
									}
		
									long	connect_timeout = getLongProperty( "URL_Connect_Timeout" );
									
									if ( connect_timeout >= 0 ){
									
										con.setConnectTimeout((int)connect_timeout );
									}
									
									long	read_timeout = getLongProperty( "URL_Read_Timeout" );
									
									if ( read_timeout >= 0 ){
									
										con.setReadTimeout((int)read_timeout );
									}
									
									boolean	trust_content_length = getBooleanProperty( "URL_Trust_Content_Length" );
									
									try{
										con.connect();
										
									}catch( AEProxyFactory.UnknownHostException e ){
										
										throw( new UnknownHostException( e.getMessage()));
									}
									
									int response = con instanceof HttpURLConnection?((HttpURLConnection)con).getResponseCode():HttpURLConnection.HTTP_OK;
												
									ok = true;
									
									if ( 	response == HttpURLConnection.HTTP_MOVED_TEMP ||
											response == HttpURLConnection.HTTP_MOVED_PERM ){
										
											// auto redirect doesn't work from http to https or vice-versa
										
										String	move_to = con.getHeaderField( "location" );
										
										if ( move_to != null ){
											
											if ( redirect_urls.contains( move_to ) || redirect_urls.size() > 32 ){
												
												throw( new ResourceDownloaderException( this, "redirect loop" )); 
											}
											
											redirect_urls.add( move_to );
											
											try{
													// don't URL decode the move-to as its already in the right format!
												
												URL	move_to_url = new URL( move_to ); // URLDecoder.decode( move_to, "UTF-8" ));
												
												boolean	follow = false;
												
												if ( current_plugin_proxy != null ){
													
													PluginProxy child = current_plugin_proxy.getChildProxy( "redirect", move_to_url );
													
													if ( child != null ){
														
														initial_url		= move_to_url;
														
															// use an overall property to force this through on the redirect
														
														setProperty( "URL_HOST", initial_url.getHost() + (initial_url.getPort()==-1?"":(":" + initial_url.getPort())));

														current_proxy	= child.getProxy();
														move_to_url		= child.getURL();	
														
														follow = true;
													}
												}

												String	original_protocol 	= current_url.getProtocol().toLowerCase();
												String	new_protocol		= move_to_url.getProtocol().toLowerCase();
												
												if ( follow || !original_protocol.equals( new_protocol )){
													
													current_url = move_to_url;
													
													try{
														List<String>	cookies_list = con.getHeaderFields().get( "Set-cookie" );
														
														List<String>	cookies_set = new ArrayList<String>();
														
														if ( cookies_list != null ){
															
															for (int i=0;i<cookies_list.size();i++){
																
																String[] cookie_bits = ((String)cookies_list.get(i)).split(";");
																
																if ( cookie_bits.length > 0 ){
																
																	cookies_set.add( cookie_bits[0] );
																}
															}
														}
														
														if ( cookies_set.size() > 0 ){
															
															String	new_cookies = "";
															
															Map properties = getLCKeyProperties();
															
															Object obj = properties.get( "url_cookie" );
															
															if ( obj instanceof String ){
															
																new_cookies = (String)obj;
															}
															
															for ( String s: cookies_set ){
																
																new_cookies += (new_cookies.length()==0?"":"; ") + s;
															}
															
															setProperty( "URL_Cookie", new_cookies );
														}
													}catch( Throwable e ){
														
														Debug.out( e );
													}
													
													follow_redirect = true;
													
													continue redirect_label;
												}
											}catch( Throwable e ){
												
											}
										}
									}
									
									setProperty( "URL_HTTP_Response", new Long( response ));
	
									if ( 	response != HttpURLConnection.HTTP_CREATED && 
											response != HttpURLConnection.HTTP_ACCEPTED && 
											response != HttpURLConnection.HTTP_NO_CONTENT && 
											response != HttpURLConnection.HTTP_OK ) {
										
										HttpURLConnection	http_con = (HttpURLConnection)con;
										
										InputStream error_stream = http_con.getErrorStream();
										
										String error_str = null;
										
										if ( error_stream != null ){
											
											String encoding = con.getHeaderField( "content-encoding");
							 				
							 				if ( encoding != null ){
							 					
							 					if ( encoding.equalsIgnoreCase( "gzip"  )){
							 									 					
							 						error_stream = new GZIPInputStream( error_stream );
								 					
							 					}else if ( encoding.equalsIgnoreCase( "deflate" )){
								 						
							 						error_stream = new InflaterInputStream( error_stream );
							 					}
							 				}
							 				
											error_str = FileUtil.readInputStreamAsString( error_stream, 512 );
										}
										
											// grab properties anyway as they may be useful
										
										getRequestProperties( con );
										
										throw( new ResourceDownloaderException( this, "Error on connect for '" + trimForDisplay( current_url ) + "': " + Integer.toString(response) + " " + http_con.getResponseMessage() + (error_str==null?"":( ": error=" + error_str ))));    
									}
									
									getRequestProperties( con );
									
									boolean compressed = false;
									
									try{
										this_mon.enter();
										
										input_stream = con.getInputStream();
										
										String encoding = con.getHeaderField( "content-encoding");
						 				
						 				if ( encoding != null ){
						 					
						 					if ( encoding.equalsIgnoreCase( "gzip"  )){
		
								 				compressed = true;
							 									 					
							 					input_stream = new GZIPInputStream( input_stream );
							 					
						 					}else if ( encoding.equalsIgnoreCase( "deflate" )){
		
						 						compressed = true;
						 						
						 						input_stream = new InflaterInputStream( input_stream );
						 					}
						 				}
									}finally{
										
										this_mon.exit();
									}
									
									if ( con instanceof MagnetConnection2 ){
										
											// hack - status reports for magnet connections are returned
										
										List<String> errors = ((MagnetConnection2) con).getResponseMessages( true );
										
										if ( errors.size() > 0 ){
											
											throw( new ResourceDownloaderException( this, errors.get(0)));
										}
									}
									
									ByteArrayOutputStream	baos		= null;
									FileOutputStream		fos			= null;
									
									try{
										byte[] buf = new byte[BUFFER_SIZE];
										
										long	total_read	= 0;
										
											// unfortunately not all servers set content length
										
										/* From Apache's mod_deflate doc:
										 * http://httpd.apache.org/docs/2.0/mod/mod_deflate.html
												Note on Content-Length
		
												If you evaluate the request body yourself, don't trust the
												Content-Length header! The Content-Length header reflects 
												the length of the incoming data from the client and not the
												byte count of the decompressed data stream.
										 */
										long size = compressed ? -1 : UrlUtils.getContentLength( con );					
										
										baos = size>0?new ByteArrayOutputStream(size>MAX_IN_MEM_READ_SIZE?MAX_IN_MEM_READ_SIZE:(int)size):new ByteArrayOutputStream();
										
										while( !cancel_download ){
											
											if ( size >= 0 && total_read >= size && trust_content_length ){
												
												break;
											}
											
											int read = input_stream.read(buf);
												
											if ( read > 0 ){
											
												if ( total_read > MAX_IN_MEM_READ_SIZE ){
													
													if ( fos == null ){
														
														temp_file = AETemporaryFileHandler.createTempFile();
														
														fos = new FileOutputStream( temp_file );
														
														fos.write( baos.toByteArray());
														
														baos = null;
													}
													
													fos.write( buf, 0, read );
													
												}else{
													
													baos.write(buf, 0, read);
												}
												
												total_read += read;
										        
												informAmountComplete( total_read );
												
												if ( size > 0){
													
													informPercentDone((int)(( 100 * total_read ) / size ));
												}
											}else{
												
												break;
											}
										}
										
											// if we've got a size, make sure we've read all of it
										
										if ( size > 0 && total_read != size ){
											
											if ( total_read > size ){
												
													// this has been seen with UPnP linksys - more data is read than
													// the content-length has us believe is coming (1 byte in fact...)
												
												Debug.outNoStack( "Inconsistent stream length for '" + trimForDisplay( original_url ) + "': expected = " + size + ", actual = " + total_read );
												
											}else{
												
												throw( new IOException( "Premature end of stream" ));
											}
										}
									}finally{
										
										if ( fos != null ){
											
											try{
												fos.close();
												
											}catch( Throwable e ){
											}
										}
										
										input_stream.close();
									}
						
									InputStream	res;
									
									if ( temp_file != null ){
									
										res = new DeleteFileOnCloseInputStream( temp_file );
										
										temp_file = null;
										
									}else{
										
										res = new ByteArrayInputStream( baos.toByteArray());
									}
									
									boolean	handed_over = false;
									
									try{
										if ( informComplete( res )){
													
											handed_over = true;
											
											return( res );
										}
									}finally{
									
										if ( !handed_over ){
											
											res.close();
										}
									}
									
									throw( new ResourceDownloaderException( this, "Contents downloaded but rejected: '" + trimForDisplay( original_url ) + "'" ));
			
								}catch( SSLException e ){
									
									if ( connect_loop == 0 ){
										
										if ( SESecurityManager.installServerCertificates( current_url ) != null ){
											
												// certificate has been installed
											
											continue;	// retry with new certificate
										}
									}
		
									throw( e );
									
								}catch( ZipException e ){
									
									if ( connect_loop == 0 ){
										
										use_compression = false;
										
										continue;
									}
								}catch( IOException e ){
									
									if ( connect_loop == 0 ){
										
										String	msg = e.getMessage();
										
										if ( msg != null ){
											
											msg = msg.toLowerCase( MessageText.LOCALE_ENGLISH );
											
											if ( msg.indexOf( "gzip" ) != -1 ){
									
												use_compression = false;
												
												continue;
											}
										}
																      			
							      		URL retry_url = UrlUtils.getIPV4Fallback( current_url );
							      			
							      		if ( retry_url != null ){
							      				
							      			current_url = retry_url;
							      			
							      			continue;
							      		}
									}
									
									throw( e );
									
								}finally{
									
									if ( temp_file != null ){
										
										temp_file.delete();
									}
								}
							}
						}finally{
							
							if ( plugin_proxy != null ){
								
								plugin_proxy.setOK( ok );
							}
						}
					}
					
					throw( new ResourceDownloaderException( this, "Should never get here" ));
					
				}finally{
							
					if ( auth_supplied ){
								
						SESecurityManager.setPasswordHandler( outer_url, null );
					}
					
					if ( force_no_proxy ){
						
						AEProxySelectorFactory.getSelector().endNoProxy();
					}
				}
			}catch (java.net.MalformedURLException e){
				
				throw( new ResourceDownloaderException( this, "Exception while parsing URL '" + trimForDisplay( original_url ) + "':" + e.getMessage(), e));
				
			}catch (java.net.UnknownHostException e){
				
				throw( new ResourceDownloaderException( this, "Exception while initializing download of '" + trimForDisplay( original_url ) + "': Unknown Host '" + e.getMessage() + "'", e));
				
			}catch (java.io.IOException e ){
				
				throw( new ResourceDownloaderException( this, "I/O Exception while downloading '" + trimForDisplay( original_url ) + "'", e ));
			}
		}catch( Throwable e ){
			
			ResourceDownloaderException	rde;
			
			if ( e instanceof ResourceDownloaderException ){
				
				rde = (ResourceDownloaderException)e;
				
			}else{
				Debug.out(e);
				
				rde = new ResourceDownloaderException( this, "Unexpected error", e );
			}
			
			informFailed(rde);
			
			throw( rde );
		}
	}
	
	public void
	cancel()
	{
		setCancelled();
		
		cancel_download	= true;
		
		try{
			this_mon.enter();
			
			if ( input_stream != null ){
				
				try{
					input_stream.close();
					
				}catch( Throwable e ){
					
				}
			}
		}finally{
			
			this_mon.exit();
		}
		
		informFailed( new ResourceDownloaderCancelledException(  this  ));
	}
	
	protected void
	setRequestProperties(
		URLConnection		con,
		boolean				use_compression )
	{
		Map properties = getLCKeyProperties();
		
		Iterator	it = properties.entrySet().iterator();
		
		while( it.hasNext()){
			
			Map.Entry entry = (Map.Entry)it.next();
			
			String	key 	= (String)entry.getKey();
			Object	value	= entry.getValue();
			
			if ( key.startsWith( "url_" ) && value instanceof String ){
			
				if ( value.equals( "skip" )){
					
					continue;
				}
				
				if ( key.equalsIgnoreCase( "URL_HTTP_VERB" )){
					
					continue;
				}
				
				key = key.substring(4);
				
				if ( key.equals( "accept-encoding" ) && !use_compression ){
					
					//skip
					
				}else{
					
					con.setRequestProperty(key,(String)value);
				}
			}
		}
	}
	
	protected void
	getRequestProperties(
		URLConnection		con )
	{
		try{
			setProperty( ResourceDownloader.PR_STRING_CONTENT_TYPE, con.getContentType() );
			
			setProperty( "URL_URL", con.getURL());
			
			Map	headers = con.getHeaderFields();
			
			Iterator it = headers.entrySet().iterator();
			
			while( it.hasNext()){
				
				Map.Entry	entry = (Map.Entry)it.next();
				
				String	key = (String)entry.getKey();
				Object	val	= entry.getValue();
				
				if ( key != null ){
					
					setProperty( "URL_" + key, val );
				}
			}
			
			setPropertiesSet();
			
		}catch( Throwable e ){
			
			Debug.printStackTrace(e);
		}
	}
	
	public PasswordAuthentication
	getAuthentication(
		String		realm,
		URL			tracker )
	{
		if ( user_name == null || password == null ){
			
			String user_info = tracker.getUserInfo();
			
			if ( user_info == null ){
				
				return( null );
			}
			
			String	user_bit	= user_info;
			String	pw_bit		= "";
			
			int	pos = user_info.indexOf(':');
			
			if ( pos != -1 ){
				
				user_bit	= user_info.substring(0,pos);
				pw_bit		= user_info.substring(pos+1);
			}
			
			return( new PasswordAuthentication( user_bit, pw_bit.toCharArray()));
		}
		
		return( new PasswordAuthentication( user_name, password.toCharArray()));
	}
	
	public void
	setAuthenticationOutcome(
		String		realm,
		URL			tracker,
		boolean		success )
	{
	}
	
	public void
	clearPasswords()
	{
	}
	
	private URLConnection 
	openConnection(
		Proxy		proxy,
		URL 		url ) 
	
		throws IOException 
	{
		if ( force_no_proxy ){
		
			return( url.openConnection( Proxy.NO_PROXY ));
			
		}else if ( proxy != null ){
			
			return( url.openConnection( proxy ));

		}else{
			
			return url.openConnection();
		}
	}
	
	protected String
	trimForDisplay(
		URL		url )
	{
		String str = url.toString();
		
		int pos = str.indexOf( '?' );
		
		if ( pos != -1 ){
			
			str = str.substring( 0, pos );
		}
		
		return( str );
	}
}