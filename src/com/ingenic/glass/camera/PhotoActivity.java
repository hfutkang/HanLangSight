/*
 */

package com.ingenic.glass.camera;

import android.app.Activity;

import com.ingenic.glass.camera.ui.RotateLayout;
import com.ingenic.glass.camera.util.Exif;
import com.ingenic.glass.camera.util.Util;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.location.Location;
import android.media.CamcorderProfile;
import android.media.CameraProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.os.Process;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.Animation.AnimationListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PagedView;
import android.widget.GestureDetector;
import android.widget.GestureDetector.SimpleOnGestureListener;
import android.database.Cursor;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import android.filterpacks.videosink.MediaRecorderStopException;
import android.provider.MediaStore.Images;

import com.ingenic.glass.camera.R.anim;
import com.ingenic.glass.camera.gallery.BitmapManager;
import com.ingenic.glass.camera.gallery.MovieActivity;;
import com.ingenic.glass.camera.gallery.GalleryPicker;

import java.util.ArrayList;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.view.View.OnTouchListener;
import android.content.Intent;
/**
 * The Camcorder activity.
 */
public class PhotoActivity extends ActivityBase
    implements /*CameraPreference.OnPreferenceChangedListener,*/
	       SurfaceHolder.Callback,OnTouchListener{
    
    private static final String TAG = "TakePicture";
    private static final boolean DEBUG = false;	
    static final int START_PREVIEW=1;
    static final int SORT_DESCENDING=2;
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
    private ImageView mAnimationView;

    //store current picture
    private Bitmap mCurrentBitmap;
    private TextView mRecordingTimeView;
    private View mBgLearningMessageFrame;
    private boolean mOpenCameraFail = false;
    private boolean mCameraDisabled = false;

    private long mStorageSpace;
    private EffectsRecorder mEffectsRecorder;

    private int mEffectType = EffectsRecorder.EFFECT_NONE;
    private Object mEffectParameter = null;
    private String mEffectUriFromGallery = null;
    private String mPrefVideoEffectDefault;
    private boolean mResetEffect = true;
    public static final String RESET_EFFECT_EXTRA = "reset_effect";
    private boolean mCameraPictureTaking = false;
    // The video file that the hardware camera is about to record into
    // (or is recording into.)
    private CamcorderProfile mProfile;

    // The video duration limit. 0 menas no limit.
    private int mMaxVideoDurationInMs;
    private boolean mCaptureTimeLapse = false;
    // Default 0. If it is larger than 0, the camcorder is in time lapse mode.
    private int mTimeBetweenTimeLapseFrameCaptureMs = 0;
    private View mTimeLapseLabel;

    private int mDesiredPreviewWidth;
    private int mDesiredPreviewHeight;

    boolean mPausing = false;
    boolean mPreviewing = false; // True if preview is started.
    // The display rotation in degrees. This is only valid when mPreviewing is
    // true.
    private int mDisplayRotation;
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
    private Thread mStartPreviewThread;
    private boolean mFirstStartPreview=true;
    // voice
    private final String COMMAND_TAKE_PICTURE="拍照";
    private final String COMMAND_PHOTOS="查看照片";
    private final String COMMAND_EXIT="退出";
    private final String [] CAMERA_VOICE_CMDS={COMMAND_TAKE_PICTURE,COMMAND_PHOTOS,COMMAND_EXIT};
    private ArrayList mCameraCmdList=new ArrayList();
    // This Handler is used to post message back onto the main thread of the
    // application
    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
	    if(DEBUG) Log.d(TAG,"handleMessage in msg.what="+msg.what);
            switch (msg.what) {
	    case START_PREVIEW:{
		startPreview();
		mFirstStartPreview=false;	
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
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
	if(DEBUG) Log.d(TAG,"onCreate in");
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
        mTimeLapseLabel = findViewById(R.id.time_lapse_label);
        mBgLearningMessageFrame = findViewById(R.id.bg_replace_message_frame);
        showTimeLapseUI(mCaptureTimeLapse);
	for(int i=0;i<CAMERA_VOICE_CMDS.length;i++)
	    mCameraCmdList.add(CAMERA_VOICE_CMDS[i]);
	setRecognizeCommands(mCameraCmdList);
	setUseTimeout(false); 
	mGestureDetector = new GestureDetector(this, new MySimpleGestureDetectorListener());
	root.setOnTouchListener(this);
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
                    mCameraDevice = Util.openCamera(PhotoActivity.this, mCameraId);
                    readVideoPreferences();
                } catch (CameraHardwareException e) {
                    mOpenCameraFail = true;
                } catch (CameraDisabledException e) {
                    mCameraDisabled = true;
                }
            }
        });
	mStartPreviewThread.start();
        Util.enterLightsOutMode(getWindow());
        // Make sure preview is started.
        try {
            mStartPreviewThread.join();
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

    private void readVideoPreferences() {
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
        String frameIntervalStr = mPreferences.getString(
                CameraSettings.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL,
                getString(R.string.pref_video_time_lapse_frame_interval_default));
        mTimeBetweenTimeLapseFrameCaptureMs = Integer.parseInt(frameIntervalStr);

        mCaptureTimeLapse = (mTimeBetweenTimeLapseFrameCaptureMs != 0);
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
        mParameters = mCameraDevice.getParameters();
	mDesiredPreviewWidth = mProfile.videoFrameWidth;
	mDesiredPreviewHeight = mProfile.videoFrameHeight;
	if(DEBUG) Log.d(TAG, "mProfile------mDesiredPreviewWidth=" + mDesiredPreviewWidth +". mDesiredPreviewHeight=" + mDesiredPreviewHeight);
    }

    @Override
    protected void doOnResume() {
	if(DEBUG) Log.d(TAG,"doOnResume in");
	init();
        if (mOpenCameraFail || mCameraDisabled) return;

        mPausing = false;

        if (!mPreviewing && !mFirstStartPreview) {
            if (resetEffect()) {
                mBgLearningMessageFrame.setVisibility(View.GONE);
            }
	    setCameraIPUDirect("preview");

        }
        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter =
                new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addDataScheme("file");
        mReceiver = new MyBroadcastReceiver();
        registerReceiver(mReceiver, intentFilter);
        mStorageSpace = Storage.getAvailableSpace();

        mHandler.postDelayed(new Runnable() {
		public void run() {
		    showStorageHint();
		}
	    }, 200);
    }

    private void setPreviewDisplay(SurfaceHolder holder) {
        try {
            if (effectsActive()) {
                mEffectsRecorder.setPreviewDisplay(
                        mSurfaceHolder,
                        mSurfaceWidth,
                        mSurfaceHeight);
            } else {
                mCameraDevice.setPreviewDisplay(holder);
            }
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("setPreviewDisplay failed", ex);
        }
    }

    private void startPreview() {
        if(DEBUG) Log.d(TAG, "startPreview");

        mCameraDevice.setErrorCallback(mErrorCallback);
        if (mPreviewing == true) {
	    return;
        }

        mDisplayRotation = Util.getDisplayRotation(this);
        int orientation = Util.getDisplayOrientation(mDisplayRotation, mCameraId);
        mCameraDevice.setDisplayOrientation(orientation);
        setCameraParameters();

        if (!effectsActive()) {
            setPreviewDisplay(mSurfaceHolder);
            try {
                mCameraDevice.startPreview();
            } catch (Throwable ex) {
                closeCamera();
                throw new RuntimeException("startPreview failed", ex);
            }
        } else {
            initializeEffectsPreview();
            mEffectsRecorder.startPreview();
        }

        mPreviewing = true;
    }

    private void closeCamera() {
        if(DEBUG) Log.d(TAG, "closeCamera");
        if (mCameraDevice == null) {
            return;
        }
        if (mEffectsRecorder != null) {
            mEffectsRecorder.release();
        }
        mEffectType = EffectsRecorder.EFFECT_NONE;
        CameraHolder.instance().release();
        mCameraDevice.setErrorCallback(null);
        mCameraDevice = null;
        mPreviewing = false;
    }
    @Override
    protected void onPause() {
	if(DEBUG) Log.d(TAG,"onPause in");
        super.onPause();
        mPausing = true;
        closeCamera();
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
        if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
        }
        mLocationManager.recordLocation(false);
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
        if (mPausing) {
            return;
        }

        if (mCameraDevice == null) return;

        if (mPreviewing && (Util.getDisplayRotation(this) == mDisplayRotation)
                && holder.isCreating()) {
            setPreviewDisplay(holder);
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        mSurfaceHolder = null;
    }
    private void initializeEffectsPreview() {
        if(DEBUG) Log.d(TAG, "initializeEffectsPreview");
        // If the mCameraDevice is null, then this activity is going to finish
        if (mCameraDevice == null) return;

        CameraInfo info = CameraHolder.instance().getCameraInfo()[mCameraId];

        mEffectsRecorder = new EffectsRecorder(this);

        // TODO: Confirm none of the foll need to go to initializeEffectsRecording()
        // and none of these change even when the preview is not refreshed.
        mEffectsRecorder.setCamera(mCameraDevice);
        mEffectsRecorder.setCameraFacing(info.facing);
        mEffectsRecorder.setProfile(mProfile);
        // See android.hardware.Camera.Parameters.setRotation for
        // documentation.
        int rotation = 0;
        if (mOrientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
            rotation = mOrientationCompensation % 360;
        }
        mEffectsRecorder.setOrientationHint(rotation);

        mEffectsRecorder.setPreviewDisplay(
                mSurfaceHolder,
                mSurfaceWidth,
                mSurfaceHeight);

        if (mEffectType == EffectsRecorder.EFFECT_BACKDROPPER &&
                ((String) mEffectParameter).equals(EFFECT_BG_FROM_GALLERY)) {
            mEffectsRecorder.setEffect(mEffectType, mEffectUriFromGallery);
        } else {
            mEffectsRecorder.setEffect(mEffectType, mEffectParameter);
        }
    }
    private static boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }

    private void setCameraParameters() {
        mParameters = mCameraDevice.getParameters();

        mParameters.setPreviewSize(mDesiredPreviewWidth, mDesiredPreviewHeight);
        mParameters.setPreviewFrameRate(mProfile.videoFrameRate);

        // Set flash mode.
        String flashMode = mPreferences.getString(
                CameraSettings.KEY_VIDEOCAMERA_FLASH_MODE,
                getString(R.string.pref_camera_video_flashmode_default));
        List<String> supportedFlash = mParameters.getSupportedFlashModes();
        if (isSupported(flashMode, supportedFlash)) {
            mParameters.setFlashMode(flashMode);
        } else {
            flashMode = mParameters.getFlashMode();
            if (flashMode == null) {
                flashMode = getString(
                        R.string.pref_camera_flashmode_no_flash);
            }
        }

        // Set white balance parameter.
        String whiteBalance = mPreferences.getString(
                CameraSettings.KEY_WHITE_BALANCE,
                getString(R.string.pref_camera_whitebalance_default));
        if (isSupported(whiteBalance,
                mParameters.getSupportedWhiteBalance())) {
            mParameters.setWhiteBalance(whiteBalance);
        } else {
            whiteBalance = mParameters.getWhiteBalance();
            if (whiteBalance == null) {
                whiteBalance = Parameters.WHITE_BALANCE_AUTO;
            }
        }
        // Set continuous autofocus.
        List<String> supportedFocus = mParameters.getSupportedFocusModes();
        if (isSupported(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO, supportedFocus)) {
            mParameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }

        mParameters.setRecordingHint(true);

        // Enable video stabilization. Convenience methods not available in API
        // level <= 14
        String vstabSupported = mParameters.get("video-stabilization-supported");
        if ("true".equals(vstabSupported)) {
            mParameters.set("video-stabilization", "true");
        }

        // Set picture size.
        String pictureSize = mPreferences.getString(
                CameraSettings.KEY_PICTURE_SIZE, null);
	if(DEBUG)Log.d(TAG,"pictureSize="+pictureSize);
        if (pictureSize == null) {
            CameraSettings.initialCameraPictureSize(this, mParameters);
        } else {
            List<Size> supported = mParameters.getSupportedPictureSizes();
	    for(Size s:supported){
		if(DEBUG)Log.d(TAG,"s="+s);
	    }
            CameraSettings.setCameraPictureSize(
                    pictureSize, supported, mParameters);
        }

        // Set JPEG quality.
        int jpegQuality = CameraProfile.getJpegEncodingQualityParameter(mCameraId,
                CameraProfile.QUALITY_HIGH);
        mParameters.setJpegQuality(jpegQuality);


	//jim add for set ipu parameters.
	Log.e(TAG, "set camera_ipu_direct preview.");
	mParameters.set("camera_ipu_direct", "preview");

		// Added by dybai_bj 20150625 Config photo resolution
		SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
		SAXParser saxParser = null;
		TakePictureSAX takePictureSAX = new TakePictureSAX();
		try {
			saxParser = saxParserFactory.newSAXParser();
			saxParser.parse(new File("/system/etc/takepicture_profiles.xml"), takePictureSAX);
		} catch (Exception e) {
			e.printStackTrace();
		}
		TakePictureProfile takePictureProfile = takePictureSAX.getTakePicturePorfileList().get(0);
		mParameters.setPictureSize(Integer.parseInt(takePictureProfile.getWidth())
				, Integer.parseInt(takePictureProfile.getHeight()));

        mCameraDevice.setParameters(mParameters);
        // Keep preview size up to date.
        mParameters = mCameraDevice.getParameters();
    }
    @Override
	public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if(DEBUG)Log.d(TAG, "onConfigurationChanged in");
    }

    private boolean effectsActive() {
        return (mEffectType != EffectsRecorder.EFFECT_NONE);
    }
    private void showTimeLapseUI(boolean enable) {
        if (mTimeLapseLabel != null) {
            mTimeLapseLabel.setVisibility(enable ? View.VISIBLE : View.GONE);
        }
    }

    private final class JpegPictureCallback implements PictureCallback {
        Location mLocation;

        public JpegPictureCallback(Location loc) {
            mLocation = loc;
        }

        @Override
        public void onPictureTaken(byte [] jpegData, android.hardware.Camera camera) {
            storeImage(jpegData, mLocation);
	      //takePictureAnimation(jpegData);
	    mCameraPictureTaking = false;
        }
    }

    private void takePictureAnimation(byte [] jpegData) {
	if(mAnimationView == null){
	    mAnimationView= new ImageView(this);
	      //mAnimationView.setBackgroundColor(0xff000000);
	    mAnimationView.setBackgroundResource(R.drawable.rectangle);
	    mAnimationView.setScaleType(ImageView.ScaleType.FIT_XY);
	    mAnimationView.setLayoutParams(new android.view.ViewGroup.LayoutParams(
					       android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
					       android.view.ViewGroup.LayoutParams.MATCH_PARENT));
	    mPreviewFrameLayout.addView(mAnimationView);
	    mAnimationView.setVisibility(View.INVISIBLE);
	}
	  //take a bitmap 
	  // int orientation = Exif.getOrientation(jpegData);
	  // mCurrentBitmap = Util.makeBitmap(jpegData, 50 * 1024);
	  // mCurrentBitmap = Util.rotate(mCurrentBitmap, orientation);
	  //add a animation ^_^
	mAnimationView.setVisibility(View.VISIBLE);
	  //mAnimationView.setImageBitmap(mCurrentBitmap);
	AnimationSet set = new AnimationSet(true);
	ScaleAnimation scale = new ScaleAnimation(
						  1,1, //X from fill_parent to 0
						  1,1,//Y from fill_parent to 0
						  Animation.RELATIVE_TO_SELF,0.5f,
						  Animation.RELATIVE_TO_SELF,0.5f
						  );
	scale.setDuration(500);//0.5s
	set.addAnimation(scale);
	
	set.setAnimationListener(new AnimationListener(){
		
		public void onAnimationStart(Animation animation) {
		      // Do nothing.
		}
		
		public void onAnimationRepeat(Animation animation) {
		      // Do nothing.
		}
		
		public void onAnimationEnd(Animation animation) {
		    mAnimationView.setVisibility(View.INVISIBLE);
		}
		
	    });
	
	mAnimationView.startAnimation(set);
	Toast.makeText(this, R.string.save_picture_info,
		       Toast.LENGTH_SHORT).show();
    }

    private void storeImage(final byte[] data, Location loc) {
    	ContentResolver resolver = getContentResolver();
        long dateTaken = System.currentTimeMillis();
        String title = Util.createJpegName(dateTaken);
        int orientation = Exif.getOrientation(data);
        Size s = mParameters.getPictureSize();
        Uri uri = Storage.addImage(resolver, title, dateTaken, loc, orientation, data,
				   s.width, s.height);
        if (uri != null) {
            Util.broadcastNewPicture(this, uri);
            BitmapManager.instance().getThumbnail(resolver, ContentUris.parseId(uri),
                    Images.Thumbnails.MINI_KIND, null, false);
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
    private void takePicture(){
	if(mCameraPictureTaking == true) return;
	// Set rotation and gps data.
	Util.setRotationParameter(mParameters, mCameraId, mOrientation);
	Location loc = mLocationManager.getCurrentLocation();
	Util.setGpsParameters(mParameters, loc);
	mCameraDevice.setParameters(mParameters);
	mCameraPictureTaking = true;
	if(DEBUG) Log.e(TAG,"------take a picture in");
	mCameraDevice.takePicture(null, null, null, new JpegPictureCallback(loc));
	takePictureAnimation(null);
    }
    private void setCameraIPUDirect(String mode){
	if(DEBUG)Log.d(TAG, "set cam`era_ipu_direct record and restart preview.");
	Parameters p = mCameraDevice.getParameters();
	p.set("camera_ipu_direct", mode);
	p.setPreviewSize(mDesiredPreviewWidth,mDesiredPreviewHeight);
	mCameraDevice.stopPreview();
	mCameraDevice.setParameters(p);
	mCameraDevice.startPreview();
	mPreviewing = true;
    }
    @Override
	public boolean onTouch(View v, MotionEvent event) {

	return mGestureDetector.onTouchEvent(event);	
}
    class MySimpleGestureDetectorListener extends GestureDetector.SimpleOnGestureListener{
	@Override
	    public boolean onSlideLeft(boolean fromPhone) {
	    // TODO Auto-generated method stub
	    Log.d(TAG, "onSlideLeft");
	    Intent intent=new Intent(PhotoActivity.this,GalleryPicker.class);
	    intent.putExtra("from_camera",true);
	    startActivity(intent);
	    finish();
	    return true;
	}
	@Override
	    public boolean onSlideDown(boolean fromPhone){
	    finish();
	    return true;
	}
	@Override
	    public boolean onTap(boolean fromPhone) {
	    takePicture();
	    return true;
	}
    }
    /*************for voice****************/
    @Override
	protected boolean onCommandResult(String result, float score) {
	if(DEBUG)Log.d(TAG, "onCommandResult " + result + " " + score);
	if (score > -15) {
	    boolean ret=false;
	    if(result.equals(COMMAND_TAKE_PICTURE)){
		takePicture();	
	    }else if(result.equals(COMMAND_PHOTOS)){
		Intent intent=new Intent(PhotoActivity.this,GalleryPicker.class);
		intent.putExtra("from_camera",true);
		startActivity(intent);
		finish();
	    }
	    return ret;							    
	} else {								    	    
	    return false;							    
	}									    
    }
    @Override
	protected boolean onExit(float score) {
	if (score > -15) {
	    finish();
	    return true;
	} 
         return false;
    }

} //class TakePicture
