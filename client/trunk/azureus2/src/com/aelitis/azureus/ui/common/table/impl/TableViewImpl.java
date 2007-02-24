/**
 * 
 */
package com.aelitis.azureus.ui.common.table.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.aelitis.azureus.ui.common.table.*;

/**
 * @author TuxPaper
 * @created Feb 6, 2007
 */
public abstract class TableViewImpl
	implements TableView
{
	// List of DataSourceChangedListener
	private List listenersDataSourceChanged = new ArrayList(1);

	private List listenersSelection = new ArrayList();

	private List listenersLifeCycle = new ArrayList();

	private List listenersRefresh = new ArrayList();

	private List listenersCountChange = new ArrayList(1);

	private Object parentDataSource;

	public void addSelectionListener(TableSelectionListener listener,
			boolean bFireSelection) {
		listenersSelection.add(listener);
		if (bFireSelection) {
			TableRowCore[] rows = getSelectedRows();
			listener.selected(rows);
			listener.focusChanged(getFocusedRow());
		}
	}

	// @see com.aelitis.azureus.ui.common.table.TableView#addTableDataSourceChangedListener(com.aelitis.azureus.ui.common.table.TableDataSourceChangedListener, boolean)
	public void addTableDataSourceChangedListener(
			TableDataSourceChangedListener l, boolean trigger) {
		listenersDataSourceChanged.add(l);
		if (trigger) {
			l.tableDataSourceChanged(parentDataSource);
		}
	}

	// @see com.aelitis.azureus.ui.common.table.TableView#removeTableDataSourceChangedListener(com.aelitis.azureus.ui.common.table.TableDataSourceChangedListener)
	public void removeTableDataSourceChangedListener(
			TableDataSourceChangedListener l) {
		listenersDataSourceChanged.remove(l);
	}

	// @see com.aelitis.azureus.ui.common.table.TableView#setParentDataSource(java.lang.Object)
	public void setParentDataSource(Object newDataSource) {
		parentDataSource = newDataSource;
		Object[] listeners = listenersDataSourceChanged.toArray();
		for (int i = 0; i < listeners.length; i++) {
			TableDataSourceChangedListener l = (TableDataSourceChangedListener) listeners[i];
			l.tableDataSourceChanged(newDataSource);
		}
	}

	/**
	 * @param selectedRows
	 */
	protected void triggerDefaultSelectedListeners(TableRowCore[] selectedRows) {
		for (Iterator iter = listenersSelection.iterator(); iter.hasNext();) {
			TableSelectionListener l = (TableSelectionListener) iter.next();
			l.defaultSelected(selectedRows);
		}
	}

	/**
	 * @param eventType
	 */
	protected void triggerLifeCycleListener(int eventType) {
		Object[] listeners = listenersLifeCycle.toArray();
		if (eventType == TableLifeCycleListener.EVENT_INITIALIZED) {
			for (int i = 0; i < listeners.length; i++) {
				TableLifeCycleListener l = (TableLifeCycleListener) listeners[i];
				l.tableViewInitialized();
			}
		} else {
			for (int i = 0; i < listeners.length; i++) {
				TableLifeCycleListener l = (TableLifeCycleListener) listeners[i];
				l.tableViewDestroyed();
			}
		}
	}

	protected void triggerSelectionListeners(TableRowCore[] rows) {
		if (rows == null || rows.length == 0) {
			return;
		}
		Object[] listeners = listenersSelection.toArray();
		for (int i = 0; i < listeners.length; i++) {
			TableSelectionListener l = (TableSelectionListener) listeners[i];
			l.selected(rows);
		}
	}

	protected void triggerDeselectionListeners(TableRowCore[] rows) {
		if (rows == null || rows.length == 0) {
			return;
		}
		Object[] listeners = listenersSelection.toArray();
		for (int i = 0; i < listeners.length; i++) {
			TableSelectionListener l = (TableSelectionListener) listeners[i];
			l.deselected(rows);
		}
	}

	protected void triggerFocusChangedListeners(TableRowCore row) {
		Object[] listeners = listenersSelection.toArray();
		for (int i = 0; i < listeners.length; i++) {
			TableSelectionListener l = (TableSelectionListener) listeners[i];
			l.focusChanged(row);
		}
	}

	/**
	 * 
	 */
	protected void triggerTableRefreshListeners() {
		Object[] listeners = listenersRefresh.toArray();
		for (int i = 0; i < listeners.length; i++) {
			TableRefreshListener l = (TableRefreshListener) listeners[i];
			l.tableRefresh();
		}
	}

	// @see com.aelitis.azureus.ui.common.table.TableView#addLifeCycleListener(com.aelitis.azureus.ui.common.table.TableLifeCycleListener)
	public void addLifeCycleListener(TableLifeCycleListener l) {
		listenersLifeCycle.add(l);
		if (!isDisposed()) {
			l.tableViewInitialized();
		}
	}

	// @see com.aelitis.azureus.ui.common.table.TableView#addRefreshListener(com.aelitis.azureus.ui.common.table.TableRefreshListener, boolean)
	public void addRefreshListener(TableRefreshListener l, boolean trigger) {
		listenersRefresh.add(l);
		if (trigger) {
			l.tableRefresh();
		}
	}

	// @see com.aelitis.azureus.ui.common.table.TableView#addCountChangeListener(com.aelitis.azureus.ui.common.table.TableCountChangeListener)
	public void addCountChangeListener(TableCountChangeListener listener) {
		listenersCountChange.add(listener);
	}

	protected void triggerListenerRowAdded(TableRowCore row) {
		for (Iterator iter = listenersCountChange.iterator(); iter.hasNext();) {
			TableCountChangeListener l = (TableCountChangeListener) iter.next();
			l.rowAdded(row);
		}
	}

	protected void triggerListenerRowRemoved(TableRowCore row) {
		for (Iterator iter = listenersCountChange.iterator(); iter.hasNext();) {
			TableCountChangeListener l = (TableCountChangeListener) iter.next();
			l.rowRemoved(row);
		}
	}

	public void runForAllRows(TableGroupRowRunner runner) {
		// put to array instead of synchronised iterator, so that runner can remove
		TableRowCore[] rows = getRows();
		if (runner.run(rows)) {
			return;
		}

		for (int i = 0; i < rows.length; i++) {
			runner.run(rows[i]);
		}
	}
}