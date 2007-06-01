package com.aelitis.azureus.core.speedmanager.impl;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.util.AEDiagnosticsLogger;
import org.gudy.azureus2.core3.util.AEDiagnostics;
import org.gudy.azureus2.core3.util.SystemTime;

/**
 * Created on May 23, 2007
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
 * This class is responsible for re-adjusting the limits used by AutoSpeedV2.
 *
 * This class will keep track of the "status" (i.e. seeding, downloading)of the
 * application. It will then re-adjust the MAX limits when it thinks limits
 * are being reached.
 *
 * Here are the rules it will use.
 *
 * #1) When seeding. If the upload is AT_LIMIT for a period of time it will allow
 * that to adjust upward.
 * #2) When downloading. If the download is AT_LIMIT for a period of time it will
 * allow that to adjust upward.
 *
 * #3) When downloading, if a down-tick is detected and the upload is near a limit,
 * it will drop the upload limit to 80% of MAX_UPLOAD.
 *
 * #4) Once that limit is reached it will drop both the upload and download limits together.
 *
 * #5) Seeding mode is triggered when - download bandwidth at LOW - compared to CAPACITY for 5 minutes continously.
 *
 * #6) Download mode is triggered when - download bandwidth reaches MEDIUM - compared to CURRENT_LIMIT for the first time.
 *
 * Rules #5 and #6 favor downloading over seeding.
 *
 */

public class SpeedLimitMonitor
{

    //use for home network.
    private int uploadLimitMax = 38000;
    private int uploadLimitMin = 5000;
    private int downloadLimitMax = 80000;
    private int downloadLimitMin = 8000;

    private static float upDownRatio=2.0f;

    //Upload and Download bandwidth usage modes. Compare usage to current limit.
    private SaturatedMode uploadBandwidthStatus =SaturatedMode.NONE;
    private SaturatedMode downloadBandwidthStatus =SaturatedMode.NONE;

    //Compare current limit to max limit.
    private SaturatedMode uploadLimitSettingStatus=SaturatedMode.AT_LIMIT;
    private SaturatedMode downloadLimitSettingStatus=SaturatedMode.AT_LIMIT;  

    //these methods are used to see how high limits can go.
    private boolean isUploadMaxPinned=true;   //ToDo: Might want to change this into a mode class.
    private boolean isDownloadMaxPinned=true; //ToDo: Might want to change this into a mode class.
    long uploadAtLimitStartTime =0;
    long downloadAtLimitStartTime =0;

    private static final long TIME_AT_LIMIT_BEFORE_UNPINNING = 5 * 60 * 1000; //five minutes.//ToDo: make this configurable.
    //private static final long TIME_AT_LIMIT_BEFORE_UNPINNING = 1 * 60 * 1000; //ToDo: REMOVE THIS IS FOR TESTING ONLY.


    private AEDiagnosticsLogger dLog = AEDiagnostics.getLogger("v3.AutoSpeed_Beta_Debug");


    public SpeedLimitMonitor(){
        //
    }

    public void updateFromCOConfigManager(){

        uploadLimitMax= COConfigurationManager.getIntParameter(SpeedManagerAlgorithmProviderV2.SETTING_UPLOAD_MAX_LIMIT);
        uploadLimitMin=COConfigurationManager.getIntParameter(SpeedManagerAlgorithmProviderV2.SETTING_UPLOAD_MIN_LIMIT);
        downloadLimitMax=COConfigurationManager.getIntParameter(SpeedManagerAlgorithmProviderV2.SETTING_DOWNLOAD_MAX_LIMIT);
        downloadLimitMin=COConfigurationManager.getIntParameter(SpeedManagerAlgorithmProviderV2.SETTING_DOWNLOAD_MIN_LIMIT);

        //tie the upload and download ratios together.
        upDownRatio = ( (float)downloadLimitMax/(float)uploadLimitMax );
        COConfigurationManager.setParameter(
                SpeedManagerAlgorithmProviderV2.SETTING_V2_UP_DOWN_RATIO, upDownRatio);

    }

    //SpeedLimitMonitorStatus

    public float getUpDownRatio(){
        return upDownRatio;
    }


    public void setDownloadBandwidthMode(int rate, int limit){
        downloadBandwidthStatus = SaturatedMode.getSaturatedMode(rate,limit);
    }

    public void setUploadBandwidthMode(int rate, int limit){
        uploadBandwidthStatus = SaturatedMode.getSaturatedMode(rate,limit);
    }

    public void setDownloadLimitSettingMode(int currLimit){
        downloadLimitSettingStatus = SaturatedMode.getSaturatedMode(currLimit,downloadLimitMax);
    }

    public void setUploadLimitSettingMode(int currLimit){
        uploadLimitSettingStatus = SaturatedMode.getSaturatedMode(currLimit,uploadLimitMax);
    }

    public SaturatedMode getDownloadBandwidthMode(){
        return downloadBandwidthStatus;
    }

    public SaturatedMode getUploadBandwidthMode(){
        return uploadBandwidthStatus;
    }

    public SaturatedMode getDownloadLimitSettingMode(){
        return downloadLimitSettingStatus;
    }

