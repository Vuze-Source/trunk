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
	private InetSocketAddress		external_address;
	private InetSocketAddress		transport_address;
	
	private byte[]				id;
	private int					instance_id;
	
	protected
	DHTTransportUDPContactImpl(
		DHTTransportUDPImpl		_transport,
		InetSocketAddress		_transport_address,
		InetSocketAddress		_external_address,
		int						_instance_id )
	
		throws DHTTransportException
	{
		transport				= _transport;
		transport_address		= _transport_address;
		external_address		= _external_address;
		
		if ( transport_address.equals( external_address )){
			
			external_address	= transport_address;
		}
		
		instance_id		=		 _instance_id;
		
		if ( 	transport_address == external_address ||
				transport_address.getAddress().equals( external_address.getAddress())){
			
			id = DHTUDPUtils.getNodeID( external_address );
		}
	}
	
	protected boolean
	isValid()
	{
		return( id != null );
	}
	
	public InetSocketAddress
	getTransportAddress()
	{
		return( transport_address );
	}
	
	public InetSocketAddress
	getExternalAddress()
	{
		return( external_address );
	}
	
	public int
	getMaxFailForLiveCount()
	{
		return( transport.getMaxFailForLiveCount() );
	}
	
	public int
	getMaxFailForUnknownCount()
	{
		return( transport.getMaxFailForUnknownCount() );
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
		if ( id == null ){
			
			throw( new RuntimeException( "Invalid contact" ));
		}
		
		return( id );
	}
	
	public void
	exportContact(
		DataOutputStream	os )
	
		throws IOException, DHTTransportException
	{
		transport.exportContact( this, os );
	}
	
	public String
	getString()
	{
		if ( transport_address.equals( external_address )){
			
			return( transport_address.toString());
		}
		
		return( "tran="+transport_address.toString()+",ext="+external_address);
	}
}
