/*
 * File    : PRUDPPacket.java
 * Created : 20-Jan-2004
 * By      : parg
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

package com.aelitis.net.udp;

/**
 * @author parg
 *
 */

import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;

import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.SystemTime;

public abstract class 
PRUDPPacket 
{
	public static final int	MAX_PACKET_SIZE			= 8192;
	public static final int DEFAULT_UDP_TIMEOUT		= 30000;

	protected static int			next_id 	= new Random(SystemTime.getCurrentTime()).nextInt();
	protected static AEMonitor		class_mon	= new AEMonitor( "PRUDPPacket" );

	private	InetSocketAddress	address;
	
	private int		type;
	private int		transaction_id;
	
	protected
	PRUDPPacket(
		int		_type,
		int		_transaction_id )
	{
		type			= _type;
		transaction_id	= _transaction_id;
	}
	
	protected
	PRUDPPacket(
		int		_type )
	{
		type			= _type;
		
		try{
			class_mon.enter();
			
			transaction_id	= next_id++;
			
		}finally{
			
			class_mon.exit();
		}
	}
	
	public void
	setAddress(
		InetSocketAddress	_address )
	{
		address	= _address;
	}
	
	public InetSocketAddress
	getAddress()
	{
		return( address );
	}
	
	public int
	getAction()
	{
		return( type );
	}
	
	public int
	getTransactionId()
	{
		return( transaction_id );
	}
	
	public abstract void
	serialise(
		DataOutputStream	os )
	
		throws IOException;
	
	public String
	getString()
	{
		return( "type=".concat(String.valueOf(type)));
	}
}
