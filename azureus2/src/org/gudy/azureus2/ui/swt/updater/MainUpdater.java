/*
 * File    : DownloadAll.java
 * Created : 3 avr. 2004
 * By      : Olivier
 * 
 * Azureus - a Java Bittorrent client
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
 */
package org.gudy.azureus2.ui.swt.updater;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.swt.SWT;
import org.gudy.azureus2.core3.logging.LGLogger;

/**
 * @author Olivier Chalouhi
 *
 */
public class MainUpdater implements SWTDownloadURLsListener,SWTZipDownloadListener {

  String platform;
  GeneralListener listener;
  
  SWTDownloadURLsGetter urlGetter;
  SWTZipDownloader zipDownloader;
  
  public MainUpdater(GeneralListener listener){
    this.listener = listener;
    this.platform = SWT.getPlatform();    
    urlGetter = new SWTDownloadURLsGetter(this);
  }
  
  public void percentDone(int percent) {
    listener.percentDone(percent);
  }
  

  public void processName(String name) {
    listener.processName(name);
  }
  
  public void reportURLs(String[] urls) {
    if(urls != null) {
      zipDownloader = new SWTZipDownloader(this,urls);
    } else {
      listener.processFailed();
    }
  }

  public void reportData(InputStream inputStream) {
    if(inputStream != null) {      
      try {
        File f = new File("swtTemp.zip");
        if(f.exists()) f.delete();
        FileOutputStream fos = new FileOutputStream(f);
        byte[] buffer = new byte[4096];
        int read = 0;
        while((read = inputStream.read(buffer)) > 0) {
          fos.write(buffer,0,read);
        }
        fos.close();
        listener.processSucceeded();
      } catch(IOException e) {
        listener.processFailed();
      }
    } else {
      listener.processFailed();
    }
  }
  
  public void launchSWTUpdate() {
    try {
      String classPath = System.getProperty("java.class.path"); //$NON-NLS-1$
      String libraryPath = System.getProperty("java.library.path"); //$NON-NLS-1$
      String userPath = System.getProperty("user.dir"); //$NON-NLS-1$
      String javaPath = System.getProperty("java.home")
                      + System.getProperty("file.separator")
                      + "bin"
                      + System.getProperty("file.separator");
      
      /*String exec = "\"" + javaPath + "java\" -classpath \"" + classPath
      + "\" -Duser.dir=\"" + userPath + "\" org.gudy.azureus2.ui.swt.updater.UpdateSWT \"" + platform + "\" \"swtTemp.zip\" \""
      + userPath + "\" \"" + libraryPath + "\"";*/
      
      String exec[] = {
          javaPath + "java" ,
          "-classpath",
          classPath,
          "-Duser.dir=\"" + userPath + "\"",
          "org.gudy.azureus2.ui.swt.updater.UpdateSWT",
          platform,
          "swtTemp.zip",
          userPath,
          libraryPath         
      };
      
      //System.out.println(exec);
      String execLog = "";
      for(int i = 0 ; i < exec.length ; i++) {
        execLog += exec[i] + "\n";
      }
      LGLogger.log("SWT Updater is about to execute : " + execLog);
      
      File f = new File("updateSWT.log");
      FileOutputStream fosLog = new FileOutputStream(f,true);
      fosLog.write(("SWT Updater is about to execute : " + execLog + "\n").getBytes());         
      fosLog.close();
      
      Runtime.getRuntime().exec(exec);
    } catch(Exception e) {
      e.printStackTrace();
    }
  }
  
  public void cancel() {
    if(urlGetter != null) urlGetter.cancel();
    if(zipDownloader != null) zipDownloader.cancel();
  }
}
