/*
 * Created on 13-Mar-2004
 * Created by James Yeh
 * Copyright (C) 2004, 2005, 2006 Aelitis, All Rights Reserved.
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
 * 
 * AELITIS, SAS au capital de 46,603.30 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package org.gudy.azureus2.platform.macosx;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.text.MessageFormat;
import java.util.HashSet;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.logging.LogAlert;
import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.util.AEDiagnostics;
import org.gudy.azureus2.core3.util.AEDiagnosticsEvidenceGenerator;
import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.IndentWriter;
import org.gudy.azureus2.core3.util.SystemProperties;
import org.gudy.azureus2.platform.PlatformManager;
import org.gudy.azureus2.platform.PlatformManagerCapabilities;
import org.gudy.azureus2.platform.PlatformManagerListener;
import org.gudy.azureus2.platform.PlatformManagerPingCallback;
import org.gudy.azureus2.platform.macosx.access.jnilib.OSXAccess;
import org.gudy.azureus2.plugins.platform.PlatformManagerException;

import com.apple.cocoa.application.NSApplication;


/**
 * Performs platform-specific operations with Mac OS X
 *
 * @author James Yeh
 * @version 1.0 Initial Version
 * @see PlatformManager
 */
public class PlatformManagerImpl implements PlatformManager, AEDiagnosticsEvidenceGenerator
{
    private static final LogIDs LOGID = LogIDs.CORE;

    protected static PlatformManagerImpl singleton;
    protected static AEMonitor class_mon = new AEMonitor("PlatformManager");
    
    private static String fileBrowserName = "Finder";

    //T: PlatformManagerCapabilities
    private final HashSet capabilitySet = new HashSet();

    private volatile String		computer_name;
    private volatile boolean	computer_name_tried;
    
    /**
     * Gets the platform manager singleton, which was already initialized
     */
    public static PlatformManagerImpl getSingleton()
    {
        return singleton;
    }

    /**
     * Tries to enable cocoa-java access and instantiates the singleton
     */
    static
    {
        initializeSingleton();
    }

    /**
     * Instantiates the singleton
     */
    private static void initializeSingleton()
    {
        try
        {
            class_mon.enter();
            singleton = new PlatformManagerImpl();
        }
        catch (Throwable e)
        {
        	Logger.log(new LogEvent(LOGID, "Failed to initialize platform manager"
					+ " for Mac OS X", e));
        }
        finally
        {
            class_mon.exit();
        }

        COConfigurationManager.addAndFireParameterListener("FileBrowse.usePathFinder", new ParameterListener() {
					public void parameterChanged(String parameterName) {
						fileBrowserName = COConfigurationManager.getBooleanParameter("FileBrowse.usePathFinder")
	        		? "Path Finder" : "Finder";
					}
				});
    }

    /**
     * Creates a new PlatformManager and initializes its capabilities
     */
    public PlatformManagerImpl()
    {
        capabilitySet.add(PlatformManagerCapabilities.RecoverableFileDelete);
        capabilitySet.add(PlatformManagerCapabilities.ShowFileInBrowser);
        capabilitySet.add(PlatformManagerCapabilities.ShowPathInCommandLine);
        capabilitySet.add(PlatformManagerCapabilities.CreateCommandLineProcess);
        capabilitySet.add(PlatformManagerCapabilities.GetUserDataDirectory);
        capabilitySet.add(PlatformManagerCapabilities.UseNativeScripting);
        capabilitySet.add(PlatformManagerCapabilities.PlaySystemAlert);
        capabilitySet.add(PlatformManagerCapabilities.RequestUserAttention);
        
        if (OSXAccess.isLoaded()) {
	        capabilitySet.add(PlatformManagerCapabilities.GetVersion);
        }
 
        AEDiagnostics.addEvidenceGenerator(this);
        
        if ( checkPList()){
        	
            capabilitySet.add(PlatformManagerCapabilities.AccessExplicitVMOptions);
        }
        
        capabilitySet.add(PlatformManagerCapabilities.RunAtLogin);
    }

    /**
     * {@inheritDoc}
     */
    public int getPlatformType()
    {
        return PT_MACOSX;
    }

    /**
     * {@inheritDoc}
     */
    public String getVersion() throws PlatformManagerException
    {
    	if (!OSXAccess.isLoaded()) {
        throw new PlatformManagerException("Unsupported capability called on platform manager");
    	}
    	
    	return OSXAccess.getVersion();
    }

