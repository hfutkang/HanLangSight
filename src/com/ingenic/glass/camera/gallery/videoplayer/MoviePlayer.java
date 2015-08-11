/*
 */

package com.ingenic.glass.camera.gallery;
import com.ingenic.glass.camera.R;

import android.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.MediaController;
//import android.widget.VideoView;
import android.widget.GestureDetector;
import android.widget.GestureDetector.SimpleOnGestureListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public class MoviePlayer implements
        MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener,
        ControllerOverlay.Listener {
    @SuppressWarnings("unused")
    private static final String TAG = "MoviePlayer";
    private static final boolean DEBUG = false;
    private static final String KEY_VIDEO_POSITION = "video-position";
    private static final String KEY_RESUMEABLE_TIME = "resumeable-timeout";

    // Copied from MediaPlaybackService in the Music Player app.
    private static final String SERVICECMD = "com.android.music.musicservicecommand";
    private static final String CMDNAME = "command";
    private static final String CMDPAUSE = "pause";

    //touch event 
    public static final int SINGLECLICK = 1;
    public static final int LONGCLICK = 2;
    public static final int GOBACK = 3;

    //touch time
    public static final int ClickTimeMs = 2000;

    // If we resume the acitivty with in RESUMEABLE_TIMEOUT, we will keep playing.
    // Otherwise, we pause the player.
    private static final long RESUMEABLE_TIMEOUT = 3 * 60 * 1000; // 3 mins

    private Context mContext;
    private final View mRootView;
    private final VideoView mVideoView;
    private final Bookmarker mBookmarker;
    private final Uri mUri;
    private final AudioBecomingNoisyReceiver mAudioBecomingNoisyReceiver;
    private final ControllerOverlay mController;

    private long mResumeableTime = Long.MAX_VALUE;
    private int mVideoPosition = 0;
    private boolean mHasPaused = false;
    private GestureDetector mGestureDetector = null;
    // If the time bar is being dragged.
    private boolean mDragging;

    // If the time bar is visible.
    private boolean mShowing;
    private boolean mInfoSeekFlag = false;
    private static final int TOUCHLENGTH = 50;
    private int startX=0,endX=0,curX= 0;
    private int startY=0,endY=0;
    private long touchStartTime = 0,touchEndTime = 0;
    private int touchEvent = 0;
    private Handler mHandler;
    private final Runnable mPlayingChecker = new Runnable() {
        @Override
        public void run() {
            if (mVideoView.isPlaying()) {
                mController.showPlaying();
            } else {
                mHandler.postDelayed(mPlayingChecker, 250);
            }
        }
    };

    private final Runnable mProgressChecker = new Runnable() {
        @Override
        public void run() {
            int pos = setProgress();
            mHandler.postDelayed(mProgressChecker, 1000 - (pos % 1000));
        }
    };

    public MoviePlayer(View rootView, final MovieActivity movieActivity, Uri videoUri,
		       Bundle savedInstance, boolean canReplay,Handler handler) {
        mContext = movieActivity.getApplicationContext();
	mHandler = handler;
	mRootView = rootView;
        mVideoView = (VideoView) rootView.findViewById(R.id.surface_view);
        mBookmarker = new Bookmarker(movieActivity);
        mUri = videoUri;

        mController = new MovieControllerOverlay(mContext,mVideoView);
        ((ViewGroup)rootView).addView(mController.getView());
        mController.setListener(this);
        mController.setCanReplay(canReplay);
	if(DEBUG) Log.d(TAG,"---------layout uri="+videoUri.toString());
        mController.setUri(videoUri);

        mVideoView.setOnErrorListener(this);
        mVideoView.setOnCompletionListener(this);
        mVideoView.setVideoURI(mUri);
	showSystemUi(false);

	mRootView.setOnTouchListener(new View.OnTouchListener() {
		public boolean onTouch(View v, MotionEvent event) {
		    //将ontouch事件传递给widget下的手势识别处理  
		    return mGestureDetector.onTouchEvent(event);
		}
	    });
	mGestureDetector = new GestureDetector(mContext, new SimpleOnGestureListener(){	
		@Override
		    public boolean onDown ( boolean fromPhone){
		    //when ACTION_DOWN occure , show seekbar and play view
		    Log.d(TAG,"ondown....."); 
		    mController.show();
		    mInfoSeekFlag = true;		    
		    return true;
		}
	
		@Override
		    public boolean onLongPress(boolean fromPhone) {		    
		    Log.d(TAG,"onLongPress...."+fromPhone);
		    //show seekbar,seekleft view and seekright view
		    if(!fromPhone){
		    if(mInfoSeekFlag == true){
			mInfoSeekFlag = false;
			mController.infoSeek();
			touchEvent = LONGCLICK;
		    }
		    }	
		    return false;		
		}

		@Override	
		    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY,boolean fromPhone){
		    //onlongpress >>> onScrolling >>> move触发
		    if( touchEvent == LONGCLICK ){
			// curX = (int)e1.getX();
			// endX = (int)e2.getX();
			//	Log.d(TAG,"curX = "+curX+"  endX"+endX);
			if(Math.abs(distanceX) > 2){//distanceX的值是move前与move的坐标差
			    //当手指肚全按上时，左右稍微受力不均横都会导致x值的左右飘动。故用2个px值来降低灵敏度。
			    mController.setSeek((int)distanceX,e2);
			    curX = endX;
			}		 
		    }
		    return true;
		}

		@Override
		    public boolean onUp( MotionEvent ev,boolean fromPhone){
		    //when ACTION_UP occure , end seek
		    if( touchEvent == LONGCLICK ){
			Log.d(TAG,"up....");		
			mController.setSeekEnd(ev);
			touchEvent = 0;
		    }
		    return true;
		}

		@Override
		//handle onclick event
		    public boolean onTap(boolean fromPhone){
		    Log.d(TAG,"tap....");		    
		    mController.doClick();	
		    return true;
		}

		@Override
		    public boolean onSlideDown(boolean fromPhone){
		    Log.d(TAG,"sliding down....");
		    onFinish();
		    return true;
		}
	    });
        // When the user touches the screen or uses some hard key, the framework
        // will change system ui visibility from invisible to visible. We show
        // the media control at this point.
	/*
        mVideoView.setOnSystemUiVisibilityChangeListener(
                new View.OnSystemUiVisibilityChangeListener() {
            public void onSystemUiVisibilityChange(int visibility) {
                if ((visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
		    Log.d(TAG,"--onTouch in--1");
                    mController.show();
                }
            }
        });
	*/
        mAudioBecomingNoisyReceiver = new AudioBecomingNoisyReceiver();
        mAudioBecomingNoisyReceiver.register();

        Intent i = new Intent(SERVICECMD);
        i.putExtra(CMDNAME, CMDPAUSE);
        movieActivity.sendBroadcast(i);

        if (savedInstance != null) { // this is a resumed activity
            mVideoPosition = savedInstance.getInt(KEY_VIDEO_POSITION, 0);
            mResumeableTime = savedInstance.getLong(KEY_RESUMEABLE_TIME, Long.MAX_VALUE);
            mVideoView.start();
            mVideoView.suspend();
            mHasPaused = true;
        } else {
            final Integer bookmark = mBookmarker.getBookmark(mUri);
            if (bookmark != null) {
                //showResumeDialog(movieActivity, bookmark);
		Log.d(TAG,"start playback from duration="+bookmark);
                mVideoView.seekTo(bookmark);
		startVideo();
            } else {
                startVideo();
            }
        }
    }

    private void showSystemUi(boolean visible) {
        int flag = visible ? 0 : View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        mVideoView.setSystemUiVisibility(flag);
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(KEY_VIDEO_POSITION, mVideoPosition);
        outState.putLong(KEY_RESUMEABLE_TIME, mResumeableTime);
    }

    public void onPause() {
        mHasPaused = true;
        mHandler.removeCallbacksAndMessages(null);
        mVideoPosition = mVideoView.getCurrentPosition();
        mBookmarker.setBookmark(mUri, mVideoPosition, mVideoView.getDuration());
        mVideoView.suspend();
        mResumeableTime = System.currentTimeMillis() + RESUMEABLE_TIMEOUT;
    }

    public void onResume() {
        if (mHasPaused) {
            mVideoView.seekTo(mVideoPosition);
            mVideoView.resume();

            // If we have slept for too long, pause the play
            if (System.currentTimeMillis() > mResumeableTime) {
                pauseVideo();
            }
        }
        mHandler.post(mProgressChecker);
    }

    public void onDestroy() {
        mVideoView.stopPlayback();
        mAudioBecomingNoisyReceiver.unregister();
    }

    // This updates the time bar display (if necessary). It is called every
    // second by mProgressChecker and also from places where the time bar needs
    // to be updated immediately.
    private int setProgress() {
        if (mDragging || !mShowing) {
            return 0;
        }
        int position = mVideoView.getCurrentPosition();
        int duration = mVideoView.getDuration();
        mController.setTimes(position, duration);
        return position;
    }

    private void startVideo() {
        // For streams that we expect to be slow to start up, show a
        // progress spinner until playback starts.
        String scheme = mUri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "rtsp".equalsIgnoreCase(scheme)) {
            mController.showLoading();
            mHandler.removeCallbacks(mPlayingChecker);
            mHandler.postDelayed(mPlayingChecker, 250);
        } else {
            mController.showPlaying();
        }

        mVideoView.start();
        setProgress();
    }

    private void playVideo() {
        mVideoView.start();
        mController.showPlaying();
        mShowing = true;
        setProgress();
    }

    private void pauseVideo() {
        mVideoView.pause();
        mController.showPaused();
        mShowing = false;
    }

    // Below are notifications from VideoView
    @Override
    public boolean onError(MediaPlayer player, int arg1, int arg2) {
        mHandler.removeCallbacksAndMessages(null);
        // VideoView will show an error dialog if we return false, so no need
        // to show more message.
	  // mController.showErrorMessage("");
	Log.d(TAG,"--onError in---------");
	mHandler.sendEmptyMessageDelayed(MovieActivity.FINISH, 0);
        return true;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
	Log.d(TAG,"--onCompletion in");
        mController.showEnded();
        onCompletion();
    }

    /*rewrite by MovieActivity*/
    public void onCompletion() {
    }
    public void onFinish() {
    }

    // Below are notifications from ControllerOverlay
    @Override
    public void onBackPressed() {
	Log.d(TAG,"--onBackPressed");
	onFinish();
    }
    @Override
    public void onPlayPause() {
        if (mVideoView.isPlaying()) {
            pauseVideo();
        } else {
            playVideo();
        }
    }

    @Override
    public void onSeekStart() {
        mDragging = true;
    }

    @Override
    public void onSeekMove(int time) {
        mVideoView.seekTo(time);
    }

    @Override
    public void onSeekEnd(int time) {
        mDragging = false;
        mVideoView.seekTo(time);
        setProgress();
    }

    @Override
    public void onShown() {
        mShowing = true;
	//        showSystemUi(true);
        setProgress();
    }

    @Override
    public void onHidden() {
        mShowing = false;
	//        showSystemUi(false);
    }

    @Override
    public void onReplay() {
        startVideo();
    }

    // We want to pause when the headset is unplugged.
    private class AudioBecomingNoisyReceiver extends BroadcastReceiver {
        public void register() {
            mContext.registerReceiver(this,
                    new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        }

        public void unregister() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
	    mController.doClick();
        }
    }
}

