/*
 * Created on Jul 21, 2006 3:19:03 PM
 * Copyright (C) 2006 Aelitis, All Rights Reserved.
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
 */
package org.gudy.azureus2.platform.macosx.access.jnilib;

import java.io.File;
import java.util.Map;

import org.eclipse.swt.internal.carbon.AEDesc;

import org.gudy.azureus2.core3.util.*;

import com.aelitis.azureus.core.drivedetector.DriveDetectorFactory;
import com.aelitis.azureus.util.MapUtils;

/**
 * @author TuxPaper
 * @created Jul 21, 2006
 *
 * javah -d . -classpath ../../../../../../../../bin org.gudy.azureus2.platform.macosx.access.jnilib.OSXAccess
 */
public class OSXAccess
{
	private static boolean bLoaded = false;

	private static boolean DEBUG = Constants.isCVSVersion();

	static {
		try {
			System.loadLibrary("OSXAccess");
			System.out.println("OSXAccess v" + getVersion() + " Load complete!");
			bLoaded = true;
			initDriveDetection();
		} catch (UnsatisfiedLinkError e1) {
			Debug.out("Could not find libOSXAccess.jnilib");
		}
	}

	private static void initDriveDetection() {
		try {
			initializeDriveDetection(new OSXDriveDetectListener() {
				public void driveRemoved(File mount, Map driveInfo) {
					if (DEBUG) {
						System.out.println("UNMounted " + mount);
						for (Object key : driveInfo.keySet()) {
							Object val = driveInfo.get(key);
							System.out.println("\t" + key + "\t:\t" + val);
						}
					}
					DriveDetectorFactory.getDeviceDetector().driveRemoved(mount);
				}

				public void driveDetected(File mount, Map driveInfo) {
					if (DEBUG) {
						System.out.println("Mounted " + mount);
						for (Object key : driveInfo.keySet()) {
							Object val = driveInfo.get(key);
							System.out.println("\t" + key + "\t:\t" + val);
						}
					}

					boolean isOptical = MapUtils.getMapLong(driveInfo, "isOptical", 0) != 0;
					boolean isRemovable = MapUtils.getMapLong(driveInfo, "Removable", 0) != 0;
					if (isRemovable && !isOptical) {
						DriveDetectorFactory.getDeviceDetector().driveDetected(mount);
					}
				}
			});
		} catch (Throwable t) {
		}
	}

	public static final native int AEGetParamDesc(int theAppleEvent,
			int theAEKeyword, int desiredType, AEDesc result);

	public static final native String getVersion();

	// 1.02
	public static final native String getDocDir();

	// 1.03
	public static final native void memmove(byte[] dest, int src, int size);

	// 1.04
	public static final native void initializeDriveDetection(
			OSXDriveDetectListener d);

	public static boolean isLoaded() {
		return bLoaded;
	}
}
