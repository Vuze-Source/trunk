/*
 * File    : LocaleDecoderImpl.java
 * Created : 30-Mar-2004
 * By      : parg
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

package org.gudy.azureus2.pluginsimpl.local.utils;

/**
 * @author parg
 *
 */

import java.io.UnsupportedEncodingException;

import org.gudy.azureus2.core3.internat.LocaleUtilDecoder;
import org.gudy.azureus2.core3.util.Debug;

import org.gudy.azureus2.plugins.utils.*;

public class 
LocaleDecoderImpl
	implements LocaleDecoder
{	
	LocaleUtilDecoder		decoder;
	
	protected
	LocaleDecoderImpl(
		LocaleUtilDecoder		_decoder )
	{
		decoder	= _decoder;
	}
	
	public String
	getName()
	{
		return( decoder.getName());
	}
	
	public String
	decode(
		byte[]		encoded_bytes )
	{
		try{
			return( decoder.decodeString( encoded_bytes ));
			
		}catch( UnsupportedEncodingException	e ){
			
			Debug.printStackTrace( e );
			
			return( null );
		}
	}
}
