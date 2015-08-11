package com.ingenic.glass.camera;
import com.ingenic.glass.camera.R;
import java.util.ArrayList;
import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GestureDetector.SimpleOnGestureListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.ingenic.glass.voicerecognizer.api.VoiceRecognizerView;
public class GallerySettingsView extends VoiceRecognizerView{
	protected boolean mAttached;
	private Animation mInAnim = null;
	private Animation mOutAnim = null;
	protected ArrayList<View> mSettingsList = new ArrayList<View>();
	private GestureDetector mGestureDetector = null;
       private MySimpleGesture mSimpleGesture;
	private int mCurSelection = 0;
	private boolean mScrolling = false;
	protected LinearLayout mContainer;
	protected ImageView mOutLine;
	protected int mScreenW;
	protected int mScreenH;
        private int mOutLineWidth;
	private OnItemClickListener mOnItemClickListener;
	private OnDownSlidingBackListener mOnDownSlidingBackListener;
	
	private boolean mClickable = true;	
		
	public GallerySettingsView(Context context) {
		this(context, null);
	}
	
	public GallerySettingsView(Context context, AttributeSet attrs) {
		super(context, attrs);
		LayoutInflater inflater = (LayoutInflater) context
			    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.settings_list, this, true);
	}
	
	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		if (!mAttached) {
			mAttached = true;
			initViews(getContext());
		}
		Log.e("sn","onAttachedToWindow ..................");
	}

	@Override
	protected void onDetachedFromWindow() {//view detached from windows do sth
		super.onDetachedFromWindow();
		mAttached = false;
		removeAllViews();
		Log.e("sn","onDetachedFromWindow ..................");
	}

	@Override
	protected void onVisibilityChanged(View changedView, int visibility) {
		if (changedView == this) {
			if (visibility == View.VISIBLE && mInAnim != null)
				startAnimation(mInAnim);
			else if (visibility != View.VISIBLE && mOutAnim != null)
				startAnimation(mOutAnim);
		}
		super.onVisibilityChanged(changedView, visibility);
	}
	
	protected void initViews(Context context) {
		mSimpleGesture = new MySimpleGesture();
		mGestureDetector = new GestureDetector(context, mSimpleGesture);
		setSimpleGesture(mSimpleGesture);
		mGestureDetector.setIsLongpressEnabled(false);
		mContainer = (LinearLayout) findViewById(R.id.item_container);
		// DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
		mScreenW =getResources().getDisplayMetrics().widthPixels;
		mScreenH = getResources().getDisplayMetrics().heightPixels;;
		
		  // outline
		mOutLine = new ImageView(context);
		mOutLine.setImageResource(R.drawable.item_card_text_outline);
		RelativeLayout.LayoutParams imglp = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		imglp.addRule(RelativeLayout.CENTER_VERTICAL);
		((RelativeLayout) findViewById(R.id.container)).addView(mOutLine, imglp);
	}
	
	protected void updateOutLine() {//bottom line
		if (mOutLine == null)
			return;
		mOutLineWidth = (int) mScreenW / mSettingsList.size();
		int height = (int) mScreenH / 2;
		RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mOutLine.getLayoutParams();
		lp.width = mOutLineWidth;
		lp.height = height;
		mOutLine.setLayoutParams(lp);
		mOutLine.setX(mCurSelection * mOutLineWidth);
	}
	
        private class MySimpleGesture extends SimpleOnGestureListener {
		@Override
	        public boolean onSlideDown(boolean fromPhone) {	        
		        if (mOnDownSlidingBackListener != null)
			        mOnDownSlidingBackListener.onDownSlidingBack();
			else
			        setVisibility(View.GONE);
			return true;
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2,
					float distanceX, float distanceY, boolean fromPhone) {
			mScrolling = true;
			int indicatorPos = (int) (mOutLine.getX() - distanceX);
			indicatorPos = (int) Math.max(0, Math.min(indicatorPos, mScreenW - mOutLine.getWidth()));
			mOutLine.setTranslationX(indicatorPos);
			return true;
		}

		@Override
		public boolean onTap(boolean fromPhone) {
			if (mClickable && !mScrolling && mOnItemClickListener != null) {
				mOnItemClickListener.onItemClick(mSettingsList.get(mCurSelection), mCurSelection);
			}
			return true;
		}

		@Override
		public boolean onSlideLeft(boolean fromPhone) {
		        int nextSelection = 0;
			if (fromPhone) {
			        nextSelection = (int) (mOutLine.getX() / mOutLine.getWidth()) - 1;
				Log.d("onSlideLeft","nextSelection  "+nextSelection);
				nextSelection = Math.max(0, Math.min(nextSelection, mSettingsList.size() - 1));
			} else {
			        nextSelection = (int) (mOutLine.getX() / mOutLine.getWidth());
				Log.d("onSlideLeft","nextSelection  "+nextSelection);
				nextSelection = Math.max(0, Math.min(nextSelection, mSettingsList.size() - 1));
			}
			scrollOutLine(nextSelection, -1);
			return true;
		}

		@Override
		public boolean onSlideRight(boolean fromPhone) {
		        int nextSelection = (int) (mOutLine.getX() / mOutLine.getWidth()) + 1;
			Log.d("onSlideRight","nextSelection  "+nextSelection);
			nextSelection = Math.max(0, Math.min(nextSelection, mSettingsList.size() - 1));
			scrollOutLine(nextSelection, 1);
			return true;
		}
	}

