/*
 * Written and copyright 2001-2003 Tobias Minich. Distributed under the GNU
 * General Public License; see the README file. This code comes with NO
 * WARRANTY.
 * 
 * XML.java
 * 
 * Created on 22.03.2004
 *
 */
package org.gudy.azureus2.ui.console.commands;

import java.io.FileOutputStream;
import java.util.List;
import org.gudy.azureus2.core3.stats.StatsWriterFactory;
import org.gudy.azureus2.core3.stats.StatsWriterStreamer;
import org.gudy.azureus2.ui.console.ConsoleInput;

/**
 * @author tobi
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class XML implements IConsoleCommand {
	
	public static void command(ConsoleInput ci, List args) {
		StatsWriterStreamer sws = StatsWriterFactory.createStreamer(ci.gm);
		String file = null;
		if ((args != null) && (!args.isEmpty()))
				file = (String) args.get(0);
		if (file == null) {
			try {
				ci.out.println("> -----");
				sws.write(ci.out);
				ci.out.println("> -----");
			} catch (Exception e) {
				ci.out.println("> Exception while trying to output xml stats:" + e.getMessage());
			}
		} else {
			try {
				FileOutputStream os = new FileOutputStream(file);

				try {

					sws.write(os);

				} finally {

					os.close();
				}
				ci.out.println("> XML stats successfully written to " + file);
			} catch (Exception e) {
				ci.out.println("> Exception while trying to write xml stats:" + e.getMessage());
			}
		}
	}

	public static void RegisterCommands() {
		try {
			ConsoleInput.commands.put("xml", XML.class.getMethod("command", ConsoleCommandParameters));
			ConsoleInput.helplines.add("xml [<file>]\t\t\t\tOutput stats in xml format (to <file> if given)");
		} catch (Exception e) {e.printStackTrace();}
	}

}