    protected PListEditor
    getPList()
    
    	throws IOException
    {
		String	plist = 
			System.getProperty("user.dir") +
			SystemProperties.SEP+ SystemProperties.getApplicationName() + ".app/Contents/Info.plist";

		if (!new File(plist).exists()) {
			Debug.out("WARNING: plist not found: " + plist);
			return null;
		}
		PListEditor editor = new PListEditor( plist );
	
		return( editor );
    }
    
    protected boolean
    checkPList()
    {
    	try{
    		PListEditor editor = getPList();
    		
    		if (editor == null) {
    			
    			return( false );
    		}
    		
    		editor.setFileTypeExtensions(new String[] {"torrent","tor","vuze","vuz"});
    		editor.setSimpleStringValue("CFBundleName", "Vuze");
			editor.setSimpleStringValue("CFBundleTypeName", "Vuze Download");
			editor.setSimpleStringValue("CFBundleGetInfoString","Vuze");
			editor.setSimpleStringValue("CFBundleShortVersionString",Constants.AZUREUS_VERSION);
			editor.setSimpleStringValue("CFBundleVersion",Constants.AZUREUS_VERSION);
			editor.setArrayValues("CFBundleURLSchemes", "string", new String[] { "magnet", "dht", "vuze"});
			
				// always touch it, see if it helps ensure we are registered as magnet
				// handler
			
			editor.touchFile();
			
			return( true );
			
    	}catch( Throwable e ){
    		
    		Debug.out( "Failed to update plist", e );
    		
    		return( false );
    	}
    }
    
    protected void
    touchPList()
    {
       	try{
    		PListEditor editor = getPList();
  	
    		editor.touchFile();
    		
       	}catch( Throwable e ){
    		
    		Debug.out( "Failed to touch plist", e );
    	}
    }
    
	public String[]
   	getExplicitVMOptions()
  	          	
     	throws PlatformManagerException
  	{
        throw new PlatformManagerException("Unsupported capability called on platform manager");
  	}
  	 
  	public void
  	setExplicitVMOptions(
  		String[]		options )
  	          	
  		throws PlatformManagerException
  	{
        throw new PlatformManagerException("Unsupported capability called on platform manager");	
  	}
  	
  	public boolean 
  	getRunAtLogin() 
  	
  		throws PlatformManagerException 
  	{
  		return( false );
  	}
  	
  	public void 
  	setRunAtLogin(
  		boolean run ) 
  	
  		throws PlatformManagerException 
  	{
   	}
    
    private File 
    getLoginPList() 
    
    	throws PlatformManagerException
    {
    	return( new File(System.getProperty("user.home"), "/Library/Preferences/loginwindow.plist" )); 
    }
    
    /**
     * {@inheritDoc}
     * @see org.gudy.azureus2.core3.util.SystemProperties#getUserPath()
     */
    public String getUserDataDirectory() throws PlatformManagerException
    {
    	return new File(System.getProperty("user.home")
    			+ "/Library/Application Support/" 
    			+ SystemProperties.APPLICATION_NAME).getPath()
    			+ SystemProperties.SEP;
    }

	public String 
	getComputerName() 
	{
		if ( computer_name_tried ){
			
			return( computer_name );
		}
		
		try{
			String result = null;
			
			String	hostname = System.getenv( "HOSTNAME" );
			
			if ( hostname != null && hostname.length() > 0 ){
				
				result = hostname;
			}
			
			if ( result == null ){
				
				String	host = System.getenv( "HOST" );
				
				if ( host != null && host.length() > 0 ){
					
					result = host;
				}
			}
			
			if ( result == null ){
			
				try{				
					String[] to_run = new String[3];
					
				  	to_run[0] = "/bin/sh";
				  	to_run[1] = "-c";
				  	to_run[2] = "echo $HOSTNAME";
				  	
					Process p = Runtime.getRuntime().exec( to_run );
					
					if ( p.waitFor() == 0 ){
						
						String	output = "";
						
						InputStream is = p.getInputStream();
						
						while( true ){
							
							byte[] buffer = new byte[1024];
							
							int len = is.read( buffer );
							
							if ( len <= 0 ){
								
								break;
							}
							
							output += new String( buffer, 0, len );
							
							if ( output.length() > 64 ){
								
								break;
							}
						}
						
						if ( output.length() > 0 ){
							
							result = output.trim();
							
							int pos = result.indexOf(' ');
							
							if ( pos != -1 ){
								
								result = result.substring( 0, pos ).trim();
							}
						}
					}
				}catch( Throwable e ){
				}
			}
			
			if ( result != null ){
			
				int	pos = result.lastIndexOf( '.' );
				
				if ( pos != -1 ){
					
					result = result.substring( 0, pos );
				}
				
				if ( result.length() > 0 ){
					
					if ( result.length() > 32 ){
						
						result = result.substring( 0, 32 );
					}
					
					computer_name = result;
				}
			}
						
			return( computer_name );
			
		}finally{
			
			computer_name_tried = true;
		}
	}
	
