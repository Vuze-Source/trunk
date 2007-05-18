package com.aelitis.azureus.core.speedmanager.impl;

/**
 * Created on May 14, 2007
 * Created by Alan Snyder
 * Copyright (C) 2007 Aelitis, All Rights Reserved.
 * <p/>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p/>
 * AELITIS, SAS au capital de 63.529,40 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 */

public class CurrentPing
{

    String state = "";                  //m_state
    int latency=0;                      //m_pingAverage
    int lowest=0;                       //m_lowestPing
    int currentLimit=Integer.MAX_VALUE; //m_upload

    private static final String IDS_USS_STATE_PREPARING = "preparing";
    private static final String IDS_USS_STATE_WAITING = "waiting";
    private static final String IDS_USS_STATE_ERROR = "error";
    
    

}
