package com.aelitis.azureus.ui.swt.columns.torrent;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import org.gudy.azureus2.core3.disk.DiskManagerFileInfo;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.plugins.ui.tables.TableCellMouseEvent;
import org.gudy.azureus2.plugins.ui.tables.TableRowMouseEvent;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.TorrentUtil;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;
import org.gudy.azureus2.ui.swt.shells.GCStringPrinter;
import org.gudy.azureus2.ui.swt.views.FilesViewMenuUtil;

import com.aelitis.azureus.ui.common.table.TableRowCore;
import com.aelitis.azureus.ui.swt.imageloader.ImageLoader;
import com.aelitis.azureus.ui.swt.utils.ColorCache;
import com.aelitis.azureus.ui.swt.utils.FontUtils;

public class ColumnTorrentFileProgress
{

	private Image imgArrowButton;

	private Image imgPriHi;

	private Image imgPriNormal;

	private Image imgPriStopped;


	private Image imgBGfile;

	private Font progressFont;

	private Display display;

	public ColumnTorrentFileProgress(Display display) {
		this.display = display;

		ImageLoader imageLoader = ImageLoader.getInstance();
		imgArrowButton = imageLoader.getImage("image.fileprogress.arrowbtn");
		imgPriHi = imageLoader.getImage("image.fileprogress.pri.hi");
		imgPriNormal = imageLoader.getImage("image.fileprogress.pri.normal");
		imgPriStopped = imageLoader.getImage("image.fileprogress.pri.stopped");
		imgBGfile = imageLoader.getImage("image.progress.bg.file");
	}

