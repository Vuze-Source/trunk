/*
 * File    : Download.java
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

package org.gudy.azureus2.plugins.download;

import org.gudy.azureus2.plugins.torrent.Torrent;
import org.gudy.azureus2.plugins.torrent.TorrentAttribute;
import org.gudy.azureus2.plugins.peers.PeerManager;

/** Management of a Torrent's activity.
 * <PRE>
 * A download's lifecycle:
 * torrent gets added
 *    state -> QUEUED
 * slot becomes available, queued torrent is picked, "restart" executed
 *    state -> WAITING
 * state moves through PREPARING to READY
 *    state -> PREPARING
 *    state -> READY
 * execute "start" method
 *    state -> SEEDING -or- DOWNLOADING
 * if torrent is DOWNLOADING, and completes, state changes to SEEDING
 *
 * Path 1                   | Path 2
 * -------------------------+------------------------------------------------
 * execute "stop" method    | startstop rules are met, execute "stopandQueue"
 *    state -> STOPPING     |     state -> STOPPING
 *    state -> STOPPED      |     state -> STOPPED
 *                          |     state -> QUEUED
 * execute "remove" method -> deletes the download
 * a "stop" method call can be made when the download is in all states except STOPPED
 * </PRE>
 *
 * @author parg
 */

public interface 
Download 
{
  /** waiting to be told to start preparing */  
	public static final int ST_WAITING     = 1;
  /** getting files ready (allocating/checking) */  
	public static final int ST_PREPARING   = 2;
  /** ready to be started if required */  
	public static final int ST_READY       = 3;
  /** downloading */  
	public static final int ST_DOWNLOADING = 4;
  /** seeding */  
	public static final int ST_SEEDING     = 5;
  /** stopping */  
	public static final int ST_STOPPING    = 6;
  /** stopped, do not auto-start! */  
	public static final int ST_STOPPED     = 7;
  /** failed */  
	public static final int ST_ERROR       = 8;
  /** stopped, but ready for auto-starting */  
	public static final int ST_QUEUED      = 9;

	public static final String[] ST_NAMES = 
		{
			"",
			"Waiting",
			"Preparing",
			"Ready",
			"Downloading",
			"Seeding",
			"Stopping",
			"Stopped",
			"Error",
			"Queued",
		};
	
  /** Use more of the upload bandwidth than low priority downloads 
   *  don't change these as they are used by remote clients */
	
	public static final int	PR_HIGH_PRIORITY	= 1;
  /** Use less of the upload bandwidth than high priority downloads */  
	public static final int	PR_LOW_PRIORITY		= 2;
	
	
	/** get state from above ST_ set
   * @return ST_ constant
   *
   * @since 2.0.7.0
   */
	public int
	getState();

	/** When the download state is ERROR this method returns the error details
   * @return
   *
   * @since 2.0.7.0
   */
	public String
	getErrorStateDetails();
	
	/**
	 * Index of download. {@link #getPosition()}
	 * @return	index - 0 based
   *
   * @since 2.0.7.0
	 */
	public int
	getIndex();
	
	/**
	 * Each download has a corresponding torrent
	 * @return	the download's torrent
   *
   * @since 2.0.7.0
	 */
	public Torrent
	getTorrent();
	
	/**
	 * See lifecylce description above 
	 * @throws DownloadException
   *
   * @since 2.0.7.0
	 */
	public void
	initialize()
	
		throws DownloadException;

	/**
	 * See lifecylce description above 
	 * @throws DownloadException
   *
   * @since 2.0.7.0
	 */
	public void
	start()
	
		throws DownloadException;
	
	/**
	 * See lifecylce description above 
	 * @throws DownloadException
   *
   * @since 2.0.7.0
	 */
	public void
	stop()
	
		throws DownloadException;
	
	/**
	 * See lifecylce description above 
	 * @throws DownloadException
   *
   * @since 2.0.8.0
	 */
	public void
	stopAndQueue()
	
		throws DownloadException;

	/**
	 * See lifecylce description above 
	 * @throws DownloadException
   *
   * @since 2.0.7.0
	 */
	public void
	restart()
	
		throws DownloadException;
	
	
	/**
	 * When a download is "start-stop locked" it means that seeding rules shouldn't start or
	 * stop the download as it is under manual control
	 * @return True if download is locked and should not be started or stopped
   *
   * @since 2.0.7.0
	 */
	public boolean
	isStartStopLocked();
	
  /** Retrieves whether the download is force started
   * @return True if download is force started.  False if not.
   *
   * @since 2.0.8.0
   */  
	public boolean
	isForceStart();
	
  /** Set the forcestart state of the download
   * @param forceStart True - Download will start, despite any Start/Stop rules/limits<BR>
   * False - Turn forcestart state off.  Download may or may not stop, depending on
   * Start/Stop rules/limits
   *
   * @since 2.0.8.0
   */  
	public void
	setForceStart(boolean forceStart);
	
	/**
	 * Downloads can either be low or high priority (see PR_ constants above)
	 * @return the download's priority
	 *
	 * @deprecated >= 2.1.0.6 does nothing
	 * @since 2.0.7.0
	 */
	public int
	getPriority();
	
	/**
	 * This method sets a download's priority
	 * @param priority the required priority, see PR_ constants above
	 * @deprecated >= 2.1.0.6 does nothing
	 *
	 * @since 2.0.7.0
	 */
	public void
	setPriority(
		int		priority );
	
	/** When a download's priority is locked this means that seeding rules should not change
   * a downloads priority, it is under manual control
   * @return whether it is locked or not
   * @deprecated >= 2.0.8.0 does nothing
   *
   * @since 2.0.7.0
   */
	public boolean
	isPriorityLocked();
	
	/** Returns the name of the torrent.  Similar to Torrent.getName() and is usefull
   * if getTorrent() returns null and you still need the name.
   * @return name of the torrent
   *
   * @since 2.0.8.0
   */
	public String 
	getName();
	
	/** Returns the full file path and name of the .torrent file
	 *
	 * @return File name of the torrent.
   *
   * @since 2.1.0.0
	 */
  public String getTorrentFileName();
  
  
  	/**
  	 * Sets an attribute of this download. For category use the Category torrent attribute
  	 * @param attribute
  	 * @return
  	 */
  
  public String
  getAttribute(
  	TorrentAttribute		attribute );
  
  public void
  setAttribute(
  	TorrentAttribute		attribute,
	String					value );
  
  /** Returns the name of the Category
   *
   * @return name of the category
   *
   * @since 2.1.0.0
   */
  public String getCategoryName();
  
  /** Sets the category for the download 
   *
   * @param sName Category name
   *
   * @since 2.1.0.0
   */
  public void setCategory(String sName);

	/**
	 * Removes a download. The download must be stopped or in error. Removal may fail if another 
	 * component does not want the removal to occur - in this case a "veto" exception is thrown
	 * @throws DownloadException
	 * @throws DownloadRemovalVetoException
   *
   * @since 2.0.7.0
	 */
	public void
	remove()
	
		throws DownloadException, DownloadRemovalVetoException;
	
	/**
	 * Returns the current position in the queue
	 * Completed and Incompleted downloads have seperate position sets.  This means
	 * we can have a position x for Completed, and position x for Incompleted.
   *
   * @since 2.0.8.0
	 */
	public int
	getPosition();
	
		/**
		 * returns the time this download was created in milliseconds
		 * @return
		 */
	
	public long
	getCreationTime();
	
	/**
	 * Sets the position in the queue
	 * Completed and Incompleted downloads have seperate position sets
   *
   * @since 2.0.8.0
	 */
	public void
	setPosition(
		int newPosition);

	/**
	 * Moves the download position up one
   *
   * @since 2.1.0.0
	 */
	public void
	moveUp();
	
	/**
	 * Moves the download down one position
   *
   * @since 2.1.0.0
	 */
	public void
	moveDown();
	
	/**
	 * Tests whether or not a download can be removed. Due to synchronization issues it is possible
	 * for a download to report OK here but still fail removal.
	 * @return
	 * @throws DownloadRemovalVetoException
   *
   * @since 2.0.7.0
	 */
	public boolean
	canBeRemoved()
	
		throws DownloadRemovalVetoException;
	
	/**
	 * Gives access to the last announce result received from the tracker for the download
	 * @return
   *
   * @since 2.0.7.0
	 */
	public DownloadAnnounceResult
	getLastAnnounceResult();
	
	/**
	 * Gives access to the last scrape result received from the tracker for the download
	 * @return
   *
   * @since 2.0.7.0
	 */
	public DownloadScrapeResult
	getLastScrapeResult();
	
	/**
	 * Gives access to the download's statistics
	 * @return
   *
   * @since 2.0.7.0
	 */
	public DownloadStats
	getStats();
	
	/** Downloads can be persistent (be remembered accross Azureus sessions), or
	 * non-persistent.
	 *
	 * @return true - persistent<br>
	 *         false - non-persistent
     *
     * @since 2.1.0.0
	 */
	
    public boolean
    isPersistent();
  
    /**
     * Sets the maximum download speed in bytes per second. 0 -> unlimited
     * @since 2.1.0.2
     * @param kb
     */
    
  	public void
	setMaximumDownloadKBPerSecond(
		int		kb );
  	
  	public int
	getMaximumDownloadKBPerSecond();
    
    
    /**
     * Get the max upload rate allowed for this download.
     * @return upload rate in bytes per second, 0 for unlimited, -1 for upload disabled
     */
    public int getUploadRateLimitBytesPerSecond();
    
    /**
     * Set the max upload rate allowed for this download.
     * @param max_rate_bps limit in bytes per second, 0 for unlimited, -1 for upload disabled
     */
    public void setUploadRateLimitBytesPerSecond( int max_rate_bps );
    

  	/**
  	 * indicates if the download has completed or not 
  	 * @return
  	 * @since 2.1.0.4
  	 */
  	
  	public boolean
	isComplete();
  	
  	public String
	getSavePath();
  	
  		/**
  		 * return the current peer manager for the download. 
  		 * @return	null returned if torrent currently doesn't have one (e.g. it is stopped)
  		 */
  	
  	public PeerManager
	getPeerManager();
  	
  		/**
  		 * request a tracker announce 
  		 * @since 2.1.0.5
  		 */
  	
  	public void
	requestTrackerAnnounce();
  	
	/**
	 * Adds a listener to the download that will be informed of changes in the download's state
	 * @param l
   *
   * @since 2.0.7.0
	 */
	public void
	addListener(
		DownloadListener	l );
	
	/**
	 * Removes listeners added above
	 * @param l
   *
   * @since 2.0.7.0
	 */
	public void
	removeListener(
		DownloadListener	l );

	/**
	 * Adds a listener that will be informed when the latest announce/scrape results change
	 * @param l
   *
   * @since 2.0.7.0
	 */
	public void
	addTrackerListener(
		DownloadTrackerListener	l );
	
	/**
	 * Removes listeners added above
	 * @param l
   *
   * @since 2.0.7.0
	 */
	public void
	removeTrackerListener(
		DownloadTrackerListener	l );
	
	/**
	 * Adds a listener that will be informed when a download is about to be removed. This gives
	 * the implementor the opportunity to veto the removal
	 * @param l
   *
   * @since 2.0.7.0
	 */
	public void
	addDownloadWillBeRemovedListener(
		DownloadWillBeRemovedListener	l );
	
	/**
	 * Removes the listener added above
	 * @param l
   *
   * @since 2.0.7.0
	 */
	public void
	removeDownloadWillBeRemovedListener(
		DownloadWillBeRemovedListener	l );
	
	/**
	 * Adds a listener to the download that will be informed of 
	 * @param l
   *
   * @since 2.1.0.0
	 */
	public void
	addPeerListener(
		DownloadPeerListener	l );
	
	/**
	 * Removes listeners added above
	 * @param l
   *
   * @since 2.1.0.0
	 */
	public void
	removePeerListener(
		DownloadPeerListener	l );
  
	public void
	addPropertyListener(
		DownloadPropertyListener	l );
	
	public void
	removePropertyListener(
		DownloadPropertyListener	l );
	
  /**
   * Get the local peerID advertised to the download swarm.
   * @return self peer id
   * 
   * @since 2.1.0.5
   */
  public byte[] getDownloadPeerId();
}
