/*
 * Created on 2004/May/14
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
 */

package org.gudy.azureus2.pluginsimpl.local.ui.SWT;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.plugins.ui.*;
import org.gudy.azureus2.plugins.ui.SWT.GraphicSWT;

import org.gudy.azureus2.plugins.PluginView;
import org.gudy.azureus2.plugins.ui.SWT.SWTManager;
import org.gudy.azureus2.plugins.ui.model.BasicPluginViewModel;
import org.gudy.azureus2.plugins.ui.model.PluginViewModel;
import org.gudy.azureus2.pluginsimpl.local.ui.UIManagerImpl;
import org.gudy.azureus2.ui.swt.mainwindow.MainMenu;
import org.gudy.azureus2.ui.swt.mainwindow.MainWindow;
import org.gudy.azureus2.ui.swt.mainwindow.SWTThread;
import org.gudy.azureus2.ui.swt.plugins.UISWTInstance;
import org.gudy.azureus2.ui.swt.pluginsimpl.BasicPluginViewImpl;

import com.aelitis.azureus.core.impl.AzureusCoreImpl;

/*
 * @deprecated
 */

public class SWTManagerImpl
	implements SWTManager
{	
	protected static SWTManagerImpl singleton;
	
	private static AEMonitor	class_mon	= new AEMonitor( "SWTManager" );

	public static SWTManagerImpl getSingleton() {
		try{
			class_mon.enter();
		
			if (singleton == null)
				singleton = new SWTManagerImpl();
			return singleton;
		}finally{
			
			class_mon.exit();
		}
	}
  
  public Display getDisplay() {
  	SWTThread instance = SWTThread.getInstance();
  	if (instance == null)
  		return null;
    return instance.getDisplay();
  }
  
  public GraphicSWT createGraphic(Image img) {
    return new GraphicSWTImpl(img);
  }
  

	public void addView(final PluginView view, final boolean bAutoOpen) {
		try {
			UIManager ui_manager = AzureusCoreImpl.getSingleton().getPluginManager()
					.getDefaultPluginInterface().getUIManager();

			ui_manager.addUIListener(new UIManagerListener() {
				public void UIAttached(UIInstance instance) {
					if (instance instanceof UISWTInstance) {
						final MainWindow window = MainWindow.getWindow();
						System.out.println("OLD addView " + view.getPluginViewName() + ";"
								+ window);

						if (window != null) {

							if (view instanceof PluginViewWrapper) {

								// legacy support for RSSImport plugin
								// model already registered, no need to do anything as UI will pick it up

							} else {

								MainMenu menu = window.getMenu();
								if (menu != null) {
									window.getMenu().addPluginView(view);
								}

								if (bAutoOpen) {

									window.getDisplay().asyncExec(new AERunnable() {
										public void runSupport() {
											window.openPluginView(view);
										}
									});
								}
							}
						}
					}
				}

				public void UIDetached(UIInstance instance) {
				}
			});

		} catch (Throwable e) {
			// SWT not available prolly
		}
	} 

  	public void 
  	addView(
  		PluginView view)
  	{
  		addView(view, false);
  	} 

	public PluginView
	createPluginView(
		PluginViewModel	model )
	{		
		return( new PluginViewWrapper(model));
	
	}
	
	protected class
	PluginViewWrapper
		extends PluginView
	{
		private	PluginViewModel		model;
		
		protected
		PluginViewWrapper(
			PluginViewModel	_model )
		{
			model	= _model;
		}
		
		public String 
		getPluginViewName()
		{
			return( model.getName());
		}
	}
	
  /* 
   * Not working due to class loader being different between plugins and
   * main program.
   * 
  public boolean loadImage(String resource,String name) {
    try {
      ImageRepository.loadImage(getDisplay(),resource,name);
      return true;
    } catch(Exception e) {
      e.printStackTrace();
      return false;
    }
  }
  
  public Image getImage(String name) {
    return ImageRepository.getImage(name);
  }*/
}
