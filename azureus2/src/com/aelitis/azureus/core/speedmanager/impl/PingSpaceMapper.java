package com.aelitis.azureus.core.speedmanager.impl;

/**
 * Created on Jun 14, 2007
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

/**
 * Classifies the ping-times and then maps them against the a
 * grid of upload and download rates.
 *
 * Create a two dimensional map of upload and download rates. Map onto this
 * space ping times.
 *
 * The mesh size will be smaller near zero, and larger higher up.
 *
 * 0 - 100 kByte/sec   - 10 kBytes mesh size.
 * 100 - 500 kBytes/sec - 50 kBytes mesh size.
 * 500 - 5000 kBytes/sec - 100 kBytes mesh size.
 * Anything above 5 MBytes/sec is one region.
 *
 */
public class PingSpaceMapper
{
    GridRegion[][] gridRegion; //here upIndex,downIndex

    int lastDownloadBitsPerSec;
    int lastUploadBitsPerSec;

    int goodPingInMilliSec;
    int badPingInMilliSec;

    int totalPointsInMap = 0;

    private static final int maxMeshIndex=70;

    /**
     * Create a grid and define good and bad ping times.
     * @param _goodPingInMilliSec -
     * @param _badPingInMilliSec -
     */
    public PingSpaceMapper(int _goodPingInMilliSec, int _badPingInMilliSec){

        createNewGrid();

        goodPingInMilliSec = _goodPingInMilliSec;
        badPingInMilliSec = _badPingInMilliSec;
    }


    private void createNewGrid() {
        //create the mesh. We will have 70 by 70 grid. Even though we only use 63.
        gridRegion = null;
        gridRegion = new GridRegion[maxMeshIndex][maxMeshIndex];
        for(int upIndex=0;upIndex<maxMeshIndex;upIndex++){
            for(int downIndex=0;downIndex<maxMeshIndex;downIndex++){
                gridRegion[upIndex][downIndex] = new GridRegion();
            }
        }
    }


    /**
     * We have a hard coded mesh.
     * 0-9999 = 0, 10000-
     *
     *
     * @param bitsPerSec -
     * @return - mesh index.
     */
    private int convertBitsPerSec2meshIndex(int bitsPerSec){

        if( bitsPerSec<0){
            return 0;
        }

        int bytesPerSec = bitsPerSec/1024;

        if( bytesPerSec<100){
            return bytesPerSec/10;
        }else if(bytesPerSec<500){
            return 10 + ((bytesPerSec-100)/50);
        }else if(bytesPerSec<5000){
            return 10 + 8 + ((bytesPerSec-500)/100);
        }

        //return max mesh index.
        return 10 + 8 + 45;
    }//convertBitsPerSec2meshIndex

    /**
     * The reverse of bit/sec -> mesh index calculation.
     * @param meshIndex - value between 0 and 70
     * @return lowest BitsPerSecond that meets that criteria.
     */
    private int convertMeshIndex2bitsPerSec(int meshIndex){

        int bytesPerSec=0;
        if(meshIndex<=0){
            return 0;
        }

        if(meshIndex<=10){
            bytesPerSec = meshIndex*10;
        }else if(meshIndex<=18){
            bytesPerSec = 100 + (meshIndex-10) * 50;
        }else{
            bytesPerSec = 500 + (meshIndex-18) * 100;
        }

        return bytesPerSec*1024;
    }//convertMeshIndex2bitsPerSec

    public void setCurrentTransferRates(int downloadBitPerSec, int uploadBitsPerSec){
        lastDownloadBitsPerSec = downloadBitPerSec;
        lastUploadBitsPerSec = uploadBitsPerSec;
    }

    public void addMetricToMap(int metric){

        int downIndex = convertBitsPerSec2meshIndex(lastDownloadBitsPerSec);
        int upIndex = convertBitsPerSec2meshIndex(lastUploadBitsPerSec);

        totalPointsInMap++;

        if( metric<goodPingInMilliSec ){
            gridRegion[upIndex][downIndex].incrementMetricCount( GridRegion.INDEX_PING_GOOD );
        }else if( metric<badPingInMilliSec ){
            gridRegion[upIndex][downIndex].incrementMetricCount( GridRegion.INDEX_PING_NEUTRAL );
        }else{
            gridRegion[upIndex][downIndex].incrementMetricCount( GridRegion.INDEX_PING_BAD );
        }

    }//addMetricToMap

