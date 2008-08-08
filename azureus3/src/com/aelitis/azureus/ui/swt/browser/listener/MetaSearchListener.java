/*
 * Created on May 6, 2008
 * Created by Paul Gardner
 * 
 * Copyright 2008 Vuze, Inc.  All rights reserved.
 * 
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */

package com.aelitis.azureus.ui.swt.browser.listener;

import java.io.File;
import java.net.URL;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.mainwindow.TorrentOpener;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.aelitis.azureus.core.impl.AzureusCoreImpl;
import com.aelitis.azureus.core.messenger.browser.BrowserMessage;
import com.aelitis.azureus.core.messenger.browser.listeners.AbstractBrowserMessageListener;
import com.aelitis.azureus.core.metasearch.*;
import com.aelitis.azureus.core.subs.Subscription;
import com.aelitis.azureus.core.subs.SubscriptionDownloadListener;
import com.aelitis.azureus.core.subs.SubscriptionException;
import com.aelitis.azureus.core.subs.SubscriptionHistory;
import com.aelitis.azureus.core.subs.SubscriptionManagerFactory;
import com.aelitis.azureus.core.subs.SubscriptionResult;
import com.aelitis.azureus.core.vuzefile.VuzeFile;
import com.aelitis.azureus.core.vuzefile.VuzeFileComponent;
import com.aelitis.azureus.core.vuzefile.VuzeFileHandler;
import com.aelitis.azureus.ui.swt.views.skin.SearchResultsTabArea;
import com.aelitis.azureus.util.JSONUtils;

public class MetaSearchListener extends AbstractBrowserMessageListener {
	
	public static final String LISTENER_ID = "metasearch";

	public static final String OP_SEARCH				= "search";
		
	public static final String OP_GET_ENGINES 			= "get-engines";
	public static final String OP_GET_ALL_ENGINES 		= "get-all-engines";

	public static final String OP_CHANGE_ENGINE_SELECTION 	= "change-engine-selection";
	
	public static final String OP_SET_SELECTED_ENGINES 	= "set-selected-engines";

	public static final String OP_GET_AUTO_MODE		 	= "get-auto-mode";
	
	public static final String OP_SAVE_TEMPLATE		 	= "save-template";
	public static final String OP_LOAD_TEMPLATE		 	= "load-template";
	public static final String OP_DELETE_TEMPLATE		= "delete-template";
	public static final String OP_TEST_TEMPLATE			= "test-template";
	
	public static final String OP_EXPORT_TEMPLATE		= "export-template";
	public static final String OP_IMPORT_TEMPLATE		= "import-template";
	
	public static final String OP_OPEN_SEARCH_RESULTS	= "open-search-results";
	public static final String OP_CLOSE_SEARCH_RESULTS	= "close-search-results";
	
	public static final String OP_LOAD_TORRENT			= "load-torrent";
	public static final String OP_HAS_LOAD_TORRENT		= "has-load-torrent";
	
	public static final String OP_CREATE_SUBSCRIPTION   		= "create-subscription";
	public static final String OP_READ_SUBSCRIPTION   			= "read-subscription";
	public static final String OP_UPDATE_SUBSCRIPTION   		= "update-subscription";
	public static final String OP_READ_SUBSCRIPTION_RESULTS   	= "read-subscription-results";
	public static final String OP_DELETE_SUBSCRIPTION_RESULTS   = "delete-subscription-results";
	public static final String OP_MARK_SUBSCRIPTION_RESULTS	   	= "mark-subscription-results";
	public static final String OP_DOWNLOAD_SUBSCRIPTION   		= "download-subscription";

	private final SearchResultsTabArea searchResultsArea;
	
	
	public MetaSearchListener(SearchResultsTabArea searchResultsArea) {
		super(LISTENER_ID);
		this.searchResultsArea = searchResultsArea;
	}
	
