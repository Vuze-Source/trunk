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

package com.aelitis.azureus.core.metasearch.impl.web.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.UrlUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.aelitis.azureus.core.metasearch.Engine;
import com.aelitis.azureus.core.metasearch.Result;
import com.aelitis.azureus.core.metasearch.ResultListener;
import com.aelitis.azureus.core.metasearch.SearchException;
import com.aelitis.azureus.core.metasearch.SearchParameter;
import com.aelitis.azureus.core.metasearch.impl.EngineImpl;
import com.aelitis.azureus.core.metasearch.impl.MetaSearchImpl;
import com.aelitis.azureus.core.metasearch.impl.web.FieldMapping;
import com.aelitis.azureus.core.metasearch.impl.web.WebEngine;
import com.aelitis.azureus.core.metasearch.impl.web.WebResult;

public class 
JSONEngine 
	extends WebEngine 
{
	public static EngineImpl
	importFromBEncodedMap(
		MetaSearchImpl	meta_search,
		Map				map )
	
		throws IOException
	{
		return( new JSONEngine( meta_search, map ));
	}
	
	public static Engine
	importFromJSONString(
		MetaSearchImpl	meta_search,
		long			id,
		long			last_updated,
		String			name,
		JSONObject		map )
	
		throws IOException
	{
		return( new JSONEngine( meta_search, id, last_updated, name, map ));
	}
	
	private String resultsEntryPath;

	
		// explicit test constructor

	public 
	JSONEngine(
		MetaSearchImpl		meta_search,
		long 				id,
		long 				last_updated,
		String 				name,
		String 				searchURLFormat,
		String 				timeZone,
		boolean 			automaticDateFormat,
		String 				userDateFormat,
		String 				resultsEntryPath,
		FieldMapping[] 		mappings) 
	{
		super( meta_search, Engine.ENGINE_TYPE_JSON, id,last_updated,name,searchURLFormat,timeZone,automaticDateFormat,userDateFormat,mappings);
		
		this.resultsEntryPath = resultsEntryPath;
		
		setSource( Engine.ENGINE_SOURCE_LOCAL );
		
		setSelectionState( SEL_STATE_MANUAL_SELECTED );
	}
	
		// bencoded constructor
	
	protected 
	JSONEngine(
		MetaSearchImpl	meta_search,
		Map				map )
	
		throws IOException
	{
		super( meta_search, map );
		
		resultsEntryPath = importString( map, "json.path" );
	}
	
		// json constructor
	
	protected 
	JSONEngine(
		MetaSearchImpl	meta_search,
		long			id,
		long			last_updated,
		String			name,
		JSONObject		map )
	
		throws IOException
	{
		super( meta_search, Engine.ENGINE_TYPE_JSON, id, last_updated, name, map );
				
		resultsEntryPath = importString( map, "json_result_key" );
	}
	
	public Map 
	exportToBencodedMap() 
	
		throws IOException
	{
		Map	res = new HashMap();
		
		exportString( res, "json.path", resultsEntryPath );
		
		super.exportToBencodedMap( res );
		
		return( res );
	}
	
	protected void
	exportToJSONObject(
		JSONObject		res )
	
		throws IOException
	{
		res.put( "json_result_key", resultsEntryPath );

		super.exportToJSONObject( res );
	}
	
	protected Result[]
	searchSupport(
		SearchParameter[] 	searchParameters,
		int					max_matches,
		String				headers, 
		ResultListener		listener )
	
		throws SearchException
	{	
		debugStart();
		
		String page = super.getWebPageContent( searchParameters, headers );
		
		if ( listener != null ){
			listener.contentReceived( this, page );
		}
		
		
		String searchQuery = null;
		
		for(int i = 0 ; i < searchParameters.length ; i++) {
			if(searchParameters[i].getMatchPattern().equals("s")) {
				searchQuery = searchParameters[i].getValue();
			}
		}
		
		FieldMapping[] mappings = getMappings();

		try {
			Object jsonObject = JSONValue.parse(page);
			
			JSONArray resultArray = null;
			
			if(resultsEntryPath != null) {
				StringTokenizer st = new StringTokenizer(resultsEntryPath,".");
				if(jsonObject instanceof JSONArray && st.countTokens() > 0) {
					JSONArray array = (JSONArray) jsonObject;
					if(array.size() == 1) {
						jsonObject = array.get(0);
					}
				}
				while(st.hasMoreTokens()) {
					try {
						jsonObject = ((JSONObject)jsonObject).get(st.nextToken());
					} catch(Throwable t) {
						throw new SearchException("Invalid entry path : " + resultsEntryPath,t);
					}
				}
			}
			
			try{
				resultArray = (JSONArray) jsonObject;
				
			}catch(Throwable t){
				
				throw new SearchException("Object is not a result array. Check the JSON service and/or the entry path");
			}
				
				
			if ( resultArray != null ){
				
				List results = new ArrayList();
				
				for(int i = 0 ; i < resultArray.size() ; i++) {
					
					Object obj = resultArray.get(i);
					
					if(obj instanceof JSONObject) {
						JSONObject jsonEntry = (JSONObject) obj;
						
						if ( max_matches >= 0 ){
							if ( --max_matches < 0 ){
								break;
							}
						}
						
						if ( listener != null ){
							
								// sort for consistent order
							
							Iterator it = new TreeMap( jsonEntry ).entrySet().iterator();
							
							String[]	groups = new String[ jsonEntry.size()];
							
							int	pos = 0;
							
							while( it.hasNext()){
								
								Map.Entry entry = (Map.Entry)it.next();
								
								Object key 		= entry.getKey();
								Object value 	= entry.getValue();
								
								if ( key != null && value != null ){
								
									groups[pos++] = key.toString() + "=" + UrlUtils.encode( value.toString());
									
								}else{
									
									groups[pos++] = "";
								}
							}
							
							listener.matchFound( this, groups );
						}
						
						WebResult result = new WebResult(getRootPage(),getBasePage(),getDateParser(),searchQuery);
													
						for(int j = 0 ; j < mappings.length ; j++) {
							String fieldFrom = mappings[j].getName();
							if(fieldFrom != null) {
								int fieldTo = mappings[j].getField();
								Object fieldContentObj = ((Object)jsonEntry.get(fieldFrom));
								if(fieldContentObj != null) {
									String fieldContent = fieldContentObj.toString();
									
									switch(fieldTo) {
									case FIELD_NAME :
										result.setNameFromHTML(fieldContent);
										break;
									case FIELD_SIZE :
										result.setSizeFromHTML(fieldContent);
										break;
									case FIELD_PEERS :
										result.setNbPeersFromHTML(fieldContent);
										break;
									case FIELD_SEEDS :
										result.setNbSeedsFromHTML(fieldContent);
										break;
									case FIELD_CATEGORY :
										result.setCategoryFromHTML(fieldContent);
										break;
									case FIELD_DATE :
										result.setPublishedDateFromHTML(fieldContent);
										break;
									case FIELD_COMMENTS :
										result.setCommentsFromHTML(fieldContent);
										break;
									case FIELD_CDPLINK :
										result.setCDPLink(fieldContent);
										break;
									case FIELD_TORRENTLINK :
										result.setTorrentLink(fieldContent);
										break;
									case FIELD_PLAYLINK :
										result.setPlayLink(fieldContent);
										break;
									case FIELD_VOTES :
										result.setVotesFromHTML(fieldContent);
										break;
									case FIELD_SUPERSEEDS :
										result.setNbSuperSeedsFromHTML(fieldContent);
										break;
									case FIELD_PRIVATE :
										result.setPrivateFromHTML(fieldContent);
										break;
									default:
										break;
									}
								}
							}
						}
													
						results.add(result);
					}
				}
				
				Result[] res = (Result[]) results.toArray(new Result[results.size()]);

				debugLog( "success: found " + res.length + " results" );
				
				return( res );
				
			}else{
			
				debugLog( "success: no result array found so no results" );
				
				return( new Result[0]);
			}
			
		}catch( Throwable e ){
			
			debugLog( "failed: " + Debug.getNestedExceptionMessageAndStack( e ));
			
			if ( e instanceof SearchException ){
				
				throw((SearchException)e );
			}
			
			throw( new SearchException( "JSON matching failed", e ));
		}
	}
	
	public String getIcon() {
		
		String rootPage = getRootPage();
		
		if(rootPage != null) {
			return rootPage + "/favicon.ico";
		}
		return null;
	}
	

}
