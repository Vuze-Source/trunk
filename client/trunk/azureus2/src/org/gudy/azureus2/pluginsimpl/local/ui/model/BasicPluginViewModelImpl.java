/*
 * Created on 27-Apr-2004
 * Created by Paul Gardner
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

package org.gudy.azureus2.pluginsimpl.local.ui.model;

/**
 * @author parg
 *
 */

import org.gudy.azureus2.plugins.ui.model.*;
import org.gudy.azureus2.plugins.ui.components.*;
import org.gudy.azureus2.pluginsimpl.local.ui.UIManagerImpl;
import org.gudy.azureus2.pluginsimpl.local.ui.components.*;

public class 
BasicPluginViewModelImpl 
	implements BasicPluginViewModel
{
	private UIManagerImpl		ui_manager;
	
	private String		name;
	
	private UITextField	status;
	private UITextField	activity;
	private UITextArea	log;
	private UIProgressBar	progress;
	private String sConfigSectionID;
	
	public
	BasicPluginViewModelImpl(
		UIManagerImpl	_ui_manager,
		String			_name )
	{
		ui_manager	= _ui_manager;
		name		= _name;
		
		status 		= new UITextFieldImpl();
		activity	= new UITextFieldImpl();
		log			= new UITextAreaImpl();
		progress	= new UIProgressBarImpl();
	}
	
	public String
	getName()
	{
		return( name );
	}
	
	public UITextField
	getStatus()
	{
		return( status );
	}
	
	public UITextField
	getActivity()
	{
		return( activity );
	}
	
	public UITextArea
	getLogArea()
	{
		return( log );
	}
	
	public UIProgressBar
	getProgress()
	{
		return( progress );
	}
	
	public void
	setConfigSectionID(String id)
	{
		sConfigSectionID = id;
	}
	
	public String
	getConfigSectionID()
	{
		return sConfigSectionID;
	}
	
	public void
	destroy()
	{
		ui_manager.destroy( this );
	}
}
