/*
 * File    : TypeItem.java
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
 
package org.gudy.azureus2.ui.swt.views.tableitems.tracker;

import org.gudy.azureus2.plugins.ui.tables.*;
import org.gudy.azureus2.ui.swt.views.table.CoreTableColumnSWT;


import com.aelitis.azureus.core.tracker.TrackerPeerSource;


public class 
NameItem
	extends CoreTableColumnSWT 
    implements TableCellRefreshListener
{
	public 
	NameItem( String tableID)
	{
		super( "name", ALIGN_LEAD, POSITION_LAST, 300, tableID );
    
		setRefreshInterval( INTERVAL_GRAPHIC );
	}

	public void 
	fillTableColumnInfo(
		TableColumnInfo info ) 
	{
		info.addCategories( new String[]{
			CAT_ESSENTIAL,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	public void 
	refresh(
		TableCell cell ) 
	{
		TrackerPeerSource ps = (TrackerPeerSource)cell.getDataSource();
    
		String name = (ps==null)?"":ps.getName();

		if (!cell.setSortValue(name) && cell.isValid()){
		
			return;
		}

		cell.setText( name );
	}
}
