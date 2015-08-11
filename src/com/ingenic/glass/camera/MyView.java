package com.ingenic.glass.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.RelativeLayout;

public class MyView extends RelativeLayout{
private String TAG="MyView";
private Handler mHandler;
private boolean mStartPreview=true;
	public MyView(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
		Log.d(TAG, "MyView");
	}
	@Override
		protected void onFinishInflate() {
		// TODO Auto-generated method stub
		Log.d(TAG, "onFinishInflate");	
		super.onFinishInflate();
	}
	@Override
		protected void dispatchDraw(Canvas canvas) {
		// TODO Auto-generated method stub
		Log.d(TAG, "dispatchDraw");
		if(mStartPreview){
			mHandler.sendMessage(mHandler.obtainMessage(PhotoActivity.START_PREVIEW));
			mStartPreview=false;
		}
		super.dispatchDraw(canvas);		
	}
	public void setHandler(Handler handler){
		mHandler=handler;	
	}
	
}
