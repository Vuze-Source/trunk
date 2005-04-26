/*
 * Created on Sep 13, 2004
 * Created by Olivier Chalouhi
 * Copyright (C) 2004 Aelitis, All Rights Reserved.
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
 * AELITIS, SARL au capital de 30,000 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */
package org.gudy.azureus2.ui.swt.views.stats;


import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.core3.util.TimeFormatter;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.components.graphics.SpeedGraphic;
import org.gudy.azureus2.ui.swt.views.AbstractIView;
import sun.management.StringFlag;

import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.dht.DHT;
import com.aelitis.azureus.core.dht.control.DHTControlActivity;
import com.aelitis.azureus.core.dht.control.DHTControlStats;
import com.aelitis.azureus.core.dht.db.DHTDBStats;
import com.aelitis.azureus.core.dht.router.DHTRouterStats;
import com.aelitis.azureus.core.dht.transport.DHTTransportFullStats;
import com.aelitis.azureus.core.dht.transport.DHTTransportStats;
import com.aelitis.azureus.core.diskmanager.cache.CacheFileManagerFactory;
import com.aelitis.azureus.core.diskmanager.cache.CacheFileManagerStats;
import com.aelitis.azureus.plugins.dht.DHTPlugin;

/**
 * 
 */
public class DHTView extends AbstractIView {
  
  DHT dht;
  DHTControlStats controlStats;
  DHTDBStats dbStats;
  DHTTransportStats transportStats;
  DHTRouterStats routerStats;
  DHTTransportFullStats fullStats;
  
  Composite panel;
  
  Label lblUpTime,lblNumberOfUsers;
  Label lblNodes,lblLeaves;
  Label lblContacts,lblReplacements,lblLive,lblUnknown,lblDying;

  Label lblReceivedPackets,lblReceivedBytes;
  Label lblSentPackets,lblSentBytes;
    
  Label lblPings[] = new Label[4];
  Label lblFindNodes[] = new Label[4];
  Label lblFindValues[] = new Label[4];
  Label lblStores[] = new Label[4];
    
  Canvas  in,out;  
  SpeedGraphic inGraph,outGraph;
  
  Table activityTable;
  DHTControlActivity[] activities;

  public DHTView() {
    init();
  }
  
  private void init() {
    try {
      dht = ((DHTPlugin) AzureusCoreFactory.getSingleton().getPluginManager().getPluginInterfaceByClass( DHTPlugin.class ).getPlugin()).getDHT();
      if(dht == null) return;
      
      controlStats = dht.getControl().getStats();
      dbStats = dht.getDataBase().getStats();
      transportStats = dht.getTransport().getStats();
      routerStats = dht.getRouter().getStats();
      fullStats = dht.getTransport().getLocalContact().getStats();
      
    } catch(Exception e) {
      Debug.printStackTrace( e );
    }
  }
  
  public void initialize(Composite composite) {
    panel = new Composite(composite,SWT.NULL);
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    panel.setLayout(layout);
    
    initialiseGeneralGroup();
    initialiseTransportDetailsGroup();
    initialiseOperationDetailsGroup();
    initialiseActivityGroup();
  }
  