    public SaturatedMode getUploadLimitSettingMode(){
        return uploadLimitSettingStatus;
    }

    /**
     * Are both the upload and download bandwidths usages is low?
     * Otherwise false.
     * @return -
     */
    public boolean bandwidthUsageLow(){

        if( uploadBandwidthStatus.compareTo(SaturatedMode.LOW)<=0 &&
                downloadBandwidthStatus.compareTo(SaturatedMode.LOW)<=0){

            return true;

        }

        //Either upload or download is at MEDIUM or above.
        return false;
    }

    /**
     *
     * @return -
     */
    public boolean bandwidthUsageMedium(){
        if( uploadBandwidthStatus.compareTo(SaturatedMode.MED)<=0 &&
                downloadBandwidthStatus.compareTo(SaturatedMode.MED)<=0){
            return true;
        }

        //Either upload or download is at MEDIUM or above.
        return false;
    }

    /**
     * True if both are at limits.
     * @return - true only if both the upload and download usages are at the limits.
     */
    public boolean bandwidthUsageAtLimit(){
        if( uploadBandwidthStatus.compareTo(SaturatedMode.AT_LIMIT)==0 &&
                downloadBandwidthStatus.compareTo(SaturatedMode.AT_LIMIT)==0){
            return true;
        }
        return false;
    }

    /**
     * //ToDo: The reason this method and class exists is to make the business rules much more complex.
     * //ToDo: Test this logic.
     *
     * Here we need to handle several cases.
     * (a) If the download bandwidth is HIGH, then we need to back off on the upload limit to 80% of max.
     * (b) If upload bandwidth and limits are AT_LIMIT for a period of time then need to "unpin" that max limit
     *      to see how high it will go.
     * (c) If the download bandwidth and limits are AT_LIMIT for a period of time then need to "unpin" the max
     *      limit to see how high it will go.
     *
     *
     *
     * @param signalStrength -
     * @param multiple -
     * @param currUpLimit -
     * @return -
     */
    public Update createNewLimitEx(float signalStrength, float multiple, int currUpLimit){
        int newLimit;

        //The amount to move it against the new limit is.
        float multi = Math.abs( signalStrength * multiple * 0.3f );

        //If we are in an unpinned limits mode then consider consider
        if( !isUploadMaxPinned || !isDownloadMaxPinned ){
            //we are in a mode that is moving the limits.
            return calculateNewUnpinnedLimits(signalStrength, multiple, currUpLimit);            
        }//if

        //Force the value to the limit.
        if(multi>1.0f){
            if( signalStrength>0.0f ){
                log("forcing: max upload limit.");
                int newDownloadLimit = Math.round( uploadLimitMax*upDownRatio );
                return new Update(uploadLimitMax, true,newDownloadLimit, true);
            }else{
                log("forcing: min upload limit.");
                int newDownloadLimit = Math.round( uploadLimitMin*upDownRatio );
                return new Update(uploadLimitMin, true, newDownloadLimit, true);
            }
        }

        //don't move it all the way.
        int maxStep;
        int currStep;
        int minStep=1024;

        if(signalStrength>0.0f){
            maxStep = Math.round( uploadLimitMax -currUpLimit );
        }else{
            maxStep = Math.round( currUpLimit- uploadLimitMin);
        }

        currStep = Math.round(maxStep*multi);
        if(currStep<minStep){
            currStep=minStep;
        }

        if( signalStrength<0.0f ){
            currStep = -1 * currStep;
        }

        newLimit = currUpLimit+currStep;
        newLimit = (( newLimit + 1023 )/1024) * 1024;

        if(newLimit> uploadLimitMax){
            newLimit= uploadLimitMax;
        }
        if(newLimit< uploadLimitMin){
            newLimit= uploadLimitMin;
        }


        //determine if we should set new limits higher.
        checkForUnpinningCondition();


        log( "new-limit:"+newLimit+":"+currStep+":"+signalStrength+":"+multiple+":"+currUpLimit+":"+maxStep+":"+uploadLimitMax+":"+uploadLimitMin );

        int newDownloadLimit = Math.round( newLimit*upDownRatio );
        return new Update(newLimit, true, newDownloadLimit, true );
    }

    /**
     * Log debug info needed during beta period.
     */
    private void logPinningInfo() {
        StringBuffer sb = new StringBuffer("pin: ");
        if(isUploadMaxPinned){
            sb.append("ul-pinned:");
        }else{
            sb.append("ul-unpinned:");
        }
        if(isDownloadMaxPinned){
            sb.append("dl-pinned:");
        }else{
            sb.append("dl-unpinned:");
        }
        long currTime = SystemTime.getCurrentTime();
        long upWait = currTime - uploadAtLimitStartTime;
        long downWait = currTime - downloadAtLimitStartTime;
        sb.append(upWait).append(":").append(downWait);
        log( sb.toString() );
    }