	public File
	getLocation(
		long	location_id )
	
		throws PlatformManagerException
	{
		switch ((int)location_id) {
			case LOC_USER_DATA:
				return new File(getUserDataDirectory());
				
			case LOC_DOCUMENTS:
				try {
					return new File(OSXAccess.getDocDir());
				} catch (Throwable e) {
					// throws UnsatisfiedLinkError if no osxaccess
					// Sometimes throws NullPointerException

					// Usually in user.home + Documents
					return new File(System.getProperty("user.home"), "Documents");
				}
				
			case LOC_MUSIC:
				
			case LOC_VIDEO:

			default:
				return( null );
		}
		
	}
    /**
     * Not implemented; returns True
     */
    public boolean isApplicationRegistered() throws PlatformManagerException
    {
        return true;
    }

    
	public String
	getApplicationCommandLine()
		throws PlatformManagerException
	{
		try{	    
			String	bundle_path = System.getProperty("user.dir") +SystemProperties.SEP+ SystemProperties.getApplicationName() + ".app";

			File osx_app_bundle = new File( bundle_path ).getAbsoluteFile();
			
			if( !osx_app_bundle.exists() ) {
				String msg = "OSX app bundle not found: [" +osx_app_bundle.toString()+ "]";
				System.out.println( msg );
				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, msg));		
				throw new PlatformManagerException( msg );
			}
			
