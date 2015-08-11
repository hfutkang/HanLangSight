/*
 */

package com.ingenic.glass.camera.gallery;
import com.ingenic.glass.camera.CameraAppImpl;
import com.ingenic.glass.camera.R;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.util.Log;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.widget.GestureDetector;
import android.widget.GestureDetector.SimpleOnGestureListener;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.AdapterPagedView.OnDownSlidingBackListener;
import android.widget.AdapterPagedView.OnItemClickListener;
import android.widget.AdapterPagedView.OnItemLongPressListener;
import android.widget.AdapterPagedView;
import android.widget.MenuView;
import android.database.Cursor;
import android.text.format.DateFormat;
import android.view.ViewGroup.LayoutParams;

import java.util.Collections;  
import java.util.Comparator;
import java.util.ArrayList;
import java.io.File;

import android.view.View.OnTouchListener;

import com.ingenic.glass.camera.PhotoActivity;;

import android.content.Intent;
/**
 * The GalleryPicker activity.
 */

public class GalleryPicker extends NoSearchActivity implements OnItemClickListener, OnItemLongPressListener, OnDownSlidingBackListener,OnTouchListener{

    private static final String TAG = "GalleryPicker";
    private static final boolean DEBUG =false;
    public static final String SCAN_EXTERNAL_URI = "file:///storage/emulated/0";
    public static final int SORT_DESCENDING = 2;
    private AdapterPagedView mPagedView;
    private MenuView mMenuView;
    private ArrayList<Item> mItemList = new ArrayList<Item>();   
    private boolean mImageScanning = true;
    private boolean mVideoScanning = true;
    private boolean mHasInternalVolumeFile = false;
    private ShareModule mShareModule = null;
    Handler mHandler = new Handler();  // handler for the main thread
    Thread mImageWorkerThread; //thread :get image and show 
    Thread mVideoWorkerThread;//thread :get video thumbnail and show 
    BroadcastReceiver mReceiver;
    boolean mScanning;
    boolean mUnmounted;
    int mWindowsWidth = 0;
    int mWindowsHeight = 0;
    GestureDetector mGestureDetector;
    private final String COMMAND_DELETE="删除";
    private final String COMMAND_SHARE="分享";
    private final String COMMAND_PLAY="播放";
    private final String COMMAND_NEXT="下一个";
    private final String COMMAND_LAST="上一个";
    private final String [] VOICE_CMDS={COMMAND_DELETE,COMMAND_SHARE,COMMAND_PLAY,COMMAND_NEXT,COMMAND_LAST};
    private int mGallerySelectMode; // 0:camera,1:other
    private GalleryAdapter mAdapter;
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.main);
	mWindowsWidth = getWindowManager().getDefaultDisplay().getWidth();  
	mWindowsHeight = getWindowManager().getDefaultDisplay().getHeight();

	if(DEBUG) Log.d(TAG,"--onCreate in mWindowsWidth="+mWindowsWidth+" mWindowsHeight="+mWindowsHeight);

	ViewGroup root  = (ViewGroup) findViewById(R.id.main);
	View layout=getLayoutInflater().inflate(R.layout.gallerypicker,null);
	layout.setLayoutParams(new LayoutParams(mWindowsWidth, mWindowsHeight));
	root.addView(layout);
	mPagedView = (AdapterPagedView) layout.findViewById(R.id.pagedView);
	mPagedView.setOnItemClickListener(this);
	mPagedView.setOnItemLongPressListener(this);
	mPagedView.setOnDownSlidingBackListener(this);
	mPagedView.setFlyFlip(true);

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onReceiveMediaBroadcast(intent);
            }
        };
	mMenuView = new MenuView(this);
	mMenuView.addMenuItemInfo(getResources().getString(R.string.delete));
	mMenuView.addMenuItemInfo(getResources().getString(R.string.share));
	root.addView(mMenuView);
	mMenuView.setVisibility(View.GONE);
	mShareModule = ShareModule.getInstance(this);
        ensureOSXCompatibleFolder();
	for(int i=0;i<VOICE_CMDS.length;i++)
	    addRecognizeCommand(VOICE_CMDS[i]);
	setUseTimeout(false);
	mAdapter= new GalleryAdapter(GalleryPicker.this, mItemList);
	mPagedView.setAdapter(mAdapter);
	mGestureDetector = new GestureDetector(this, new MySimpleGestureDetectorListener());
	mPagedView.setOnTouchListener(this);
	mGallerySelectMode=getIntent().getIntExtra("mode",0);	
	checkScanning();
    }

    View mMediaScanningView;

    // Display a dialog if the storage is being scanned now.
    public void updateScanningDialog(boolean scanning) {
        boolean prevScanning = (mMediaScanningView != null);
        if (prevScanning == scanning ) return;
        // Now we are certain the state is changed.
        if (prevScanning) {
            ViewGroup root  = (ViewGroup) findViewById(R.id.main);
	    root.removeView(mMediaScanningView);
            mMediaScanningView = null;
        } else if (scanning) {
            ViewGroup root  = (ViewGroup) findViewById(R.id.main);
            mMediaScanningView = getLayoutInflater().inflate(R.layout.gallerypicker_media_scanning, null);
	    mMediaScanningView.setLayoutParams(new LayoutParams(mWindowsWidth, mWindowsHeight));
	    root.addView(mMediaScanningView);
	    mMediaScanningView.setOnTouchListener(new View.OnTouchListener() {
		public boolean onTouch(View v, MotionEvent event) {
		    return mGestureDetector.onTouchEvent(event); 
		}
	    });
        }
    }

    private View mNoImagesView;

    // Show/Hide the "no images" icon and text. Load resources on demand.
    private void showNoImagesView() {
        if (mNoImagesView == null) {
            ViewGroup root  = (ViewGroup) findViewById(R.id.main);
            mNoImagesView = getLayoutInflater().inflate(R.layout.gallerypicker_no_images, null);
	    mNoImagesView.setLayoutParams(new LayoutParams(mWindowsWidth, mWindowsHeight));
	    root.addView(mNoImagesView);
        }
        mPagedView.setVisibility(View.GONE);
	mNoImagesView.setOnTouchListener(new View.OnTouchListener() {
		public boolean onTouch(View v, MotionEvent event) {
		    return mGestureDetector.onTouchEvent(event); 
		}
	    });
//
    }

    private void hideNoImagesView() {
        if (mNoImagesView != null) {
	    if(DEBUG) Log.d(TAG,"--hide view");
            ViewGroup root  = (ViewGroup) findViewById(R.id.main);
	    root.removeView(mNoImagesView);
	    mPagedView.setVisibility(View.VISIBLE);
        }
    }

    // The storage status is changed, restart the worker or show "no images".
    private void rebake(boolean unmounted, boolean scanning) {
	if(DEBUG) Log.d(TAG,"--rebake");
        if (unmounted == mUnmounted && scanning == mScanning) return;
        abortImageWorker();
        abortVideoWorker();
        mUnmounted = unmounted;
        mScanning = scanning;
        updateScanningDialog(mScanning);
        if (mUnmounted) {
            showNoImagesView();
        } else {
            hideNoImagesView();
            startImageWorker();
            startVideoWorker();
        }
    }

    // This is called when we receive media-related broadcast.
    private void onReceiveMediaBroadcast(Intent intent) {
        String action = intent.getAction();
	Uri uri = intent.getData();
        if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
	    if(DEBUG) Log.d(TAG,"--onReceiveMediaBroadcast 1");
            // SD card available
            // TODO put up a "please wait" message
        } else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
            // SD card unavailable
	    if(DEBUG) Log.d(TAG,"--onReceiveMediaBroadcast 2");
            rebake(true, false);
        } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED) 
		   && uri.toString().equals(SCAN_EXTERNAL_URI)) {
	    if(DEBUG) Log.d(TAG,"--onReceiveMediaBroadcast 4");
            rebake(false, false);
        } else if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
	    if(DEBUG) Log.d(TAG,"--onReceiveMediaBroadcast 5");
            rebake(true, false);
        }
    }


    @Override
    public void onStop() {
        super.onStop();
	if(DEBUG) Log.d(TAG,"--onStop in");
        abortImageWorker();
        abortVideoWorker();
        unregisterReceiver(mReceiver);
    }

    @Override
	protected void onDestroy() {
	super.onDestroy();	
	if (DEBUG)
	    Log.d(TAG, "--onDestory called.");
	mItemList.clear();
    }

    @Override
    public void onStart() {
        super.onStart();
	if(DEBUG) Log.d(TAG,"--onStart in");
        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addDataScheme("file");

	registerReceiver(mReceiver, intentFilter);
	// Assume the storage is mounted and not scanning.
	mUnmounted = false;
	
	mScanning = false;

    }

    // This is used to stop the worker thread.
    volatile boolean mImageAbort = false;
    volatile boolean mVideoAbort = false;

    // Create the worker thread.
    private void startImageWorker() {
        mImageAbort = false;
        mImageWorkerThread = new Thread("GalleryPicker ImageWorker") {
            @Override
            public void run() {
		imageWorkerRun();
            }
        };
        BitmapManager.instance().allowThreadDecoding(mImageWorkerThread);
        mImageWorkerThread.start();
    }
    private void abortImageWorker() {
        if (mImageWorkerThread != null) {
            BitmapManager.instance().cancelThreadDecoding(mImageWorkerThread, getContentResolver());
            mImageAbort = true;
            try {
                mImageWorkerThread.join();
            } catch (InterruptedException ex) {
                Log.e(TAG, "join interrupted");
            }
            mImageWorkerThread = null;
            // Remove all runnables in mHandler.
            // (We assume that the "what" field in the messages are 0
            // for runnables).
            mHandler.removeMessages(0);
        }
    }
   private void startVideoWorker() {
        mVideoAbort = false;
        mVideoWorkerThread = new Thread("GalleryPicker VideoWorker") {
            @Override
            public void run() {	    
                videoWorkerRun();
            }
        };
        BitmapManager.instance().allowThreadDecoding(mVideoWorkerThread);
        mVideoWorkerThread.start();
    }

    private void abortVideoWorker() {
        if (mVideoWorkerThread != null) {
            BitmapManager.instance().cancelThreadDecoding(mVideoWorkerThread, getContentResolver());
            mVideoAbort = true;
            try {
                mVideoWorkerThread.join();
            } catch (InterruptedException ex) {
                Log.e(TAG, "join interrupted");
            }
            mVideoWorkerThread = null;
            // Remove all runnables in mHandler.
            // (We assume that the "what" field in the messages are 0
            // for runnables).
            mHandler.removeMessages(0);
        }
    }

    // This is run in the worker thread.
    private void imageWorkerRun() {
        if (mImageAbort) return;
	ImageGetter ig = new ImageGetter(getContentResolver(),Images.Media.EXTERNAL_CONTENT_URI,
					 SORT_DESCENDING,null);
	Cursor cursor = ig.createCursor(mGallerySelectMode);
	if(cursor != null && cursor.moveToFirst()){
	    do{
		final Item it;
	    it = ig.getImage(cursor);
		if(it!=null){
		    mItemList.add(it);
		    sortClass sort = new sortClass();  
		    Collections.sort(mItemList,sort);
		    mHandler.post(new Runnable() {
			    public void run() {			
				    mAdapter.notifyDataSetChanged();
			    }
			});
		}
	    }while(cursor.moveToNext());  
	}else{
	    mImageScanning = false;
	    if(mVideoScanning == false && mHasInternalVolumeFile == false){
		mHandler.post(new Runnable() {
			public void run() {
			    showNoImagesView();
			}
		    });
	    }
	}

	if (cursor != null) cursor.close(); 
    }

    // This is run in the worker thread.
    private void videoWorkerRun() {
        if (mVideoAbort) return;
	//get video Thumbnails
	if(CameraAppImpl.OTHER == mGallerySelectMode)
	    scanInternalVolume();
	VideoGetter vig = new VideoGetter(getContentResolver(),
						    Uri.parse("content://media/external/video/media"),
						    SORT_DESCENDING,null);
	Cursor cursor = vig.createCursor(mGallerySelectMode);
	if (cursor != null && cursor.moveToFirst()) {
	    do {
		final Item it;
	    it = vig.getImage(cursor);
		if(it!=null){
		    mItemList.add(it);
		    sortClass sort = new sortClass();  
		    Collections.sort(mItemList,sort); 
		    mHandler.post(new Runnable() {
			    public void run() {
				    mAdapter.notifyDataSetChanged();
			    }
			});
		}
	    } while (cursor.moveToNext());
	    cursor.close();
	} else {
	    mVideoScanning = false;
	    if(mImageScanning == false && mHasInternalVolumeFile == false){
		mHandler.post(new Runnable() {
			public void run() {
			    showNoImagesView();
			}
		    });
	    }

	}
	if (cursor != null) cursor.close(); 
    }
    
    private void scanInternalVolume(){
	VideoGetter vig = new VideoGetter(getContentResolver(),
						    Uri.parse("content://media/internal/video/media"),
						    SORT_DESCENDING,null);
	Cursor cursor = vig.createCursor(mGallerySelectMode);
	if(cursor != null && cursor.moveToFirst()){
	    mHasInternalVolumeFile = true;
	    do{
		final Item it = vig.getImage(cursor);
		mItemList.add(it);
		sortClass sort = new sortClass();  
		Collections.sort(mItemList,sort);
		mHandler.post(new Runnable() {
			public void run() {			
			    mAdapter.notifyDataSetChanged();
			}
		    });
	    } while (cursor.moveToNext());
	}

	if (cursor != null) cursor.close(); 
    }

    // This is run in the worker thread.
    private void checkScanning() {
	(new Thread() {
            @Override
		public void run() {
		ContentResolver cr = getContentResolver();
		mScanning =isMediaScannerScanning(cr);
		mHandler.post(new Runnable() {
			public void run() {
			    if(mScanning){
				updateScanningDialog(mScanning);
			    }else{
				startImageWorker();
				startVideoWorker();
			    }
			}
		    });
            }//run
        }).start();
    }

    // This is run in the main thread. from ImageManager.java
    public static boolean isMediaScannerScanning(ContentResolver cr) {
        boolean result = false;
	Cursor cursor = null;
        try {
            cursor = cr.query(MediaStore.getMediaScannerUri(), new String [] {MediaStore.MEDIA_SCANNER_VOLUME},
			      null, null, null);
         } catch (UnsupportedOperationException ex) {
	    Log.e(TAG,"find a error");
        }

        if (cursor != null) {
            if (cursor.getCount() == 1) {
                cursor.moveToFirst();
                result = "external".equals(cursor.getString(0));
            }
            cursor.close();
        }
	
        return result;
    }
    // This is run in the main thread. from ImageManager.java
    public static void ensureOSXCompatibleFolder() {
        File nnnAAAAA = new File(
            Environment.getExternalStorageDirectory().toString()
            + "/DCIM/100ANDRO");
        if ((!nnnAAAAA.exists()) && (!nnnAAAAA.mkdir())) {
            Log.e(TAG, "create NNNAAAAA file: " + nnnAAAAA.getPath()
                    + " failed");
        }
    }


    /*override from PageView*/
    @Override
    public void onItemClick(AdapterPagedView pagedView, View v, int position) {
	startVideo(position);
    }
    @Override
    public void onItemLongPress(AdapterPagedView pagedView, View v, int position) {
	if (DEBUG)
	    Log.d(TAG, "onItemLongPress in position=" + mMenuView.getVisibility());
	setUpMenu(position);
    }
    @Override
    public void onDownSlidingBack(AdapterPagedView pagedView) {
	if (DEBUG)
	    Log.d(TAG, "OnDownSlidingBack in ");
	finish();

    }
    private void setUpMenu(int position){
	final int longPressPosition=position;
	View v = mPagedView.getSelectedView();
	mMenuView.setVisibility(View.VISIBLE);	
	mMenuView.setOnClickListener(v, new MenuView.OnClickListener() {
		public void onClick(View v, int position) {
		    if (position == 0) {
			deleteCurrentView(longPressPosition);
		    }
		    if (position == 1) {
			shareSyncToPhone(longPressPosition);				
		    }
		    mMenuView.setVisibility(View.GONE);
		}
	    });
	mMenuView.setOnBackListener(v, new MenuView.OnBackListener() {
		public void onBack(View v) {
		    mMenuView.setVisibility(View.GONE);
		}
	    });
    }
    private void startVideo(int currentPosition) {
	if (mItemList.get(currentPosition).MimeType.indexOf("video/") != -1) {
	    String file_path = "file://" + mItemList.get(currentPosition).DataPath;
	    Log.d(TAG, "----file_path !" + file_path);
	    Intent intent;
	    intent = new Intent();
	    intent.setClass(this,MovieActivity.class);
	    intent.setDataAndType(Uri.parse(file_path), "video/*");
	    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);	    
	    startActivity(intent);
	}
    }

    private void shareSyncToPhone(int currentPosition) {
	if (DEBUG)
	    Log.d(TAG, "shareSyncToPhone in");
	// share
	if (mShareModule != null) {
	    if (!mShareModule.sendData(mItemList.get(currentPosition).DataPath))
		showToast(getResources().getString(R.string.share_failed));			
	}
    }
    private void deleteCurrentView(int currentPosition) {
	if (DEBUG)
	    Log.d(TAG, "deleteCurrentView in");
	// delete
	if (getContentResolver().delete(mItemList.get(currentPosition).ContentUri, null, null) <= 0) {
	    Log.e(TAG, "-------deleteCurrentView faild");	    
	}
	mItemList.remove(currentPosition);
	mAdapter.notifyDataSetChanged();
	showToast(getResources().getString(R.string.delete_success));
	if (mItemList.size() <= 0)
	    showNoImagesView();
    }
    private void showToast(String str){
	Toast toast = new Toast(this);
	View layout = getLayoutInflater().inflate(R.layout.toast, null);
	TextView toast_tv = null;
	toast_tv = (TextView) layout.findViewById(R.id.tv_toast);
	toast_tv.setText(str);
	toast.setView(layout);
	toast.show();
    }
    @Override
    public boolean onTouch(View v, MotionEvent event) {
	if(DEBUG)Log.d(TAG,"ontouch "+"mPagedView.getCurScreen()="+mPagedView.getCurScreen());
	boolean IsFromCamera=getIntent().getBooleanExtra("from_camera",false);
	if(mPagedView.getCurScreen()!=0 || !IsFromCamera)
	    return mPagedView.onTouchEvent(event);
	else 
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
	    if(mPagedView.getVisibility()==View.VISIBLE)
		startVideo(0);
	    return true;
	}
	@Override
	public boolean onLongPress(boolean fromPhone) {
	    if(mPagedView.getVisibility()==View.VISIBLE)
		setUpMenu(0);
	    return true;
        }
	@Override
	public boolean onSlideRight( boolean fromPhone ){
	    if(DEBUG)Log.d(TAG,"onSlideRight");
	    if(mPagedView.getVisibility()==View.VISIBLE){
		Intent intent=new Intent(GalleryPicker.this,PhotoActivity.class);
		startActivity(intent);
		finish();
	    }
	    return true;
	}
	@Override
	public boolean onSlideLeft( boolean fromPhone ){
	    if(DEBUG)Log.d(TAG,"onSlideLeft");
	    if(mPagedView.getVisibility()==View.VISIBLE)
		mPagedView.setCurrentScreen(1);
	    return true;
	}
    }
    /*******************for voice****************************/
    @Override
	protected boolean onCommandResult(String result, float score) {
	if(DEBUG)Log.d(TAG, "onCommandResult " + result + " " + score);
	if (score > -15) {
	    boolean ret=true;
	    if(result.equals(COMMAND_DELETE)){
		deleteCurrentView(mPagedView.getCurScreen());
		ret=false;	
	    }else if(result.equals(COMMAND_SHARE)){
		shareSyncToPhone(mPagedView.getCurScreen());
		ret=false;		
	    }else if(result.equals(COMMAND_PLAY)){
		startVideo(mPagedView.getCurScreen());
		ret=true;
	    }else if(result.equals(COMMAND_NEXT)){
		if(mPagedView.getCurScreen() != mItemList.size() - 1)
		    mPagedView.setCurrentScreen(mPagedView.getCurScreen()+1);
		ret=false;
	    }else if(result.equals(COMMAND_LAST)){
		if(mPagedView.getCurScreen()!=0)
		    mPagedView.setCurrentScreen(mPagedView.getCurScreen()-1);
		ret=false;
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
	} else
	    return false;
    }
    private class sortClass implements Comparator{  
	public int compare(Object arg0,Object arg1){  
	    Item item0 = (Item)arg0;  
	    Item item1 = (Item)arg1;  
	    int flag = item1.DateTaken.compareTo(item0.DateTaken);  
	    return flag;  
	}  
    }  
}


