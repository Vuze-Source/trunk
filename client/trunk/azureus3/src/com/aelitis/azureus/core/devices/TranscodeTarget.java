/*
 * Created on Feb 4, 2009
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


package com.aelitis.azureus.core.devices;

import java.io.File;

public interface 
TranscodeTarget 
{
	public Device
	getDevice();
	
	public File
	getWorkingDirectory();
	
	public void
	setWorkingDirectory(
		File		directory );
	
	public TranscodeProfile[]
	getTranscodeProfiles();
	
	public void
	setTranscodeProfiles(
		TranscodeProfile[]	profiles );
	
	public TranscodeProfile
	getDefaultTranscodeProfile();
	
	public void
	setDefaultTranscodeProfile(
		TranscodeProfile		profile );
	
}
