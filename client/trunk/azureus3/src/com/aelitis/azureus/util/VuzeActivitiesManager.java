/**
 * Created on Jan 28, 2008 
 *
 * Copyright 2008 Vuze, Inc.  All rights reserved.
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License only.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA 
 */

package com.aelitis.azureus.util;

import java.util.*;

import org.gudy.azureus2.core3.disk.DiskManagerFileInfo;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.download.DownloadManagerListener;
import org.gudy.azureus2.core3.download.DownloadManagerState;
import org.gudy.azureus2.core3.global.GlobalManager;
import org.gudy.azureus2.core3.global.GlobalManagerListener;
import org.gudy.azureus2.core3.torrent.TOTorrent;
import org.gudy.azureus2.core3.util.*;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.messenger.config.PlatformRatingMessenger;
import com.aelitis.azureus.core.messenger.config.PlatformVuzeActivitiesMessenger;
import com.aelitis.azureus.core.messenger.config.PlatformRatingMessenger.GetRatingReply;
import com.aelitis.azureus.core.torrent.PlatformTorrentUtils;
import com.aelitis.azureus.ui.swt.skin.SWTSkin;
import com.aelitis.azureus.ui.swt.utils.ImageLoader;

/**
 * Manage Vuze News Entries.  Loads, Saves, and expires them
 * 
 * @author TuxPaper
 * @created Jan 28, 2008
 *
 */
public class VuzeActivitiesManager
{
	public static final long MAX_LIFE_MS = 1000L * 60 * 60 * 24 * 30;

	private static final long DEFAULT_PLATFORM_REFRESH = 60 * 60 * 1000L * 24;

	private static final long RATING_REMINDER_DELAY = 1000L * 60 * 60 * 24 * 3;

	private static final String SAVE_FILENAME = "VuzeActivities.config";

	protected static final String TYPEID_DL_COMPLETE = "DL-Complete";

	protected static final String TYPEID_DL_ADDED = "DL-Added";

	protected static final String TYPEID_DL_REMOVE = "DL-Remove";

	protected static final String TYPEID_RATING_REMINDER = "Rating-Reminder";

	protected static final boolean SHOW_DM_REMOVED_ACTIVITY = false;

	private static ArrayList listeners = new ArrayList();

	private static ArrayList allEntries = new ArrayList();

	private static List removedEntries = new ArrayList();

	private static PlatformVuzeActivitiesMessenger.GetEntriesReplyListener replyListener;

	private static AEDiagnosticsLogger diag_logger;

	private static long lastVuzeNewsAt;

	private static boolean skipAutoSave = true;

	private static DownloadManagerListener dmListener;

	private static SWTSkin skin;

	private static ImageLoader imageLoader;

	static {
		if (System.getProperty("debug.vuzenews", "0").equals("1")) {
			diag_logger = AEDiagnostics.getLogger("v3.vuzenews");
			diag_logger.log("\n\nVuze News Logging Starts");
		} else {
			diag_logger = null;
		}
	}

