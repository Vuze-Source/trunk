/*
 * File    : AboutWindow.java
 * Created : 18 d�c. 2003}
 * By      : Olivier
 *
 * Azureus - a Java Bittorrent client
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
package org.gudy.azureus2.ui.swt.help;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.components.shell.ShellFactory;
import org.gudy.azureus2.ui.swt.mainwindow.*;
import org.gudy.azureus2.update.CorePatchLevel;

import com.aelitis.azureus.ui.swt.imageloader.ImageLoader;

/**
 * @author Olivier
 *
 */
public class AboutWindow {
	private final static String IMG_SPLASH = "azureus_splash";

  static Image image;
  static AEMonitor	class_mon	= new AEMonitor( "AboutWindow" );
  private static Shell instance;
	private static Image imgSrc;
	private static int paintColorTo = 0;
	private static int paintColorDir = 2;

	private static Image imageToDispose;

  public static void show() {
  	Utils.execSWTThread(new AERunnable() {
			public void runSupport() {
				_show();
			}
		});
  }

  private static void _show() {
    if(instance != null)
    {
        instance.open();
        return;
    }
    
    paintColorTo = 0;

    final Shell window = ShellFactory.createMainShell(SWT.DIALOG_TRIM);
    Utils.setShellIcon(window);
    final Display display = window.getDisplay();

    window.setText(MessageText.getString("MainWindow.about.title") + " " + Constants.getCurrentVersion());
    GridData gridData;
    window.setLayout(new GridLayout(2, false));

    ImageLoader imageLoader = ImageLoader.getInstance();
    imgSrc = imageLoader.getImage(IMG_SPLASH);
    if (imgSrc != null) {
      int w = imgSrc.getBounds().width;
      int ow = w;
      int h = imgSrc.getBounds().height;
      
      Image imgGray = new Image(display, imageLoader.getImage(IMG_SPLASH),
					SWT.IMAGE_GRAY);
      imageLoader.releaseImage(IMG_SPLASH);
      GC gc = new GC(imgGray);
      if (Constants.isOSX) {
      	gc.drawImage(imgGray, (w - ow) / 2, 0);
      } else {
      	gc.copyArea(0, 0, ow, h, (w - ow) / 2, 0);
      }
      gc.dispose();
      
      Image image2 = new Image(display, w, h);
      gc = new GC(image2);
      gc.setBackground(window.getBackground());
      gc.fillRectangle(image2.getBounds());
      gc.dispose();
      imageToDispose = image = Utils.renderTransparency(display, image2, imgGray, new Point(0, 0), 180);
      image2.dispose();
      imgGray.dispose();
    }
    
    final Canvas labelImage = new Canvas(window, SWT.DOUBLE_BUFFERED);
    //labelImage.setImage(image);
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL);
    gridData.horizontalSpan = 2;
    gridData.horizontalIndent = gridData.verticalIndent = 0;
    final Rectangle imgBounds = image.getBounds();
		final Rectangle boundsColor = imgSrc.getBounds();
    gridData.widthHint = Utils.adjustPXForDPI(300);
    gridData.heightHint = imgBounds.height + imgBounds.y + 20;
    labelImage.setLayoutData(gridData);
    labelImage.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) {
				try{
					Rectangle clipping = e.gc.getClipping();
					int ofs = (labelImage.getSize().x - boundsColor.width) / 2;
					if (paintColorTo > 0) {
						e.gc.drawImage(imgSrc, 0, 0, paintColorTo, boundsColor.height, ofs, 10, paintColorTo, boundsColor.height);
					}
					
					if (clipping.x + clipping.width > ofs + paintColorTo && imgBounds.width - paintColorTo - 1 > 0) {
						e.gc.drawImage(image, 
								paintColorTo + 1, 0, imgBounds.width - paintColorTo - 1, imgBounds.height, 
								paintColorTo + 1 + ofs, 10, imgBounds.width - paintColorTo - 1, imgBounds.height);
					}
				}catch( Throwable f ){
					// seen some 'argument not valid errors spewed here, couldn't track down
					// the cause though :( parg.
				}
			}
		});
  
    Group gInternet = new Group(window, SWT.NULL);
    GridLayout gridLayout = new GridLayout();
    gridLayout.numColumns = 2;
    gridLayout.makeColumnsEqualWidth = true;
    gInternet.setLayout(gridLayout);
    Messages.setLanguageText(gInternet, "MainWindow.about.section.internet"); //$NON-NLS-1$
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    Utils.setLayoutData(gInternet, gridData);
  
    Group gSys = new Group(window, SWT.NULL);
    gSys.setLayout(new GridLayout());
    Messages.setLanguageText(gSys, "MainWindow.about.section.system"); //$NON-NLS-1$
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    gridData.verticalSpan = 1;
    Utils.setLayoutData(gSys, gridData);
    
    String swt = "";
    if (Utils.isGTK) {
    	try {
    		swt = "/" + System.getProperty("org.eclipse.swt.internal.gtk.version");
			} catch (Throwable e1) {
				// TODO Auto-generated catch block
			}
    }

    Text txtSysInfo = new Text(gSys, SWT.READ_ONLY | SWT.MULTI | SWT.WRAP);
    txtSysInfo.setBackground(display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
    
    String about_text = 
    		
    		"Java " + System.getProperty("java.version") + "\n "
    				+ System.getProperty("java.vendor") + "\n"
    				+ System.getProperty("java.home") + "\n\n" 
    				+ "SWT v" + SWT.getVersion() + ", " + SWT.getPlatform() + swt + "\n"
    				+ System.getProperty("os.name") + " v"
    				+ System.getProperty("os.version") + ", "
    				+ System.getProperty("os.arch") + "(OS=" + (Constants.isOS64Bit?64:32) + " bit) \n"
    				+ Constants.APP_NAME.charAt(0) + Constants.getCurrentVersion() + (Constants.AZUREUS_SUBVER.length()==0?"":("-"+Constants.AZUREUS_SUBVER)) + "/" + CorePatchLevel.getCurrentPatchLevel() + " " 
    				+ COConfigurationManager.getStringParameter("ui");
    
    txtSysInfo.setText( about_text );
    Utils.setLayoutData(txtSysInfo, gridData = new GridData(GridData.FILL_BOTH));
    if (window.getCaret() != null)
    	window.getCaret().setVisible(false);

		final String[][] link = {
			{
				"homepage",
				"bugreports",
				"forumdiscussion",
				"wiki",
				"!Vuze Wiki Hidden Service (I2P)",
				"!Vuze Wiki Hidden Service (Tor)",
				"contributors",
				"!EULA",
				"!Privacy Policy",
				"!Legal",
				"!FOSS Licenses"
			},
			{
				"https://www.vuze.com",
				"http://www.vuze.com/forums/open-development",
				"http://forum.vuze.com",
				Constants.AZUREUS_WIKI,
				"http://que23xpe7o3lzq6auv6stb4bha7ddavrlgqdv2cuhgd36fgfmp6q.b32.i2p/",
				"http://dr5aamfveql2b34p.onion/",
				Constants.AZUREUS_WIKI + "Contributors",
				"https://www.vuze.com/corp/terms.php",
				"https://www.vuze.com/corp/privacy.php",
				"https://www.vuze.com/corp/legal",
				Constants.AZUREUS_WIKI + "Vuze_Client_FOSS_Licenses"
			}
		};
  
    for (int i = 0; i < link[0].length; i++) {
      final CLabel linkLabel = new CLabel(gInternet, SWT.NONE);
      if (link[0][i].startsWith("!")) {
        linkLabel.setText(link[0][i].substring(1));
      } else {
      	linkLabel.setText(MessageText.getString("MainWindow.about.internet." + link[0][i]));
      }
      linkLabel.setData(link[1][i]);
      linkLabel.setCursor(display.getSystemCursor(SWT.CURSOR_HAND));
      linkLabel.setForeground(Colors.blue);
      gridData = new GridData(GridData.FILL_HORIZONTAL);
      gridData.horizontalSpan = 1;
      Utils.setLayoutData(linkLabel, gridData);
      linkLabel.addMouseListener(new MouseAdapter() {
        public void mouseDoubleClick(MouseEvent arg0) {
        	Utils.launch((String) ((CLabel) arg0.widget).getData());
        }
        public void mouseUp(MouseEvent arg0) {
        	Utils.launch((String) ((CLabel) arg0.widget).getData());
        }
      });
      ClipboardCopy.addCopyToClipMenu( linkLabel );
    }
    
    Label labelOwner = new Label(window, SWT.WRAP | SWT.CENTER);
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL);
    gridData.horizontalSpan = 2;
    gridData.horizontalIndent = gridData.verticalIndent = 0;
    Utils.setLayoutData(labelOwner, gridData);
    labelOwner.setText(MessageText.getString( "MainWindow.about.product.info" ));

    
    Listener keyListener =  new Listener() {
      public void handleEvent(Event e) {
        if(e.character == SWT.ESC) {
          window.dispose();                
        }
      }
    };
    
    window.addListener(SWT.KeyUp,keyListener);
  
    window.pack();
    txtSysInfo.setFocus();
    Utils.centreWindow(window);
    window.open();

    instance = window;
    window.addDisposeListener(new DisposeListener() {
        public void widgetDisposed(DisposeEvent event) {
            instance = null;
            disposeImage();
        }
    });

		final int maxX = image.getBounds().width;
		final int maxY = image.getBounds().height;
		Utils.execSWTThread(new AERunnable() {
			public void runSupport() {
				if (image == null || image.isDisposed() || labelImage.isDisposed()) {
					return;
				}
				if (display.isDisposed()) {
					return;
				}
				paintColorTo += paintColorDir;

				Utils.execSWTThreadLater(7 * paintColorDir, this);

				int ofs = (labelImage.getSize().x - boundsColor.width) / 2;
				labelImage.redraw(paintColorTo - paintColorDir + ofs, 10, paintColorDir, maxY, true);

				if (paintColorTo >= maxX || paintColorTo <= 0) {
					paintColorTo = 0;
					//paintColorDir = (int) (Math.random() * 5) + 2;
					Image tmp = image;
					image = imgSrc;
					imgSrc = tmp;
				}
			}
    });

  }
  
  public static void 
  disposeImage() 
  {
  	try{
  		class_mon.enter();
			Utils.disposeSWTObjects(new Object[] {
				imageToDispose
			});
	    ImageLoader imageLoader = ImageLoader.getInstance();
	    imageLoader.releaseImage(IMG_SPLASH);
	    image = null;
	    imgSrc = null;
  	}finally{
  		
  		class_mon.exit();
  	}
  }

  public static void main(String[] args) {
  	try {
  		Display display = new Display();
  		Colors.getInstance();
			SWTThread.createInstance(null);
			show();
			
			while (!display.isDisposed() && instance != null && !instance.isDisposed()) {
				if (!display.readAndDispatch()) {
					display.sleep();
				}
			}
			
			if (!display.isDisposed()) {
				display.dispose();
			}
		} catch (SWTThreadAlreadyInstanciatedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
