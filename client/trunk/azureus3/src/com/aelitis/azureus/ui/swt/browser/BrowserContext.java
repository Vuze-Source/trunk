/*
 * Created on Jul 19, 2006 10:16:26 PM
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
 * AELITIS, SAS au capital de 46,603.30 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 */
package com.aelitis.azureus.ui.swt.browser;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.*;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.*;

import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.plugins.utils.StaticUtilities;
import org.gudy.azureus2.plugins.utils.resourcedownloader.ResourceDownloader;
import org.gudy.azureus2.ui.swt.Utils;

import com.aelitis.azureus.core.impl.AzureusCoreImpl;
import com.aelitis.azureus.core.messenger.ClientMessageContextImpl;
import com.aelitis.azureus.core.messenger.browser.listeners.BrowserMessageListener;
import com.aelitis.azureus.core.messenger.config.PlatformConfigMessenger;
import com.aelitis.azureus.core.vuzefile.VuzeFile;
import com.aelitis.azureus.core.vuzefile.VuzeFileHandler;
import com.aelitis.azureus.ui.swt.browser.msg.MessageDispatcherSWT;
import com.aelitis.azureus.util.Constants;
import com.aelitis.azureus.util.JSONUtils;

/**
 * Manages the context for a single SWT {@link Browser} component,
 * including transactions, listeners and messages.
 * 
 * @author dharkness
 * @created Jul 19, 2006
 */