    private Update calculateNewUnpinnedLimits(float signalStrength,float multiple,int currUpLimit){

        //first verify that is this is an up signal.
        if(signalStrength<0.0f){
            //down-tick is a signal to stop moving the files up.
            isUploadMaxPinned=true;
            isDownloadMaxPinned=true;
        }//if

        //just verify settings to make sure everything is sane before updating.
        boolean updateUpload=false;
        boolean updateDownload=false;

        if( uploadBandwidthStatus.compareTo(SaturatedMode.AT_LIMIT)==0 &&
                uploadLimitSettingStatus.compareTo(SaturatedMode.AT_LIMIT)==0 ){
            updateUpload=true;
        }

        if( downloadBandwidthStatus.compareTo(SaturatedMode.AT_LIMIT)==0 &&
                downloadLimitSettingStatus.compareTo(SaturatedMode.AT_LIMIT)==0 ){
            updateDownload=true;
        }

        boolean uploadChanged=false;
        boolean downloadChanged=false;
        //Lets do something very simple at this point to get it tested.
        //ToDo: get a better algorithm.
        if(updateUpload){
            //increase limit by one kilobyte.
            uploadLimitMax+=1024;
            uploadChanged=true;
            COConfigurationManager.setParameter(
                    SpeedManagerAlgorithmProviderV2.SETTING_UPLOAD_MAX_LIMIT, uploadLimitMax);
        }
        if(updateDownload){
            //increase limit by one kilobyte.
            downloadLimitMax+=1024;
            downloadChanged=true;
            COConfigurationManager.setParameter(
                    SpeedManagerAlgorithmProviderV2.SETTING_DOWNLOAD_MAX_LIMIT, downloadLimitMax);
        }

        //apply any rules that need applied.
        //The download limit can never be less then the upload limit.
        if( uploadLimitMax>downloadLimitMax ){
            downloadLimitMax=uploadLimitMax;
            downloadChanged=true;
        }

        //calculate the new ratio.
        upDownRatio = ( (float)downloadLimitMax/(float)uploadLimitMax );
        COConfigurationManager.setParameter(
                SpeedManagerAlgorithmProviderV2.SETTING_V2_UP_DOWN_RATIO, upDownRatio);

        
        //ToDo: these will need to vary independently. (There is a bug here, this only works if both are saturated.)
        return new Update(uploadLimitMax,uploadChanged,downloadLimitMax,downloadChanged);
    }//calculateNewUnpinnedLimits

    /**
     * Make a decision about unpinning either the upload or download limit. This is based on the
     * time we are saturating the limit without a down-tick signal.
     */
    public void checkForUnpinningCondition(){

        long currTime = SystemTime.getCurrentTime();

        //upload useage must be at limits for a set period of time before unpinning.
        if( !uploadBandwidthStatus.equals(SaturatedMode.AT_LIMIT) &&
                !uploadLimitSettingStatus.equals(SaturatedMode.AT_LIMIT) )
        {
            //start the clock over.
            uploadAtLimitStartTime = currTime;
        }else{
            //check to see if we have been here for the time limit.
            if( uploadAtLimitStartTime+TIME_AT_LIMIT_BEFORE_UNPINNING < currTime ){
                //we have been AT_LIMIT long enough. Time to un-pin the limitsee if we can go higher.
                isUploadMaxPinned = false;
                log("unpinning the upload max limit!!");
            }
        }

        //download usage must be at limits for a set period of time before unpinning.
        if( !downloadBandwidthStatus.equals(SaturatedMode.AT_LIMIT) &&
                !downloadLimitSettingStatus.equals(SaturatedMode.AT_LIMIT) )
        {
            //start the clock over.
            downloadAtLimitStartTime = currTime;
        }else{
            //check to see if we have been here for the time limit.
            if( downloadAtLimitStartTime+TIME_AT_LIMIT_BEFORE_UNPINNING < currTime ){
                //we have been AT_LIMIT long enough. Time to un-pin the limitsee if we can go higher.
                isDownloadMaxPinned = false;
                log("unpinning the download max limit!!");
            }
        }

        logPinningInfo();
    }

    /**
     * If we have a down-tick signal then reset all the counters for increasing the limits.
     */
    public void notifyOfDownSingal(){

        if( !isUploadMaxPinned ){
            log("pinning the upload max limit, due to downtick signal.");
        }

        if( !isDownloadMaxPinned ){
            log("pinning the download max limit, due to downtick signal.");
        }

        long currTime = SystemTime.getCurrentTime();

        uploadAtLimitStartTime = currTime;
        downloadAtLimitStartTime = currTime;
        isUploadMaxPinned = true;
        isDownloadMaxPinned = true;
    }


    /**
     * Need to
     */
    public void updateAtLimit(){

    }


    protected void log(String str){
        if(dLog!=null){
            dLog.log(str);
        }
    }//log


    /**
     * Class for sending update data.
     */
    static class Update{

        public int newUploadLimit;
        public int newDownloadLimit;

        public boolean hasNewUploadLimit;
        public boolean hasNewDownloadLimit;

        public Update(int upLimit, boolean newUpLimit, int downLimit, boolean newDownLimit){
            newUploadLimit = upLimit;
            newDownloadLimit = downLimit;

            hasNewUploadLimit = newUpLimit;
            hasNewDownloadLimit = newDownLimit;
        }

    }



}//SpeedLimitMonitor
