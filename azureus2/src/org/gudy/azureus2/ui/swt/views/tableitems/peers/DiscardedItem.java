/*
 * File    : DiscardedItem.java
 * Created : 24 nov. 2003
 * By      : Olivier
 *
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
 
package org.gudy.azureus2.ui.swt.views.tableitems.peers;

import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.core3.util.DisplayFormatters;

import org.gudy.azureus2.plugins.ui.tables.TableCell;
import org.gudy.azureus2.plugins.ui.tables.TableCellRefreshListener;
import org.gudy.azureus2.plugins.ui.tables.TableColumnInfo;
import org.gudy.azureus2.ui.swt.views.table.CoreTableColumnSWT;

/**
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/19: modified to TableCellAdapter)
 */
public class DiscardedItem
       extends CoreTableColumnSWT 
       implements TableCellRefreshListener
{
	public static final String COLUMN_ID = "discarded";

	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_BYTES,
		});
	}

  /** Default Constructor */
  public DiscardedItem(String table_id) {
    super(COLUMN_ID, ALIGN_TRAIL, POSITION_INVISIBLE, 60, table_id);
    setRefreshInterval(INTERVAL_LIVE);
  }

  public void refresh(TableCell cell) {
    PEPeer peer = (PEPeer)cell.getDataSource();
    long value = (peer == null) ? 0 : peer.getStats().getTotalBytesDiscarded();

    if (!cell.setSortValue(value) && cell.isValid())
      return;

    cell.setText(DisplayFormatters.formatByteCountToKiBEtc(value));
  }
}
