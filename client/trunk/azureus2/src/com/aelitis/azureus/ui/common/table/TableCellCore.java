/**
 * File    : TableCellCore.java
 * Created : 2004/May/14
 *
 * Copyright (C) 2004-2007 Aelitis SAS, All rights Reserved
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
 * AELITIS, SAS au capital de 46,603.30 euros,
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 */

package com.aelitis.azureus.ui.common.table;

import org.gudy.azureus2.plugins.ui.tables.TableCell;
import org.gudy.azureus2.plugins.ui.tables.TableCellMouseEvent;
import org.gudy.azureus2.plugins.ui.tables.TableCellVisibilityListener;

/** 
 * Core Table Cell functions are those available to plugins plus
 * some core-only functions.  The core-only functions are listed here.
 *
 * @see org.gudy.azureus2.ui.swt.views.table.impl.TableCellImpl
 */
public interface TableCellCore extends TableCell, Comparable
{
	static final int TOOLTIPLISTENER_HOVER = 0;

	static final int TOOLTIPLISTENER_HOVERCOMPLETE = 1;

	public void invalidate(boolean bMustRefresh);

	/** 
	 * Refresh the cell
	 * 
	 * @param bDoGraphics Whether to update graphic cells 
	 */
	public boolean refresh(boolean bDoGraphics);

	/** 
	 * Refresh the cell, including graphic types 
	 */
	public boolean refresh();

	/**
	 * Refresh the cell.  This method overide takes a bRowVisible paramater
	 * and a bCellVisible parameter in order to reduce the number of calls to 
	 * TableRow.isVisible() and calculations of cell visibility.
	 * 
	 * @param bDoGraphics Whether to update graphic cells
	 * @param bRowVisible Assumed visibility state of row
	 * @param bCellVisible Assumed visibility state of the cell
	 */
	public boolean refresh(boolean bDoGraphics, boolean bRowVisible,
			boolean bCellVisible);

	/**
	 * Refresh the cell.  This method overide takes a bRowVisible paramater in
	 * order to reduce the number of calls to TableRow.isVisible() in cases where
	 * multiple cells on the same row are being refreshed.
	 * 
	 * @param bDoGraphics Whether to update graphic cells
	 * @param bRowVisible Visibility state of row
	 */
	public boolean refresh(boolean bDoGraphics, boolean bRowVisible);

	/** 
	 * dispose of the cell 
	 */
	public void dispose();

	/** 
	 * Retrieve whether the cell need any paint calls (graphic)
	 *
	 * @return whether the cell needs painting
	 */
	public boolean needsPainting();

	/** 
	 * Location of the cell has changed 
	 */
	public void locationChanged();

	/** 
	 * Retrieve the row that this cell belongs to
	 *
	 * @return the row that this cell belongs to
	 */
	public TableRowCore getTableRowCore();

	/**
	 * Trigger all the tooltip listeners that have been added to this cell
	 * 
	 * @param type {@link #TOOLTIPLISTENER_HOVER}, {@link #TOOLTIPLISTENER_HOVERCOMPLETE}
	 */
	public void invokeToolTipListeners(int type);

	/**
	 * Trigger all the mouse listeners that have been addded to this cell
	 * 
	 * @param event event to trigger
	 */
	public void invokeMouseListeners(TableCellMouseEvent event);

	/**
	 * Trigger all the visibility listeners that have been added to this cell.<BR>
	 * 
	 * @param visibility See {@link TableCellVisibilityListener}.VISIBILITY_* constants
	 */
	public void invokeVisibilityListeners(int visibility);

	/**
	 * Sets whether the cell will need updating when it's visible again
	 * 
	 * @param upToDate
	 */
	public void setUpToDate(boolean upToDate);

	/**
	 * Returns whether the cell will need updating when it's visible again
	 * 
	 * @return
	 */
	boolean isUpToDate();

	/**
	 * Return the text used when generating diagnostics
	 * 
	 * @return
	 */
	String getObfusticatedText();

	/**
	 * Get the cursor ID we are currently using
	 * 
	 * XXX Should NOT be SWT.CURSOR_ constants!
	 * 
	 * @return
	 */
	public int getCursorID();

	/**
	 * Set the cursor ID that should be used for the cell
	 * 
	 * @param cursor_hand
	 */
	public void setCursorID(int cursorID);

	/**
	 * Returns whether the cell has visually changed since the last refresh call.
	 * Could be used to prevent a refresh, or refresh early.
	 * 
	 * @return visually changed since refresh state
	 */
	boolean getVisuallyChangedSinceRefresh();
}
