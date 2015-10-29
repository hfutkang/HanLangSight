package com.ingenic.glass.camera.live;
import android.app.Activity;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.location.Location;
import android.media.CamcorderProfile;
import android.media.CameraProfile;
import android.media.MediaRecorder;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.os.Process;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.IBinder;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.GestureDetector;
import android.widget.GestureDetector.SimpleOnGestureListener;
import android.filterpacks.videosink.MediaRecorderStopException;

import java.util.List;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.ingenic.glass.camera.*;
import com.ingenic.glass.camera.util.Util;
import com.ingenic.glass.camera.ui.RotateLayout;
import com.ingenic.glass.camera.gallery.GalleryPicker;
import com.ingenic.glass.incall.aidl.IInCallService;
import com.ingenic.glass.incall.aidl.IInCallListener;
import android.os.BatteryManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
/**
 * The Camcorder activity.
 */
public class CameraLive extends ActivityBase
    implements /*CameraPreference.OnPreferenceChangedListener,*/
	       SurfaceHolder.Callback,
	       MediaRecorder.OnErrorListener, 
	       MediaRecorder.OnInfoListener,
	       EffectsRecorder.EffectsListener,OnTouchListener{    
    private static final String TAG = "CameraLive";
    private static final boolean DEBUG = true;	
    private static final int CLEAR_SCREEN_DELAY = 4;
    private static final int UPDATE_RECORD_TIME = 5;
    static final int START_PREVIEW=1;
    private static final int SCREEN_DELAY = 2 * 60 * 1000;

    private static final String LIVE_QUIT = "com.ingenic.glass.camera.live.LIVE_QUIT";
    private int OUTPUT_FORMAT_LIVE = 9; // see media/mediarecorder.h

    // Send message to GlassSync.LiveModule
    private final String PACKAGE_NAME = "cn.ingenic.glasssync";
    private final String CAMERA_ACTION_START = "com.ingenic.glass.camera.live.START";
    private final String CAMERA_ACTION_STOP = "com.ingenic.glass.camera.live.STOP";
    private final String CAMERA_ACTION_ERROR = "com.ingenic.glass.camera.live.ERROR";

    // JNI message, consistent with definition in frameworks/av/media/libstagefright/LiveWriter.cpp
    private final int MEDIA_RECORDER_TRACK_INFO_LIVE_SERVER_START = 1900;
    private final int MEDIA_RECORDER_TRACK_INFO_LIVE_SERVER_STOP  = 1901;

    // Sort
    private static final String EFFECT_BG_FROM_GALLERY = "gallery";

    private final CameraErrorCallback mErrorCallback = new CameraErrorCallback();

    private ComboPreferences mPreferences;
    private FrameLayout mPreviewFrameLayout;
    private SurfaceHolder mSurfaceHolder = null;
    private int mSurfaceWidth;
    private int mSurfaceHeight;

    /*device w&h*/
    private int mWindowsWidth;
    private int mWindowsHeight;

    //store current picture
    private TextView mRecordingTimeView;
    private View mBgLearningMessageFrame;

    private boolean mIsVideoCaptureIntent;

    private boolean mOpenCameraFail = false;
    private boolean mCameraDisabled = false;

    private long mStorageSpace;

    private MediaRecorder mMediaRecorder;
    private EffectsRecorder mEffectsRecorder;

    private int mEffectType = EffectsRecorder.EFFECT_NONE;
    private Object mEffectParameter = null;
    private String mEffectUriFromGallery = null;
    private String mPrefVideoEffectDefault;
    private boolean mResetEffect = true;
    public static final String RESET_EFFECT_EXTRA = "reset_effect";

    private boolean mCameraPictureTaking = false;
    private boolean mMediaRecorderRecording = false;
    private long mRecordingStartTime;
    private boolean mRecordingTimeCountsDown = false;
    private RotateLayout mRecordingTimeRect;
    private long mOnResumeTime;
    // The video file that the hardware camera is about to record into
    // (or is recording into.)
    private String mVideoFilename;
    private ParcelFileDescriptor mVideoFileDescriptor;

    private String mCurrentVideoFilename;
    private Uri mCurrentVideoUri;
    private ContentValues mCurrentVideoValues;
    private CamcorderProfile mProfile;

    // The video duration limit. 0 menas no limit.
    private int mMaxVideoDurationInMs;
    private boolean mCaptureTimeLapse = false;
    // Default 0. If it is larger than 0, the camcorder is in time lapse mode.
    private int mTimeBetweenTimeLapseFrameCaptureMs = 0;
    private View mTimeLapseLabel;

    private int mDesiredPreviewWidth;
    private int mDesiredPreviewHeight;

    // The display rotation in degrees. This is only valid when mPreviewing is
    // true.
    private int mDisplayRotation;
    private ContentResolver mContentResolver;
    private LocationManager mLocationManager;
    private final Handler mHandler = new MainHandler();
    private Parameters mParameters;

    // multiple cameras support
    private int mNumberOfCameras;
    private int mCameraId;
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    private int mOrientationCompensation = 0;
    //gallery
    private GestureDetector mGestureDetector;
    private  Thread mStartPreviewThread ;
    private PowerManager pManager;
    private WakeLock  mWakeLock;
	private static CameraLive mInstance = null;

    // check incall state and add tts
    private AudioManager mAudioManager ;
    private Object mLock = new Object();
    private boolean mIsCRUISEBoard = false;
    private boolean mFinished = false;
    private boolean mHasError = false;
    private IInCallService mService = null;
    private boolean mNeedWaitInCallConnected = true;
    private boolean mNeedStartPreview = false;
    private long mListenerId = -1;

    // This Handler is used to post message back onto the main thread of the
    // application
    private class MainHandler extends Handler {
        @Override
	    public void handleMessage(Message msg) {
	    if(DEBUG) Log.d(TAG,"handleMessage in msg.what="+msg.what);
            switch (msg.what) {
	    case UPDATE_RECORD_TIME: {
		updateRecordingTime();
		break;
	    }
	    case START_PREVIEW:{	
		/*get Reord intent from voice system*/
		if(DEBUG) Log.d(TAG,"-------start recording");
		synchronized (mLock) {		    
		    if (mFinished)
			return;
		}
		readVideoPreferences(mAudioManager.getMode() != AudioManager.MODE_IN_CALL);
		setCameraIPUDirect(CameraAppImpl.NO_SCREEN);
		requestStopRecognizeImmediate();
		startVideoRecording(false);
		break;
	    }
	    default:
		if(DEBUG)Log.d(TAG, "Unhandled message: " + msg.what);
		break;
            }
        }
    }

    private BroadcastReceiver mReceiver = null;

    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
	    public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
	    if (action.equals(CAMERA_ACTION_STOP)) {		    
	        finish();
	    }

        }
    }

    private BroadcastReceiver mBatteryReceiver = null;
   private class BatteryBroadcastReceiver extends BroadcastReceiver {
        @Override
	    public void onReceive(Context context, Intent intent) {
	    if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)){
		int currentBatteryVoltage = 
		    intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE,LOWEST_BATTERY_VOLTAGE);
		if (currentBatteryVoltage <= LOWEST_BATTERY_VOLTAGE){
		    Log.e(TAG,"battery is lower :: currentBatteryVoltage= "+currentBatteryVoltage);
		    mHasError = true;
		    finish();
		}
	    }
        }
    }
    
    // We should do no-audio recording before audio mode being set to MODE_IN_CALL,
    // and do with-audio recording after audio mode being set to other mode.
    private IInCallListener.Stub mBinderListener = new IInCallListener.Stub(){
	    @Override
	    public void onPreModeStateChanged(int mode){
		Log.d(TAG,"before set mode to " + mode + " isRecording:"+isRecording());
		if (!isRecording())
		    return;
		if (mode == AudioManager.MODE_IN_CALL) {
		    stopVideoRecording();
		}
	    }

	    @Override
	    public void onPostModeStateChanged(int mode){
		Log.d(TAG,"onPostModeStateChanged :: set mode to " + mode + " isRecording:"+isRecording());
	    }
	};

    private ServiceConnection mServiceConnection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName classname, IBinder obj) {
		if(DEBUG) Log.d(TAG, "onServiceConnected to InCallService");
		mService = IInCallService.Stub.asInterface(obj);
		try {
		    mListenerId = mService.registerInCallListener(IInCallListener.Stub
								  .asInterface(mBinderListener));
		} catch (RemoteException ex) {
		}
	    }
	    public void onServiceDisconnected(ComponentName classname) {
		if (DEBUG) Log.d(TAG, "onServiceDisconnected from InCallService");
		mService = null;
	    }
	};
	

    private String createName(long dateTaken) {
        Date date = new Date(dateTaken);
        SimpleDateFormat dateFormat = new SimpleDateFormat(
							   getString(R.string.live_video_file_name_format));

        return dateFormat.format(date);
    }
    public static CameraLive getMInstance() {
	    return mInstance;
    }
    @Override
	public void onCreate(Bundle icicle) {
	    super.onCreate(icicle);
		CameraLive.mInstance = this;
	if(DEBUG) Log.d(TAG,"onCreate in");
	mIsCRUISEBoard = "cruise".equalsIgnoreCase(Build.BOARD);
	requestWindowFeature(Window.FEATURE_PROGRESS);
	mWindowsWidth = getWindowManager().getDefaultDisplay().getWidth();  
	mWindowsHeight = getWindowManager().getDefaultDisplay().getHeight();
	setContentView(R.layout.main);
	ViewGroup root  = (ViewGroup) findViewById(R.id.main);
	MyView view = (MyView) getLayoutInflater().inflate(R.layout.video_camera, null);
	view.setLayoutParams(new LayoutParams(mWindowsWidth, mWindowsHeight));
	root.addView(view);  
        view.setHandler(mHandler);
	mPreviewFrameLayout = (FrameLayout) view.findViewById(R.id.frame);
	RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mPreviewFrameLayout.getLayoutParams();
	lp.width=mWindowsWidth;
	lp.height=mWindowsWidth*3/4;
	mPreviewFrameLayout.setLayoutParams(lp);
        SurfaceView sv = (SurfaceView) view.findViewById(R.id.camera_preview);
        SurfaceHolder holder = sv.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mRecordingTimeView = (TextView) findViewById(R.id.recording_time);
        mRecordingTimeRect = (RotateLayout) findViewById(R.id.recording_time_rect);
        mTimeLapseLabel = findViewById(R.id.time_lapse_label);
        mBgLearningMessageFrame = findViewById(R.id.bg_replace_message_frame);
        showTimeLapseUI(mCaptureTimeLapse);
	mGestureDetector = new GestureDetector(this, new MySimpleGestureDetectorListener());
	root.setOnTouchListener(this);
	try{
	    int currentBatteryVoltage = Settings.System.getInt(getContentResolver(),
							       "batteryVoltage");
	    if(DEBUG) Log.d(TAG,"currentBatteryVoltage = "+currentBatteryVoltage);
	    if (currentBatteryVoltage <= LOWEST_BATTERY_VOLTAGE){
		Log.e(TAG,"battery is lower :: currentBatteryVoltage= "+currentBatteryVoltage);
		mHasError = true;
		finish();
		return;
	    }
	}catch(SettingNotFoundException  e){
	    e.printStackTrace();
	}
	setAppName(getString(R.string.camera_live_label));
	if (mIsCRUISEBoard) {
	    mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

	    // finish video recording
	    if (VideoActivity.isRecording()) {
		VideoActivity.getMInstance().finish();
	    }
	}

	init();

	if (mIsCRUISEBoard) {
	    if (mOpenCameraFail || mCameraDisabled) {
		    Intent intent = new Intent(CAMERA_ACTION_ERROR);
		    intent.setPackage(PACKAGE_NAME);
		    intent.putExtra("error", getString(R.string.video_record_error));
		    sendBroadcast(intent);
		mHasError = true;
		finish();
		return;
	    }

	   bindService(new Intent("com.ingenic.glass.incall.AudioModeService"), 
			     mServiceConnection, Context.BIND_AUTO_CREATE);

	    if (mAudioManager.getMode() != AudioManager.MODE_IN_CALL) {
		requestPlayTTS(getString(R.string.tts_live_video_record_start));
	    }
	}
    }
    @Override
	public void onStart() {
        super.onStart();
	if(DEBUG) Log.d(TAG,"--onStart in");
    }
    @Override
	public void onStop() {
        super.onStop();
	if(DEBUG) Log.d(TAG,"--onStop in");
    }
	
    private void init(){
        Util.initializeScreenBrightness(getWindow(), getContentResolver());
        mPreferences = new ComboPreferences(this);
        mLocationManager = new LocationManager(this, null);
        CameraSettings.upgradeGlobalPreferences(mPreferences.getGlobal());
        mCameraId = CameraSettings.readPreferredCameraId(mPreferences);
	if(DEBUG) Log.e(TAG,"---mCameraId="+mCameraId);
        //Testing purpose. Launch a specific camera through the intent extras.
        int intentCameraId = Util.getCameraFacingIntentExtras(this);
        if (intentCameraId != -1) {
            mCameraId = intentCameraId;
        }

        mPreferences.setLocalId(this, mCameraId);
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());
        mNumberOfCameras = CameraHolder.instance().getNumberOfCameras();
        mPrefVideoEffectDefault = getString(R.string.pref_video_effect_default);
        // Do not reset the effect if users are switching between back and front
        // cameras.
        mResetEffect = getIntent().getBooleanExtra(RESET_EFFECT_EXTRA, true);
        resetEffect();
	IntentFilter filter =
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        mBatteryReceiver = new BatteryBroadcastReceiver();
	registerReceiver(mBatteryReceiver, filter);
        /*
         * To reduce startup time, we start the preview in another thread.
         * We make sure the preview is started at the end of onCreate.
         */
        mStartPreviewThread = new Thread(new Runnable() {
		public void run() {
		    try {
			if(DEBUG) Log.e(TAG,"---opencamera mCameraId="+mCameraId);
			mCameraDevice = Util.openCamera(CameraLive.this, mCameraId);
			if (mCameraDevice == null){
			    mOpenCameraFail = true;
			}else {
			    mCameraDevice.setErrorCallback(mErrorCallback);
			    mErrorCallback.setHandler(mHandler);
			}
		    } catch (CameraHardwareException e) {
			mOpenCameraFail = true;
		    } catch (CameraDisabledException e) {
			mCameraDisabled = true;
		    }
		}
	    });
	mStartPreviewThread.start();
        Util.enterLightsOutMode(getWindow());
        mContentResolver = getContentResolver();       
        mIsVideoCaptureIntent = isVideoCaptureIntent();
        // Make sure preview is started.
        try {
            mStartPreviewThread.join();
	    if (mIsCRUISEBoard && (mOpenCameraFail || mCameraDisabled)) {
		return;
	    }
            if (mOpenCameraFail) {		
                Util.showErrorAndFinish(this, R.string.cannot_connect_camera);
                return;
            } else if (mCameraDisabled) {
                Util.showErrorAndFinish(this, R.string.camera_disabled);
                return;
            }
        } catch (InterruptedException ex) {
            // ignore
        }

	if (resetEffect()) {
	    mBgLearningMessageFrame.setVisibility(View.GONE);
	}	           
        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        mReceiver = new MyBroadcastReceiver();
        intentFilter.addAction(CAMERA_ACTION_STOP);
        registerReceiver(mReceiver, intentFilter);

        mStorageSpace = Storage.getAvailableSpace();
        mHandler.postDelayed(new Runnable() {
		public void run() {
		    showStorageHint();
		}
	    }, 200);
    }

    private OnScreenHint mStorageHint;

    private void updateAndShowStorageHint() {
        mStorageSpace = Storage.getAvailableSpace();
        showStorageHint();
    }

    private void showStorageHint() {
        String errorMessage = null;
        if (mStorageSpace == Storage.UNAVAILABLE) {
            errorMessage = getString(R.string.no_storage);
        } else if (mStorageSpace == Storage.PREPARING) {
            errorMessage = getString(R.string.preparing_sd);
        } else if (mStorageSpace == Storage.UNKNOWN_SIZE) {
            errorMessage = getString(R.string.access_sd_fail);
        } else if (mStorageSpace < Storage.LOW_STORAGE_THRESHOLD) {
            errorMessage = getString(R.string.spaceIsLow_content);
        }

        if (errorMessage != null) {
            if (mStorageHint == null) {
                mStorageHint = OnScreenHint.makeText(this, errorMessage);
            } else {
                mStorageHint.setText(errorMessage);
            }
            mStorageHint.show();
        } else if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }
    }

    private void readVideoPreferences(boolean hasAudio) {
        // The preference stores values from ListPreference and is thus string type for all values.
        // We need to convert it to int manually.
        String defaultQuality = CameraSettings.getDefaultVideoQuality(mCameraId,
								      getResources().getString(R.string.pref_video_quality_default));
        String videoQuality =
	    mPreferences.getString(CameraSettings.KEY_VIDEO_QUALITY,
				   defaultQuality);
        int quality = Integer.valueOf(videoQuality);

        // Set video quality.
        Intent intent = getIntent();
        if (intent.hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
            int extraVideoQuality =
		intent.getIntExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
            if (extraVideoQuality > 0) {
                quality = CamcorderProfile.QUALITY_HIGH;
            } else {  // 0 is mms.
                quality = CamcorderProfile.QUALITY_LOW;
            }
        }

        // Set video duration limit. The limit is read from the preference,
        // unless it is specified in the intent.
        if (intent.hasExtra(MediaStore.EXTRA_DURATION_LIMIT)) {
            int seconds =
		intent.getIntExtra(MediaStore.EXTRA_DURATION_LIMIT, 0);
            mMaxVideoDurationInMs = 1000 * seconds;
        } else {
            mMaxVideoDurationInMs = CameraSettings.DEFAULT_VIDEO_DURATION;
        }

        // Set effect
        mEffectType = CameraSettings.readEffectType(mPreferences);
        if (mEffectType != EffectsRecorder.EFFECT_NONE) {
            mEffectParameter = CameraSettings.readEffectParameter(mPreferences);
            // Set quality to 480p for effects, unless intent is overriding it
            if (!intent.hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
                quality = CamcorderProfile.QUALITY_480P;
            }
        } else {
            mEffectParameter = null;
        }
        // Read time lapse recording interval.
	if (!hasAudio) {
	    String frameIntervalStr = mPreferences.getString(CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL,getString(R.string.pref_video_time_lapse_frame_interval_default));
	    mTimeBetweenTimeLapseFrameCaptureMs = Integer.parseInt(frameIntervalStr);
	    mCaptureTimeLapse = (mTimeBetweenTimeLapseFrameCaptureMs != 0);
	} else {
	    mTimeBetweenTimeLapseFrameCaptureMs = 0;
	    mCaptureTimeLapse = false;
	}
        // TODO: This should be checked instead directly +1000.
        if (mCaptureTimeLapse) quality += 1000;
        mProfile = CamcorderProfile.get(mCameraId, quality);
	mProfile.fileFormat = OUTPUT_FORMAT_LIVE;
        getDesiredPreviewSize();
    }

    private void writeDefaultEffectToPrefs()  {
        ComboPreferences.Editor editor = mPreferences.edit();
        editor.putString(CameraSettings.KEY_VIDEO_EFFECT,
			 getString(R.string.pref_video_effect_default));
        editor.apply();
    }

    private void getDesiredPreviewSize() {
	if (mCameraDevice == null)
	    return;
        mParameters = mCameraDevice.getParameters();
	mDesiredPreviewWidth = mProfile.videoFrameWidth;
	mDesiredPreviewHeight = mProfile.videoFrameHeight;
	if(DEBUG) Log.d(TAG, "mProfile------mDesiredPreviewWidth=" + mDesiredPreviewWidth +". mDesiredPreviewHeight=" + mDesiredPreviewHeight);
    }

    @Override
    protected void doOnResume() {
    }

    private void setPreviewDisplay(SurfaceHolder holder) {
        try {
            if (effectsActive()) {
                mEffectsRecorder.setPreviewDisplay(mSurfaceHolder,mSurfaceWidth,mSurfaceHeight);
            } else {
                mCameraDevice.setPreviewDisplay(holder);
            }
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("setPreviewDisplay failed", ex);
        }
    }
    private void closeCamera() {
        if(DEBUG) Log.d(TAG, "closeCamera");
        if (mCameraDevice == null) {
            return;
        }
        if (mEffectsRecorder != null) {
            mEffectsRecorder.release();
        }
        mCameraDevice.stopPreview();
        mEffectType = EffectsRecorder.EFFECT_NONE;
        CameraHolder.instance().release();
        mCameraDevice.setErrorCallback(null);
        mCameraDevice = null;
    }

    private void finishRecorderAndCloseCamera() {
        // This is similar to what mShutterButton.performClick() does,
        // but not quite the same.
        stopVideoRecording();
        closeCamera();
    }

    @Override
    protected void onDestroy() {
	super.onDestroy();
	if(DEBUG) Log.d(TAG,"onDestroy in");

	CameraLive.mInstance = null;

	// do it in finish()
        // finishRecorderAndCloseCamera();
        closeVideoFileDescriptor();
	releaseWakeLock();
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }

	if (mBatteryReceiver != null){
	    unregisterReceiver(mBatteryReceiver);
	    mBatteryReceiver = null;
	}

        if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }

	try {
	    if (mService != null) {
		mService.unregisterInCallListener(mListenerId);
		unbindService(mServiceConnection);
	    }
	} catch (RemoteException ex) {
	    ex.printStackTrace();
	}

        mLocationManager.recordLocation(false);
    }

    @Override
    protected void onPause() {
	if(DEBUG) Log.d(TAG,"onPause in");
	super.onPause();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // Make sure we have a surface in the holder before proceeding.
        if (holder.getSurface() == null) {
	    if(DEBUG) Log.d(TAG, "holder.getSurface() == null");
            return;
        }

        if(DEBUG) Log.d(TAG, "surfaceChanged. w=" + w + ". h=" + h);
        mSurfaceHolder = holder;
        mSurfaceWidth = w;
        mSurfaceHeight = h;
        SurfaceView preview = (SurfaceView) findViewById(R.id.camera_preview);

        if (mCameraDevice == null) return;

        if ((Util.getDisplayRotation(this) == mDisplayRotation)
	    && holder.isCreating()) {
            setPreviewDisplay(holder);
        } else {
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        mSurfaceHolder = null;
    }

    private boolean isVideoCaptureIntent() {
        String action = getIntent().getAction();
        return (MediaStore.ACTION_VIDEO_CAPTURE.equals(action));
    }

    private void cleanupEmptyFile() {
        if (mVideoFilename != null) {
            File f = new File(mVideoFilename);
            if (f.length() == 0 && f.delete()) {
		if(DEBUG) Log.d(TAG, "Empty video file deleted: " + mVideoFilename);
                mVideoFilename = null;
            }
        }
    }

    // Prepares media recorder.
    private void initializeRecorder() {
        // If the mCameraDevice is null, then this activity is going to finish
        if (mCameraDevice == null) return;

        if (mSurfaceHolder == null) {
            if(DEBUG)Log.d(TAG, "Surface holder is null. Wait for surface changed.");
            return;
        }

        Intent intent = getIntent();
        Bundle myExtras = intent.getExtras();

        long requestedSizeLimit = 0;
        closeVideoFileDescriptor();
        if (mIsVideoCaptureIntent && myExtras != null) {
            Uri saveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            if (saveUri != null) {
                try {
                    mVideoFileDescriptor =
			mContentResolver.openFileDescriptor(saveUri, "rw");
                    mCurrentVideoUri = saveUri;
                } catch (java.io.FileNotFoundException ex) {
                    // invalid uri
                    Log.e(TAG, ex.toString());
                }
            }
            requestedSizeLimit = myExtras.getLong(MediaStore.EXTRA_SIZE_LIMIT);
        }
        mMediaRecorder = new MediaRecorder();

        // Unlock the camera object before passing it to media recorder.
        mCameraDevice.unlock();
        mMediaRecorder.setCamera(mCameraDevice);
        if (!mCaptureTimeLapse) {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        }
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
	mMediaRecorder.setProfile(mProfile);

        mMediaRecorder.setMaxDuration(mMaxVideoDurationInMs);
        if (mCaptureTimeLapse) {
            mMediaRecorder.setCaptureRate((1000 / (double) mTimeBetweenTimeLapseFrameCaptureMs));
        }

        Location loc = mLocationManager.getCurrentLocation();
        if (loc != null) {
            mMediaRecorder.setLocation((float) loc.getLatitude(),
				       (float) loc.getLongitude());
        }

        // Set output file.
        // Try Uri in the intent first. If it doesn't exist, use our own
        // instead.
        if (mVideoFileDescriptor != null) {
            mMediaRecorder.setOutputFile(mVideoFileDescriptor.getFileDescriptor());
        } else {
            generateVideoFilename(mProfile.fileFormat);
            mMediaRecorder.setOutputFile(mVideoFilename);
        }

        mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());

        // Set maximum file size.
        long maxFileSize = mStorageSpace - Storage.LOW_STORAGE_THRESHOLD;
        if (requestedSizeLimit > 0 && requestedSizeLimit < maxFileSize) {
            maxFileSize = requestedSizeLimit;
        }

        try {
            mMediaRecorder.setMaxFileSize(maxFileSize);
        } catch (RuntimeException exception) {
        }
        int rotation = 0;
        if (mOrientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
            CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];
            if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
                rotation = (info.orientation - mOrientation + 360) % 360;
            } else {  // back-facing camera
                rotation = (info.orientation + mOrientation) % 360;
            }
        }
        mMediaRecorder.setOrientationHint(rotation);

        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "prepare failed for " + mVideoFilename, e);
            releaseMediaRecorder();
            throw new RuntimeException(e);
        }

        mMediaRecorder.setOnErrorListener(this);
        mMediaRecorder.setOnInfoListener(this);
    }
    private void initializeEffectsRecording() {
        if(DEBUG) Log.d(TAG, "initializeEffectsRecording");

        Intent intent = getIntent();
        Bundle myExtras = intent.getExtras();

        long requestedSizeLimit = 0;
        closeVideoFileDescriptor();
        if (mIsVideoCaptureIntent && myExtras != null) {
            Uri saveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            if (saveUri != null) {
                try {
                    mVideoFileDescriptor =
			mContentResolver.openFileDescriptor(saveUri, "rw");
                    mCurrentVideoUri = saveUri;
                } catch (java.io.FileNotFoundException ex) {
                    // invalid uri
                    Log.e(TAG, ex.toString());
                }
            }
            requestedSizeLimit = myExtras.getLong(MediaStore.EXTRA_SIZE_LIMIT);
        }

        mEffectsRecorder.setProfile(mProfile);
        // important to set the capture rate to zero if not timelapsed, since the
        // effectsrecorder object does not get created again for each recording
        // session
        if (mCaptureTimeLapse) {
            mEffectsRecorder.setCaptureRate((1000 / (double) mTimeBetweenTimeLapseFrameCaptureMs));
        } else {
            mEffectsRecorder.setCaptureRate(0);
        }

        // Set output file
        if (mVideoFileDescriptor != null) {
            mEffectsRecorder.setOutputFile(mVideoFileDescriptor.getFileDescriptor());
        } else {
            generateVideoFilename(mProfile.fileFormat);
            mEffectsRecorder.setOutputFile(mVideoFilename);
        }

        // Set maximum file size.
        long maxFileSize = mStorageSpace - Storage.LOW_STORAGE_THRESHOLD;
        if (requestedSizeLimit > 0 && requestedSizeLimit < maxFileSize) {
            maxFileSize = requestedSizeLimit;
        }
        mEffectsRecorder.setMaxFileSize(maxFileSize);
        mEffectsRecorder.setMaxDuration(mMaxVideoDurationInMs);
    }


    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            cleanupEmptyFile();
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
        mVideoFilename = null;
    }

    private void releaseEffectsRecorder() {
        if (mEffectsRecorder != null) {
            cleanupEmptyFile();
            mEffectsRecorder.release();
            mEffectsRecorder = null;
        }
        mVideoFilename = null;
    }

    private void generateVideoFilename(int outputFileFormat) {
        long dateTaken = System.currentTimeMillis();
        String title = createName(dateTaken);
        // Used when emailing.
        String filename = title + convertOutputFormatToFileExt(outputFileFormat);
        String mime = convertOutputFormatToMimeType(outputFileFormat);

	//create path /sdcard/IGlass/Video when it is not exist
        File dir = new File(Storage.DIRECTORY_VIDEO);
        dir.mkdirs();

        mVideoFilename = Storage.DIRECTORY_VIDEO + '/' + filename;
        mCurrentVideoValues = new ContentValues(7);
        mCurrentVideoValues.put(Video.Media.TITLE, title);
        mCurrentVideoValues.put(Video.Media.DISPLAY_NAME, filename);
        mCurrentVideoValues.put(Video.Media.DATE_TAKEN, dateTaken);
        mCurrentVideoValues.put(Video.Media.MIME_TYPE, mime);
        mCurrentVideoValues.put(Video.Media.DATA, mVideoFilename);
        mCurrentVideoValues.put(Video.Media.RESOLUTION,
				Integer.toString(mProfile.videoFrameWidth) + "x" +
				Integer.toString(mProfile.videoFrameHeight));
        if(DEBUG)Log.d(TAG, "New video filename: " + mVideoFilename);
    }

    private void addVideoToMediaStore() {
        if (mVideoFileDescriptor == null) {
            Uri videoTable = Uri.parse("content://media/external/video/media");
            mCurrentVideoValues.put(Video.Media.SIZE,
				    new File(mCurrentVideoFilename).length());
            long duration = SystemClock.uptimeMillis() - mRecordingStartTime;
            if (duration > 0) {
                if (mCaptureTimeLapse) {
                    duration = getTimeLapseVideoLength(duration);
                }
                mCurrentVideoValues.put(Video.Media.DURATION, duration);
            } else {
                Log.w(TAG, "Video duration <= 0 : " + duration);
            }
            try {
                mCurrentVideoUri = mContentResolver.insert(videoTable,
							   mCurrentVideoValues);
                sendBroadcast(new Intent(android.hardware.Camera.ACTION_NEW_VIDEO,
					 mCurrentVideoUri));
            } catch (Exception e) {
                // We failed to insert into the database. This can happen if
                // the SD card is unmounted.
                mCurrentVideoUri = null;
                mCurrentVideoFilename = null;
            } finally {
                if(DEBUG)Log.d(TAG, "Current video URI: " + mCurrentVideoUri);
            }
        }
        mCurrentVideoValues = null;
    }

    private void deleteVideoFile(String fileName) {
        if(DEBUG) Log.d(TAG, "Deleting video " + fileName);
        File f = new File(fileName);
        if (!f.delete()) {
            if(DEBUG)Log.d(TAG, "Could not delete " + fileName);
        }
    }

    // from MediaRecorder.OnErrorListener
    public void onError(MediaRecorder mr, int what, int extra) {
        if(DEBUG) Log.e(TAG, "MediaRecorder error. what=" + what + ". extra=" + extra);
	if (mIsCRUISEBoard) {
		Intent intent = new Intent(CAMERA_ACTION_ERROR);
		intent.setPackage(PACKAGE_NAME);
		intent.putExtra("ErrorInfo", getString(R.string.video_record_error));
		sendBroadcast(intent);
	    stopVideoRecording();
	    mHasError = true;
	    return;
	}
        if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN) {
            // We may have run out of space on the sdcard.
            stopVideoRecording();
            updateAndShowStorageHint();
        } else {
		mHasError = true;
		finish();
	}
    }

    // from MediaRecorder.OnInfoListener
    public void onInfo(MediaRecorder mr, int what, int extra) {
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            if (mMediaRecorderRecording) stopVideoRecording();
        } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
            if (mMediaRecorderRecording) stopVideoRecording();

            // Show the toast.
            Toast.makeText(this, R.string.video_reach_size_limit,
			   Toast.LENGTH_LONG).show();
        } else if (what == MEDIA_RECORDER_TRACK_INFO_LIVE_SERVER_START) {
	    // send start message to Glass.LiveModule
	    Intent intent = new Intent(CAMERA_ACTION_START);
	    intent.setPackage(PACKAGE_NAME);
	    sendBroadcast(intent);
	} else if (what == MEDIA_RECORDER_TRACK_INFO_LIVE_SERVER_STOP) {
	    Log.d(TAG, "[ onInfo ] Live server stop from network");
	    finish();
        }
    }

    /*
     * Make sure we're not recording music playing in the background, ask the
     * MediaPlaybackService to pause playback.
     */
    private void pauseAudioPlayback() {
        // Shamelessly copied from MediaPlaybackService.java, which
        // should be public, but isn't.
        Intent i = new Intent("com.android.music.musicservicecommand");
        i.putExtra("command", "pause");
        sendBroadcast(i);
    }

    // For testing.
    public boolean isRecording() {
        return mMediaRecorderRecording;
    }

    private void startVideoRecording(boolean readPref) {
	if (mMediaRecorderRecording || mFinished) 
	    return;
	requestStopRecognizeImmediate();
	if (readPref)
	    readVideoPreferences(mAudioManager.getMode() != AudioManager.MODE_IN_CALL);

        if(DEBUG) Log.d(TAG, "startVideoRecording");
        updateAndShowStorageHint();
        if (mStorageSpace < Storage.LOW_STORAGE_THRESHOLD) {
            if(DEBUG)Log.d(TAG, "Storage issue, ignore the start request");
            return;
        }

        if (effectsActive()) {
            initializeEffectsRecording();
            if (mEffectsRecorder == null) {
                Log.e(TAG, "Fail to initialize effect recorder");
                return;
            }
        } else {
            initializeRecorder();
            if (mMediaRecorder == null) {
                Log.e(TAG, "Fail to initialize media recorder");
                return;
            }
        }

        pauseAudioPlayback();

        if (effectsActive()) {
            try {
                mEffectsRecorder.startRecording();
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not start effects recorder. ", e);
                releaseEffectsRecorder();
                return;
            }
        } else {
            try {
                mMediaRecorder.start(); // Recording is now started
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not start media recorder. ", e);
                releaseMediaRecorder();
                // If start fails, frameworks will not lock the camera for us.
                mCameraDevice.lock();
                return;
            }
        }
        mMediaRecorderRecording = true;
        mRecordingStartTime = SystemClock.uptimeMillis();
        showRecordingUI(true);
	updateRecordingTime();
    }

    private void showRecordingUI(final boolean recording) {
	if(DEBUG) Log.d(TAG,"showRecordingUI in recording="+recording);
	runOnUiThread(new Runnable(){
		@Override
		public void run() {
			if (recording) {
				mRecordingTimeView.setText("");

				if(mWindowsHeight >=480)
					mRecordingTimeView.setTextSize(50);
				else
					mRecordingTimeView.setTextSize(30);

				mRecordingTimeView.setVisibility(View.VISIBLE);
			} else {
				mRecordingTimeView.setVisibility(View.GONE);
			}
		}
	    });
    }

    private void stopVideoRecording() {
        if(DEBUG) Log.d(TAG, "stopVideoRecording");

        if (mMediaRecorderRecording) {
            boolean shouldAddToMediaStoreNow = false;

            try {
                if (effectsActive()) {
                    // This is asynchronous, so we can't add to media store now because thumbnail
                    // may not be ready. In such case addVideoToMediaStore is called later
                    // through a callback from the MediaEncoderFilter to EffectsRecorder,
                    // and then to the VideoCamera.
                    mEffectsRecorder.stopRecording();
                } else {
                    mMediaRecorder.setOnErrorListener(null);
                    mMediaRecorder.setOnInfoListener(null);
                    mMediaRecorder.stop();
                    shouldAddToMediaStoreNow = true;
                }
                mCurrentVideoFilename = mVideoFilename;
                if(DEBUG)Log.d(TAG, "Setting current video filename: "
			       + mCurrentVideoFilename);
            } catch (RuntimeException e) {
                Log.e(TAG, "stop fail",  e);
                if (mVideoFilename != null) deleteVideoFile(mVideoFilename);
            }

            mMediaRecorderRecording = false;
            showRecordingUI(false);
            if (shouldAddToMediaStoreNow) {
                addVideoToMediaStore();
            }
        }
        // always release media recorder
        if (!effectsActive()) {
            releaseMediaRecorder();
        }
    }
    private static String millisecondToTimeString(long milliSeconds, boolean displayCentiSeconds) {
        long seconds = milliSeconds / 1000; // round down to compute seconds
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long remainderMinutes = minutes - (hours * 60);
        long remainderSeconds = seconds - (minutes * 60);

        StringBuilder timeStringBuilder = new StringBuilder();

        // Hours
        if (hours > 0) {
            if (hours < 10) {
                timeStringBuilder.append('0');
            }
            timeStringBuilder.append(hours);

            timeStringBuilder.append(':');
        }

        // Minutes
        if (remainderMinutes < 10) {
            timeStringBuilder.append('0');
        }
        timeStringBuilder.append(remainderMinutes);
        timeStringBuilder.append(':');

        // Seconds
        if (remainderSeconds < 10) {
            timeStringBuilder.append('0');
        }
        timeStringBuilder.append(remainderSeconds);

        // Centi seconds
        if (displayCentiSeconds) {
            timeStringBuilder.append('.');
            long remainderCentiSeconds = (milliSeconds - seconds * 1000) / 10;
            if (remainderCentiSeconds < 10) {
                timeStringBuilder.append('0');
            }
            timeStringBuilder.append(remainderCentiSeconds);
        }

        return timeStringBuilder.toString();
    }

    private long getTimeLapseVideoLength(long deltaMs) {
        // For better approximation calculate fractional number of frames captured.
        // This will update the video time at a higher resolution.
        double numberOfFrames = (double) deltaMs / mTimeBetweenTimeLapseFrameCaptureMs;
        return (long) (numberOfFrames / mProfile.videoFrameRate * 1000);
    }

    private void updateRecordingTime() {
        if (!mMediaRecorderRecording) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long delta = now - mRecordingStartTime;

        // Starting a minute before reaching the max duration
        // limit, we'll countdown the remaining time instead.
        boolean countdownRemainingTime = (mMaxVideoDurationInMs != 0
					  && delta >= mMaxVideoDurationInMs - 60000);

        long deltaAdjusted = delta;
        if (countdownRemainingTime) {
            deltaAdjusted = Math.max(0, mMaxVideoDurationInMs - deltaAdjusted) + 999;
        }
        String text;

        long targetNextUpdateDelay;
        if (!mCaptureTimeLapse) {
            text = millisecondToTimeString(deltaAdjusted, false);
            targetNextUpdateDelay = 1000;
        } else {
            // The length of time lapse video is different from the length
            // of the actual wall clock time elapsed. Display the video length
            // only in format hh:mm:ss.dd, where dd are the centi seconds.
            text = millisecondToTimeString(getTimeLapseVideoLength(delta), true);
            targetNextUpdateDelay = mTimeBetweenTimeLapseFrameCaptureMs;
        }

        mRecordingTimeView.setText(text);

        if (mRecordingTimeCountsDown != countdownRemainingTime) {
            // Avoid setting the color on every update, do it only
            // when it needs changing.
            mRecordingTimeCountsDown = countdownRemainingTime;

            int color = getResources().getColor(countdownRemainingTime
						? R.color.recording_time_remaining_text
						: R.color.recording_time_elapsed_text);

            mRecordingTimeView.setTextColor(color);
        }

        long actualNextUpdateDelay = targetNextUpdateDelay - (delta % targetNextUpdateDelay);
        mHandler.sendEmptyMessageDelayed(
					 UPDATE_RECORD_TIME, actualNextUpdateDelay);
    }

    private static boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }
    @Override
	public void onEffectsUpdate(int effectId, int effectMsg) {
        if(DEBUG) Log.d(TAG, "onEffectsUpdate in");
        if (effectMsg == EffectsRecorder.EFFECT_MSG_EFFECTS_STOPPED) {
            // Effects have shut down. Hide learning message if any,
            // and restart regular preview.
            mBgLearningMessageFrame.setVisibility(View.GONE);
            checkQualityAndStartPreview();
        } else if (effectMsg == EffectsRecorder.EFFECT_MSG_RECORDING_DONE) {
            addVideoToMediaStore();
        } else if (effectId == EffectsRecorder.EFFECT_BACKDROPPER) {
            switch (effectMsg) {
	    case EffectsRecorder.EFFECT_MSG_STARTED_LEARNING:
		mBgLearningMessageFrame.setVisibility(View.VISIBLE);
		break;
	    case EffectsRecorder.EFFECT_MSG_DONE_LEARNING:
	    case EffectsRecorder.EFFECT_MSG_SWITCHING_EFFECT:
		mBgLearningMessageFrame.setVisibility(View.GONE);
		break;
            }
        }
    }

    @Override
	public synchronized void onEffectsError(Exception exception, String fileName) {
	    // TODO: Eventually we may want to show the user an error dialog, and then restart the
	    // camera and encoder gracefully. For now, we just delete the file and bail out.
	    if (fileName != null && new File(fileName).exists()) {
		deleteVideoFile(fileName);
	    }
	    if (exception instanceof MediaRecorderStopException) {
		Log.w(TAG, "Problem recoding video file. Removing incomplete file.");
		return;
	    }
	    throw new RuntimeException("Error during recording!", exception);
	}

    @Override
	public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if(DEBUG)Log.d(TAG, "onConfigurationChanged in");
    }

    private boolean effectsActive() {
        return (mEffectType != EffectsRecorder.EFFECT_NONE);
    }

    private void checkQualityAndStartPreview() {
        readVideoPreferences(true);
        showTimeLapseUI(mCaptureTimeLapse);
        Size size = mParameters.getPreviewSize();
        if (size.width != mDesiredPreviewWidth
	    || size.height != mDesiredPreviewHeight) {
        }
    }

    private void showTimeLapseUI(boolean enable) {
        if (mTimeLapseLabel != null) {
            mTimeLapseLabel.setVisibility(enable ? View.VISIBLE : View.GONE);
        }
    }
    private boolean resetEffect() {
        if (mResetEffect) {
            String value = mPreferences.getString(CameraSettings.KEY_VIDEO_EFFECT,
						  mPrefVideoEffectDefault);
            if (!mPrefVideoEffectDefault.equals(value)) {
                writeDefaultEffectToPrefs();
                return true;
            }
        }
        mResetEffect = true;
        return false;
    }

    private String convertOutputFormatToMimeType(int outputFileFormat) {
        if (outputFileFormat == MediaRecorder.OutputFormat.MPEG_4) {
            return "video/mp4";
        }
        return "video/mp4";
    }

    private String convertOutputFormatToFileExt(int outputFileFormat) {
        if (outputFileFormat == MediaRecorder.OutputFormat.MPEG_4) {
            return ".mp4";
        }
        return ".mp4";
    }

    private void closeVideoFileDescriptor() {
        if (mVideoFileDescriptor != null) {
            try {
                mVideoFileDescriptor.close();
            } catch (IOException e) {
                Log.e(TAG, "Fail to close fd", e);
            }
            mVideoFileDescriptor = null;
        }
    }

    @Override
    public void finish() {
	boolean finished;
	synchronized (mLock) {		    
	    finished = mFinished;
	    mFinished = true;
	}
	    
	if (!finished) {
	    // 及时closecamera，否则第二次开启会失败，
	    // 原先是在onDestroy中做，而onDestroy调用较晚如果在onDestroy
	    // 调用之前开启录像就会出错，所以把closecamera提前到finish时做
	    finishRecorderAndCloseCamera();
	}
	    
	if (mIsCRUISEBoard) {
		if (mAudioManager.getMode() != AudioManager.MODE_IN_CALL) {
		    String tts = null;
		    if (mHasError) {
			if (mCurrentVideoFilename != null) 
				deleteVideoFile(mCurrentVideoFilename);
			tts = getString(R.string.tts_live_video_record_error);
		    } else 
			tts = getString(R.string.tts_live_video_record_stop);
		    requestPlayTTS(tts);
		    mHasError = false;
		}
	} else if (!finished) {
		Intent intent=new Intent(CameraLive.this, GalleryPicker.class);
		startActivity(intent);	
	}

	if (!finished)
	    super.finish();
    }

    private void setCameraIPUDirect(int mode){
	Log.d(TAG, "set camera_ipu_direct record and restart preview.");
	if (mCameraDevice == null)
		return;
	Parameters p = mCameraDevice.getParameters();
	p.set("preview_mode", mode);
	p.setPreviewSize(mDesiredPreviewWidth,mDesiredPreviewHeight);
	mCameraDevice.setParameters(p);
	addWakeLock();	
    }
    private void addWakeLock(){
	if(mWakeLock==null){
	    pManager = ((PowerManager) getSystemService(POWER_SERVICE));  
	    mWakeLock = pManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,TAG);  
	    mWakeLock.acquire(); 
	}
    }
    private void releaseWakeLock(){
	if(null != mWakeLock){  
	    mWakeLock.release();
	    mWakeLock=null;
	}
    } 
    @Override
    public boolean onTouch(View v, MotionEvent event) {
	
	return mGestureDetector.onTouchEvent(event);	
    }
    class MySimpleGestureDetectorListener extends GestureDetector.SimpleOnGestureListener{
	@Override
	public boolean onSlideDown(boolean fromPhone){
	    finish();
	    return true;
	}
	@Override
	public boolean onTap(boolean fromPhone) {
	    if(mMediaRecorderRecording)
		finish();
	    else
		startVideoRecording(true);
	    return true;
	}
    }
    @Override
	protected void onRecognizeWakeup() {
	    requestUnRegister();
	if(DEBUG)Log.d(TAG, "onRecognizeWakeup");
	finish();
    }
}
