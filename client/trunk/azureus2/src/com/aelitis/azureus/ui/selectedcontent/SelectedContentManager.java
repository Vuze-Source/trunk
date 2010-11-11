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

import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.util.Debug;

import com.aelitis.azureus.core.util.CopyOnWriteList;
import com.aelitis.azureus.ui.common.table.TableView;

/**
 * Manages the currently selected content in the visible display
 * 
 * @author TuxPaper
 * @created May 6, 2008
 *
 */
public class SelectedContentManager
{
	private static CopyOnWriteList<SelectedContentListener> listeners = new CopyOnWriteList<SelectedContentListener>();

	private static ISelectedContent[] currentlySelectedContent = new ISelectedContent[0];

	private static String viewID = null;
	
	private static TableView tv = null;

	public static String getCurrentySelectedViewID() {
		return viewID;
	}

	public static void addCurrentlySelectedContentListener(
			SelectedContentListener l) {
		listeners.add(l);
		l.currentlySelectedContentChanged(currentlySelectedContent, viewID);
	}

	public static void clearCurrentlySelectedContent() {
		changeCurrentlySelectedContent(null, null, null);
	}

	public static void changeCurrentlySelectedContent(String viewID,
			ISelectedContent[] currentlySelectedContent) {
		changeCurrentlySelectedContent(viewID, currentlySelectedContent, null);
	}

	public static void changeCurrentlySelectedContent(String viewID,
			ISelectedContent[] currentlySelectedContent, TableView tv) {
		if (currentlySelectedContent == null) {
			currentlySelectedContent = new ISelectedContent[0];
		}
		/*
		System.out.println("change CURSEL for '"
				+ viewID
				+ "' to "
				+ currentlySelectedContent.length
				+ ";"
				+ (currentlySelectedContent.length > 0 ? currentlySelectedContent[0]
						: "") + Debug.getCompressedStackTrace());
						*/
		if (currentlySelectedContent.length == 0
				&& SelectedContentManager.viewID != null && viewID != null
				&& !viewID.equals(SelectedContentManager.viewID)) {
			// don't allow clearing if someone else set the currently selected
			//System.out.println("-->abort because it's not " + SelectedContentManager.viewID);
			return;
		}

		synchronized( SelectedContentManager.class ){
			boolean	same = SelectedContentManager.tv == tv && tv != null;
			
			if ( same ){
				
				same = 
					SelectedContentManager.viewID == viewID ||
					( 	SelectedContentManager.viewID != null && 
						viewID != null &&
						SelectedContentManager.viewID.equals( viewID ));
				
				if ( same ){
					
					if ( SelectedContentManager.currentlySelectedContent.length == currentlySelectedContent.length ){
						
						for ( int i=0;i<currentlySelectedContent.length && same ;i++){
							
							same = currentlySelectedContent[i].sameAs( SelectedContentManager.currentlySelectedContent[i]);
						}
				
						if ( same ){
														
							return;
						}
					}
				}
			}
			
			SelectedContentManager.tv = tv;
			SelectedContentManager.currentlySelectedContent = currentlySelectedContent;
			SelectedContentManager.viewID = viewID;
		}
		
		for( SelectedContentListener l: listeners ){
	
			try{
				l.currentlySelectedContentChanged( currentlySelectedContent, viewID);
				
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
	}

	public static ISelectedContent[] getCurrentlySelectedContent() {
		return currentlySelectedContent;
	}
	
	public static DownloadManager[] getDMSFromSelectedContent() {
		ISelectedContent[] sc = SelectedContentManager.getCurrentlySelectedContent();
		if (sc.length > 0) {
			int x = 0;
			DownloadManager[] dms = new DownloadManager[sc.length];
			for (int i = 0; i < sc.length; i++) {
				ISelectedContent selectedContent = sc[i];
				if (selectedContent == null) {
					continue;
				}
				dms[x] = selectedContent.getDownloadManager();
				if (dms[x] != null) {
					x++;
				}
			}
			if (x > 0) {
				System.arraycopy(dms, 0, dms, 0, x);
				return dms;
			}
		}
		return null;
	}

	public static TableView getCurrentlySelectedTableView() {
		return tv;
	}
}