	void fillInfoProgressETA(TableRowCore row, GC gc,
			DiskManagerFileInfo fileInfo, Rectangle cellArea) {
		long percent = 0;
		long bytesDownloaded = fileInfo.getDownloaded();
		long length = fileInfo.getLength();

		if (bytesDownloaded < 0) {

			return;

		} else if (length == 0) {

			percent = 1000;

		} else if (fileInfo.getLength() != 0) {

			percent = (1000 * bytesDownloaded) / length;
		}

		gc.setAdvanced(true);
		gc.setTextAntialias(SWT.ON);

		final int BUTTON_WIDTH = imgArrowButton.getBounds().width;
		final int HILOW_WIDTH = imgPriHi.getBounds().width;
		final int BUTTON_HEIGHT = imgArrowButton.getBounds().height;
		final int HILOW_HEIGHT = imgPriHi.getBounds().height;
		final int PADDING_X = 12;
		final int PADDING_TEXT = 5;
		final int PROGRESS_HEIGHT = imgBGfile.getBounds().height;
		final int PROGRESS_TO_HILOW_GAP = 3;
		final int HILOW_TO_BUTTON_GAP = 3;

		cellArea.width -= 3;

		int ofsX = PADDING_X;
		int ofsY = (cellArea.height / 2) - (PROGRESS_HEIGHT / 2) - 1;
		int progressWidth = cellArea.width - (ofsX * 2) - PROGRESS_TO_HILOW_GAP
				- HILOW_WIDTH - HILOW_TO_BUTTON_GAP - BUTTON_WIDTH;
		
		if ( progressWidth > 0 ){
			if (progressFont == null) {
				progressFont = FontUtils.getFontWithHeight(gc.getFont(), gc,
						PROGRESS_HEIGHT - 2);
			}
			gc.setFont(progressFont);
			gc.setForeground(ColorCache.getSchemedColor(display, fileInfo.isSkipped()
					? "#95a6b2" : "#88acc1"));
			gc.drawRectangle(cellArea.x + ofsX, cellArea.y + ofsY - 1, progressWidth,
					PROGRESS_HEIGHT + 1);
	
			int pctWidth = (int) (percent * (progressWidth - 1) / 1000);
			gc.setBackground(ColorCache.getSchemedColor(display, fileInfo.isSkipped()
					? "#a6bdce" : "#8ccfff"));
			gc.fillRectangle(cellArea.x + ofsX + 1, cellArea.y + ofsY, pctWidth,
					PROGRESS_HEIGHT);
			gc.setBackground(Colors.white);
			gc.fillRectangle(cellArea.x + ofsX + pctWidth + 1, cellArea.y + ofsY,
					progressWidth - pctWidth - 1, PROGRESS_HEIGHT);
	
			Rectangle boundsImgBG = imgBGfile.getBounds();
			gc.drawImage(imgBGfile, boundsImgBG.x, boundsImgBG.y, boundsImgBG.width,
					boundsImgBG.height, cellArea.x + ofsX + 1,
					cellArea.y + ofsY, progressWidth - 1, PROGRESS_HEIGHT);
		}
		
		Color colorText = ColorCache.getSchemedColor(display, fileInfo.isSkipped()
				? "#556875" : "#2678b1");

		Rectangle printBounds = new Rectangle(
				cellArea.x + PADDING_X + PADDING_TEXT, cellArea.y, progressWidth
						- (PADDING_TEXT * 2), cellArea.height);
		ofsY = (cellArea.height / 2) - (BUTTON_HEIGHT / 2) - 1;

		Rectangle buttonBounds = new Rectangle(cellArea.x + cellArea.width
				- BUTTON_WIDTH - PADDING_X, cellArea.y + ofsY, BUTTON_WIDTH,
				BUTTON_HEIGHT);
		row.setData("buttonBounds", buttonBounds);

		ofsY = (cellArea.height / 2) - (HILOW_HEIGHT / 2) - 1;
		Rectangle hilowBounds = new Rectangle(buttonBounds.x - HILOW_TO_BUTTON_GAP
				- HILOW_WIDTH, cellArea.y + ofsY, HILOW_WIDTH, HILOW_HEIGHT);
		row.setData("hilowBounds", hilowBounds);

		gc.setForeground(colorText);

		String s = DisplayFormatters.formatPercentFromThousands((int) percent);
		GCStringPrinter.printString(gc, s, printBounds, true, false, SWT.LEFT);

		//gc.setForeground(ColorCache.getRandomColor());

		String tmp = null;
		if (fileInfo.getDownloadManager().getState() == DownloadManager.STATE_STOPPED) {
			tmp = MessageText.getString("FileProgress.stopped");
		} else {

			int st = fileInfo.getStorageType();
			if ((st == DiskManagerFileInfo.ST_COMPACT || st == DiskManagerFileInfo.ST_REORDER_COMPACT)
					&& fileInfo.isSkipped()) {
				tmp = MessageText.getString("FileProgress.deleted");
			} else if (fileInfo.isSkipped()) {
				tmp = MessageText.getString("FileProgress.stopped");
			} else if (fileInfo.getPriority() > 0) {

				int pri = fileInfo.getPriority();

				if (pri > 1) {
					tmp = MessageText.getString("FileItem.high");
					tmp += " (" + pri + ")";
				}
			} else {
				//tmp = MessageText.getString("FileItem.normal");
			}
		}

		if (tmp != null) {
			GCStringPrinter.printString(gc, tmp.toUpperCase(), printBounds, false,
					false, SWT.RIGHT);
		}

		gc.drawImage(imgArrowButton, buttonBounds.x, buttonBounds.y);
		Image imgPriority = fileInfo.isSkipped() ? imgPriStopped
				: fileInfo.getPriority() > 0 ? imgPriHi : imgPriNormal;
		gc.drawImage(imgPriority, hilowBounds.x, hilowBounds.y);

		//System.out.println(cellArea + s + ";" + Debug.getCompressedStackTrace());
		// make relative to row, because mouse events are
		hilowBounds.y -= cellArea.y;
		hilowBounds.x -= cellArea.x;
		buttonBounds.x -= cellArea.x;
		buttonBounds.y -= cellArea.y;
	}

