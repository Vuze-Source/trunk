/*
 * Created on Mar 20, 2013
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


package com.aelitis.azureus.core.tag;

public interface 
TagFeatureRateLimit 
	extends TagFeature
{
	public boolean
	supportsTagRates();
	
	public boolean
	supportsTagUploadLimit();
	
	public boolean
	supportsTagDownloadLimit();
	
	public int
	getTagUploadLimit();
	
	public void
	setTagUploadLimit(
		int		bps );
	
	public int
	getTagCurrentUploadRate();
	
	public int
	getTagDownloadLimit();
	
	public void
	setTagDownloadLimit(
		int		bps );
	
	public int
	getTagCurrentDownloadRate();
	
	public long[]
	getTagSessionUploadTotal();
	
	public void
	resetTagSessionUploadTotal();
	
	public long[]
	getTagSessionDownloadTotal();

	public void
	resetTagSessionDownloadTotal();
	
	public long[]
	getTagUploadTotal();
	
	public long[]
	getTagDownloadTotal();
	
	public void
	setRecentHistoryRetention(
		boolean	enable );
	
	public int[][]
	getRecentHistory();
	
	public int
	getTagUploadPriority();
	
	public void
	setTagUploadPriority(
		int		priority );
	
	public int
	getTagMinShareRatio();
	
	public void
	setTagMinShareRatio(
		int		ratio_in_thousandths );
	
	public int
	getTagMaxShareRatio();
	
	public void
	setTagMaxShareRatio(
		int		ratio_in_thousandths );
	
	public int
	getTagAggregateShareRatio();
}
