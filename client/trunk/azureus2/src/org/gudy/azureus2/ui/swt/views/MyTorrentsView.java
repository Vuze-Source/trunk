/*
 * Created on 30 juin 2003
 *
 * Copyright (C) 2004 Aelitis SARL, All rights Reserved
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
 *
 * AELITIS, SARL au capital de 30,000 euros,
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 */

package org.gudy.azureus2.ui.swt.views;

import com.aelitis.azureus.core.AzureusCore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;
import org.gudy.azureus2.core3.category.Category;
import org.gudy.azureus2.core3.category.CategoryListener;
import org.gudy.azureus2.core3.category.CategoryManager;
import org.gudy.azureus2.core3.category.CategoryManagerListener;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.download.DownloadManagerListener;
import org.gudy.azureus2.core3.global.GlobalManager;
import org.gudy.azureus2.core3.global.GlobalManagerDownloadRemovalVetoException;
import org.gudy.azureus2.core3.global.GlobalManagerListener;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.logging.LGLogger;
import org.gudy.azureus2.core3.peer.PEPeerSource;
import org.gudy.azureus2.core3.torrent.TOTorrent;
import org.gudy.azureus2.core3.torrent.TOTorrentFactory;
import org.gudy.azureus2.core3.tracker.client.TRTrackerAnnouncer;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.plugins.ui.tables.TableManager;
import org.gudy.azureus2.ui.swt.*;
import org.gudy.azureus2.ui.swt.URLTransfer;
import org.gudy.azureus2.ui.swt.components.FlatImageButton;
import org.gudy.azureus2.ui.swt.exporttorrent.wizard.ExportTorrentWizard;
import org.gudy.azureus2.ui.swt.help.HealthHelpWindow;
import org.gudy.azureus2.ui.swt.mainwindow.MainWindow;
import org.gudy.azureus2.ui.swt.mainwindow.TorrentOpener;
import org.gudy.azureus2.ui.swt.maketorrent.MultiTrackerEditor;
import org.gudy.azureus2.ui.swt.maketorrent.TrackerEditorListener;
import org.gudy.azureus2.ui.swt.views.table.TableColumnCore;
import org.gudy.azureus2.ui.swt.views.table.TableRowCore;
import org.gudy.azureus2.ui.swt.views.utils.ManagerUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;

/** Displays a list of torrents in a table view.
 *
 * @author Olivier
 * @author TuxPaper
 *         2004/Apr/18: Use TableRowImpl instead of PeerRow
 *         2004/Apr/20: Remove need for tableItemToObject
 *         2004/Apr/21: extends TableView instead of IAbstractView
 */