	public void fileInfoMouseTrigger(TableCellMouseEvent event) {
		if (event.eventType != TableRowMouseEvent.EVENT_MOUSEDOWN) {
			return;
		}
		final Object dataSource = ((TableRowCore) event.row).getDataSource(true);
		if (dataSource instanceof DiskManagerFileInfo) {
			final DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) dataSource;
			Rectangle hilowBounds = (Rectangle) event.row.getData("hilowBounds");
			if (event.button == 1 && hilowBounds != null
					&& hilowBounds.contains(event.x, event.y)) {
				if (fileInfo.getPriority() > 0) {
					fileInfo.setPriority(0);
				} else {
					fileInfo.setPriority(1);
				}
				((TableRowCore) event.row).redraw();
			}

			Rectangle buttonBounds = (Rectangle) event.row.getData("buttonBounds");

			if (buttonBounds != null && buttonBounds.contains(event.x, event.y)) {
				Menu menu = new Menu(Display.getDefault().getActiveShell(), SWT.POP_UP);

				MenuItem itemHigh = new MenuItem(menu, SWT.RADIO);
				Messages.setLanguageText(itemHigh, "priority.high");
				itemHigh.addListener(SWT.Selection, new Listener() {
					public void handleEvent(Event event) {
						FilesViewMenuUtil.changePriority(FilesViewMenuUtil.PRIORITY_HIGH,
								new Object[] {
									dataSource
								});
					}
				});
				itemHigh.setSelection(fileInfo.getPriority() != 0); 

				MenuItem itemNormal = new MenuItem(menu, SWT.RADIO);
				Messages.setLanguageText(itemNormal, "priority.normal");
				itemNormal.addListener(SWT.Selection, new Listener() {
					public void handleEvent(Event event) {
						FilesViewMenuUtil.changePriority(FilesViewMenuUtil.PRIORITY_NORMAL,
								new Object[] {
									dataSource
								});
					}
				});
				itemNormal.setSelection(fileInfo.getPriority() == 0);

				new MenuItem(menu, SWT.SEPARATOR);
				
				boolean canStart = fileInfo.isSkipped() || fileInfo.getDownloadManager().getState() == DownloadManager.STATE_STOPPED;

				MenuItem itemStop = new MenuItem(menu, SWT.PUSH);
				Messages.setLanguageText(itemStop, "v3.MainWindow.button.stop");
				itemStop.addListener(SWT.Selection, new Listener() {
					public void handleEvent(Event event) {
						FilesViewMenuUtil.changePriority(
								FilesViewMenuUtil.PRIORITY_SKIPPED, new Object[] {
									dataSource
								});
					}
				});
				itemStop.setEnabled(!canStart);

				MenuItem itemStart = new MenuItem(menu, SWT.PUSH);
				Messages.setLanguageText(itemStart, "v3.MainWindow.button.start");
				itemStart.addListener(SWT.Selection, new Listener() {
					public void handleEvent(Event event) {
						if (fileInfo.getDownloadManager().getState() == DownloadManager.STATE_STOPPED) {
							TorrentUtil.queueDataSources(new Object[] { dataSource }, true);
						}
						
						FilesViewMenuUtil.changePriority(FilesViewMenuUtil.PRIORITY_NORMAL,
								new Object[] {
									dataSource
								});
					}
				});
				itemStart.setEnabled(canStart);

				new MenuItem(menu, SWT.SEPARATOR);

				MenuItem itemDelete = new MenuItem(menu, SWT.PUSH);
				Messages.setLanguageText(itemDelete, "v3.MainWindow.button.delete");
				itemDelete.addListener(SWT.Selection, new Listener() {
					public void handleEvent(Event event) {
						FilesViewMenuUtil.changePriority(FilesViewMenuUtil.PRIORITY_DELETE,
								new Object[] {
									dataSource
								});
					}
				});

				menu.setVisible(true);
				event.skipCoreFunctionality = true;
			}
			/*
			if (buttonBounds != null && buttonBounds.contains(event.x, event.y)) {
				int st = fileInfo.getStorageType();
				if ((st == DiskManagerFileInfo.ST_COMPACT || st == DiskManagerFileInfo.ST_REORDER_COMPACT)
						&& fileInfo.isSkipped()) {
					// deleted: Move to normal
					fileInfo.setPriority(0);
					fileInfo.setSkipped(false);
				} else if (fileInfo.isSkipped()) {
					// skipped: move to normal
					fileInfo.setPriority(0);
					fileInfo.setSkipped(false);
				} else if (fileInfo.getPriority() > 0) {

					// high: move to skipped
					fileInfo.setSkipped(true);
				} else {
					// normal: move to high
					fileInfo.setPriority(1);
				}
				//((TableRowCore) event.row).invalidate();
				((TableRowCore) event.row).redraw();
			}
			*/
		}
	}

}
