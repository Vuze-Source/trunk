/*
 * File    : RPRequestHandler.java
 * Created : 15-Mar-2004
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

package org.gudy.azureus2.pluginsimpl.remote;

/**
 * @author parg
 *
 */

import java.util.*;

import org.gudy.azureus2.plugins.*;
import org.gudy.azureus2.plugins.logging.*;
import org.gudy.azureus2.plugins.ipfilter.*;
import org.gudy.azureus2.pluginsimpl.remote.download.*;
import org.gudy.azureus2.pluginsimpl.remote.rpexceptions.*;

public class
RPRequestHandler
{
    protected PluginInterface   plugin_interface;

    protected Map   reply_cache = new HashMap();
    protected boolean generic_classes;
    
    public RPRequestHandler(PluginInterface _pi) {
    	this(_pi, false);
    }

    public
    RPRequestHandler(
        PluginInterface     _pi,
        boolean use_generic_classes)
    {
        plugin_interface    = _pi;
        generic_classes = use_generic_classes;
    }

    public RPReply
    processRequest(
        RPRequest                   request )
    {
        return( processRequest( request, null));
    }

    public RPReply
    processRequest(
        RPRequest                   request,
        RPRequestAccessController   access_controller )
    {
        Long    connection_id   = new Long( request.getConnectionId());

        replyCache  cached_reply = connection_id.longValue()==0?null:(replyCache)reply_cache.get(connection_id);

        if ( cached_reply != null ){

            if ( cached_reply.getId() == request.getRequestId()){

                return( cached_reply.getReply());
            }
        }

        RPReply reply = processRequestSupport( request, access_controller );
        reply_cache.put( connection_id, new replyCache( request.getRequestId(), reply ));

        return( reply );
    }

    protected RPReply
    processRequestSupport(
        RPRequest                   request,
        RPRequestAccessController   access_controller)
    {
        try{
            RPObject        object  = request.getObject();
            String          method  = request.getMethod();

            if ( object == null && method.equals("getSingleton")){
                RPObject pi = null;
                if (this.generic_classes) {
                    pi = GenericRPPluginInterface.create(plugin_interface);
                }
                else {
                    pi = RPPluginInterface.create(plugin_interface);
                }
                RPReply reply = new RPReply(pi);
                return( reply );

            }else if ( object == null && method.equals( "getDownloads")){

                RPPluginInterface pi = null;
                if (this.generic_classes) {
                    pi = GenericRPPluginInterface.create(plugin_interface);
                }
                else {
                    pi = RPPluginInterface.create(plugin_interface);
                }

                    // short cut method for quick access to downloads
                    // used by GTS

                RPObject dm = (RPObject)pi._process( new RPRequest(null, "getDownloadManager", null )).getResponse();

                RPReply rep = dm._process(new RPRequest( null, "getDownloads", null ));

                rep.setProperty( "azureus_name", pi.azureus_name );
                rep.setProperty( "azureus_version", pi.azureus_version );

                return( rep );

            }else if ( object == null ){
                throw new RPNoObjectIDException();

            }else{

                // System.out.println( "Request: con = " + request.getConnectionId() + ", req = " + request.getRequestId() + ", client = " + request.getClientIP());

                object = RPObject._lookupLocal( object._getOID());

                    // _setLocal synchronizes the RP objects with their underlying
                    // plugin objects

                object._setLocal();

                if ( method.equals( "_refresh" )){

                    RPReply reply = new RPReply( object );

                    return( reply );

                }else{

                    String  name = object._getName();

                    if ( access_controller != null ){

                        access_controller.checkAccess( name, request );
                    }

                    RPReply reply = object._process( request );

                    if (    name.equals( "IPFilter" ) &&
                            method.equals( "setInRangeAddressesAreAllowed[boolean]" ) &&
                            request.getClientIP() != null ){

                        String  client_ip   = request.getClientIP();

                            // problem here, if someone changes the mode here they'll lose their
                            // connection coz they'll be denied access :)

                        boolean b = ((Boolean)request.getParams()[0]).booleanValue();

                        LoggerChannel[] channels = plugin_interface.getLogger().getChannels();

                        IPFilter filter = plugin_interface.getIPFilter();

                        if ( b ){

                            if ( filter.isInRange( client_ip )){

                                    // we gotta add the client's address range

                                for (int i=0;i<channels.length;i++){

                                    channels[i].log(
                                            LoggerChannel.LT_INFORMATION,
                                        "Adding range for client '" + client_ip + "' as allow/deny flag changed to allow" );
                                }

                                filter.createAndAddRange(
                                        "auto-added for remote interface",
                                        client_ip,
                                        client_ip,
                                        false );

                                filter.save();

                                plugin_interface.getPluginconfig().save();
                            }

                        }else{

                            IPRange[]   ranges = filter.getRanges();

                            for (int i=0;i<ranges.length;i++){

                                if ( ranges[i].isInRange(client_ip)){

                                    for (int j=0;j<channels.length;j++){

                                        channels[j].log(
                                                LoggerChannel.LT_INFORMATION,
                                                "deleting range '" + ranges[i].getStartIP() + "-" + ranges[i].getEndIP() + "' for client '" + client_ip + "' as allow/deny flag changed to deny" );
                                    }

                                    ranges[i].delete();
                                }
                            }

                            filter.save();

                            plugin_interface.getPluginconfig().save();
                        }
                    }

                    return( reply );
                }
            }
        }catch( RPException e ){
            return( new RPReply( e ));
        }
        catch (Exception e) {
            throw new RPInternalProcessException(e);
        }
    }

    protected static class
    replyCache
    {
        protected long      id;
        protected RPReply   reply;

        protected
        replyCache(
            long        _id,
            RPReply     _reply )
        {
            id      = _id;
            reply   = _reply;
        }

        protected long
        getId()
        {
            return( id );
        }

        protected RPReply
        getReply()
        {
            return( reply );
        }
    }

}