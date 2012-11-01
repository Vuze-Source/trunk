/*
 * Created on Jun 11, 2009
 * Created by Paul Gardner
 * 
 * Copyright 2009 Vuze, Inc.  All rights reserved.
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


package com.aelitis.azureus.core.util;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;


public class 
DNSUtils 
{
	private static String
	getFactory()
	{
		return( System.getProperty( "azureus.dns.context.factory", "com.sun.jndi.dns.DnsContextFactory" ));
	}
	
	public static DirContext
	getInitialDirContext()
	
		throws NamingException
	{
		Hashtable env = new Hashtable();
		
		env.put ( Context.INITIAL_CONTEXT_FACTORY, getFactory());
		
		return( new InitialDirContext( env ));

	}
	
	public static DirContext
	getDirContextForServer(
		String		dns_server_ip )
	
		throws NamingException
	{
		Hashtable env = new Hashtable();
		
		env.put( Context.INITIAL_CONTEXT_FACTORY, getFactory());
		
		env.put( Context.PROVIDER_URL, "dns://"+dns_server_ip+"/" );
		
		return( new InitialDirContext( env ));
	}
	
	public static Inet6Address
	getIPV6ByName(
		String		host )
	
		throws UnknownHostException
	{
		List<Inet6Address>	all = getAllIPV6ByName( host );
	
		return( all.get(0));
	}
	
	public static List<Inet6Address>
	getAllIPV6ByName(
		String		host )
		
		throws UnknownHostException
	{
		List<Inet6Address>	result = new ArrayList<Inet6Address>();

		try{			
			DirContext context = getInitialDirContext();
			
			Attributes attrs = context.getAttributes( host, new String[]{ "AAAA" });
			
			if ( attrs != null ){
			
				Attribute attr = attrs.get( "aaaa" );
			
				if ( attr != null ){
					
					NamingEnumeration values = attr.getAll();
			
					while( values.hasMore()){
					
						Object value = values.next();
						
						if ( value instanceof String ){
							
							try{
								result.add( (Inet6Address)InetAddress.getByName((String)value));
								
							}catch( Throwable e ){
							}
						}
					}
				}
			}
		}catch( Throwable e ){
		}
		
		if ( result.size() > 0 ){
		
			return( result );
		}
		
		throw( new UnknownHostException( host ));
	}
	
	public static List<InetAddress>
	getAllByName(
		String		host )
		
		throws UnknownHostException
	{
		try{
			return( getAllByName( getInitialDirContext(), host ));
			
		}catch( NamingException e ){
			
			throw( new UnknownHostException( host ));
		}
	}
	
	public static List<InetAddress>
	getAllByName(
		DirContext	context,
		String		host )
		
		throws UnknownHostException
	{
		List<InetAddress>	result = new ArrayList<InetAddress>();

		try{						
			String[] attributes = new String[]{ "A", "AAAA" };
			
			Attributes attrs = context.getAttributes( host, attributes );
			
			if ( attrs != null ){
			
				for( String a: attributes ){
					
					Attribute attr = attrs.get( a );
				
					if ( attr != null ){
						
						NamingEnumeration values = attr.getAll();
				
						while( values.hasMore()){
						
							Object value = values.next();
							
							if ( value instanceof String ){
								
								try{
									result.add( InetAddress.getByName((String)value));
									
								}catch( Throwable e ){
								}
							}
						}
					}
				}
			}
		}catch( Throwable e ){
		}
		
		if ( result.size() > 0 ){
		
			return( result );
		}
		
		throw( new UnknownHostException( host ));
	}
	
	private static Map<String,String>	test_records = new HashMap<String,String>();
	
	static{
		test_records.put( "test1.test.null", "BITTORRENT DENY ALL" );
		test_records.put( "test2.test.null", "BITTORRENT" );
		test_records.put( "test3.test.null", "BITTORRENT TCP:1 TCP:2 UDP:1 UDP:2" );
		test_records.put( "test4.test.null", "BITTORRENT TCP:3" );
		test_records.put( "test5.test.null", "BITTORRENT UDP:4" );
	}
	
	public static List<String>
	getTXTRecords(
		String		query )
	{
		// System.out.println( "DNSTXTQuery: " + query );
		
		List<String>	result = new ArrayList<String>();
	
		String test_reply = test_records.get( query );

		if ( test_reply != null ){
			
			result.add( test_reply );
			
			return( result );
		}
		
		DirContext context = null;
		
		try{
			context = DNSUtils.getInitialDirContext();
			
			Attributes attrs = context.getAttributes( query, new String[]{ "TXT" });
			
			NamingEnumeration n_enum = attrs.getAll();

			while( n_enum.hasMoreElements()){
				
				Attribute	attr =  (Attribute)n_enum.next();

				NamingEnumeration n_enum2 = attr.getAll();
				
				while( n_enum2.hasMoreElements()){
				
					String attribute = (String)n_enum2.nextElement();

					if ( attribute != null ){
						
						attribute = attribute.trim();
						
						if ( attribute.startsWith( "\"" )){
							
							attribute = attribute.substring(1);
						}
						
						if ( attribute.endsWith( "\"" )){
							
							attribute = attribute.substring(0,attribute.length()-1);
						}
						
						if ( attribute.length() > 0 ){
														
							result.add( attribute );
						}
					}
				}
			}
						
		}catch( Throwable e ){
				
			//e.printStackTrace();
			
		}finally{
			
			if ( context != null ){
				
				try{
					context.close();
					
				}catch( Throwable e ){
				}
			}
		}
		
		return( result );
	}
	
	public static void
	main(
		String[]	args )
	{
		try{
			//List<String> records = getTXTRecords( "tracker.openbittorrent.com" );
			/*			
			List<String> records = getTXTRecords( "www.ibm.com" );
			
			for ( String record: records ){
				
				System.out.println( record );
			}
			*/
			
			DirContext ctx = getDirContextForServer( "8.8.4.4" );

			System.out.println( getAllByName( ctx, "www.google.com" ));
			
		}catch( Throwable e ){
			
			e.printStackTrace();
		}
	}
}
