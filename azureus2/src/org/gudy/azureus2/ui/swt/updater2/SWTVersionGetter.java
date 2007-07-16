/*
 * Created on 20 mai 2004
 * Created by Olivier Chalouhi
 * 
 * Copyright (C) 2004, 2005, 2006 Aelitis SAS, All rights Reserved
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 * AELITIS, SAS au capital de 46,603.30 euros,
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 */
package org.gudy.azureus2.ui.swt.updater2;

import java.util.Map;

import org.eclipse.swt.SWT;

import org.gudy.azureus2.core3.logging.*;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.platform.PlatformManager;
import org.gudy.azureus2.platform.PlatformManagerCapabilities;
import org.gudy.azureus2.platform.PlatformManagerFactory;

import com.aelitis.azureus.core.versioncheck.VersionCheckClient;

import org.gudy.azureus2.plugins.update.UpdateChecker;


/**
 * @author Olivier Chalouhi
 *
 */
public class SWTVersionGetter {
	private static final LogIDs LOGID = LogIDs.GUI;

  private String platform; 
  private int currentVersion;
  private int latestVersion;
  private UpdateChecker	checker;
  
  private String[] mirrors;

	private String infoURL;
  
  public 
  SWTVersionGetter(
  		UpdateChecker	_checker ) 
  {
    this.platform 		= SWT.getPlatform();
    this.currentVersion = SWT.getVersion();
    

    /**
     * Hack to make users re-download swt package which is hacked to include
     * our osx platform jnilib. 
     */
    if (currentVersion == 3232 && Constants.isOSX) {
			PlatformManager p_man = PlatformManagerFactory.getPlatformManager();
			if (p_man != null
					&& !p_man.hasCapability(PlatformManagerCapabilities.GetVersion)) {
				currentVersion = 3231;
			}
		}
    
    /* hack no longer needed as most (all?) CVS users will have rolled back by now and
     * we're shipping with 3.1.1
     
    if ( currentVersion == 3206 ){
    	
    		// problem here with 3.2M2 that we rolled out to CVS users - it doesn't work
    		// on windows 98 (hangs the app). We therefore decided to fall back to 3.1.1
    		// which does work. However, to rollback the CVS users we need to make it appear
    		// that 3206 is < 3.1.1. We do this by hacking the version here
    	
    	System.out.println( "Rolling back SWT version 3.2M2 to 3.1.1" );
    	
    	currentVersion = 3138;	// 3.1.1 is 3139
    }
    */
    
    this.latestVersion = 0;
    checker	= _checker;
  }
  
  public boolean needsUpdate() {
    try {
      downloadLatestVersion();

      String msg = "SWT: current version = " + currentVersion + ", latest version = " + latestVersion;
      
      checker.reportProgress( msg );
      
      if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, msg));
      
      return latestVersion > currentVersion;
    } catch(Exception e) {
      e.printStackTrace();
      return false;
    }        
  }
  
  private void downloadLatestVersion() {
  	if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, "Requesting latest SWT version "
					+ "and url from version check client."));
    
    Map reply = VersionCheckClient.getSingleton().getVersionCheckInfo(VersionCheckClient.REASON_CHECK_SWT);
    
    String msg = "SWT version check received:";
    
    byte[] version_bytes = (byte[])reply.get( "swt_version" );
    if( version_bytes != null ) {
      latestVersion = Integer.parseInt( new String( version_bytes ) );
      msg += " version=" + latestVersion;
    }
    
    byte[] url_bytes = (byte[])reply.get( "swt_url" );
    if( url_bytes != null ) {
      mirrors = new String[] { new String( url_bytes ) };
      msg += " url=" + mirrors[0];
    }
    
    byte[] info_bytes = (byte[])reply.get( "swt_info_url" );
    if( info_bytes != null ) {
    	try {
    		infoURL = new String( info_bytes );
    	} catch (Exception e) {
    		Logger.log(new LogEvent(LOGID, "swt info_url", e));
    	}
    }

    if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, msg));
  }
  

  
  
  /**
   * @return Returns the latestVersion.
   */
  public int getLatestVersion() {
    return latestVersion;
  }
  
  public int getCurrentVersion() {
	    return currentVersion;
  }
  /**
   * @return Returns the platform.
   */
  public String getPlatform() {
    return platform;
  }
  
  /**
   * @return Returns the mirrors.
   */
  public String[] getMirrors() {
    return mirrors;
  }

	public String getInfoURL() {
		return infoURL;
	}
}
