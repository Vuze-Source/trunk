/*
 * Created on Dec 7, 2016
 * Created by Paul Gardner
 * 
 * Copyright 2016 Azureus Software, Inc.  All rights reserved.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or 
 * (at your option) any later version.
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


package com.aelitis.azureus.ui.swt.search;

import java.util.*;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.AENetworkClassifier;
import org.gudy.azureus2.core3.util.Base32;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.UrlUtils;
import org.gudy.azureus2.plugins.ui.tables.TableColumn;
import org.gudy.azureus2.plugins.ui.tables.TableColumnCreationListener;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.mainwindow.ClipboardCopy;
import org.gudy.azureus2.ui.swt.views.table.TableViewSWT;
import org.gudy.azureus2.ui.swt.views.table.TableViewSWTMenuFillListener;
import org.gudy.azureus2.ui.swt.views.table.impl.TableViewFactory;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.AzureusCoreRunningListener;
import com.aelitis.azureus.core.metasearch.Engine;
import com.aelitis.azureus.core.metasearch.MetaSearchManager;
import com.aelitis.azureus.core.metasearch.MetaSearchManagerFactory;
import com.aelitis.azureus.core.metasearch.Result;
import com.aelitis.azureus.core.metasearch.ResultListener;
import com.aelitis.azureus.core.metasearch.SearchParameter;
import com.aelitis.azureus.core.util.CopyOnWriteSet;
import com.aelitis.azureus.ui.UIFunctions;
import com.aelitis.azureus.ui.UIFunctionsManager;
import com.aelitis.azureus.ui.common.table.TableColumnCore;
import com.aelitis.azureus.ui.common.table.TableLifeCycleListener;
import com.aelitis.azureus.ui.common.table.TableRowCore;
import com.aelitis.azureus.ui.common.table.TableSelectionListener;
import com.aelitis.azureus.ui.common.table.TableViewFilterCheck;
import com.aelitis.azureus.ui.common.table.impl.TableColumnManager;
import com.aelitis.azureus.ui.selectedcontent.DownloadUrlInfo;
import com.aelitis.azureus.ui.selectedcontent.ISelectedContent;
import com.aelitis.azureus.ui.selectedcontent.SelectedContent;
import com.aelitis.azureus.ui.selectedcontent.SelectedContentManager;
import com.aelitis.azureus.ui.swt.columns.search.ColumnSearchResultSite;
import com.aelitis.azureus.ui.swt.columns.searchsubs.*;
import com.aelitis.azureus.ui.swt.imageloader.ImageLoader;
import com.aelitis.azureus.ui.swt.imageloader.ImageLoader.ImageDownloaderListener;
import com.aelitis.azureus.ui.swt.search.SearchResultsTabArea.SearchQuery;
import com.aelitis.azureus.ui.swt.skin.SWTSkinObject;
import com.aelitis.azureus.ui.swt.skin.SWTSkinObjectContainer;
import com.aelitis.azureus.ui.swt.skin.SWTSkinObjectText;
import com.aelitis.azureus.ui.swt.skin.SWTSkinObjectTextbox;
import com.aelitis.azureus.ui.swt.skin.SWTSkinObjectToggle;
import com.aelitis.azureus.ui.swt.skin.SWTSkinToggleListener;

public class 
SBC_SearchResultsView 
	implements SearchResultsTabAreaBase, TableViewFilterCheck<SBC_SearchResult>
{
	public static final String TABLE_SR = "SearchResults";

	private static boolean columnsAdded = false;
	
	private SearchResultsTabArea		parent;
	
	private TableViewSWT<SBC_SearchResult> tv_subs_results;

	private Composite			table_parent;
	
	
	private Text txtFilter;


	private int minSize;
	private int maxSize;
	
	private final CopyOnWriteSet<String>	deselected_engines = new CopyOnWriteSet<String>( false );
	
	private Composite engine_area;
	
	private List<SBC_SearchResult>	last_selected_content = new ArrayList<SBC_SearchResult>();
	
	private Object 			search_lock	= new Object();
	private SearchInstance	current_search;
	
	protected
	SBC_SearchResultsView(
		SearchResultsTabArea		_parent )
	{
		parent	= _parent;
	}
	
	private SWTSkinObject 
	getSkinObject(
		String viewID )
	{
		return( parent.getSkinObject(viewID));
	}
	
	public Object 
	skinObjectInitialShow(
		SWTSkinObject skinObject, Object params ) 
	{
		AzureusCoreFactory.addCoreRunningListener(
			new AzureusCoreRunningListener() 
			{
				public void 
				azureusCoreRunning(
					AzureusCore core )
				{
					initColumns( core );
				}
			});
		
		SWTSkinObjectTextbox soFilterBox = (SWTSkinObjectTextbox) getSkinObject("filterbox");
		if (soFilterBox != null) {
			txtFilter = soFilterBox.getTextControl();
		}

		final SWTSkinObject soFilterArea = getSkinObject("filterarea");
		if (soFilterArea != null) {
			
			SWTSkinObjectToggle soFilterButton = (SWTSkinObjectToggle) getSkinObject("filter-button");
			if (soFilterButton != null) {
				soFilterButton.addSelectionListener(new SWTSkinToggleListener() {
					public void toggleChanged(SWTSkinObjectToggle so, boolean toggled) {
						soFilterArea.setVisible(toggled);
						Utils.relayout(soFilterArea.getControl().getParent());
					}
				});
			}
			
			Composite parent = (Composite) soFilterArea.getControl();
	
			Composite filter_area = new Composite(parent, SWT.NONE);
			FormData fd = Utils.getFilledFormData();
			filter_area.setLayoutData(fd);

			GridLayout layout = new GridLayout();
			layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0; 
			filter_area.setLayout(layout);
			
			int sepHeight = 20;
			
			Composite cRow = new Composite(filter_area, SWT.NONE);
			cRow.setLayoutData(new GridData( GridData.FILL_HORIZONTAL ));
			
			RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
			rowLayout.spacing = 5;
			rowLayout.marginBottom = rowLayout.marginTop = rowLayout.marginLeft = rowLayout.marginRight = 0; 
			rowLayout.center = true;
			cRow.setLayout(rowLayout);
			
			

			/////
			
		

				// min size
			
			Composite cMinSize = new Composite(cRow, SWT.NONE);
			layout = new GridLayout(2, false);
			layout.marginWidth = 0;
			layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
			cMinSize.setLayout(layout);
			Label lblMinSize = new Label(cMinSize, SWT.NONE);
			lblMinSize.setText(MessageText.getString("SubscriptionResults.filter.min_size"));
			Spinner spinMinSize = new Spinner(cMinSize, SWT.BORDER);
			spinMinSize.setMinimum(0);
			spinMinSize.setMaximum(100*1024*1024);	// 100 TB should do...
			spinMinSize.setSelection(minSize);
			spinMinSize.addListener(SWT.Selection, new Listener() {
				public void handleEvent(Event event) {
					minSize = ((Spinner) event.widget).getSelection();
					refilter();
				}
			});
			
			// max size
			
			Label label = new Label(cRow, SWT.VERTICAL | SWT.SEPARATOR);
			label.setLayoutData(new RowData(-1, sepHeight));

			Composite cMaxSize = new Composite(cRow, SWT.NONE);
			layout = new GridLayout(2, false);
			layout.marginWidth = 0;
			layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
			cMaxSize.setLayout(layout);
			Label lblMaxSize = new Label(cMaxSize, SWT.NONE);
			lblMaxSize.setText(MessageText.getString("SubscriptionResults.filter.max_size"));
			Spinner spinMaxSize = new Spinner(cMaxSize, SWT.BORDER);
			spinMaxSize.setMinimum(0);
			spinMaxSize.setMaximum(100*1024*1024);	// 100 TB should do...
			spinMaxSize.setSelection(maxSize);
			spinMaxSize.addListener(SWT.Selection, new Listener() {
				public void handleEvent(Event event) {
					maxSize = ((Spinner) event.widget).getSelection();
					refilter();
				}
			});
			
			engine_area = new Composite(filter_area, SWT.NONE);
			engine_area.setLayoutData(new GridData( GridData.FILL_HORIZONTAL ));

			buildEngineArea( new Engine[0] );
			
			parent.layout(true);
		}

		return null;
	}
	
	private void
	buildEngineArea(
		Engine[]	engines )
	{
		Utils.disposeComposite( engine_area, false );
		
		RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
		rowLayout.spacing = 5;
		rowLayout.marginBottom = rowLayout.marginTop = rowLayout.marginLeft = rowLayout.marginRight = 0; 
		rowLayout.center = true;
		engine_area.setLayout(rowLayout);
		
		for ( final Engine engine: engines ){
			
			final Button button = new Button( engine_area, SWT.CHECK );
			
			button.setText( engine.getName());
			
			button.setSelection( !deselected_engines.contains( engine.getUID()));
			
			Image image = 
				getIcon(
					engine,
					new ImageLoadListener() {
						
						public void imageLoaded(Image image) {
							button.setImage( image );
						}
					});
			
			if ( image != null ){
				
				button.setImage( image );
			}
			
			button.addSelectionListener(
				new SelectionAdapter() {
			
					public void widgetSelected(SelectionEvent e){
						
						String id = engine.getUID();
						
						if ( button.getSelection()){
							
							deselected_engines.remove( id );
							
						}else{
							
							deselected_engines.add( id );
						}
						
						refilter();
					}
				});
		}
		
		engine_area.layout( true );
	}
	
	private void
	setSearchEngines(
		final Engine[]		engines )
	{
		Utils.execSWTThread(
			new Runnable()
			{
				public void 
				run() 
				{
					buildEngineArea( engines );
				}
			});
	}
	
	private boolean 
	isOurContent(
		SBC_SearchResult result) 
	{
		long	size = result.getSize();
		
		boolean show = 
			
			(size==-1||(size >= 1024L*1024*minSize)) &&
			(size==-1||(maxSize ==0 || size <= 1024L*1024*maxSize));
		
		if ( !show ){
			
			return( false );
		}
		
		String engine_id = result.getEngine().getUID();
		
		if ( deselected_engines.contains( engine_id )){
			
			return( false );
		}
		
		return( true );
	}


	protected void refilter() {
		if (tv_subs_results != null) {
			tv_subs_results.refilter();
		}
	}


	private void 
	initColumns(
		AzureusCore core ) 
	{
		synchronized( SBC_SearchResultsView.class ){
			
			if ( columnsAdded ){
			
				return;
			}
		
			columnsAdded = true;
		}
		
		TableColumnManager tableManager = TableColumnManager.getInstance();
				
		tableManager.registerColumn(
			SBC_SearchResult.class, 
			ColumnSearchSubResultType.COLUMN_ID,
				new TableColumnCreationListener() {
					
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultType(column);
					}
				});			
		tableManager.registerColumn(
			SBC_SearchResult.class, 
			ColumnSearchSubResultName.COLUMN_ID,
				new TableColumnCreationListener() {
					
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultName(column);
					}
				});	
		
		tableManager.registerColumn(
			SBC_SearchResult.class, 
			ColumnSearchSubResultActions.COLUMN_ID,
				new TableColumnCreationListener() {
					
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultActions(column);
					}
				});			
		
		tableManager.registerColumn(
			SBC_SearchResult.class, 
			ColumnSearchSubResultSize.COLUMN_ID,
				new TableColumnCreationListener() {
					
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultSize(column);
					}
				});			
			
		tableManager.registerColumn(
			SBC_SearchResult.class, 
			ColumnSearchSubResultSeedsPeers.COLUMN_ID,
				new TableColumnCreationListener() {
					
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultSeedsPeers(column);
					}
				});		
	
		tableManager.registerColumn(
			SBC_SearchResult.class, 
			ColumnSearchSubResultRatings.COLUMN_ID,
				new TableColumnCreationListener() {
					
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultRatings(column);
					}
				});		

		tableManager.registerColumn(
			SBC_SearchResult.class, 
			ColumnSearchSubResultAge.COLUMN_ID,
				new TableColumnCreationListener() {
					
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultAge(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class, 
			ColumnSearchSubResultRank.COLUMN_ID,
				new TableColumnCreationListener() {
					
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultRank(column);
					}
				});
		
		tableManager.registerColumn(
			SBC_SearchResult.class, 
			ColumnSearchSubResultCategory.COLUMN_ID,
				new TableColumnCreationListener() {
					
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultCategory(column);
					}
				});
		
		tableManager.registerColumn(
			SBC_SearchResult.class, 
			ColumnSearchResultSite.COLUMN_ID,
				new TableColumnCreationListener() {
					
					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchResultSite(column);
					}
				});
	}
	
	public void
	showView()
	{
		SWTSkinObject so_list = getSkinObject("search-results-list");

		if ( so_list != null ){
				
			so_list.setVisible(true);
			
			initTable((Composite) so_list.getControl());
		}
	}
	
	public void
	hideView()
	{
		synchronized( search_lock ){
			
			if ( current_search != null ){
				
				current_search.cancel();
				
				current_search = null;
			}
		}
		
		Utils.disposeSWTObjects(new Object[] {
			table_parent,
		});
	}
	
	private void 
	initTable(
		Composite control ) 
	{
		tv_subs_results = TableViewFactory.createTableViewSWT(
				SBC_SearchResult.class, 
				TABLE_SR,
				TABLE_SR, 
				new TableColumnCore[0], 
				ColumnSearchSubResultName.COLUMN_ID,
				SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL );
		
		TableColumnManager tableManager = TableColumnManager.getInstance();

		tableManager.setDefaultColumnNames( TABLE_SR,
				new String[] {
				ColumnSearchSubResultType.COLUMN_ID,
				ColumnSearchSubResultName.COLUMN_ID,
				ColumnSearchSubResultActions.COLUMN_ID,
				ColumnSearchSubResultSize.COLUMN_ID,
				ColumnSearchSubResultSeedsPeers.COLUMN_ID,
				ColumnSearchSubResultRatings.COLUMN_ID,
				ColumnSearchSubResultAge.COLUMN_ID,
				ColumnSearchSubResultRank.COLUMN_ID,
				ColumnSearchSubResultCategory.COLUMN_ID,
				ColumnSearchResultSite.COLUMN_ID,
			});
		
		tableManager.setDefaultSortColumnName(TABLE_SR, ColumnSearchSubResultRank.COLUMN_ID);
		
		
		if (txtFilter != null) {
			tv_subs_results.enableFilterCheck(txtFilter, this);
		}
		
		tv_subs_results.setRowDefaultHeight(COConfigurationManager.getIntParameter( "Search Subs Row Height" ));
		
		SWTSkinObject soSizeSlider = getSkinObject("table-size-slider");
		if (soSizeSlider instanceof SWTSkinObjectContainer) {
			SWTSkinObjectContainer so = (SWTSkinObjectContainer) soSizeSlider;
			if (!tv_subs_results.enableSizeSlider(so.getComposite(), 16, 100)) {
				so.setVisible(false);
			}
		}
		
		table_parent = new Composite(control, SWT.NONE);
		table_parent.setLayoutData(Utils.getFilledFormData());
		GridLayout layout = new GridLayout();
		layout.marginHeight = layout.marginWidth = layout.verticalSpacing = layout.horizontalSpacing = 0;
		table_parent.setLayout(layout);

		tv_subs_results.addSelectionListener(new TableSelectionListener() {

			public void 
			selected(
				TableRowCore[] _rows) 
			{
				updateSelectedContent();
			}

			public void mouseExit(TableRowCore row) {
			}

			public void mouseEnter(TableRowCore row) {
			}

			public void focusChanged(TableRowCore focus) {
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				if (uiFunctions != null) {
					uiFunctions.refreshIconBar();
				}
			}

			public void deselected(TableRowCore[] rows) {
				updateSelectedContent();
			}

			public void defaultSelected(TableRowCore[] rows, int stateMask) {
			}
			
			private void
			updateSelectedContent()
			{
				TableRowCore[] rows = tv_subs_results.getSelectedRows();
				
				ArrayList<ISelectedContent>	valid = new ArrayList<ISelectedContent>();

				last_selected_content.clear();
				
				for (int i=0;i<rows.length;i++){
					
					SBC_SearchResult rc = (SBC_SearchResult)rows[i].getDataSource();
					
					last_selected_content.add( rc );
					
					byte[] hash = rc.getHash();
					
					if ( hash != null && hash.length > 0 ){
						
						SelectedContent sc = new SelectedContent(Base32.encode(hash), rc.getName());
						
						sc.setDownloadInfo(new DownloadUrlInfo(	getDownloadURI( rc )));
						
						valid.add(sc);
					}
				}
				
				ISelectedContent[] sels = valid.toArray( new ISelectedContent[valid.size()] );
				
				SelectedContentManager.changeCurrentlySelectedContent("IconBarEnabler",
						sels, tv_subs_results);
				
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				
				if ( uiFunctions != null ){
					
					uiFunctions.refreshIconBar();
				}
			}
		}, false);

		tv_subs_results.addLifeCycleListener(
			new TableLifeCycleListener() 
			{
				public void 
				tableViewInitialized() 
				{
				}

				public void 
				tableViewDestroyed() 
				{
				}
			});


		tv_subs_results.addMenuFillListener(
			new TableViewSWTMenuFillListener()
			{
				public void 
				fillMenu(String sColumnName, Menu menu)
				{
					Object[] _related_content = tv_subs_results.getSelectedDataSources().toArray();

					final SBC_SearchResult[] results = new SBC_SearchResult[_related_content.length];

					System.arraycopy(_related_content, 0, results, 0, results.length);
					
					MenuItem item = new MenuItem(menu, SWT.PUSH);
					item.setText(MessageText.getString("label.copy.url.to.clip"));
					item.addSelectionListener(new SelectionAdapter() {
						public void widgetSelected(SelectionEvent e) {
							
							StringBuffer buffer = new StringBuffer(1024);
							
							for ( SBC_SearchResult result: results ){
								
								if ( buffer.length() > 0 ){
									buffer.append( "\r\n" );
								}
								
								buffer.append( getDownloadURI( result ));
							}
							ClipboardCopy.copyToClipBoard( buffer.toString());
						};
					});
					
					item.setEnabled( results.length > 0 );
					
					new MenuItem(menu, SWT.SEPARATOR );
				}

				public void 
				addThisColumnSubMenu(
					String columnName, Menu menuThisColumn) 
				{
				}
			});
		
		tv_subs_results.initialize( table_parent );

		control.layout(true);
	}
	
	protected void
	invalidate(
		SBC_SearchResult		result )
	{
		TableRowCore row = tv_subs_results.getRow( result );
		
		if ( row != null ){
			
			row.invalidate( true );
		}
	}
	
	public boolean 
	filterCheck(
		SBC_SearchResult ds, 
		String filter, 
		boolean regex)
	{	
		if (!isOurContent(ds)){
			
			return false;
		}

		if ( filter == null || filter.length() == 0 ){
			
			return( true );
		}

		try{
			String name = ds.getName();
			
			String s = regex ? filter : "\\Q" + filter.replaceAll("[|;]", "\\\\E|\\\\Q") + "\\E";
			
			boolean	match_result = true;
			
			if ( regex && s.startsWith( "!" )){
				
				s = s.substring(1);
				
				match_result = false;
			}
			
			Pattern pattern = Pattern.compile(s, Pattern.CASE_INSENSITIVE);
  
			return( pattern.matcher(name).find() == match_result );
			
		}catch(Exception e ){
			
			return true;
		}
	}
	
	public void filterSet(String filter) {
	}

	public void
	anotherSearch(
		SearchQuery	sq )
	{		
		synchronized( search_lock ){
			
			if ( current_search != null ){
				
				current_search.cancel();
			}
			
			current_search = new SearchInstance( sq );
		}
	}
	
	public String
	getDownloadURI(
		SBC_SearchResult	result )
	{
		String torrent_url = (String)result.getTorrentLink();
		
		if ( torrent_url != null && torrent_url.length() > 0 ){
			
			return( torrent_url );
		}
		
		String uri = UrlUtils.getMagnetURI( result.getHash(), result.getName(), new String[]{ AENetworkClassifier.AT_PUBLIC });
		
		return( uri );
	}
	
	private static ImageLoader	image_loader = new ImageLoader( null, null );

	private static Map<String,Object[]>	image_map = new HashMap<String,Object[]>();

	public Image
	getIcon(
		final SBC_SearchResult		result )
	{
		return( getIcon( result.getEngine(), result ));
	}
	
	public Image
	getIcon(
		Engine					engine,
		ImageLoadListener		result )
	{
		String icon = engine.getIcon();
		
		Image img = null;
		
		if ( icon != null ){
			
			Object[] x = image_map.get( icon );
			
			if ( x == null ){
				
				Set<ImageLoadListener>	waiters = new HashSet<ImageLoadListener>();
				
				final Object[] f_x = new Object[]{ null, waiters, SystemTime.getMonotonousTime() };
				
				waiters.add( result );
				
				image_map.put( icon, f_x );
				
				image_loader.getUrlImage( 
					icon, 
					new ImageDownloaderListener() {
						
						public void imageDownloaded(Image image, boolean returnedImmediately) {
							
							f_x[0]	= image;
							
							Set<ImageLoadListener> set = (Set<ImageLoadListener>)f_x[1];
			
							for ( ImageLoadListener result: set ){
								
								result.imageLoaded( image );
							}
							
							f_x[1] = null;
						}
					});
				
				img = (Image)f_x[0];	// in case synchronously set
				
			}else{
				
				if ( x[1] instanceof Set ){
					
					((Set<ImageLoadListener>)x[1]).add( result );
					
				}else{
					
					img = (Image)x[0];
					
					if ( img == null ){
						
						if ( SystemTime.getMonotonousTime() - (Long)x[2] > 120*1000 ){
							
							image_map.remove( icon );
						}
					}
				}
			}
		}
		
		return( img );
	}
	
	public interface
	ImageLoadListener
	{
		public void
		imageLoaded(
			Image		image );
	}
	
	private class
	SearchInstance
		implements ResultListener
	{
		private volatile boolean	cancelled;
		
		private
		SearchInstance(
			SearchQuery		sq )
		{
			tv_subs_results.removeAllTableRows();
			
			SWTSkinObjectText title = (SWTSkinObjectText)parent.getSkinObject("title");
				
			if ( title != null ){
					
				title.setText( MessageText.getString( "search.results.view.title", new String[]{ sq.term }));
			}
			
			MetaSearchManager metaSearchManager = MetaSearchManagerFactory.getSingleton();
			
			List<SearchParameter>	sps = new ArrayList<SearchParameter>();
						
			sps.add( new SearchParameter( "s", sq.term ));
			
			SearchParameter[] parameters = sps.toArray(new SearchParameter[ sps.size()] );
			
			Map<String,String>	context = new HashMap<String, String>();
			
			context.put( Engine.SC_FORCE_FULL, "true" );
			
			context.put( Engine.SC_BATCH_PERIOD, "250" );
			
			context.put( Engine.SC_REMOVE_DUP_HASH, "true" );
			
			String headers = null;	// use defaults
			
			Engine[] engines = metaSearchManager.getMetaSearch().search( this, parameters, headers, context, 500 );
			
			setSearchEngines( engines );
		}
		
		protected void
		cancel()
		{
			cancelled	= true;
		}
		
		public void 
		contentReceived(
			Engine engine, 
			String content ) 
		{
		}
		
		public void 
		matchFound(
			Engine 		engine, 
			String[] 	fields ) 
		{
		}
		
		public void 
		engineFailed(
			Engine 		engine, 
			Throwable 	e ) 
		{	
			if ( cancelled ){
				
				return;
			}
			
			Debug.out( e );
		}
		
		public void 
		engineRequiresLogin(
			Engine 		engine, 
			Throwable 	e ) 
		{
			if ( cancelled ){
				
				return;
			}
			
			Debug.out( e );
		}
		
		public void 
		resultsComplete(
			Engine engine ) 
		{
			if ( cancelled ){
				
				return;
			}
			
			System.out.println( "Got complete from " + engine.getName());
		}
		
		public void 
		resultsReceived(
			Engine 		engine,
			Result[] 	results) 
		{
			if ( cancelled ){
				
				return;
			}
			
			SBC_SearchResult[]	data_sources = new  SBC_SearchResult[ results.length ];
			
			for ( int i=0;i<results.length;i++){
				
				data_sources[i] = new SBC_SearchResult( SBC_SearchResultsView.this, engine, results[i] );
			}
			
			tv_subs_results.addDataSources( data_sources );
		}
	}
}
