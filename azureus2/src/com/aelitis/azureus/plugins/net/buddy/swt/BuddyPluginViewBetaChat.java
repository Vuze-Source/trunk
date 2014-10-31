/*
 * Created on Apr 26, 2008
 * Created by Paul Gardner
 * 
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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


package com.aelitis.azureus.plugins.net.buddy.swt;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.gudy.azureus2.core3.util.AENetworkClassifier;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.plugins.utils.LocaleUtilities;
import org.gudy.azureus2.pluginsimpl.local.utils.FormattersImpl;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.components.BufferedLabel;
import org.gudy.azureus2.ui.swt.components.LinkLabel;
import org.gudy.azureus2.ui.swt.components.shell.ShellFactory;
import org.gudy.azureus2.ui.swt.mainwindow.ClipboardCopy;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;
import org.gudy.azureus2.ui.swt.mainwindow.TorrentOpener;

import com.aelitis.azureus.plugins.net.buddy.BuddyPlugin;
import com.aelitis.azureus.plugins.net.buddy.BuddyPluginBeta;
import com.aelitis.azureus.plugins.net.buddy.BuddyPluginBeta.*;

public class 
BuddyPluginViewBetaChat 
	implements ChatListener
{
	private static final boolean TEST_LOOPBACK_CHAT = System.getProperty( "az.chat.loopback.enable", "0" ).equals( "1" );
	private static final boolean DEBUG_ENABLED		= System.getProperty( "az.chat.buddy.debug", "0" ).equals( "1" );

	private BuddyPlugin			plugin;
	private ChatInstance		chat;
	
	private LocaleUtilities		lu;
	
	private Shell 					shell;
	
	private StyledText 				log;
	private Table					buddy_table;
	private BufferedLabel		 	status;
	
	private Button 			shared_nick_button;
	private Text 			nickname;
	
	private Text 		input_area;
	
	private List<ChatMessage>			messages		= new ArrayList<ChatMessage>();
	private List<ChatParticipant>		participants 	= new ArrayList<ChatParticipant>();
	
	private Map<ChatParticipant,ChatMessage>	participant_last_message_map = new HashMap<ChatParticipant, ChatMessage>();
	
	private boolean		table_resort_required;
	
	private Font	italic_font;
	
	protected
	BuddyPluginViewBetaChat(
		BuddyPlugin		_plugin,
		ChatInstance	_chat )
	{
		plugin	= _plugin;
		chat	= _chat;
		
		lu		= plugin.getPluginInterface().getUtilities().getLocaleUtilities();
		
		shell = ShellFactory.createMainShell( SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX );

		shell.addDisposeListener(
			new DisposeListener()
			{
				public void 
				widgetDisposed(
					DisposeEvent arg0 ) 
				{
					if ( italic_font != null ){
						
						italic_font.dispose();
						
						italic_font = null;
					}
					
					closed();
				}
			});
		
		shell.setText( lu.getLocalisedMessageText( "label.chat" ) + ": " + chat.getName());
				
		Utils.setShellIcon(shell);
		
		build( shell );
		
		shell.addListener(
				SWT.Traverse, 
				new Listener() 
				{	
					public void 
					handleEvent(
						Event e ) 
					{
						if ( e.character == SWT.ESC){
						
							close();
					}
				}
			});
		
	    shell.setSize( 500, 500 );
	    
	    Utils.createURLDropTarget(shell, input_area);
	    Utils.centreWindow(shell);
	    shell.open();
	}
	
	protected
	BuddyPluginViewBetaChat(
		BuddyPlugin		_plugin,
		ChatInstance	_chat,
		Composite		_parent )
	{
		plugin	= _plugin;
		chat	= _chat;
		
		lu		= plugin.getPluginInterface().getUtilities().getLocaleUtilities();
		
		build( _parent );
	}
	
	private void
	build(
		Composite		parent )
	{
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		parent.setLayout(layout);
		GridData grid_data = new GridData(GridData.FILL_BOTH );
		parent.setLayoutData(grid_data);

		Composite lhs = new Composite(parent, SWT.NONE);
		layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		lhs.setLayout(layout);
		grid_data = new GridData(GridData.FILL_BOTH );
		grid_data.widthHint = 300;
		lhs.setLayoutData(grid_data);
		
		Composite temp = new Composite(lhs, SWT.NONE);
		layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		temp.setLayout(layout);
		grid_data = new GridData(GridData.FILL_HORIZONTAL);
		grid_data.horizontalIndent = 8;
		grid_data.heightHint = 20;
		temp.setLayoutData(grid_data);
		
		status = new BufferedLabel( temp, SWT.LEFT | SWT.DOUBLE_BUFFERED );
		grid_data = new GridData(GridData.FILL_BOTH);
		
		status.setLayoutData(grid_data);
		status.setText( "Pending" );
		
		Composite log_holder = new Composite(lhs, SWT.BORDER);
		layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.marginLeft = 4;
		log_holder.setLayout(layout);
		grid_data = new GridData(GridData.FILL_BOTH );
		log_holder.setLayoutData(grid_data);
		
		log = new StyledText(log_holder,SWT.READ_ONLY | SWT.V_SCROLL | SWT.WRAP | SWT.NO_FOCUS );
		grid_data = new GridData(GridData.FILL_BOTH);
		grid_data.horizontalSpan = 1;
		//grid_data.horizontalIndent = 4;
		log.setLayoutData(grid_data);
		//log.setIndent( 4 );
		
		log.setEditable( false );

		log_holder.setBackground( log.getBackground());

		final Menu log_menu = new Menu( log );
		
		log.setMenu(  log_menu );

		FontData fontData = log.getFont().getFontData()[0];
		
		italic_font = new Font( log.getDisplay(), new FontData( fontData.getName(), fontData.getHeight(), SWT.ITALIC ));
		
		final MenuItem mi_open_vuze = new MenuItem( log_menu, SWT.PUSH );
		
		mi_open_vuze.addSelectionListener(
			new SelectionAdapter() {
				
				public void 
				widgetSelected(
					SelectionEvent e ) 
				{
					String url_str = (String)mi_open_vuze.getData();
					
					if ( url_str != null ){
						
						String lc_url_str = url_str.toLowerCase( Locale.US );
						
						if ( lc_url_str.startsWith( "chat:" )){
							
							try{
								plugin.getBeta().handleURI( url_str );
								
							}catch( Throwable f ){
								
								Debug.out( f );
							}
							
						}else{
						
							TorrentOpener.openTorrent( url_str );
						}
					}
				}
			});
		
		final MenuItem mi_open_ext = new MenuItem( log_menu, SWT.PUSH );
		
		mi_open_ext.setText( lu.getLocalisedMessageText( "azbuddy.dchat.open.in.browser" ));
		
		mi_open_ext.addSelectionListener(
			new SelectionAdapter() {
				
				public void 
				widgetSelected(
					SelectionEvent e ) 
				{
					String url_str = (String)mi_open_ext.getData();
					
					Utils.launch( url_str );
				}
			});
		
		new MenuItem( log_menu, SWT.SEPARATOR );
		
		final MenuItem mi_copy_clip = new MenuItem( log_menu, SWT.PUSH );
		
		mi_copy_clip.setText( lu.getLocalisedMessageText( "ConfigView.copy.to.clipboard.tooltip" ));
		
		mi_copy_clip.addSelectionListener(
				new SelectionAdapter() {
					
					public void 
					widgetSelected(
						SelectionEvent e ) 
					{
						String url_str = (String)mi_copy_clip.getData();
						
						if ( url_str != null ){
							
							ClipboardCopy.copyToClipBoard( url_str );
						}
					}
				});
		
		log.addMenuDetectListener(
			new MenuDetectListener() {
				
				public void 
				menuDetected(
					MenuDetectEvent e ) 
				{
					e.doit = false;
					
					try{
						Point mapped = log.getDisplay().map( null, log, new Point( e.x, e.y ));
						
						int offset = log.getOffsetAtLocation( mapped );
						
						StyleRange sr = log.getStyleRangeAtOffset(  offset );
						
						if ( sr != null ){
							
							String url_str = (String)sr.data;
							
							if ( url_str != null ){
																
								String str = url_str;
								
								if ( str.length() > 50 ){
									
									str = str.substring( 0, 50 ) + "...";
								}
								
								str = lu.getLocalisedMessageText( "azbuddy.dchat.open.in.vuze" ) + ": " + str;
								
								mi_open_vuze.setText( str);
								mi_open_vuze.setData( url_str );
								
								if ( url_str.toLowerCase().startsWith( "http" )){
									
									mi_open_ext.setData( url_str );
									
									mi_open_ext.setEnabled( true );
									
								}else{
									
									mi_open_ext.setEnabled( false );
								}
								
								mi_copy_clip.setData( url_str );
								
								e.doit = true;
							}
						}
					}catch( Throwable f ){
						
					}
				}
			});
		
		

		
		
		Composite rhs = new Composite(parent, SWT.NONE);
		layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		rhs.setLayout(layout);
		grid_data = new GridData(GridData.FILL_VERTICAL );
		grid_data.widthHint = 150;
		rhs.setLayoutData(grid_data);

			// options
		
		Composite top_right = new Composite(rhs, SWT.NONE);
		layout = new GridLayout();
		layout.numColumns = 3;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.marginRight = 4;
		top_right.setLayout(layout);
		grid_data = new GridData( GridData.FILL_HORIZONTAL );
		grid_data.heightHint = 50;
		top_right.setLayoutData(grid_data);
		
		Label label = new Label( top_right, SWT.NULL );
		
		label.setText( lu.getLocalisedMessageText( "azbuddy.dchat.nick.shared" ));

		shared_nick_button = new Button( top_right, SWT.CHECK );
		
		shared_nick_button.setSelection( chat.isSharedNickname());
		
		LinkLabel link = new LinkLabel( top_right, "label.help", lu.getLocalisedMessageText( "azbuddy.dchat.link.url" ));	
		grid_data = new GridData( GridData.FILL_HORIZONTAL );
		grid_data.horizontalAlignment = SWT.END;
		link.getlabel().setLayoutData( grid_data );

		nickname = new Text( top_right, SWT.BORDER );
		grid_data = new GridData( GridData.FILL_HORIZONTAL );
		grid_data.horizontalSpan=3;
		nickname.setLayoutData( grid_data );

		nickname.setText( chat.getNickname());

		shared_nick_button.addSelectionListener(
			new SelectionAdapter() 
			{
				public void widgetSelected(SelectionEvent arg0) {
					
					boolean shared = shared_nick_button.getSelection();
					
					chat.setSharedNickname( shared );
				}
			});
		
		nickname.addListener(SWT.FocusOut, new Listener() {
	        public void handleEvent(Event event) {
	        	String nick = nickname.getText().trim();
	        	
	        	if ( chat.isSharedNickname()){
	        		
	        		if ( chat.getNetwork() == AENetworkClassifier.AT_PUBLIC ){
	        		
	        			plugin.getBeta().setSharedPublicNickname( nick );
	        			
	        		}else{
	        			
	        			plugin.getBeta().setSharedAnonNickname( nick );
	        		}
	        	}else{
	        		
	        		chat.setInstanceNickname( nick );
	        	}
	        }
	    });
		
			// table
		
		buddy_table = new Table(rhs, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION | SWT.VIRTUAL);

		String[] headers = { 
				"azbuddy.ui.table.name" };

		int[] sizes = { 120 };

		int[] aligns = { SWT.LEFT };

		for (int i = 0; i < headers.length; i++){

			TableColumn tc = new TableColumn(buddy_table, aligns[i]);
				
			tc.setWidth(sizes[i]);

			Messages.setLanguageText(tc, headers[i]);
		}	

	    buddy_table.setHeaderVisible(true);

	    grid_data = new GridData(GridData.FILL_BOTH);
	    // grid_data.heightHint = buddy_table.getHeaderHeight() * 3;
		buddy_table.setLayoutData(grid_data);
		
		
		buddy_table.addListener(
			SWT.SetData,
			new Listener()
			{
				public void 
				handleEvent(
					Event event) 
				{
					TableItem item = (TableItem)event.item;
					
					int index = buddy_table.indexOf(item);
	
					if ( index < 0 || index >= participants.size()){
						
						return;
					}
					
					ChatParticipant	participant = (BuddyPluginBeta.ChatParticipant)participants.get(index);
					
					item.setData( participant );
					
					item.setText(0, participant.getName());		
					
					setProperties( item, participant );
				}
			});
		
		final Menu menu = new Menu(buddy_table);
		
		buddy_table.setMenu( menu );
		
		menu.addMenuListener(
			new MenuListener() 
			{
				public void 
				menuShown(
					MenuEvent e ) 
				{
					MenuItem[] items = menu.getItems();
					
					for (int i = 0; i < items.length; i++){
						
						items[i].dispose();
					}

					final TableItem[] selection = buddy_table.getSelection();
					
					boolean	can_ignore 	= false;
					boolean	can_listen	= false;
					boolean	can_pin		= false;
					boolean	can_unpin	= false;
					
					for (int i=0;i<selection.length;i++){
						
						ChatParticipant	participant = (ChatParticipant)selection[i].getData();
						
						if ( DEBUG_ENABLED ){
							
							System.out.println( participant.getName() + "/" + participant.getAddress());
							
							List<ChatMessage>	messages = participant.getMessages();
							
							for ( ChatMessage msg: messages ){
								
								System.out.println( "    " + msg.getTimeStamp() + ", " + msg.getAddress() + " - " + msg.getMessage());
							}
						}
						
						if ( participant.isIgnored()){
						
							can_listen = true;
							
						}else{
							
							can_ignore = true;
						}
						
						if ( participant.isPinned()){
							
							can_unpin = true;
							
						}else{
							
							can_pin = true;
						}
					}
					
					final MenuItem ignore_item = new MenuItem(menu, SWT.PUSH);
					
					ignore_item.setText( lu.getLocalisedMessageText( "label.mute" ) );

					ignore_item.addSelectionListener(
						new SelectionAdapter() 
						{
							public void 
							widgetSelected(
								SelectionEvent e) 
							{
								for (int i=0;i<selection.length;i++){
									
									ChatParticipant	participant = (ChatParticipant)selection[i].getData();
									
									if ( !participant.isIgnored()){
										
										participant.setIgnored( true );
										
										setProperties( selection[i], participant );
										
										messagesChanged();
									}
								}
							};
						});
					
					ignore_item.setEnabled( can_ignore );
					
					final MenuItem listen_item = new MenuItem(menu, SWT.PUSH);
					
					listen_item.setText(lu.getLocalisedMessageText( "label.listen" ) );

					listen_item.addSelectionListener(
						new SelectionAdapter() 
						{
							public void 
							widgetSelected(
								SelectionEvent e) 
							{
								for (int i=0;i<selection.length;i++){
									
									ChatParticipant	participant = (ChatParticipant)selection[i].getData();
									
									if ( participant.isIgnored()){
										
										participant.setIgnored( false );
										
										setProperties( selection[i], participant );
										
										messagesChanged();
									}
								}
							};
						});
					
					listen_item.setEnabled( can_listen );
					
					new MenuItem(menu, SWT.SEPARATOR );
					
					final MenuItem pin_item = new MenuItem(menu, SWT.PUSH);
					
					pin_item.setText( lu.getLocalisedMessageText( "label.pin" ) );

					pin_item.addSelectionListener(
						new SelectionAdapter() 
						{
							public void 
							widgetSelected(
								SelectionEvent e) 
							{
								for (int i=0;i<selection.length;i++){
									
									ChatParticipant	participant = (ChatParticipant)selection[i].getData();
									
									if ( !participant.isPinned()){
										
										participant.setPinned( true );
										
										setProperties( selection[i], participant );
									}
								}
							};
						});
					
					pin_item.setEnabled( can_pin );
					
					final MenuItem unpin_item = new MenuItem(menu, SWT.PUSH);
					
					unpin_item.setText( lu.getLocalisedMessageText( "label.unpin" ) );

					unpin_item.addSelectionListener(
						new SelectionAdapter() 
						{
							public void 
							widgetSelected(
								SelectionEvent e) 
							{
								for (int i=0;i<selection.length;i++){
									
									ChatParticipant	participant = (ChatParticipant)selection[i].getData();
									
									if ( participant.isPinned()){
										
										participant.setPinned( false );
										
										setProperties( selection[i], participant );
									}
								}
							};
						});
					
					unpin_item.setEnabled( can_unpin );
					
					if ( !chat.isPrivateChat()){
						
						new MenuItem(menu, SWT.SEPARATOR );
						
						final MenuItem private_chat_item = new MenuItem(menu, SWT.PUSH);
						
						private_chat_item.setText( lu.getLocalisedMessageText( "label.private.chat" ) );
	
						final byte[]	chat_pk = chat.getPublicKey();
	
						private_chat_item.addSelectionListener(
							new SelectionAdapter() 
							{
								public void 
								widgetSelected(
									SelectionEvent e) 
								{
									for (int i=0;i<selection.length;i++){
										
										ChatParticipant	participant = (ChatParticipant)selection[i].getData();
										
										if ( TEST_LOOPBACK_CHAT || !Arrays.equals( participant.getPublicKey(), chat_pk )){
											
											try{
												ChatInstance chat = participant.createPrivateChat();
											
												new BuddyPluginViewBetaChat( plugin, chat);
												
											}catch( Throwable f ){
												
												Debug.out( f );
											}
										}
									}
								};
							});
							
						boolean	pc_enable = false;
						
						if ( chat_pk != null ){
							
							for (int i=0;i<selection.length;i++){
								
								ChatParticipant	participant = (ChatParticipant)selection[i].getData();
								
								if ( !Arrays.equals( participant.getPublicKey(), chat_pk )){
									
									pc_enable = true;
								}
							}
						}
						
						private_chat_item.setEnabled( pc_enable || TEST_LOOPBACK_CHAT );
					}
				}
				
				public void menuHidden(MenuEvent e) {
				}
			});
		
	
		
			// Text
		
		input_area = new Text( parent, SWT.MULTI | SWT.V_SCROLL | SWT.WRAP | SWT.BORDER);
		grid_data = new GridData(GridData.FILL_HORIZONTAL );
		grid_data.horizontalSpan = 2;
		grid_data.heightHint = 30;
		input_area.setLayoutData(grid_data);
			
		input_area.setTextLimit( 256 );
		
		input_area.addKeyListener(
			new KeyListener()
			{
				public void 
				keyPressed(
					KeyEvent e) 
				{
					if ( e.keyCode == SWT.CR ){
				
						e.doit = false;
						
						String message = input_area.getText().trim();
						
						if ( message.length() > 0 ){
							
							sendMessage(  message );
							
							input_area.setText( "" );
						}
					}
				}
				
				public void 
				keyReleased(
					KeyEvent e ) 
				{
				}
			});
		
		input_area.setFocus();
		
		BuddyPluginBeta.ChatParticipant[] existing_participants = chat.getParticipants();
		
		synchronized( participants ){
			
			participants.addAll( Arrays.asList( existing_participants ));
		}
		
		table_resort_required = true;
		
		updateTable( false );
		
		BuddyPluginBeta.ChatMessage[] history = chat.getHistory();
		
		for (int i=0;i<history.length;i++){
			
			logChatMessage( history[i] );
		}
		
		chat.addListener( this );
	}
	
	private void
	setProperties(
		TableItem			item,
		ChatParticipant		p )
	{
		if ( p.isIgnored()){
		
			item.setForeground( 0, Colors.grey );
			
		}else{
			
			if ( p.isPinned()){
			
				item.setForeground( 0, Colors.fadedGreen );
				
			}else{
			
				if ( p.isMe()){
					
					item.setForeground( 0, Colors.fadedGreen );
					
					item.setFont( 0, italic_font );
					
				}else if ( p.isNickClash()){
					
					item.setForeground( 0, Colors.red );
					
				}else{
					
					if ( p.hasNickname()){
						
						item.setForeground( 0, Colors.blues[Colors.FADED_DARKEST] );
						
					}else{
						
						item.setForeground( 0, Colors.black );
					}
				}
			}
		}
	}
	
	protected void
	addDisposeListener(
		final DisposeListener	listener )
	{
		if ( shell != null ){
			
			if ( shell.isDisposed()){
				
				listener.widgetDisposed( null );
				
			}else{
									
				shell.addDisposeListener( listener );
			}
		}
	}
	
	protected void
	updateTable(
		boolean	async )
	{
		if ( async ){
			
			if ( !buddy_table.isDisposed()){

				buddy_table.getDisplay().asyncExec(
					new Runnable()
					{
						public void
						run()
						{
							if ( buddy_table.isDisposed()){

								return;
							}
							
							updateTable( false );
						}
					});
			}					
		}else{
			
			if ( table_resort_required ){
				
				table_resort_required = false;
				
				sortParticipants();
			}
			
			buddy_table.setItemCount( participants.size());
			buddy_table.clearAll();
			buddy_table.redraw();
		}
	}
	
	protected void
	close()
	{
		if ( shell != null ){
		
			shell.dispose();
		}
	}
	
	protected void
	closed()
	{
		chat.removeListener( this );
		
		chat.destroy();
	}
	
	public void 
	stateChanged(
		final boolean avail ) 
	{
		if ( buddy_table.isDisposed()){
			
			return;
		}
	
		buddy_table.getDisplay().asyncExec(
			new Runnable()
			{
				public void
				run()
				{
					if ( buddy_table.isDisposed()){
						
						return;
					}
					
					input_area.setEnabled( avail );
				}
			});
	}
	
	public void 
	updated() 
	{
		if ( status.isDisposed()){
			
			return;
		}
	
		status.getControl().getDisplay().asyncExec(
			new Runnable()
			{
				public void
				run()
				{
					if ( status.isDisposed()){
						
						return;
					}
				
					status.setText( chat.getStatus());
					
					boolean	is_shared = chat.isSharedNickname();
					
					if ( is_shared != shared_nick_button.getSelection()){
						
						shared_nick_button.setSelection( is_shared );
					}
						
					if ( !nickname.isFocusControl()){
						
						String nick = nickname.getText().trim();
							
						if ( !chat.getNickname().equals( nick )){
								
							nickname.setText( chat.getNickname());
						}
					}
					
					if ( table_resort_required ){
						
						updateTable( false );
					}
				}
			});
	}
	
	private void
	sortParticipants()
	{
		Collections.sort(
			participants,
			new Comparator<ChatParticipant>()
			{
				private Comparator<String> comp = new FormattersImpl().getAlphanumericComparator( true );
				
				public int 
				compare(
					ChatParticipant p1, 
					ChatParticipant p2 ) 
				{
					boolean	b_p1 = p1.hasNickname();
					boolean	b_p2 = p2.hasNickname();
					
					if ( b_p1 == b_p2 ){
					
						return( comp.compare( p1.getName(), p2.getName()));
						
					}else if ( b_p1 ){
						
						return( -1 );
						
					}else{
						
						return( 1 );
					}
				}
			});
	}
	
	public void
	participantAdded(
		ChatParticipant		participant )
	{
		synchronized( participants ){
			
			participants.add( participant );
			
			table_resort_required = true;
		}
		
		updateTable( true );
	}
	
	public void
	participantChanged(
		final ChatParticipant		participant )
	{
		if ( !buddy_table.isDisposed()){

			buddy_table.getDisplay().asyncExec(
				new Runnable()
				{
					public void
					run()
					{
						if ( buddy_table.isDisposed()){

							return;
						}
						
						TableItem[] items = buddy_table.getItems();
						
						String	name = participant.getName();
						
						for ( TableItem item: items ){
							
							if ( item.getData() == participant ){
								
								String old_name = item.getText(0);
								
								if ( !old_name.equals( name )){
								
									item.setText( 0, name );
									
									table_resort_required = true;
								}
							}
						}
					}
				});
		}			
	}
	
	public void
	participantRemoved(
		ChatParticipant		participant )
	{
		synchronized( participants ){
			
			participants.remove( participant );
			
			participant_last_message_map.remove( participant );
		}
		
		updateTable( true );
	}
	
	protected void
	sendMessage(
		String		text )
	{
		//logChatMessage( plugin.getNickname(), Colors.green, text );
		
		chat.sendMessage( text );
	}
	
	public void
	messageReceived(
		final ChatMessage	message )
	{
		if ( !log.isDisposed()){

			log.getDisplay().asyncExec(
				new Runnable()
				{
					public void
					run()
					{
						if ( log.isDisposed()){

							return;
						}
													
						logChatMessage( message );
					}
				});
		}
	}
	
	public void 
	messagesChanged() 
	{
		if ( !log.isDisposed()){

			log.getDisplay().asyncExec(
				new Runnable()
				{
					public void
					run()
					{
						if ( log.isDisposed()){

							return;
						}
						
						try{								
							resetChatMessages();
							
							BuddyPluginBeta.ChatMessage[] history = chat.getHistory();
															
							logChatMessages( history );
							
						}catch( Throwable e ){
							
							Debug.printStackTrace(e);
						}
					}
				});
		}
	}
	
	private void
	resetChatMessages()
	{
		log.setText( "" );
		
		messages.clear();
		
		synchronized( participants ){
			
			participant_last_message_map.clear();
		}
	}
	
	private void
	logChatMessage(
		ChatMessage		message )
	{
		logChatMessages( new ChatMessage[]{ message } );
	}
	
	private void
	logChatMessages(
		ChatMessage[]		all_messages )
	{
		boolean	changed = false;
		
		for ( ChatMessage message: all_messages ){
			
			if ( messages.contains( message )){
				
				return;
			}
			
			messages.add( message );
			
			String	msg		= message.getMessage();

			if ( !message.isIgnored() && msg.length() > 0 ){
				
				changed = true;
				
				String	nick 	= message.getNickName();
				
				int	message_type = message.getMessageType();
				
				ChatParticipant participant = message.getParticipant();
				
				Color colour = Colors.blues[Colors.FADED_DARKEST];
				
				if ( message_type ==  ChatMessage.MT_INFO ){
					
					colour = Colors.grey;
					
				}else if ( message_type ==  ChatMessage.MT_ERROR ){
						
					colour = Colors.red;		
					
				}else if ( participant.isPinned() || participant.isMe()){
					
					colour = Colors.fadedGreen;
					
				}else if ( message.isNickClash()){
					
					colour = Colors.red;
				}
				
				long time = message.getTimeStamp();
				
				String stamp = new SimpleDateFormat( "HH:mm" ).format( new Date( time ));
				
				ChatMessage	last_message;
				
				synchronized( participants ){
					
					last_message = participant_last_message_map.get( participant );
					
					participant_last_message_map.put( participant, message );
				}

				String says;
				
				if ( message_type != ChatMessage.MT_NORMAL ){
					
					says = "[" + stamp + "]";
					
				}else{
					
					says = stamp + " " + (nick.length()>20?(nick.substring(0,16) + "..."):nick);
			
					if ( last_message != null ){
						
						String last_nick = last_message.getNickName();
						
						if ( !nick.equals(last_nick)){
							
							says += " (was " + (last_nick.length()>20?(last_nick.substring(0,16) + "..."):last_nick) + ")";
						}
					}
				}
				
				says += message_type == ChatMessage.MT_NORMAL?"\n":" ";
				
				int	start = log.getText().length();
						
				log.append( says ); 
				
				if ( colour != Colors.black ){
					
					StyleRange styleRange = new StyleRange();
					styleRange.start = start;
					styleRange.length = says.length();
					styleRange.foreground = colour;
					
					if ( participant.isMe()){
						styleRange.font = italic_font;
					}
					
					log.setStyleRange(styleRange);
				}
								
				start = log.getText().length();
				
				log.append( msg ); 

				if ( message_type ==  ChatMessage.MT_INFO ){
					
					StyleRange styleRange = new StyleRange();
					styleRange.start = start;
					styleRange.length = msg.length();
					styleRange.foreground = Colors.grey;
					
					log.setStyleRange(styleRange);
				}else{
					int	pos = 0;
					
					while( pos < msg.length()){
						
						pos = msg.indexOf( ':', pos );
						
						if ( pos == -1 ){
							
							break;
						}
						
						String	protocol = "";
						
						for (int i=pos-1; i>=0; i-- ){
							
							char c = msg.charAt(i);
							
							if ( Character.isWhitespace( c )){
								
								break;
							}
							
							protocol = c + protocol;
						}
						
						if ( protocol.length() > 0 ){
							
							int	end = msg.length();
							
							for ( int i=pos+1;i<msg.length();i++){
								
								if ( Character.isWhitespace( msg.charAt(i))){
									
									end = i;
									
									break;
								}
							}
													
							try{
								String url_str = protocol + msg.substring( pos, end );
						
								if ( protocol.equalsIgnoreCase( "chat" )){
									
									if ( url_str.toLowerCase( Locale.US ).startsWith( "chat:anon" )){
										
										if ( !plugin.getBeta().isI2PAvailable()){
											
											throw( new Exception( "Anonymous chat unavailable" ));
										}
									}
								}else{
								
									URL	url = new URL( url_str );
								}
								
								StyleRange styleRange = new StyleRange();
								styleRange.start = start+pos-protocol.length();
								styleRange.length = url_str.length();
								styleRange.foreground = Colors.blue;
								styleRange.underline = true;
								
									// DON'T store the URL object because in their wisdom SWT invokes the .equals method
									// on data objects when trying to find 'similar' ones, and for URLs this causes
									// a name service lookup...
								
								styleRange.data = url_str;
								
								log.setStyleRange(styleRange);
								
							}catch( Throwable e ){
								
							}
							
							pos = end;
	
						}else{
							
							pos = pos+1;
						}		
					}
				}
				
				log.append( "\n" ); 
			}
		}

		if ( changed ){
		
			log.setSelection( log.getText().length());
		}
	}
}
