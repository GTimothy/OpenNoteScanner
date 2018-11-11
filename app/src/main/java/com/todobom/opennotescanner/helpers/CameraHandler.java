package com.todobom.opennotescanner.helpers;

import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import com.todobom.opennotescanner.OpenNoteScannerActivity;

import java.util.List;

public class CameraHandler {
    private static final String TAG = "CameraHandler";
    private PackageManager pm;
    private Camera mCamera;
    private Handler autofocusLoopHandler = new Handler();
    private Camera.Parameters param;
    private boolean isFocusable = false;
    private boolean mFocused=true;
    private boolean safeToTakePicture = true;
    private static int maxFocusTries = 5;


    public CameraHandler(int cameraID, PackageManager packageManager){
        mCamera = Camera.open(cameraID);
        param = mCamera.getParameters();
        pm = packageManager;
        Camera.Size pSize = getMaxPreviewResolution();
        param.setPreviewSize(pSize.width, pSize.height);
        Camera.Size maxRes = getMaxPictureResolution(pSize.width / pSize.height);
        if ( maxRes != null) {
            param.setPictureSize(maxRes.width, maxRes.height);
            Log.d(TAG,"max supported picture resolution: " + maxRes.width + "x" + maxRes.height);
        }

        if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS)) {
            //following code prevents crash on newer tablets -jdl
            List<String> focusModes = param.getSupportedFocusModes();
            if (focusModes.contains("auto"))
                param.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);

            else if (focusModes.contains("continuous-picture"))
                param.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            isFocusable=true;
            Log.d(TAG, "enabling autofocus");
        } else {
            mFocused = true;
            isFocusable=false;
            Log.d(TAG, "autofocus not available");
        }

        mCamera.setParameters(param);


        try {
            mCamera.setAutoFocusMoveCallback(new Camera.AutoFocusMoveCallback() {
                @Override
                public void onAutoFocusMoving(boolean start, Camera camera) {
                    safeToTakePicture=!start;
                    Log.d(TAG, "focusMoving: " + start);
                }
            });
        } catch (Exception e) {
            Log.d(TAG, "failed setting AutoFocusMoveCallback");
        }
    }

//    ===================================GETTERS===============================

    public Camera.Size getPreviewSize(){
        return param.getPreviewSize();
    }

    public boolean isSafeToTakePicture(){
        return safeToTakePicture;
    }

//    ==================================SETTERS=================================

    public void setSafeToTakePicture(boolean b) {
        safeToTakePicture=b;
    }

//    =================================CONFIGURE=========================================

    public boolean setFlash(boolean stateFlash) {
        if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            param.setFlashMode(stateFlash ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
            mCamera.setParameters(param);
            Log.d(CameraHandler.TAG, "flash: " + (stateFlash ? "on" : "off"));
            return stateFlash;
        }
        return false;
    }

    public void setDisplayOrientation(int degrees){
        mCamera.setDisplayOrientation(degrees);
    }