  private void initialiseGeneralGroup() {
    Group gGeneral = new Group(panel,SWT.NONE);
    Messages.setLanguageText(gGeneral,"DHTView.general.title");
    
    GridData data = new GridData(GridData.FILL_HORIZONTAL);
    data.verticalAlignment = SWT.BEGINNING;
    gGeneral.setLayoutData(data);
    
    GridLayout layout = new GridLayout();
    layout.numColumns = 6;
    gGeneral.setLayout(layout);
    
    
    Label label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.uptime");    
    
    lblUpTime = new Label(gGeneral,SWT.NONE);
    lblUpTime.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    
    label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.users");    
    
    lblNumberOfUsers = new Label(gGeneral,SWT.NONE);
    lblNumberOfUsers.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    
    label = new Label(gGeneral,SWT.NONE);
    label = new Label(gGeneral,SWT.NONE);
    
    
    label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.nodes");    
    
    lblNodes = new Label(gGeneral,SWT.NONE);
    lblNodes.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    
    label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.leaves");    
    
    lblLeaves = new Label(gGeneral,SWT.NONE);
    lblLeaves.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    
    label = new Label(gGeneral,SWT.NONE);
    label = new Label(gGeneral,SWT.NONE);
    
    
    label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.contacts");    
    
    lblContacts = new Label(gGeneral,SWT.NONE);
    lblContacts.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    
    label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.replacements");    
    
    lblReplacements = new Label(gGeneral,SWT.NONE);
    lblReplacements.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    
    label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.live");    
    
    lblLive= new Label(gGeneral,SWT.NONE);
    lblLive.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    
    label = new Label(gGeneral,SWT.NONE);
    label = new Label(gGeneral,SWT.NONE);
    
    label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.unknown");    
    
    lblUnknown = new Label(gGeneral,SWT.NONE);
    lblUnknown.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    
    label = new Label(gGeneral,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.general.dying");    
    
    lblDying = new Label(gGeneral,SWT.NONE);
    lblDying.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));    
  }
  
  private void initialiseTransportDetailsGroup() {
    Group gTransport = new Group(panel,SWT.NONE);
    Messages.setLanguageText(gTransport,"DHTView.transport.title");
    
    GridData data = new GridData(GridData.FILL_VERTICAL);
    data.widthHint = 300;
    data.verticalSpan = 3;
    gTransport.setLayoutData(data);
    
    GridLayout layout = new GridLayout();
    layout.numColumns = 3;    
    layout.makeColumnsEqualWidth = true;
    gTransport.setLayout(layout);
    
    
    Label label = new Label(gTransport,SWT.NONE);
    
    label = new Label(gTransport,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.transport.packets");
    label.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    
    label = new Label(gTransport,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.transport.bytes");
    label.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    
    label = new Label(gTransport,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.transport.received");
    
    lblReceivedPackets = new Label(gTransport,SWT.NONE);
    lblReceivedPackets.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    
    lblReceivedBytes = new Label(gTransport,SWT.NONE);
    lblReceivedBytes.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    
    label = new Label(gTransport,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.transport.sent");
    
    lblSentPackets = new Label(gTransport,SWT.NONE);
    lblSentPackets.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    
    lblSentBytes = new Label(gTransport,SWT.NONE);
    lblSentBytes.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    
    label = new Label(gTransport,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.transport.in");
    data = new GridData();
    data.horizontalSpan = 3;
    label.setLayoutData(data);
    
    
    in = new Canvas(gTransport,SWT.NONE);
    data = new GridData(GridData.FILL_BOTH);
    data.horizontalSpan = 3;
    in.setLayoutData(data);
    inGraph = SpeedGraphic.getInstance();
    inGraph.initialize(in);
    
    label = new Label(gTransport,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.transport.out");
    data = new GridData();
    data.horizontalSpan = 3;
    label.setLayoutData(data);
    
    out = new Canvas(gTransport,SWT.NONE);
    data = new GridData(GridData.FILL_BOTH);
    data.horizontalSpan = 3;
    out.setLayoutData(data);
    outGraph = SpeedGraphic.getInstance();
    outGraph.initialize(out);
  }
  
  private void initialiseOperationDetailsGroup() {
    Group gOperations = new Group(panel,SWT.NONE);
    Messages.setLanguageText(gOperations,"DHTView.operations.title");
    gOperations.setLayoutData(new GridData(SWT.FILL,SWT.BEGINNING,true,false));
    
    GridLayout layout = new GridLayout();
    layout.numColumns = 5;
    layout.makeColumnsEqualWidth = true;
    gOperations.setLayout(layout);
    
    
    Label label = new Label(gOperations,SWT.NONE);
    
    label = new Label(gOperations,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.operations.sent");
    label.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    
    label = new Label(gOperations,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.operations.ok");
    label.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    
    label = new Label(gOperations,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.operations.failed");
    label.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    
    label = new Label(gOperations,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.operations.received");
    label.setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    
    
    label = new Label(gOperations,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.operations.ping");
    
    for(int i = 0 ; i < 4 ; i++) {
      lblPings[i] = new Label(gOperations,SWT.NONE);      
      lblPings[i].setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    }
    
    
    label = new Label(gOperations,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.operations.findNode");
    
    for(int i = 0 ; i < 4 ; i++) {
      lblFindNodes[i] = new Label(gOperations,SWT.NONE);      
      lblFindNodes[i].setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    }
    
    
    label = new Label(gOperations,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.operations.findValue");
    
    for(int i = 0 ; i < 4 ; i++) {
      lblFindValues[i] = new Label(gOperations,SWT.NONE);      
      lblFindValues[i].setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    }
    
    
    label = new Label(gOperations,SWT.NONE);
    Messages.setLanguageText(label,"DHTView.operations.store");
    
    for(int i = 0 ; i < 4 ; i++) {
      lblStores[i] = new Label(gOperations,SWT.NONE);      
      lblStores[i].setLayoutData(new GridData(SWT.FILL,SWT.TOP,true,false));
    }
    
  }
  
  private void initialiseActivityGroup() {
    Group gActivity = new Group(panel,SWT.NONE);
    Messages.setLanguageText(gActivity,"DHTView.activity.title");
    gActivity.setLayoutData(new GridData(SWT.FILL,SWT.FILL,true,true));
    gActivity.setLayout(new GridLayout());
    
    activityTable = new Table(gActivity,SWT.VIRTUAL | SWT.BORDER);
    activityTable.setLayoutData(new GridData(GridData.FILL_BOTH));
    
    TableColumn colStatus =  new TableColumn(activityTable,SWT.LEFT);
    Messages.setLanguageText(colStatus,"DHTView.activity.status");
    colStatus.setWidth(70);
    
    TableColumn colType =  new TableColumn(activityTable,SWT.LEFT);
    Messages.setLanguageText(colType,"DHTView.activity.type");
    colType.setWidth(70);
    
    TableColumn colName =  new TableColumn(activityTable,SWT.LEFT);
    Messages.setLanguageText(colName,"DHTView.activity.name");
    colName.setWidth(150);
    
    TableColumn colDetails =  new TableColumn(activityTable,SWT.LEFT);
    Messages.setLanguageText(colDetails,"DHTView.activity.details");
    colDetails.setWidth(300);
    
    activityTable.setHeaderVisible(true);
    
    activityTable.addListener(SWT.SetData, new Listener() {
      public void handleEvent(Event event) {
        TableItem item = (TableItem) event.item;
        int index = activityTable.indexOf (item);
        item.setText (0,activities[index].isQueued() + "");
        item.setText (1,activities[index].getType() + "");
        item.setText (2,activities[index].getName());
        item.setText (3,activities[index].getString());
      }
    });
    
  }
  

  public void delete() {
    Utils.disposeComposite(panel);
  }

  public String getFullTitle() {
    return MessageText.getString("DHTView.title.full"); //$NON-NLS-1$
  }
  
  public Composite getComposite() {
    return panel;
  }
  
  public void refresh() {    
    if(dht == null) { 
      init();
      return;
    }
    
    inGraph.refresh();
    outGraph.refresh();
    
    refreshGeneral();
    refreshTransportDetails();
    refreshOperationDetails();
    refreshActivity();
  }  
  
  private void refreshGeneral() {
    lblUpTime.setText(TimeFormatter.format(controlStats.getRouterUptime() / 1000));
    lblNumberOfUsers.setText("" + controlStats.getEstimatedDHTSize());
    long[] stats = routerStats.getStats();
    lblNodes.setText("" + stats[DHTRouterStats.ST_NODES]);
    lblLeaves.setText("" + stats[DHTRouterStats.ST_LEAVES]);
    lblContacts.setText("" + stats[DHTRouterStats.ST_CONTACTS]);
    lblReplacements.setText("" + stats[DHTRouterStats.ST_REPLACEMENTS]);
    lblLive.setText("" + stats[DHTRouterStats.ST_CONTACTS_LIVE]);
    lblUnknown.setText("" + stats[DHTRouterStats.ST_CONTACTS_UNKNOWN]);
    lblDying.setText("" + stats[DHTRouterStats.ST_CONTACTS_DEAD]);
  }

  private void refreshTransportDetails() {
    lblReceivedBytes.setText(DisplayFormatters.formatByteCountToKiBEtc(transportStats.getBytesReceived()));
    lblSentBytes.setText(DisplayFormatters.formatByteCountToKiBEtc(transportStats.getBytesSent()));
    lblReceivedPackets.setText("" + transportStats.getPacketsReceived());
    lblSentPackets.setText("" + transportStats.getPacketsSent());
  }
  
  private void refreshOperationDetails() {    
    long[] pings = transportStats.getPings();
    for(int i = 0 ; i < 4 ; i++) {
      lblPings[i].setText("" + pings[i]);
    }
    
    long[] findNodes = transportStats.getFindNodes();
    for(int i = 0 ; i < 4 ; i++) {
      lblFindNodes[i].setText("" + findNodes[i]);
    }
    
    long[] findValues = transportStats.getFindValues();
    for(int i = 0 ; i < 4 ; i++) {
      lblFindValues[i].setText("" + findValues[i]);
    }
    
    long[] stores = transportStats.getStores();
    for(int i = 0 ; i < 4 ; i++) {
      lblStores[i].setText("" + stores[i]);
    }
  }
  
  private void refreshActivity() {
    activities = dht.getControl().getActivities();
    activityTable.setItemCount(activities.length);
    activityTable.redraw();
  }
  
  public void periodicUpdate() {
    if(dht == null) return;
    inGraph.addIntValue((int)fullStats.getAverageBytesReceived());
    outGraph.addIntValue((int)fullStats.getAverageBytesSent());
  }
  
  public String getData() {
    return "DHTView.title.full";
  }
    
}


