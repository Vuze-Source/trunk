/*
 * Created on Feb 8, 2008
 * Created by Paul Gardner
 * 
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License only.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.aelitis.azureus.plugins.extseed;

public class 
ExternalSeedManualPeer 
{
	private ExternalSeedPeer		peer;
	
	protected
	ExternalSeedManualPeer(
		ExternalSeedPeer	_peer )
	{
		peer		= _peer;
	}
	
	public String
	getIP()
	{
		return( peer.getIp());
	}
	  
	public byte[]
	read(
		int		piece_number,
		int		offset,
		int		length,
		int		timeout )
	
		throws ExternalSeedException
	{
		return( peer.getReader().read( piece_number, offset, length, timeout ));
	}
	
	public ExternalSeedPeer
	getDelegate()
	{
		return( peer );
	}
}
