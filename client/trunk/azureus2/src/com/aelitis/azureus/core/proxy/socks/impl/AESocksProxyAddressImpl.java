/*
 * Created on 13-Dec-2004
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

package com.aelitis.azureus.core.proxy.socks.impl;

import java.net.InetAddress;

import com.aelitis.azureus.core.proxy.socks.AESocksProxyAddress;

/**
 * @author parg
 *
 */

public class 
AESocksProxyAddressImpl
	implements AESocksProxyAddress
{
	protected String			unresolved_address;
	protected InetAddress		address;
	protected int				port;
	
	protected
	AESocksProxyAddressImpl(
		String			_unresolved_address,
		InetAddress		_address,
		int				_port )
	{
		unresolved_address	= _unresolved_address;
		address				= _address;
		port				= _port;
	}
	
	public String
	getUnresolvedAddress()
	{
		return( unresolved_address );
	}
	
	public InetAddress
	getAddress()
	{
		return( address );
	}
	
	public int
	getPort()
	{
		return( port );
	}
}
