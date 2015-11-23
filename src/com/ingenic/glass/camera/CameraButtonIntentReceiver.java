/*
 */
package com.ingenic.glass.camera;
import android.content.BroadcastReceiver;
import android.provider.MediaStore;
import android.util.Log;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
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
		    Log.e(TAG,"camera is working.so recode be refused!");
		}
	    } else if (intent.getAction().equals("ingenic.action.camera.live.start")) {
	    	if (DEBUG) Log.d(TAG, "ingenic.action.camera.live.start.");
	    	if(CameraLive.getMInstance() != null){
	    	    if (DEBUG) Log.d(TAG, "--stop live");
	    	    CameraLive.getMInstance().finish();
	    	}else if(VideoActivity.getMInstance() != null){
	    	    if (DEBUG) Log.d(TAG, "--stop video and start live");
	    	    VideoActivity.getMInstance().finish(true);
	    	}else if(QuickCapture.getMInstance() != null){
	    	    if (DEBUG) Log.d(TAG, "--stop capture and start live");
		    QuickCapture.getMInstance().finish(true);
	    	}else{
	    	    if (DEBUG) Log.d(TAG, "--start live");
		    ComponentName com = new ComponentName("com.ingenic.glass.camera",
							  "com.ingenic.glass.camera.live.CameraLive");
		    Intent intentlive = new Intent();
		    intentlive.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		    intentlive.setComponent(com);
		    context.startActivity(intentlive);
		}
	    }
	    
	}
}
