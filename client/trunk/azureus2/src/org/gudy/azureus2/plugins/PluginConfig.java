/*
 * File    : PluginConfig.java
 * Created : 17 nov. 2003
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
 
package org.gudy.azureus2.plugins;

/**
 * @author Olivier
 *
 */
public interface PluginConfig {  

  //TODO : Add proper documentation for javadoc  
  
  //Azureus's parameters accessor
  public int getIntParameter(String key);
  public String getStringParameter(String key);
  public boolean getBooleanParameter(String key);
  
  //Plugin specific parameters
  public int getPluginIntParameter(String key);
  public int getPluginIntParameter(String key,int defaultValue);
  
  public String getPluginStringParameter(String key);
  public String getPluginStringParameter(String key,int defaultValue);
    
  public boolean getPluginBooleanParameter(String key);
  public boolean getPluginBooleanParameter(String key,int defaultValue);
    
  public void setPluginParameter(String key,int value);
  public void setPluginParameter(String key,String value);
  public void setPluginParameter(String key,boolean value);  
}
