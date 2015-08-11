package com.ingenic.glass.camera.gallery;

import java.util.Set;
import java.io.File;
import java.io.FileNotFoundException;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import cn.ingenic.glasssync.services.SyncData;
import cn.ingenic.glasssync.services.SyncModule;
import cn.ingenic.glasssync.services.SyncException;

public class ShareModule extends SyncModule {
    private static final String TAG = "ShareModule";
    private boolean DEBUG = true;
    
      //private static final String CMD = "cmd";
    public static final String SHARE_TYPE_IMG = "img";
    public static final String SHARE_TYPE_VIDEO = "video";
    
    private Context mContext;
    private static ShareModule sInstance;
    
    private ShareModule(Context context){
	super(TAG, context);
	mContext = context;
    }

    public static ShareModule getInstance(Context c) {
	if (null == sInstance)
	    sInstance = new ShareModule(c);
	return sInstance;
    }

    @Override
    protected void onCreate() {
    }

    private boolean checkBTBinded(){
    	BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
	if (adapter == null || (!adapter.isEnabled()) ) {
	    Log.e(TAG, "---bluetooth is no exist or no open");
	    return false;
	}
	
	Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
        if (bondedDevices == null) {
            return false;
        }

	return true;
    }
    public boolean sendData(String path) {
	if(checkBTBinded()==false)
	    return false;

	SyncData data = new SyncData();

	int pos = path.lastIndexOf("/");
	if(pos > 0)
	    path = path.substring(pos+1);
	
	Log.e(TAG, "---send data: path=" +path);
	data.putString(SHARE_TYPE_IMG, path);
	try{
	    send(data);
	}catch (SyncException e){
	    Log.e(TAG, "---send file sync failed:" + e);
	    Toast.makeText(mContext,  "蓝牙传送失败！", Toast.LENGTH_LONG).show();
	}
	return true;
    }

    public boolean sendData(String type,String path) {
	BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
	if (adapter == null || (!adapter.isEnabled()) ) {
	    Log.e(TAG, "---bluetooth is no exist or no open");
	    return false;
	}

	SyncData data = new SyncData();
	File f = new File(path);
	try{
	    sendFile(f, type, (int)f.length());
	}catch (SyncException e){
	    Log.e(TAG, "---send file sync failed:" + e);
	}catch (FileNotFoundException e){
	    Log.e(TAG, "---send file not found failed:" + e);
	}
	return true;
    }

    protected void onFileSendComplete(String fileName, boolean success) {
	    Log.e(TAG, "---onFileSendComplete:" + fileName+" success="+success);
    }
}