	public static void initialize(AzureusCore core, SWTSkin skin) {
		VuzeActivitiesManager.skin = skin;
		imageLoader = skin.getImageLoader(skin.getSkinProperties());
		if (diag_logger != null) {
			diag_logger.log("Initialize Called");
		}

		loadEvents();

		replyListener = new PlatformVuzeActivitiesMessenger.GetEntriesReplyListener() {
			public void gotVuzeNewsEntries(VuzeActivitiesEntry[] entries,
					long refreshInMS) {
				if (diag_logger != null) {
					diag_logger.log("Received Reply from platform with " + entries.length
							+ " entries.  Refresh in " + refreshInMS);
				}

				addEntries(entries);

				if (refreshInMS <= 0) {
					refreshInMS = DEFAULT_PLATFORM_REFRESH;
				}

				SimpleTimer.addEvent("GetVuzeNews",
						SystemTime.getOffsetTime(refreshInMS), new TimerEventPerformer() {
							public void perform(TimerEvent event) {
								PlatformVuzeActivitiesMessenger.getEntries(Math.min(
										SystemTime.getCurrentTime() - lastVuzeNewsAt, MAX_LIFE_MS),
										5000, replyListener);
								lastVuzeNewsAt = SystemTime.getCurrentTime();
							}
						});
			}
		};

		pullActivitiesNow(5000);

		PlatformRatingMessenger.addListener(new PlatformRatingMessenger.RatingUpdateListener() {
			public void ratingUpdated(GetRatingReply rating) {
				Object[] allEntriesArray = allEntries.toArray();
				for (int i = 0; i < allEntriesArray.length; i++) {
					VuzeActivitiesEntry entry = (VuzeActivitiesEntry) allEntriesArray[i];
					if (entry.getTypeID().equals(TYPEID_RATING_REMINDER) && entry.dm != null) {
						try {
							String hash = entry.dm.getTorrent().getHashWrapper().toBase32String();
							if (rating.hasHash(hash)) {
								removeEntries(new VuzeActivitiesEntry[] {
									entry
								});
							}
						} catch (Exception e) {
						}
					}
				}
			}
		});

		dmListener = new DownloadManagerListener() {

			public void stateChanged(DownloadManager manager, int state) {
				// TODO Auto-generated method stub

			}

			public void positionChanged(DownloadManager download, int oldPosition,
					int newPosition) {
				// TODO Auto-generated method stub

			}

			public void filePriorityChanged(DownloadManager download,
					DiskManagerFileInfo file) {
				// TODO Auto-generated method stub

			}

			public void downloadComplete(DownloadManager dm) {
				try {
					String hash = dm.getTorrent().getHashWrapper().toBase32String();

					//System.out.println("DC " + dm.getDisplayName());
					VuzeActivitiesEntry[] entries = getAllEntries();
					for (int i = 0; i < entries.length; i++) {
						VuzeActivitiesEntry oldEntry = entries[i];
						if (oldEntry.dm != null && oldEntry.dm.equals(dm)
								&& oldEntry.getTypeID().equals(TYPEID_DL_ADDED)) {
							//System.out.println("remove added entry " + oldEntry.id);
							removeEntries(new VuzeActivitiesEntry[] {
								oldEntry
							});
						}
					}

					long completedTime = dm.getDownloadState().getLongParameter(
							DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME);

					String title;
					if (PlatformTorrentUtils.isContent(dm.getTorrent(), true)) {
						String url = Constants.URL_PREFIX + Constants.URL_DETAILS + hash
								+ ".html?" + Constants.URL_SUFFIX;
						title = "<A HREF=\"" + url + "\">"
								+ PlatformTorrentUtils.getContentTitle2(dm) + "</A>";
					} else {
						title = PlatformTorrentUtils.getContentTitle2(dm);
					}

					VuzeActivitiesEntry entry = new VuzeActivitiesEntry();
					entry.type = 1;
					entry.setTimestamp(completedTime);
					entry.id = hash + ";c" + entry.getTimestamp();
					entry.text = title + " has completed downloading";
					entry.setTypeID(TYPEID_DL_COMPLETE, true);
					entry.dm = dm;

					addEntries(new VuzeActivitiesEntry[] {
						entry
					});
				} catch (Throwable t) {
					Debug.out(t);
				}
				dm.removeListener(this);
			}

			public void completionChanged(DownloadManager manager, boolean completed) {
				// TODO Auto-generated method stub

			}
		};

		GlobalManager gm = core.getGlobalManager();
		gm.addListener(new GlobalManagerListener() {

			public void seedingStatusChanged(boolean seeding_only_mode) {
			}

			public void downloadManagerRemoved(DownloadManager dm) {
				try {
					if (PlatformTorrentUtils.getAdId(dm.getTorrent()) != null) {
						return;
					}

					VuzeActivitiesEntry[] entries = getAllEntries();
					for (int i = 0; i < entries.length; i++) {
						VuzeActivitiesEntry oldEntry = entries[i];
						if (oldEntry.dm != null && oldEntry.dm.equals(dm)) {
							removeEntries(new VuzeActivitiesEntry[] {
								oldEntry
							});
						}
					}
					if (SHOW_DM_REMOVED_ACTIVITY) {
  					VuzeActivitiesEntry entry = new VuzeActivitiesEntry();
  
  					String hash = dm.getTorrent().getHashWrapper().toBase32String();
  					String title;
  					if (PlatformTorrentUtils.isContent(dm.getTorrent(), true)) {
  						String url = Constants.URL_PREFIX + Constants.URL_DETAILS + hash
  								+ ".html?" + Constants.URL_SUFFIX;
  						title = "<A HREF=\"" + url + "\">"
  								+ PlatformTorrentUtils.getContentTitle2(dm) + "</A>";
  						entry.assetHash = hash;
  					} else {
  						title = PlatformTorrentUtils.getContentTitle2(dm);
  					}
  
  					entry.type = 1;
  					entry.setTimestamp(SystemTime.getCurrentTime());
  					entry.id = hash + ";r" + entry.getTimestamp();
  					entry.text = title + " has been removed from your library";
  					entry.setTypeID(TYPEID_DL_REMOVE, true);
  					addEntries(new VuzeActivitiesEntry[] {
  						entry
  					});
					}
				} catch (Throwable t) {
					// ignore
				}

				dm.removeListener(dmListener);
			}

			public void downloadManagerAdded(DownloadManager dm) {
				TOTorrent torrent = dm.getTorrent();
				if (PlatformTorrentUtils.getAdId(torrent) != null) {
					return;
				}
				boolean isContent = PlatformTorrentUtils.isContent(torrent, true);

				if (dm.getAssumedComplete()) {
					dmListener.downloadComplete(dm);
				} else {
  				try {
  					long addedOn = (dm == null) ? 0
  							: dm.getDownloadState().getLongParameter(
  									DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME);
  					if (addedOn < getCutoffTime()) {
  						return;
  					}
  
  					VuzeActivitiesEntry entry = new VuzeActivitiesEntry();
  					String hash = torrent.getHashWrapper().toBase32String();
  
  					String title;
  					if (isContent) {
  						String url = Constants.URL_PREFIX + Constants.URL_DETAILS + hash
  								+ ".html?" + Constants.URL_SUFFIX;
  						title = "<A HREF=\"" + url + "\">"
  								+ PlatformTorrentUtils.getContentTitle2(dm) + "</A>";
  						entry.assetHash = hash;
  					} else {
  						title = PlatformTorrentUtils.getContentTitle2(dm);
  					}
  
  					entry.type = 1;
  					entry.id = hash + ";a" + addedOn;
  					entry.text = title + " has been added to your download list";
  					entry.setTimestamp(addedOn);
  					entry.setTypeID(TYPEID_DL_ADDED, true);
  					entry.dm = dm;
  					addEntries(new VuzeActivitiesEntry[] {
  						entry
  					});
  				} catch (Throwable t) {
  					// ignore
  				}
				}

				try {
					if (isContent) {
						long completedOn = dm.getDownloadState().getLongParameter(
								DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME);
						if (completedOn > 0
								&& SystemTime.getCurrentTime() - completedOn > RATING_REMINDER_DELAY
								&& completedOn + RATING_REMINDER_DELAY > getCutoffTime()) {
							int userRating = PlatformTorrentUtils.getUserRating(torrent);
							if (userRating < 0) {
								VuzeActivitiesEntry entry = new VuzeActivitiesEntry();

								String hash = torrent.getHashWrapper().toBase32String();
								String title;
								String url = Constants.URL_PREFIX + Constants.URL_DETAILS
										+ hash + ".html?" + Constants.URL_SUFFIX;
								title = "<A HREF=\"" + url + "\">"
										+ PlatformTorrentUtils.getContentTitle2(dm) + "</A>";
								entry.assetHash = hash;

								entry.dm = dm;
								entry.showThumb = false;
								entry.type = 1;
								entry.id = hash + ";r" + completedOn;
								entry.text = "To improve your recommendations, please rate "
										+ title;
								entry.setTimestamp(SystemTime.getCurrentTime());
								entry.setTypeID(TYPEID_RATING_REMINDER, true);

								addEntries(new VuzeActivitiesEntry[] {
									entry
								});
							}
						}
					}
				} catch (Throwable t) {
					// ignore
				}

				dm.addListener(dmListener);
			}

			public void destroyed() {
			}

			public void destroyInitiated() {
			}
		}, true);
	}

