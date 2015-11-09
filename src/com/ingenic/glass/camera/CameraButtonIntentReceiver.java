/*
 */
package com.ingenic.glass.camera;
import android.content.BroadcastReceiver;
import android.provider.MediaStore;
import android.util.Log;
import android.content.Context;
import android.content.Intent;

import com.ingenic.glass.camera.QuickCapture;
import com.ingenic.glass.camera.live.CameraLive;
/**  */
public class CameraButtonIntentReceiver extends BroadcastReceiver{

	private static final String TAG = "CameraButtonIntentReceiver";
	private static final boolean DEBUG = true;

        @Override
	public void onReceive(Context context, Intent intent) {
    	if (DEBUG) Log.d(TAG, "onReceive in");
	    if (intent.getAction().equals(MediaStore.ACTION_IMAGE_CAPTURE)) {
	    	if (DEBUG) Log.d(TAG, "Action is ACTION_IMAGE_CAPTURE.");
		if(CameraLive.getMInstance() == null && VideoActivity.getMInstance() == null && QuickCapture.getMInstance() == null){
		   //refuse capture when recording or live
		   QuickCapture quickCapture = new QuickCapture(context);
		   quickCapture.start();
		}
	    } else if (intent.getAction().equals(MediaStore.ACTION_VIDEO_CAPTURE)) {
	    	if (DEBUG) Log.d(TAG, "Action is ACTION_VIDEO_CAPTURE.");
		if(CameraLive.getMInstance() != null){
			//stop video live
			if (DEBUG) Log.d(TAG, "--stop live");
			CameraLive.getMInstance().finish();
		}else if (VideoActivity.getMInstance() != null) {
			if (DEBUG) Log.d(TAG, "--stop video");
	    		VideoActivity.getMInstance().finish();
			
	    	} else if (QuickCapture.getMInstance() == null){
	    		Intent intentVideo = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
	    		intentVideo.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			if (DEBUG) Log.d(TAG, "--start video");
	    		context.startActivity(intentVideo);
	    	}else{
		    Log.e("camera is working.so recode be refused!");
		}
	    }
	}
}
