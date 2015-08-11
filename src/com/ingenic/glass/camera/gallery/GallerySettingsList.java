package com.ingenic.glass.camera.gallery;

import com.ingenic.glass.camera.CameraAppImpl;
import com.ingenic.glass.camera.R;
import com.ingenic.glass.camera.GallerySettingsView;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.ImageView;

public class GallerySettingsList extends GallerySettingsView implements GallerySettingsView.OnItemClickListener {
    private static final int SETTING_ITEMS_SIZE = 2;
    private static final String TAG = "GallerySettingsList";
    private static final boolean DEBUG = false;
    private final String COMMAND_DCIM ="相机";
    private final String COMMAND_Other = "其他";
    private final String[] VOICE_CMDS = { COMMAND_DCIM, COMMAND_Other};
    private Context mContecxt;
    private final String MODE="mode";	
    public GallerySettingsList(Context context) {
	super(context);
	for(int i=0;i<VOICE_CMDS.length;i++)
	    addRecognizeCommand(VOICE_CMDS[i]);
	mContecxt=context;
    }
    @Override
	protected void initViews(Context context) {
	super.initViews(context);
	int width = (int) mScreenW / SETTING_ITEMS_SIZE;
	int height = (int) mScreenH / 2;		
	LayoutInflater inflate = LayoutInflater.from(context);
	android.widget.LinearLayout.LayoutParams itemLp = new android.widget.LinearLayout.LayoutParams(width, height, Gravity.CENTER);
	// camera_gallery
	View camera_gallery = inflate.inflate(R.layout.setting_item, null);		
	TextView camera_gallery_Title = (TextView) camera_gallery.findViewById(R.id.title);
	camera_gallery_Title.setText(R.string.camera_gallery);
	camera_gallery.setId(CameraAppImpl.DCIM);
	mContainer.addView(camera_gallery, itemLp);
	mSettingsList.add(camera_gallery);		
	// other_gallery
	View other_gallery = inflate.inflate(R.layout.setting_item, null);
	TextView other_gallery_Title = (TextView) other_gallery.findViewById(R.id.title);
	other_gallery_Title.setText(R.string.other_gallery);
	other_gallery.setId(CameraAppImpl.OTHER);
	mContainer.addView(other_gallery, itemLp);
	mSettingsList.add(other_gallery);		
	setOnItemClickListener(this);		
	updateOutLine();	//update The scroll bar	
    }
    private void startActivity(int mode){
	Intent intent=new Intent(mContecxt,GalleryPicker.class);
	intent.putExtra(MODE,mode);
	mContecxt.startActivity(intent);
    }
    @Override
	public void onItemClick(View v, int position) {//To handle click events
	if (DEBUG)
	    Log.d(TAG, "onItemClick in position=" + position);
	switch (position){
	case CameraAppImpl.DCIM:
	    startActivity(CameraAppImpl.DCIM);
	    break;
	case CameraAppImpl.OTHER:
	    startActivity(CameraAppImpl.OTHER);
	    break;
	}
    }
    /*******************for voice****************************/
    @Override
	protected boolean onCommandResult(String result, float score) {
	if (DEBUG)
	    Log.d(TAG, "onCommandResult " + result + " " + score);
	if (score > -15) {
	    boolean ret = true;
	    if (result.equals(COMMAND_DCIM)) {
		startActivity(CameraAppImpl.DCIM);
	    } else if (result.equals(COMMAND_Other)) {
		startActivity(CameraAppImpl.OTHER);
	    }
	    return true;
	} else 
	    return false;
    }
    @Override
	protected boolean onExit(float score) {
	if (score > -15) {
	    ((Gallery)getContext()).finish();
	    return true;
	} else
	    return false;
    }
}
