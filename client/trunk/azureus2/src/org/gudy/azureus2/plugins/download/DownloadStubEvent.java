/*
 * Created on Jul 8, 2013
 * Created by Paul Gardner
 * 
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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
 */


package org.gudy.azureus2.plugins.download;

import java.util.List;

public interface 
DownloadStubEvent 
{
	public static final int DSE_STUB_ADDED				= 1;
	public static final int DSE_STUB_REMOVED			= 2;
	public static final int DSE_STUB_WILL_BE_ADDED		= 3;
	public static final int DSE_STUB_WILL_BE_REMOVED	= 4;

	public int
	getEventType();
	
	public List<DownloadStub>
	getDownloadStubs();
}