	/**
	 * Pull entries from webapp
	 * 
	 * @param delay max time to wait before running request
	 *
	 * @since 3.0.4.3
	 */
	public static void pullActivitiesNow(long delay) {
		PlatformVuzeActivitiesMessenger.getEntries(Math.min(
				SystemTime.getCurrentTime() - lastVuzeNewsAt, MAX_LIFE_MS), delay,
				replyListener);
		lastVuzeNewsAt = SystemTime.getCurrentTime();
	}

	/**
	 * Pull entries from webapp
	 * 
	 * @param agoMS Pull all events within this timespan (ms)
	 * @param delay max time to wait before running request
	 *
	 * @since 3.0.4.3
	 */
	public static void pullActivitiesNow(long agoMS, long delay) {
		PlatformVuzeActivitiesMessenger.getEntries(agoMS, delay, replyListener);
		lastVuzeNewsAt = SystemTime.getCurrentTime();
	}

	/**
	 * Clear the removed entries list so that an entry that was once deleted will
	 * will be able to be added again
	 * 
	 *
	 * @since 3.0.4.3
	 */
	public static void resetRemovedEntries() {
		removedEntries.clear();
		saveEvents();
	}

	/**
	 * 
	 *
	 * @since 3.0.4.3
	 */
	private static void loadEvents() {
		skipAutoSave = true;

		try {

			Map map = BDecoder.decodeStrings(FileUtil.readResilientConfigFile(SAVE_FILENAME));

			lastVuzeNewsAt = MapUtils.getMapLong(map, "LastCheck", 0);
			long cutoffTime = getCutoffTime();
			if (lastVuzeNewsAt < cutoffTime) {
				lastVuzeNewsAt = cutoffTime;
			}

			Object value;

			List newRemovedEntries = (List) MapUtils.getMapObject(map,
					"removed-entries", null, List.class);
			if (newRemovedEntries != null) {
				for (Iterator iter = newRemovedEntries.iterator(); iter.hasNext();) {
					value = iter.next();
					if (!(value instanceof Map)) {
						continue;
					}
					VuzeActivitiesEntry entry = VuzeActivitiesEntry.readFromMap((Map) value);

					if (entry.getTimestamp() > cutoffTime) {
						removedEntries.add(entry);
					}
				}
			}

			value = map.get("entries");
			if (!(value instanceof List)) {
				return;
			}

			List entries = (List) value;
			for (Iterator iter = entries.iterator(); iter.hasNext();) {
				value = iter.next();
				if (!(value instanceof Map)) {
					continue;
				}

				VuzeActivitiesEntry entry = VuzeActivitiesEntry.readFromMap((Map) value);

				if (entry.getTimestamp() > cutoffTime) {
					addEntries(new VuzeActivitiesEntry[] {
						entry
					});
				}
			}
		} finally {
			skipAutoSave = false;
		}
	}