/////////////////////////
	//////////////////////////////
	//////////////////////////////
	@Override
	public boolean onTouchEvent(MotionEvent event) {//handle touch action
		mGestureDetector.onTouchEvent(event);
		if (event.getAction() == MotionEvent.ACTION_UP
				 || event.getAction() == MotionEvent.ACTION_CANCEL) {
			Log.d("event  up","event x="+event.getX()+"event y="+event.getY()+" mScrolling="+mScrolling);
			if (mScrolling) {
				int tmpSelection = (int) (mOutLine.getX() / mOutLine.getWidth());//get tmpcard id
				int whichSelection = (mOutLine.getX() % mOutLine.getWidth()) / mOutLine.getWidth() > 0.5 ? tmpSelection + 1 : tmpSelection;//get selected card id
				Log.e("sn","x="+mOutLine.getX()+" mOutLine.getWidth()="+mOutLine.getWidth()+" tmpSelection="+tmpSelection+" whichSelection="+whichSelection);
				whichSelection = Math.max(0, Math.min(whichSelection, mSettingsList.size() - 1));//recycle
				Log.d("onTouchEvent","whichSelection 1 "+whichSelection);
				int offset = whichSelection > mCurSelection ? 1 : -1;
				scrollOutLine(whichSelection, offset);
			}
		}
		return true;
	}

        private void scrollOutLine(int nextSelection, int offset) {
	        while(mSettingsList.get(nextSelection).getAlpha() != 1) {
		    Log.d("onTouchEvent","getAlpha!=1");
		    nextSelection += offset;
		    if (nextSelection >= mSettingsList.size() || nextSelection < 0) {
			nextSelection -= offset;
			Log.d("onTouchEvent","nextSelection 2 "+nextSelection);
			break;
			
		    }
		}
		while(mSettingsList.get(nextSelection).getAlpha() != 1) {
		    nextSelection -= offset;
		    Log.d("onTouchEvent","getAlpha==1");
		    if (nextSelection >= mSettingsList.size() || nextSelection < 0) {
			nextSelection += offset;
			Log.d("onTouchEvent","nextSelection 3 "+nextSelection);
			break;
			
		    }
		}
		Log.d("onTouchEvent","nextSelection "+nextSelection);
		mOutLine.setTranslationX(nextSelection * mOutLine.getWidth());
		mCurSelection = nextSelection;
		mScrolling = false;
	}

	public void setClickable(boolean clickable) {
		mClickable = clickable;
	}
	
	public void setInOutAnimation(Context context, int inResourceID,
			int outResourceID) {
		mInAnim = AnimationUtils.loadAnimation(context, inResourceID);
		mOutAnim = AnimationUtils.loadAnimation(context, outResourceID);
	}

        public void setCurrentItem(int position) {
	    mCurSelection = position;
	    if (mOutLine != null)
		mOutLine.setX(position * mOutLineWidth);
	}

        public void addItem(View v) {

	}

	public interface OnItemClickListener {
        void onItemClick(View v, int position);
	}

	public void setOnItemClickListener(OnItemClickListener listener) {
		mOnItemClickListener = listener;
	}
	
	public interface OnDownSlidingBackListener {
        void onDownSlidingBack();
	}

	public void setOnDownSlidingBackListener(OnDownSlidingBackListener listener) {
		mOnDownSlidingBackListener = listener;
	}
}
