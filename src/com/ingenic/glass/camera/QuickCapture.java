/*
 */
package com.ingenic.glass.camera;

import java.io.File;
import java.io.IOException;

import org.xmlpull.v1.XmlPullParserException;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.location.Location;
import android.net.Uri;
import android.provider.MediaStore.Images;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.Gravity;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;

import com.ingenic.glass.camera.gallery.ImageGetter;
import com.ingenic.glass.camera.gallery.BitmapManager;
import com.ingenic.glass.camera.util.Exif;
import com.ingenic.glass.camera.util.Util;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import com.ingenic.glass.voicerecognizer.api.VoiceRecognizer;

/** The QuickCapture class which can take pictures quickly. */
public class QuickCapture implements SurfaceHolder.Callback, android.hardware.Camera.ErrorCallback  {

    private static final String TAG = "QuickCapture";
    private static final boolean DEBUG = true;
    private FrameLayout mPreviewFrameLayout;
    private SurfaceView mPreviewFrame;  // Preview frame area.
    private Context mContext;
    private Parameters mParameters;
    private ComboPreferences mPreferences;
    private SurfaceHolder mSurfaceHolder = null;
    private boolean mOpenCameraFail = false;
    private boolean mCameraDisabled = false;

    private static final int PREVIEW_STOPPED = 0;
    private static final int IDLE = 1;  // preview is active
    private static final int SNAPSHOT_IN_PROGRESS = 2;
    private int mCameraState = PREVIEW_STOPPED;

    private ContentResolver mContentResolver;
    private LocationManager mLocationManager;
    private OnScreenHint mStorageHint;
    private long mPicturesRemaining;

    private int mCameraId;
    private Camera mCameraDevice;
    private View mRootView;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mWindowParams;

    // play camera click sound effect
    private MediaPlayer mPlayer;

    private VoiceRecognizer mVoiceRecognizer = null;
    private static QuickCapture mInstance = null;

    private boolean IS_USE_QuickCapture_HALStore = false;
    private boolean QuickCapture_HALStoreJpeg_Flag = true;
    private String mQuickCapture_HALStoreJpeg_fullpath = null;

    public static QuickCapture getMInstance() {
	    return mInstance;
    }
    public QuickCapture(Context context){
	mContext = context;
	QuickCapture.mInstance = this;

	mVoiceRecognizer = new VoiceRecognizer(VoiceRecognizer.REC_TYPE_COMMAND, null);
	mVoiceRecognizer.setAppName("QuickCapture");
    }

    private final class JpegPictureCallback implements PictureCallback {
        Location mLocation;

        public JpegPictureCallback(Location loc) {
            mLocation = loc;
        }

        public void onPictureTaken(final byte [] jpegData, final android.hardware.Camera camera) {
            if(DEBUG) Log.d(TAG, "------onPictureTaken in");
	    if (DEBUG) {
		    Log.e(TAG, "IS_USE_QuickCapture_HALStore = " + IS_USE_QuickCapture_HALStore);
		    Log.e(TAG, "QuickCapture_HALStoreJpeg_Flag = " + QuickCapture_HALStoreJpeg_Flag);
	    }
	    /**
	     * IS_USE_QuickCapture_HALStore   : 是否使用CameraHal存储
	     * QuickCapture_HALStoreJpeg_Flag : CameraHal存储是否成功
	     */
	    if (IS_USE_QuickCapture_HALStore && QuickCapture_HALStoreJpeg_Flag) {
		    /* 使用CameraHal存储并且存储成功，停止预览，结束快拍 */
		    if (DEBUG)
			    Log.d(TAG, "Camera Hal Store Jpeg success.");
		    //stopAudio();
		    //startAudio(R.raw.camera_click);

		    stopPreview();
		    notifyMediaScanner();
		    finish();
	    } else if (IS_USE_QuickCapture_HALStore && (!QuickCapture_HALStoreJpeg_Flag)) {
		    /* 使用CameraHal存储但是存储失败，此时删除CameraHal存储时生成的文件，并且应用重新存储文件；然后停止预览，结束快拍 */
		    new File(mQuickCapture_HALStoreJpeg_fullpath).delete();
		    //stopAudio();
		    //startAudio(R.raw.camera_click);

		    stopPreview();
		    storeImage(jpegData, mLocation);
		    notifyMediaScanner();
		    finish();
	    } else {
		    /* 否则，重新存储图片，并停止预览，结束快拍 */
		    //stopAudio();
		    //startAudio(R.raw.camera_click);

		    stopPreview();
		    storeImage(jpegData, mLocation);
		    notifyMediaScanner();
		    finish();
	    }
	}
    }

