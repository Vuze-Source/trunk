/*
 * Created on May 8, 2004
 * Created by Alon Rohter
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

package com.aelitis.azureus.core.networkmanager;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Represents a peer Transport connection (eg. a network socket).
 */
public interface 
Transport 
	extends TransportBase
{
	
  public static final int TRANSPORT_MODE_NORMAL = 0;
  public static final int TRANSPORT_MODE_FAST   = 1;
  public static final int TRANSPORT_MODE_TURBO  = 2;

  public int
  getMssSize();
  
  /**
   * Inject the given already-read data back into the read stream.
   * @param bytes_already_read data
   */
  public void setAlreadyRead( ByteBuffer bytes_already_read );
  
  
  /**
   * Get the socket channel used by the transport.
   * @return the socket channel
   */
  public TransportEndpoint getTransportEndpoint();  
  
  public boolean isEncrypted();
  
  /**
   * Return a textual description of the encryption for this transport
   * @return
   */
  public String getEncryption();
      
  /**
   * fake a wakeup so that a read cycle is attempted
   */
  
  public void setReadyForRead();
  
  /**
   * Write data to the transport from the given buffers.
   * NOTE: Works like GatheringByteChannel.
   * @param buffers from which bytes are to be retrieved
   * @param array_offset offset within the buffer array of the first buffer from which bytes are to be retrieved
   * @param length maximum number of buffers to be accessed
   * @return number of bytes written
   * @throws IOException on write error
   */
  public long write( ByteBuffer[] buffers, int array_offset, int length ) throws IOException;

  
  
  /**
   * Read data from the transport into the given buffers.
   * NOTE: Works like ScatteringByteChannel.
   * @param buffers into which bytes are to be placed
   * @param array_offset offset within the buffer array of the first buffer into which bytes are to be placed
   * @param length maximum number of buffers to be accessed
   * @return number of bytes read
   * @throws IOException on read error
   */
  
  public long read( ByteBuffer[] buffers, int array_offset, int length ) throws IOException;
  
  /**
   * Set the transport to the given speed mode.
   * @param mode to change to
   */
  public void setTransportMode( int mode );
 
  /**
   * Get the transport's speed mode.
   * @return current mode
   */
  public int getTransportMode();
  
  
  /**
   * Kick off an outbound connection
   * @param listener
   */
  public void
  connectOutbound(
		ByteBuffer			initial_data,
		ConnectListener 	listener,
		boolean				high_priority );
   
  /**
   * Indicate that inbound connection is complete 
   */
  
  public void
  connectedInbound();
  
  /**
   * Close the transport connection.
   */
  public void close( String reason );
  
  public void
  setTrace(
		boolean	on );
  
  /**
   * Listener for notification of connection establishment.
   */
  public interface ConnectListener {
    /**
     * The connection establishment process has started,
     * i.e. the connection is actively being attempted.
     */
    public void connectAttemptStarted();   
     
    /**
     * The connection attempt succeeded.
     * The connection is now established.
     */
    public void connectSuccess(Transport	transport, ByteBuffer remaining_initial_data );
    
    /**
     * The connection attempt failed.
     * @param failure_msg failure reason
     */
    public void connectFailure( Throwable failure_msg );
  }
}
