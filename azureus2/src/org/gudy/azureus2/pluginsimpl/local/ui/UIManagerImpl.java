/*
 * Created on 19-Apr-2004
 * Created by Paul Gardner
 * Copyright (C) 2004 Aelitis, All Rights Reserved.
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
 * AELITIS, SARL au capital de 30,000 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package org.gudy.azureus2.pluginsimpl.local.ui;

/**
 * @author parg
 *
 */

import org.gudy.azureus2.plugins.*;
import org.gudy.azureus2.plugins.ui.*;
import org.gudy.azureus2.plugins.ui.model.*;
import org.gudy.azureus2.plugins.ui.tables.mytracker.*;
import org.gudy.azureus2.pluginsimpl.local.ui.mytracker.*;
import org.gudy.azureus2.pluginsimpl.local.ui.model.*;
import org.gudy.azureus2.pluginsimpl.local.ui.view.BasicPluginViewImpl;

import org.gudy.azureus2.ui.swt.mainwindow.ClipboardCopy;

public class 
UIManagerImpl 
	implements UIManager
{	
	protected PluginInterface		pi;
	
	public
	UIManagerImpl(
		PluginInterface		_pi )
	{
		pi		=_pi;
	}
	
	public BasicPluginViewModel
	getBasicPluginViewModel(
		String			name )
	{
		return( new BasicPluginViewModelImpl( name ));
	}
	
	public PluginView
	createPluginView(
		PluginViewModel	model )
	{
	  if(model instanceof BasicPluginViewModel) {
	    return new BasicPluginViewImpl((BasicPluginViewModel)model);
	  } else {
	    //throw new Exception("Unsupported Model : " + model.getClass());
	    return null;
	  }
	}
	
	public BasicPluginConfigModel
	createBasicPluginConfigModel(
		String		section_name )
	{
		try{
			return( new BasicPluginConfigModelImpl( pi, null, section_name ));
		}catch( Throwable e ){
			// no SWT probably
			
			return( null );
		}
	}
	
	
	public BasicPluginConfigModel
	createBasicPluginConfigModel(
		String		parent_section,
		String		section_name )
	{
		try{
			return( new BasicPluginConfigModelImpl( pi, parent_section, section_name ));
		}catch( Throwable e ){
			// no SWT probably
			
			return( null );
		}
	}
	
	public MyTracker
	getMyTracker()
	{
		return( MyTrackerImpl.getSingleton());
	}
	
	public void
	copyToClipBoard(
		String		data )
	
		throws UIException
	{
		try{
			ClipboardCopy.copyToClipBoard( data );
			
		}catch( Throwable e ){
			
			throw( new UIException( "Failed to copy to clipboard", e ));
		}

	}
}
