/*
 * Created on 2 juil. 2003
 *
 */
package org.gudy.azureus2.ui.swt;

import java.util.HashMap;
import java.util.Iterator;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

import org.gudy.azureus2.core.DownloadManager;
import org.gudy.azureus2.core.Piece;

/**
 * @author Olivier
 * 
 */
public class PiecesView implements IView, IComponentListener {

  DownloadManager manager;
  Table table;
  HashMap items;
  int loopFactor;

  public PiecesView(DownloadManager manager) {
    this.manager = manager;
    items = new HashMap();
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IView#initialize(org.eclipse.swt.widgets.Composite)
   */
  public void initialize(Composite composite) {
    table = new Table(composite, SWT.SINGLE | SWT.FULL_SELECTION);
    table.setLinesVisible(false);
    table.setHeaderVisible(true);
    String[] titlesPieces =
      { "#", "Size", "# of Blocks", "Blocks", "Completed", "Availability" };
    int[] alignPieces =
      { SWT.LEFT, SWT.RIGHT, SWT.RIGHT, SWT.CENTER, SWT.RIGHT, SWT.RIGHT };
    for (int i = 0; i < titlesPieces.length; i++) {
      TableColumn column = new TableColumn(table, alignPieces[i]);
      column.setText(titlesPieces[i]);      
    }
    table.getColumn(0).setWidth(50);
    table.getColumn(1).setWidth(60);
    table.getColumn(2).setWidth(65);
    table.getColumn(3).setWidth(300);
    table.getColumn(4).setWidth(80);
    table.getColumn(5).setWidth(80);

    manager.addListener(this);
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IView#getComposite()
   */
  public Composite getComposite() {
    return table;
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IView#refresh()
   */
  public void refresh() {
    synchronized (items) {
      Iterator iter = items.values().iterator();
      while (iter.hasNext()) {
        PieceTableItem pti = (PieceTableItem) iter.next();
        pti.updateDisplay();
      }
    }
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IView#delete()
   */
  public void delete() {
    manager.removeListener(this);
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IView#getShortTitle()
   */
  public String getShortTitle() {
    return "Pieces";
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IView#getFullTitle()
   */
  public String getFullTitle() {
    return "Pieces";
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IComponentListener#objectAdded(java.lang.Object)
   */
  public void objectAdded(Object created) {
    if (!(created instanceof Piece))
      return;
    synchronized (items) {
      if (items.containsKey(created))
        return;
      PieceTableItem item = new PieceTableItem(table, (Piece) created);
      items.put(created, item);
    }
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IComponentListener#objectRemoved(java.lang.Object)
   */
  public void objectRemoved(Object removed) {
    //System.out.println("removed : " + removed.getClass() + ":" + removed);
    PieceTableItem item;
    synchronized (items) {
      item = (PieceTableItem) items.remove(removed);
    }
    if (item == null)
      return;
    item.remove();
  }

}
