/*
 * Created on Apr 30, 2004
 * Created by Olivier Chalouhi
 * Copyright (C) 2004, 2005, 2006 Aelitis, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * AELITIS, SAS au capital de 46,603.30 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */
package org.gudy.azureus2.ui.swt.mainwindow;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.ui.swt.ImageRepository;
import org.gudy.azureus2.ui.swt.Utils;

import com.aelitis.azureus.ui.IUIIntializer;
import com.aelitis.azureus.ui.InitializerListener;
import com.aelitis.azureus.ui.swt.utils.ColorCache;

/**
 * The initial Splash Screen shown while azureus loads 
 */
public class SplashWindow
	implements InitializerListener
{

	// config 1 : PB_HEIGHT = 3, PB_INVERTED = false
	// config 2 : PB_HEIGHT = 3, PB_INVERTED = true, PB_INVERTED_BG_HEIGHT = 3
	// config 3 : PB_HEIGHT = 2, PB_INVERTED = true, PB_INVERTED_BG_HEIGHT = 2
	// config 4 : PB_HEIGHT = 3, PB_INVERTED = true, PB_INVERTED_BG_HEIGHT = 1, PB_INVERTED_X_OFFSET = 4
	
	protected static final int OFFSET_LEFT = 10;
	protected static final int OFFSET_RIGHT = 139;
	protected static final int OFFSET_BOTTOM = 10;
	protected static final int PB_HEIGHT = 2;
	
	protected static final boolean PB_INVERTED = true;
	protected static final int PB_INVERTED_BG_HEIGHT = 2;
	protected static final int PB_INVERTED_X_OFFSET = 0;
	
	protected static final boolean DISPLAY_BORDER = true;

	Display display;

	IUIIntializer initializer;

	Shell splash;

	//Label currentTask;
	//ProgressBar percentDone;

	Canvas canvas;

	Image background;

	int width;

	int height;

	Image current;

	Color progressBarColor;

	Color textColor;
	
	Color fadedGreyColor;

	Font textFont;

	private String task;

	private int percent;

	private boolean updating;
	
	int pbX, pbY, pbWidth;

	public SplashWindow(Display display) {
		this(display, null);
	}

	public static void main(String args[]) {
		Display display = new Display();
		ImageRepository.loadImagesForSplashWindow(display);

		final SplashWindow splash = new SplashWindow(display);

		Thread t = new Thread() {
			public void run() {
				try {
					int percent = 0;
					while (percent <= 100) {
						splash.reportPercent(percent++);
						splash.reportCurrentTask("Loading dbnvsudn vjksfdh fgshdu fbhsduh bvsfd fbsd fbvsdb fsuid : "
								+ percent);
						Thread.sleep(100);
					}
				} catch (Exception e) {
					// TODO: handle exception
				}
				splash.closeSplash();
			}
		};
		t.start();

		while (!splash.splash.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		display.dispose();
	}

	public SplashWindow(Display _display, IUIIntializer initializer) {
		this.display = _display;
		this.initializer = initializer;

		splash = new Shell(display, SWT.NO_TRIM);
		splash.setText(Constants.APP_NAME);
		Utils.setShellIcon(splash);

		/*GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		splash.setLayout(layout);
		Label label = new Label(splash, SWT.NONE);
		label.setImage(ImageRepository.getImage("azureus_splash"));

		currentTask = new Label(splash,SWT.BORDER);
		GridData gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.GRAB_HORIZONTAL);
		currentTask.setLayoutData(gridData);
		currentTask.setBackground(ColorCache.getColor(display, 255, 255, 255));
		currentTask.setText("(: " + Constants.APP_NAME + " :)");
		
		this.percentDone = new ProgressBar(splash,SWT.HORIZONTAL);
		this.percentDone.setMinimum(0);
		this.percentDone.setMaximum(100);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		this.percentDone.setLayoutData(gridData);*/

		splash.setLayout(new FillLayout());
		canvas = new Canvas(splash, SWT.DOUBLE_BUFFERED);

		background = ImageRepository.getImage("azureus_splash");
		current = new Image(display, background, SWT.IMAGE_COPY);

		progressBarColor = new Color(display, 21, 92, 198);
		textColor = new Color(display, 180, 180, 180);
		fadedGreyColor = new Color(display, 70, 70, 70);

		width = background.getBounds().width;
		height = background.getBounds().height;
		
		pbX = OFFSET_LEFT;
		pbY = height - OFFSET_BOTTOM;
		pbWidth = width - OFFSET_LEFT - OFFSET_RIGHT;

		canvas.setSize(width, height);
		Font font = canvas.getFont();
		FontData[] fdata = font.getFontData();
		fdata[0].setHeight(Constants.isOSX ? 9 : 7);
		textFont = new Font(display, fdata);

		canvas.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent event) {
				GC gc = event.gc;
				gc.drawImage(current, event.x, event.y, event.width, event.height,
						event.x, event.y, event.width, event.height);
			}
		});

		//splash.pack();
		splash.setSize(width, height);
		splash.layout();
		Utils.centreWindow(splash);
		splash.open();

		if (initializer != null) {
			initializer.addListener(this);
		}
	}

	public static void create(final Display display, final Initializer initializer) {
		Utils.execSWTThread(new AERunnable() {
			public void runSupport() {
				if (display == null || display.isDisposed())
					return;

				new SplashWindow(display, initializer);
			}
		});
	}

	/*
	 * Should be called by the GUI thread
	 */
	public void closeSplash() {
		Utils.execSWTThread(new AERunnable() {
			public void runSupport() {
				try {
					if (initializer != null)
						initializer.removeListener(SplashWindow.this);
					if (splash != null && !splash.isDisposed())
						splash.dispose();
					ImageRepository.unloadImage("azureus_splash");
					if (current != null && !current.isDisposed()) {
						current.dispose();
					}
					if (progressBarColor != null && !progressBarColor.isDisposed()) {
						progressBarColor.dispose();
					}
					if (textColor != null && !textColor.isDisposed()) {
						textColor.dispose();
					}
					if (textFont != null && !textFont.isDisposed()) {
						textFont.dispose();
					}

				} catch (Exception e) {
					//ignore
				}
			}
		});
	}

	/*
	 * STProgressListener implementation
	 */

	// AzureusCoreListener
	public void reportCurrentTask(final String task) {
		//Ensure that display is set and not disposed
		if (display == null || display.isDisposed())
			return;

		if (this.task == null || this.task.compareTo(task) != 0) {
			this.task = task;
			update();
		}
	}

	/**
	 * 
	 *
	 * @since 3.0.0.7
	 */
	private void update() {
		if (updating && !Utils.isThisThreadSWT()) {
			return;
		}

		updating = true;
		//Post runnable to SWTThread
		Utils.execSWTThread(new AERunnable() {
			public void runSupport() {
				updating = false;
				if (splash == null || splash.isDisposed()) {
					return;
				}

				Image newCurrent = new Image(display, background, SWT.IMAGE_COPY);
				GC gc = new GC(newCurrent);

				try {
					gc.setAntialias(SWT.ON);
					gc.setTextAntialias(SWT.ON);
				} catch (Exception e) {

				}

				if (task != null) {
					if (task.length() > 60) {
						task = task.substring(0, 60);
						if(Constants.isOSX) task = task.substring(0, 51);
					}
					gc.setFont(textFont);
					gc.setForeground(textColor);
					gc.drawText(task, OFFSET_LEFT, height - OFFSET_BOTTOM - 16, true);
				}

				if(PB_INVERTED){
					gc.setForeground(fadedGreyColor);
					gc.setBackground(fadedGreyColor);
					gc.fillRectangle(pbX-PB_INVERTED_X_OFFSET, pbY + Math.abs(PB_HEIGHT - PB_INVERTED_BG_HEIGHT) / 2, pbWidth+2*PB_INVERTED_X_OFFSET, PB_INVERTED_BG_HEIGHT);
					gc.setForeground(progressBarColor);
					gc.setBackground(progressBarColor);
					gc.fillRectangle(pbX, pbY, percent * pbWidth / 100, PB_HEIGHT);
					
				} else {
					gc.setForeground(progressBarColor);
					gc.setBackground(progressBarColor);
					if(DISPLAY_BORDER){
						gc.fillRectangle(pbX + 1, pbY, percent * (pbWidth - 2) / 100, PB_HEIGHT);
						gc.setForeground(fadedGreyColor);
						gc.setBackground(fadedGreyColor);
						gc.fillRectangle(pbX, pbY - 1, pbWidth, 1);
						gc.fillRectangle(pbX + pbWidth - 1, pbY, 1, PB_HEIGHT);
						gc.fillRectangle(pbX, pbY + PB_HEIGHT, pbWidth, 1);
						gc.fillRectangle(pbX, pbY, 1, PB_HEIGHT);
					} else {
						gc.fillRectangle(pbX, pbY, percent * pbWidth / 100, PB_HEIGHT);
					}
				}
				
				Image old = current;
				current = newCurrent;
				if (old != null && !old.isDisposed()) {
					old.dispose();
				}

				gc.dispose();

				canvas.redraw(0, height - 26, width, 26, true);
			}
		});
	}

	public int getPercent() {
		return percent;
	}

	// AzureusCoreListener
	public void reportPercent(final int percent) {
		//System.out.println("splash: " + percent + " via " + Debug.getCompressedStackTrace());
		//Ensure that display is set and not disposed
		if (display == null || display.isDisposed())
			return;

		//OK Tricky way to close the splash window BUT ... sending a percent > 100 means closing
		if (percent > 100) {
			closeSplash();
			return;
		}

		if (this.percent != percent) {
			this.percent = percent;
			update();
		}
	}

}
