/**
 * Created on May 6, 2008
 *
 * Copyright 2008 Vuze, Inc.  All rights reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA 
 */

package com.aelitis.azureus.ui.selectedcontent;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the currently selected content in the visible display
 * 
 * @author TuxPaper
 * @created May 6, 2008
 *
 */
public class SelectedContentManager
{
	private static List listeners = new ArrayList();

	private static ISelectedContent[] currentlySelectedContent = new ISelectedContent[0];
	
	private static String viewID = null;

	public static void addCurrentlySelectedContentListener(
			SelectedContentListener l) {
		listeners.add(l);
		l.currentlySectedContentChanged(currentlySelectedContent);
	}

	public static void changeCurrentlySelectedContent(String viewID,
			ISelectedContent[] currentlySelectedContent) {
		if (currentlySelectedContent == null) {
			currentlySelectedContent = new ISelectedContent[0];
		}
		if (currentlySelectedContent.length == 0
				&& SelectedContentManager.viewID != null
				&& !viewID.equals(SelectedContentManager.viewID)) {
			// don't allow clearing if someone else set the currently selected
			return;
		}

		SelectedContentManager.currentlySelectedContent = currentlySelectedContent == null
				? new ISelectedContent[0] : currentlySelectedContent;
		SelectedContentManager.viewID = viewID;

		Object[] listenerArray = listeners.toArray();
		for (int i = 0; i < listenerArray.length; i++) {
			SelectedContentListener l = (SelectedContentListener) listenerArray[i];
			l.currentlySectedContentChanged(SelectedContentManager.currentlySelectedContent);
		}
	}

	public static ISelectedContent[] getCurrentlySelectedContent() {
		return currentlySelectedContent;
	}
}
