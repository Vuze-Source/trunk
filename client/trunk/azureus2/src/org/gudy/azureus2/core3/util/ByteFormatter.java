/*
 * Created on 2 juil. 2003
 * Copyright (C) 2003, 2004, 2005, 2006 Aelitis, All Rights Reserved.
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
package org.gudy.azureus2.core3.util;

/**
 * @author Olivier
 * 
 */

import java.nio.ByteBuffer;


public class ByteFormatter
{
	final static char[] HEXDIGITS = {
  	'0' , '1' , '2' , '3' , '4' , '5' ,
  	'6' , '7' , '8' , '9' , 'A' , 'B' ,
  	'C' , 'D' , 'E' , 'F' };

	public static String
  nicePrint(
  	String	str )
  {
  	return( nicePrint(str.getBytes(),true));
  }
  
  public static String nicePrint(byte[] data) {
    return( nicePrint( data, false ));
  }
  
  
  public static String nicePrint( ByteBuffer data ) {
    byte[] raw = new byte[ data.limit() ];
    
    for( int i=0; i < raw.length; i++ ) {
      raw[i] = data.get( i );
    }
    
    return nicePrint( raw );
  }
  
  
    
  public static String 
  nicePrint(
  	byte[] data, 
	boolean tight) 
  {
		if (data == null) {
			return "";
		}

		int dataLength = data.length;

		// Arbitrary limit
		if (dataLength > 1024) {
			dataLength = 1024;
		}

		int size = dataLength * 2;
		if (!tight) {
			size += (dataLength - 1) / 4;
		}

		char[] out = new char[size];

		try {
			int pos = 0;
			for (int i = 0; i < dataLength; i++) {
				if ((!tight) && (i % 4 == 0) && i > 0) {
					out[pos++] = ' ';
				}

				out[pos++] = HEXDIGITS[(byte) ((data[i] >> 4) & 0xF)];
				out[pos++] = HEXDIGITS[(byte) (data[i] & 0xF)];
			}

		} catch (Exception e) {
			Debug.printStackTrace(e);
		}

		try {
			return new String(out);
		} catch (Exception e) {
			Debug.printStackTrace(e);
		}

		return "";
	}


  public static String nicePrint(byte b) {
    byte b1 = (byte) ((b >> 4) & 0x0000000F);
    byte b2 = (byte) (b & 0x0000000F);
    return nicePrint2(b1) + nicePrint2(b2);
  }


  public static String nicePrint2(byte b) {
    String out = "";
    switch (b) {
      case 0 :
        out = "0";
        break;
      case 1 :
        out = "1";
        break;
      case 2 :
        out = "2";
        break;
      case 3 :
        out = "3";
        break;
      case 4 :
        out = "4";
        break;
      case 5 :
        out = "5";
        break;
      case 6 :
        out = "6";
        break;
      case 7 :
        out = "7";
        break;
      case 8 :
        out = "8";
        break;
      case 9 :
        out = "9";
        break;
      case 10 :
        out = "A";
        break;
      case 11 :
        out = "B";
        break;
      case 12 :
        out = "C";
        break;
      case 13 :
        out = "D";
        break;
      case 14 :
        out = "E";
        break;
      case 15 :
        out = "F";
        break;
    }
    return out;
  }

  public static String
  encodeString(
  	byte[]		bytes )
  {
  	return( nicePrint( bytes, true ));
  }
  
  public static String
  encodeString(
  	byte[]		bytes,
  	int			offset,
  	int			len )
  {
	  byte[]	x = new byte[len];
	  
	  System.arraycopy( bytes, offset, x, 0, len );
	  
  	  return( nicePrint( x, true ));
  }
  
  public static byte[]
  decodeString(
  	String		str )
  {
  	char[]	chars = str.toCharArray();
  	
  	int	chars_length = chars.length - chars.length%2;
  	
  	byte[]	res = new byte[chars_length/2];
  	
  	for (int i=0;i<chars_length;i+=2){
 
  		String	b = new String(chars,i,2);
   		
  		res[i/2] = (byte)Integer.parseInt(b,16);
  	}
  	
  	return( res );
  }
}
