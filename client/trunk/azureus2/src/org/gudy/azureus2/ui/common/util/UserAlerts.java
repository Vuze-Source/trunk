/*
 * Created on 28.11.2003
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package org.gudy.azureus2.ui.common.util;

import java.applet.Applet;
import java.applet.AudioClip;

import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.download.DownloadManagerListener;
import org.gudy.azureus2.core3.global.GlobalManager;
import org.gudy.azureus2.core3.global.GlobalManagerListener;

import org.gudy.azureus2.core3.config.COConfigurationManager;

/**
 * Contains methods to alert the user of certain events.
 * @author Rene Leonhardt
 */

public class 
UserAlerts 
{
  	private AudioClip audio_clip = null;

	public 
	UserAlerts(
		GlobalManager	global_manager ) 
 	{	
		final DownloadManagerListener download_manager_listener = new
			DownloadManagerListener()
			{
				public void
				stateChanged(
					int		state )
				{
				}
		
				public void
				downloadComplete()
				{
					downloadFinished();
				}
			}; 
			
    	global_manager.addListener(
    		new GlobalManagerListener()
    		{
				public void downloadManagerAdded(DownloadManager manager) 
				{
					manager.addListener( download_manager_listener );
				}

				public void downloadManagerRemoved(DownloadManager removed) 
				{
					removed.removeListener(download_manager_listener);
				}   		
			}); 			
     }

  	protected synchronized void
  	downloadFinished()
  	{
  		try{
  		
	    	if( COConfigurationManager.getBooleanParameter("Play Download Finished", true)){
	    
	    		if ( audio_clip == null ){
	    		
					audio_clip = Applet.newAudioClip(ClassLoader.getSystemResource("org/gudy/azureus2/ui/icons/downloadFinished.wav"));
	    		}
	    		
	    		if ( audio_clip != null ){
	    		    			
					audio_clip.play();
	    		}
	    	}
  		}catch( Throwable e ){
  			
  			e.printStackTrace();
  		}
  	}
}