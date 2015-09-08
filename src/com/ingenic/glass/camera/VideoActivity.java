package com.ingenic.glass.camera;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
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
import android.os.IBinder;
import android.os.RemoteException;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
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

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ingenic.glass.camera.ui.RotateLayout;
import com.ingenic.glass.camera.util.Util;
import com.ingenic.glass.camera.gallery.GalleryPicker;
import com.ingenic.glass.camera.gallery.BitmapManager;
import com.ingenic.glass.incall.aidl.IInCallService;
import com.ingenic.glass.incall.aidl.IInCallListener;
import com.ingenic.glass.camera.util.StorageSpaceUtil;

/**
 * The Camcorder activity.
 */
public class VideoActivity extends ActivityBase
    implements /*CameraPreference.OnPreferenceChangedListener,*/
	       SurfaceHolder.Callback,
	       MediaRecorder.OnErrorListener, 
	       MediaRecorder.OnInfoListener,
	       EffectsRecorder.EffectsListener,OnTouchListener{    
    private static final String TAG = "VideoActivity";
    private static final boolean DEBUG = true;	
    private static final int CLEAR_SCREEN_DELAY = 4;
    private static final int UPDATE_RECORD_TIME = 5;
    private static final int SUBSECTION_RECORD = 6;
    static final int START_PREVIEW=1;
    private static final int SCREEN_DELAY = 2 * 60 * 1000;
    // Sort
    private static final String EFFECT_BG_FROM_GALLERY = "gallery";

    private static VideoActivity mInstance = null;

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
    private volatile static boolean mMediaRecorderRecording = false;
    private long mRecordingStartTime;
    private boolean mRecordingTimeCountsDown = false;
    private RotateLayout mRecordingTimeRect;
    private long mOnResumeTime;
    // The video file that the hardware camera is about to record into
    // (or is recording into.)
    private String mVideoFilename;
    private ParcelFileDescriptor mVideoFileDescriptor;

    // Added by dybai_bj 20150702 Add subsection recode configure And Driving recorder mode.
    private static final int CAR_MODE_N = 0;
    private static final int CAR_MODE_Y = 1;
    private final Timer mSubsectionTimer = new Timer();
    private TimerTask mSubsectionTask = null;
    private long mSubsectionTimed = 0; // Unit is minute.
    private int mCarMode = CAR_MODE_N;
    private Queue<File> mCarVideoFileQueue = null;
    private boolean mIsStorageSpaceLess = false;

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

    // check incall state and add tts
    private AudioManager mAudioManager ;
    private IInCallService mService = null;
    private long mListenerId = -1;
    private Object mLock = new Object();
    private boolean mNeedWaitInCallConnected = true;
    private boolean mNeedStartPreview = false;
    private String mTTS = null;
    private boolean mFinished = false;
    private boolean mHasError = false;
    private boolean mNoEnoughStorage = false;

    // This Handler is used to post message back onto the main thread of the
    // application
    private class MainHandler extends Handler {
        @Override
	    public void handleMessage(Message msg) {
	    if(DEBUG) Log.d(TAG,"handleMessage in msg.what="+msg.what);
            switch (msg.what) {
	    case UPDATE_RECORD_TIME: {
//		updateRecordingTime();
	    sendCheckAvailableStrorageMsg();
		checkVideoFileSize();
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
		setCameraIPUDirect(0x01);
		requestStopRecognizeImmediate();
		try {
			startVideoRecording(false);
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(VideoActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
			VideoActivity.this.finish();
		}
		break;
	    }
	    // Added by dybai_bj 20150702 Add subsection recode configure And Driving recorder mode.
	    case SUBSECTION_RECORD: {
	    	try {
		    	stopVideoRecording();
		    	startVideoRecording(false);
	    	} catch (Exception e) {
	    		e.printStackTrace();
	    		Toast.makeText(VideoActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
	    		VideoActivity.this.finish();
	    	}
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
	    Uri uri = intent.getData();
            if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                updateAndShowStorageHint();
                stopVideoRecording();
            } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                updateAndShowStorageHint();
            } else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                // SD card unavailable
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)
		       && uri.toString().equals(GalleryPicker.SCAN_EXTERNAL_URI)) {
                updateAndShowStorageHint();
            }

        }
    }

    private String createName(long dateTaken) {
        Date date = new Date(dateTaken);
        String strFormat = null;

        // Modifyed by dybai_bj 20150703 Add subsection recode configure And Driving recorder mode.
	if (CAR_MODE_Y == this.mCarMode) {
	    if (mCaptureTimeLapse)
		strFormat = getString(R.string.car_video_no_sound_file_name_format);
	    else
		strFormat = getString(R.string.car_video_file_name_format);
	} else if (mCaptureTimeLapse)
	    strFormat = getString(R.string.video_no_sound_file_name_format);

	if (strFormat == null) {
        	strFormat = getString(R.string.video_file_name_format);
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat(strFormat);

        return dateFormat.format(date);
    }

    @Override
	public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
	if(DEBUG) Log.d(TAG,"onCreate in");
	VideoActivity.mInstance = this;

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

	mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
	long storageSpace = StorageSpaceUtil.getAvailableSpace();
	if(storageSpace <= StorageSpaceUtil.LOW_STORAGE_THRESHOLD){
	    mNoEnoughStorage = true;
	    finish();
	    return;
	}
	
	init();

        this.sortCarVideoFileList();
	
	setAppName(getString(R.string.video_recorder_label));

	mAutoRecognize = false;
	if (mOpenCameraFail || mCameraDisabled) {
	    mHasError = true;
	    finish();
	    return;
	}
	if (!bindService(new Intent("com.ingenic.glass.incall.InCallService"), 
			 mServiceConnection, Context.BIND_AUTO_CREATE)) {
	    synchronized (mLock) {
		mNeedWaitInCallConnected = false;
	    }
	}
	if (mAudioManager.getMode() != AudioManager.MODE_IN_CALL) {
	    synchronized (mLock) {						
		mTTS = getString(R.string.tts_video_record_start);
		requestRegister();
		requestPlayTTS(mTTS);
	    }
	}
    }

    /**
     * Add subsection recode configure And Driving recorder mode.
     * @author Added by dybai_bj 20150703
     */
	private void readSubsectionConfig() {
			SharedPreferences sharedPreferences = this.getSharedPreferences(
					"com.ingenic.glass.video_record_preferences", Activity.MODE_PRIVATE);
			this.mCarMode = sharedPreferences.getInt("CarMode", CAR_MODE_N);
			this.mSubsectionTimed = sharedPreferences.getInt("SubsectionTimed", 10); // Unit is minute,default 10min.
	
			if (DEBUG) Log.d(TAG, "CarMode = " + this.mCarMode
					+ ", SubsectionTimed = " + this.mSubsectionTimed);
	
			//comment by hky@sctek.cn 20150720
//			if (this.mSubsectionTimed > 0) {
//				this.mSubsectionTask = new SubsectionTimerTask();
//				// Start the subsection video recode task.
//				this.mSubsectionTimer.schedule(this.mSubsectionTask
//						// Unit is millisecond.
//						, this.mSubsectionTimed*60*1000
//						, this.mSubsectionTimed*60*1000);
//			}
	}

    /**
     * Add subsection recode configure And Driving recorder mode.
     * @author Added by dybai_bj 20150703
     */
	private void sortCarVideoFileList() {
	    File carVideoDirectory = new File(Storage.DIRECTORY_VIDEO);
	    if (!carVideoDirectory.exists()) {
		carVideoDirectory.mkdirs();
	    } else if (!carVideoDirectory.isDirectory()) {
		carVideoDirectory.delete();
		carVideoDirectory.mkdirs();
	    }

	      // Filter the car video files.
	    File [] files = carVideoDirectory.listFiles(new FilenameFilter() {
		    @Override
			public boolean accept(File dir, String name) {
			if (name.startsWith("CarVideo_")) {
			    return true;
			}
			return false;
		    }
		});

	      // Sort the car video file list, according to the ascending order.
	    Arrays.sort(files, new Comparator<File>() {
		    @Override
			public int compare(File f1, File f2) {
			long result = f1.lastModified() - f2.lastModified();
			if (result < 0) {
			    return -1;
			} else if (result > 0) {
			    return 1;
			}
			return 0;
		    }
		});
	    this.mCarVideoFileQueue = new LinkedList<File>();
	    for (int i = 0; i < files.length; i++) {
		this.mCarVideoFileQueue.add(files[i]);
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

        /*
         * To reduce startup time, we start the preview in another thread.
         * We make sure the preview is started at the end of onCreate.
         */
        mStartPreviewThread = new Thread(new Runnable() {
		public void run() {
		    try {
			if(DEBUG) Log.e(TAG,"---opencamera mCameraId="+mCameraId);
			mCameraDevice = Util.openCamera(VideoActivity.this, mCameraId);
			if (mCameraDevice == null)
			    mOpenCameraFail = true;
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
	    if ((mOpenCameraFail || mCameraDisabled)) {
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
        intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addDataScheme("file");
        mReceiver = new MyBroadcastReceiver();
        registerReceiver(mReceiver, intentFilter);
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
        // Set video quality.
	int quality = CamcorderProfile.QUALITY_HIGH;
        Intent intent = getIntent();

        // Set video duration limit. The limit is read from the preference,
        // unless it is specified in the intent.
        //if is cruise board set it to mSubsecttionTimed,add by hky@sctek.cn 20150721
	readSubsectionConfig();
	mMaxVideoDurationInMs = (int)mSubsectionTimed*60*1000;

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

	VideoActivity.mInstance = null;
	
	// do it in finish()
        // finishRecorderAndCloseCamera();
        closeVideoFileDescriptor();
        releaseWakeLock();

        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
        if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }
	if(mLocationManager != null)
	    mLocationManager.recordLocation(false);

	  // Stop the subsection video recode task.
	if (this.mSubsectionTimed > 0) {
	    this.mSubsectionTimer.cancel();
	}

	try {
		if (mService != null) {
			mService.unregisterInCallListener(mListenerId);
			unbindService(mServiceConnection);
		}
	} catch (RemoteException ex) {
		ex.printStackTrace();
	}
	if(DEBUG) Log.d(TAG,"onDestroy out");
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
            createAndChangVideoFileMode();
            mMediaRecorder.setOutputFile(mVideoFilename);
        }

        mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());

        // Set maximum file size.set maxFileSize to mStorageSpace rather than mStorageSpace - Storage.LOW_STORAGE_THRESHOLD by hky@sctek.cn 20150724
//        long maxFileSize = mStorageSpace - Storage.LOW_STORAGE_THRESHOLD;
        long maxFileSize = mStorageSpace;
        if (requestedSizeLimit > 0 && requestedSizeLimit < maxFileSize) {
            maxFileSize = requestedSizeLimit;
        }
        
        Log.e(TAG, "-------------maxFileSize:" + maxFileSize);
        //dont set maxFileSize because storage check will make by time.
//        try {
//            mMediaRecorder.setMaxFileSize(maxFileSize);
//        } catch (RuntimeException exception) {
//        }

	this.mIsStorageSpaceLess = true;
	this.checkVideoFileSize();
	  //added by hky@sctek.cn 20150722 return when no enough space.
	if(mMediaRecorder == null)
	    return;

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
			  createAndChangVideoFileMode();
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
    
    private void createAndChangVideoFileMode() {
    	
    	try {
			File videoFile = new File(mVideoFilename);
				if(!videoFile.exists()) {
					videoFile.createNewFile();
					Runtime.getRuntime().exec("chmod 777 " + videoFile.getAbsolutePath());
				}	
		} catch (Exception e) {
			e.printStackTrace();
		}
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
        if(!dir.exists())
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
	mHasError = true;
	finish();
	return;
    }

    // from MediaRecorder.OnInfoListener
    public void onInfo(MediaRecorder mr, int what, int extra) {
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            if (mMediaRecorderRecording) {
            	try {
    		    	stopVideoRecording();
    		    	startVideoRecording(true);
    	    	} catch (Exception e) {
    	    		e.printStackTrace();
    	    		Toast.makeText(VideoActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
    	    		VideoActivity.this.finish();
    	    	}
            }
        } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
        	
            if (mMediaRecorderRecording) {
            	try {
    		    	stopVideoRecording();
    		    	startVideoRecording(true);
    	    	} catch (Exception e) {
    	    		e.printStackTrace();
    	    		Toast.makeText(VideoActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
    	    		VideoActivity.this.finish();
    	    	}
            }
            // Show the toast.
            Toast.makeText(this, R.string.video_reach_size_limit,
			   Toast.LENGTH_LONG).show();
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

    public static boolean isRecording() {
        return mMediaRecorderRecording;
    }

    private void startVideoRecording(boolean readPref) {
	if (mMediaRecorderRecording || mFinished) 
	    return;
	requestStopRecognizeImmediate();

	if (readPref)
	    readVideoPreferences(mAudioManager.getMode() != AudioManager.MODE_IN_CALL);

        if(DEBUG) Log.d(TAG, "startVideoRecording mCaptureTimeLapse:"+mCaptureTimeLapse);
        updateAndShowStorageHint();
        //comment by hky 20150720
//        if (mStorageSpace < Storage.LOW_STORAGE_THRESHOLD) {
//            if(DEBUG)Log.d(TAG, "Storage issue, ignore the start request");
//            return;
//        }

	synchronized (mLock) {
	    if (mNeedWaitInCallConnected || mTTS != null) {
		if (mNeedWaitInCallConnected)
		    Log.w(TAG, "Need to wait BluetoothHfDevice connected.");
		else
		    Log.w(TAG, "Need to wait tts play end.");
		mNeedStartPreview = true;
		return;
	    }
	    mNeedStartPreview = false;
	}

	Log.e(TAG, "initializeRecorder ...");
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
                //finish the activity when error,by hky@sctek.cn 20150720
                finish();
                return;
            }
        }

        pauseAudioPlayback();

	Log.e(TAG, "startRecording ...");
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
//        showRecordingUI(true);
//	updateRecordingTime();
        sendCheckAvailableStrorageMsg();

	Intent in = new Intent("cn.ingenic.glass.ACTION_MEDIA_VIDEO_START");
	in.setPackage("com.smartglass.device");
	sendBroadcast(in);
    }

    private void sendCheckAvailableStrorageMsg() {
    	mHandler.removeMessages(UPDATE_RECORD_TIME);
    	mHandler.sendEmptyMessageDelayed(UPDATE_RECORD_TIME, 2000);
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
            ContentResolver resolver = getContentResolver();

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
		if (!this.mCarVideoFileQueue.offer(new File(mCurrentVideoFilename))) {
		    if (DEBUG) Log.d(TAG, "WARNING: Car video file added to queue failure!");
		}
            } catch (RuntimeException e) {
                Log.e(TAG, "stop fail",  e);
                if (mVideoFilename != null) deleteVideoFile(mVideoFilename);
            }
            mMediaRecorderRecording = false;
//            showRecordingUI(false);
            if (shouldAddToMediaStoreNow) {
//                addVideoToMediaStore();
//                BitmapManager.instance().getThumbnail(resolver, ContentUris.parseId(mCurrentVideoUri),
//                		Video.Thumbnails.MINI_KIND, null, true);
            	creatAndSaveVideoThumbnail(mCurrentVideoFilename);
            }

	    Intent i = new Intent("cn.ingenic.glass.ACTION_MEDIA_VIDEO_FINISH");
	    i.setPackage("com.smartglass.device");
	    sendBroadcast(i); 
        }

	mNeedStartPreview = false;

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

	runOnUiThread(new Runnable(){
	    @Override
	    public void run() {
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
	});
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
	     // ������closecamera������������������������������������
	     // ������������onDestroy������������onDestroy������������������������onDestroy
	     // ������������������������������������������������closecamera���������finish������
	     finishRecorderAndCloseCamera();
	}
	if (mAudioManager.getMode() != AudioManager.MODE_IN_CALL) {
	    synchronized (mLock) {	
		if(mNoEnoughStorage)
		    mTTS = getString(R.string.tts_video_record_no_storage);
		else {
		    if (mHasError)
			mTTS = getString(R.string.tts_video_record_error);
		    else
			mTTS = getString(R.string.tts_video_record_stop);
		}
		requestPlayTTS(mTTS);
		mHasError = false;
		mNoEnoughStorage = false;
	    }
	}
	
	mHandler.removeMessages(UPDATE_RECORD_TIME);
	
	if (!finished)
	    super.finish();
    }

    private void setCameraIPUDirect(int mode){
	if(DEBUG)Log.d(TAG, "set camera_ipu_direct record and restart preview.");
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
	    mWakeLock = pManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, TAG);  
	    mWakeLock.acquire(); 
	}
    }
    private void releaseWakeLock(){
	if(null != mWakeLock){  
	    mWakeLock.release();
	    mWakeLock=null;
	}
    } 

    private void checkVideoFileSize () {
	if (this.mIsStorageSpaceLess) {
        	long storageSpace = StorageSpaceUtil.getAvailableSpace();
        	long estimateVideoFileSize =
            		VideoActivity.this.mSurfaceWidth * VideoActivity.this.mSurfaceHeight * 3 / 2 / 10 // A frame size
            		* 20 // 1 second
            		* VideoActivity.this.mSubsectionTimed*60; // A video file subsection time.
	        if (DEBUG) Log.d(TAG, "storageSpace=" + storageSpace);
	        if (storageSpace < estimateVideoFileSize) {
	        	this.mIsStorageSpaceLess = true;
	        	 if (storageSpace <= 0) {
                 this.recyclingVideoSpace();
	        	 }
	        } else {
	        	this.mIsStorageSpaceLess = false;
	        }
    	}
    }

    private void recyclingVideoSpace () {
	  // In car mode, if there is no enough storage space, then delete the earliest video files.
	if (CAR_MODE_Y == this.mCarMode) {
	    File videoFile = null;
	    while (null != (videoFile = this.mCarVideoFileQueue.poll())) {
		if (videoFile.exists() && videoFile.delete()) {
		    if (DEBUG) Log.d(TAG, "WARNING: Car Mode, No enough storage space! "
				     + "Delete file: " + videoFile.getAbsolutePath() + " success!");
		    break;
		}
		if (DEBUG) Log.d(TAG, "WARNING: Car Mode, No enough storage space! "
				 + "Delete file: " + videoFile.getAbsolutePath() + " failure!");
	    }
	        	
	    if(videoFile == null) {
		mNoEnoughStorage = true;
		if(mMediaRecorderRecording) {
		    this.finish();
		}
		else {
		    mCameraDevice.lock();
		    releaseMediaRecorder();
		    return;
		}
	    }
	} else {
	      //set mNoEnoughStorage to true when there is no enough storage add by hky@sctek.cn 20150720
	    mNoEnoughStorage = true;
	    if(mMediaRecorderRecording) {
		this.finish();
	    }
	    else {
		mCameraDevice.lock();
		releaseMediaRecorder();
		return;
	    }
//	        	throw new RuntimeException(getResources().getString(R.string.not_enough_space));
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

    @Override
    protected void onTTSPlayEnd(String tts) {
	synchronized (mLock) {
		Log.d(TAG, "onTTSPlayEnd "+tts+" "+mTTS+" "+getString(R.string.tts_video_record_start)+" "+mNeedStartPreview);
	    if (mTTS != null
		&& mTTS.equals(tts)
		&& mTTS.equals(getString(R.string.tts_video_record_start))) {
		mTTS = null;
		if (mNeedStartPreview) {
		    startVideoRecording(true);	
		}
	    } else
		mTTS = null;
	}
    }

    @Override
    protected void onRecognizePreempted() {
	synchronized (mLock) {
	    if (mTTS != null
		&& mTTS.equals(getString(R.string.tts_video_record_start))) {
		mTTS = null;
		if (mNeedStartPreview) {
		    startVideoRecording(true);	
		}
	    } else
		mTTS = null;
	}	    
    }

    public static VideoActivity getMInstance() {
		return mInstance;
	}
	private class SubsectionTimerTask extends TimerTask {

		@Override
		public void run() {
			VideoActivity.this.mHandler.sendEmptyMessage(SUBSECTION_RECORD);
		}
    } 

    // We should do no-audio recording before audio mode being set to MODE_IN_CALL,
    // and do with-audio recording after audio mode being set to other mode.
    private IInCallListener.Stub mBinderListener = new IInCallListener.Stub(){
	    @Override
	    public void onPreModeStateChanged(int mode){
		    if (DEBUG) Log.d(TAG,"before set mode to " + mode + " isRecording:"+isRecording());
		    if (!isRecording())
			    return;
		    if (mode == AudioManager.MODE_IN_CALL) {
			    stopVideoRecording();
			    readVideoPreferences(false);
			    startVideoRecording(false);		    
		    }
	    }

	    @Override
	    public void onPostModeStateChanged(int mode){
		    if (DEBUG) Log.d(TAG,"after set mode to " + mode + " isRecording:"+isRecording());
		    if (!isRecording())
			    return;
		    if (mode != AudioManager.MODE_IN_CALL) {
			    stopVideoRecording();
			    startVideoRecording(true);		    		    
		    }
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
		synchronized (mLock) {
			mNeedWaitInCallConnected = false;
			if (mNeedStartPreview) {
				startVideoRecording(true);	
			}
		}
	    }
	    public void onServiceDisconnected(ComponentName classname) {
		if (DEBUG) Log.d(TAG, "onServiceDisconnected from InCallService");
		mService = null;
	    }
	};
	
	/*
	 *create video thumbnail and save it to /apache/GlassData/.videothumbnail
	 *by hky@sctek.cn 20150804
	 */
	public void creatAndSaveVideoThumbnail(String videoPath) {
		try {
			
			Bitmap bitmap = Thumbnail.createVideoThumbnail(videoPath, 200);
			
			if(bitmap == null)
				return;
			
			String savePath = videoPath.replace("vedios", ".videothumbnails");

			File thumbfile = new File(savePath);
			File videothumbnails = new File(thumbfile.getParent());
			if (!videothumbnails.exists()) {
				videothumbnails.mkdirs();
			}

			thumbfile.delete();
			thumbfile.createNewFile();
			thumbfile.setReadable(true, false);
			thumbfile.setWritable(true, false);
			thumbfile.setExecutable(true, false);
			
			FileOutputStream fos = new FileOutputStream(savePath);
			bitmap.compress(CompressFormat.JPEG, 100, fos);
			fos.flush();
			fos.close();
		
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
