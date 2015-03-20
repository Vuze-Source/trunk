/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
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

package org.gudy.azureus2.ui.swt.mainwindow;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.widgets.*;

import org.gudy.azureus2.core3.category.Category;
import org.gudy.azureus2.core3.category.CategoryManager;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.disk.DiskManagerFileInfo;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.download.DownloadManagerState;
import org.gudy.azureus2.core3.internat.LocaleTorrentUtil;
import org.gudy.azureus2.core3.internat.LocaleUtilDecoder;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.logging.*;
import org.gudy.azureus2.core3.torrent.TOTorrent;
import org.gudy.azureus2.core3.torrent.TOTorrentAnnounceURLSet;
import org.gudy.azureus2.core3.torrent.TOTorrentFactory;
import org.gudy.azureus2.core3.torrent.TOTorrentFile;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.pluginsimpl.local.PluginCoreUtils;
import org.gudy.azureus2.pluginsimpl.local.download.DownloadManagerImpl;
import org.gudy.azureus2.ui.common.util.MenuItemManager;
import org.gudy.azureus2.ui.swt.*;
import org.gudy.azureus2.ui.swt.beta.BetaWizard;
import org.gudy.azureus2.ui.swt.components.shell.ShellManager;
import org.gudy.azureus2.ui.swt.config.wizard.ConfigureWizard;
import org.gudy.azureus2.ui.swt.debug.UIDebugGenerator;
import org.gudy.azureus2.ui.swt.donations.DonationWindow;
import org.gudy.azureus2.ui.swt.exporttorrent.wizard.ExportTorrentWizard;
import org.gudy.azureus2.ui.swt.help.AboutWindow;
import org.gudy.azureus2.ui.swt.help.HealthHelpWindow;
import org.gudy.azureus2.ui.swt.importtorrent.wizard.ImportTorrentWizard;
import org.gudy.azureus2.ui.swt.maketorrent.NewTorrentWizard;
import org.gudy.azureus2.ui.swt.minibar.AllTransfersBar;
import org.gudy.azureus2.ui.swt.minibar.MiniBarManager;
import org.gudy.azureus2.ui.swt.nat.NatTestWindow;
import org.gudy.azureus2.ui.swt.plugins.UISWTInputReceiver;
import org.gudy.azureus2.ui.swt.pluginsinstaller.InstallPluginWizard;
import org.gudy.azureus2.ui.swt.pluginsuninstaller.UnInstallPluginWizard;
import org.gudy.azureus2.ui.swt.sharing.ShareUtils;
import org.gudy.azureus2.ui.swt.shells.CoreWaiterSWT;
import org.gudy.azureus2.ui.swt.shells.MessageBoxShell;
import org.gudy.azureus2.ui.swt.speedtest.SpeedTestWizard;
import org.gudy.azureus2.ui.swt.update.UpdateMonitor;
import org.gudy.azureus2.ui.swt.views.stats.StatsView;
import org.gudy.azureus2.ui.swt.views.table.TableViewSWT;
import org.gudy.azureus2.ui.swt.views.table.utils.TableContextMenuManager;
import org.gudy.azureus2.ui.swt.views.utils.ManagerUtils;
import org.gudy.azureus2.ui.swt.welcome.WelcomeWindow;

import com.aelitis.azureus.core.*;
import com.aelitis.azureus.core.speedmanager.SpeedLimitHandler;
import com.aelitis.azureus.core.vuzefile.VuzeFileComponent;
import com.aelitis.azureus.core.vuzefile.VuzeFileHandler;
import com.aelitis.azureus.ui.UIFunctions;
import com.aelitis.azureus.ui.UIFunctionsManager;
import com.aelitis.azureus.ui.mdi.MdiEntry;
import com.aelitis.azureus.ui.mdi.MultipleDocumentInterface;
import com.aelitis.azureus.ui.swt.UIFunctionsManagerSWT;
import com.aelitis.azureus.ui.swt.UIFunctionsSWT;

import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.ui.UIInputReceiver;
import org.gudy.azureus2.plugins.ui.UIInputReceiverListener;
import org.gudy.azureus2.plugins.ui.menus.MenuManager;
import org.gudy.azureus2.plugins.ui.tables.*;
import org.gudy.azureus2.plugins.update.Update;
import org.gudy.azureus2.plugins.update.UpdateCheckInstance;
import org.gudy.azureus2.plugins.update.UpdateCheckInstanceListener;