//    ================================AUTOFOCUS==========================================

    public void startAutofocusLoop(){
        autofocusLoopHandler.post(autofocusLoop);
    }
    private final Runnable autofocusLoop = new Runnable() {
        public void run() {
            mCamera.autoFocus(null);
            autofocusLoopHandler.postDelayed(autofocusLoop, 1000); // 1 second
        }
    };

    public void stopAutofocusLoop(){
        autofocusLoopHandler.removeCallbacksAndMessages(null);

    }

    public void oneFocusAttempt() {
        mCamera.autoFocus(null);
    }

    private int loops_left = maxFocusTries;


    private class autofocusLoopCallback implements Camera.AutoFocusCallback{
        private Boolean myTakePictureOnFocus = false;
        private OpenNoteScannerActivity myOpenNoteScannerActivity;

        public autofocusLoopCallback(Boolean takePictureOnFocus, OpenNoteScannerActivity openNoteScannerActivity){
            super();
            myTakePictureOnFocus=takePictureOnFocus;
            myOpenNoteScannerActivity=openNoteScannerActivity;
        }

        public autofocusLoopCallback(){
            super();
        }

        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            if (success) {
                Log.d(TAG, String.format("autofocusLoopCallback  %d/%d: focus achieved, loop stopping, " +
                        "loop number reset for the future",maxFocusTries-loops_left+1,maxFocusTries));
                loops_left = maxFocusTries;
                if (myTakePictureOnFocus){
                    mCamera.takePicture(null,null,myOpenNoteScannerActivity);
                }
            } else {
                if (loops_left > 0) {
                    Log.d(TAG,String.format("autofocusLoopCallback %d/%d: focus failed, trying again",
                            maxFocusTries-loops_left+1,maxFocusTries));
                    loops_left--;
                    mCamera.autoFocus(this);
                }else {
                    Log.d(TAG, String.format("autofocusLoopCallback %d/%d: focus failed, loop stopping (no tries left)" +
                            ", loop number reset for the future", maxFocusTries - loops_left, maxFocusTries));
                    safeToTakePicture=true;
                }
            }
        }
    }

    public void loopUntilFocused() {
        mCamera.autoFocus(new autofocusLoopCallback());
    }

    public void loopUntilFocusedForPicture(OpenNoteScannerActivity openNoteScannerActivity) {
        safeToTakePicture=false;
        mCamera.autoFocus(new autofocusLoopCallback(true,openNoteScannerActivity));
    }

    public void cancelAutoFocus(){
        mCamera.cancelAutoFocus();
    }

//    ===========================COMMANDS====================================

    public void takePicture(OpenNoteScannerActivity openNoteScannerActivity){
        mCamera.takePicture(null, null, openNoteScannerActivity);
    }

//    ==============================PARAMETERS==========================================

    public List<Camera.Size> getResolutionList() {
        return mCamera.getParameters().getSupportedPreviewSizes();
    }

    public Camera.Size getMaxPreviewResolution() {
        int maxWidth=0;
        Camera.Size curRes=null;

        mCamera.lock();

        for ( Camera.Size r: getResolutionList() ) {
            if (r.width>maxWidth) {
                Log.d(TAG,"supported preview resolution: "+r.width+"x"+r.height);
                maxWidth=r.width;
                curRes=r;
            }
        }

        return curRes;
    }

    public List<Camera.Size> getPictureResolutionList() {
        return mCamera.getParameters().getSupportedPictureSizes();
    }

    public Camera.Size getMaxPictureResolution(float previewRatio) {
        int maxPixels=0;
        int ratioMaxPixels=0;
        Camera.Size currentMaxRes=null;
        Camera.Size ratioCurrentMaxRes=null;
        for ( Camera.Size r: getPictureResolutionList() ) {
            float pictureRatio = (float) r.width / r.height;
            Log.d(TAG,"supported picture resolution: "+r.width+"x"+r.height+" ratio: "+pictureRatio);
            int resolutionPixels = r.width * r.height;

            if (resolutionPixels>ratioMaxPixels && pictureRatio == previewRatio) {
                ratioMaxPixels=resolutionPixels;
                ratioCurrentMaxRes=r;
            }

            if (resolutionPixels>maxPixels) {
                maxPixels=resolutionPixels;
                currentMaxRes=r;
            }
        }

//        boolean matchAspect = openNoteScannerActivity.mSharedPref.getBoolean("match_aspect", true); todo:sort this out
        boolean matchAspect = false;

        if (ratioCurrentMaxRes!=null && matchAspect) {

            Log.d(TAG,"Max supported picture resolution with preview aspect ratio: "
                    + ratioCurrentMaxRes.width+"x"+ratioCurrentMaxRes.height);
            return ratioCurrentMaxRes;

        }

        return currentMaxRes;
    }

    public Camera.Size getPictureSize(){
        return param.getPictureSize();
    }




    //    ================================================COMMANDS====================================
    public void refreshCamera(OpenNoteScannerActivity openNoteScannerActivity, SurfaceHolder mSurfaceHolder) {
        try {
            mCamera.stopPreview();
        }

        catch (Exception e) {
        }

        try {
            mCamera.setPreviewDisplay(mSurfaceHolder);

            mCamera.startPreview();
            mCamera.setPreviewCallback(openNoteScannerActivity);
        }
        catch (Exception e) {
        }
    }

    public void surfaceDestroyed(){
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }
}
