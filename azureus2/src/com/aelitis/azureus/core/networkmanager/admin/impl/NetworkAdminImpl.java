/*
 * Created on 1 Nov 2006
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
 * AELITIS, SAS au capital de 63.529,40 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */


package com.aelitis.azureus.core.networkmanager.admin.impl;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.logging.LogAlert;
import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.util.SimpleTimer;
import org.gudy.azureus2.core3.util.TimerEvent;
import org.gudy.azureus2.core3.util.TimerEventPerformer;

import com.aelitis.azureus.core.networkmanager.admin.NetworkAdmin;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdminPropertyChangeListener;
import com.aelitis.azureus.core.util.CopyOnWriteList;

public class 
NetworkAdminImpl
	extends NetworkAdmin
{
	private static final LogIDs LOGID = LogIDs.NWMAN;
	
	private Set			old_network_interfaces;
	private InetAddress	old_bind_ip;
	
	private CopyOnWriteList	listeners = new CopyOnWriteList();
	
	public
	NetworkAdminImpl()
	{
		COConfigurationManager.addParameterListener(
			"Bind IP",
			new ParameterListener()
			{
				public void 
				parameterChanged(
					String parameterName )
				{
					checkDefaultBindAddress( false );
				}
			});
		
		SimpleTimer.addPeriodicEvent(
			"NetworkAdmin:checker",
			15000,
			new TimerEventPerformer()
			{
				public void 
				perform(
					TimerEvent event )
				{
					checkNetworkInterfaces( false );
				}
			});
		
			// populate initial values
		
		checkNetworkInterfaces(true);
		
		checkDefaultBindAddress(true);
	}
	
	protected void
	checkNetworkInterfaces(
		boolean	first_time )
	{
		try{
			Enumeration 	nis = NetworkInterface.getNetworkInterfaces();
		
			boolean	changed	= false;

			if ( nis == null && old_network_interfaces == null ){
				
			}else if ( nis == null ){
				
				old_network_interfaces	= null;
					
				changed = true;
					
			}else if ( old_network_interfaces == null ){
				
				Set	new_network_interfaces = new HashSet();
				
				while( nis.hasMoreElements()){

					new_network_interfaces.add( nis.nextElement());
				}
				
				old_network_interfaces = new_network_interfaces;
				
				changed = true;
				
			}else{
				
				Set	new_network_interfaces = new HashSet();
				
				while( nis.hasMoreElements()){
					
					Object	 ni = nis.nextElement();
					
						// NetworkInterface's "equals" method is based on ni name + addresses
					
					if ( !old_network_interfaces.contains( ni )){
						
						changed	= true;
					}
					
					new_network_interfaces.add( ni );
				}
					
				if ( old_network_interfaces.size() != new_network_interfaces.size()){
					
					changed = true;
				}
				
				old_network_interfaces = new_network_interfaces;
			}
			
			if ( changed ){
					
				if ( !first_time ){
					
					Logger.log(
						new LogEvent(LOGID,
								"NetworkAdmin: network interfaces have changed" ));
				}
				
				firePropertyChange( NetworkAdmin.PR_NETWORK_INTERFACES );
				
				checkDefaultBindAddress( first_time );
			}
		}catch( Throwable e ){
		}
	}
	
	public InetAddress
	getDefaultBindAddress()
	{
		return( old_bind_ip );
	}
	
	protected void
	checkDefaultBindAddress(
		boolean	first_time )
	{
		boolean	changed = false;
		
		String bind_ip = COConfigurationManager.getStringParameter("Bind IP", "").trim();

		try{
	
			if ( bind_ip.length() == 0 & old_bind_ip == null ){
				
			}else if ( bind_ip.length() == 0 ){
				
				old_bind_ip = null;
				
				changed = true;
				
			}else{
			
				InetAddress new_bind_ip	= null;
				
				if ( bind_ip.indexOf('.') == -1 ){
				
						// no dots -> interface name (e.g. eth0 )
					
					Enumeration 	nis = NetworkInterface.getNetworkInterfaces();

					while( nis.hasMoreElements()){
						
						NetworkInterface	 ni = (NetworkInterface)nis.nextElement();

						if ( bind_ip.equalsIgnoreCase( ni.getName())){
							
							Enumeration addresses = ni.getInetAddresses();
							
							if ( addresses.hasMoreElements()){
								
								new_bind_ip = (InetAddress)addresses.nextElement();
							}
						}
					}
					
					if ( new_bind_ip == null ){
						
						Logger.log(
								new LogAlert(LogAlert.UNREPEATABLE,
									LogAlert.AT_ERROR, "Bind IP '" + bind_ip + "' is invalid - no matching network interfaces" ));

						return;
					}
				}else{
				
					new_bind_ip = InetAddress.getByName( bind_ip );
				}
				
				if ( old_bind_ip == null || !old_bind_ip.equals( new_bind_ip )){
					
					old_bind_ip = new_bind_ip;
					
					changed = true;
				}
			}
			
			if ( changed ){
				
				if ( !first_time ){
					
					Logger.log(
						new LogEvent(LOGID,
								"NetworkAdmin: default bind ip has changed to '" + (old_bind_ip==null?"none":old_bind_ip.getHostAddress())  + "'"));
				}
				
				firePropertyChange( NetworkAdmin.PR_DEFAULT_BIND_ADDRESS );
			}
			
		}catch( Throwable e ){
			
			Logger.log(
				new LogAlert(LogAlert.UNREPEATABLE,
					LogAlert.AT_ERROR, "Bind IP '" + bind_ip + "' is invalid" ));
			
		}
	}
	
	public String
	getNetworkInterfacesAsString()
	{
		Set	interfaces = old_network_interfaces;
		
		if ( interfaces == null ){
			
			return( "" );
		}
		
		Iterator	it = interfaces.iterator();
		
		String	str = "";
		
		while( it.hasNext()){
			
			NetworkInterface ni = (NetworkInterface)it.next();
			
			str += (str.length()==0?"":",") + ni.getName() + "=";
			
			Enumeration addresses = ni.getInetAddresses();
		
			int	add_num = 0;
			
			while( addresses.hasMoreElements()){
				
				add_num++;
				
				InetAddress	ia = (InetAddress)addresses.nextElement();
				
				str += (add_num==1?"":";") + ia.getHostAddress();
			}
		}
		
		return( str );
	}
	
	protected void
	firePropertyChange(
		String	property )
	{
		Iterator it = listeners.iterator();
		
		while( it.hasNext()){
			
			((NetworkAdminPropertyChangeListener)it.next()).propertyChanged( property );
		}
	}
	
	public void
	addPropertyChangeListener(
		NetworkAdminPropertyChangeListener	listener )
	{
		listeners.add( listener );
	}
	
	public void
	removePropertyChangeListener(
		NetworkAdminPropertyChangeListener	listener )
	{
		listeners.remove( listener );
	}
}
