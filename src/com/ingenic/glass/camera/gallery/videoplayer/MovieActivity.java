/*
 */

package com.ingenic.glass.camera.gallery;
import com.ingenic.glass.camera.R;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.MediaStore.Video.VideoColumns;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.os.StrictMode;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;
/**
 * This activity plays a video from a specified URI.
 */
public class MovieActivity extends Activity {
    @SuppressWarnings("unused")
    private static final String TAG = "MovieActivity";

    private MoviePlayer mPlayer;
    private boolean mFinishOnCompletion;
    private boolean DEVELOPER_MODE= false;
    public static final int FINISH = 1;
    private final Handler mHandler = new MainHandler();
    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
	        case FINISH:{
		    Toast.makeText(getApplicationContext(),
				   R.string.err_play, Toast.LENGTH_SHORT).show();
		      finish();
		    break;
		}
                default:
                    break;
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

	if(DEVELOPER_MODE){ 
	    StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()				       
				       .detectAll()
				       .penaltyLog()
				       .penaltyDialog() ////打印logcat
				       .build());
	    StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
				   .detectAll()
				   .penaltyLog()
				   .build());
	}

        requestWindowFeature(Window.FEATURE_ACTION_BAR);
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);

        setContentView(R.layout.movie_view);
        View rootView = findViewById(R.id.root);
        Intent intent = getIntent();

        mFinishOnCompletion = intent.getBooleanExtra(
                MediaStore.EXTRA_FINISH_ON_COMPLETION, true);
        mPlayer = new MoviePlayer(rootView, this, intent.getData(), savedInstanceState,
				  !mFinishOnCompletion,mHandler) {
            @Override
            public void onCompletion() {
                if (mFinishOnCompletion) {
                    finish();
                }
            }
            @Override
            public void onFinish() {
		finish();
            }
        };
        if (intent.hasExtra(MediaStore.EXTRA_SCREEN_ORIENTATION)) {
            int orientation = intent.getIntExtra(
                    MediaStore.EXTRA_SCREEN_ORIENTATION,
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            if (orientation != getRequestedOrientation()) {
                setRequestedOrientation(orientation);
            }
        }
        Window win = getWindow();
        WindowManager.LayoutParams winParams = win.getAttributes();
        winParams.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
        winParams.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
        win.setAttributes(winParams);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return false;
    }

    @Override
    public void onStart() {
        ((AudioManager) getSystemService(AUDIO_SERVICE))
                .requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        super.onStart();
    }

    @Override
    protected void onStop() {
        ((AudioManager) getSystemService(AUDIO_SERVICE))
                .abandonAudioFocus(null);
        super.onStop();
    }

    @Override
    public void onPause() {
        mPlayer.onPause();
        super.onPause();
    }

    @Override
    public void onResume() {
        mPlayer.onResume();
        super.onResume();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mPlayer.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroy() {
	mPlayer.onDestroy();
        super.onDestroy();
    }
}
