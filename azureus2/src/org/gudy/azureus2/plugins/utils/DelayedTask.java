/*
 * Created on Apr 24, 2008
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


package org.gudy.azureus2.plugins.utils;

public interface 
DelayedTask 
{
	public void
	setTask(
		Runnable		target );
	
		/**
		 * Queue the task for execution. The task MUST have been set prior to this. If successful
		 * this will result in the runnable target being invoked when all prior delayed tasks
		 * have completed
		 */
	
	public void
	queue();
	
		/**
		 * This method MUST be called at come point for all tasks that have been queued otherwise
		 * subsequent tasks will be blocked indefinitely. 
		 * Typically this will be called from the 'target' runnable above 
		 */
	public void
	setComplete();
}