class Bookmarker {
    private static final String TAG = "Bookmarker";

    private static final String BOOKMARK_CACHE_FILE = "bookmark";
    private static final int BOOKMARK_CACHE_MAX_ENTRIES = 100;
    private static final int BOOKMARK_CACHE_MAX_BYTES = 10 * 1024;
    private static final int BOOKMARK_CACHE_VERSION = 1;

    private static final int HALF_MINUTE = 30 * 1000;
    private static final int TWO_MINUTES = 4 * HALF_MINUTE;

    private final Context mContext;

    private Uri mUri;
    private int mBookmark;
    private int mDuration;
    private static Object mRequestLock = new Object();

    public Bookmarker(Context context) {
        mContext = context;
    }

    public void setBookmark(Uri uri, int bookmark, int duration) {
	mUri = uri;
	mBookmark = bookmark;
	mDuration = duration;
	Log.d(TAG,"==========setBookmark in");
	new Thread(){
	    @Override
		public void run() {
		super.run();
		
		try {
		    BlobCache cache = CacheManager.getCache(mContext,
							    BOOKMARK_CACHE_FILE, BOOKMARK_CACHE_MAX_ENTRIES,
							    BOOKMARK_CACHE_MAX_BYTES, BOOKMARK_CACHE_VERSION);
		    
		    ByteArrayOutputStream bos = new ByteArrayOutputStream();
		    DataOutputStream dos = new DataOutputStream(bos);
		    dos.writeUTF(mUri.toString());
		    dos.writeInt(mBookmark);
		    dos.writeInt(mDuration);
		    dos.flush();
		    cache.insert(mUri.hashCode(), bos.toByteArray());
		} catch (Throwable t) {
		    Log.w(TAG, "setBookmark failed", t);
		}		
	    }	
	}.start();
    }

