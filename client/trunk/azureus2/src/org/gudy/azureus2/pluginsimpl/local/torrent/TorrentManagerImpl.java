/*
 * File    : TorrentManagerImpl.java
 * Created : 28-Feb-2004
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

package org.gudy.azureus2.pluginsimpl.local.torrent;

/**
 * @author parg
 *
 */

import java.util.*;
import java.net.URL;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

import org.gudy.azureus2.plugins.torrent.*;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.core3.internat.*;
import org.gudy.azureus2.core3.torrent.*;

public class 
TorrentManagerImpl 
	implements TorrentManager, TOTorrentProgressListener
{
	private static TorrentManagerImpl	singleton;
	private static AEMonitor 			class_mon 	= new AEMonitor( "TorrentManager" );

	private static TorrentAttribute	category_attribute = new TorrentAttributeCategoryImpl();
	private static TorrentAttribute	networks_attribute = new TorrentAttributeNetworksImpl();
	private static TorrentAttribute	peer_sources_attribute = new TorrentAttributePeerSourcesImpl();
	
	private static Map	attribute_map = new HashMap();
	
	static{
		attribute_map.put( TorrentAttribute.TA_CATEGORY, 	category_attribute );
		attribute_map.put( TorrentAttribute.TA_NETWORKS, 	networks_attribute );
		attribute_map.put( TorrentAttribute.TA_PEER_SOURCES, peer_sources_attribute );
	}
	
	public static TorrentManagerImpl
	getSingleton()
	{
		try{
			class_mon.enter();
		
			if ( singleton == null ){
				
				singleton = new TorrentManagerImpl();
			}
			
			return( singleton );
			
		}finally{
			
			class_mon.exit();
		}
	}
	
	protected List		listeners = new ArrayList();
	
	protected
	TorrentManagerImpl()
	{

	}
	
	public TorrentDownloader
	getURLDownloader(
		URL		url )
	
		throws TorrentException
	{
		return( new TorrentDownloaderImpl( this, url ));
	}
	
	public TorrentDownloader
	getURLDownloader(
		URL		url,
		String	user_name,
		String	password )
	
		throws TorrentException
	{
		return( new TorrentDownloaderImpl( this, url, user_name, password ));
	}
	
	public Torrent
	createFromBEncodedFile(
		File		file )
	
		throws TorrentException
	{
		try{
			return( new TorrentImpl(TorrentUtils.readFromFile( file, false )));
			
		}catch( TOTorrentException e ){
			
			throw( new TorrentException( "TorrentManager::createFromBEncodedFile Fails", e ));
		}
	}
	
	public Torrent
	createFromBEncodedInputStream(
		InputStream		data )
	
		throws TorrentException
	{
		try{
			return( new TorrentImpl(TOTorrentFactory.deserialiseFromBEncodedInputStream( data )));
				
		}catch( TOTorrentException e ){
				
			throw( new TorrentException( "TorrentManager::createFromBEncodedFile Fails", e ));
		}
	}
	
	public Torrent
	createFromBEncodedData(
		byte[]		data )
	
		throws TorrentException
	{
		ByteArrayInputStream	is = null;
		
		try{
			is = new ByteArrayInputStream( data );
			
			return( new TorrentImpl(TOTorrentFactory.deserialiseFromBEncodedInputStream(is)));
			
		}catch( TOTorrentException e ){
			
			throw( new TorrentException( "TorrentManager::createFromBEncodedData Fails", e ));
			
		}finally{
			
			try{
				is.close();
				
			}catch( Throwable e ){
				
				Debug.printStackTrace( e );
			}
		}
	}
	
	public Torrent
	createFromDataFile(
		File		data,
		URL			announce_url )
	
		throws TorrentException
	{
		return( createFromDataFile( data, announce_url, false ));
	}
	
	public Torrent
	createFromDataFile(
		File		data,
		URL			announce_url,
		boolean		include_other_hashes )
	
		throws TorrentException
	{
		try{
			TOTorrentCreator c = TOTorrentFactory.createFromFileOrDirWithComputedPieceLength( data, announce_url, include_other_hashes);
			
			c.addListener( this );
			
			return( new TorrentImpl(c.create()));
			
		}catch( TOTorrentException e ){
			
			throw( new TorrentException( "TorrentManager::createFromDataFile Fails", e ));
		}	}
	
	public TorrentAttribute[]
	getDefinedAttributes()
	{
		Collection	entries = attribute_map.values();
		
		TorrentAttribute[]	res = new TorrentAttribute[entries.size()];
		
		entries.toArray( res );
		
		return( res );
	}
	
	public TorrentAttribute
	getAttribute(
		String		name )
	{
		return((TorrentAttribute)attribute_map.get(name));
	}
	
	public void
	reportProgress(
		int		percent_complete )
	{
	}
		
	public void
	reportCurrentTask(
		final String	task_description )
	{
		for (int i=0;i<listeners.size();i++){
			
			((TorrentManagerListener)listeners.get(i)).event(
					new TorrentManagerEvent()
					{
						public Object
						getData()
						{
							return( task_description );
						}
					});
		}
	}
	
	protected void
	tryToSetTorrentEncoding(
		TOTorrent	torrent,
		String		encoding )
	
		throws TorrentEncodingException
	{
		try{
			LocaleUtil.getSingleton().setTorrentEncoding( torrent, encoding );
			
		}catch( LocaleUtilEncodingException e ){
			
			String[]	charsets = e.getValidCharsets();
			
			if ( charsets == null ){
				
				throw( new TorrentEncodingException("Failed to set requested encoding", e));
				
			}else{
				
				throw( new TorrentEncodingException(charsets,e.getValidTorrentNames()));
			}
		}
	}
	
	protected void
	tryToSetDefaultTorrentEncoding(
		TOTorrent		torrent )
	
		throws TorrentException 
	{
		try{
			LocaleUtil.getSingleton().setDefaultTorrentEncoding( torrent );
			
		}catch( LocaleUtilEncodingException e ){
			
			String[]	charsets = e.getValidCharsets();
			
			if ( charsets == null ){
				
				throw( new TorrentEncodingException("Failed to set default encoding", e));
				
			}else{
				
				throw( new TorrentEncodingException(charsets,e.getValidTorrentNames()));
			}
		}
	}
	
	public void
	addListener(
		TorrentManagerListener	l )
	{
		listeners.add( l );
	}
		
	public void
	removeListener(
		TorrentManagerListener	l )
	{
		listeners.remove(l);
	}
}