			return "open -a \"" +osx_app_bundle.toString()+ "\"";
			//return osx_app_bundle.toString() +"/Contents/MacOS/JavaApplicationStub";
			
		}
		catch( Throwable t ){	
			t.printStackTrace();
			return null;
		}
	}
	
	
	public boolean
	isAdditionalFileTypeRegistered(
		String		name,				// e.g. "BitTorrent"
		String		type )				// e.g. ".torrent"
	
		throws PlatformManagerException
	{
	    throw new PlatformManagerException("Unsupported capability called on platform manager");
	}
	
	public void
	unregisterAdditionalFileType(
		String		name,				// e.g. "BitTorrent"
		String		type )				// e.g. ".torrent"
		
		throws PlatformManagerException
	{
		throw new PlatformManagerException("Unsupported capability called on platform manager");
	}
	
	public void
	registerAdditionalFileType(
		String		name,				// e.g. "BitTorrent"
		String		description,		// e.g. "BitTorrent File"
		String		type,				// e.g. ".torrent"
		String		content_type )		// e.g. "application/x-bittorrent"
	
		throws PlatformManagerException
	{
	   throw new PlatformManagerException("Unsupported capability called on platform manager");
	}
	
    public void registerApplication() throws PlatformManagerException
    {
    	touchPList();
    }

    /**
     * {@inheritDoc}
     */
    public void createProcess(String cmd, boolean inheritsHandles) throws PlatformManagerException
    {
        try
        {
            performRuntimeExec(cmd.split(" "));
        }
        catch (Throwable e)
        {
            throw new PlatformManagerException("Failed to create process", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void performRecoverableFileDelete(String path) throws PlatformManagerException
    {
        File file = new File(path);
        if(!file.exists())
        {
	        	if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, "Cannot find "
									+ file.getName()));
            return;
        }

        boolean useOSA = !NativeInvocationBridge.sharedInstance().isEnabled() || !NativeInvocationBridge.sharedInstance().performRecoverableFileDelete(file);

        if(useOSA)
        {
            try
            {
                StringBuffer sb = new StringBuffer();
                sb.append("tell application \"");
                sb.append("Finder");
                sb.append("\" to move (posix file \"");
                sb.append(path);
                sb.append("\" as alias) to the trash");

                performOSAScript(sb);
            }
            catch (Throwable e)
            {
                throw new PlatformManagerException("Failed to move file", e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasCapability(PlatformManagerCapabilities capability)
    {
        return capabilitySet.contains(capability);
    }

    /**
     * {@inheritDoc}
     */
    public void dispose()
    {
    	try {
    		if (NativeInvocationBridge.hasSharedInstance()) {
    			NativeInvocationBridge.sharedInstance().dispose();
    		}
    	} catch (Throwable t) {
    		Debug.out("Problem disposing NativeInvocationBridge", t);
    	}
    }

    /**
     * {@inheritDoc}
     */
    public void setTCPTOSEnabled(boolean enabled) throws PlatformManagerException
    {
        throw new PlatformManagerException("Unsupported capability called on platform manager");
    }

	public void
    copyFilePermissions(
		String	from_file_name,
		String	to_file_name )
	
		throws PlatformManagerException
	{
	    throw new PlatformManagerException("Unsupported capability called on platform manager");		
	}
	
    /**
     * {@inheritDoc}
     */
    public void showFile(String path) throws PlatformManagerException
    {
        File file = new File(path);
        if(!file.exists())
        {
        	if (Logger.isEnabled())
        		Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, "Cannot find "
        				+ file.getName()));
            throw new PlatformManagerException("File not found");
        }

        showInFinder(file);
    }

    // Public utility methods not shared across the interface

    /**
     * Plays the system alert (the jingle is specified by the user in System Preferences)
     */
    public void playSystemAlert()
    {
        try
        {
            performRuntimeExec(new String[]{"beep"});
        }
        catch (IOException e)
        {
        	if (Logger.isEnabled())
        		Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
						"Cannot play system alert"));
        	Logger.log(new LogEvent(LOGID, "", e));
        }
    }

    /**
     * <p>Shows the given file or directory in Finder</p>
     * @param path Absolute path to the file or directory
     */
    public void showInFinder(File path)
    {
        boolean useOSA = !NativeInvocationBridge.sharedInstance().isEnabled() || !NativeInvocationBridge.sharedInstance().showInFinder(path,fileBrowserName);

        if(useOSA)
        {
            StringBuffer sb = new StringBuffer();
            sb.append("tell application \"");
            sb.append(getFileBrowserName());
            sb.append("\"\n");
            sb.append("reveal (posix file \"");
            sb.append(path);
            sb.append("\" as alias)\n");
            sb.append("activate\n");
            sb.append("end tell\n");

            try
            {
                performOSAScript(sb);
            }
            catch (IOException e)
            {
                Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_ERROR, e
						.getMessage()));
            }
        }
    }

    /**
     * <p>Shows the given file or directory in Terminal by executing cd /absolute/path/to</p>
     * @param path Absolute path to the file or directory
     */
    public void showInTerminal(String path)
    {
        showInTerminal(new File(path));
    }

    /**
     * <p>Shows the given file or directory in Terminal by executing cd /absolute/path/to</p>
     * @param path Absolute path to the file or directory
     */
    public void showInTerminal(File path)
    {
        if (path.isFile())
        {
            path = path.getParentFile();
        }

        if (path != null && path.isDirectory())
        {
            StringBuffer sb = new StringBuffer();
            sb.append("tell application \"");
            sb.append("Terminal");
            sb.append("\" to do script \"cd ");
            sb.append(path.getAbsolutePath().replaceAll(" ", "\\ "));
            sb.append("\"");

            try
            {
                performOSAScript(sb);
            }
            catch (IOException e)
            {
                Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_ERROR, e
						.getMessage()));
            }
        }
        else
        {
        	if (Logger.isEnabled())
        		Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, "Cannot find "
        				+ path.getName()));
        }
    }

    // Internal utility methods

    /**
     * Compiles a new AppleScript instance and runs it
     * @param cmd AppleScript command to execute; do not surround command with extra quotation marks
     * @return Output of the script
     * @throws IOException If the script failed to execute
     */
    protected static String performOSAScript(CharSequence cmd) throws IOException
    {
        return performOSAScript(new CharSequence[]{cmd});
    }

    /**
     * Compiles a new AppleScript instance and runs it
     * @param cmds AppleScript Sequence of commands to execute; do not surround command with extra quotation marks
     * @return Output of the script
     * @throws IOException If the script failed to execute
     */
    protected static String performOSAScript(CharSequence[] cmds) throws IOException
    {
        long start = System.currentTimeMillis();
        Debug.outNoStack("Executing OSAScript: ");
        for (int i = 0; i < cmds.length; i++)
        {
            Debug.outNoStack("\t" + cmds[i]);
        }

        String[] cmdargs = new String[2 * cmds.length + 1];
        cmdargs[0] = "osascript";
        for (int i = 0; i < cmds.length; i++)
        {
            cmdargs[i * 2 + 1] = "-e";
            cmdargs[i * 2 + 2] = String.valueOf(cmds[i]);
        }

        Process osaProcess = performRuntimeExec(cmdargs);
        BufferedReader reader = new BufferedReader(new InputStreamReader(osaProcess.getInputStream()));
        String line = reader.readLine();
        reader.close();
        Debug.outNoStack("OSAScript Output: " + line);

        reader = new BufferedReader(new InputStreamReader(osaProcess.getErrorStream()));
        String errorMsg = reader.readLine();
        reader.close();

        Debug.outNoStack("OSAScript Error (if any): " + errorMsg);

        Debug.outNoStack(MessageFormat.format("OSAScript execution ended ({0}ms)", new Object[]{String.valueOf(System.currentTimeMillis() - start)}));

        try {
        	osaProcess.destroy();
        } catch (Throwable t) {
        	//ignore
        }

        if (errorMsg != null)
        {
            throw new IOException(errorMsg);
        }

        return line;
    }

    /**
     * Compiles a new AppleScript instance and runs it
     * @param script AppleScript file (.scpt) to execute
     * @return Output of the script
     * @throws IOException If the script failed to execute
     */
    protected static String performOSAScript(File script) throws IOException
    {
        long start = System.currentTimeMillis();
        Debug.outNoStack("Executing OSAScript from file: " + script.getPath());

        Process osaProcess = performRuntimeExec(new String[]{"osascript", script.getPath()});
        BufferedReader reader = new BufferedReader(new InputStreamReader(osaProcess.getInputStream()));
        String line = reader.readLine();
        reader.close();
        Debug.outNoStack("OSAScript Output: " + line);

        reader = new BufferedReader(new InputStreamReader(osaProcess.getErrorStream()));
        String errorMsg = reader.readLine();
        reader.close();

        Debug.outNoStack("OSAScript Error (if any): " + errorMsg);

        Debug.outNoStack(MessageFormat.format("OSAScript execution ended ({0}ms)", new Object[]{String.valueOf(System.currentTimeMillis() - start)}));

        try {
        	osaProcess.destroy();
        } catch (Throwable t) {
        	//ignore
        }
        if (errorMsg != null)
        {
            throw new IOException(errorMsg);
        }

        return line;
    }

    /**
     * Compiles a new AppleScript instance to the specified location
     * @param cmd         Command to compile; do not surround command with extra quotation marks
     * @param destination Destination location of the AppleScript file
     * @return True if compiled successfully
     */
    protected static boolean compileOSAScript(CharSequence cmd, File destination)
    {
        return compileOSAScript(new CharSequence[]{cmd}, destination);
    }

    /**
     * Compiles a new AppleScript instance to the specified location
     * @param cmds Sequence of commands to compile; do not surround command with extra quotation marks
     * @param destination Destination location of the AppleScript file
     * @return True if compiled successfully
     */
    protected static boolean compileOSAScript(CharSequence[] cmds, File destination)
    {
        long start = System.currentTimeMillis();
        Debug.outNoStack("Compiling OSAScript: " + destination.getPath());
        for (int i = 0; i < cmds.length; i++)
        {
            Debug.outNoStack("\t" + cmds[i]);
        }

        String[] cmdargs = new String[2 * cmds.length + 3];
        cmdargs[0] = "osacompile";
        for (int i = 0; i < cmds.length; i++)
        {
            cmdargs[i * 2 + 1] = "-e";
            cmdargs[i * 2 + 2] = String.valueOf(cmds[i]);
        }

        cmdargs[cmdargs.length - 2] = "-o";
        cmdargs[cmdargs.length - 1] = destination.getPath();

        String errorMsg;
        try
        {
            Process osaProcess = performRuntimeExec(cmdargs);

            BufferedReader reader = new BufferedReader(new InputStreamReader(osaProcess.getErrorStream()));
            errorMsg = reader.readLine();
            reader.close();
        }
        catch (IOException e)
        {
            Debug.outNoStack("OSACompile Execution Failed: " + e.getMessage());
            Debug.printStackTrace(e);
            return false;
        }

        Debug.outNoStack("OSACompile Error (if any): " + errorMsg);

        Debug.outNoStack(MessageFormat.format("OSACompile execution ended ({0}ms)", new Object[]{String.valueOf(System.currentTimeMillis() - start)}));

        return (errorMsg == null);
    }

    /**
     * @see Runtime#exec(String[])
     */
    protected static Process performRuntimeExec(String[] cmdargs) throws IOException
    {
        try
        {
            return Runtime.getRuntime().exec(cmdargs);
        }
        catch (IOException e)
        {
            Logger.log(new LogAlert(LogAlert.UNREPEATABLE, e.getMessage(), e));
            throw e;
        }
    }

    /**
     * <p>Gets the preferred file browser name</p>
     * <p>Currently supported browsers are Path Finder and Finder. If Path Finder is currently running
     * (not just installed), then "Path Finder is returned; else, "Finder" is returned.</p>
     * @return "Path Finder" if it is currently running; else "Finder"
     */
    private static String getFileBrowserName()
    {
    	return fileBrowserName;
    }
    
	public boolean
	testNativeAvailability(
		String	name )
	
		throws PlatformManagerException
	{
	    throw new PlatformManagerException("Unsupported capability called on platform manager");		
	}
    
	public void
	traceRoute(
		InetAddress							interface_address,
		InetAddress							target,
		PlatformManagerPingCallback			callback )
	
		throws PlatformManagerException
	{
	    throw new PlatformManagerException("Unsupported capability called on platform manager");		
	}
	
	public void
	ping(
		InetAddress							interface_address,
		InetAddress							target,
		PlatformManagerPingCallback			callback )
	
		throws PlatformManagerException
	{
	    throw new PlatformManagerException("Unsupported capability called on platform manager");		
	}
	
    public void
    addListener(
    	PlatformManagerListener		listener )
    {
    }
    
    public void
    removeListener(
    	PlatformManagerListener		listener )
    {
    }

		// @see org.gudy.azureus2.core3.util.AEDiagnosticsEvidenceGenerator#generate(org.gudy.azureus2.core3.util.IndentWriter)
		public void generate(IndentWriter writer) {
			writer.println("PlatformManager: MacOSX");
			try {
				writer.indent();
				
				if (OSXAccess.isLoaded()) {
					try {
						writer.println("Version " + getVersion());
						writer.println("User Data Dir: " + getLocation(LOC_USER_DATA));
						writer.println("User Doc Dir: " + getLocation(LOC_DOCUMENTS));
					} catch (PlatformManagerException e) {
					}
				} else {
					writer.println("Not loaded");
				}
				
				writer.println("Computer Name: " + getComputerName());

			} finally {
				writer.exdent();
			}
		}

	// @see org.gudy.azureus2.platform.PlatformManager#getAzComputerID()
	public String getAzComputerID() throws PlatformManagerException {
		throw new PlatformManagerException(
				"Unsupported capability called on platform manager");
	}

	/**
	 * If the application is not active causes the application icon at the bottom to bounce until the application becomes active
	 * If the application is already active then this method does nothing.
	 * 
	 * Note: This is an undocumented feature from Apple so it's behavior may change without warning
	 * 
	 * @param type one of USER_REQUEST_INFO, USER_REQUEST_WARNING
	 */
	public void requestUserAttention(int type, Object data)
			throws PlatformManagerException {
		try {
			NSApplication app = NSApplication.sharedApplication();
			if (type == USER_REQUEST_INFO) {
				app.requestUserAttention(NSApplication.UserAttentionRequestInformational);
			} else if (type == USER_REQUEST_WARNING) {
				app.requestUserAttention(NSApplication.UserAttentionRequestCritical);
			} else if (type == USER_REQUEST_QUESTION) {
				// not applicable
			}

		} catch (Exception e) {
			throw new PlatformManagerException("Failed to request user attention", e);
		}

	}
}