public class BrowserContext
	extends ClientMessageContextImpl
	implements DisposeListener
{
	private static final String CONTEXT_KEY = "BrowserContext";

	private static final String KEY_ENABLE_MENU = "browser.menu.enable";

	private Browser browser;

	private Display display;

	private boolean pageLoading = false;

	private String lastValidURL = null;

	private final boolean forceVisibleAfterLoad;

	private TimerEventPeriodic checkURLEvent;

	private Control widgetWaitIndicator;
	
	private MessageDispatcherSWT messageDispatcherSWT;

	protected boolean wiggleBrowser = org.gudy.azureus2.core3.util.Constants.isOSX;

	private torrentURLHandler		torrentURLHandler;

	private List loadingListeners = Collections.EMPTY_LIST;
	
	/**
	 * Creates a context and registers the given browser.
	 * 
	 * @param id unique identifier of this context
	 * @param browser the browser to be registered
	 */
	public 
	BrowserContext(
		String 		_id, 
		Browser 	_browser,
		Control 	_widgetWaitingIndicator, 
		boolean 	_forceVisibleAfterLoad ) 
	{
		super( _id, null );
		
		browser 				= _browser;
		forceVisibleAfterLoad 	= _forceVisibleAfterLoad;
		widgetWaitIndicator 	= _widgetWaitingIndicator;

		System.out.println( "Registered browser context: id=" + getID());
		
		messageDispatcherSWT = new MessageDispatcherSWT(this);
		
		setMessageDispatcher( messageDispatcherSWT );
				
		final TimerEventPerformer showBrowersPerformer = new TimerEventPerformer() {
			public void perform(TimerEvent event) {
				if (browser != null && !browser.isDisposed()) {
					Utils.execSWTThread(new AERunnable() {
						public void runSupport() {
							if (forceVisibleAfterLoad && browser != null
									&& !browser.isDisposed() && !browser.isVisible()) {
								browser.setVisible(true);
							}
						}
					});
				}
			}
		};

		final TimerEventPerformer hideIndicatorPerformer = new TimerEventPerformer() {
			public void perform(TimerEvent event) {
				setPageLoading(false);
				if (widgetWaitIndicator != null && !widgetWaitIndicator.isDisposed()) {
					Utils.execSWTThread(new AERunnable() {
						public void runSupport() {
							if (widgetWaitIndicator != null
									&& !widgetWaitIndicator.isDisposed()) {
								widgetWaitIndicator.setVisible(false);
							}
						}
					});
				}
			}
		};

		final TimerEventPerformer checkURLEventPerformer = new TimerEventPerformer() {
			public void perform(TimerEvent event) {
				if (browser != null && !browser.isDisposed()) {
					Utils.execSWTThreadLater(0, new AERunnable() {
						public void runSupport() {
							if (browser != null && !browser.isDisposed()) {
								browser.execute("try { "
										+ "tuxLocString = document.location.toString();"
										+ "if (tuxLocString.indexOf('res://') == 0) {"
										+ "  document.title = 'err: ' + tuxLocString;"
										+ "} else {"
										+ "  tuxTitleString = document.title.toString();"
										+ "  if (tuxTitleString.indexOf('408 ') == 0 || tuxTitleString.indexOf('503 ') == 0 || tuxTitleString.indexOf('500 ') == 0) "
										+ "  { document.title = 'err: ' + tuxTitleString; } " + "}"
										+ "} catch (e) { }");
							}
						}
					});
				}
			}
		};

		if (forceVisibleAfterLoad) {
			
			browser.setVisible(false);
		}
		
		setPageLoading(false);
		
		if (widgetWaitIndicator != null && !widgetWaitIndicator.isDisposed()) {
			widgetWaitIndicator.setVisible(false);
		}
		
		browser.addTitleListener(new TitleListener() {
			public void changed(TitleEvent event) {
				/*
				 * The browser might have been disposed already by the time this method is called 
				 */
				if(true == browser.isDisposed()){
					return;
				}
				
				if (!browser.isVisible()) {
					SimpleTimer.addEvent("Show Browser",
							System.currentTimeMillis() + 700, showBrowersPerformer);
				}
				if (event.title.startsWith("err: ")) {
					fillWithRetry(event.title);
				}
			}
		});

		browser.addProgressListener(new ProgressListener() {
			public void changed(ProgressEvent event) {
				//int pct = event.total == 0 ? 0 : 100 * event.current / event.total;
				//System.out.println(pct + "%/" + event.current + "/" + event.total);
			}

			public void completed(ProgressEvent event) {
				/*
				 * The browser might have been disposed already by the time this method is called 
				 */
				if(true == browser.isDisposed()){
					return;
				}
				
				checkURLEventPerformer.perform(null);
				if (forceVisibleAfterLoad && !browser.isVisible()) {
					browser.setVisible(true);
				}

				browser.execute("try { if (azureusClientWelcome) { azureusClientWelcome('"
						+ Constants.AZID
						+ "',"
						+ "{ 'azv':'"
						+ org.gudy.azureus2.core3.util.Constants.AZUREUS_VERSION
						+ "', 'browser-id':'" + getID() + "' }" + ");} } catch (e) { }");

				if (org.gudy.azureus2.core3.util.Constants.isCVSVersion()
						|| System.getProperty("debug.https", null) != null) {
					if (browser.getUrl().indexOf("https") == 0) {
						browser.execute("try { o = document.getElementsByTagName('body'); if (o) o[0].style.borderTop = '2px dotted #3b3b3b'; } catch (e) {}");
					}
				}

				if (wiggleBrowser ) {
					Shell shell = browser.getShell();
					Point size = shell.getSize();
					size.x -= 1;
					size.y -= 1;
					shell.setSize(size);
					size.x += 1;
					size.y += 1;
					shell.setSize(size);
				}
			}
		});

		checkURLEvent = SimpleTimer.addPeriodicEvent("checkURL", 10000,
				checkURLEventPerformer);

		
		browser.addOpenWindowListener(new OpenWindowListener() {
			public void open(WindowEvent event) {
				event.required = true;

				final Browser subBrowser = new Browser(browser,SWT.NONE);
				subBrowser.addLocationListener(new LocationListener() {
					public void changed(LocationEvent arg0) {
						// TODO Auto-generated method stub
						
					}
					public void changing(LocationEvent event) {
						event.doit = false;
						System.out.println("SubBrowser URL : " + event.location);
						if(event.location.startsWith("http://") || event.location.startsWith("https://")) {
							Program.launch(event.location);
						}
						Utils.execSWTThreadLater(0, new AERunnable() {
							public void runSupport() {
								subBrowser.dispose();
							}
						});
					}
				});
				event.browser = subBrowser;
			}
		});
		
		browser.addLocationListener(new LocationListener() {
			private TimerEvent timerevent;

			public void changed(LocationEvent event) {
				//System.out.println("cd" + event.location);
				if (timerevent != null) {
					timerevent.cancel();
				}
				checkURLEventPerformer.perform(null);
				setPageLoading(false);
				if (widgetWaitIndicator != null && !widgetWaitIndicator.isDisposed()) {
					widgetWaitIndicator.setVisible(false);
				}
			}

			public void changing(LocationEvent event) {
				//System.out.println("cing " + event.location); 
				
				/*
				 * The browser might have been disposed already by the time this method is called 
				 */
				
				if ( browser.isDisposed()){
					return;
				}
				
				String event_location = event.location;
				
				if (event_location.startsWith("javascript")
						&& event_location.indexOf("back()") > 0) {
					if (browser.isBackEnabled()) {
						browser.back();
					} else if (lastValidURL != null) {
						fillWithRetry(event_location);
					}
					return;
				}
				boolean isWebURL = event_location.startsWith("http://")
						|| event_location.startsWith("https://");
				if (!isWebURL) {
					if (event_location.startsWith("res://") && lastValidURL != null) {
						fillWithRetry(event_location);
					}
					// we don't get a changed state on non URLs (mailto, javascript, etc)
					return;
				}

				boolean blocked = PlatformConfigMessenger.isURLBlocked(event_location);

				if (blocked) {
					event.doit = false;
					Utils.openMessageBox(Utils.findAnyShell(), SWT.OK, "URL blocked",
							"Tried to open " + event_location + " but it's blocked");
					browser.back();
				} else {
					setPageLoading(true);
					if(event.top) {
						lastValidURL = event_location;
						if (widgetWaitIndicator != null && !widgetWaitIndicator.isDisposed()) {
							widgetWaitIndicator.setVisible(true);
						}
	
						// Backup in case changed(..) is never called
						timerevent = SimpleTimer.addEvent("Hide Indicator",
								System.currentTimeMillis() + 20000, hideIndicatorPerformer);
					} else {
						boolean isTorrent 	= false;
						boolean isVuzeFile	= false;
						
						//Try to catch .torrent files
						if(event_location.endsWith(".torrent")) {
							isTorrent = true;
						} else {
							//If it's not obviously a web page
							
							boolean	can_rpc = PlatformConfigMessenger.urlCanRPC(event_location);
							
							boolean	test_for_torrent 	= !can_rpc && event_location.indexOf(".htm") == -1;
							boolean	test_for_vuze		= can_rpc &&  ( event_location.endsWith( ".xml" ) || event_location.endsWith( ".vuze" ));
							
							if ( test_for_torrent || test_for_vuze ){
								
								try {
									//See what the content type is
									URL url = new URL(event_location);
									URLConnection conn = url.openConnection();
									
										// we're only trying to get the content type so just use head
									
									((HttpURLConnection)conn).setRequestMethod("HEAD");
									
									String	referer_str = null;
									
									try{
										URL referer = new URL(((Browser)event.widget).getUrl());

										if ( referer != null ){
											
											referer_str = referer.toExternalForm();

										}
									}catch( Throwable e ){
									}
									
									UrlUtils.setBrowserHeaders( conn, referer_str );
									
									UrlUtils.connectWithTimeouts( conn, 1500, 5000 );
									
									String contentType = conn.getContentType();
									
									if ( contentType != null ){
										
										if ( test_for_torrent && contentType.indexOf("torrent") != -1 ) {
									
											isTorrent = true;
										}
										
										if ( test_for_vuze && contentType.indexOf("vuze") != -1 ) {
											
											isVuzeFile = true;
										}
									}
									
									String contentDisposition = conn.getHeaderField("Content-Disposition");
									
									if (contentDisposition != null ){
										
										if ( test_for_torrent && contentDisposition.indexOf(".torrent") != -1) {
									
											isTorrent = true;
										}
										
										if ( test_for_vuze && contentDisposition.indexOf(".vuze") != -1) {
											
											isVuzeFile = true;
										}
			
									}
									
								} catch(Exception e) {
									e.printStackTrace();
								}
								
								//System.out.println( "Test for t/v: " + event_location + " -> " + isTorrent + "/" + isVuzeFile );
							}
						}
						
						if ( isTorrent ){
							
							event.doit = false;
							
							try {
								String referer_str = null;

								try{
									referer_str = new URL(((Browser)event.widget).getUrl()).toExternalForm();

								}catch( Throwable e ){
								}
																
								Map headers = UrlUtils.getBrowserHeaders( referer_str );
											
								
								String cookies = (String) ((Browser)event.widget).getData("current-cookies");
								
								if (cookies != null ){
									
									headers.put("Cookie", cookies);
								}
								
								String	url = event_location;
								
								if ( torrentURLHandler != null ){
									
									try{
										torrentURLHandler.handleTorrentURL(url);
										
									}catch( Throwable e ){
										
										Debug.printStackTrace(e);
									}
								}
								
								AzureusCoreImpl.getSingleton().getPluginManager().getDefaultPluginInterface().getDownloadManager().addDownload(
										new URL(url), headers );
								
							}catch( Throwable e ){
								
								e.printStackTrace();
							}
						}else if ( isVuzeFile ){
							
							event.doit = false;
							
							try {
								String referer_str = null;

								try{
									referer_str = new URL(((Browser)event.widget).getUrl()).toExternalForm();

								}catch( Throwable e ){
								}
																
								Map headers = UrlUtils.getBrowserHeaders( referer_str );
																		
								String cookies = (String) ((Browser)event.widget).getData("current-cookies");
								
								if ( cookies != null ){
									
									headers.put("Cookie", cookies);
								}
								
								ResourceDownloader rd = StaticUtilities.getResourceDownloaderFactory().create( new URL( event_location ));
								
								VuzeFileHandler vfh = VuzeFileHandler.getSingleton();
								
								VuzeFile vf = vfh.loadVuzeFile( rd.download());
								
								if ( vf == null ){
									
									event.doit = true;
									
								}else{
									
									vfh.handleFiles( new VuzeFile[]{ vf }, 0 );
								}
							}catch( Throwable e ){
								
								e.printStackTrace();
							}
						}
					}
				}
			}
		});

		browser.setData(CONTEXT_KEY, this);
		browser.addDisposeListener(this);

		// enable right-click context menu only if system property is set
		final boolean enableMenu = System.getProperty(KEY_ENABLE_MENU, "0").equals(
				"1");
		browser.addListener(SWT.MenuDetect, new Listener() {
			// @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
			public void handleEvent(Event event) {
				event.doit = enableMenu;
			}
		});

		messageDispatcherSWT.registerBrowser(browser);
		this.display = browser.getDisplay();
	}

	/**
	 * @param b
	 *
	 * @since 3.1.1.1
	 */
	protected void setPageLoading(boolean b) {
		if (pageLoading == b) {
			return;
		}
		pageLoading = b;
		Object[] listeners = loadingListeners.toArray();
		for (int i = 0; i < listeners.length; i++) {
			loadingListener l = (loadingListener) listeners[i];
			l.browserLoadingChanged(b);
		}
	}

	public void 
	setTorrentURLHandler(
		torrentURLHandler handler) 
	{
		torrentURLHandler = handler;
	}
	
	public void fillWithRetry(String s) {
		browser.setText("<html><body style='overflow:auto; font-family: verdana; font-size: 10pt' bgcolor=#1b1b1b text=#a0a0a0>"
				+ "<br>Sorry, there was a problem loading this page.<br> "
				+ "Please check if your internet connection is working and click <a href='"
				+ lastValidURL
				+ "' style=\"color: rgb(100, 155, 255); \">retry</a> to continue."
				+ "<div style='word-wrap: break-word'><font size=1 color=#2e2e2e>"
				+ s
				+ "</font></div>" + "</body></html>");
	}

	private void 
	deregisterBrowser() 
	{
		if (browser == null) {
			throw new IllegalStateException("Context " + getID()
					+ " doesn't have a registered browser");
		}

		System.out.println( "Unregistered browser context: id=" + getID());
	
		browser.setData(CONTEXT_KEY, null);
		browser.removeDisposeListener(this);
		messageDispatcherSWT.deregisterBrowser(browser);
		browser = null;

		if (checkURLEvent != null && !checkURLEvent.isCancelled()) {
			checkURLEvent.cancel();
			checkURLEvent = null;
		}
	}

	/**
	 * Accesses the context associated with the given browser.
	 * 
	 * @param browser holds the context in its application data map
	 * @return the browser's context or <code>null</code> if there is none
	 */
	public static BrowserContext getContext(Browser browser) {
		Object data = browser.getData(CONTEXT_KEY);
		if (data != null && !(data instanceof BrowserContext)) {
			Debug.out("Data in Browser with key " + CONTEXT_KEY
					+ " is not a BrowserContext");
			return null;
		}

		return (BrowserContext) data;
	}

	public void addMessageListener(BrowserMessageListener listener) {
		messageDispatcherSWT.addListener(listener);
	}

	public Object getBrowserData(String key) {
		return browser.getData(key);
	}

	public void setBrowserData(String key, Object value) {
		browser.setData(key, value);
	}

	public boolean sendBrowserMessage(String key, String op) {
		return sendBrowserMessage(key, op, (Map) null);
	}

	public boolean sendBrowserMessage(String key, String op, Map params) {
		StringBuffer msg = new StringBuffer();
		msg.append("az.msg.dispatch('").append(key).append("', '").append(op).append(
				"'");
		if (params != null) {
			msg.append(", ").append(JSONUtils.encodeToJSON(params));
		}
		msg.append(")");

		return executeInBrowser(msg.toString());
	}

	public boolean sendBrowserMessage(String key, String op, Collection params) {
		StringBuffer msg = new StringBuffer();
		msg.append("az.msg.dispatch('").append(key).append("', '").append(op).append(
				"'");
		if (params != null) {
			msg.append(", ").append(JSONUtils.encodeToJSON(params));
		}
		msg.append(")");

		return executeInBrowser(msg.toString());
	}

	protected boolean maySend(String key, String op, Map params) {
		return !pageLoading;
	}

	public boolean executeInBrowser(final String javascript) {
		if (!mayExecute(javascript)) {
			debug("BLOCKED: browser.execute( " + getShortJavascript(javascript)
					+ " )");
			return false;
		}
		if (display == null || display.isDisposed()) {
			debug("CANNOT: browser.execute( " + getShortJavascript(javascript) + " )");
			return false;
		}

		// swallow errors silently
		final String reallyExecute = "try { " + javascript + " } catch ( e ) { }";
		Utils.execSWTThread(new AERunnable() {
			public void runSupport() {
				if (browser == null || browser.isDisposed()) {
					debug("CANNOT: browser.execute( " + getShortJavascript(javascript)
							+ " )");
				} else if (!browser.execute(reallyExecute)) {
					debug("FAILED: browser.execute( " + getShortJavascript(javascript)
							+ " )");
				} else {
					debug("SUCCESS: browser.execute( " + getShortJavascript(javascript)
							+ " )");
				}
			}
		});

		return true;
	}

	protected boolean mayExecute(String javascript) {
		return !pageLoading;
	}

	public void widgetDisposed(DisposeEvent event) {
		if (event.widget == browser) {
			deregisterBrowser();
		}
	}

	private String getShortJavascript(String javascript) {
		if (javascript.length() < (256 + 3 + 256)) {
			return javascript;
		}
		StringBuffer result = new StringBuffer();
		result.append(javascript.substring(0, 256));
		result.append("...");
		result.append(javascript.substring(javascript.length() - 256));
		return result.toString();
	}

	public void setWiggleBrowser(boolean wiggleBrowser) {
		this.wiggleBrowser = wiggleBrowser;
	}

	public boolean isPageLoading() {
		return pageLoading;
	}

	
	public void addListener(loadingListener l) {
		if (loadingListeners == Collections.EMPTY_LIST) {
			loadingListeners = new ArrayList(1);
		}
		loadingListeners.add(l);
	}
	
	public static interface loadingListener {
		public void browserLoadingChanged(boolean loading);
	}
}