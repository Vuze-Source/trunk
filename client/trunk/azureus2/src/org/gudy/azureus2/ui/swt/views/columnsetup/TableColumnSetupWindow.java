/**
 * Created on Jan 3, 2009
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

package org.gudy.azureus2.ui.swt.views.columnsetup;

import java.util.*;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.components.shell.ShellFactory;
import org.gudy.azureus2.ui.swt.shells.GCStringPrinter;
import org.gudy.azureus2.ui.swt.views.table.impl.TableViewSWTImpl;
import org.gudy.azureus2.ui.swt.views.table.utils.TableColumnManager;

import com.aelitis.azureus.ui.common.table.*;
import com.aelitis.azureus.ui.common.updater.UIUpdatable;
import com.aelitis.azureus.ui.swt.uiupdater.UIUpdaterSWT;

import org.gudy.azureus2.plugins.ui.tables.TableColumn;
import org.gudy.azureus2.plugins.ui.tables.TableColumnInfo;
import org.gudy.azureus2.plugins.ui.tables.TableRow;

/**
 * @author TuxPaper
 * @created Jan 3, 2009
 *
 */
public class TableColumnSetupWindow
	implements UIUpdatable
{
	private static final String TABLEID_AVAIL = "ColumnSetupAvail";

	private static final String TABLEID_CHOSEN = "ColumnSetupChosen";

	private Shell shell;

	private TableViewColumnSetup tvAvail;

	private final String forTableID;

	private final Class forDataSourceType;

	private Composite cTableAvail;

	private Composite cCategories;

	private TableViewColumnSetup tvChosen;

	private Composite cTableChosen;

	private TableColumnCore[] columnsChosen;

	private final TableRow sampleRow;

	private DragSourceListener dragSourceListener;

	private final TableStructureModificationListener listener;

	private TableColumnCore[] columnsOriginalOrder;

	protected boolean apply = false;

	private Button[] radProficiency = new Button[3];

	private Map<TableColumnCore, Boolean> mapNewVisibility = new HashMap();

	private ArrayList<TableColumnCore> listColumnsNoCat;

	public TableColumnSetupWindow(final Class forDataSourceType, String _tableID,
			TableRow sampleRow, TableStructureModificationListener _listener) {
		this.sampleRow = sampleRow;
		this.listener = _listener;
		FormData fd;
		this.forDataSourceType = forDataSourceType;
		forTableID = _tableID;

		dragSourceListener = new DragSourceListener() {
			private TableColumnCore tableColumn;

			public void dragStart(DragSourceEvent event) {
				event.doit = true;

				Table table = (Table) ((DragSource) event.widget).getControl();
				TableView tv = (TableView) table.getData("TableView");
				// drag start happens a bit after the mouse moves, so the
				// cursor location isn't accurate
				//Point cursorLocation = event.display.getCursorLocation();
				//cursorLocation = tv.getTableComposite().toControl(cursorLocation);
				//TableRowCore row = tv.getRow(cursorLocation.x, cursorLocation.y);
				//System.out.println("" + event.x + ";" + event.y + "/" + cursorLocation);

				// event.x and y doesn't always return correct values!
				//TableRowCore row = tv.getRow(event.x, event.y);

				TableRowCore row = tv.getFocusedRow();
				if (row == null) {
					event.doit = false;
					return;
				}

				tableColumn = (TableColumnCore) row.getDataSource();

				if (event.image != null) {
					GC gc = new GC(event.image);
					Rectangle bounds = event.image.getBounds();
					gc.fillRectangle(bounds);
					String title = MessageText.getString(
							tableColumn.getTitleLanguageKey(), tableColumn.getName());
					String s = title
							+ " Column will be placed at the location you drop it, shifting other columns down";
					GCStringPrinter sp = new GCStringPrinter(gc, s, bounds, false, false,
							SWT.CENTER | SWT.WRAP);
					sp.calculateMetrics();
					if (sp.isCutoff()) {
						GCStringPrinter.printString(gc, title, bounds, false, false,
								SWT.CENTER | SWT.WRAP);
					} else {
						sp.printString();
					}
					gc.dispose();
				}
			}

			public void dragSetData(DragSourceEvent event) {
				Table table = (Table) ((DragSource) event.widget).getControl();
				TableView tv = (TableView) table.getData("TableView");
				event.data = "" + (tv == tvChosen ? "c" : "a");
			}

			public void dragFinished(DragSourceEvent event) {
			}
		};

		shell = ShellFactory.createShell(Utils.findAnyShell(), SWT.SHELL_TRIM);
		Utils.setShellIcon(shell);
		FormLayout formLayout = new FormLayout();
    shell.setText(MessageText.getString("ColumnSetup.title", new String[] {
			MessageText.getString(_tableID + "View.header")
		}));
		shell.setLayout(formLayout);
		shell.setSize(780, 550);

		shell.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				close();
			}
		});

		Label topInfo = new Label(shell, SWT.WRAP);
		topInfo.setText("Welcome to the new Column Setup Window for "
				+ _tableID
				+ ". This is still beta.  Drag and drop to add and move, or use the add button");

		fd = Utils.getFilledFormData();
		fd.left.offset = 2;
		fd.bottom = null;
		topInfo.setLayoutData(fd);

		Button btnOk = new Button(shell, SWT.PUSH);
		Messages.setLanguageText(btnOk, "Button.ok");
		btnOk.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				apply = true;
				shell.dispose();
			}
		});

		Group cPickArea = new Group(shell, SWT.NONE);
		cPickArea.setLayout(new FormLayout());

		cCategories = new Composite(cPickArea, SWT.NONE);
		cCategories.setLayout(new RowLayout());

		final TableColumnManager tcm = TableColumnManager.getInstance();

		Group cResultArea = new Group(shell, SWT.NONE);
		cResultArea.setLayout(new FormLayout());
		fd = new FormData();
		fd.top = new FormAttachment(topInfo, 5);
		fd.right = new FormAttachment(100, 0);
		fd.bottom = new FormAttachment(btnOk, -5);
		fd.width = 200;
		cResultArea.setLayoutData(fd);

		tvAvail = createTVAvail();

		cTableAvail = new Composite(cPickArea, SWT.NO_FOCUS);
		GridLayout gridLayout = new GridLayout();
		gridLayout.marginWidth = gridLayout.marginHeight = 0;
		cTableAvail.setLayout(gridLayout);

		tvAvail.initialize(cTableAvail);

		TableColumnCore[] datasources = tcm.getAllTableColumnCoreAsArray(
				forDataSourceType, forTableID);

		listColumnsNoCat = new ArrayList<TableColumnCore>(
				Arrays.asList(datasources));
		List<String> listCats = new ArrayList<String>();
		for (int i = 0; i < datasources.length; i++) {
			TableColumnCore column = datasources[i];
			TableColumnInfo info = tcm.getColumnInfo(forDataSourceType, forTableID,
					column.getName());
			if (info != null) {
				String[] categories = info.getCategories();
				if (categories != null && categories.length > 0) {
					for (int j = 0; j < categories.length; j++) {
						String cat = categories[j];
						if (!listCats.contains(cat)) {
							listCats.add(cat);
						}
					}
					listColumnsNoCat.remove(column);
				}
			}
		}

		Listener radListener = new Listener() {
			public void handleEvent(Event event) {
				fillAvail();
			}
		};

		Composite cProficiency = new Composite(cPickArea, SWT.NONE);
		cProficiency.setLayout(new FormLayout());

		Label lblProficiency = new Label(cProficiency, SWT.NONE);
		lblProficiency.setText("Your Proficiency:");

		radProficiency[0] = new Button(cProficiency, SWT.RADIO);
		Messages.setLanguageText(radProficiency[0], "ConfigView.section.mode.beginner");
		fd = new FormData();
		fd.left = new FormAttachment(lblProficiency, 5);
		radProficiency[0].setLayoutData(fd);
		radProficiency[0].addListener(SWT.Selection, radListener);

		radProficiency[1] = new Button(cProficiency, SWT.RADIO);
		Messages.setLanguageText(radProficiency[1], "ConfigView.section.mode.intermediate");
		fd = new FormData();
		fd.left = new FormAttachment(radProficiency[0], 5);
		radProficiency[1].setLayoutData(fd);
		radProficiency[1].addListener(SWT.Selection, radListener);

		radProficiency[2] = new Button(cProficiency, SWT.RADIO);
		Messages.setLanguageText(radProficiency[2], "ConfigView.section.mode.advanced");
		fd = new FormData();
		fd.left = new FormAttachment(radProficiency[1], 5);
		radProficiency[2].setLayoutData(fd);
		radProficiency[2].addListener(SWT.Selection, radListener);

		int userMode = COConfigurationManager.getIntParameter("User Mode");
		if (userMode < 0) {
			userMode = 0;
		} else if (userMode >= radProficiency.length) {
			userMode = radProficiency.length - 1;
		}
		radProficiency[userMode].setSelection(true);

		// >>>>>>>> Buttons

		Listener buttonListener = new Listener() {
			public void handleEvent(Event event) {
				Control[] children = cCategories.getChildren();
				for (int i = 0; i < children.length; i++) {
					Control child = children[i];
					if (child != event.widget && (child instanceof Button)) {
						Button btn = (Button) child;
						btn.setSelection(false);
					}
				}
				
				fillAvail();
			}
		};

		Label lblFilter = new Label(cPickArea, SWT.NONE);
		lblFilter.setText("Filter:");

		Button button = new Button(cCategories, SWT.TOGGLE);
		Messages.setLanguageText(button, "Categories.all");
		button.addListener(SWT.Selection, buttonListener);
		button.setSelection(true);

		for (String cat : listCats) {
			button = new Button(cCategories, SWT.TOGGLE);
			button.setData("cat", cat);
			button.setText(cat);
			button.addListener(SWT.Selection, buttonListener);
		}

		if (listColumnsNoCat.size() > 0) {
			button = new Button(cCategories, SWT.TOGGLE);
			button.setText("?");
			button.setData("cat", "uncat");
			button.addListener(SWT.Selection, buttonListener);
		}

		// <<<<<<< Buttons
		
		fillAvail();

		// >>>>>>> Chosen

		Label lblChosenHeader = new Label(cResultArea, SWT.CENTER);
		lblChosenHeader.setText("Selected Columns");

		Button btnUp = new Button(cResultArea, SWT.PUSH);
		btnUp.setText("Up");
		btnUp.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent e) {
				moveChosenUp();
			}

			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		Button btnDown = new Button(cResultArea, SWT.PUSH);
		btnDown.setText("Down");
		btnDown.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent e) {
				moveChosenDown();
			}

			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		Button btnDel = new Button(cResultArea, SWT.PUSH);
		btnDel.setText("Remove");
		btnDel.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent e) {
				removeSelectedChosen();
			}

			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		tvChosen = createTVChosen();

		cTableChosen = new Composite(cResultArea, SWT.NONE);
		gridLayout = new GridLayout();
		gridLayout.marginWidth = gridLayout.marginHeight = 0;
		cTableChosen.setLayout(gridLayout);

		tvChosen.initialize(cTableChosen);

		columnsChosen = tcm.getAllTableColumnCoreAsArray(forDataSourceType,
				forTableID);
		Arrays.sort(columnsChosen,
				TableColumnManager.getTableColumnOrderComparator());
		columnsOriginalOrder = new TableColumnCore[columnsChosen.length];
		System.arraycopy(columnsChosen, 0, columnsOriginalOrder, 0,
				columnsChosen.length);
		int pos = 0;
		for (int i = 0; i < columnsChosen.length; i++) {
			boolean visible = columnsChosen[i].isVisible();
			mapNewVisibility.put(columnsChosen[i], new Boolean(visible));
			if (visible) {
				columnsChosen[i].setPositionNoShift(pos++);
				tvChosen.addDataSource(columnsChosen[i]);
			}
		}

		Button btnCancel = new Button(shell, SWT.PUSH);
		Messages.setLanguageText(btnCancel, "Button.cancel");
		btnCancel.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				shell.dispose();
			}
		});

		Button btnApply = new Button(shell, SWT.PUSH);
		Messages.setLanguageText(btnApply, "Button.apply");
		btnApply.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				apply();
			}
		});

		fd = new FormData();
		fd.left = new FormAttachment(0, 5);
		fd.right = new FormAttachment(100, -5);
		lblChosenHeader.setLayoutData(fd);

		fd = new FormData();
		fd.left = new FormAttachment(0, 5);
		fd.top = new FormAttachment(lblChosenHeader, 5);
		btnUp.setLayoutData(fd);

		fd = new FormData();
		fd.left = new FormAttachment(btnUp, 5);
		fd.top = new FormAttachment(lblChosenHeader, 5);
		btnDown.setLayoutData(fd);

		fd = new FormData();
		fd.left = new FormAttachment(btnDown, 5);
		fd.top = new FormAttachment(lblChosenHeader, 5);
		btnDel.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(btnUp, 3);
		fd.left = new FormAttachment(0, 0);
		fd.right = new FormAttachment(100, 0);
		fd.bottom = new FormAttachment(100, 0);
		cTableChosen.setLayoutData(fd);

		fd = new FormData();
		fd.right = new FormAttachment(100, -8);
		fd.bottom = new FormAttachment(100, -3);
		fd.width = 64;
		btnApply.setLayoutData(fd);

		fd = new FormData();
		fd.right = new FormAttachment(btnApply, -3);
		fd.bottom = new FormAttachment(btnApply, 0, SWT.BOTTOM);
		fd.width = 65;
		btnCancel.setLayoutData(fd);

		fd = new FormData();
		fd.right = new FormAttachment(btnCancel, -3);
		fd.bottom = new FormAttachment(btnApply, 0, SWT.BOTTOM);
		fd.width = 64;
		btnOk.setLayoutData(fd);

		// <<<<<<<<< Chosen

		fd = new FormData();
		fd.top = new FormAttachment(topInfo, 5);
		fd.left = new FormAttachment(0, 0);
		fd.right = new FormAttachment(cResultArea, -3);
		fd.bottom = new FormAttachment(100, -3);
		cPickArea.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(cProficiency, 2);
		fd.left = new FormAttachment(lblFilter, 0);
		fd.right = new FormAttachment(100, 0);
		cCategories.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(0, 0);
		fd.left = new FormAttachment(0, 2);
		fd.right = new FormAttachment(100, 0);
		cProficiency.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(cProficiency, 9);
		fd.left = new FormAttachment(0, 2);
		fd.bottom = new FormAttachment(cTableAvail, 0);
		lblFilter.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(cCategories, 3);
		fd.left = new FormAttachment(0, 0);
		fd.right = new FormAttachment(100, 0);
		fd.bottom = new FormAttachment(100, 0);
		cTableAvail.setLayoutData(fd);

		//cTableAvail.setFocus();
		//tvAvail.getTableComposite().setFocus();

		shell.setTabList(new Control[] {
			cPickArea,
			cResultArea,
			btnOk,
			btnCancel,
			btnApply
		});

		cPickArea.setTabList(new Control[] {
			cTableAvail
		});

		UIUpdaterSWT.getInstance().addUpdater(this);
	}

	/**
	 * 
	 *
	 * @since 4.0.0.5
	 */
	protected void fillAvail() {
		String selectedCat = null;
		Control[] children = cCategories.getChildren();
		for (int i = 0; i < children.length; i++) {
			Control child = children[i];
			if (child instanceof Button) {
				Button btn = (Button) child;
				if (btn.getSelection()) {
					selectedCat = (String) btn.getData("cat");
					break;
				}
			}
		}
		
		
		byte selectedProf = 0;
		for (byte i = 0; i < radProficiency.length; i++) {
			Button btn = radProficiency[i];
			if (btn.getSelection()) {
				selectedProf = i;
				break;
			}
		}

		tvAvail.removeAllTableRows();

		final TableColumnManager tcm = TableColumnManager.getInstance();
		TableColumnCore[] datasources = tcm.getAllTableColumnCoreAsArray(
				forDataSourceType, forTableID);
		
		if (selectedCat == "uncat") {
			datasources = (TableColumnCore[]) listColumnsNoCat.toArray();
		}
		for (int i = 0; i < datasources.length; i++) {
			TableColumnCore column = datasources[i];
			TableColumnInfo info = tcm.getColumnInfo(forDataSourceType,
					forTableID, column.getName());
			if (info == null || info.getCategories() == null) {
				continue;
			}
			String[] cats = info.getCategories();
			for (int j = 0; j < cats.length; j++) {
				String cat = cats[j];
				if ((selectedCat == null || selectedCat.equalsIgnoreCase(cat))
						&& info.getProficiency() <= selectedProf) {
					tvAvail.addDataSource(column);
					break;
				}
			}
		}
	}

	/**
	 * 
	 *
	 * @since 4.0.0.5
	 */
	protected void removeSelectedChosen() {
		Object[] datasources = tvChosen.getSelectedDataSources();
		for (int i = 0; i < datasources.length; i++) {
			TableColumnCore column = (TableColumnCore) datasources[i];
			mapNewVisibility.put(column, Boolean.FALSE);
		}
		tvChosen.removeDataSources(datasources);
	}

	/**
	 * 
	 *
	 * @since 4.0.0.5
	 */
	protected void moveChosenDown() {
		TableRowCore[] selectedRows = tvChosen.getSelectedRows();
		TableRowCore[] rows = tvChosen.getRows();
		for (int i = selectedRows.length - 1; i >= 0; i--) {
			TableRowCore row = selectedRows[i];
			TableColumnCore column = (TableColumnCore) row.getDataSource();
			if (column != null) {
				int oldColumnPos = column.getPosition();
				int oldRowPos = row.getIndex();
				if (oldRowPos < rows.length - 1) {
					TableRowCore displacedRow = rows[oldRowPos + 1];
					((TableColumnCore) displacedRow.getDataSource()).setPositionNoShift(oldColumnPos);
					rows[oldRowPos + 1] = rows[oldRowPos];
					rows[oldRowPos] = displacedRow;
					column.setPositionNoShift(oldColumnPos + 1);
				}
			}
		}
		tvChosen.tableInvalidate();
		tvChosen.refreshTable(true);
	}

	/**
	 * 
	 *
	 * @since 4.0.0.5
	 */
	protected void moveChosenUp() {
		TableRowCore[] selectedRows = tvChosen.getSelectedRows();
		TableRowCore[] rows = tvChosen.getRows();
		for (int i = 0; i < selectedRows.length; i++) {
			TableRowCore row = selectedRows[i];
			TableColumnCore column = (TableColumnCore) row.getDataSource();
			if (column != null) {
				int oldColumnPos = column.getPosition();
				int oldRowPos = row.getIndex();
				if (oldRowPos > 0) {
					TableRowCore displacedRow = rows[oldRowPos - 1];
					((TableColumnCore) displacedRow.getDataSource()).setPositionNoShift(oldColumnPos);
					rows[oldRowPos - 1] = rows[oldRowPos];
					rows[oldRowPos] = displacedRow;
					column.setPositionNoShift(oldColumnPos - 1);
				}
			}
		}
		tvChosen.tableInvalidate();
		tvChosen.refreshTable(true);
	}

	/**
	 * 
	 *
	 * @since 4.0.0.5
	 */
	protected void apply() {
		for (int i = 0; i < columnsChosen.length; i++) {
			TableColumnCore column = columnsChosen[i];
			if (column != null) {
				column.setVisible(mapNewVisibility.get(column).booleanValue());
			}
		}
		TableColumnManager.getInstance().saveTableColumns(forDataSourceType,
				forTableID);
		listener.tableStructureChanged(true);
	}

	/**
	 * @return
	 *
	 * @since 4.0.0.5
	 */
	private TableViewColumnSetup createTVChosen() {
		final TableColumnManager tcm = TableColumnManager.getInstance();
		TableColumnCore[] columnTVChosen = tcm.getAllTableColumnCoreAsArray(
				TableColumn.class, TABLEID_CHOSEN);
		for (int i = 0; i < columnTVChosen.length; i++) {
			TableColumnCore column = columnTVChosen[i];
			column.setVisible(column.getName().equals(ColumnTC_ChosenColumn.COLUMN_ID));
		}

		final TableViewColumnSetup tvChosen = new TableViewColumnSetup(this,
				TABLEID_CHOSEN, columnTVChosen, ColumnTC_ChosenColumn.COLUMN_ID, true);
		tvChosen.setMenuEnabled(false);
		tvChosen.setSampleRow(sampleRow);
		//tvChosen.setRowDefaultHeight(16);
		tvChosen.setDataSourceType(TableColumn.class);

		tvChosen.addLifeCycleListener(new TableLifeCycleListener() {
			private DragSource dragSource;

			public void tableViewInitialized() {
				dragSource = tvChosen.createDragSource(DND.DROP_MOVE | DND.DROP_COPY
						| DND.DROP_LINK);
				dragSource.setTransfer(new Transfer[] {
					TextTransfer.getInstance()
				});
				dragSource.addDragListener(dragSourceListener);

				DropTarget dropTarget = tvChosen.createDropTarget(DND.DROP_DEFAULT
						| DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK
						| DND.DROP_TARGET_MOVE);
				dropTarget.setTransfer(new Transfer[] {
					TextTransfer.getInstance()
				});
				dropTarget.addDropListener(new DropTargetListener() {

					public void dropAccept(DropTargetEvent event) {
						// TODO Auto-generated method stub

					}

					public void drop(DropTargetEvent event) {
						String id = (String) event.data;
						TableRowCore destRow = tvChosen.getRow(event);

						TableView tv = id.equals("c") ? tvChosen : tvAvail;

						Object[] dataSources = tv.getSelectedDataSources();
						for (int i = 0; i < dataSources.length; i++) {
							TableColumnCore column = (TableColumnCore) dataSources[i];
							if (column != null) {
								chooseColumn(column, destRow, true);
								TableRowCore row = tvAvail.getRow(column);
								if (row != null) {
									row.redraw();
								}
							}
						}
					}

					public void dragOver(DropTargetEvent event) {
						// TODO Auto-generated method stub

					}

					public void dragOperationChanged(DropTargetEvent event) {
						// TODO Auto-generated method stub

					}

					public void dragLeave(DropTargetEvent event) {
						// TODO Auto-generated method stub

					}

					public void dragEnter(DropTargetEvent event) {
						// TODO Auto-generated method stub

					}
				});
			}

			public void tableViewDestroyed() {
				// TODO Auto-generated method stub

			}
		});

		tvChosen.addKeyListener(new KeyListener() {
			public void keyReleased(KeyEvent e) {
			}

			public void keyPressed(KeyEvent e) {
				if (e.stateMask == 0
						&& (e.keyCode == SWT.ARROW_LEFT || e.keyCode == SWT.DEL)) {
					removeSelectedChosen();
					e.doit = false;
				}

				if (e.stateMask == SWT.CONTROL) {
					if (e.keyCode == SWT.ARROW_UP) {
						moveChosenUp();
						e.doit = false;
					} else if (e.keyCode == SWT.ARROW_DOWN) {
						moveChosenDown();
						e.doit = false;
					}
				}
			}
		});
		return tvChosen;
	}

	/**
	 * @return
	 *
	 * @since 4.0.0.5
	 */
	private TableViewColumnSetup createTVAvail() {
		final TableColumnManager tcm = TableColumnManager.getInstance();
		Map mapColumns = tcm.getTableColumnsAsMap(TableColumn.class, TABLEID_AVAIL);
		TableColumnCore[] columns = {
			(TableColumnCore) mapColumns.get(ColumnTC_NameInfo.COLUMN_ID),
			(TableColumnCore) mapColumns.get(ColumnTC_Sample.COLUMN_ID),
		};
		for (int i = 0; i < columns.length; i++) {
			TableColumnCore column = columns[i];
			if (column != null) {
				column.setVisible(true);
				column.setPositionNoShift(i);
			}
		}

		final TableViewColumnSetup tvAvail = new TableViewColumnSetup(this,
				TABLEID_AVAIL, columns, ColumnTC_ChosenColumn.COLUMN_ID, false);
		tvAvail.setMenuEnabled(false);
		tvAvail.setSampleRow(sampleRow);
		tvAvail.setRowDefaultHeight(55);
		tvAvail.setDataSourceType(TableColumn.class);

		tvAvail.addLifeCycleListener(new TableLifeCycleListener() {
			private DragSource dragSource;

			public void tableViewInitialized() {
				dragSource = tvAvail.createDragSource(DND.DROP_MOVE | DND.DROP_COPY
						| DND.DROP_LINK);
				dragSource.setTransfer(new Transfer[] {
					TextTransfer.getInstance()
				});
				dragSource.addDragListener(dragSourceListener);
			}

			public void tableViewDestroyed() {
				if (dragSource != null && !dragSource.isDisposed()) {
					dragSource.dispose();
				}
			}
		});
		tvAvail.addSelectionListener(new TableSelectionAdapter() {
			public void defaultSelected(TableRowCore[] rows, int stateMask) {
				for (int i = 0; i < rows.length; i++) {
					TableRowCore row = rows[i];
					TableColumnCore column = (TableColumnCore) row.getDataSource();
					chooseColumn(column, null, false);
				}
			}
		}, false);

		tvAvail.addKeyListener(new KeyListener() {
			public void keyReleased(KeyEvent e) {
			}

			public void keyPressed(KeyEvent e) {
				if (e.stateMask == 0) {
					if (e.keyCode == SWT.ARROW_RIGHT) {
						TableRowCore[] selectedRows = tvAvail.getSelectedRows();
						for (int i = 0; i < selectedRows.length; i++) {
							TableRowCore row = selectedRows[i];
							TableColumnCore column = (TableColumnCore) row.getDataSource();
							chooseColumn(column, null, false);
							tvChosen.processDataSourceQueue();
							row.redraw();
						}
						e.doit = false;
					} else if (e.keyCode == SWT.ARROW_LEFT) {
						TableRowCore[] selectedRows = tvAvail.getSelectedRows();
						for (int i = 0; i < selectedRows.length; i++) {
							TableRowCore row = selectedRows[i];
							TableColumnCore column = (TableColumnCore) row.getDataSource();
							mapNewVisibility.put(column, Boolean.FALSE);
							tvChosen.removeDataSource(column);
							tvChosen.processDataSourceQueue();
							row.redraw();
						}
						e.doit = false;
					}
				}
			}
		});

		return tvAvail;
	}

	public void open() {
		shell.open();
	}

	// @see com.aelitis.azureus.ui.common.updater.UIUpdatable#getUpdateUIName()
	public String getUpdateUIName() {
		// TODO Auto-generated method stub
		return null;
	}

	// @see com.aelitis.azureus.ui.common.updater.UIUpdatable#updateUI()
	public void updateUI() {
		if (shell.isDisposed()) {
			UIUpdaterSWT.getInstance().removeUpdater(this);
			return;
		}
		if (tvAvail != null && !tvAvail.isDisposed()) {
			tvAvail.refreshTable(false);
		}
		if (tvChosen != null && !tvChosen.isDisposed()) {
			tvChosen.refreshTable(false);
		}
	}

	public class TableViewColumnSetup
		extends TableViewSWTImpl
	{
		private TableRow sampleRow = null;

		private final TableColumnSetupWindow setupWindow;

		public TableViewColumnSetup(TableColumnSetupWindow setupWindow,
				String tableID, TableColumnCore[] items, String defaultSortOn,
				boolean multi) {
			super(tableID, tableID, items, defaultSortOn, SWT.FULL_SELECTION
					| SWT.VIRTUAL | (multi ? SWT.MULTI : SWT.SINGLE));
			this.setupWindow = setupWindow;
		}

		public TableRow getSampleRow() {
			return sampleRow;
		}

		public void setSampleRow(TableRow sampleRow) {
			this.sampleRow = sampleRow;
		}

		public void chooseColumn(TableColumnCore column) {
			setupWindow.chooseColumn(column, null, false);
			TableRowCore row = tvAvail.getRow(column);
			if (row != null) {
				row.redraw();
			}
		}

		public boolean isColumnAdded(TableColumnCore column) {
			TableRowCore row = tvChosen.getRow(column);
			return row != null;
		}
	}

	/**
	 * @param column
	 *
	 * @since 4.0.0.5
	 */
	public void chooseColumn(final TableColumnCore column,
			TableRowCore placeAboveRow, boolean ignoreExisting) {
		TableRowCore row = tvChosen.getRow(column);

		if (row == null || ignoreExisting) {
			int newPosition = 0;

			row = placeAboveRow == null && !ignoreExisting ? tvChosen.getFocusedRow()
					: placeAboveRow;
			if (row == null) {
				if (columnsChosen.length > 0) {
					newPosition = columnsChosen.length;
				}
			} else {
				newPosition = ((TableColumn) row.getDataSource()).getPosition();
			}

			int oldPosition = column.getPosition();
			final boolean shiftDir = oldPosition > newPosition
					||  !mapNewVisibility.get(column).booleanValue();
			column.setPositionNoShift(newPosition);
			mapNewVisibility.put(column, Boolean.TRUE);

			Arrays.sort(columnsChosen, new Comparator() {
				public int compare(Object arg0, Object arg1) {
					if ((arg1 instanceof TableColumn) && (arg0 instanceof TableColumn)) {
						int iPositionA = ((TableColumn) arg0).getPosition();
						if (iPositionA < 0)
							iPositionA = 0xFFFF + iPositionA;
						int iPositionB = ((TableColumn) arg1).getPosition();
						if (iPositionB < 0)
							iPositionB = 0xFFFF + iPositionB;

						int i = iPositionA - iPositionB;
						if (i == 0) {
							if (column == arg0) {
								return shiftDir ? -1 : 1;
							} else {
								return shiftDir ? 1 : -1;
							}
						}
						return i;
					}
					return 0;
				}
			});

			int pos = 0;
			for (int i = 0; i < columnsChosen.length; i++) {
				if (mapNewVisibility.get(columnsChosen[i]).booleanValue()) {
					columnsChosen[i].setPositionNoShift(pos++);
				}
			}

			TableRowCore existingRow = tvChosen.getRow(column);
			if (existingRow == null) {
				tvChosen.addDataSource(column);
				tvChosen.processDataSourceQueue();
				tvChosen.addCountChangeListener(new TableCountChangeListener() {

					public void rowRemoved(TableRowCore row) {
					}

					public void rowAdded(final TableRowCore row) {
						Utils.execSWTThreadLater(500, new AERunnable() {
							public void runSupport() {
								tvChosen.setSelectedRows(new TableRowCore[] { row });
								tvChosen.showRow(row);
							}
						});
						tvChosen.removeCountChangeListener(this);
					}
				});
			}

			tvChosen.tableInvalidate();
			tvChosen.refreshTable(true);

			Arrays.sort(columnsChosen,
					TableColumnManager.getTableColumnOrderComparator());
		} else {
			row.setSelected(true);
		}
	}

	private void close() {
		if (apply) {
			apply();
		} else {
			for (int i = 0; i < columnsOriginalOrder.length; i++) {
				TableColumnCore column = columnsOriginalOrder[i];
				if (column != null) {
					column.setPositionNoShift(i);
				}
			}
		}
	}
}