	private static void saveEvents() {
		if (skipAutoSave) {
			return;
		}

		Map mapSave = new HashMap();
		mapSave.put("LastCheck", new Long(lastVuzeNewsAt));

		List entriesList = new ArrayList();

		VuzeActivitiesEntry[] allEntriesArray = getAllEntries();
		for (int i = 0; i < allEntriesArray.length; i++) {
			VuzeActivitiesEntry entry = allEntriesArray[i];
			if (entry.type > 0) {
				entriesList.add(entry.toMap());
			}
		}
		mapSave.put("entries", entriesList);

		List removedEntriesList = new ArrayList();
		for (Iterator iter = removedEntries.iterator(); iter.hasNext();) {
			VuzeActivitiesEntry entry = (VuzeActivitiesEntry) iter.next();
			removedEntriesList.add(entry.toMap());
		}
		mapSave.put("removed-entries", removedEntriesList);

		FileUtil.writeResilientConfigFile(SAVE_FILENAME, mapSave);
	}

	public static long getCutoffTime() {
		return SystemTime.getOffsetTime(-MAX_LIFE_MS);
	}

	public static void addListener(VuzeActivitiesListener l) {
		listeners.add(l);
	}

	public static void removeListener(VuzeActivitiesListener l) {
		listeners.remove(l);
	}

