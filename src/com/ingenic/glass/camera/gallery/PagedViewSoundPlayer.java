package com.ingenic.glass.camera.gallery;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.Log;

public class PagedViewSoundPlayer implements SoundPool.OnLoadCompleteListener{
	private static final String TAG = "PagedViewSound";
    // mSoundPool is created every time load() is called and cleared every
    // time release() is called.
    private SoundPool mSoundPool;
    // Sound ID of each sound resources. Given when the sound is loaded.
    private final int[] mSoundIDs;
    private final boolean[] mSoundIDReady;
    private int mSoundIDToPlay;
    private Context mContext;
    // ID returned by load() should be non-zero.
    private static final int ID_NOT_LOADED = 0;   
    private static final int NUM_SOUND_STREAMS = 1;
    // Sound actions.
    public static final int PAGE_FLIP = 0;
    public static final int PAGE_DOWNSLIDE = 1;
    private static final int LAST_ACTION = 1;
    
    private static final String[] SOUND_FILES = {
        "/system/media/audio/ui/Effect_Tick.ogg",
        "/system/media/audio/ui/Effect_Tick.ogg",
    };	
    
    public PagedViewSoundPlayer(Context context) {
        mSoundPool = new SoundPool(NUM_SOUND_STREAMS,
                AudioManager.STREAM_SYSTEM, 0);
        mSoundPool.setOnLoadCompleteListener(this);
        mSoundIDs = new int[SOUND_FILES.length];
        mSoundIDReady = new boolean[SOUND_FILES.length];
        for (int i = 0; i < SOUND_FILES.length; i++) {
            mSoundIDs[i] = mSoundPool.load(SOUND_FILES[i], 1);
            mSoundIDReady[i] = false;
        }
    }

    public synchronized void play(int action) {
        if (action < 0 || action > LAST_ACTION) {
            Log.e(TAG, "Resource ID not found for action:" + action + " in play().");
            return;
        }

        if (mSoundIDs[action] == ID_NOT_LOADED) {
            // Not loaded yet, load first and then play when the loading is complete.
            mSoundIDs[action] = mSoundPool.load(SOUND_FILES[action], 1);
            mSoundIDToPlay = mSoundIDs[action];
        } else if (!mSoundIDReady[action]) {
            // Loading and not ready yet.
            mSoundIDToPlay = mSoundIDs[action];
        } else {
            mSoundPool.play(mSoundIDs[action], 1f, 1f, 0, 0, 1f);
        }
    }
    
    @Override
    public void onLoadComplete(SoundPool pool, int soundID, int status) {
        if (status != 0) {
            Log.e(TAG, "loading sound tracks failed (status=" + status + ")");
            for (int i = 0; i < mSoundIDs.length; i++ ) {
                if (mSoundIDs[i] == soundID) {
                    mSoundIDs[i] = ID_NOT_LOADED;
                    break;
                }
            }
            return;
        }

        for (int i = 0; i < mSoundIDs.length; i++ ) {
            if (mSoundIDs[i] == soundID) {
                mSoundIDReady[i] = true;
                break;
            }
        }

        if (soundID == mSoundIDToPlay) {
            mSoundIDToPlay = ID_NOT_LOADED;
            mSoundPool.play(soundID, 1f, 1f, 0, 0, 1f);
        }
    }
	
    public synchronized void release() {
        if (mSoundPool != null) {
            mSoundPool.release();
            mSoundPool = null;
        }
        mContext = null;
    }
}