public class MyTorrentsView
       extends TableView
       implements GlobalManagerListener,
                  ParameterListener,
                  DownloadManagerListener,
                  CategoryManagerListener,
                  CategoryListener
{
	private AzureusCore		azureus_core;

  private GlobalManager globalManager;
  private boolean isSeedingView;

  private Composite cTablePanel;
  private Font fontButton = null;
  private Combo cCategories;
  private SelectionListener cCategoriesListener;
  private Control catRemoveButton;
  private Menu menuCategory;
  private MenuItem menuItemChangeDir = null;

  private Map downloadBars;
  private AEMonitor				downloadBars_mon	= new AEMonitor( "MyTorrentsView:DL" );

  private Category currentCategory;
  private boolean skipDMAdding = true;

  // table item index, where the drag has started
  private int drag_drop_line_start = -1;

  private boolean confirmDataDelete = COConfigurationManager.getBooleanParameter("Confirm Data Delete", true);

  public 
  MyTorrentsView(
  		AzureusCore			_azureus_core, 
		boolean 			isSeedingView,
        TableColumnCore[] 	basicItems) 
  {
    super((isSeedingView) ? TableManager.TABLE_MYTORRENTS_COMPLETE
                          : TableManager.TABLE_MYTORRENTS_INCOMPLETE,
          "MyTorrentsView", basicItems, "#", 
          SWT.MULTI | SWT.FULL_SELECTION | SWT.BORDER);
    ptIconSize = new Point(16, 16);
    azureus_core		= _azureus_core;
    this.globalManager 	= azureus_core.getGlobalManager();
    this.isSeedingView 	= isSeedingView;

    downloadBars = MainWindow.getWindow().getDownloadBars();
    currentCategory = CategoryManager.getCategory(Category.TYPE_ALL);
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IView#initialize(org.eclipse.swt.widgets.Composite)
   */
  public void initialize(Composite composite0) {
    if(cTablePanel != null) {
      return;
    }

    super.initialize(composite0);

    initCategoryViewList();

    createDragDrop();

    COConfigurationManager.addParameterListener("Confirm Data Delete", this);

    activateCategory(currentCategory);
    CategoryManager.addCategoryManagerListener(this);
    // globalManager.addListener sends downloadManagerAdded()'s when you addListener
    // we don't need them..
    skipDMAdding = true;
    globalManager.addListener(this);
    skipDMAdding = false;
  }


  public void tableStructureChanged() {
    super.tableStructureChanged();

    createDragDrop();
    activateCategory(currentCategory);
  }

  public Composite createMainPanel(Composite composite) {
    GridData gridData;
    Composite panel = new Composite(composite, SWT.NULL);
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    layout.horizontalSpacing = 0;
    layout.verticalSpacing = 0;
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    panel.setLayout(layout);
    panel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    cTablePanel = new Composite(panel, SWT.NULL);
    gridData = new GridData(GridData.FILL_BOTH);
    gridData.horizontalSpan = 2;
    cTablePanel.setLayoutData(gridData);

    layout = new GridLayout(1, false);
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    layout.verticalSpacing = 0;
    layout.horizontalSpacing = 0;
    cTablePanel.setLayout(layout);

    return panel;
  }

  private void initCategoryViewList() {
      GridData gridData;

      final Label l = new Label(getComposite(), SWT.WRAP);
      gridData = new GridData();
      gridData.horizontalIndent = 3;
      l.setLayoutData(gridData);
      Messages.setLanguageText(l, sTableID + "View.header");
      l.pack();

      final Composite cComp = new Composite(getComposite(), SWT.NONE);
      final RowLayout rowLayout = new RowLayout();
      rowLayout.marginTop = (Constants.isOSX) ? 3 : 0; // this fixes display artifacts
      rowLayout.marginBottom = (Constants.isOSX) ? 3 : 0; // this fixes display artifacts
      rowLayout.marginLeft = (Constants.isOSX) ? 3 : 0; // this fixes display artifacts
      rowLayout.marginRight = (Constants.isOSX) ? 3 : 0; // this fixes display artifacts
      rowLayout.fill = true;
      cComp.setLayout(new RowLayout());


      cComp.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));

      final SelectionAdapter actionButtonListener = new SelectionAdapter() {
          public void widgetSelected(SelectionEvent event) {
              final Category cat = addCategory();
              if(cat  != null && getSelectedRows().length > 0) {
                  cCategories.select(cCategories.indexOf(cat.getName()));
              }
          }
      };

      final Control actionButton;
      if(Constants.isOSX) {
        actionButton = new FlatImageButton(cComp, FlatImageButton.PLUS_BUTTON);
        ((FlatImageButton)actionButton).addSelectionListener(actionButtonListener);
      }
      else {
        actionButton = new Button(cComp, SWT.PUSH);
        ((Button)actionButton).setText(" + ");
        ((Button)actionButton).addSelectionListener(actionButtonListener);
      }

      final SelectionAdapter removeActionListener = new SelectionAdapter() {
          public void widgetSelected(SelectionEvent event) {
              final Category catToDelete = (Category)catRemoveButton.getData("Category");
              if(catToDelete != null) {
                java.util.List managers = catToDelete.getDownloadManagers();
                // move to array,since setcategory removed it from the category,
                // which would mess up our loop
                DownloadManager dms[] = (DownloadManager [])managers.toArray(new DownloadManager[managers.size()]);
                for (int i = 0; i < dms.length; i++) {
                    dms[i].getDownloadState().setCategory(null);
                }
                if (currentCategory == catToDelete) {
                    activateCategory(CategoryManager.getCategory(Category.TYPE_ALL));
                    catRemoveButton.setEnabled(false);
                }
                CategoryManager.removeCategory(catToDelete);
              }
          }
      };

      if(Constants.isOSX) {
          catRemoveButton = new FlatImageButton(cComp, FlatImageButton.MINUS_BUTTON);
          ((FlatImageButton)catRemoveButton).addSelectionListener(removeActionListener);
      }
      else {
          catRemoveButton = new Button(cComp, SWT.PUSH);
          ((Button)catRemoveButton).setText(" - ");
          ((Button)catRemoveButton).addSelectionListener(removeActionListener);
      }

      cCategories = new Combo(cComp, SWT.READ_ONLY);
      final RowData rData = new RowData();
      rData.width = 200;
      cCategories.setLayoutData(rData);
      cCategories.pack();

      // layout "hack"
      if(Constants.isOSX)
      {
          actionButton.setLayoutData(new RowData(actionButton.getSize().x,  cCategories.getSize().y));
          catRemoveButton.setLayoutData(new RowData(catRemoveButton.getSize().x,  cCategories.getSize().y));
      }
      catRemoveButton.setEnabled(false);

      cComp.moveAbove(null);
      l.moveAbove(null);

      cCategoriesListener = null;

      cComp.layout();
      getComposite().layout();

      modifyCategoryViewList();
  }

  private void modifyCategoryViewList() {
    final Category[] categories = CategoryManager.getCategories();

    if(cCategoriesListener != null) {
        cCategories.removeSelectionListener(cCategoriesListener);
    }

    cCategories.removeAll();
    if(categories.length == 0) {
        cCategories.add(MessageText.getString("Categories.all"));
        cCategories.setEnabled(false);
    }
    else {
        boolean enableCombo = false;
        Arrays.sort(categories);
        for (int i = 0; i < categories.length; i++) {
            final Category cat = categories[i];
            if(cat.getType() == Category.TYPE_USER) {
                cCategories.add(cat.getName());
                enableCombo = true;
            }
            else {
                cCategories.add(MessageText.getString(cat.getName()));
            }
        }
        cCategories.setEnabled(enableCombo);
    }

    cCategoriesListener = new SelectionAdapter() {
        public void widgetSelected(SelectionEvent event) {
            final Category selCat = categories[cCategories.getSelectionIndex()];

            if(selCat == currentCategory) {return;}

            catRemoveButton.setEnabled(selCat.getType() == Category.TYPE_USER);
            catRemoveButton.setData("Category", selCat);
            activateCategory(selCat);
        }
    };
    cCategories.addSelectionListener(cCategoriesListener);

    if(cCategories.getSelectionIndex() == -1) {
        if(currentCategory == null) {
            cCategories.select(0);
        }
        else if(currentCategory.getType() == Category.TYPE_USER) {
            cCategories.select(cCategories.indexOf(currentCategory.getName()));
        }
        else {
            cCategories.select(cCategories.indexOf(MessageText.getString(currentCategory.getName())));
        }
    }
  }

  public Table createTable() {
    bSkipFirstColumn = true;
    Table table = new Table(cTablePanel, SWT.MULTI | SWT.FULL_SELECTION | SWT.BORDER);
    table.setLayoutData(new GridData(GridData.FILL_BOTH));

    table.addKeyListener(createKeyListener());

    table.addSelectionListener(new SelectionAdapter() {
      public void widgetDefaultSelected(SelectionEvent e) {
        DownloadManager dm = (DownloadManager)getFirstSelectedDataSource();
        if (dm != null)
          MainWindow.getWindow().openManagerView(dm);
      }
    });

    cTablePanel.layout();
    return table;
  }

  public void fillMenu(final Menu menu) {
    final MenuItem itemDetails = new MenuItem(menu, SWT.PUSH);
    Messages.setLanguageText(itemDetails, "MyTorrentsView.menu.showdetails"); //$NON-NLS-1$
    menu.setDefaultItem(itemDetails);
    Utils.setMenuItemImage(itemDetails, "details");

    final MenuItem itemBar = new MenuItem(menu, SWT.CHECK);
    Messages.setLanguageText(itemBar, "MyTorrentsView.menu.showdownloadbar"); //$NON-NLS-1$
    Utils.setMenuItemImage(itemBar, "downloadBar");

    new MenuItem(menu, SWT.SEPARATOR);

    final MenuItem itemOpen = new MenuItem(menu, SWT.PUSH);
    Messages.setLanguageText(itemOpen, "MyTorrentsView.menu.open"); //$NON-NLS-1$
    Utils.setMenuItemImage(itemOpen, "run");

    final MenuItem itemExplore = new MenuItem(menu, SWT.PUSH);
    Messages.setLanguageText(itemExplore, "MyTorrentsView.menu.explore"); //$NON-NLS-1$

    // advanced menu
    
    final MenuItem itemAdvanced = new MenuItem(menu, SWT.CASCADE);
    Messages.setLanguageText(itemAdvanced, "MyTorrentsView.menu.advancedmenu"); //$NON-NLS-1$
    
    final Menu menuAdvanced = new Menu(getComposite().getShell(), SWT.DROP_DOWN);
    itemAdvanced.setMenu(menuAdvanced);
    
    //advanced > networks menu
    
    final MenuItem itemNetworks = new MenuItem(menuAdvanced,SWT.CASCADE);
    Messages.setLanguageText(itemNetworks, "MyTorrentsView.menu.networks"); //$NON-NLS-1$
    
    final Menu menuNetworks = new Menu(getComposite().getShell(), SWT.DROP_DOWN);
    itemNetworks.setMenu(menuNetworks);
    
    for (int i=0;i<AENetworkClassifier.AT_NETWORKS.length;i++){
		final String	nn = AENetworkClassifier.AT_NETWORKS[i];
		String	msg_text	= "ConfigView.section.connection.networks." + nn;
		final MenuItem itemNetwork = new MenuItem(menuNetworks,SWT.CHECK);
		itemNetwork.setData("network",nn);
		Messages.setLanguageText(itemNetwork, msg_text); //$NON-NLS-1$
		itemNetwork.addListener(SWT.Selection,new SelectedTableRowsListener() {
	      public void run(TableRowCore row) {
	        ((DownloadManager)row.getDataSource(true)).getDownloadState().setNetworkEnabled(nn,itemNetwork.getSelection());
	      }
	    });
    }
    
    //advanced > peer sources menu
    
    final MenuItem itemPeerSource = new MenuItem(menuAdvanced,SWT.CASCADE);
    Messages.setLanguageText(itemPeerSource, "MyTorrentsView.menu.peersource"); //$NON-NLS-1$
    
    final Menu menuPeerSource = new Menu(getComposite().getShell(), SWT.DROP_DOWN);
    itemPeerSource.setMenu(menuPeerSource);
    
    
    for (int i=0;i<PEPeerSource.PS_SOURCES.length;i++){
		
		final String	p = PEPeerSource.PS_SOURCES[i];
		String	msg_text	= "ConfigView.section.connection.peersource." + p;
		final MenuItem itemPS = new MenuItem(menuPeerSource,SWT.CHECK);
		itemPS.setData("peerSource",p);
		Messages.setLanguageText(itemPS, msg_text); //$NON-NLS-1$
		itemPS.addListener(SWT.Selection,new SelectedTableRowsListener() {
	      public void run(TableRowCore row) {
	        ((DownloadManager)row.getDataSource(true)).getDownloadState().setPeerSourceEnabled(p,itemPS.getSelection());
	      }
	    });
	}
    
    // advanced > export menu
    
    final MenuItem itemExport = new MenuItem(menuAdvanced, SWT.CASCADE);
    Messages.setLanguageText(itemExport, "MyTorrentsView.menu.exportmenu"); //$NON-NLS-1$
    Utils.setMenuItemImage(itemExport, "export");

    final Menu menuExport = new Menu(getComposite().getShell(), SWT.DROP_DOWN);
    itemExport.setMenu(menuExport);

    final MenuItem itemExportXML = new MenuItem(menuExport, SWT.PUSH);
    Messages.setLanguageText(itemExportXML, "MyTorrentsView.menu.export"); //$NON-NLS-1$    

    final MenuItem itemExportTorrent = new MenuItem(menuExport, SWT.PUSH);
    Messages.setLanguageText(itemExportTorrent, "MyTorrentsView.menu.exporttorrent"); //$NON-NLS-1$
 
    final MenuItem itemHost = new MenuItem(menu, SWT.PUSH);
    Messages.setLanguageText(itemHost, "MyTorrentsView.menu.host"); //$NON-NLS-1$
    Utils.setMenuItemImage(itemHost, "host");

    final MenuItem itemPublish = new MenuItem(menu, SWT.PUSH);
    Messages.setLanguageText(itemPublish, "MyTorrentsView.menu.publish"); //$NON-NLS-1$
    Utils.setMenuItemImage(itemPublish, "publish");

    new MenuItem(menu, SWT.SEPARATOR);

    final MenuItem itemMove = new MenuItem(menu, SWT.CASCADE);
    Messages.setLanguageText(itemMove, "MyTorrentsView.menu.move"); //$NON-NLS-1$
    Utils.setMenuItemImage(itemMove, "move");

    final Menu menuMove = new Menu(getComposite().getShell(), SWT.DROP_DOWN);
    itemMove.setMenu(menuMove);

    final MenuItem itemMoveTop = new MenuItem(menuMove, SWT.PUSH);
    Messages.setLanguageText(itemMoveTop, "MyTorrentsView.menu.moveTop"); //$NON-NLS-1$
    Utils.setMenuItemImage(itemMoveTop, "top");

    final MenuItem itemMoveUp = new MenuItem(menuMove, SWT.PUSH);
    Messages.setLanguageText(itemMoveUp, "MyTorrentsView.menu.moveUp"); //$NON-NLS-1$
    Utils.setMenuItemImage(itemMoveUp, "up");

    final MenuItem itemMoveDown = new MenuItem(menuMove, SWT.PUSH);
    Messages.setLanguageText(itemMoveDown, "MyTorrentsView.menu.moveDown"); //$NON-NLS-1$
    Utils.setMenuItemImage(itemMoveDown, "down");

    final MenuItem itemMoveEnd = new MenuItem(menuMove, SWT.PUSH);
    Messages.setLanguageText(itemMoveEnd, "MyTorrentsView.menu.moveEnd"); //$NON-NLS-1$
    Utils.setMenuItemImage(itemMoveEnd, "bottom");
    
    final MenuItem itemSpeed = new MenuItem(menuAdvanced, SWT.CASCADE);
    Messages.setLanguageText(itemSpeed, "MyTorrentsView.menu.setSpeed"); //$NON-NLS-1$
    Utils.setMenuItemImage(itemSpeed, "speed");

    final Menu menuSpeed = new Menu(getComposite().getShell(), SWT.DROP_DOWN);
    itemSpeed.setMenu(menuSpeed);

    final MenuItem itemCurrentSpeed = new MenuItem(menuSpeed,SWT.PUSH);
    itemCurrentSpeed.setEnabled(false);

    new MenuItem(menuSpeed,SWT.SEPARATOR);

    final MenuItem itemsSpeed[] = new MenuItem[12];
    Listener itemsSpeedListener = new Listener() {
      public void handleEvent(Event e) {
        if(e.widget != null && e.widget instanceof MenuItem) {
          MenuItem item = (MenuItem) e.widget;
          int speed = item.getData("maxul") == null ? 0 : ((Integer)item.getData("maxul")).intValue();
          setSelectedTorrentsSpeed(speed);
        }
      }
    };



    itemsSpeed[1] = new MenuItem(menuSpeed,SWT.PUSH);
    Messages.setLanguageText(itemsSpeed[1],"MyTorrentsView.menu.setSpeed.unlimit");
    itemsSpeed[1].setData("maxul", new Integer(0));    
    itemsSpeed[1].addListener(SWT.Selection,itemsSpeedListener);
    
    for(int i = 2 ; i < 12 ; i++) {
      itemsSpeed[i] = new MenuItem(menuSpeed,SWT.PUSH);      
      itemsSpeed[i].addListener(SWT.Selection,itemsSpeedListener);
    }
    
    
    /*  //TODO ensure that all limits combined don't go under the min 5kbs ?
    //Disable at the end of the list, thus the first item of the array is instanciated last.
    itemsSpeed[0] = new MenuItem(menuSpeed,SWT.PUSH);
    Messages.setLanguageText(itemsSpeed[0],"MyTorrentsView.menu.setSpeed.disable");
    itemsSpeed[0].setData("maxul", new Integer(-1));    
    itemsSpeed[0].addListener(SWT.Selection,itemsSpeedListener);
     */

    // Category

    menuCategory = new Menu(getComposite().getShell(), SWT.DROP_DOWN);
    final MenuItem itemCategory = new MenuItem(menu, SWT.CASCADE);
    Messages.setLanguageText(itemCategory, "MyTorrentsView.menu.setCategory"); //$NON-NLS-1$
    //itemCategory.setImage(ImageRepository.getImage("speed"));
    itemCategory.setMenu(menuCategory);

    addCategorySubMenu();

    // Tracker
    final Menu menuTracker = new Menu(getComposite().getShell(), SWT.DROP_DOWN);
    final MenuItem itemTracker = new MenuItem(menuAdvanced, SWT.CASCADE);
    Messages.setLanguageText(itemTracker, "MyTorrentsView.menu.tracker");
    itemTracker.setMenu(menuTracker);

    final MenuItem itemChangeTracker = new MenuItem(menuTracker, SWT.PUSH);
    Messages.setLanguageText(itemChangeTracker, "MyTorrentsView.menu.changeTracker"); //$NON-NLS-1$
    Utils.setMenuItemImage(itemChangeTracker, "add_tracker");

    final MenuItem itemEditTracker = new MenuItem(menuTracker, SWT.PUSH);
    Messages.setLanguageText(itemEditTracker, "MyTorrentsView.menu.editTracker"); //$NON-NLS-1$
    Utils.setMenuItemImage(itemEditTracker, "edit_trackers");

    final MenuItem itemManualUpdate = new MenuItem(menuTracker,SWT.PUSH);
    Messages.setLanguageText(itemManualUpdate, "GeneralView.label.trackerurlupdate"); //$NON-NLS-1$
    //itemManualUpdate.setImage(ImageRepository.getImage("edit_trackers"));

    new MenuItem(menu, SWT.SEPARATOR);

    final MenuItem itemQueue = new MenuItem(menu, SWT.PUSH);
    Messages.setLanguageText(itemQueue, "MyTorrentsView.menu.queue"); //$NON-NLS-1$
    Utils.setMenuItemImage(itemQueue, "start");

    final MenuItem itemForceStart = new MenuItem(menu, SWT.CHECK);
    Messages.setLanguageText(itemForceStart, "MyTorrentsView.menu.forceStart");
    Utils.setMenuItemImage(itemForceStart, "forcestart");

    final MenuItem itemStop = new MenuItem(menu, SWT.PUSH);
    Messages.setLanguageText(itemStop, "MyTorrentsView.menu.stop"); //$NON-NLS-1$
    Utils.setMenuItemImage(itemStop, "stop");

    final MenuItem itemRemove = new MenuItem(menu, SWT.PUSH);
    Messages.setLanguageText(itemRemove, "MyTorrentsView.menu.remove"); //$NON-NLS-1$
    Utils.setMenuItemImage(itemRemove, "delete");

    final MenuItem itemRemoveAnd = new MenuItem(menu, SWT.CASCADE);
    Messages.setLanguageText(itemRemoveAnd, "MyTorrentsView.menu.removeand"); //$NON-NLS-1$
    Utils.setMenuItemImage(itemRemoveAnd, "delete");

    final Menu menuRemove = new Menu(getComposite().getShell(), SWT.DROP_DOWN);
    itemRemoveAnd.setMenu(menuRemove);
    final MenuItem itemDeleteTorrent = new MenuItem(menuRemove, SWT.PUSH);
    Messages.setLanguageText(itemDeleteTorrent, "MyTorrentsView.menu.removeand.deletetorrent"); //$NON-NLS-1$
    final MenuItem itemDeleteData = new MenuItem(menuRemove, SWT.PUSH);
    Messages.setLanguageText(itemDeleteData, "MyTorrentsView.menu.removeand.deletedata");
    final MenuItem itemDeleteBoth = new MenuItem(menuRemove, SWT.PUSH);
    Messages.setLanguageText(itemDeleteBoth, "MyTorrentsView.menu.removeand.deleteboth");

    final MenuItem itemRecheck = new MenuItem(menu, SWT.PUSH);
    Messages.setLanguageText(itemRecheck, "MyTorrentsView.menu.recheck");
    Utils.setMenuItemImage(itemRecheck, "recheck");

    new MenuItem(menu, SWT.SEPARATOR);

    super.fillMenu(menu);

    menu.addListener(SWT.Show, new Listener() {
      public void handleEvent(Event e) {
        Object[] dms = getSelectedDataSources();
        boolean hasSelection = (dms.length > 0);

        itemDetails.setEnabled(hasSelection);

        itemOpen.setEnabled(hasSelection);
        itemExplore.setEnabled(hasSelection);
        itemExport.setEnabled(hasSelection);
        itemHost.setEnabled(hasSelection);
        itemPublish.setEnabled(hasSelection);

        itemMove.setEnabled(hasSelection);
        itemCategory.setEnabled(hasSelection);
        itemBar.setEnabled(hasSelection);

        itemManualUpdate.setEnabled(hasSelection);

        itemAdvanced.setEnabled(hasSelection);
        
        boolean bChangeDir = false;
        if (hasSelection) {
          bChangeDir = true;
          
          boolean moveUp, moveDown, start, stop, changeUrl, barsOpened,
                  forceStart, forceStartEnabled, recheck, manualUpdate, changeSpeed;
          
          moveUp = moveDown = changeUrl = barsOpened = manualUpdate = changeSpeed = true;
          
          forceStart = forceStartEnabled = recheck =  start = stop = false;
         
          long totalSpeed = 0;
          
          boolean speedUnlimited = false;
          
          boolean speedDisabled = false;
          
          MenuItem itemsNetwork[] = menuNetworks.getItems();
          for(int j = 0 ; j < itemsNetwork.length ; j++) {
            itemsNetwork[j].setSelection(true);
          }
          
          MenuItem itemsPeersource[] = menuPeerSource.getItems();
          for(int j = 0 ; j < itemsPeersource.length ; j++) {
            itemsPeersource[j].setSelection(true);
          }
          
          for (int i = 0; i < dms.length; i++) {
            DownloadManager dm = (DownloadManager)dms[i];
            
            try {
              int maxul = dm.getStats().getUploadRateLimitBytesPerSecond();              
              if(maxul == 0) {speedUnlimited = true; }
              if(maxul == -1) { maxul = 0; speedDisabled = true; }              
              totalSpeed += maxul;
            } catch(NullPointerException ex) {
              changeSpeed  = false;;
            } catch (Exception ex) {
            	Debug.printStackTrace( ex );
            }
            
            if (dm.getTrackerClient() == null){
              changeUrl = false;
            }
            
            if (!downloadBars.containsKey(dm)){
              barsOpened = false;
            }

            stop 	= stop || ManagerUtils.isStopable(dm);
            
            start 	= start || ManagerUtils.isStartable(dm);
           
            recheck = recheck || dm.canForceRecheck();
   
            forceStartEnabled = forceStartEnabled || ManagerUtils.isForceStartable(dm);
            	         
            forceStart = forceStart || dm.isForceStart();
  
            if (!dm.isMoveableDown()){
              moveDown = false;
            }
            
            if (!dm.isMoveableUp()){
              moveUp = false;
            }
            
            TRTrackerAnnouncer trackerClient = dm.getTrackerClient();
            
            if(trackerClient != null) {
              boolean update_state = ((SystemTime.getCurrentTime()/1000 - trackerClient.getLastUpdateTime() >= TRTrackerAnnouncer.REFRESH_MINIMUM_SECS ));
              manualUpdate = manualUpdate & update_state;
            }
           
            bChangeDir &= (dm.getState() == DownloadManager.STATE_ERROR && !dm.filesExist());
                          
            for(int j=0;j<itemsNetwork.length;j++) {
              String network = (String) itemsNetwork[j].getData("network");
              if(! dm.getDownloadState().isNetworkEnabled(network)) {
                itemsNetwork[j].setSelection(false);
              }
            }
            
            for(int j=0;j<itemsPeersource.length;j++) {
              String ps = (String) itemsPeersource[j].getData("peerSource");
              if(! dm.getDownloadState().isPeerSourceEnabled(ps)) {
                itemsPeersource[j].setSelection(false);
              }
            }            
          }
          
          //itemCurrentSpeed.setText((float) ((int) (totalSpeed * 1000)) / 10 + " %");  //TODO
          
          itemBar.setSelection(barsOpened);

          itemMoveTop.setEnabled(moveUp);
          itemMoveEnd.setEnabled(moveDown);

          itemForceStart.setSelection(forceStart);
          itemForceStart.setEnabled(forceStartEnabled);
          itemQueue.setEnabled(start);
          itemStop.setEnabled(stop);
          itemRemove.setEnabled( true );
          itemRemoveAnd.setEnabled( true );

          StringBuffer speedText = new StringBuffer();
          String separator = "";
          //itemSpeed.                   
          if(speedDisabled) {
            speedText.append(MessageText.getString("MyTorrentsView.menu.setSpeed.disabled"));
            separator = " / ";
          }
          if(speedUnlimited) {
            speedText.append(separator);
            speedText.append(MessageText.getString("MyTorrentsView.menu.setSpeed.unlimited"));
            separator = " / ";
          }                    
          if(totalSpeed > 0) {
            speedText.append(separator);
            speedText.append(DisplayFormatters.formatByteCountToKiBEtcPerSec(totalSpeed));
          }                              
          itemCurrentSpeed.setText(speedText.toString());          
          
          
          int maxUpload = COConfigurationManager.getIntParameter("Max Upload Speed KBs",0) * 1024;
          //using 75KiB/s as the default limit when no limit set.
          if(maxUpload == 0) maxUpload = 75 * 1024;
          
          
          if(dms.length > 0) {
	          for(int i = 2 ; i < 12 ; i++) {
	            int limit = maxUpload / (10 * dms.length) * (12 - i);
	            StringBuffer speed = new StringBuffer();
	            speed.append(DisplayFormatters.formatByteCountToKiBEtcPerSec(limit * dms.length));
	            if(dms.length > 1) {
	              speed.append(" ");
	              speed.append(MessageText.getString("MyTorrentsView.menu.setSpeed.in"));
	              speed.append(" ");
	              speed.append(dms.length);
	              speed.append(" ");
	              speed.append(MessageText.getString("MyTorrentsView.menu.setSpeed.slots"));
	              speed.append(" ");
	              speed.append( DisplayFormatters.formatByteCountToKiBEtcPerSec(limit));	            
	            }
	           
	            itemsSpeed[i].setText(speed.toString());
	            itemsSpeed[i].setData("maxul", new Integer(limit));
	          }
          }
                    
          itemEditTracker.setEnabled(true);
          itemChangeTracker.setEnabled(changeUrl);
          itemRecheck.setEnabled(recheck);

          itemManualUpdate.setEnabled(manualUpdate);

        } else {
          itemBar.setSelection(false);

          itemForceStart.setEnabled(false);
          itemForceStart.setSelection(false);
          itemQueue.setEnabled(false);
          itemStop.setEnabled(false);
          itemRemove.setEnabled(false);
          itemRemoveAnd.setEnabled(false);

          itemEditTracker.setEnabled(false);
          itemChangeTracker.setEnabled(false);
          itemRecheck.setEnabled(false);
        }

        if (menuItemChangeDir != null && !menuItemChangeDir.isDisposed()) {
          menuItemChangeDir.dispose();
        }
        if (bChangeDir) {
          menuItemChangeDir = new MenuItem(menu, SWT.PUSH, 0);
          Messages.setLanguageText(menuItemChangeDir, "MyTorrentsView.menu.changeDirectory");
          menuItemChangeDir.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event e) {
              changeDirSelectedTorrents();
            }
          });
        }
      }
    });

    itemQueue.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event e) {
        queueSelectedTorrents();
      }
    });

    itemStop.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event e) {
        stopSelectedTorrents();
      }
    });

    itemRemove.addListener(SWT.Selection,
                           new SelectedTableRowsListener() {
      public void run(TableRowCore row) {
        removeTorrent((DownloadManager)row.getDataSource(true), false, false);
      }
    });

    itemDeleteTorrent.addListener(SWT.Selection,
                                  new SelectedTableRowsListener() {
      public void run(TableRowCore row) {
        removeTorrent((DownloadManager)row.getDataSource(true), true, false);
      }
    });

    itemDeleteData.addListener(SWT.Selection,
                               new SelectedTableRowsListener() {
      public void run(TableRowCore row) {
        removeTorrent((DownloadManager)row.getDataSource(true), false, true);
      }
    });

    itemDeleteBoth.addListener(SWT.Selection,
                               new SelectedTableRowsListener() {
      public void run(TableRowCore row) {
        removeTorrent((DownloadManager)row.getDataSource(true), true, true);
      }
    });

    itemChangeTracker.addListener(SWT.Selection,
                                  new SelectedTableRowsListener() {
      public void run(TableRowCore row) {
        TRTrackerAnnouncer tc = ((DownloadManager)row.getDataSource(true)).getTrackerClient();
        if (tc != null)
          new TrackerChangerWindow(MainWindow.getWindow().getDisplay(), tc);
      }
    });

    itemEditTracker.addListener(SWT.Selection,
                                new SelectedTableRowsListener() {
      public void run(TableRowCore row) {
        final DownloadManager dm = (DownloadManager)row.getDataSource(true);
        if (dm.getTorrent() != null) {
          final TOTorrent torrent = dm.getTorrent();

          java.util.List group = TorrentUtils.announceGroupsToList(torrent);

          new MultiTrackerEditor(null, group, new TrackerEditorListener() {
            public void trackersChanged(String str, String str2, 
                                        java.util.List group) {
              TorrentUtils.listToAnnounceGroups(group, torrent);

              try {
                TorrentUtils.writeToFile(torrent);
              } catch(Throwable e) {
              	Debug.printStackTrace( e );
              }

              if (dm.getTrackerClient() != null)
                dm.getTrackerClient().resetTrackerUrl( true );
            }
          }, true);
        }
      } // run
    }); 


    itemManualUpdate.addListener(SWT.Selection, 
                                 new SelectedTableRowsListener() {
      public void run(TableRowCore row) {
        ((DownloadManager)row.getDataSource(true)).checkTracker();
      }
    });

    itemDetails.addListener(SWT.Selection,
                            new SelectedTableRowsListener() {
      public void run(TableRowCore row) {
        MainWindow.getWindow().openManagerView((DownloadManager)row.getDataSource(true));
      }
    });



    itemOpen.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event event) {
        runSelectedTorrents();
      }
    });
    
    itemExplore.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event event) {
        openSelectedTorrents();
      }
    });

    itemExportXML.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event event) {
        DownloadManager dm = (DownloadManager)getFirstSelectedDataSource();
        if (dm != null)
          new ExportTorrentWizard(azureus_core, itemExportXML.getDisplay(), dm);
      }
    });

    itemExportTorrent.addListener(SWT.Selection, new Listener() {
        public void handleEvent(Event event) {
          DownloadManager dm = (DownloadManager)getFirstSelectedDataSource();
          if (dm != null){
			FileDialog fd = new FileDialog(getComposite().getShell());
			
			fd.setFileName( dm.getTorrentFileName());
						
			String path = fd.open();
			
			if( path != null ){
				
				try{
					File	target = new File( path );
					
						// first copy the torrent - DON'T use "writeTorrent" as this amends the
						// "filename" field in the torrent
					
					TorrentUtils.copyToFile( dm.getDownloadState().getTorrent(), target );
					
						// now remove the non-standard entries
					
					TOTorrent	dest = TOTorrentFactory.deserialiseFromBEncodedFile( target );
					
					dest.removeAdditionalProperties();
					
					dest.serialiseToBEncodedFile( target );
					
				}catch( Throwable  e){
					
					LGLogger.logRepeatableAlert( "Torrent export failed", e );
				}
	
			}    
          }
        }
      });
    itemHost.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event event) {
        hostSelectedTorrents();
      }
    });

    itemPublish.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event event) {
        publishSelectedTorrents();
      }
    });

    itemBar.addListener(SWT.Selection,
                        new SelectedTableRowsListener() {
      public void run(TableRowCore row) {
        DownloadManager dm = (DownloadManager)row.getDataSource(true);
        try{
        	downloadBars_mon.enter();
        
          if (downloadBars.containsKey(dm)) {
            MinimizedWindow mw = (MinimizedWindow) downloadBars.remove(dm);
            mw.close();
          } else {
            MinimizedWindow mw = new MinimizedWindow(dm, cTablePanel.getShell());
            downloadBars.put(dm, mw);
          }
        }finally{
        	
        	downloadBars_mon.exit();
        }
      } // run
    });

    itemMoveDown.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event event) {
        moveSelectedTorrentsDown();
      }
    });

    itemMoveUp.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event event) {
        moveSelectedTorrentsUp();
      }
    });

    itemMoveTop.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event event) {
        moveSelectedTorrentsTop();
      }
    });

    itemMoveEnd.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event event) {
        moveSelectedTorrentsEnd();
      }
    });

    itemForceStart.addListener(SWT.Selection,
                         new SelectedTableRowsListener() {
      public void run(TableRowCore row) {
      	DownloadManager dm = (DownloadManager)row.getDataSource(true);
      	
      	if ( ManagerUtils.isForceStartable( dm )){
      		dm.setForceStart(itemForceStart.getSelection());
      	}
      }
    });

    itemRecheck.addListener(SWT.Selection,
                         new SelectedTableRowsListener() {
      public void run(TableRowCore row) {
     	DownloadManager dm = (DownloadManager)row.getDataSource(true);
     	 
     	if ( dm.canForceRecheck()){
     		
     		dm.forceRecheck();
     	}
      }
    });

  } // fillMenu

  private void addCategorySubMenu() {
    MenuItem[] items = menuCategory.getItems();
    int i;
    for (i = 0; i < items.length; i++) {
      items[i].dispose();
    }

    Category[] categories = CategoryManager.getCategories();
    Arrays.sort(categories);

    if (categories.length > 0) {
      Category catUncat = CategoryManager.getCategory(Category.TYPE_UNCATEGORIZED);
      if (catUncat != null) {
        final MenuItem itemCategory = new MenuItem(menuCategory, SWT.PUSH);
        Messages.setLanguageText(itemCategory, catUncat.getName());
        itemCategory.setData("Category", catUncat);
        itemCategory.addListener(SWT.Selection, new Listener() {
          public void handleEvent(Event event) {
            MenuItem item = (MenuItem)event.widget;
            assignSelectedToCategory((Category)item.getData("Category"));
          }
        });

        new MenuItem(menuCategory, SWT.SEPARATOR);
      }

      for (i = 0; i < categories.length; i++) {
        if (categories[i].getType() == Category.TYPE_USER) {
          final MenuItem itemCategory = new MenuItem(menuCategory, SWT.PUSH);
          itemCategory.setText(categories[i].getName());
          itemCategory.setData("Category", categories[i]);

          itemCategory.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
              MenuItem item = (MenuItem)event.widget;
              assignSelectedToCategory((Category)item.getData("Category"));
            }
          });
        }
      }

      new MenuItem(menuCategory, SWT.SEPARATOR);
    }

    final MenuItem itemAddCategory = new MenuItem(menuCategory, SWT.PUSH);
    Messages.setLanguageText(itemAddCategory,
                             "MyTorrentsView.menu.setCategory.add");

    itemAddCategory.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event event) {
        addCategory();
      }
    });

  }

  /* SubMenu for column specific tasks.
   */
  public void addThisColumnSubMenu(String sColumnName, Menu menuThisColumn) {
    final Table table = getTable();

    if (sColumnName.equals("health")) {
      MenuItem item = new MenuItem(menuThisColumn, SWT.PUSH);
      Messages.setLanguageText(item, "MyTorrentsView.menu.health");
      Utils.setMenuItemImage(item, "st_explain");
      item.addListener(SWT.Selection, new Listener() {
        public void handleEvent(Event e) {
          HealthHelpWindow.show(table.getDisplay());
        }
      });

    } else if (sColumnName.equals("maxuploads")) {
      int iStart = COConfigurationManager.getIntParameter("Max Uploads") - 2;
      if (iStart < 2) iStart = 2;
      for (int i = iStart; i < iStart + 6; i++) {
        MenuItem item = new MenuItem(menuThisColumn, SWT.PUSH);
        item.setText(String.valueOf(i));
        item.setData("MaxUploads", new Long(i));
        item.addListener(SWT.Selection,
                         new SelectedTableRowsListener() {
          public void run(TableRowCore row) {
            DownloadManager dm = (DownloadManager)row.getDataSource(true);
            MenuItem item = (MenuItem)event.widget;
            if (item != null) {
              int value = ((Long)item.getData("MaxUploads")).intValue();
              dm.getStats().setMaxUploads(value);
            }
          } // run
        }); // listener
      } // for
    }
  }

  private void createDragDrop() {
    Transfer[] types = new Transfer[] { TextTransfer.getInstance()};

    DragSource dragSource = new DragSource(getTable(), DND.DROP_MOVE);
    dragSource.setTransfer(types);
    dragSource.addDragListener(new DragSourceAdapter() {
      public void dragStart(DragSourceEvent event) {
        Table table = getTable();
        if (table.getSelectionCount() != 0 &&
           table.getSelectionCount() != table.getItemCount())
        {
          event.doit = true;
          drag_drop_line_start = table.getSelectionIndex();
         } else {
          event.doit = false;
          drag_drop_line_start = -1;
        }
      }
    });

    DropTarget dropTarget = new DropTarget(getTable(),
                                           DND.DROP_DEFAULT | DND.DROP_MOVE |
                                           DND.DROP_COPY | DND.DROP_LINK |
                                           DND.DROP_TARGET_MOVE);
    dropTarget.setTransfer(new Transfer[] { URLTransfer.getInstance(),
                                            FileTransfer.getInstance(),
                                            TextTransfer.getInstance()});
    dropTarget.addDropListener(new DropTargetAdapter() {
/*
      public void dragEnter(DropTargetEvent event) {
        System.out.print("dragEnter typ id: " + event.currentDataType.type + ",types count: " + event.dataTypes.length + " = ");
        for (int i = 0; i < event.dataTypes.length; i++) {
          System.out.print(event.dataTypes[i].type + ",");
        }
        System.out.println();
      }
      public void dropAccept(DropTargetEvent event) {
        System.out.print("dropAccept typ id: " + event.currentDataType.type + ",types count: " + event.dataTypes.length + " = ");
        for (int i = 0; i < event.dataTypes.length; i++) {
          System.out.print(event.dataTypes[i].type + ",");
        }
        System.out.println();
      }
      public void dragOperationChanged(DropTargetEvent event) {
        System.out.print("dragOperationChanged typ id: " + event.currentDataType.type + ",types count: " + event.dataTypes.length + " = ");
        for (int i = 0; i < event.dataTypes.length; i++) {
          System.out.print(event.dataTypes[i].type + ",");
        }
        System.out.println();
      }
      public void dragLeave(DropTargetEvent event) {
        System.out.print("dragLeave typ id: " + event.currentDataType.type + ",types count: " + event.dataTypes.length + " = ");
        for (int i = 0; i < event.dataTypes.length; i++) {
          System.out.print(event.dataTypes[i].type + ",");
        }
        System.out.println();
      }
//*/
      public void dragOver(DropTargetEvent event) {
/*
        System.out.print("dragOver typ id: " + event.currentDataType.type + ",types count: " + event.dataTypes.length + " = ");
        for (int i = 0; i < event.dataTypes.length; i++) {
          System.out.print(event.dataTypes[i].type + ",");
        }
        System.out.println();
//*/
        if(drag_drop_line_start < 0) {
          if(event.detail != DND.DROP_COPY)
            event.detail = DND.DROP_LINK;
        } else if(TextTransfer.getInstance().isSupportedType(event.currentDataType)) {
          event.feedback = DND.FEEDBACK_EXPAND | DND.FEEDBACK_SCROLL | DND.FEEDBACK_SELECT | DND.FEEDBACK_INSERT_BEFORE | DND.FEEDBACK_INSERT_AFTER;
          event.detail = event.item == null ? DND.DROP_NONE : DND.DROP_MOVE;
        }
      }
      public void drop(DropTargetEvent event) {
        // Torrent file from shell dropped
        if(drag_drop_line_start >= 0) { // event.data == null
          event.detail = DND.DROP_NONE;
          if(event.item == null)
            return;
          int drag_drop_line_end = getTable().indexOf((TableItem)event.item);
          moveSelectedTorrents(drag_drop_line_start, drag_drop_line_end);
          drag_drop_line_start = -1;
        } else {
          TorrentOpener.openDroppedTorrents(azureus_core, event);
        }
      }
    });
  }

  private void moveSelectedTorrents(int drag_drop_line_start, int drag_drop_line_end) {
    if (drag_drop_line_end == drag_drop_line_start)
      return;

    java.util.List list = getSelectedRowsList();
    if (list.size() == 0)
      return;

    TableItem ti = getTable().getItem(drag_drop_line_end);
    TableRowCore row = (TableRowCore)ti.getData("TableRow");
    DownloadManager dm = (DownloadManager)row.getDataSource(true);
    
    int iNewPos = dm.getPosition();
    for (Iterator iter = list.iterator(); iter.hasNext();) {
      row = (TableRowCore)iter.next();
      dm = (DownloadManager)row.getDataSource(true);
      int iOldPos = dm.getPosition();
      
      globalManager.moveTo(dm, iNewPos);
      if (sorter.isAscending()) {
        if (iOldPos > iNewPos)
          iNewPos++;
      } else {
        if (iOldPos < iNewPos)
          iNewPos--;
      }
    }

    if (sorter.getLastField().equals("#"))
      sorter.sortColumn(true);
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IView#refresh()
   */
  public void refresh() {
    if (getComposite() == null || getComposite().isDisposed())
      return;

    computePossibleActions();
    MainWindow.getWindow().refreshIconBar();

    super.refresh();
  }


  public void delete() {
    super.delete();

    if (fontButton != null && !fontButton.isDisposed()) {
      fontButton.dispose();
      fontButton = null;
    }
    CategoryManager.removeCategoryManagerListener(this);
    globalManager.removeListener(this);
    COConfigurationManager.removeParameterListener("Confirm Data Delete", this);
  }

  private KeyListener createKeyListener() {
    return new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
/*
        String string = "stateMask=0x" + Integer.toHexString(e.stateMask);
        if ((e.stateMask & SWT.CTRL) != 0)
          string += " CTRL";
        if ((e.stateMask & SWT.ALT) != 0)
          string += " ALT";
        if ((e.stateMask & SWT.SHIFT) != 0)
          string += " SHIFT";
        if ((e.stateMask & SWT.COMMAND) != 0)
          string += " COMMAND";
        string += ", keyCode=0x" + Integer.toHexString(e.keyCode) + "=" + e.keyCode;
        string += ", character=0x" + Integer.toHexString(e.character);
        switch (e.character) {
          case 0 :
            string += " '\\0'";
            break;
          case SWT.BS :
            string += " '\\b'";
            break;
          case SWT.CR :
            string += " '\\r'";
            break;
          case SWT.DEL :
            string += " DEL";
            break;
          case SWT.ESC :
            string += " ESC";
            break;
          case SWT.LF :
            string += " '\\n'";
            break;
          case SWT.TAB :
            string += " '\\t'";
            break;
          default :
            string += " '" + e.character + "'";
            break;
        }
        System.out.println(string);
//*/
        if (e.stateMask == (SWT.CTRL|SWT.SHIFT)) {
          // CTRL+SHIFT+S stop all Torrents
          if(e.character == 0x13)
            globalManager.stopAllDownloads();
        } else if (e.stateMask == SWT.CTRL) {
          // CTRL+CURSOR DOWN move selected Torrents one down
          if(e.keyCode == 0x1000001)
            moveSelectedTorrentsUp();
          // CTRL+CURSOR UP move selected Torrents one up
          else if(e.keyCode == 0x1000002)
            moveSelectedTorrentsDown();
          // CTRL+HOME move selected Torrents to top
          else if(e.keyCode == 0x1000007)
            moveSelectedTorrentsTop();
          // CTRL+END move selected Torrents to end
          else if(e.keyCode == 0x1000008)
            moveSelectedTorrentsEnd();
          // CTRL+A select all Torrents
          else if(e.character == 0x1)
            getTable().selectAll();
          else if(e.character == 0x3) {
            clipboardSelected();
          // CTRL+R resume/start selected Torrents
          } else if(e.character == 0x12)
            resumeSelectedTorrents();
          // CTRL+S stop selected Torrents
          else if(e.character == 0x13)
            stopSelectedTorrents();
        } else if(e.stateMask == 0) {
          // DEL remove selected Torrents
          if(e.keyCode == 127) {
            removeSelectedTorrents();
          } else {
            // normal character: jump to next item with a name beginning with this character
            TableItem[] items = getTable().getSelection();
            int lastSelectedIndex = items.length == 0 ? -1 : getTable().indexOf(items[items.length-1]);
            int nextIndex = globalManager.getNextIndexForCharacter(e.character, lastSelectedIndex);
            if (nextIndex >= 0)
              getTable().setSelection(nextIndex);
          }
        }
      }
    };
  }

  private void changeDirSelectedTorrents() {
    Object[] dataSources = getSelectedDataSources();
    if (dataSources.length <= 0)
      return;

    String sDefPath = COConfigurationManager.getBooleanParameter("Use default data dir") ?
                      COConfigurationManager.getStringParameter("Default save path", "") :
                      "";
    
    if ( sDefPath.length() > 0 ){
	    File	f = new File(sDefPath);
	    
	    if ( !f.exists()){
	    	f.mkdirs();
	    }
    }
    
    DirectoryDialog dDialog = new DirectoryDialog(cTablePanel.getShell(),
                                                  SWT.SYSTEM_MODAL);
    dDialog.setFilterPath(sDefPath);
    dDialog.setMessage(MessageText.getString("MainWindow.dialog.choose.savepath"));
    String sSavePath = dDialog.open();
    if (sSavePath != null) {
      for (int i = 0; i < dataSources.length; i++) {
        DownloadManager dm = (DownloadManager)dataSources[i];
        if (dm.getState() == DownloadManager.STATE_ERROR ){
        	
            dm.setTorrentSaveDir(sSavePath);
        	
            if ( dm.filesExist()) {
            	dm.setState(DownloadManager.STATE_STOPPED);
            	ManagerUtils.queue(dm, cTablePanel);
            }
        }
      }
    }
  }

  private void removeTorrent(DownloadManager dm, boolean bDeleteTorrent, boolean bDeleteData) {
    
    if( COConfigurationManager.getBooleanParameter( "confirm_torrent_removal" ) ) {
    	
      MessageBox mb = new MessageBox(cTablePanel.getShell(), SWT.ICON_WARNING | SWT.YES | SWT.NO);
      
      mb.setText(MessageText.getString("deletedata.title"));
      
      mb.setMessage(MessageText.getString("deletetorrent.message1")
            + dm.getDisplayName() + " :\n"
            + dm.getTorrentFileName()
            + MessageText.getString("deletetorrent.message2"));
      
      if( mb.open() == SWT.NO ) {
        return;
      }
    }
    
    int choice;
    if (confirmDataDelete && bDeleteData) {
      String path = dm.getTorrentSaveDirAndFile();
      
      MessageBox mb = new MessageBox(cTablePanel.getShell(), SWT.ICON_WARNING | SWT.YES | SWT.NO);
      
      mb.setText(MessageText.getString("deletedata.title"));
      
      mb.setMessage(MessageText.getString("deletedata.message1")
          + dm.getDisplayName() + " :\n"
          + path
          + MessageText.getString("deletedata.message2"));

      choice = mb.open();
    } else {
      choice = SWT.YES;
    }

    if (choice == SWT.YES) {
      try {
        dm.stopIt( DownloadManager.STATE_STOPPED, bDeleteTorrent, bDeleteData );
        dm.getGlobalManager().removeDownloadManager( dm );
      }
      catch (GlobalManagerDownloadRemovalVetoException f) {
        Alerts.showErrorMessageBoxUsingResourceString("globalmanager.download.remove.veto", f);
      }
      catch (Exception ex) {
        Debug.printStackTrace( ex );
      }
    }
  }

  private void removeSelectedTorrents() {
    runForSelectedRows(new GroupTableRowRunner() {
      public void run(TableRowCore row) {
        removeTorrent((DownloadManager)row.getDataSource(true), false, false);
      }
    });
  }

  private void stopSelectedTorrents() {
    runForSelectedRows(new GroupTableRowRunner() {
      public void run(TableRowCore row) {
        ManagerUtils.stop((DownloadManager)row.getDataSource(true), cTablePanel);
      }
    });
  }

  private void queueSelectedTorrents() {
    runForSelectedRows(new GroupTableRowRunner() {
      public void run(TableRowCore row) {
        ManagerUtils.queue((DownloadManager)row.getDataSource(true), cTablePanel);
      }
    });
  }

  private void resumeSelectedTorrents() {
    runForSelectedRows(new GroupTableRowRunner() {
      public void run(TableRowCore row) {
        ManagerUtils.start((DownloadManager)row.getDataSource(true));
      }
    });
  }

  private void hostSelectedTorrents() {
    runForSelectedRows(new GroupTableRowRunner() {
      public void run(TableRowCore row) {
        ManagerUtils.host(azureus_core, (DownloadManager)row.getDataSource(true), cTablePanel);
      }
    });
    MainWindow.getWindow().showMyTracker();
  }

  private void publishSelectedTorrents() {
    runForSelectedRows(new GroupTableRowRunner() {
      public void run(TableRowCore row) {
        ManagerUtils.publish(azureus_core, (DownloadManager)row.getDataSource(true), cTablePanel);
      }
    });
    MainWindow.getWindow().showMyTracker();
  }

  // Note: This only runs the first selected torrent!
  private void runSelectedTorrents() {
    Object[] dataSources = getSelectedDataSources();
    for (int i = dataSources.length - 1; i >= 0; i--) {
      DownloadManager dm = (DownloadManager)dataSources[i];
      if (dm != null) {
        ManagerUtils.run(dm);
      }
    }
  }
  
  //Note: This only opens the first selected torrent!
  private void openSelectedTorrents() {
    Object[] dataSources = getSelectedDataSources();
    for (int i = dataSources.length - 1; i >= 0; i--) {
      DownloadManager dm = (DownloadManager)dataSources[i];
      if (dm != null) {
        ManagerUtils.open(dm);
      }
    }
  }

  private void moveSelectedTorrentsDown() {
    // Don't use runForSelectDataSources to ensure the order we want
    Object[] dataSources = getSelectedDataSources();
    Arrays.sort(dataSources, new Comparator() {
      public int compare (Object a, Object b) {
        return ((DownloadManager)a).getPosition() - ((DownloadManager)b).getPosition();
      }
    });
    for (int i = dataSources.length - 1; i >= 0; i--) {
      DownloadManager dm = (DownloadManager)dataSources[i];
      if (dm.isMoveableDown()) {
        dm.moveDown();
      }
    }

    if (sorter.getLastField().equals("#"))
      sorter.sortColumn(true);
  }

  private void moveSelectedTorrentsUp() {
    // Don't use runForSelectDataSources to ensure the order we want
    Object[] dataSources = getSelectedDataSources();
    Arrays.sort(dataSources, new Comparator() {
      public int compare (Object a, Object b) {
        return ((DownloadManager)a).getPosition() - ((DownloadManager)b).getPosition();
      }
    });
    for (int i = 0; i < dataSources.length; i++) {
      DownloadManager dm = (DownloadManager)dataSources[i];
      if (dm.isMoveableUp()) {
        dm.moveUp();
      }
    }

    if (sorter.getLastField().equals("#"))
      sorter.sortColumn(true);
  }

  private void moveSelectedTorrentsTop() {
    moveSelectedTorrentsTopOrEnd(true);
  }

  private void moveSelectedTorrentsEnd() {
    moveSelectedTorrentsTopOrEnd(false);
  }

  private void moveSelectedTorrentsTopOrEnd(boolean moveToTop) {
    DownloadManager[] downloadManagers = (DownloadManager[])getSelectedDataSources(new DownloadManager[0]);
    if (downloadManagers.length == 0)
      return;
    if(moveToTop)
      globalManager.moveTop(downloadManagers);
    else
      globalManager.moveEnd(downloadManagers);
    if (sorter.getLastField().equals("#"))
      sorter.sortColumn(true);
  }

  /**
   * @param parameterName the name of the parameter that has changed
   * @see org.gudy.azureus2.core3.config.ParameterListener#parameterChanged(java.lang.String)
   */
  public void parameterChanged(String parameterName) {
    super.parameterChanged(parameterName);
    confirmDataDelete = COConfigurationManager.getBooleanParameter("Confirm Data Delete", true);
  }

  private boolean top,bottom,up,down,run,host,publish,start,stop,remove;

  private void computePossibleActions() {
    Object[] dataSources = getSelectedDataSources();
    // enable up and down so that we can do the "selection rotate trick"
    up = down = run = host = publish = remove = (dataSources.length > 0);
    top = bottom = start = stop = false;
    for (int i = 0; i < dataSources.length; i++) {
      DownloadManager dm = (DownloadManager)dataSources[i];

      if(!start && ManagerUtils.isStartable(dm))
        start =  true;
      if(!stop && ManagerUtils.isStopable(dm))
        stop = true;
      if(!top && dm.isMoveableUp())
        top = true;
      if(!bottom && dm.isMoveableDown())
        bottom = true;
    }
  }

  public boolean isEnabled(String itemKey) {
    if(itemKey.equals("run"))
      return run;
    if(itemKey.equals("host"))
      return host;
    if(itemKey.equals("publish"))
      return publish;
    if(itemKey.equals("start"))
      return start;
    if(itemKey.equals("stop"))
      return stop;
    if(itemKey.equals("remove"))
      return remove;
    if(itemKey.equals("top"))
      return top;
    if(itemKey.equals("bottom"))
      return bottom;
    if(itemKey.equals("up"))
      return up;
    if(itemKey.equals("down"))
      return down;
    return false;
  }

  public void itemActivated(String itemKey) {
    if(itemKey.equals("top")) {
      moveSelectedTorrentsTop();
      return;
    }
    if(itemKey.equals("bottom")){
      moveSelectedTorrentsEnd();
      return;
    }
    if(itemKey.equals("up")) {
      moveSelectedTorrentsUp();
      return;
    }
    if(itemKey.equals("down")){
      moveSelectedTorrentsDown();
      return;
    }
    if(itemKey.equals("run")){
      runSelectedTorrents();
      return;
    }
    if(itemKey.equals("host")){
      hostSelectedTorrents();
      return;
    }
    if(itemKey.equals("publish")){
      publishSelectedTorrents();
      return;
    }
    if(itemKey.equals("start")){
      queueSelectedTorrents();
      return;
    }
    if(itemKey.equals("stop")){
      stopSelectedTorrents();
      return;
    }
    if(itemKey.equals("remove")){
      removeSelectedTorrents();
      return;
    }
  }



  public void  removeDownloadBar(DownloadManager dm) {
    try{
    	downloadBars_mon.enter();
    
    	downloadBars.remove(dm);
    }finally{
    	
    	downloadBars_mon.exit();
    }
  }

  private Category addCategory() {
    CategoryAdderWindow adderWindow = new CategoryAdderWindow(MainWindow.getWindow().getDisplay());
    Category newCategory = adderWindow.getNewCategory();
    if (newCategory != null)
      assignSelectedToCategory(newCategory);
    return newCategory;
  }

  // categorymanagerlistener Functions
  public void downloadManagerAdded(Category category, final DownloadManager manager)
  {
    boolean bCompleted = manager.getStats().getDownloadCompleted(false) == 1000;
    if ((bCompleted && isSeedingView) || (!bCompleted && !isSeedingView)) {
      addDataSource(manager);
    }
  }

  public void downloadManagerRemoved(Category category, DownloadManager removed)
  {
    removeDataSource(removed);
  }


  // DownloadManagerListener Functions
  public void stateChanged(DownloadManager manager, int state) {
  }

  public void positionChanged(DownloadManager download, int oldPosition, int newPosition) {
  }
  
  public void completionChanged(final DownloadManager manager, boolean bCompleted) {
    // manager has moved lists
    if ((isSeedingView && bCompleted) || (!isSeedingView && !bCompleted)) {
      addDataSource(manager);
    } else if ((isSeedingView && !bCompleted) || (!isSeedingView && bCompleted)) {
      removeDataSource(manager);
    }
  }

  public void downloadComplete(DownloadManager manager) {
  }

  // Category Stuff
  private void assignSelectedToCategory(final Category category) {
    runForSelectedRows(new GroupTableRowRunner() {
      public void run(TableRowCore row) {
        ((DownloadManager)row.getDataSource(true)).getDownloadState().setCategory(category);
      }
    });
  }

  private void activateCategory(Category category) {
    if (currentCategory != null)
      currentCategory.removeCategoryListener(this);
    if (category != null)
      category.addCategoryListener(this);

    currentCategory = category;

    int catType = (currentCategory == null) ? Category.TYPE_ALL : currentCategory.getType();
    java.util.List managers;
    if (catType == Category.TYPE_USER)
      managers = currentCategory.getDownloadManagers();
    else
      managers = globalManager.getDownloadManagers();

    removeAllTableRows();

    // add new
    if (catType == Category.TYPE_UNCATEGORIZED) {
      for (int i = 0; i < managers.size(); i++) {
        DownloadManager manager = (DownloadManager)managers.get(i);
        if (manager.getDownloadState().getCategory() == null)
          downloadManagerAdded(currentCategory, manager);
      }
    } else {
      for (int i = 0; i < managers.size(); i++) {
        downloadManagerAdded(currentCategory, (DownloadManager)managers.get(i));
      }
    }
  }


  // CategoryManagerListener Functions
  public void categoryAdded(Category category) {
  	MainWindow.getWindow().getDisplay().asyncExec(
	  		new AERunnable() 
			{
	  			public void 
				runSupport() 
	  			{
	  				modifyCategoryViewList();
	  				addCategorySubMenu();
	  			}
			});
  }

  public void categoryRemoved(Category category) {
	MainWindow.getWindow().getDisplay().asyncExec(
	  		new AERunnable() 
			{
	  			public void 
				runSupport() 
	  			{
	  				modifyCategoryViewList();
	  				addCategorySubMenu();
	  			}
			});
  }

  // globalmanagerlistener Functions
  public void downloadManagerAdded( DownloadManager dm ) {
    dm.addListener( this );

    if (skipDMAdding ||
        (currentCategory != null && currentCategory.getType() == Category.TYPE_USER))
      return;
    Category cat = dm.getDownloadState().getCategory();
    if (cat == null)
      downloadManagerAdded(null, dm);
  }

  public void downloadManagerRemoved( DownloadManager dm ) {
    dm.removeListener( this );

    MinimizedWindow mw = (MinimizedWindow) downloadBars.remove(dm);
    if (mw != null) mw.close();

    if (skipDMAdding ||
        (currentCategory != null && currentCategory.getType() == Category.TYPE_USER))
      return;
    downloadManagerRemoved(null, dm);
  }

  public void destroyInitiated() {  }
  public void destroyed() { }

  // End of globalmanagerlistener Functions
  
  private void setSelectedTorrentsSpeed(int speed) {      
    Object[] dms = getSelectedDataSources();
    if(dms.length > 0) {            
      for (int i = 0; i < dms.length; i++) {
        try {
          DownloadManager dm = (DownloadManager)dms[i];
          dm.getStats().setUploadRateLimitBytesPerSecond(speed);
        } catch (Exception e) {
        	Debug.printStackTrace( e );
        }
      }
    }
  }
}
