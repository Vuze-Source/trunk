/*
 * Created on 28-Nov-2005
 * Created by Paul Gardner
 * Copyright (C) 2005 Aelitis, All Rights Reserved.
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
 * AELITIS, SAS au capital de 40,000 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package com.aelitis.net.upnp;

import java.util.Comparator;

import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.plugins.utils.UTTimer;
import org.gudy.azureus2.plugins.utils.resourcedownloader.ResourceDownloaderFactory;
import org.gudy.azureus2.plugins.utils.xml.simpleparser.SimpleXMLParserDocument;
import org.gudy.azureus2.plugins.utils.xml.simpleparser.SimpleXMLParserDocumentException;

public interface 
UPnPAdapter 
{
	public SimpleXMLParserDocument
	parseXML(
		String	data )
	
		throws SimpleXMLParserDocumentException;
	
	public ResourceDownloaderFactory
	getResourceDownloaderFactory();
	
	public UTTimer
	createTimer(
		String	name );

	public void
	createThread(
		String		name,
		AERunnable	runnable );
	
	public Comparator
	getAlphanumericComparator();
	
	public void
	trace(
		Throwable	e );
	
	public void
	trace(
		String	str );
	
	public String
	getTraceDir();
}