    /**
     * Start accumlating data from scratch.
     */
    public void reset(){
        totalPointsInMap=0;
        createNewGrid();
    }

    public static final int RESULT_UPLOAD_INDEX = 0;
    public static final int RESULT_DOWNLOAD_INDEX = 1;

    private Result getHighestMeshIndexWithGoodPing(){
       Result[] retVal = calculate();
       return retVal[0];
    }


    private Result getHighestMeshIndexWithAnyPing(){
        Result[] retVal = calculate();
        return retVal[1];
    }

    /**
     * Look at the Map and find the highest index for each catagory.
     * @return Result[2], where index 0 is goodPing, index 1 is anyPing
     */
    private Result[] calculate(){

        final int goodPing=0;
        final int anyPing=1;

        Result[] retVal = new Result[2];
        retVal[goodPing] = new Result();
        retVal[anyPing] = new Result();

        for(int upIndex=0;upIndex<maxMeshIndex;upIndex++){
            for(int downIndex=0;downIndex<maxMeshIndex;downIndex++)
            {
                //Register this grid point if it has more good then bad pings.
                float rating = gridRegion[upIndex][downIndex].getRating();
                if( rating>0.0f ){
                    retVal[goodPing].checkAndUpdate(upIndex,downIndex);
                }

                //Register this grid point if it has any ping values.
                int count = gridRegion[upIndex][downIndex].getTotal();
                if( count>0 ){
                    retVal[anyPing].checkAndUpdate(upIndex,downIndex);
                }

            }//for
        }//for

        return retVal;
    }

    /**
     * Make a guess at the upload capacity based on metric data.
     * @return -
     */
    public int guessUploadLimit(){

        Result result = getHighestMeshIndexWithGoodPing();
        int upMeshIndex = result.getUploadIndex();
        return convertMeshIndex2bitsPerSec( upMeshIndex );
    }

    /**
     * Make a guess at the download capacity based on metric data.
     * @return -
     */
    public int guessDownloadLimit(){

        Result result = getHighestMeshIndexWithGoodPing();
        int downMeshIndex = result.getDownloadIndex();
        return convertMeshIndex2bitsPerSec( downMeshIndex );
    }

    /**
     * Class to return a result.
     */
    static class Result{
        int highestUploadIndex=0;
        int highestDownloadIndex=0;

        /**
         * If the input index is higher then stored, update it with the new value.
         * @param uploadIndex -
         * @param downloadIndex -
         */
        public void checkAndUpdate(int uploadIndex, int downloadIndex){

            if( uploadIndex>highestUploadIndex ){
                highestUploadIndex=uploadIndex;
            }

            if( downloadIndex>highestDownloadIndex ){
                highestDownloadIndex=downloadIndex;
            }
        }

        public int getUploadIndex(){
            return highestUploadIndex;
        }

        public int getDownloadIndex(){
            return highestDownloadIndex;
        }
    }//class Result

    /**
     * A region on the grid for accumulating counts.
     */
    static class GridRegion{

        public static final int INDEX_PING_GOOD = 0;
        public static final int INDEX_PING_NEUTRAL = 1;
        public static final int INDEX_PING_BAD = 2;

        int pingCount[] = new int[3];
        int uploadBound[] = new int[2];
        int downloadBound[] = new int[2];

        public void incrementMetricCount( int pingIndex){
            if(pingIndex>=0 && pingIndex<=3){
                ++pingCount[pingIndex];
            }
        }//incrementMetricCount

        public float getRating(){

            int total = getTotal();

            if( total==0 ){
                return 0.0f;
            }

            float score = (pingCount[INDEX_PING_GOOD]
                    + 0.5f * pingCount[INDEX_PING_NEUTRAL]
                    - pingCount[INDEX_PING_BAD]);

            return ( score / (float) total );
        }//getRating

        public int getTotal(){

            return  pingCount[INDEX_PING_GOOD]
                   +pingCount[INDEX_PING_NEUTRAL]
                   +pingCount[INDEX_PING_BAD];
        }//getTotal

    }//class

}
