/*
 * File    : DownloadImpl.java
 * Created : 06-Jan-2004
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

package org.gudy.azureus2.pluginsimpl.download;

/**
 * @author parg
 *
 */

import java.util.*;

import org.gudy.azureus2.core3.global.*;
import org.gudy.azureus2.core3.download.*;
import org.gudy.azureus2.core3.torrent.*;
import org.gudy.azureus2.core3.tracker.client.*;

import org.gudy.azureus2.plugins.torrent.Torrent;
import org.gudy.azureus2.pluginsimpl.torrent.TorrentImpl;

import org.gudy.azureus2.plugins.download.Download;
import org.gudy.azureus2.plugins.download.DownloadListener;
import org.gudy.azureus2.plugins.download.DownloadTrackerListener;
import org.gudy.azureus2.plugins.download.DownloadAnnounceResult;
import org.gudy.azureus2.plugins.download.DownloadScrapeResult;
import org.gudy.azureus2.plugins.download.DownloadStats;
import org.gudy.azureus2.plugins.download.DownloadException;
import org.gudy.azureus2.plugins.download.DownloadRemovalVetoException;
import org.gudy.azureus2.plugins.download.DownloadWillBeRemovedListener;

public class 
DownloadImpl
	implements Download, DownloadManagerListener, DownloadManagerTrackerListener
{
	protected DownloadManager		download_manager;
	
	protected int		latest_state		= ST_STOPPED;
	
	protected DownloadAnnounceResult	last_announce_result;
	protected DownloadScrapeResult		last_scrape_result;
	
	protected List		listeners 			= new ArrayList();
	protected List		tracker_listeners	= new ArrayList();
	protected List		removal_listeners 	= new ArrayList();
	
	protected
	DownloadImpl(
		DownloadManager		_dm )
	{
		download_manager	= _dm;
		
		download_manager.addListener( this );
	}
	
	protected DownloadManager
	getDownload()
	{
		return( download_manager );
	}
	
	public int
	getState()
	{
		int	state = download_manager.getState();
			
		// dm states: waiting -> initialising -> initialized -> 
		//		disk states: allocating -> checking -> ready ->
		// dm states: downloading -> finishing -> seeding -> stopping -> stopped
		
		// "initialize" call takes from waiting -> initialising -> waiting (no port) or initialized (ok)
		// if initialized then disk manager runs through to ready
		// "startdownload" takes ready -> dl etc.
		// "stopIt" takes to stopped which is equiv to ready
		
		switch( state ){
			case DownloadManager.STATE_WAITING:
			{
				latest_state	= ST_WAITING;
				
				break;
			}		
			case DownloadManager.STATE_INITIALIZING:
			case DownloadManager.STATE_INITIALIZED:
			case DownloadManager.STATE_ALLOCATING:
			case DownloadManager.STATE_CHECKING:
			{
				latest_state	= ST_PREPARING;
					
				break;
			}
			case DownloadManager.STATE_READY:
			{
				latest_state	= ST_READY;
					
				break;
			}
			case DownloadManager.STATE_DOWNLOADING:
			case DownloadManager.STATE_FINISHING:		// finishing download - transit to seeding
			{
				latest_state	= ST_DOWNLOADING;
					
				break;
			}
			case DownloadManager.STATE_SEEDING:
			{
				latest_state	= ST_SEEDING;
				
				break;
			}
			case DownloadManager.STATE_STOPPING:
			{
				latest_state	= ST_STOPPING;
				
				break;
			}
			case DownloadManager.STATE_STOPPED:
			{
				latest_state	= ST_STOPPED;
					
				break;
			}
			case DownloadManager.STATE_ERROR:
			{
				latest_state	= ST_ERROR;
				
				break;
			}
			default:
			{
				latest_state	= ST_ERROR;
			}
		}
		
		return( latest_state );
	}
	
	public Torrent
	getTorrent()
	{
		TOTorrent	torrent = download_manager.getTorrent();
		
		if ( torrent == null ){
			
			return( null );
			
		}else{
			
			return( new TorrentImpl( torrent ));
		}
	}

	public void
	start()
	
		throws DownloadException
	{
		if ( download_manager.getState() == DownloadManager.STATE_STOPPED){
			
			download_manager.setState(DownloadManager.STATE_WAITING);
			
		}else{
			
			throw( new DownloadException( "Download::start: download not stopped" ));
		}
	}
	
	public void
	stop()
	
		throws DownloadException
	{
		if ( download_manager.getState() != DownloadManager.STATE_STOPPED){
			
			download_manager.stopIt();
			
		}else{
			
			throw( new DownloadException( "Download::stop: download already stopped" ));
		}
	}
	
	public boolean
	isStartStopLocked()
	{
		return( download_manager.isStartStopLocked());
	}
	
	public void
	remove()
	
		throws DownloadException, DownloadRemovalVetoException
	{
		if ( download_manager.getState() == DownloadManager.STATE_STOPPED){
			
			GlobalManager globalManager = download_manager.getGlobalManager();
			
			try{
				globalManager.removeDownloadManager(download_manager);
				
			}catch( GlobalManagerDownloadRemovalVetoException e ){
				
				throw( new DownloadRemovalVetoException( "Download::remove: operation vetoed" ));
			}
			
		}else{
			
			throw( new DownloadException( "Download::remove: download not stopped" ));
		}
	}
	
	public DownloadStats
	getStats()
	{
		return( new DownloadStatsImpl( download_manager ));
	}
	
	protected void
	isRemovable()
		throws DownloadRemovalVetoException
	{
		synchronized( removal_listeners ){
			
			for (int i=0;i<removal_listeners.size();i++){
				
				try{
					((DownloadWillBeRemovedListener)removal_listeners.get(i)).downloadWillBeRemoved(this);
					
				}catch( DownloadRemovalVetoException e ){
					
					throw( e );
					
				}catch( Throwable e ){
					
					e.printStackTrace();
				}
			}
		}
	}
	
	protected void
	destroy()
	{
		download_manager.removeListener( this );
	}
	
	// DownloadManagerListener methods
	
	public void
	stateChanged(
		int		state )
	{
		int	prev_state 	= latest_state;
		int	curr_state	= getState();
	
		if ( prev_state != curr_state ){
			
			synchronized( listeners ){
				
				for (int i=0;i<listeners.size();i++){
					
					try{
						((DownloadListener)listeners.get(i)).stateChanged( prev_state, curr_state );
					
					}catch( Throwable e ){
						
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	public void
	downloadComplete()
	{	
	}
	
	public void
	addListener(
		DownloadListener	l )
	{
		synchronized( listeners ){
			
			listeners.add(l);
		}
	}
	
	public void
	removeListener(
		DownloadListener	l )
	{
		synchronized( listeners ){
			
			listeners.remove(l);
		}
	}
	
	public DownloadAnnounceResult
	getLastAnnounceResult()
	{
		return( last_announce_result );
	}
	
	public DownloadScrapeResult
	getLastScrapeResult()
	{
		// TODO: scraping is *NOT* yet event driven from download manager
		
		TRTrackerScraperResponse response = download_manager.getTrackerScrapeResponse();
	
		last_scrape_result = new DownloadScrapeResultImpl( response );
		
		return( last_scrape_result );
	}
	
	
	public void
	scrapeResult(
		TRTrackerScraperResponse	response )
	{
		last_scrape_result		= new DownloadScrapeResultImpl( response );
		
		synchronized( tracker_listeners ){
			
			for (int i=0;i<tracker_listeners.size();i++){
				
				try{						
					((DownloadTrackerListener)tracker_listeners.get(i)).scrapeResult( last_scrape_result );

				}catch( Throwable e ){
					
					e.printStackTrace();
				}
			}
		}	
	}
	
	public void
	announceResult(
		TRTrackerResponse			response )
	{
		last_announce_result = new DownloadAnnounceResultImpl(this, response);
		

		synchronized( tracker_listeners ){
			
			for (int i=0;i<tracker_listeners.size();i++){
				
				try{						
					((DownloadTrackerListener)tracker_listeners.get(i)).announceResult( last_announce_result );

				}catch( Throwable e ){
					
					e.printStackTrace();
				}
			}
		}
	}
	
	public void
	addTrackerListener(
		DownloadTrackerListener	l )
	{
		synchronized( tracker_listeners ){
	
			tracker_listeners.add( l );
			
			if ( tracker_listeners.size() == 1 ){
				
				download_manager.addTrackerListener( this );
			}
		}
	}
	
	public void
	removeTrackerListener(
		DownloadTrackerListener	l )
	{
		synchronized( tracker_listeners ){
			
			tracker_listeners.remove( l );
			
			if ( tracker_listeners.size() == 0 ){
				
				download_manager.removeTrackerListener( this );
			}
		}
	}
	
	public void
	addDownloadWillBeRemovedListener(
		DownloadWillBeRemovedListener	l )
	{
		synchronized( removal_listeners ){
			
			removal_listeners.add(l);
		}
	}
	
	public void
	removeDownloadWillBeRemovedListener(
		DownloadWillBeRemovedListener	l ) 
	{
		synchronized( removal_listeners ){
			
			removal_listeners.remove(l);
		}
	}
}
