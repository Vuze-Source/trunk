/*
 * File    : TRHostListener.java
 * Created : 30-Oct-2003
 * By      : stuff
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

package org.gudy.azureus2.core3.tracker.host;

/**
 * @author parg
 *
 */

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URL;

import org.gudy.azureus2.core3.util.AsyncController;

public interface 
TRHostListener 
{
	public void
	torrentAdded(
		TRHostTorrent		t );	
		
	public void
	torrentChanged(
		TRHostTorrent		t );
	
	public void
	torrentRemoved(
		TRHostTorrent		t );
	
	public boolean
	handleExternalRequest(
		InetSocketAddress	client_address,
		String				user,
		String				url,
		URL					absolute_url,
		String				header,
		InputStream			is,
		OutputStream		os,
		AsyncController		async )
	
		throws IOException;
}
