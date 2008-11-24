/*
 * Created on Nov 20, 2008
 * Created by Paul Gardner
 * 
 * Copyright 2008 Vuze, Inc.  All rights reserved.
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


package com.aelitis.azureus.core.cnetwork;

import java.net.URL;

import com.aelitis.azureus.core.vuzefile.VuzeFile;

public interface 
ContentNetwork 
{
	public static final long	CONTENT_NETWORK_VUZE		= 1;
	public static final long	CONTENT_NETWORK_RFN			= 2;

	public static final int		SERVICE_SEARCH				= 1;	// String - query text
	public static final int		SERVICE_XSEARCH				= 2;	// String - query text; Boolean - toSubscribe
	
		/**
		 * Returns one of the above CONTENT_NETWORK constants
		 * @return
		 */
	
	public long
	getID();
	
		/**
		 * Test if the network supports a particular service
		 * @param service_type
		 * @return
		 */
	
	public boolean
	isServiceSupported(
		int			service_type );
	
		/**
		 * Returns the base URL of the service. If not parameterised then this is sufficient to
		 * invoke the service
		 * @param service_type
		 * @return
		 */
	
	public URL
	getServiceURL(
		int			service_type );
	
		/**
		 * Generic parameterised service method
		 * @param service_type
		 * @param params
		 * @return
		 */
	
	public URL
	getServiceURL(
		int			service_type,
		Object[]	params );
	
		/**
		 * search service helper method
		 * @param query
		 * @return
		 */
	
	public URL
	getSearchService(
		String		query );
	
	
	public URL
	getXSearchService(
		String		query,
		boolean		to_subscribe );
	
		/**
		 * export to vuze file
		 * @return
		 */
	
	public VuzeFile
	getVuzeFile();
}