	public void handleMessage(BrowserMessage message) {
		
		String opid = message.getOperationId();

		MetaSearchManager metaSearchManager = MetaSearchManagerFactory.getSingleton();
		
		metaSearchManager.log( "BrowserListener: received " + message );
		
		if (OP_SEARCH.equals(opid)) {
			Map decodedMap = message.getDecodedMap();
			
			String searchText = (String) decodedMap.get("searchText");
			
			String headers = (String)decodedMap.get( "headers" );
			
			final Long	sid = (Long)decodedMap.get( "sid" );

			Boolean	mature = (Boolean)decodedMap.get( "mature" );
			
			ResultListener listener = new ResultListener() {
				
				public void contentReceived(Engine engine, String content) {
					// TODO Auto-generated method stub

				}
				
				public void matchFound(Engine engine, String[] fields) {
					// TODO Auto-generated method stub
					
				}
				public void engineFailed(Engine engine, Throwable e) {
					
					Map params = getParams( engine );
					
					params.put( "error", Debug.getNestedExceptionMessage( e ));
					
					sendBrowserMessage("metasearch", "engineFailed", params );
				}
				
				public void resultsComplete(Engine engine) {
				
					sendBrowserMessage("metasearch", "engineCompleted", getParams( engine ));
				}
				
				public void resultsReceived(Engine engine,Result[] results) {
					Map params = getParams(engine);
					List resultsList = new ArrayList(results.length);
					for(int i = 0 ; i < results.length ; i++) {
						Result result = results[i];
						resultsList.add(result.toJSONMap());
					}
					params.put("results", resultsList);
					sendBrowserMessage("metasearch", "resultsReceived",params);
				}
				
				protected Map
				getParams(
					Engine	engine )
				{
					Map params = new HashMap();
					params.put("id", new Long(engine.getId()));
					params.put("name", engine.getName());
					params.put("favicon", engine.getIcon());
					params.put("dl_link_css", engine.getDownloadLinkCSS());
					
					if ( sid != null ){
						params.put( "sid", sid );
					}
					return( params );
				}
			};
			
			List	sps = new ArrayList();
						
			sps.add( new SearchParameter( "s", searchText ));
			
			if ( mature != null ){
				
				sps.add( new SearchParameter( "m", mature.toString()));
			}
			
			SearchParameter[] parameters = (SearchParameter[])sps.toArray(new SearchParameter[ sps.size()] );
			
			metaSearchManager.getMetaSearch().search( listener, parameters, headers );

		} else if(OP_GET_ENGINES.equals(opid)) {

			Engine[] engines = metaSearchManager.getMetaSearch().getEngines( true, true );
			List params = new ArrayList();
			for(int i = 0 ; i < engines.length ; i++) {
				Engine engine = engines[i];
				
				if ( !engine.isActive() || engine.getSource() == Engine.ENGINE_SOURCE_UNKNOWN ){
					continue;
				}
				Map engineMap = new HashMap();
				engineMap.put("id", new Long(engine.getId()));
				engineMap.put("name", engine.getName());
				engineMap.put("favicon", engine.getIcon());
				engineMap.put("dl_link_css", engine.getDownloadLinkCSS());
				engineMap.put("selected", Engine.SEL_STATE_STRINGS[ engine.getSelectionState()]);
				engineMap.put("type", Engine.ENGINE_SOURCE_STRS[ engine.getSource()]);
				params.add(engineMap);
			}
			sendBrowserMessage("metasearch", "enginesUsed",params);
			
		} else if(OP_GET_ALL_ENGINES.equals(opid)) {

			Engine[] engines = metaSearchManager.getMetaSearch().getEngines( false, true );
			List params = new ArrayList();
			for(int i = 0 ; i < engines.length ; i++) {
				Engine engine = engines[i];
				
				if ( engine.getSource() == Engine.ENGINE_SOURCE_UNKNOWN ){
					continue;
				}
				
				Map engineMap = new HashMap();
				engineMap.put("id", new Long(engine.getId()));
				engineMap.put("name", engine.getName());
				engineMap.put("favicon", engine.getIcon());
				engineMap.put("dl_link_css", engine.getDownloadLinkCSS());
				engineMap.put("selected", Engine.SEL_STATE_STRINGS[ engine.getSelectionState()]);
				engineMap.put("type", Engine.ENGINE_SOURCE_STRS[ engine.getSource()]);
				params.add(engineMap);
			}
			sendBrowserMessage("metasearch", "engineList",params);
			
		} else if( OP_SET_SELECTED_ENGINES.equals(opid)){
			
			Map decodedMap = message.getDecodedMap();

			List template_ids = (List)decodedMap.get( "template_ids" );
			
			long[] ids = new long[template_ids.size()];
			
			for (int i=0;i<ids.length;i++ ){
				
				Map m = (Map)template_ids.get(i);
				
				ids[i] = ((Long)m.get("id")).longValue();
			}
			
			boolean	auto = ((Boolean)decodedMap.get( "auto" )).booleanValue();
			
			try{
				metaSearchManager.setSelectedEngines( ids, auto );
				
				Map params = new HashMap();
				sendBrowserMessage("metasearch", "setSelectedCompleted",params);

			}catch( Throwable e ){
				
				Map params = new HashMap();
				params.put("error",Debug.getNestedExceptionMessage(e));

				sendBrowserMessage("metasearch", "setSelectedFailed",params);
			}
		} else if( OP_CHANGE_ENGINE_SELECTION.equals(opid)){
			
			Map decodedMap = message.getDecodedMap();

			MetaSearch ms = metaSearchManager.getMetaSearch();
			
			Engine[] engines = ms.getEngines( false, true );
			
			Set selected = new HashSet();
			
			for (int i=0;i<engines.length;i++){
				
				Engine e = engines[i];
				
				if ( e.getSelectionState() == Engine.SEL_STATE_MANUAL_SELECTED ){
					
					selected.add( new Long( e.getId()));
				}
			}
			
			List l_engines = (List)decodedMap.get( "engines" );
			
			for (int i=0;i<l_engines.size();i++){
				
				Map	map = (Map)l_engines.get(i);
				
				long id = ((Long)map.get("id")).longValue();

				String str = (String)map.get( "selected");
				
				if ( str.equalsIgnoreCase( Engine.SEL_STATE_STRINGS[Engine.SEL_STATE_MANUAL_SELECTED])){
					
					selected.add( new Long( id ));
					
				}else if ( str.equalsIgnoreCase( Engine.SEL_STATE_STRINGS[Engine.SEL_STATE_DESELECTED])){
					
					selected.remove( new Long( id ));
				}
			}
			
			long[] ids = new long[selected.size()];
			
			Iterator it = selected.iterator();
			
			int	pos = 0;
			
			while( it.hasNext()){
				
				long	 id = ((Long)it.next()).longValue();
				
				ids[pos++] = id;
			}
						
			try{
				metaSearchManager.setSelectedEngines( ids, metaSearchManager.isAutoMode());
				
				Map params = new HashMap();
				sendBrowserMessage("metasearch", "changeEngineSelectionCompleted",params);

			}catch( Throwable e ){
				
				Map params = new HashMap();
				params.put("error",Debug.getNestedExceptionMessage(e));

				sendBrowserMessage("metasearch", "changeEngineSelectionFailed",params);
			}	
		} else if(OP_GET_AUTO_MODE.equals(opid)) {
						
			boolean mode = metaSearchManager.isAutoMode();
			
			Map params = new HashMap();
			params.put( "auto", new Boolean( mode ));

			sendBrowserMessage("metasearch", "getAutoModeResult",params);
			
		} else if( OP_SAVE_TEMPLATE.equals(opid)){
			
			Map decodedMap = message.getDecodedMap();

			String	type_str = (String)decodedMap.get( "type" );
			
			String	name = (String)decodedMap.get( "name" );
			
			Long	l_id	= (Long)decodedMap.get( "id" );
			
			long	id = l_id == null?-1:l_id.longValue();
			
			String	json = (String)decodedMap.get( "value" );
			
			try{
				Engine engine = 
					metaSearchManager.addEngine( 
							id, 
							type_str.equals( "json" )?Engine.ENGINE_TYPE_JSON:Engine.ENGINE_TYPE_REGEX, 
							name, 
							json );
				
				Map params = new HashMap();
				params.put( "id", new Long( engine.getId() ));
	
				sendBrowserMessage( "metasearch", "saveTemplateCompleted", params );
				
			}catch( Throwable e ){
				
				Map params = new HashMap();
				params.put( "id", new Long( id ));
				params.put("error",Debug.getNestedExceptionMessage(e));

				sendBrowserMessage("metasearch", "saveTemplateFailed",params);
			}
		} else if( OP_LOAD_TEMPLATE.equals(opid)){
			
			Map decodedMap = message.getDecodedMap();

			long	id	= ((Long)decodedMap.get( "id" )).longValue();
			
			Engine engine = metaSearchManager.getMetaSearch().getEngine( id );
		
			if ( engine == null ){
			
				Map params = new HashMap();
				params.put( "id", new Long( id ));
				params.put("error","Template not found");

				sendBrowserMessage("metasearch", "loadTemplateFailed",params);
				
			}else{
				
				try{
					Map params = new HashMap();
					params.put("id", new Long(engine.getId()));
					params.put("name", engine.getName());
					params.put("type", Engine.ENGINE_TYPE_STRS[ engine.getType()]);
					params.put("value", JSONObject.escape( engine.exportToJSONString()));
				
					sendBrowserMessage( "metasearch", "loadTemplateCompleted", params );
					
				}catch( Throwable e ){
					
					Map params = new HashMap();
					params.put( "id", new Long( id ));
					params.put("error",Debug.getNestedExceptionMessage(e));

					sendBrowserMessage("metasearch", "loadTemplateFailed",params);
				}
			}		
		} else if( OP_DELETE_TEMPLATE.equals(opid)){
			
			Map decodedMap = message.getDecodedMap();

			long	id	= ((Long)decodedMap.get( "id" )).longValue();
			
			Engine engine = metaSearchManager.getMetaSearch().getEngine( id );
			
			if ( engine == null ){
			
				Map params = new HashMap();
				params.put( "id", new Long( id ));
				params.put( "error", "Template not found" );

				sendBrowserMessage("metasearch", "deleteTemplateFailed",params);
			
			}else if ( engine.getSource() != Engine.ENGINE_SOURCE_LOCAL ){
			
				Map params = new HashMap();
				params.put( "id", new Long( id ));
				params.put( "error", "Template is not local" );

				sendBrowserMessage("metasearch", "deleteTemplateFailed",params);
				
			}else{
				
				engine.delete();
				
				Map params = new HashMap();
				params.put( "id", new Long( id ));
				sendBrowserMessage( "metasearch", "deleteTemplateCompleted", params );
			}
		} else if( OP_TEST_TEMPLATE.equals(opid)){
			
			Map decodedMap = message.getDecodedMap();

			final long	id		= ((Long)decodedMap.get( "id" )).longValue();
			long	match_count	= ((Long)decodedMap.get( "max_matches" )).longValue();
			
			String searchText 	= (String) decodedMap.get("searchText");
			String headers		= (String) decodedMap.get("headers");
			
			final Long	sid = (Long)decodedMap.get( "sid" );

			Engine engine = metaSearchManager.getMetaSearch().getEngine( id );
			
			if ( engine == null ){
			
				Map params = new HashMap();
				params.put( "id", new Long( id ));
				params.put( "error", "Template not found" );
				if ( sid != null )params.put( "sid", sid );

				sendBrowserMessage("metasearch", "testTemplateFailed",params);
			
			}else{
				
				SearchParameter parameter = new SearchParameter("s",searchText);
				SearchParameter[] parameters = new SearchParameter[] {parameter};

				engine.search(
						parameters, 
						(int)match_count,
						headers,
						new ResultListener()
						{
							private String	content;
							private List	matches = new ArrayList();
							
							public void 
							contentReceived(
								Engine 		engine, 
								String 		_content )
							{
								content = _content;
							}
							
							public void 
							matchFound(
								Engine 		engine,
								String[] 	fields) 
							{
								matches.add( fields );
							}
							
							public void 
							resultsReceived(
								Engine 		engine,
								Result[] 	results )
							{								
							}
							
							public void 
							resultsComplete(
								Engine 		engine )
							{
								Map params = new HashMap();
								params.put( "id", new Long( id ));
								if ( sid != null )params.put( "sid", sid );
								params.put( "content", JSONObject.escape( content ));

								JSONArray	l_matches = new JSONArray();
								
								params.put( "matches", l_matches );
								
								for (int i=0;i<matches.size();i++){
									
									String[]	match = (String[])matches.get(i);
									
									JSONArray	l_match = new JSONArray();
									
									l_matches.add( l_match );
									
									for (int j=0;j<match.length;j++){
										
										l_match.add( match[j] );
									}
								}
															
								sendBrowserMessage( "metasearch", "testTemplateCompleted", params );
			
							}
							
							public void 
							engineFailed(
								Engine 		engine,
								Throwable 	e )
							{
								Map params = new HashMap();
								params.put( "id", new Long( id ));
								params.put( "error", Debug.getNestedExceptionMessage( e ));
								if ( sid != null )params.put( "sid", sid );

								sendBrowserMessage("metasearch", "testTemplateFailed",params);
							}
						});

			}	
		} else if ( OP_EXPORT_TEMPLATE.equals(opid)){
			
			Map decodedMap = message.getDecodedMap();

			final long	id		= ((Long)decodedMap.get( "id" )).longValue();
			
			final Engine engine = metaSearchManager.getMetaSearch().getEngine( id );
			
			if ( engine == null ){
				
				Map params = new HashMap();
				params.put( "error", "template '" + id + "' not found" );

				sendBrowserMessage("metasearch", "exportTemplateFailed",params);
				
			}else{
				final Shell shell = Utils.findAnyShell();
				
				shell.getDisplay().asyncExec(
					new AERunnable() 
					{
						public void 
						runSupport()
						{
							FileDialog dialog = 
								new FileDialog( shell, SWT.SYSTEM_MODAL | SWT.SAVE );
							
							dialog.setFilterPath( TorrentOpener.getFilterPathData() );
													
							dialog.setText(MessageText.getString("metasearch.export.select.template.file"));
							
							dialog.setFilterExtensions(new String[] {
									"*.vuze",
									"*.vuz",
									Constants.FILE_WILDCARD
								});
							dialog.setFilterNames(new String[] {
									"*.vuze",
									"*.vuz",
									Constants.FILE_WILDCARD
								});
							
							String path = TorrentOpener.setFilterPathData( dialog.open());
		
							if ( path != null ){
								
								String lc = path.toLowerCase();
								
								if ( !lc.endsWith( ".vuze" ) && !lc.endsWith( ".vuz" )){
									
									path += ".vuze";
								}
								
								try{
									engine.exportToVuzeFile( new File( path ));
									
									Map params = new HashMap();
									params.put( "id", new Long( id ));
									sendBrowserMessage( "metasearch", "exportTemplateCompleted", params );

								}catch( Throwable e ){
									
									Map params = new HashMap();
									params.put( "id", new Long( id ));
									params.put( "error", "save failed: " + Debug.getNestedExceptionMessage(e));

									sendBrowserMessage("metasearch", "exportTemplateFailed",params);
								}
							}else{
								
								Map params = new HashMap();
								params.put( "id", new Long( id ));
								params.put( "error", "operation cancelled" );

								sendBrowserMessage("metasearch", "exportTemplateFailed",params);
							}
						}
					});
			}
		}else if ( OP_IMPORT_TEMPLATE.equals(opid)){
						
			final Shell shell = Utils.findAnyShell();
			
			shell.getDisplay().asyncExec(
				new AERunnable() 
				{
					public void 
					runSupport()
					{
						FileDialog dialog = 
							new FileDialog( shell, SWT.SYSTEM_MODAL | SWT.OPEN );
						
						dialog.setFilterPath( TorrentOpener.getFilterPathData() );
												
						dialog.setText(MessageText.getString("metasearch.import.select.template.file"));
						
						dialog.setFilterExtensions(new String[] {
								"*.vuze",
								"*.vuz",
								Constants.FILE_WILDCARD
							});
						dialog.setFilterNames(new String[] {
								"*.vuze",
								"*.vuz",
								Constants.FILE_WILDCARD
							});
						
						String path = TorrentOpener.setFilterPathData( dialog.open());
	
						if ( path != null ){
							
							VuzeFileHandler vfh = VuzeFileHandler.getSingleton();
							
							VuzeFile vf = vfh.loadAndHandleVuzeFile( path, VuzeFileComponent.COMP_TYPE_METASEARCH_TEMPLATE );
							
							if ( vf == null ){
								
								Map params = new HashMap();
								params.put( "error", "invalid .vuze file" );

								sendBrowserMessage("metasearch", "importTemplateFailed",params);
								
							}else{
								
								VuzeFileComponent[] comps = vf.getComponents();
								
								for (int i=0;i<comps.length;i++){
									
									VuzeFileComponent comp = comps[i];
									
									if ( comp.getType() == VuzeFileComponent.COMP_TYPE_METASEARCH_TEMPLATE ){
										
										Engine engine = (Engine)comp.getData( Engine.VUZE_FILE_COMPONENT_ENGINE_KEY );
										
										if ( engine != null ){
											
											Map params = new HashMap();
											params.put( "id", new Long( engine.getId()));
											sendBrowserMessage( "metasearch", "importTemplateCompleted", params );

											return;
										}
									}
								}
								
								Map params = new HashMap();
								params.put( "error", "invalid search template file" );

								sendBrowserMessage("metasearch", "importTemplateFailed",params);
							}
						}else{
							
							Map params = new HashMap();
								// don't change this message as the UI uses it!
							params.put( "error", "operation cancelled" );

							sendBrowserMessage("metasearch", "importTemplateFailed",params);
						}
					}
				});
		}else if ( OP_OPEN_SEARCH_RESULTS.equals(opid)){
			
			Map decodedMap = message.isParamObject() ? message.getDecodedMap()
					: new HashMap();
			searchResultsArea.openSearchResults(decodedMap);
		}else if ( OP_CLOSE_SEARCH_RESULTS.equals(opid)){
			
			Map decodedMap = message.isParamObject() ? message.getDecodedMap()
					: new HashMap();
			searchResultsArea.closeSearchResults(decodedMap);
		}else if(OP_LOAD_TORRENT.equals(opid)) {
			Map decodedMap = message.isParamObject() ? message.getDecodedMap()
					: new HashMap();
			final String id = (String) decodedMap.get("id");
			final String torrentUrl	= (String) decodedMap.get( "torrent_url" );
			final String referer_str	= (String) decodedMap.get( "referer_url" );
			
			try {
			
				Map headers = UrlUtils.getBrowserHeaders( referer_str );
				
				AzureusCoreImpl.getSingleton().getPluginManager().getDefaultPluginInterface().getDownloadManager().addDownload(
						new URL(torrentUrl), headers );
				
				Map params = new HashMap();
				params.put("id",id);
				params.put("torrent_url",torrentUrl);
				params.put("referer_url",referer_str);
				sendBrowserMessage("metasearch", "loadTorrentCompleted",params);
				
			} catch(Exception e) {
				Map params = new HashMap();
				params.put("id",id);
				params.put("torrent_url",torrentUrl);
				params.put("referer_url",referer_str);
				params.put( "error", e.getMessage() );
				sendBrowserMessage("metasearch", "loadTorrentFailed",params);
			}
				
		}else if(OP_HAS_LOAD_TORRENT.equals(opid)) {
			
			Map params = new HashMap();
			params.put("result","1");
			sendBrowserMessage("metasearch", "hasLoadTorrent",params);
			
		}else if(OP_CREATE_SUBSCRIPTION.equals(opid)) {
			
			Map decodedMap = message.isParamObject() ? message.getDecodedMap():new HashMap();
			
			Long	 tid = (Long) decodedMap.get("tid");

			String 		name 		= (String) decodedMap.get( "name" );
			Boolean 	isPublic	= (Boolean) decodedMap.get( "is_public" );
			Map			options		= (Map)decodedMap.get( "options" );
			
			Boolean 	isEnabled		= (Boolean)options.get( "is_enabled" );
			Boolean 	autoDownload	= (Boolean)options.get( "auto_dl" );

			Map result = new HashMap();

			if ( tid != null )result.put( "tid", tid );

			try{
				JSONObject	payload = new JSONObject();
				
					// change this you need to change update too below
				
				payload.put( "engine_id", decodedMap.get( "engine_id" ));
				payload.put( "search_term", decodedMap.get( "search_term" ));
				payload.put( "filters", decodedMap.get( "filters" ));
				payload.put( "schedule", decodedMap.get( "schedule" ));
				payload.put( "options", decodedMap.get( "options" ));
				
				Subscription subs = SubscriptionManagerFactory.getSingleton().create(name, isPublic.booleanValue(), payload.toString());
				
				subs.getHistory().setDetails(
					isEnabled==null?true:isEnabled.booleanValue(),
					autoDownload==null?false:autoDownload.booleanValue());
								
				result.put( "id", subs.getID());
				
				sendBrowserMessage( "metasearch", "createSubscriptionCompleted", result );

			} catch( Throwable e ){
				
				result.put( "error", "create failed: " + Debug.getNestedExceptionMessage(e));

				sendBrowserMessage( "metasearch", "createSubscriptionFailed", result );
			}
		}else if( OP_READ_SUBSCRIPTION.equals(opid)){
			
			Map decodedMap = message.isParamObject() ? message.getDecodedMap():new HashMap();
			
			final Long	 tid = (Long) decodedMap.get("tid");

			final String sid = (String) decodedMap.get("id");
			
			Map result = new HashMap();

			if ( tid != null )result.put( "tid", tid );

			try{
				Subscription subs = SubscriptionManagerFactory.getSingleton().getSubscriptionByID( sid );
				
				if ( subs == null ){
					
					result.put( "error", "Subscription not found" );

					sendBrowserMessage("metasearch", "readSubscriptionFailed",result);
					
				}else{
										
					result.put( "id", subs.getID());
					result.put( "name", subs.getName());
					result.put( "is_public", new Boolean( subs.isPublic()));
					result.put( "is_author", new Boolean( subs.isMine()));
					
					SubscriptionHistory history = subs.getHistory();
					
					Map	options = new HashMap();

					result.put( "options", options );

					options.put( "is_enabled", new Boolean( history.isEnabled()));
					options.put( "auto_dl", new Boolean( history.isAutoDownload()));
					
					Map	info = new HashMap();

					result.put( "info", info );
					
					info.put( "last_scan", new Long( history.getLastScanTime()));
					info.put( "last_new", new Long( history.getLastNewResultTime()));
					info.put( "num_unread", new Long( history.getNumUnread()));
					info.put( "num_read", new Long( history.getNumRead()));
					
					String json = subs.getJSON();
					
					Map map = JSONUtils.decodeJSON( json );
					
					result.put( "engine_id", map.get( "engine_id" ));
					result.put( "search_term", map.get( "search_term" ));
					result.put( "filters", map.get( "filters" ));
					result.put( "schedule", map.get( "schedule" ));
					
					sendBrowserMessage( "metasearch", "readSubscriptionCompleted", result );
				}
			} catch( Throwable e ){
				
				result.put( "error", "read failed: " + Debug.getNestedExceptionMessage(e));

				sendBrowserMessage("metasearch", "readSubscriptionFailed",result);
			}
		}else if (OP_UPDATE_SUBSCRIPTION.equals(opid)) {
			
			Map decodedMap = message.isParamObject() ? message.getDecodedMap():new HashMap();
			
			final Long	 tid = (Long) decodedMap.get("tid");
			
			final String 	name 		= (String)decodedMap.get("name");
			final Boolean 	isPublic	= (Boolean)decodedMap.get( "is_public" );
			final String 	sid 		= (String)decodedMap.get("id");
			
			Map			options		= (Map)decodedMap.get( "options" );
			
			Boolean 	isEnabled		= (Boolean)options.get( "is_enabled" );
			Boolean 	autoDownload	= (Boolean)options.get( "auto_dl" );

			Map result = new HashMap();

			if ( tid != null )result.put( "tid", tid );

			try{
				Subscription subs = SubscriptionManagerFactory.getSingleton().getSubscriptionByID( sid );
				
				if ( subs == null ){
					
					result.put( "error", "Subscription not found" );

					sendBrowserMessage("metasearch", "updateSubscriptionFailed",result);
					
				}else{
					
					JSONObject	payload = new JSONObject();
					
						// change this you need to change create too above
				
					payload.put( "engine_id", decodedMap.get( "engine_id" ));
					payload.put( "search_term", decodedMap.get( "search_term" ));
					payload.put( "filters", decodedMap.get( "filters" ));
					payload.put( "schedule", decodedMap.get( "schedule" ));
					payload.put( "options", decodedMap.get( "options" ));
				
					subs.setDetails( name, isPublic.booleanValue(), payload.toString());
					
					subs.getHistory().setDetails(
							isEnabled==null?true:isEnabled.booleanValue(),
							autoDownload==null?false:autoDownload.booleanValue());

					result.put( "id", subs.getID());
					
					sendBrowserMessage( "metasearch", "updateSubscriptionCompleted", result );
				}
			} catch( Throwable e ){
				
				result.put( "error", "update failed: " + Debug.getNestedExceptionMessage(e));

				sendBrowserMessage("metasearch", "updateSubscriptionCompleted",result);
			}
		}else if(OP_READ_SUBSCRIPTION_RESULTS.equals(opid)) {
			
			Map decodedMap = message.isParamObject() ? message.getDecodedMap():new HashMap();
			
			final Long	 tid = (Long) decodedMap.get("tid");
			
			final String sid = (String) decodedMap.get("id");
			
			Map result = new HashMap();

			if ( tid != null )result.put( "tid", tid );

			try{
				Subscription subs = SubscriptionManagerFactory.getSingleton().getSubscriptionByID( sid );
				
				if ( subs == null ){
								
					result.put( "error", "Subscription not found" );

					sendBrowserMessage("metasearch", "readSubscriptionResultsFailed",result);
					
				}else{
										
					result.put( "id", subs.getID());
					
					encodeResults( subs, result );
					
					sendBrowserMessage( "metasearch", "readSubscriptionResultsCompleted", result );
				}
			} catch( Throwable e ){
				
				result.put( "error", "read failed: " + Debug.getNestedExceptionMessage(e));

				sendBrowserMessage("metasearch", "readSubscriptionFailed",result);
			}
		}else if( OP_DELETE_SUBSCRIPTION_RESULTS.equals(opid)){
			
			Map decodedMap = message.isParamObject() ? message.getDecodedMap():new HashMap();
						
			String sid = (String)decodedMap.get("id");
			
			List	rids	= (List)decodedMap.get( "rids" );
			
			try{
				Subscription subs = SubscriptionManagerFactory.getSingleton().getSubscriptionByID( sid );
				
				if ( subs == null ){
					
					Map params = new HashMap();
					
					params.put( "error", "Subscription not found" );

					sendBrowserMessage("metasearch", "deleteSubscriptionResultsFailed",params);
					
				}else{
					
					String[]	rids_a = (String[])rids.toArray( new String[rids.size()]);
					
					subs.getHistory().deleteResults( rids_a );
					
					Map result = new HashMap();
					
					sendBrowserMessage( "metasearch", "deleteSubscriptionResultsCompleted", result );
				}
			} catch( Throwable e ){
				
				Map params = new HashMap();

				params.put( "error", "delete failed: " + Debug.getNestedExceptionMessage(e));

				sendBrowserMessage("metasearch", "deleteSubscriptionResultsFailed",params);
			}
		}else if( OP_MARK_SUBSCRIPTION_RESULTS.equals(opid)){
			
			Map decodedMap = message.isParamObject() ? message.getDecodedMap():new HashMap();
						
			String sid = (String)decodedMap.get("id");
			
			List	rids	= (List)decodedMap.get( "rids" );
			List	reads	= (List)decodedMap.get( "reads" );
			
			Map result = new HashMap();		

			try{
				Subscription subs = SubscriptionManagerFactory.getSingleton().getSubscriptionByID( sid );
				
				if ( subs == null ){
					
					result.put( "error", "Subscription not found" );

					sendBrowserMessage("metasearch", "markSubscriptionResultsFailed",result);
					
				}else{
					
					String[]	rids_a = (String[])rids.toArray( new String[rids.size()]);
					
					boolean[]	reads_a = new boolean[reads.size()];
					
					for (int i=0;i<reads.size();i++){
						
						reads_a[i] = ((Boolean)reads.get(i)).booleanValue();
					}
					
					subs.getHistory().markResults( rids_a, reads_a );
										
					sendBrowserMessage( "metasearch", "markSubscriptionResultsCompleted", result );
				}
			} catch( Throwable e ){
				
				result.put( "error", "delete failed: " + Debug.getNestedExceptionMessage(e));

				sendBrowserMessage("metasearch", "markSubscriptionResultsFailed",result);
			}
		}else if( OP_DOWNLOAD_SUBSCRIPTION.equals(opid)) {
			
			Map decodedMap = message.isParamObject() ? message.getDecodedMap():new HashMap();
			
			final Long	 tid = (Long) decodedMap.get("tid");

			final String sid = (String) decodedMap.get("id");
			
			final Map result = new HashMap();

			if ( tid != null )result.put( "tid", tid );

			try{
				Subscription subs = SubscriptionManagerFactory.getSingleton().getSubscriptionByID( sid );
				
				if ( subs == null ){
					
					result.put( "error", "Subscription not found" );

					sendBrowserMessage("metasearch", "downloadSubscriptionFailed", result );
					
				}else{
					
					subs.getManager().getScheduler().download(
						subs,
						new SubscriptionDownloadListener()
						{
							public void
							complete(
								Subscription		subs )
							{
								result.put( "id", subs.getID());
								
								encodeResults( subs, result );
								
								sendBrowserMessage( "metasearch", "downloadSubscriptionCompleted", result );
							}
							
							public void
							failed(
								Subscription			subs,
								SubscriptionException	error )
							{
								result.put( "error", "read failed: " + Debug.getNestedExceptionMessage(error));

								sendBrowserMessage( "metasearch", "downloadSubscriptionFailed", result );

							}
						});
				}
			} catch( Throwable e ){
				
				result.put( "error", "read failed: " + Debug.getNestedExceptionMessage(e));

				sendBrowserMessage( "metasearch", "downloadSubscriptionFailed", result );
			}
		}
	}
	
	protected void
	encodeResults(
		Subscription	subs,
		Map				result )
	{
		JSONArray	results_list = new JSONArray();
		
		SubscriptionResult[]	results = subs.getHistory().getResults( false );
		
		for(int i=0; i<results.length; i++){
			
			SubscriptionResult r = results[i];
			
			results_list.add( r.toJSONMap());
		}
		
		result.put( "results", results_list );
	}
	
	public boolean 
	sendBrowserMessage(
		String 		key, 
		String 		op, 
		Map 		params )
	{
		MetaSearchManagerFactory.getSingleton().log( "BrowserListener: sent " + op + ": " + params );

		return( context.sendBrowserMessage(key, op, params));
	}
	
	public boolean 
	sendBrowserMessage(
		String 			key, 
		String 			op, 
		Collection 		params )
	{
		MetaSearchManagerFactory.getSingleton().log( "BrowserListener: sent " + op + ": " + params );

		return( context.sendBrowserMessage(key, op, params));
	}

}
