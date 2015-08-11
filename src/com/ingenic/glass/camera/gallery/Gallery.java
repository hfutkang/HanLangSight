/*
 */

package com.ingenic.glass.camera.gallery;

import com.ingenic.glass.camera.GallerySettingsView.OnDownSlidingBackListener;
import android.app.Activity;
import android.os.Bundle;;
/**
 * The GalleryPicker activity.
 */

public class Gallery extends Activity  implements OnDownSlidingBackListener{
    private GallerySettingsList mGallerySettingsList = null;
    @Override
	public void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	mGallerySettingsList = new GallerySettingsList(this);
	setContentView(mGallerySettingsList);//goto this view
	mGallerySettingsList.setOnDownSlidingBackListener(this);
    }
   
    @Override
	public void onDownSlidingBack() {
	finish();
    }
   
}