    private void storeImage(final byte[] data, Location loc) {
        long dateTaken = System.currentTimeMillis();
        String title = Util.createJpegName(dateTaken);
        int orientation = Exif.getOrientation(data);
        Size s = mParameters.getPictureSize();
        Uri uri = Storage.addImage(mContentResolver, title, dateTaken, loc, 
				   orientation, data, s.width, s.height);
    }

    private boolean takePicture() {
        if(DEBUG) Log.d(TAG, "takePicture in mCameraState = "+mCameraState);
        // If we are already in the middle of taking a snapshot then ignore.
        if (mCameraState == SNAPSHOT_IN_PROGRESS || mCameraDevice == null) {
            return false;
        }
        // Set rotation and gps data.
        Util.setRotationParameter(mParameters, mCameraId, OrientationEventListener.ORIENTATION_UNKNOWN);
        Location loc = mLocationManager.getCurrentLocation();
        Util.setGpsParameters(mParameters, loc);
	if(DEBUG) Log.d(TAG,"call takePicture------");
        mCameraDevice.takePicture(null, null, null, new JpegPictureCallback(loc));
        mCameraState = SNAPSHOT_IN_PROGRESS;
	return true;
    }

    private void getPreferredCameraId() {
        mPreferences = new ComboPreferences(mContext);
        CameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal());
        mCameraId = CameraSettings.readPreferredCameraId(mPreferences);
    }

    private void openCamera() {
	try {
	    mCameraDevice = Util.openCamera(mContext, mCameraId);
	    if(DEBUG) Log.d(TAG,"mCameraDevice="+mCameraDevice);
	    if (mCameraDevice == null)
		mOpenCameraFail = true;
	} catch (CameraHardwareException e) {
	    mOpenCameraFail = true;
	    Log.e(TAG,"openCamera failed");
	} catch (CameraDisabledException e) {
	    mCameraDisabled = true;
	    Log.e(TAG,"camera disabled");
	}
    }

    public void start() {
	try{
	    int currentBatteryVoltage = Settings.System.getInt(mContext.getContentResolver(),
							       "batteryVoltage");
	    if(DEBUG) Log.d(TAG,"currentBatteryVoltage = "+currentBatteryVoltage);
	    if (currentBatteryVoltage <= ActivityBase.LOWEST_BATTERY_VOLTAGE){
		mVoiceRecognizer.playTTS(mContext.getString(R.string.tts_take_picture_lowpower));
		finish();
		return;
	    }
	}catch(SettingNotFoundException  e){
	    e.printStackTrace();
	}
	if(DEBUG) Log.d(TAG,"start in");

        mContentResolver = mContext.getContentResolver();
	if(checkStorage() == false){
	    finish();
	    return;
	}

	startAudio(R.raw.empty);

	getPreferredCameraId();

	openCamera();

	if (mOpenCameraFail) {
	    // Toast.makeText(mContext, R.string.cannot_connect_camera, Toast.LENGTH_LONG).show();
	    startAudio(R.raw.camera_error);
	    finish();
	    return;
	} else if (mCameraDisabled) {
	    // Toast.makeText(mContext, R.string.camera_disabled, Toast.LENGTH_LONG).show();
	    startAudio(R.raw.camera_error);
	    finish();
	    return;
	}

	stopAudio();
	//startAudio(R.raw.camera_focus);

	initView();

        mCameraDevice.setErrorCallback(this);

	SurfaceHolder holder = mPreviewFrame.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mPreferences.setLocalId(mContext, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());

	mLocationManager = new LocationManager(mContext, null);
        mLocationManager.recordLocation(false);
    }

    private void finish() {
	if (mWindowManager != null)
	    mWindowManager.removeView(mRootView);
	closeCamera();
	mContext = null;
	QuickCapture.mInstance = null;
    }

    private boolean checkStorage() {
        mPicturesRemaining = Storage.getAvailableSpace();
        if (mPicturesRemaining > Storage.LOW_STORAGE_THRESHOLD) {
            mPicturesRemaining = (mPicturesRemaining - Storage.LOW_STORAGE_THRESHOLD) / Storage.PICTURE_SIZE;
        } else if (mPicturesRemaining > 0) {
            mPicturesRemaining = 0;
        }

        return updateStorageHint();
    }

    private boolean updateStorageHint() {
        String noStorageText = null;
        if (mPicturesRemaining == Storage.UNAVAILABLE) {
            noStorageText = mContext.getString(R.string.no_storage);
        } else if (mPicturesRemaining == Storage.PREPARING) {
            noStorageText = mContext.getString(R.string.preparing_sd);
        } else if (mPicturesRemaining == Storage.UNKNOWN_SIZE) {
            noStorageText = mContext.getString(R.string.access_sd_fail);
        } else if (mPicturesRemaining < 1L) {
            noStorageText = mContext.getString(R.string.not_enough_space);
        }

        if (noStorageText != null) {
	    Log.e(TAG, "checkStorage " + noStorageText);
	    mVoiceRecognizer.playTTS(noStorageText);
            // if (mStorageHint == null) {
            //     mStorageHint = OnScreenHint.makeText(mContext, noStorageText);
            // } else {
            //     mStorageHint.setText(noStorageText);
            // }
            // mStorageHint.show();
	    return false;
        } else if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }

	return true;
    }
    
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
	if(DEBUG) Log.d(TAG, "surfaceChanged w = " + w + " h = " + h);
        // Make sure we have a surface in the holder before proceeding.
        if (holder.getSurface() == null) {
            return;
        }
        mSurfaceHolder = holder;

        if (mCameraState == PREVIEW_STOPPED) {		
	    startPreview();
	    takePicture();
	    stopAudio();
	    startAudio(R.raw.camera_click);
        } else {
	    if (holder.isCreating()) {
	    	setPreviewDisplay(holder);
	    }
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        mSurfaceHolder = null;
    }

    private void closeCamera() {
        if (mCameraDevice != null) {
	    if(DEBUG) Log.d(TAG,"closeCamera------");
            CameraHolder.instance().release();
            mCameraDevice.setErrorCallback(null);
            mCameraDevice = null;
            mCameraState = PREVIEW_STOPPED;
        }
    }

    private void setPreviewDisplay(SurfaceHolder holder) {
        try {
            mCameraDevice.setPreviewDisplay(holder);
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("setPreviewDisplay failed", ex);
        }
    }

    private void startPreview() {
        // If we're previewing already, stop the preview first (this will blank the screen).
        if (mCameraState != PREVIEW_STOPPED) stopPreview();

        setPreviewDisplay(mSurfaceHolder);

        setCameraParameters();
        // If the focus mode is continuous autofocus, call cancelAutoFocus to
        // resume it because it may have been paused by autoFocus call.
        if (Parameters.FOCUS_MODE_CONTINUOUS_PICTURE.equals(mParameters.getFocusMode())) {
            mCameraDevice.cancelAutoFocus();
        }

        try {
	    if(DEBUG) Log.d(TAG,"startPreview------");
	    mCameraDevice.startPreview();
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("startPreview failed", ex);
        }
        mCameraState = IDLE;
    }

    private void stopPreview() {
        if (mCameraDevice != null && mCameraState != PREVIEW_STOPPED) {
            if(DEBUG) Log.d(TAG, "stopPreview------");
            mCameraDevice.stopPreview();
        }
        mCameraState = PREVIEW_STOPPED;
    }

    private void setCameraParameters() {
        mParameters = mCameraDevice.getParameters();
	mParameters.set("preview_mode", CameraAppImpl.NO_SCREEN);//ipu_direct

	/* 设置使用CameraHal存储 */
	mQuickCapture_HALStoreJpeg_fullpath = Storage.generate_QuickPicturefullname();
	IS_USE_QuickCapture_HALStore = true;
	mParameters.set("quickcapture-halstore-dir", mQuickCapture_HALStoreJpeg_fullpath);

	mParameters.setPictureSize(3264, 2448);
	mParameters.setPreviewSize(640, 480);
	mCameraDevice.setParameters(mParameters);
    }

    public void initView() {
    	mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
    	mWindowParams = new WindowManager.LayoutParams();
    	mWindowParams.type = WindowManager.LayoutParams.TYPE_PHONE;
    	mWindowParams.format = PixelFormat.RGBA_8888;
    	mWindowParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
	    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    	mWindowParams.width = WindowManager.LayoutParams.MATCH_PARENT;
    	mWindowParams.height = WindowManager.LayoutParams.MATCH_PARENT;
    	mWindowParams.gravity = Gravity.CENTER;

	int windowW = mWindowManager.getDefaultDisplay().getWidth();  
	int windowH = mWindowManager.getDefaultDisplay().getHeight();
	if(DEBUG) Log.d(TAG,"window w = "+windowW+" h = "+windowH);
	LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	mRootView = inflater.inflate(R.layout.preview_frame, null);
	mPreviewFrameLayout = (FrameLayout) mRootView.findViewById(R.id.frame);
        mPreviewFrame = (SurfaceView) mRootView.findViewById(R.id.camera_preview);
	mPreviewFrameLayout.setLayoutParams(new FrameLayout.LayoutParams(windowW, windowH));
	// mPreviewFrame.setLayoutParams(new FrameLayout.LayoutParams(windowW, windowH));
	mPreviewFrame.setLayoutParams(new FrameLayout.LayoutParams(1, 1));

	mWindowManager.addView(mRootView, mWindowParams);
    }

    private void notifyMediaScanner() {
	Thread thread = new Thread() {
		@Override
		    public void run() {
		    ImageGetter ig = new ImageGetter(mContentResolver, Images.Media.EXTERNAL_CONTENT_URI,
						     PhotoActivity.SORT_DESCENDING, null);
		}
	    };
        BitmapManager.instance().allowThreadDecoding(thread);
        thread.start();
    }

    synchronized private void startAudio(int resid){
	if (resid!=0) {
	    if (mPlayer == null)
		mPlayer = MediaPlayer.create(mContext, resid);
            if (mPlayer != null) {
            	mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            	mPlayer.setOnCompletionListener(new OnCompletionListener() {
			public void onCompletion(MediaPlayer mp) {
			    stopAudio();
			    if(DEBUG) Log.d(TAG, "media play completion");
			}
		    });
            	mPlayer.setOnErrorListener(new OnErrorListener() {
			public boolean onError(MediaPlayer mp, int what, int extra) {
			    stopAudio();                    		
			    if(DEBUG) Log.d(TAG, "media play error");
			    return true;
			}
		    });
                mPlayer.start();
		if(DEBUG) Log.d(TAG,"media play start");
            }
        }
    }

    synchronized public void stopAudio() {
	if (mPlayer != null) {
	    try {
		if(DEBUG) Log.d(TAG,"media play stop");
		mPlayer.stop();
		mPlayer.release();
	    } finally {
		mPlayer = null;
	    }
	}
    }

    @Override
    public void onError(int error, Camera camera) {
        Log.e(TAG, "Got camera error callback. error=" + error);

	switch(error) {
	/* 如果CameraHal存储时，发生错误 */
	case CameraAppImpl.CAMERA_ERROR_QUICKCAPTURE_HAL_STORE:
		QuickCapture_HALStoreJpeg_Flag = false;
		break;
	default :
		startAudio(R.raw.camera_error);
		stopPreview();
		finish();	
	}
    }
}
