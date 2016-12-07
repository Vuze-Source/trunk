/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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
 */

package com.aelitis.azureus.ui.swt.columns.subscriptions;


import org.gudy.azureus2.plugins.ui.tables.*;

import com.aelitis.azureus.ui.swt.subscriptions.SBC_SubscriptionResult;


public class ColumnSubResultSeedsPeers
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static String COLUMN_ID = "seeds_peers";


	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			TableColumn.CAT_ESSENTIAL,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	/** Default Constructor */
	public ColumnSubResultSeedsPeers(TableColumn column) {
		column.initialize(TableColumn.ALIGN_CENTER, TableColumn.POSITION_LAST, 80 );
		column.setRefreshInterval(TableColumn.INTERVAL_INVALID_ONLY);
		column.addListeners(this);
	}

	public void refresh(TableCell cell) {
		SBC_SubscriptionResult result = (SBC_SubscriptionResult) cell.getDataSource();
	
		long sort = result.getSeedsPeersSortValue();
				
		if ( !cell.setSortValue(sort) && cell.isValid()){
			
			return;
		}

		if (!cell.isShown()) {
			return;
		}
		
		String str = result.getSeedsPeers();

		cell.setText(str);
		
		return;
		
	}
}
