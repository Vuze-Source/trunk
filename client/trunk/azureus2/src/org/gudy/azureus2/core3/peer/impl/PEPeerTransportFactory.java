/*
 * File    : PEPeerTransportFactory.java
 * Created : 21-Oct-2003
 * By      : stuff
 * 
 * Azureus - a Java Bittorrent client
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License.
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

package org.gudy.azureus2.core3.peer.impl;

/**
 * @author parg
 *
 */

import org.gudy.azureus2.core3.config.*;
import org.gudy.azureus2.core3.peer.*;
import org.gudy.azureus2.core3.peer.impl.transport.base.*;
import org.gudy.azureus2.core3.peer.impl.transport.sharedport.*;

public class 
PEPeerTransportFactory 
{
	public static PEPeerTransport
	createTransport(
		PEPeerControl	 	control, 
		byte[] 				peerId, 
		String 				ip, 
		int 				port, 
		boolean 			fake )
	{
		return( new PEPeerTransportImpl( control, peerId, ip, port, fake ));
	}

	public static PEPeerServer
	createServer()
	{
		boolean shared_port 	= COConfigurationManager.getBooleanParameter( "Server.shared.port", true );
		
		if ( shared_port ){
			
			return( new PESharedPortServerImpl());
			
		}else{
		
			return( new PEPeerServerImpl());
		}
	}
}
