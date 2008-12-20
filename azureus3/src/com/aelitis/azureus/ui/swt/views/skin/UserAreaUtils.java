/**
 * Copyright (C) 2007 Aelitis, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * AELITIS, SAS au capital de 63.529,40 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package com.aelitis.azureus.ui.swt.views.skin;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.FileUtil;
import org.gudy.azureus2.core3.util.SystemProperties;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.ui.swt.Utils;

import com.aelitis.azureus.buddy.impl.VuzeBuddyManager;
import com.aelitis.azureus.core.cnetwork.ContentNetwork;
import com.aelitis.azureus.ui.skin.SkinConstants;
import com.aelitis.azureus.ui.swt.UIFunctionsSWT;
import com.aelitis.azureus.ui.swt.shells.LightBoxBrowserWindow;
import com.aelitis.azureus.ui.swt.skin.*;
import com.aelitis.azureus.ui.swt.skin.SWTSkinButtonUtility.ButtonListenerAdapter;
import com.aelitis.azureus.ui.swt.utils.ImageLoaderFactory;
import com.aelitis.azureus.ui.swt.utils.SWTLoginUtils;
import com.aelitis.azureus.ui.swt.utils.ImageLoader.ImageDownloaderListener;
import com.aelitis.azureus.util.*;
import com.aelitis.azureus.util.LoginInfoManager.LoginInfo;

public class UserAreaUtils
{
	private SWTSkin skin;

	private UIFunctionsSWT uiFunctions = null;

	private boolean firstLoginStateSync = true;

	private SWTSkinObjectImage soImage;

	public UserAreaUtils(final SWTSkin skin, UIFunctionsSWT uiFunctions) {
		this.skin = skin;
		this.uiFunctions = uiFunctions;

		updateLoginLabels(null);
		
		hookListeners();
		
	}

	private void hookListeners() {

		/*
		 * New user-info (drop down arrow)
		 */

		SWTSkinObject skinObject = skin.getSkinObject("user-info-image");
		if (skinObject != null) {
  		final Control control = skinObject.getControl();
  		final Menu menu = new Menu(control.getShell(), SWT.POP_UP);
  		fillUserInfoMenu(menu);
  
  		menu.addListener(SWT.Show, new Listener() {
  			public void handleEvent(Event event) {
  				MenuItem[] menuItems = menu.getItems();
  				for (int i = 0; i < menuItems.length; i++) {
  					menuItems[i].dispose();
  				}
  
  				fillUserInfoMenu(menu);
  			}
  		});

			SWTSkinButtonUtility btnGo = new SWTSkinButtonUtility(skinObject);
			btnGo.addSelectionListener(new ButtonListenerAdapter() {
				public void pressed(SWTSkinButtonUtility buttonUtility, SWTSkinObject skinObject, int stateMask) {
					Point point = control.getShell().toDisplay(
							control.getParent().getLocation());
					point.y += (control.getSize().y / 2) + 10;
					menu.setLocation(point);
					menu.setVisible(true);
				}
			});
		}

		/*
		 * New user-info (name)
		 */
		skinObject = skin.getSkinObject("user-info-name");

		if (skinObject != null) {
			SWTSkinButtonUtility btnGo = new SWTSkinButtonUtility(skinObject);
			btnGo.addSelectionListener(new ButtonListenerAdapter() {
				public void pressed(SWTSkinButtonUtility buttonUtility, SWTSkinObject skinObject, int stateMask) {
					if (true == LoginInfoManager.getInstance().isLoggedIn()) {
						/*
						 * If the user is logged in then go to profile page
						 */
						if (null != uiFunctions) {
							String url = ConstantsV3.DEFAULT_CONTENT_NETWORK.getServiceURL( ContentNetwork.SERVICE_MY_PROFILE );
							uiFunctions.viewURL(url, SkinConstants.VIEWID_BROWSER_BROWSE, 0,
									0, true, true);
						}

					} else {
						/*
						 * If the user it not logged in then go to SignIn
						 */

						SWTLoginUtils.openLoginWindow();

					}

				}
			});
		}
		
		skinObject = skin.getSkinObject("user-info-profile-image");
		if (skinObject instanceof SWTSkinObjectImage) {
			soImage = (SWTSkinObjectImage) skinObject;
		}

		/*
		 * Listens for changes in the login state and update the UI appropriately
		 */
		LoginInfoManager.getInstance().addListener(new ILoginInfoListener() {
			public void loginUpdate(LoginInfo info, boolean isNewLoginID) {
				synchLoginStates(info, isNewLoginID);
			}
			
			// @see com.aelitis.azureus.util.ILoginInfoListener#avatarURLUpdated()
			public void avatarURLUpdated(String newAvatarURL) {
				soImage.setImageUrl(newAvatarURL);
			}
		});
	}

	/**
	 * Updates the login/logout labels and also resets all embedded browsers
	 * @param userName
	 * @param displayName
	 * @param isNewLoginID
	 */
	private void synchLoginStates(LoginInfo info, boolean isNewLoginID) {

		updateLoginLabels(info);

		if (firstLoginStateSync) {
			firstLoginStateSync = false;
			return;
		}

		// 3.2 TODO: These are different now that we have a sidebar
		/*
		 * Reset browser tabs if the login state has changed
		 */
		if (true == isNewLoginID) {
			/*
			 * If the user has logged out (user name is null) then reset all pages to their original URL's
			 */
			if (null == info.userName) {
				resetBrowserPage(SkinConstants.VIEWID_BROWSER_BROWSE);
				resetBrowserPage(SkinConstants.VIEWID_BROWSER_PUBLISH);
			} else {

				/*
				 * Otherwise just refresh the current URL so the pages can be re-loaded with fresh information
				 */
				refreshBrowserPage(SkinConstants.VIEWID_BROWSER_BROWSE);
				refreshBrowserPage(SkinConstants.VIEWID_BROWSER_PUBLISH);
			}
		}
	}

	/**
	 * Updates the login/logout labels to reflect the user's login state
	 * @param userName
	 * @param displayName
	 */
	private void updateLoginLabels(LoginInfo info) {
		if (info != null && null != info.userName) {
			SWTSkinObject skinObjectName = skin.getSkinObject("user-info-name");
			if (skinObjectName instanceof SWTSkinObjectText) {
				if (null != info.displayName) {
					((SWTSkinObjectText) skinObjectName).setText(info.displayName);
				} else {
					((SWTSkinObjectText) skinObjectName).setText(info.userName);
				}
			}

		} else {
			SWTSkinObject skinObjectName = skin.getSkinObject("user-info-name");
			if (skinObjectName instanceof SWTSkinObjectText) {
				((SWTSkinObjectText) skinObjectName).setTextID("v3.MainWindow.text.log.in");
			}

		}

		/*
		 * Make sure it's now visible since it was initialized as invisible
		 */
		SWTSkinObject skinObject = skin.getSkinObject("user-info");
		if (null != skinObject) {
			if (false == skinObject.isVisible()) {
				skinObject.setVisible(true);
			}
		}

		Utils.execSWTThread(new Runnable() {
			public void run() {
				SWTSkinObject skinObject = skin.getSkinObject("user-area");
				if (null != skinObject) {
					Utils.relayout(skinObject.getControl());
				}
			}
		});

	}

	/**
	 * Resets the embedded browser with the given viewID
	 * @param targetViewID
	 */
	private void resetBrowserPage(String targetViewID) {
		SWTSkinObject skinObject = skin.getSkinObject(targetViewID);
		if (skinObject instanceof SWTSkinObjectBrowser) {
			((SWTSkinObjectBrowser) skinObject).restart();
		}
	}

	/**
	 * Refreshes the embedded browser with the given viewID
	 * @param targetViewID
	 */
	private void refreshBrowserPage(final String targetViewID) {
		Utils.execSWTThread(new AERunnable() {
			public void runSupport() {
				final SWTSkinObject skinObject = skin.getSkinObject(targetViewID);
				if (skinObject instanceof SWTSkinObjectBrowser) {
					((SWTSkinObjectBrowser) skinObject).getBrowser().refresh();
				}
			}
		});
	}

	/**
	 * Fill the menu with the appropriate items for the user info drop down
	 * @param menu
	 */
	private void fillUserInfoMenu(Menu menu) {

		if (true == LoginInfoManager.getInstance().isLoggedIn()) {

			/*
			 * Account info
			 */
			MenuItem item = new MenuItem(menu, SWT.PUSH);
			item.setText(MessageText.getString("v3.MainWindow.text.my.account"));
			item.addSelectionListener(new SelectionListener() {

				public void widgetSelected(SelectionEvent e) {
					if (null != uiFunctions) {
						String url = ContentNetworkUtils.getUrl(
								ConstantsV3.DEFAULT_CONTENT_NETWORK,
								ContentNetwork.SERVICE_MY_ACCOUNT);
						if (url == null) {
							return;
						}
						uiFunctions.viewURL(url, SkinConstants.VIEWID_BROWSER_BROWSE, 0, 0,
								true, true);
					}

				}

				public void widgetDefaultSelected(SelectionEvent e) {
					widgetSelected(e);
				}
			});

			/*
			 * Profile
			 */

			item = new MenuItem(menu, SWT.PUSH);
			item.setText(MessageText.getString("v3.MainWindow.text.my.profile"));
			item.addSelectionListener(new SelectionListener() {

				public void widgetSelected(SelectionEvent e) {
					if (true == LoginInfoManager.getInstance().isLoggedIn()) {
						/*
						 * If the user is logged in then go to profile page
						 */
						if (null != uiFunctions) {
							String url = ContentNetworkUtils.getUrl(
									ConstantsV3.DEFAULT_CONTENT_NETWORK,
									ContentNetwork.SERVICE_MY_PROFILE);
							uiFunctions.viewURL(url, SkinConstants.VIEWID_BROWSER_BROWSE, 0,
									0, true, true);
						}

					} else {
						/*
						 * If the user it not logged in then go to SignIn
						 */

						SWTLoginUtils.openLoginWindow();

					}

				}

				public void widgetDefaultSelected(SelectionEvent e) {
					widgetSelected(e);
				}
			});

			item = new MenuItem(menu, SWT.SEPARATOR);

			/*
			 * Logout
			 */
			item = new MenuItem(menu, SWT.PUSH);
			item.setText(MessageText.getString("v3.MainWindow.text.log.out"));
			item.addSelectionListener(new SelectionListener() {

				public void widgetSelected(SelectionEvent e) {
					widgetDefaultSelected(e);
				}

				public void widgetDefaultSelected(SelectionEvent e) {

					/*
					 * We log out by opening the following URL in a browser.  The page
					 * that is loaded will send a 'status:login-update' message which the 
					 * ILoginInfoListener will respond to and update the UI accordingly
					 */
					final String url = ContentNetworkUtils.getUrl(
							ConstantsV3.DEFAULT_CONTENT_NETWORK,
							ContentNetwork.SERVICE_LOGOUT);

					/*
					 * Loads the page without switching to the On Vuze tab
					 */
					SWTSkinObject skinObject = skin.getSkinObject(SkinConstants.VIEWID_BROWSER_BROWSE);
					if (skinObject instanceof SWTSkinObjectBrowser) {

						/*
						 * KN: Temporary fix for sign-in lead to sign-out when 'browse' tab have not been initialized problem
						 */
						Browser browser = ((SWTSkinObjectBrowser) skinObject).getBrowser();
						if (null != browser) {
							String existingURL = browser.getUrl();
							if (null == existingURL || existingURL.length() < 1) {
								((SWTSkinObjectBrowser) skinObject).setStartURL(ContentNetworkUtils.getUrl(
										ConstantsV3.DEFAULT_CONTENT_NETWORK,
										ContentNetwork.SERVICE_BIG_BROWSE));
							}
						}

						((SWTSkinObjectBrowser) skinObject).setURL(url);
					}
				}
			});

		} else {

			LoginInfo info = LoginInfoManager.getInstance().getUserInfo();

			/*
			 * Account info
			 */
			MenuItem item = new MenuItem(menu, SWT.PUSH);
			item.setText(MessageText.getString("v3.MainWindow.text.my.account"));
			item.setEnabled(false);

			/*
			 * Profile
			 */

			item = new MenuItem(menu, SWT.PUSH);
			item.setText(MessageText.getString("v3.MainWindow.text.my.profile"));
			item.setEnabled(false);

			/*
			 * Sign Up -- Only show if this client instance has not been registered already
			 */
			if (true == info.isRegistrationStillOpen) {
				item = new MenuItem(menu, SWT.SEPARATOR);

				item = new MenuItem(menu, SWT.PUSH);
				item.setText(MessageText.getString("v3.MainWindow.text.get.started"));
				item.addSelectionListener(new SelectionListener() {

					public void widgetSelected(SelectionEvent e) {
						if (null != uiFunctions) {
							String url = ContentNetworkUtils.getUrl(
									ConstantsV3.DEFAULT_CONTENT_NETWORK,
									ContentNetwork.SERVICE_REGISTER);
							if (url == null) {
								return;
							}
							new LightBoxBrowserWindow(url, ConstantsV3.URL_PAGE_VERIFIER_VALUE,
									460, 577);
						}

					}

					public void widgetDefaultSelected(SelectionEvent e) {
						widgetSelected(e);
					}
				});
			}
		}

	}
}
