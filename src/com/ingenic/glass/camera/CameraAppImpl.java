/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ingenic.glass.camera;

import com.ingenic.glass.camera.util.Util;

import android.app.Application;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class CameraAppImpl extends Application {

        private final String TAG = "CameraAppImpl";
	public static final int DCIM=0;
	public static final int OTHER=1;
	public static final int CAMERA_ERROR_QUICKCAPTURE_HAL_STORE = 5;
        public static final int CAMERA_ERROR_PREVIEW = 2;

	// 1.没有屏幕
	public static final int NO_SCREEN = 0x00;
	// 2. 快拍模式
	public static final int CAPTURE = 0x08;
	// 3. 正常显示
	public static final int NORMAL_DISPLAY = 0x0c;
	// 4. 低功耗显示拍照
	public static final int LOW_POWER_DISPLAY_CAPTURE = 0x0e;
	// 5. 低功耗显示录像
	public static final int LOW_POWER_DISPLAY_VIDEO = 0x0f;

        private final int MSG_RELEASE_WAKE_LOCK = 1;
        

        private WakeLock  mWakeLock;
    
        private Handler mHandler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
		    switch(msg.what){
		    case MSG_RELEASE_WAKE_LOCK:
			mWakeLock.release();
			Log.d(TAG,"WakeLock has release for timeout");
			break;
		    }
		}
	    };
    
	@Override
		public void onCreate() {
		super.onCreate();
		Util.initialize(this);
		PowerManager manager = ((PowerManager) getSystemService(POWER_SERVICE));  
		mWakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"camera");  
	}

        public void acquireWakeLock(){
	    Log.d(TAG,"Wakelock has acquire");
	    mWakeLock.acquire(); 
	}

        public void releaseWakeLock(){
	    Log.d(TAG,"WakeLock has release");
	    mWakeLock.release();
	} 

        public void releaseWakeLock(int timeout){
	    Log.d(TAG,"releaseWakeLock :: timeout=" +timeout);
	    mHandler.sendEmptyMessageDelayed(MSG_RELEASE_WAKE_LOCK, timeout);
	}
    
}


