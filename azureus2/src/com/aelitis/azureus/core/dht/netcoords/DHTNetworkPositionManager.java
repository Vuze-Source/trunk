/*
 * Created on 24-Apr-2006
 * Created by Paul Gardner
 * Copyright (C) 2006 Aelitis, All Rights Reserved.
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
 * AELITIS, SAS au capital de 46,603.30 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package com.aelitis.azureus.core.dht.netcoords;

import java.util.*;
import java.io.DataInputStream;
import java.io.IOException;

import org.gudy.azureus2.core3.util.Debug;

public class 
DHTNetworkPositionManager 
{
	private static DHTNetworkPositionProvider[]	providers = new DHTNetworkPositionProvider[0];
	
	public static void
	registerProvider(
		DHTNetworkPositionProvider	provider )
	{
		synchronized( providers ){
			
			DHTNetworkPositionProvider[]	p = new DHTNetworkPositionProvider[providers.length + 1 ];
			
			System.arraycopy( providers, 0, p, 0, providers.length );
			
			p[providers.length] = provider;
			
			providers	= p;
		}
	}
	
	public static DHTNetworkPosition[]
	createPositions()
	{
		DHTNetworkPositionProvider[]	prov = providers;
		
		DHTNetworkPosition[]	res = new DHTNetworkPosition[prov.length];
		
		for (int i=0;i<res.length;i++){
			
			res[i] = prov[i].create();
		}
		
		return( res );
	}
	
	public static float
	estimateRTT(
		DHTNetworkPosition[]		p1s,
		DHTNetworkPosition[]		p2s )
	{
		byte	best_provider = DHTNetworkPosition.POSITION_TYPE_NONE;
		
		float	best_result	= Float.NaN;
		
		for (int i=0;i<p1s.length;i++){
			
			DHTNetworkPosition	p1 = p1s[i];
			
			byte	p1_type = p1.getPositionType();
			
			for (int j=0;j<p2s.length;j++){
				
				DHTNetworkPosition	p2 = p2s[j];
				
				if ( p1_type == p2.getPositionType()){
					
					float	f = p1.estimateRTT( p2 );
					
					if ( !Float.isNaN( f )){
						
						if ( p1_type > best_provider ){
							
							best_result		= f;
							best_provider	= p1_type;
						}
					}
					
					break;
				}
			}
		}
		
		return( best_result );
	}
	
	public static void
	update(
		DHTNetworkPosition[]	local_positions,
		DHTNetworkPosition[]	remote_positions,
		float					rtt )
	{	
		for (int i=0;i<local_positions.length;i++){
			
			DHTNetworkPosition	p1 = local_positions[i];
						
			for (int j=0;j<remote_positions.length;j++){
				
				DHTNetworkPosition	p2 = remote_positions[j];
				
				if ( p1.getPositionType() == p2.getPositionType()){
					
					p1.update( p2, rtt );
				}
			}
		}
	}
	
	public static DHTNetworkPosition
	deserialise(
		byte			position_type,
		DataInputStream	is )
	
		throws IOException
	{
		DHTNetworkPositionProvider[]	prov = providers;

		for (int i=0;i<prov.length;i++){
			
			if ( prov[i].getPositionType() == position_type ){
				
				return( prov[i].deserialise( is ));
			}
		}
		
		return( null );
	}
}