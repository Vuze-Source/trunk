/*
 * Created on 21-Jan-2005
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

package com.aelitis.azureus.core.dht.transport.udp.impl;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;


import com.aelitis.azureus.core.dht.transport.*;
import com.aelitis.azureus.core.dht.transport.udp.*;

/**
 * @author parg
 *
 */

public class 
DHTTransportUDPContactImpl
	implements DHTTransportUDPContact
{
	private	DHTTransportUDPImpl		transport;
	private InetSocketAddress		address;
	
	private byte[]				id;
	private int					instance_id;
	
	protected
	DHTTransportUDPContactImpl(
		DHTTransportUDPImpl		_transport,
		InetSocketAddress		_address,
		int						_instance_id )
	{
		transport		= _transport;
		address			= _address;
		instance_id		= _instance_id;
		
		id = DHTUDPUtils.getNodeID( address );
	}
	
	public InetSocketAddress
	getAddress()
	{
		return( address );
	}
	
	public int
	getMaxFailCount()
	{
		return( transport.getMaxFailCount());
	}
	
	public int
	getInstanceID()
	{
		return( instance_id );
	}
	
	protected void
	setInstanceID(
		int		_instance_id )
	{
		instance_id	= _instance_id;
	}
	
	public void
	sendPing(
		DHTTransportReplyHandler	handler )
	{
		transport.sendPing( this, handler );
	}
		
	public void
	sendStore(
		DHTTransportReplyHandler	handler,
		byte[]						key,
		DHTTransportValue			value )
	{
		transport.sendStore( this, handler, key, value );
	}
	
	public void
	sendFindNode(
		DHTTransportReplyHandler	handler,
		byte[]						nid )
	{
		transport.sendFindNode( this, handler, nid );
	}
		
	public void
	sendFindValue(
		DHTTransportReplyHandler	handler,
		byte[]						key )
	{
		transport.sendFindValue( this, handler, key );
	}
	
	public byte[]
	getID()
	{
		return( id );
	}
	
	public void
	exportContact(
		DataOutputStream	os )
	
		throws IOException
	{
		transport.exportContact( this, os );
	}
	
	public String
	getString()
	{
		return( address.toString());
	}
}
