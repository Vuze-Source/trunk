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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;

import org.gudy.azureus2.core3.util.SystemTime;

import com.aelitis.azureus.core.dht.transport.DHTTransportException;
import com.aelitis.net.udp.PRUDPPacketRequest;

/**
 * @author parg
 *
 */

public class 
DHTUDPPacketRequest 
	extends PRUDPPacketRequest
{
	public static final int	HEADER_SIZE	= PRUDPPacketRequest.HEADER_SIZE + 13 + DHTUDPUtils.INETSOCKETADDRESS_IPV4_SIZE;
	

	private byte				version;
	private long				originator_time;
	private InetSocketAddress	originator_address;
	private int					originator_instance_id;
	
	private long				skew;
	
	public
	DHTUDPPacketRequest(
		int								_type,
		long							_connection_id,
		DHTTransportUDPContactImpl		_contact )
	{
		super( _type, _connection_id );
		
		version	= DHTUDPPacket.VERSION;
		
		originator_address		= _contact.getExternalAddress();
		originator_instance_id	= _contact.getInstanceID();
		originator_time			= SystemTime.getCurrentTime();
	}
	
	protected
	DHTUDPPacketRequest(
		DataInputStream		is,
		int					type,
		long				con_id,
		int					trans_id )
	
		throws IOException
	{
		super( type, con_id, trans_id );
		
		version	= is.readByte();
		
		DHTUDPPacket.checkVersion( version );
		
		originator_address		= DHTUDPUtils.deserialiseAddress( is );
		
		originator_instance_id	= is.readInt();
		
		originator_time			= is.readLong();
		
			// We maintain a rough view of the clock diff between them and us,
			// times are then normalised appropriately. 
			// If the skew is positive then this means our clock is ahead of their
			// clock. Thus any times they send us will need to have the skew added in
			// so that they're correct relative to us.
			// For example: X has clock = 01:00, they create a value that expires at
			// X+8 hours 09:00. They send X to us. Our clock is an hour ahead (skew=+1hr)
			// We receive it at 02:00 (our time) and therefore time it out an hour early.
			// We therefore need to adjust the creation time to be 02:00.
		
			// Likewise, when we return a time to a caller we need to adjust by - skew to
			// put the time into their frame of reference.
		
		skew = SystemTime.getCurrentTime() - originator_time;
	}
	
	protected long
	getClockSkew()
	{
		return( skew );
	}
	
	protected byte
	getVersion()
	{
		return( version );
	}
	
	protected InetSocketAddress
	getOriginatorAddress()
	{
		return( originator_address );
	}
	
	protected void
	setOriginatorAddress(
		InetSocketAddress	address )
	{
		originator_address	= address;
	}
	
	protected int
	getOriginatorInstanceID()
	{
		return( originator_instance_id );
	}
	
	public void
	serialise(
		DataOutputStream	os )
	
		throws IOException
	{
		super.serialise(os);

			// add to this and you need to amend HEADER_SIZE above
		
		os.writeByte( version );		
		
		try{
			DHTUDPUtils.serialiseAddress( os, originator_address );
			
		}catch( DHTTransportException	e ){
			
			throw( new IOException( e.getMessage()));
		}
		
		os.writeInt( originator_instance_id );
		
		os.writeLong( originator_time );
	}
}
