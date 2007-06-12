/**
 * Copyright (C) 2006 Aelitis, All Rights Reserved.
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

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.ui.swt.browser.listener.publish.DownloadStateAndRemoveListener;
import com.aelitis.azureus.ui.swt.skin.SWTSkinObject;
import com.aelitis.azureus.ui.swt.skin.SWTSkinObjectBrowser;
import com.aelitis.azureus.ui.swt.utils.PublishUtils;
import com.aelitis.azureus.util.Constants;

import org.gudy.azureus2.plugins.*;

/**
 * @author TuxPaper
 * @created Oct 1, 2006
 *
 */
public class Publish
	extends SkinView
{
	private SWTSkinObjectBrowser browserSkinObject;

	public Object showSupport(final SWTSkinObject skinObject, Object params) {
		browserSkinObject = (SWTSkinObjectBrowser) skinObject;

		AzureusCore core = AzureusCoreFactory.getSingleton();

		// Need to have a "azdirector" plugin interface because 
		// DownloadStateAndRemoveListener uses plugin attributes from it, and I'm
		// to lazy to refactor it atm

		// first, check if it's already there (evil!)
		PluginInterface pi = core.getPluginManager().getPluginInterfaceByID(
				"azdirector");
		if (pi == null) {
			PluginManager.registerPlugin(new Plugin() {
				public void initialize(PluginInterface pluginInterface)
						throws PluginException {
				}
			}, "azdirector");

			// initialization should be immediate, since the UI is running
			pi = core.getPluginManager().getPluginInterfaceByID("azdirector");
		}

		// copied from DirectorPlugin.java
		// We are going to monitor Published Torrent to alert the User when he 
		// removes a published torrent from azureus
		DownloadStateAndRemoveListener downloadListener = new DownloadStateAndRemoveListener(
				pi, skinObject.getControl().getDisplay());
		pi.getDownloadManager().addListener(downloadListener);

		PublishUtils.setupContext(browserSkinObject.getContext(), pi, downloadListener);

		String sURL = Constants.URL_PREFIX + Constants.URL_PUBLISH + "?"
				+ Constants.URL_SUFFIX;
		browserSkinObject.setURL(sURL);

		return null;
	}

	/**
	 * 
	 */
	public void restart() {
		browserSkinObject.restart();
	}
}
