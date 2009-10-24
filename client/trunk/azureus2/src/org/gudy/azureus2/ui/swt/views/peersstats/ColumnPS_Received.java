package org.gudy.azureus2.ui.swt.views.peersstats;

import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.plugins.ui.tables.TableCell;
import org.gudy.azureus2.plugins.ui.tables.TableCellRefreshListener;
import org.gudy.azureus2.plugins.ui.tables.TableColumn;

public class ColumnPS_Received
	implements TableCellRefreshListener
{

	public static final String COLUMN_ID = "received";

	public ColumnPS_Received(TableColumn column) {
		column.initialize(TableColumn.ALIGN_LEAD, TableColumn.POSITION_LAST, 100);
		column.addListeners(this);
		column.setType(TableColumn.TYPE_TEXT_ONLY);
		column.setRefreshInterval(2);
	}

	public void refresh(TableCell cell) {
		PeersStatsDataSource ds = (PeersStatsDataSource) cell.getDataSource();
		cell.setSortValue(ds.bytesReceived);
		cell.setText(DisplayFormatters.formatByteCountToKiBEtc(ds.bytesReceived));
	}
}
