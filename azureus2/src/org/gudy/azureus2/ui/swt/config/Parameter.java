/*
 * Created on 22-May-2004
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

package org.gudy.azureus2.ui.swt.config;

/**
 * @author parg
 *
 */

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.widgets.Control;

public abstract class 
Parameter 
	implements IParameter
{
	protected  List	change_listeners;

	public Control[]
	getControls()
	{
		return( new Control[]{ getControl() });
	}
	
 	public synchronized void
	addChangeListener(
		ParameterChangeListener	l )
	{
 		if ( change_listeners == null ){
 			
 			change_listeners = new ArrayList(1);
 		}
 		
  		change_listeners.add( l );
	}
		
	public void
	removeChangeListener(
		ParameterChangeListener	l )
	{
		change_listeners.remove(l);
	}
}