public class MenuFactory
	implements IMenuConstants
{

	private static boolean isAZ3 = "az3".equalsIgnoreCase(COConfigurationManager.getStringParameter("ui"));

	public static MenuItem createFileMenuItem(Menu menuParent) {
		return createTopLevelMenuItem(menuParent, MENU_ID_FILE);
	}

	public static MenuItem createTransfersMenuItem(Menu menuParent) {
		MenuItem transferMenuItem = createTopLevelMenuItem(menuParent,
				MENU_ID_TRANSFERS);

		Menu transferMenu = transferMenuItem.getMenu();

		MenuFactory.addStartAllMenuItem(transferMenu);
		MenuFactory.addStopAllMenuItem(transferMenu);

		final MenuItem itemPause 	= MenuFactory.addPauseMenuItem(transferMenu);
		final MenuItem itemPauseFor = MenuFactory.addPauseForMenuItem(transferMenu);
		final MenuItem itemResume 	= MenuFactory.addResumeMenuItem(transferMenu);
		//		if (notMainWindow) {
		//			MenuFactory.performOneTimeDisable(itemPause, true);
		//			MenuFactory.performOneTimeDisable(itemResume, true);
		//		}

		transferMenu.addMenuListener(new MenuListener() {
			public void menuShown(MenuEvent menu) {
				if (!AzureusCoreFactory.isCoreRunning()) {
					itemPause.setEnabled(true);
					itemResume.setEnabled(true);
				} else {
					AzureusCore core = AzureusCoreFactory.getSingleton();
					itemPause.setEnabled(core.getGlobalManager().canPauseDownloads());
					itemResume.setEnabled(core.getGlobalManager().canResumeDownloads());
				}
			}

			public void menuHidden(MenuEvent menu) {
					// this behaviour required to get the accelerators to work properly as they won't fire if the menu item is
					// disabled even if if the menu were to be shown they would be...
				itemPause.setEnabled(true);
				itemResume.setEnabled(true);
			}
		});

		return transferMenuItem;
	}

	public static MenuItem createViewMenuItem(Menu menuParent) {
		return createTopLevelMenuItem(menuParent, MENU_ID_VIEW);
	}

	public static MenuItem createAdvancedMenuItem(Menu menuParent) {
		return createTopLevelMenuItem(menuParent, MENU_ID_ADVANCED);
	}

	public static Menu createTorrentMenuItem(final Menu menuParent) {
		final Menu torrentMenu = createTopLevelMenuItem(menuParent,
				MENU_ID_TORRENT).getMenu();

		/*
		 * The Torrents menu is context-sensitive to which torrent is selected in the UI.
		 * For this reason we need to dynamically build the menu when ever it is about to be displayed
		 * so that the states of the menu items accurately reflect what was selected in the UI. 
		 */
		MenuBuildUtils.addMaintenanceListenerForMenu(torrentMenu,
				new MenuBuildUtils.MenuBuilder() {
					public void buildMenu(Menu menu, MenuEvent menuEvent) {
						buildTorrentMenu(menu);
					}
				});
		return torrentMenu;
	}

	public static void buildTorrentMenu(Menu menu) {
		DownloadManager[] current_dls = (DownloadManager[]) menu.getData("downloads");
		if (current_dls == null || current_dls[0] == null) {
			return;
		}

		if (AzureusCoreFactory.isCoreRunning()) {
			boolean is_detailed_view = ((Boolean) menu.getData("is_detailed_view")).booleanValue();
			TableViewSWT<?> tv = (TableViewSWT<?>) menu.getData("TableView");
			AzureusCore core = AzureusCoreFactory.getSingleton();

			TorrentUtil.fillTorrentMenu(menu, current_dls, core,
					menu.getShell(), !is_detailed_view, 0, tv);
		}

		org.gudy.azureus2.plugins.ui.menus.MenuItem[] menu_items;

		menu_items = MenuItemManager.getInstance().getAllAsArray(
				new String[] {
					MenuManager.MENU_TORRENT_MENU,
					MenuManager.MENU_DOWNLOAD_CONTEXT
				});

		final Object[] plugin_dls = DownloadManagerImpl.getDownloadStatic(current_dls);

		if (menu_items.length > 0) {
			addSeparatorMenuItem(menu);

			MenuBuildUtils.addPluginMenuItems(menu_items, menu, true, true,
					new MenuBuildUtils.MenuItemPluginMenuControllerImpl(plugin_dls));
		}

		menu_items = null;

		/**
		 * OK, "hack" time - we'll allow plugins which add menu items against
		 * a table to appear in this menu. We'll have to fake the table row
		 * object though. All downloads need to share a common table.
		 */
			String table_to_use = null;
		for (int i = 0; i < current_dls.length; i++) {
			String table_name = (current_dls[i].isDownloadComplete(false)
					? TableManager.TABLE_MYTORRENTS_COMPLETE
							: TableManager.TABLE_MYTORRENTS_INCOMPLETE);
			if (table_to_use == null || table_to_use.equals(table_name)) {
				table_to_use = table_name;
			} else {
				table_to_use = null;
				break;
			}
		}

		if (table_to_use != null) {
			menu_items = TableContextMenuManager.getInstance().getAllAsArray(
					table_to_use);
		}

		if (menu_items != null) {
			addSeparatorMenuItem(menu);

			TableRow[] dls_as_rows = null;
			dls_as_rows = new TableRow[plugin_dls.length];
			for (int i = 0; i < plugin_dls.length; i++) {
				dls_as_rows[i] = wrapAsRow(plugin_dls[i], table_to_use);
			}

			MenuBuildUtils.addPluginMenuItems(menu_items, menu, true, true,
					new MenuBuildUtils.MenuItemPluginMenuControllerImpl(dls_as_rows));
		}

	}

	public static MenuItem createToolsMenuItem(Menu menuParent) {
		return createTopLevelMenuItem(menuParent, MENU_ID_TOOLS);
	}

	/**
	 * Creates the Plugins menu item and all it's children
	 * @param menuParent
	 * @param includeGetPluginsMenu if <code>true</code> then also include a menu item for getting new plugins
	 * @return
	 */
	public static MenuItem createPluginsMenuItem(final Menu menuParent,
			final boolean includeGetPluginsMenu) {

		MenuItem pluginsMenuItem = createTopLevelMenuItem(menuParent,
				MENU_ID_PLUGINS);
		MenuBuildUtils.addMaintenanceListenerForMenu(pluginsMenuItem.getMenu(),
				new MenuBuildUtils.MenuBuilder() {
					public void buildMenu(Menu menu, MenuEvent menuEvent) {
						PluginsMenuHelper.getInstance().buildPluginMenu(menu,
								menuParent.getShell(), includeGetPluginsMenu);
					}
				});

		return pluginsMenuItem;
	}

	public static MenuItem createWindowMenuItem(Menu menuParent) {
		return createTopLevelMenuItem(menuParent, MENU_ID_WINDOW);
	}

	public static MenuItem createHelpMenuItem(Menu menuParent) {
		return createTopLevelMenuItem(menuParent, MENU_ID_HELP);
	}

	public static MenuItem addCreateMenuItem(Menu menuParent) {
		MenuItem file_create = addMenuItem(menuParent, MENU_ID_CREATE,
				new Listener() {
					public void handleEvent(Event e) {
						new NewTorrentWizard(e.display);
					}
				});
		return file_create;
	}

	public static MenuItem createOpenMenuItem(Menu menuParent) {
		return createTopLevelMenuItem(menuParent, MENU_ID_OPEN);
	}

	public static MenuItem addLogsViewMenuItem(Menu menuParent) {
		return createTopLevelMenuItem(menuParent, MENU_ID_LOG_VIEWS);
	}

	public static MenuItem addOpenTorrentMenuItem(Menu menuParent) {
		return addMenuItem(menuParent, MENU_ID_OPEN_TORRENT, new Listener() {
			public void handleEvent(Event e) {
				UIFunctionsManagerSWT.getUIFunctionsSWT().openTorrentWindow();
			}
		});
	}

	public static MenuItem addOpenURIMenuItem(Menu menuParent) {
		return addMenuItem(menuParent, MENU_ID_OPEN_URI, new Listener() {
			public void handleEvent(Event e) {
				UIFunctionsManagerSWT.getUIFunctionsSWT().openTorrentWindow();
			}
		});
	}
	public static MenuItem addOpenTorrentForTrackingMenuItem(Menu menuParent) {
		MenuItem file_new_torrent_for_tracking = addMenuItem(menuParent,
				MENU_ID_OPEN_TORRENT_FOR_TRACKING, new Listener() {
					public void handleEvent(Event e) {
						TorrentOpener.openTorrentTrackingOnly();
					}
				});
		return file_new_torrent_for_tracking;
	}
	
	public static MenuItem addSearchMenuItem(Menu menuParent) {
		MenuItem item = addMenuItem(menuParent,
				"Button.search", new Listener() {
					public void handleEvent(Event e) {
						UIFunctionsManagerSWT.getUIFunctionsSWT().promptForSearch();
					}
				});
		return item;
	}


	public static MenuItem addOpenVuzeFileMenuItem(final Menu menuParent) {
		return addMenuItem(menuParent, MENU_ID_OPEN_VUZE_FILE, new Listener() {
			public void handleEvent(Event e) {
				Display display = menuParent.getDisplay();

				display.asyncExec(new AERunnable() {
					public void runSupport() {
						FileDialog dialog = new FileDialog(menuParent.getShell(),
								SWT.SYSTEM_MODAL | SWT.OPEN);

						dialog.setFilterPath(TorrentOpener.getFilterPathData());

						dialog.setText(MessageText.getString("MainWindow.dialog.select.vuze.file"));

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

						String path = TorrentOpener.setFilterPathData(dialog.open());

						if (path != null) {

							VuzeFileHandler vfh = VuzeFileHandler.getSingleton();

							if (vfh.loadAndHandleVuzeFile(path,
									VuzeFileComponent.COMP_TYPE_NONE) == null) {

								TorrentOpener.openTorrent(path);
							}
						}
					}
				});
			}
		});
	}

	public static MenuItem createShareMenuItem(Menu menuParent) {
		MenuItem file_share = createTopLevelMenuItem(menuParent, MENU_ID_SHARE);
		return file_share;
	}

	public static MenuItem addShareFileMenuItem(final Menu menuParent) {

		MenuItem file_share_file = addMenuItem(menuParent, MENU_ID_SHARE_FILE,
				new Listener() {
					public void handleEvent(Event e) {
						ShareUtils.shareFile(menuParent.getShell());
					}
				});
		return file_share_file;
	}

	public static MenuItem addShareFolderMenuItem(final Menu menuParent) {
		MenuItem file_share_dir = addMenuItem(menuParent, MENU_ID_SHARE_DIR,
				new Listener() {
					public void handleEvent(Event e) {
						ShareUtils.shareDir(menuParent.getShell());
					}
				});
		return file_share_dir;
	}

	public static MenuItem addShareFolderContentMenuItem(final Menu menuParent) {
		MenuItem file_share_dircontents = addMenuItem(menuParent,
				MENU_ID_SHARE_DIR_CONTENT, new Listener() {
					public void handleEvent(Event e) {
						ShareUtils.shareDirContents(menuParent.getShell(), false);
					}
				});
		return file_share_dircontents;
	}

	public static MenuItem addShareFolderContentRecursiveMenuItem(
			final Menu menuParent) {
		MenuItem file_share_dircontents_rec = addMenuItem(menuParent,
				MENU_ID_SHARE_DIR_CONTENT_RECURSE, new Listener() {
					public void handleEvent(Event e) {
						ShareUtils.shareDirContents(menuParent.getShell(), true);
					}
				});
		return file_share_dircontents_rec;
	}

	public static MenuItem addImportMenuItem(Menu menuParent) {
		MenuItem file_import = addMenuItem(menuParent, MENU_ID_IMPORT,
				new Listener() {
					public void handleEvent(Event e) {
						new ImportTorrentWizard();
					}
				});
		return file_import;
	}

	public static MenuItem addExportMenuItem(Menu menuParent) {
		MenuItem file_export = addMenuItem(menuParent, MENU_ID_EXPORT,
				new Listener() {
					public void handleEvent(Event e) {
						new ExportTorrentWizard();
					}
				});
		return file_export;
	}

	public static MenuItem addCloseWindowMenuItem(final Menu menuParent) {

		MenuItem closeWindow = addMenuItem(menuParent, MENU_ID_WINDOW_CLOSE,
				new Listener() {
					public void handleEvent(Event event) {
						Shell shell = menuParent.getShell();
						if (shell != null && !shell.isDisposed()) {
							menuParent.getShell().close();
						}
					}
				});
		return closeWindow;
	}

	public static MenuItem addCloseTabMenuItem(Menu menu) {
		final MenuItem menuItem = addMenuItem(menu, MENU_ID_CLOSE_TAB, new Listener() {
			public void handleEvent(Event event) {
				MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
				if (mdi != null) {
					MdiEntry currentEntry = mdi.getCurrentEntry();
					if (currentEntry != null && currentEntry.isCloseable()) {
						mdi.closeEntry(currentEntry.getId());
					}
				}
			}
		});
		menu.addMenuListener(new MenuListener() {
			public void menuShown(MenuEvent e) {
				MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
				if (mdi != null) {
					MdiEntry currentEntry = mdi.getCurrentEntry();
					if (currentEntry != null && currentEntry.isCloseable()) {
						menuItem.setEnabled(true);
						return;
					}
				}
				menuItem.setEnabled(false);
			}
			
			public void menuHidden(MenuEvent e) {
			}
		});
		return menuItem;
	}

	public static MenuItem addCloseDetailsMenuItem(Menu menu) {
		final MenuItem item = addMenuItem(menu, MENU_ID_CLOSE_ALL_DETAIL,
				new Listener() {
					public void handleEvent(Event e) {
						UIFunctionsManagerSWT.getUIFunctionsSWT().closeAllDetails();
					}
				});

		Listener enableHandler = new Listener() {
			public void handleEvent(Event event) {
				if (true == MenuFactory.isEnabledForCurrentMode(item)) {
					if (false == item.isDisposed() && false == event.widget.isDisposed()) {
						boolean hasDetails = UIFunctionsManagerSWT.getUIFunctionsSWT().hasDetailViews();
						item.setEnabled(hasDetails);
					}
				}
			}
		};

		menu.addListener(SWT.Show, enableHandler);

		return item;
	}

	public static MenuItem addCloseDownloadBarsToMenu(Menu menu) {
		final MenuItem item = addMenuItem(menu, MENU_ID_CLOSE_ALL_DL_BARS,
				new Listener() {
					public void handleEvent(Event e) {
						MiniBarManager.getManager().closeAll();
					}
				});

		Listener enableHandler = new Listener() {
			public void handleEvent(Event event) {
				if (false == item.isDisposed()) {
					item.setEnabled(false == MiniBarManager.getManager().getShellManager().isEmpty());
				}
			}
		};
		menu.addListener(SWT.Show, enableHandler);
		//		shell.addListener(SWT.FocusIn, enableHandler);
		return item;
	}

	public static MenuItem addRestartMenuItem(Menu menuParent) {
		MenuItem file_restart = new MenuItem(menuParent, SWT.NULL);
		Messages.setLanguageText(file_restart, MENU_ID_RESTART); //$NON-NLS-1$

		file_restart.addListener(SWT.Selection, new Listener() {

			public void handleEvent(Event event) {
				UIFunctionsManagerSWT.getUIFunctionsSWT().dispose(true, false);
			}
		});
		return file_restart;
	}

	public static MenuItem addExitMenuItem(Menu menuParent) {
		final MenuItem file_exit = new MenuItem(menuParent, SWT.NULL);
		if (!COConfigurationManager.getBooleanParameter("Enable System Tray")
				|| !COConfigurationManager.getBooleanParameter("Close To Tray")) {
			KeyBindings.setAccelerator(file_exit, MENU_ID_EXIT);
		}
		Messages.setLanguageText(file_exit, MENU_ID_EXIT); //$NON-NLS-1$

		file_exit.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				UIFunctionsManagerSWT.getUIFunctionsSWT().dispose(false, false);
			}
		});

		// let platform decide
		ParameterListener paramListener = new ParameterListener() {
			public void parameterChanged(String parameterName) {
				if (COConfigurationManager.getBooleanParameter("Enable System Tray")
						&& COConfigurationManager.getBooleanParameter("Close To Tray")) {
					KeyBindings.removeAccelerator(file_exit, MENU_ID_EXIT);
				} else {
					KeyBindings.setAccelerator(file_exit, MENU_ID_EXIT);
				}
			}
		};
		COConfigurationManager.addParameterListener("Enable System Tray",
				paramListener);
		COConfigurationManager.addParameterListener("Close To Tray", paramListener);
		return file_exit;
	}

	public static MenuItem addStartAllMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_START_ALL_TRANSFERS,
				new ListenerNeedingCoreRunning() {
					public void handleEvent(AzureusCore core, Event e) {
						core.getGlobalManager().startAllDownloads();
				/*
				 * KN: Not sure why we can not use the call below as opposed to the line above
				 *  which was the exiting code
				 */
				// ManagerUtils.asyncStartAll();
			}
		});
	}

	public static MenuItem addStopAllMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_STOP_ALL_TRANSFERS, new Listener() {
			public void handleEvent(Event event) {
				ManagerUtils.asyncStopAll();
			}
		});
	}

	public static MenuItem addPauseMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_PAUSE_TRANSFERS, new Listener() {
			public void handleEvent(Event event) {
				ManagerUtils.asyncPause();
			}
		});
	}

	public static MenuItem addPauseForMenuItem(final Menu menu) {
		return addMenuItem(menu, MENU_ID_PAUSE_TRANSFERS_FOR, new ListenerNeedingCoreRunning() {
			public void handleEvent(AzureusCore core, Event event) {
				
				String text = MessageText.getString( "dialog.pause.for.period.text" );
				
				int rem = core.getGlobalManager().getPauseDownloadPeriodRemaining();
				
				if ( rem > 0 ){
										
					text += "\n\n" + MessageText.getString( "dialog.pause.for.period.text2", new String[]{ TimeFormatter.format2( rem, true ) });
				}
				
				SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
						"dialog.pause.for.period.title",
						"!" + text + "!");
				
				int def = COConfigurationManager.getIntParameter( "pause.for.period.default", 10 );
				
				entryWindow.setPreenteredText( String.valueOf( def ), false );
				
				entryWindow.prompt(new UIInputReceiverListener() {
					public void UIInputReceiverClosed(UIInputReceiver entryWindow) {
						if (!entryWindow.hasSubmittedInput()) {
							return;
						}
						String sReturn = entryWindow.getSubmittedInput();
						
						if (sReturn == null)
							return;
						
						int mins = -1;
						try {
							mins = Integer.valueOf(sReturn).intValue();
						} catch (NumberFormatException er) {
							// Ignore
						}
												
						if (mins <= 0) {
							MessageBox mb = new MessageBox(menu.getShell(), SWT.ICON_ERROR
									| SWT.OK);
							mb.setText(MessageText.getString("MyTorrentsView.dialog.NumberError.title"));
							mb.setMessage(MessageText.getString("MyTorrentsView.dialog.NumberError.text"));
							
							mb.open();
							return;
						}
						
						COConfigurationManager.setParameter( "pause.for.period.default", mins );
						
						ManagerUtils.asyncPauseForPeriod( mins*60 );
					}
				});
			}
		});
	}
	public static MenuItem addResumeMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_RESUME_TRANSFERS, new Listener() {
			public void handleEvent(Event event) {
				ManagerUtils.asyncResume();
			}
		});
	}

	public static MenuItem addMyTorrentsMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_MY_TORRENTS, new Listener() {
			public void handleEvent(Event e) {
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				if (uiFunctions != null) {
					uiFunctions.getMDI().showEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_LIBRARY);
				}
			}
		});
	}
	
	public static MenuItem addAllPeersMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_ALL_PEERS, new Listener() {
			public void handleEvent(Event e) {
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				if (uiFunctions != null) {
					uiFunctions.getMDI().showEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_ALLPEERS);
				}
			}
		});
	}

	public static MenuItem addClientStatsMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_CLIENT_STATS, new Listener() {
			public void handleEvent(Event e) {
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				if (uiFunctions != null) {
					uiFunctions.getMDI().showEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_CLIENT_STATS);
				}
			}
		});
	}

	public static MenuItem addDeviceManagerMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_DEVICEMANAGER, new Listener() {
			public void handleEvent(Event e) {
				MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
				mdi.showEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_DEVICES);
			}
		});
	}

	public static MenuItem addSubscriptionMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_SUBSCRIPTIONS, new Listener() {
			public void handleEvent(Event e) {
				MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
				mdi.showEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_SUBSCRIPTIONS);
			}
		});
	}

	public static MenuItem addMyTrackerMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_MY_TRACKERS, new Listener() {
			public void handleEvent(Event e) {
				MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
				mdi.showEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_MY_TRACKER);
			}
		});
	}

	public static MenuItem addMySharesMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_MY_SHARES, new Listener() {
			public void handleEvent(Event e) {
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				if (uiFunctions != null) {
					uiFunctions.getMDI().showEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_MY_SHARES);
				}
			}
		});
	}

	public static MenuItem addViewToolbarMenuItem(Menu menu) {
		final MenuItem item = addMenuItem(menu, SWT.CHECK, MENU_ID_TOOLBAR,
				new Listener() {
					public void handleEvent(Event e) {
						UIFunctionsSWT uiFunctions = getUIFunctionSWT();
						if (null != uiFunctions) {
							IMainWindow mainWindow = uiFunctions.getMainWindow();
							boolean isToolbarVisible = mainWindow.isVisible(IMainWindow.WINDOW_ELEMENT_TOOLBAR);
							mainWindow.setVisible(IMainWindow.WINDOW_ELEMENT_TOOLBAR,
									!isToolbarVisible);
						}
					}
				});

		final ParameterListener listener = new ParameterListener() {
			public void parameterChanged(String parameterName) {
				item.setSelection(COConfigurationManager.getBooleanParameter(parameterName));
			}
		};

		COConfigurationManager.addAndFireParameterListener("IconBar.enabled",
				listener);
		item.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				COConfigurationManager.removeParameterListener("IconBar.enabled",
						listener);
			}
		});
		return item;
	}

	public static MenuItem addTransferBarToMenu(final Menu menu) {
		final MenuItem item = addMenuItem(menu, SWT.CHECK, MENU_ID_TRANSFER_BAR,
				new ListenerNeedingCoreRunning() {
					public void handleEvent(AzureusCore core, Event e) {
						if (AllTransfersBar.getManager().isOpen(
								core.getGlobalManager())) {
							AllTransfersBar.closeAllTransfersBar();
						} else {
							AllTransfersBar.open(menu.getShell());
						}
					}
				});
		item.setSelection(!MiniBarManager.getManager().getShellManager().isEmpty());

		menu.addListener(SWT.Show, new Listener() {
			public void handleEvent(Event event) {
				if (item.isDisposed()) {
					menu.removeListener(SWT.Show, this);
				} else {
					item.setSelection(!MiniBarManager.getManager().getShellManager().isEmpty());
				}
			}
		});
		return item;
	}
	
	public static MenuItem addSpeedLimitsToMenu(final Menu menuParent) {
		MenuItem speedLimitsMenuItem = createTopLevelMenuItem(menuParent,
				MENU_ID_SPEED_LIMITS);
		MenuBuildUtils.addMaintenanceListenerForMenu(speedLimitsMenuItem.getMenu(),
				new MenuBuildUtils.MenuBuilder() {
					public void 
					buildMenu(
						Menu menu, MenuEvent menuEvent) 
					{
						if ( AzureusCoreFactory.isCoreRunning()){
					
							AzureusCore core = AzureusCoreFactory.getSingleton();
							
							final SpeedLimitHandler slh = SpeedLimitHandler.getSingleton( core );
							
							MenuItem viewCurrentItem = new MenuItem(menu, SWT.PUSH);
							Messages.setLanguageText(viewCurrentItem, "MainWindow.menu.speed_limits.view_current" );
																	
							viewCurrentItem.addListener(
								SWT.Selection,
								new Listener()
								{
									public void 
									handleEvent(
										Event arg0 )
									{
										showText(
											"MainWindow.menu.speed_limits.info.title",
											"MainWindow.menu.speed_limits.info.curr",
											slh.getCurrent());
									}
								});
							
							java.util.List<String>	profiles = slh.getProfileNames();
							
							Menu profiles_menu = new Menu(menuParent.getShell(), SWT.DROP_DOWN);
							MenuItem profiles_item = new MenuItem( menu, SWT.CASCADE);
							profiles_item.setMenu(profiles_menu);

														
							Messages.setLanguageText(profiles_item, "MainWindow.menu.speed_limits.profiles" );

							if ( profiles.size() == 0 ){
								
								profiles_item.setEnabled( false );
								
							}else{
								
								for ( final String p: profiles ){
									
									Menu profile_menu = new Menu(menuParent.getShell(), SWT.DROP_DOWN);
									MenuItem profile_item = new MenuItem( profiles_menu, SWT.CASCADE);
									profile_item.setMenu(profile_menu);
									profile_item.setText( p );

									MenuItem loadItem = new MenuItem(profile_menu, SWT.PUSH);
									Messages.setLanguageText(loadItem, "MainWindow.menu.speed_limits.load" );
																			
									loadItem.addListener(
										SWT.Selection,
										new Listener()
										{
											public void 
											handleEvent(
												Event arg0 )
											{
												showText(
													"MainWindow.menu.speed_limits.info.title",
													MessageText.getString( "MainWindow.menu.speed_limits.info.prof", new String[]{ p }),
													slh.loadProfile( p ) );
											}
										});
									
									MenuItem viewItem = new MenuItem(profile_menu, SWT.PUSH);
									Messages.setLanguageText(viewItem, "MainWindow.menu.speed_limits.view" );
																			
									viewItem.addListener(
										SWT.Selection,
										new Listener()
										{
											public void 
											handleEvent(
												Event arg0 )
											{
												showText(
													"MainWindow.menu.speed_limits.info.title", 
													MessageText.getString( "MainWindow.menu.speed_limits.info.prof", new String[]{ p }),					
													slh.getProfile( p ) );
											}
										});
									
									addSeparatorMenuItem( profile_menu );
									
									MenuItem deleteItem = new MenuItem(profile_menu, SWT.PUSH);
									Messages.setLanguageText(deleteItem, "MainWindow.menu.speed_limits.delete" );
																			
									deleteItem.addListener(
										SWT.Selection,
										new Listener()
										{
											public void 
											handleEvent(
												Event arg0 )
											{
												slh.deleteProfile( p );
											}
										});
								}
							}
							
							MenuItem saveItem = new MenuItem(menu, SWT.PUSH);
							Messages.setLanguageText(saveItem, "MainWindow.menu.speed_limits.save_current" );
							
							saveItem.addListener(
								SWT.Selection,
								new Listener()
								{
									public void 
									handleEvent(
										Event arg0 )
									{
										UISWTInputReceiver entry = new SimpleTextEntryWindow();
										
										entry.allowEmptyInput( false );
										entry.setLocalisedTitle(MessageText.getString("MainWindow.menu.speed_limits.profile" ));
										entry.prompt(new UIInputReceiverListener() {
											public void UIInputReceiverClosed(UIInputReceiver entry) {
												if (!entry.hasSubmittedInput()){
													
													return;
												}
												
												String input = entry.getSubmittedInput().trim();
												
												if ( input.length() > 0 ){
																										
													showText(
														"MainWindow.menu.speed_limits.info.title", 
														MessageText.getString( "MainWindow.menu.speed_limits.info.prof", new String[]{ input }),					
														slh.saveProfile( input ) );
												}
											}
										});
									}
								});
							
							addSeparatorMenuItem( menu );
							
							MenuItem resetItem = new MenuItem(menu, SWT.PUSH);
							Messages.setLanguageText(resetItem, "MainWindow.menu.speed_limits.reset" );
																	
							resetItem.addListener(
								SWT.Selection,
								new Listener()
								{
									public void 
									handleEvent(
										Event arg0 )
									{
										showText(
											"MainWindow.menu.speed_limits.info.title", 
											"MainWindow.menu.speed_limits.info.curr",
											slh.reset());
									}
								});
							
						addSeparatorMenuItem( menu );
							
						MenuItem scheduleItem = new MenuItem(menu, SWT.PUSH);
						Messages.setLanguageText(scheduleItem, "MainWindow.menu.speed_limits.schedule" );
																
						scheduleItem.addListener(
							SWT.Selection,
							new Listener()
							{
								public void 
								handleEvent(
									Event arg0 )
								{
									Utils.execSWTThreadLater(
											1,
											new Runnable()
											{
												public void
												run()
												{
													java.util.List<String> lines = slh.getSchedule();
													
													StringBuffer	text = new StringBuffer( 80*lines.size());
													
													for ( String s: lines ){
														
														if ( text.length() > 0 ){
															
															text.append( "\n" );
														}
														
														text.append( s );
													}
													
													final TextViewerWindow viewer =
														new TextViewerWindow(
															"MainWindow.menu.speed_limits.schedule.title", 
															"MainWindow.menu.speed_limits.schedule.msg", 
															text.toString(), false );
													
													viewer.setEditable( true );
													
													viewer.addListener(
														new TextViewerWindow.TextViewerWindowListener()
														{
															public void 
															closed() 
															{
																String text = viewer.getText();
																
																String[] lines = text.split( "\n" );
																
																java.util.List<String> updated_lines = new ArrayList<String>( Arrays.asList( lines ));
																
																java.util.List<String> result = slh.setSchedule( updated_lines );
																
																if ( result != null && result.size() > 0 ){
																	
																	showText(
																			"MainWindow.menu.speed_limits.schedule.title", 
																			"MainWindow.menu.speed_limits.schedule.err", 
																			result );
																}
															}	
														});
												}
											});
								}
							});
						
						addSeparatorMenuItem( menu );
						
						MenuItem helpItem = new MenuItem(menu, SWT.PUSH);
						Messages.setLanguageText(helpItem, "MainWindow.menu.speed_limits.wiki" );
																
						helpItem.addListener(
							SWT.Selection,
							new Listener()
							{
								public void 
								handleEvent(
									Event arg0 )
								{
									Utils.launch( MessageText.getString("MainWindow.menu.speed_limits.wiki.url"));
								}
							});
						}
					}
				});
		
		return( speedLimitsMenuItem );
	}

	public static MenuItem addAdvancedHelpMenuItem(final Menu menuParent) {
		MenuItem advancedHelpMenuItem = createTopLevelMenuItem(menuParent,
				MENU_ID_ADVANCED_TOOLS);
		MenuBuildUtils.addMaintenanceListenerForMenu(advancedHelpMenuItem.getMenu(),
				new MenuBuildUtils.MenuBuilder() {
					public void 
					buildMenu(
						final Menu menu, MenuEvent menuEvent) 
					{
						MenuItem viewTorrent = new MenuItem(menu, SWT.PUSH);
						
						Messages.setLanguageText(viewTorrent, "torrent.view.info" );
																
						viewTorrent.addListener(
							SWT.Selection,
							new Listener()
							{
								public void 
								handleEvent(
									Event arg )
								{
									Utils.execSWTThreadLater(
											1,
											new Runnable()
											{
												public void
												run()
												{
													handleTorrentView();
												}
											});
								}
							});
						
						MenuItem fixTorrent = new MenuItem(menu, SWT.PUSH);
						
						Messages.setLanguageText(fixTorrent, "torrent.fix.corrupt" );
																
						fixTorrent.addListener(
							SWT.Selection,
							new Listener()
							{
								public void 
								handleEvent(
									Event arg )
								{
									Utils.execSWTThreadLater(
											1,
											new Runnable()
											{
												public void
												run()
												{
													handleTorrentFixup();
												}
											});
								}
							});
						
						MenuItem importXMLTorrent = new MenuItem(menu, SWT.PUSH);
						
						Messages.setLanguageText(importXMLTorrent, "importTorrentWizard.title" );
																
						importXMLTorrent.addListener(
							SWT.Selection,
							new Listener()
							{
								public void 
								handleEvent(
									Event arg )
								{
									Utils.execSWTThreadLater(
											1,
											new Runnable()
											{
												public void
												run()
												{
													new ImportTorrentWizard();
												}
											});
								}
							});
						
						MenuItem showChanges = new MenuItem(menu, SWT.PUSH);
						
						Messages.setLanguageText(showChanges, "show.config.changes" );
																
						showChanges.addListener(
							SWT.Selection,
							new Listener()
							{
								public void 
								handleEvent(
									Event arg )
								{
									Utils.execSWTThreadLater(
											1,
											new Runnable()
											{
												public void
												run()
												{
													handleShowChanges();
												}
											});
								}
							});
					}
		});
		
		return( advancedHelpMenuItem );
	}
	
	private static void
	handleTorrentView()
	{
		final Shell shell = Utils.findAnyShell();
		
		try{
			FileDialog dialog = new FileDialog( shell.getShell(), SWT.SYSTEM_MODAL | SWT.OPEN );
			
			dialog.setFilterExtensions(new String[] { "*.torrent", "*.tor", Constants.FILE_WILDCARD });
			
			dialog.setFilterNames(new String[] { "*.torrent", "*.tor", Constants.FILE_WILDCARD });
			
			dialog.setFilterPath( TorrentOpener.getFilterPathTorrent());
									
			dialog.setText(MessageText.getString( "torrent.fix.corrupt.browse" ));
			
			String str = dialog.open();
													
			if ( str != null ){
			
				TorrentOpener.setFilterPathTorrent( str );

				File file = new File( str );
				
				StringBuffer content = new StringBuffer();
				
				String NL = "\r\n";
				
				try{
					TOTorrent torrent = TOTorrentFactory.deserialiseFromBEncodedFile( file );
					
					LocaleUtilDecoder	locale_decoder = LocaleTorrentUtil.getTorrentEncoding( torrent );

					content.append( "Character Encoding:\t" + locale_decoder.getName() + NL );

					String display_name = locale_decoder.decodeString( torrent.getName());
					
					content.append( "Name:\t" + display_name + NL );
					
					byte[] hash = torrent.getHash();
					
					content.append( "Hash:\t" + ByteFormatter.encodeString( hash ) + NL );
										
					content.append( 
						"Size:\t" + DisplayFormatters.formatByteCountToKiBEtc( torrent.getSize()) + 
						", piece size=" + DisplayFormatters.formatByteCountToKiBEtc( torrent.getPieceLength()) + 
						", piece count=" + torrent.getPieces().length + NL );
					
					if ( torrent.getPrivate()){
						
						content.append( "Private Torrent" + NL );
					}
					
					URL announce_url = torrent.getAnnounceURL();
					
					if ( announce_url != null ){
					
						content.append( "Announce URL:\t" + announce_url + NL );
					}
					
					TOTorrentAnnounceURLSet[] sets = torrent.getAnnounceURLGroup().getAnnounceURLSets();
					
					if ( sets.length > 0 ){
						
						content.append( "Announce List" + NL );
						
						for ( TOTorrentAnnounceURLSet set: sets ){
							
							String x = "";
							
							URL[] urls = set.getAnnounceURLs();
							
							for ( URL u: urls ){
								
								x += ( x.length()==0?"":", ") + u;
							}
						
							content.append( "\t" + x + NL );
						}
					}
					
					content.append( "Magnet URI:\t" + UrlUtils.getMagnetURI( display_name, PluginCoreUtils.wrap( torrent )) + NL );

					long c_date = torrent.getCreationDate();
					
					if ( c_date > 0 ){
					
						content.append(  "Created On:\t" + DisplayFormatters.formatDate( c_date*1000) + NL );
					}
					
					byte[] created_by = torrent.getCreatedBy();
					
					if ( created_by != null ){
					
						content.append( "Created By:\t" + locale_decoder.decodeString( created_by ) + NL );
					}
					
					byte[] comment = torrent.getComment();
					
					if ( comment != null ){
					
						content.append( "Comment:\t" + locale_decoder.decodeString( comment ) + NL );
					}
					
					TOTorrentFile[] files = torrent.getFiles();
					
					content.append( "Files:\t" + files.length + " - simple=" + torrent.isSimpleTorrent() + NL );
					
					for ( TOTorrentFile tf: files ){
						
						byte[][] comps = tf.getPathComponents();
						
						String f_name = "";
						
						for ( byte[] comp: comps ){
							
							f_name += (f_name.length()==0?"":File.separator) + locale_decoder.decodeString( comp );
						}
						
						content.append( "\t" + f_name + "\t\t" + DisplayFormatters.formatByteCountToKiBEtc( tf.getLength()) + NL );
					}
					
				}catch( Throwable e ){
					
					content.append( Debug.getNestedExceptionMessage( e ));
				}
				
				new TextViewerWindow(
						MessageText.getString( "MainWindow.menu.quick_view" ) + ": " + file.getName(),
						null, content.toString(), false  );

			}
		}catch( Throwable e ){
			
			Debug.out( e );
		}
	}
	
	private static void
	handleTorrentFixup()
	{
			// had some OSX SWT crash issues in this code so moved everything async
		
		final Shell shell = Utils.findAnyShell();
		
		try{
			FileDialog dialog = new FileDialog( shell.getShell(), SWT.SYSTEM_MODAL | SWT.OPEN );
			
			dialog.setFilterExtensions(new String[] { "*.torrent", "*.tor", Constants.FILE_WILDCARD });
			
			dialog.setFilterNames(new String[] { "*.torrent", "*.tor", Constants.FILE_WILDCARD });
			
			dialog.setFilterPath( TorrentOpener.getFilterPathTorrent());
									
			dialog.setText(MessageText.getString( "torrent.fix.corrupt.browse" ));
			
			String str = dialog.open();
													
			if ( str != null ){
			
				TorrentOpener.setFilterPathTorrent( str );

				File file = new File( str );
				
				byte[] bytes = FileUtil.readFileAsByteArray( file );

				Map existing_map = BDecoder.decode( bytes );
				
				Map existing_info = (Map)existing_map.get( "info" );
				
				byte[]	existing_info_encoded = BEncoder.encode( existing_info );
				
				final TOTorrent t = TOTorrentFactory.deserialiseFromMap( existing_map );
				
				final byte[] old_hash = t.getHash();
				byte[] new_hash	= null;
				
				for ( int i=0;i<bytes.length-5;i++){
					
					if ( 	bytes[i] == ':' && 
							bytes[i+1] == 'i' && 
							bytes[i+2] == 'n' && 
							bytes[i+3] == 'f' && 
							bytes[i+4] == 'o' ){
							
						new_hash = new SHA1Simple().calculateHash( bytes, i+5, existing_info_encoded.length );
						
						break;
					}
				}
				
				if ( new_hash != null ){
					
					final byte[] f_new_hash = new_hash;
					
					Utils.execSWTThreadLater(
							1,
							new Runnable()
							{
								public void
								run()
								{
									String	title = MessageText.getString( "torrent.fix.corrupt.result.title" );
									
									if ( Arrays.equals( old_hash, f_new_hash )){
										
										MessageBoxShell mb = 
											new MessageBoxShell( SWT.OK, title, MessageText.getString( "torrent.fix.corrupt.result.nothing" ) ); 
						 						
						 				mb.setParent( shell );
						 				
						 				mb.open( null );
						 				
									}else{
										
										MessageBoxShell mb = 
											new MessageBoxShell( SWT.OK, title, MessageText.getString( "torrent.fix.corrupt.result.fixed", new String[]{ ByteFormatter.encodeString( f_new_hash ) })); 
						 						
						 				mb.setParent( shell );
						 				
						 				mb.open( null );
						 				
						 				mb.waitUntilClosed();
						 				
						 				try{
						 					t.setHashOverride( f_new_hash );
						 				
							 				Utils.execSWTThreadLater(
													1,
													new Runnable()
													{
														public void
														run()
														{
											 				FileDialog dialog2 = new FileDialog( shell, SWT.SYSTEM_MODAL | SWT.SAVE );
																												
															dialog2.setFilterPath( TorrentOpener.getFilterPathTorrent());
																					
															dialog2.setFilterExtensions(new String[]{ "*.torrent" });
															
															String str2 = dialog2.open();
																									
															if ( str2 != null ){
																
																if ( !( str2.toLowerCase( Locale.US ).endsWith( ".tor" ) || str2.toLowerCase( Locale.US ).endsWith( ".torrent" ))){
																	
																	str2 += ".torrent";
																}
																
																try{
																	t.serialiseToBEncodedFile( new File( str2 ));
																	
																}catch( Throwable e ){
																	
																	Debug.out( e );
																}
															}
														}
													});
						 				}catch( Throwable e ){
						 					
						 					Debug.out( e );
						 				}
									}
								}
							});
				}
			}
		}catch( Throwable e ){
			
			Debug.out( e );
		}
	}
	
	private static void
	handleShowChanges()
	{
		final String NL = "\r\n";
		
		StringWriter content = new StringWriter();
				
		content.append( "**** Please review the contents of this before submitting it ****" + NL + NL );
		
		content.append( "Settings" + NL );
		
		IndentWriter iw = new IndentWriter( new PrintWriter( content ));

		iw.indent();
		
		try{
			COConfigurationManager.dumpConfigChanges( iw );
		
		}finally{
			
			iw.exdent();

			iw.close();
		}
		
		AzureusCore	core = AzureusCoreFactory.getSingleton();
		
		content.append( "Plugins" + NL );
		
		PluginInterface[] plugins = core.getPluginManager().getPlugins();
		
		for ( PluginInterface pi: plugins ){
		
			if ( pi.getPluginState().isBuiltIn()){
				
				continue;
			}
			
			content.append( "    " + pi.getPluginName() + ": " + pi.getPluginVersion() + NL );
		}
				
		java.util.List<DownloadManager> dms = core.getGlobalManager().getDownloadManagers();
		
		content.append( "Downloads - " + dms.size() + NL );

		iw = new IndentWriter( new PrintWriter( content ));
		
		iw.indent();
		
		try{
			for ( DownloadManager dm: dms ){
				
				String	hash_str;
				
				try{
					byte[] hash = dm.getTorrent().getHash();
					
					hash_str = Base32.encode( hash ).substring( 0, 16 );
					
				}catch( Throwable e ){
					
					hash_str = "<no hash>";
				}
				
				content.append( "    " + hash_str + ": " + DisplayFormatters.formatDownloadStatus( dm ) + NL );
				
				iw.indent();
				
				dm.getDownloadState().dump( iw );
				
				try{
					
				}finally{
					
					iw.exdent();
				}
			}
		}finally{
			
			iw.exdent();

			iw.close();
		}
		
		content.append( "Categories" + NL );

		Category[] cats = CategoryManager.getCategories();
		
		iw = new IndentWriter( new PrintWriter( content ));

		iw.indent();

		try{
			for ( Category cat: cats ){
				
				iw.println( cat.getName());

				iw.indent();
				
				try{
					cat.dump( iw );
					
				}finally{
					
					iw.exdent();
				}
			}
		}finally{
			
			iw.exdent();

			iw.close();
		}
		
		content.append( "Speed Limits" + NL );

		iw = new IndentWriter( new PrintWriter( content ));

		iw.indent();

		try{
			SpeedLimitHandler.getSingleton( core ).dump( iw );
		
		}finally{
			
			iw.exdent();

			iw.close();
		}
		
		new TextViewerWindow(
				MessageText.getString( "config.changes.title" ),
				null, content.toString(), false  );

	}
	
	
	public static void
	showText(
		final String					title,
		final String					message,
		final java.util.List<String>	lines )
	{
		Utils.execSWTThreadLater(
			1,
			new Runnable()
			{
				public void
				run()
				{
					StringBuffer	text = new StringBuffer( lines.size() * 80 );
					
					for ( String s: lines ){
						
						if ( text.length() > 0 ){
							text.append( "\n" );
						}
						text.append( s );
					}
					
					TextViewerWindow viewer = new TextViewerWindow(title, message, text.toString(), false );
					
					viewer.setEditable( false );
				}
			});
	}
	
	public static MenuItem addBlockedIPsMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_IP_FILTER, new ListenerNeedingCoreRunning() {
			public void handleEvent(AzureusCore core, Event e) {
				BlockedIpsWindow.showBlockedIps(core,
						getUIFunctionSWT().getMainShell());
			}
		});
	}

	public static MenuItem addConsoleMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_CONSOLE, new Listener() {
			public void handleEvent(Event e) {
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				if (uiFunctions != null) {
					uiFunctions.getMDI().showEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_LOGGER);
				}
			}
		});
	}

	public static MenuItem addStatisticsMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_STATS, new Listener() {
			public void handleEvent(Event e) {
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				if (uiFunctions != null) {
					MultipleDocumentInterface mdi = uiFunctions.getMDI();
					mdi.showEntryByID(StatsView.VIEW_ID);
				}
			}
		});
	}

	public static MenuItem addNatTestMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_NAT_TEST, new Listener() {
			public void handleEvent(Event e) {
				new NatTestWindow();
			}
		});
	}

	public static MenuItem addNetStatusMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_NET_STATUS, new Listener() {
			public void handleEvent(Event e) {
				UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
				if (uiFunctions != null) {

					PluginsMenuHelper.IViewInfo[] views = PluginsMenuHelper.getInstance().getPluginViewsInfo();
					
					for ( PluginsMenuHelper.IViewInfo view: views ){
						
						String viewID = view.viewID;
						
						if ( viewID != null && viewID.equals( "aznetstatus" )){
							
							view.openView( uiFunctions );
						}
					}
				}
			}
		});
	}
	
	public static MenuItem addSpeedTestMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_SPEED_TEST, new Listener() {
			public void handleEvent(Event e) {
				CoreWaiterSWT.waitForCoreRunning(new AzureusCoreRunningListener() {
					public void azureusCoreRunning(AzureusCore core) {
						new SpeedTestWizard();
					}
				});
			}
		});
	}

	public static MenuItem addConfigWizardMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_CONFIGURE, new Listener() {
			public void handleEvent(Event e) {
				new ConfigureWizard(false,ConfigureWizard.WIZARD_MODE_FULL);
			}
		});
	}

	public static MenuItem addOptionsMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_OPTIONS, new Listener() {
			public void handleEvent(Event e) {
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				if (uiFunctions != null) {
					uiFunctions.getMDI().showEntryByID(
							MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG);
				}
			}
		});
	}

	public static MenuItem addMinimizeWindowMenuItem(Menu menu) {
		final Shell shell = menu.getShell();

		final MenuItem item = addMenuItem(menu, MENU_ID_WINDOW_MINIMIZE,
				new Listener() {
					public void handleEvent(Event event) {
						if (null == shell || shell.isDisposed()) {
							event.doit = false;
							return;
						}
						shell.setMinimized(true);
					}
				});

		Listener enableHandler = new Listener() {
			public void handleEvent(Event event) {
				if (null == shell || true == shell.isDisposed()
						|| true == item.isDisposed()) {
					event.doit = false;
					return;
				}

				if (((shell.getStyle() & SWT.MIN) != 0)) {
					item.setEnabled(false == shell.getMinimized());
				} else {
					item.setEnabled(false);
				}
			}
		};

		menu.addListener(SWT.Show, enableHandler);
		shell.addListener(SWT.FocusIn, enableHandler);
		shell.addListener(SWT.Iconify, enableHandler);
		shell.addListener(SWT.Deiconify, enableHandler);

		return item;
	}

	public static MenuItem addBringAllToFrontMenuItem(Menu menu) {
		final MenuItem item = addMenuItem(menu, MENU_ID_WINDOW_ALL_TO_FRONT,
				new Listener() {
					public void handleEvent(Event event) {
						Iterator<Shell> iter = ShellManager.sharedManager().getWindows();
						while (iter.hasNext()) {
							Shell shell = iter.next();
							if (!shell.isDisposed() && !shell.getMinimized())
								shell.open();
						}
					}
				});

		final Listener enableHandler = new Listener() {
			public void handleEvent(Event event) {
				if (item.isDisposed()) {
					return;
				}
				Iterator<Shell> iter = ShellManager.sharedManager().getWindows();
				boolean hasNonMaximizedShell = false;
				while (iter.hasNext()) {
					Shell shell = iter.next();
					if (false == shell.isDisposed() && false == shell.getMinimized()) {
						hasNonMaximizedShell = true;
						break;
					}
				}
				item.setEnabled(hasNonMaximizedShell);
			}
		};

		menu.addListener(SWT.Show, enableHandler);
		menu.getShell().addListener(SWT.FocusIn, enableHandler);

		ShellManager.sharedManager().addWindowAddedListener(enableHandler);
		ShellManager.sharedManager().addWindowRemovedListener(enableHandler);
		item.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent event) {
				ShellManager.sharedManager().removeWindowAddedListener(enableHandler);
				ShellManager.sharedManager().removeWindowRemovedListener(enableHandler);
			}
		});

		return item;
	}

	/**
	 * Appends the list of opened interactive windows to the bottom of the specified shell menu
	 * @param menuParent The shell menu
	 * @param shell
	 */
	public static void appendWindowMenuItems(final Menu menuParent) {
		final Shell shell = menuParent.getShell();
		final int numTopItems = menuParent.getItemCount();
		Listener rebuild = new Listener() {
			public void handleEvent(Event event) {
				try {
					if (menuParent.isDisposed() || shell.isDisposed())
						return;

					final int size = ShellManager.sharedManager().getSize();
					if (size == menuParent.getItemCount() - numTopItems) {
						for (int i = numTopItems; i < menuParent.getItemCount(); i++) {
							final MenuItem item = menuParent.getItem(i);
							item.setSelection(item.getData() == shell);
						}
						return;
					}

					for (int i = numTopItems; i < menuParent.getItemCount();)
						menuParent.getItem(i).dispose();

					Iterator<Shell> iter = ShellManager.sharedManager().getWindows();
					for (int i = 0; i < size; i++) {
						final Shell sh = iter.next();

						if (sh.isDisposed() || sh.getText().length() == 0)
							continue;

						final MenuItem item = new MenuItem(menuParent, SWT.CHECK);

						item.setText(sh.getText());
						item.setSelection(shell == sh);
						item.setData(sh);

						item.addSelectionListener(new SelectionAdapter() {
							public void widgetSelected(SelectionEvent event) {
								if (event.widget.isDisposed() || sh.isDisposed())
									return;

								if (sh.getMinimized())
									sh.setMinimized(false);

								sh.open();
							}
						});
					}
				} catch (Exception e) {
					Logger.log(new LogEvent(LogIDs.GUI, "rebuild menu error", e));
				}
			}
		};

		ShellManager.sharedManager().addWindowAddedListener(rebuild);
		ShellManager.sharedManager().addWindowRemovedListener(rebuild);
		shell.addListener(SWT.FocusIn, rebuild);
		menuParent.addListener(SWT.Show, rebuild);
	}

	public static MenuItem addZoomWindowMenuItem(Menu menuParent) {
		final Shell shell = menuParent.getShell();
		final MenuItem item = addMenuItem(menuParent, MENU_ID_WINDOW_ZOOM,
				new Listener() {
					public void handleEvent(Event event) {
						if (shell.isDisposed()) {
							event.doit = false;
							return;
						}
						shell.setMaximized(!shell.getMaximized());
					}
				});

		Listener enableHandler = new Listener() {
			public void handleEvent(Event event) {
				if ( !shell.isDisposed() && !item.isDisposed()) {
					if (false == Constants.isOSX) {
						if (true == shell.getMaximized()) {
							Messages.setLanguageText(
									item,
									MessageText.resolveLocalizationKey(MENU_ID_WINDOW_ZOOM_RESTORE));
						} else {
							Messages.setLanguageText(
									item,
									MessageText.resolveLocalizationKey(MENU_ID_WINDOW_ZOOM_MAXIMIZE));
						}
					}

					if (((shell.getStyle() & SWT.MAX) != 0)) {
						item.setEnabled(false == shell.getMinimized());
					} else {
						item.setEnabled(false);
					}
				}
			}
		};

		menuParent.addListener(SWT.Show, enableHandler);
		shell.addListener(SWT.FocusIn, enableHandler);
		shell.addListener(SWT.Iconify, enableHandler);
		shell.addListener(SWT.Deiconify, enableHandler);

		return item;
	}

	public static MenuItem addAboutMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_ABOUT, new Listener() {
			public void handleEvent(Event e) {
				AboutWindow.show();
			}
		});
	}

	public static MenuItem addHealthMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_HEALTH, new Listener() {
			public void handleEvent(Event e) {
				HealthHelpWindow.show(getDisplay());
			}
		});
	}

	public static MenuItem addWhatsNewMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_WHATS_NEW, new Listener() {
			public void handleEvent(Event e) {
				Utils.launch("http://plugins.vuze.com/changelog.php?version="
						+ Constants.AZUREUS_VERSION);
			}
		});
	}

	public static MenuItem addWikiMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_COMMUNITY_WIKI, new Listener() {
			public void handleEvent(Event e) {
				Utils.launch(Constants.AZUREUS_WIKI);
			}
		});
	}
	
	public static MenuItem addVoteMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_VOTE, new Listener() {
			public void handleEvent(Event e) {
				Utils.launch(MessageText.getString("vote.vuze.url"));
			}
		});
	}
	
	public static MenuItem addReleaseNotesMenuItem(final Menu menu) {
		return addMenuItem(menu, MENU_ID_RELEASE_NOTES, new Listener() {
			public void handleEvent(Event e) {
				new WelcomeWindow(menu.getShell());
			}
		});
	}

	
	public static MenuItem addHelpSupportMenuItem(Menu menu, final String support_url ) {
		return addMenuItem(menu, MENU_ID_HELP_SUPPORT, new Listener() {
			public void handleEvent(Event e) {
				Utils.launch( support_url );
			}
		});
	}
	
	public static MenuItem addDonationMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_DONATE, new Listener() {
      public void handleEvent(Event e) {
        DonationWindow.open(true, "menu");
      }
    });
	}

	
	public static MenuItem addGetPluginsMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_PLUGINS_HELP, new Listener() {
			public void handleEvent(Event e) {
				Utils.launch("http://plugins.vuze.com/plugin_list.php");
				//MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
				//mdi.showEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_ABOUTPLUGINS);
			}
		});
	}

	public static MenuItem addDebugHelpMenuItem(Menu menu) {
		return addMenuItem(menu, MENU_ID_DEBUG_HELP, new Listener() {
			public void handleEvent(Event e) {
				UIDebugGenerator.generate(Constants.APP_NAME + " "
						+ Constants.AZUREUS_VERSION, "Generated via Help Menu");
			}
		});
	}

	public static MenuItem addCheckUpdateMenuItem(final Menu menu) {
		return addMenuItem(menu, MENU_ID_UPDATE_CHECK, new ListenerNeedingCoreRunning() {
			public void handleEvent(AzureusCore core, Event e) {
				UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
				if (uiFunctions != null) {
					uiFunctions.bringToFront();
				}
				UpdateMonitor.getSingleton(core).performCheck(true, false, false,
						new UpdateCheckInstanceListener() {
							public void cancelled(UpdateCheckInstance instance) {
							}

							public void complete(UpdateCheckInstance instance) {
								Update[] updates = instance.getUpdates();
								boolean hasUpdates = false;
								for (Update update : updates) {
									if (update.getDownloaders().length > 0) {
										hasUpdates = true;
										break;
									}
								}
								if (!hasUpdates) {
									
									int build = Constants.getIncrementalBuild();
								
									if ( COConfigurationManager.getBooleanParameter( "Beta Programme Enabled" ) && build > 0 ){
										
										String build_str = "" + build;
										
										if ( build_str.length() == 1 ){
											
											build_str = "0" + build_str;
										}
										
										MessageBoxShell mb = new MessageBoxShell(
												SWT.ICON_INFORMATION | SWT.OK,
												"window.update.noupdates.beta", new String[]{ "B" + build_str });
										
										mb.open(null);
										
									}else{
										
										MessageBoxShell mb = new MessageBoxShell(
												SWT.ICON_INFORMATION | SWT.OK,
												"window.update.noupdates", (String[]) null);
										
										mb.open(null);
									}
								}
							}
						});
			}
		});
	}

	public static MenuItem addBetaMenuItem(Menu menuParent) {
		final MenuItem menuItem = addMenuItem(menuParent, MENU_ID_BETA_PROG,
				new Listener() {
					public void handleEvent(Event e) {
						new BetaWizard();
			}
		});

		COConfigurationManager.addAndFireParameterListener(
				"Beta Programme Enabled", new ParameterListener() {
					public void parameterChanged(String parameterName) {
						Utils.execSWTThread(new AERunnable() {
							public void runSupport() {
								if ( menuItem.isDisposed()){
									return;
								}
								boolean enabled = COConfigurationManager.getBooleanParameter("Beta Programme Enabled");
								Messages.setLanguageText(
										menuItem,
										MessageText.resolveLocalizationKey(MENU_ID_BETA_PROG
												+ (enabled ? ".off" : ".on")));
							}
						});
					}
				});

		return menuItem;
	}
	
	public static MenuItem addPluginInstallMenuItem(Menu menuParent) {
		return addMenuItem(menuParent, MENU_ID_PLUGINS_INSTALL,
				new Listener() {
					public void handleEvent(Event e) {
						new InstallPluginWizard();
			}
		});
	}

	public static MenuItem addPluginUnInstallMenuItem(Menu menuParent) {
		return addMenuItem(menuParent, MENU_ID_PLUGINS_UNINSTALL,
				new Listener() {
					public void handleEvent(Event e) {
						new UnInstallPluginWizard(getDisplay());
			}
		});
	}
	
	public static void
	addAlertsMenu(
		Menu					menu,
		boolean	createSubmenu,
		final DownloadManager[]	dms )
	{
		if ( dms.length == 0 ){
			
			return;
		}

		Menu alert_menu;

		if (createSubmenu) {
  		alert_menu = new Menu( menu.getShell(), SWT.DROP_DOWN );
  		
  		MenuItem alerts_item = new MenuItem( menu, SWT.CASCADE);
  		
  		Messages.setLanguageText( alerts_item, "ConfigView.section.interface.alerts" );
  		
  		alerts_item.setMenu(alert_menu);
		} else {
			alert_menu = menu;
		}
		
		String[][] alert_keys =
			{	{ "Play Download Finished", "playdownloadfinished" },
				{ "Play Download Finished Announcement", "playdownloadspeech" },
				{ "Popup Download Finished", "popupdownloadfinished" },
			};
		
		boolean[]	all_enabled = new boolean[ alert_keys.length ];
		
		Arrays.fill( all_enabled, true );
		
		for ( DownloadManager dm: dms ){
			
			DownloadManagerState state = dm.getDownloadState();
			
			Map map = state.getMapAttribute( DownloadManagerState.AT_DL_FILE_ALERTS );
			
			if ( map == null ){
				
				Arrays.fill( all_enabled, false );
				
			}else{
				
				for (int i=0;i<alert_keys.length;i++ ){
				
					if ( !map.containsKey( alert_keys[i][0] )){
						
						all_enabled[i] = false;
					}
				}
			}
		}
		
		for (int i=0;i<alert_keys.length;i++ ){
		
			final String[] entry = alert_keys[i];
			
			if ( i != 1 || Constants.isOSX ){
				
				final MenuItem item = new MenuItem( alert_menu, SWT.CHECK);

				item.setText( MessageText.getString( "ConfigView.label." + entry[1] ));
						
				item.setSelection( all_enabled[i]);
				
				item.addListener(
					SWT.Selection, 
					new Listener() 
					{
						public void 
						handleEvent(
							Event event ) 
						{
							boolean	selected = item.getSelection();
							
							for ( DownloadManager dm: dms ){
								
								DownloadManagerState state = dm.getDownloadState();
								
								Map map = state.getMapAttribute( DownloadManagerState.AT_DL_FILE_ALERTS );

								if ( map == null ){
									
									map = new HashMap();
									
								}else{
									
									map = new HashMap( map );
								}
								
								if ( selected ){
									
									map.put( entry[0], "" );
									
								}else{
									
									map.remove( entry[0] );
									
								}
								state.setMapAttribute( DownloadManagerState.AT_DL_FILE_ALERTS, map );
							}
						}
					});
			}
		}
	}
	
	public static void
	addAlertsMenu(
		Menu							menu,
		final DownloadManager			dm,
		final DiskManagerFileInfo[]		files )
	{
		if ( files.length == 0 ){
			
			return;
		}
		
		String[][] alert_keys =
			{	{ "Play File Finished", "playfilefinished" },
				{ "Play File Finished Announcement", "playfilespeech" },
				{ "Popup File Finished", "popupfilefinished" },
			};

				
		Menu alert_menu = new Menu( menu.getShell(), SWT.DROP_DOWN );
		
		MenuItem alerts_item = new MenuItem( menu, SWT.CASCADE);
		
		Messages.setLanguageText( alerts_item, "ConfigView.section.interface.alerts" );
		
		alerts_item.setMenu(alert_menu);
		
		boolean[]	all_enabled = new boolean[ alert_keys.length ];
		
		DownloadManagerState state = dm.getDownloadState();
		
		Map map = state.getMapAttribute( DownloadManagerState.AT_DL_FILE_ALERTS );
		
		if ( map != null ){

			Arrays.fill( all_enabled, true );
		
			for ( DiskManagerFileInfo file: files ){
			
				for (int i=0;i<alert_keys.length;i++ ){
				
					String key = String.valueOf( file.getIndex()) + "." + alert_keys[i][0];
					
					if ( !map.containsKey( key )){
						
						all_enabled[i] = false;
					}
				}
			}
		}
		
		for (int i=0;i<alert_keys.length;i++ ){
		
			final String[] entry = alert_keys[i];
			
			if ( i != 1 || Constants.isOSX ){
				
				final MenuItem item = new MenuItem( alert_menu, SWT.CHECK);

				item.setText( MessageText.getString( "ConfigView.label." + entry[1] ));
						
				item.setSelection( all_enabled[i]);
				
				item.addListener(
					SWT.Selection, 
					new Listener() 
					{
						public void 
						handleEvent(
							Event event ) 
						{
							DownloadManagerState state = dm.getDownloadState();

							Map map = state.getMapAttribute( DownloadManagerState.AT_DL_FILE_ALERTS );

							if ( map == null ){
								
								map = new HashMap();
								
							}else{
								
								map = new HashMap( map );
							}
							
							boolean	selected = item.getSelection();
							
							for ( DiskManagerFileInfo file: files ){
								
								String key = String.valueOf( file.getIndex()) + "." + entry[0];
								
								if ( selected ){
									
									map.put( key, "" );
									
								}else{
									
									map.remove( key );
								}
							}
							
							state.setMapAttribute( DownloadManagerState.AT_DL_FILE_ALERTS, map );
						}
					});
			}
		}
	}
	
	/**
	 * Creates a menu item that is simply a label; it does nothing is selected
	 * @param menu
	 * @param localizationKey
	 * @return
	 */
	public static final MenuItem addLabelMenuItem(Menu menu,
			String localizationKey) {
		MenuItem item = new MenuItem(menu, SWT.NULL);
		Messages.setLanguageText(item, localizationKey);
		item.setEnabled(false);
		return item;
	}

	public static void addPairingMenuItem(Menu menu) {
		MenuFactory.addMenuItem(menu, MENU_ID_PAIRING, new Listener() {
			public void handleEvent(Event e) {
				UIFunctionsSWT uiFunctionsSWT = UIFunctionsManagerSWT.getUIFunctionsSWT();
				if (uiFunctionsSWT != null) {
					uiFunctionsSWT.openRemotePairingWindow();
				}
			}
		});

	}
	//==========================

	public static MenuItem addSeparatorMenuItem(Menu menuParent) {
		return new MenuItem(menuParent, SWT.SEPARATOR);
	}

	public static MenuItem createTopLevelMenuItem(Menu menuParent,
			String localizationKey) {
		Menu menu = new Menu(menuParent.getShell(), SWT.DROP_DOWN);
		MenuItem menuItem = new MenuItem(menuParent, SWT.CASCADE);
		Messages.setLanguageText(menuItem, localizationKey);
		menuItem.setMenu(menu);

		/*
		 * A top level menu and its menu item has the same ID; this is used to locate them at runtime 
		 */
		menu.setData(KEY_MENU_ID, localizationKey);
		menuItem.setData(KEY_MENU_ID, localizationKey);

		return menuItem;
	}

	public static final MenuItem addMenuItem(Menu menu, String localizationKey,
			Listener selListener) {
		return addMenuItem(menu, localizationKey, selListener, SWT.NONE);
	}

	public static final MenuItem addMenuItem(Menu menu, String localizationKey,
			Listener selListener, int style) {
		MenuItem menuItem = new MenuItem(menu, style);
		Messages.setLanguageText(menuItem,
				MessageText.resolveLocalizationKey(localizationKey));
		KeyBindings.setAccelerator(menuItem,
				MessageText.resolveAcceleratorKey(localizationKey));
		if (null != selListener) {
			menuItem.addListener(SWT.Selection, selListener);
		}
		/*
		 * Using the localizationKey as the id for the menu item; this can be used to locate it at runtime
		 * using .KN: missing method pointers
		 */
		menuItem.setData(KEY_MENU_ID, localizationKey);
		return menuItem;
	}

	public static final MenuItem addMenuItem(Menu menu, int style,
			String localizationKey, Listener selListener) {
		return addMenuItem(menu, style, -1, localizationKey, selListener);
	}

	public static final MenuItem addMenuItem(Menu menu, int style, int index,
			String localizationKey, Listener selListener) {
		if (index < 0 || index > menu.getItemCount()) {
			index = menu.getItemCount();
		}
		MenuItem menuItem = new MenuItem(menu, style, index);
		Messages.setLanguageText(menuItem, localizationKey);
		KeyBindings.setAccelerator(menuItem, localizationKey);
		menuItem.addListener(SWT.Selection, selListener);
		/*
		 * Using the localizationKey as the id for the menu item; this can be used to locate it at runtime
		 * using .KN: missing method pointers
		 */
		menuItem.setData(KEY_MENU_ID, localizationKey);
		return menuItem;
	}

	private static UIFunctionsSWT getUIFunctionSWT() {
		UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
		if (null != uiFunctions) {
			return uiFunctions;
		}
		throw new IllegalStateException(
				"No instance of UIFunctionsSWT found; the UIFunctionsManager might not have been initialized properly");
	}

	private static Display getDisplay() {
		return SWTThread.getInstance().getDisplay();
	}

	public static void updateMenuText(Object menu) {
		if (menu == null)
			return;
		if (menu instanceof Menu) {
			MenuItem[] menus = ((Menu) menu).getItems();
			for (int i = 0; i < menus.length; i++) {
				updateMenuText(menus[i]);
			}
		} else if (menu instanceof MenuItem) {
			MenuItem item = (MenuItem) menu;
			if (item.getData(KEY_MENU_ID) instanceof String) {
				String localizationKey = (String) item.getData(KEY_MENU_ID);
				item.setText(MessageText.getString(localizationKey));
				KeyBindings.setAccelerator(item,
						MessageText.resolveAcceleratorKey(localizationKey));
				updateMenuText(item.getMenu());
			} else {
				Messages.updateLanguageForControl(item);
			}
		}
	}

	public static void performOneTimeDisable(MenuItem item,
			boolean affectsChildMenuItems) {
		item.setEnabled(false);
		if (affectsChildMenuItems) {
			Menu childMenu = item.getMenu();
			if (childMenu == null)
				return;

			for (int i = 0; i < childMenu.getItemCount(); i++) {
				childMenu.getItem(i).setEnabled(false);
			}
		}
	}

	/**
	 * Find and return the menu with the given id starting from the given menu
	 * 
	 * @param menuToStartWith
	 * @param idToMatch any of the menu keys listed in {@link org.gudy.azureus2.ui.swt.mainwindow.IMenuConstants}
	 * @return may return <code>null</code> if not found
	 */
	public static Menu findMenu(Menu menuToStartWith, String idToMatch) {

		/*
		 * This is a recursive method; it will start at the given menuToStartWith
		 * and recursively traverse to all its sub menus until a matching
		 * menu is found or until it has touched all sub menus
		 */
		if (null == menuToStartWith || true == menuToStartWith.isDisposed()
				|| null == idToMatch || idToMatch.length() < 1) {
			return null;
		}

		/*
		 * The given menuToStartWith may be the one we're looking for
		 */
		if (true == idToMatch.equals(getID(menuToStartWith))) {
			return menuToStartWith;
		}

		MenuItem[] items = menuToStartWith.getItems();

		/*
		 * Go deeper into each child to try and find it
		 */
		for (int i = 0; i < items.length; i++) {
			MenuItem item = items[i];
			Menu menuToFind = findMenu(item.getMenu(), idToMatch);
			if (null != menuToFind) {
				return menuToFind;
			}
		}

		return null;
	}

	/**
	 * Find and return the menu item with the given id starting from the given menu
	 * 
	 * @param menuToStartWith
	 * @param idToMatch any of the menu keys listed in {@link org.gudy.azureus2.ui.swt.mainwindow.IMenuConstants}
	 * @return may return <code>null</code> if not found
	 */
	public static MenuItem findMenuItem(Menu menuToStartWith, String idToMatch) {
		return findMenuItem(menuToStartWith, idToMatch, true);
	}

	public static MenuItem findMenuItem(Menu menuToStartWith, String idToMatch, boolean deep) {
		/*
		 * This is a recursive method; it will start at the given menuToStartWith
		 * and recursively traverse to all its sub menus until a matching
		 * menu item is found or until it has touched all existing menu items
		 */
		if (null == menuToStartWith || true == menuToStartWith.isDisposed()
				|| null == idToMatch || idToMatch.length() < 1) {
			return null;
		}

		MenuItem[] items = menuToStartWith.getItems();

		for (int i = 0; i < items.length; i++) {
			MenuItem item = items[i];
			if (true == idToMatch.equals(getID(item))) {
				return item;
			}

			if (deep) {
  			/*
  			 * Go deeper into each child to try and find it
  			 */
  			MenuItem menuItemToFind = findMenuItem(item.getMenu(), idToMatch);
  			if (null != menuItemToFind) {
  				return menuItemToFind;
  			}
			}
		}

		return null;
	}

	private static String getID(Widget widget) {
		if (null != widget && false == widget.isDisposed()) {
			Object id = widget.getData(KEY_MENU_ID);
			if (null != id) {
				return id.toString();
			}
		}
		return "";
	}

	public static void setEnablementKeys(Widget widget, int keys) {
		if (null != widget && false == widget.isDisposed()) {
			widget.setData(KEY_ENABLEMENT, new Integer(keys));
		}
	}

	public static int getEnablementKeys(Widget widget) {
		if (null != widget && false == widget.isDisposed()) {
			Object keys = widget.getData(KEY_ENABLEMENT);
			if (keys instanceof Integer) {
				return ((Integer) keys).intValue();
			}
		}
		return -1;
	}

	/**
	 * Updates the enabled state of the given menu and all its applicable children
	 * <p><b>NOTE:</b> This method currently iterates through the menu hierarchy to
	 * set the enablement which may be inefficient since most menus do not have this flag set;
	 * it may be desirable to employ a map of only the effected menus for efficient direct
	 * access to them</p>
	 * @param menuToStartWith
	 */
	public static void updateEnabledStates(Menu menuToStartWith) {
		/*
		 * This is a recursive method; it will start at the given menuToStartWith
		 * and recursively traverse to all its sub menus until a matching
		 * menu item is found or until it has touched all existing menu items
		 */
		if (null == menuToStartWith || true == menuToStartWith.isDisposed()) {
			return;
		}

		/*
		 * If the given menu itself is disabled then just return since
		 * its menu items can not be seen anyway
		 */
		if (false == setEnablement(menuToStartWith)) {
			return;
		}

		MenuItem[] items = menuToStartWith.getItems();

		for (int i = 0; i < items.length; i++) {
			MenuItem item = items[i];

			/*
			 * If the current menu item is disabled then just return since
			 * its children items can not be seen anyway
			 */
			if (false == setEnablement(item)) {
				continue;
			}

			/*
			 * Go deeper into the children items and set their enablement
			 */
			updateEnabledStates(item.getMenu());

		}
	}

	/**
	 * Sets whether the given widget is enabled or not based on the value of the
	 * KEY_ENABLEMENT object data set into the given widget.
	 * @param widget
	 * @return
	 */
	public static boolean setEnablement(Widget widget) {
		if (null != widget && false == widget.isDisposed()) {
			boolean isEnabled = isEnabledForCurrentMode(widget);

			if (widget instanceof MenuItem) {
				((MenuItem) widget).setEnabled(isEnabled);
			} else if (widget instanceof Menu) {
				((Menu) widget).setEnabled(isEnabled);
			}
			return isEnabled;
		}
		return false;
	}

	/**
	 * Returns whether the given widget should be enabled for the current mode;
	 * current mode can be az2, az3, or az3 advanced.
	 * @param widget
	 * @return
	 */
	public static boolean isEnabledForCurrentMode(Widget widget) {
		int keys = getEnablementKeys(widget);
		if (keys <= 0) {
			return true;
		} else if (true == isAZ3) {
			return ((keys & FOR_AZ3) != 0);
		} else {
			return ((keys & FOR_AZ2) != 0);
		}
	}

	private static final boolean DEBUG_SET_FOREGROUND = System.getProperty("debug.setforeground") != null;
	
	private static TableRow wrapAsRow(final Object o, final String table_name) {
		return new TableRow() {
			  public Object getDataSource() {return o;}
			  public String getTableID() {return table_name;}
			  
			  private void notSupported() {
				  throw new RuntimeException("method is not supported - table row is a \"virtual\" one, only getDataSource and getTableID are supported.");
			  }
			  
			  private void setForegroundDebug() {
				  if (DEBUG_SET_FOREGROUND) {
					  Debug.out("setForeground on fake TableRow");
				  }
			  }

			  // Everything below is unsupported.
			  public int getIndex() {notSupported(); return 0;}
			  public void setForeground(int red, int green, int blue) {setForegroundDebug(); notSupported();}
			  public void setForeground(int[] rgb) {setForegroundDebug(); notSupported();}
			  public void setForegroundToErrorColor() {setForegroundDebug(); notSupported();}
			  public boolean isValid() {notSupported(); return false;}
			  public TableCell getTableCell(String sColumnName) {notSupported(); return null;}
			  public boolean isSelected()  {notSupported(); return false;}
			  public void addMouseListener(TableRowMouseListener listener) {notSupported();}
			  public void removeMouseListener(TableRowMouseListener listener) {notSupported();}
			  public Object getData(String id) {return null;}
			  public void setData(String id, Object data) {}
		};
	}
}
