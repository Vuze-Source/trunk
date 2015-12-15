/*
 * Created on Dec 14, 2015
 * Created by Paul Gardner
 * 
 * Copyright 2015 Azureus Software, Inc.  All rights reserved.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or 
 * (at your option) any later version.
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


package org.gudy.azureus2.plugins.torrent;

import java.util.List;

import org.gudy.azureus2.plugins.tag.Tag;

public interface 
TorrentOptions 
{
	public Torrent
	getTorrent();
	
	public List<Tag>
	getTags();
	
	public void
	addTag(
		Tag		tag );
	
	public void
	removeTag(
		Tag		tag );
	
	public void
	accept();
	
	public void
	cancel();
}
