/*
 * Created on 8 juil. 2003
 *
 */
package org.gudy.azureus2.ui.swt;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.StringTokenizer;

/**
 * @author Olivier
 * 
 */
public class StartServer extends Thread {

  private ServerSocket socket;
  private int state;
  private Main main;

  private boolean bContinue;
  public static final int STATE_FAULTY = 0;
  public static final int STATE_LISTENING = 1;

  public StartServer(Main main) {
    try {
      this.main = main;
      socket = new ServerSocket(6880);
      state = STATE_LISTENING;
    }
    catch (Exception e) {
      state = STATE_FAULTY;
    }
  }

  public void run() {
    bContinue = true;
    while (bContinue) {
      try {
        Socket sck = socket.accept();
        String address = sck.getInetAddress().getHostAddress();
        if (address.equals("localhost") || address.equals("127.0.0.1")) {
          BufferedReader br = new BufferedReader(new InputStreamReader(sck.getInputStream()));
          String line = br.readLine();
          //System.out.println("received : " + line);
          if (line != null) {
            StringTokenizer st = new StringTokenizer(line, ";");
            String args[] = new String[st.countTokens()];
            int i = 0;
            while (st.hasMoreElements()) {
              args[i++] = st.nextToken().replaceAll("&;", ";").replaceAll("&&", "&");
            }
            main.useParam(args);
          }
        }
        sck.close();

      }
      catch (Exception e) {
        //e.printStackTrace();
        bContinue = false;
      }
    }
  }

  public void stopIt() {
    bContinue = false;
    try {
      socket.close();
    }
    catch (Exception e) {}
  }
  /**
   * @return
   */
  public int getState() {
    return state;
  }

}