    public Integer getBookmark(Uri uri) {
		Log.d(TAG,"==========getBookmark in");
	mUri = uri;
	new Thread(){
	    @Override
		public void run() {
		super.run();

        try {
            BlobCache cache = CacheManager.getCache(mContext,
                    BOOKMARK_CACHE_FILE, BOOKMARK_CACHE_MAX_ENTRIES,
                    BOOKMARK_CACHE_MAX_BYTES, BOOKMARK_CACHE_VERSION);

            byte[] data = cache.lookup(mUri.hashCode());
            if (data == null){
		  //return null;
		mDuration = -1;
		synchronized(mRequestLock){
		    mRequestLock.notifyAll();
		}
	    }

            DataInputStream dis = new DataInputStream(
                    new ByteArrayInputStream(data));

            String uriString = dis.readUTF(dis);
            int bookmark = dis.readInt();
            int duration = dis.readInt();

            if (!uriString.equals(mUri.toString())) {
		  //return null;
		mDuration = -1;
		synchronized(mRequestLock){
		    mRequestLock.notifyAll();
		}
            }

            if ((bookmark < HALF_MINUTE) || (duration < TWO_MINUTES)
                    || (bookmark > (duration - HALF_MINUTE))) {
		  //return null;
		mDuration = -1;
		synchronized(mRequestLock){
		    mRequestLock.notifyAll();
		}
            }
	      //return Integer.valueOf(bookmark);
		mDuration = bookmark;
		synchronized(mRequestLock){
		    mRequestLock.notifyAll();
		}
        } catch (Throwable t) {
            Log.w(TAG, "getBookmark failed", t);
        }

	    }	
	}.start();

	synchronized(mRequestLock){
	    try {
		mRequestLock.wait(3*1000);//wait 3s
	    } catch (InterruptedException e) {
		e.printStackTrace();
	    }
	}
	Log.d(TAG,"==========getBookmark out mDuration="+mDuration);
	if(mDuration == -1)
	    return null;

        return Integer.valueOf(mDuration);
    }
}