	/**
	 * 
	 * @param entries
	 * @return list of entries actually added (no dups)
	 *
	 * @since 3.0.4.3
	 */
	public static VuzeActivitiesEntry[] addEntries(VuzeActivitiesEntry[] entries) {
		long cutoffTime = getCutoffTime();

		ArrayList newEntries = new ArrayList();
		for (int i = 0; i < entries.length; i++) {
			VuzeActivitiesEntry entry = entries[i];
			if ((entry.getTimestamp() >= cutoffTime || entry.type == 0)
					&& !allEntries.contains(entry) && !removedEntries.contains(entry.id)) {
				newEntries.add(entry);
				allEntries.add(entry);
			}
		}

		saveEvents();
		//Collections.sort(allEntries);

		VuzeActivitiesEntry[] newEntriesArray = (VuzeActivitiesEntry[]) newEntries.toArray(new VuzeActivitiesEntry[newEntries.size()]);

		Object[] listenersArray = listeners.toArray();
		for (int i = 0; i < listenersArray.length; i++) {
			VuzeActivitiesListener l = (VuzeActivitiesListener) listenersArray[i];
			l.vuzeNewsEntriesAdded(newEntriesArray);
		}

		return newEntriesArray;
	}

	public static void removeEntries(VuzeActivitiesEntry[] entries) {
		long cutoffTime = getCutoffTime();

		for (int i = 0; i < entries.length; i++) {
			VuzeActivitiesEntry entry = entries[i];
			//System.out.println("remove " + entry.id);
			allEntries.remove(entry);
			if (entry.getTimestamp() > cutoffTime && entry.type > 0) {
				removedEntries.add(entry);
			}
		}
		Object[] listenersArray = listeners.toArray();
		for (int i = 0; i < listenersArray.length; i++) {
			VuzeActivitiesListener l = (VuzeActivitiesListener) listenersArray[i];
			l.vuzeNewsEntriesRemoved(entries);
		}
		saveEvents();
	}

	public static VuzeActivitiesEntry[] getAllEntries() {
		return (VuzeActivitiesEntry[]) allEntries.toArray(new VuzeActivitiesEntry[allEntries.size()]);
	}

	public static void log(String s) {
		if (diag_logger != null) {
			diag_logger.log(s);
		}
	}

	/**
	 * @param vuzeActivitiesEntry
	 *
	 * @since 3.0.4.3
	 */
	public static void triggerEntryChanged(VuzeActivitiesEntry entry) {
		Object[] listenersArray = listeners.toArray();
		for (int i = 0; i < listenersArray.length; i++) {
			VuzeActivitiesListener l = (VuzeActivitiesListener) listenersArray[i];
			l.vuzeNewsEntryChanged(entry);
		}
		saveEvents();
	}
}
