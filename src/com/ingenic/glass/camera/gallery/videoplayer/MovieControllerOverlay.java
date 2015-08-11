/*
 */

package com.ingenic.glass.camera.gallery;
import com.ingenic.glass.camera.R;
import android.content.Context;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Display;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.text.method.SingleLineTransformationMethod;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.net.Uri;
import com.ingenic.glass.voicerecognizer.api.VoiceRecognizerView;
//import android.widget.VideoView;
/**
 * The playback controller for the Movie Player.
 */
public class MovieControllerOverlay extends VoiceRecognizerView implements
    ControllerOverlay,
							    /*    OnTouchListener,*/
    AnimationListener,
    TimeBar.Listener {

  private enum State {
    PLAYING,
    PAUSED,
    ENDED,
    ERROR,
    LOADING
  }
    /*
    private static final int TOUCHLENGTH = 50;
    private int startX=0,endX=0,curX= 0;
    private int startY=0,endY=0;
    private long touchStartTime = 0,touchEndTime = 0;
    private int touchEvent = 0;
    */
    private boolean mSeeking = false;
    private boolean minfoSeeking = false;
    private boolean mSeeking_right = false;
    private static final String TAG = "MovieControllerOverlay";
    private static final boolean DEBUG=true;
  private static final float ERROR_MESSAGE_RELATIVE_PADDING = 1.0f / 6;
  private static final int TEXT_SIZE_IN_DP = 50;
  private Listener listener;

  private final View background;
  private final TimeBar timeBar;

  private View mainView;
  private final LinearLayout loadingView;
  private final TextView errorView;
  private final TextView timeView;
  private final TextView uriView;
  private final ImageView playPauseReplayView;
  private final ImageView seekView;
  private final ImageView infoSeekViewLeft;
  private final ImageView infoSeekViewRight;

  private final Handler handler;
  private final Runnable startHidingRunnable;
  private final Animation hideAnimation;

  private State state;

  private boolean hidden;

  private boolean canReplay = true;
  private Uri mUri = null;
  private Context mContext;
  private final String COMMAND_STOP="停止";
  private final String COMMAND_PAUSE="暂停";
  private final String COMMAND_START="播放";
  private final String COMMAND_FAST_FORWARD="快进";
  private final String COMMAND_REWIND="快退";
  private final String [] VOICE_CMDS={COMMAND_PAUSE,COMMAND_START,COMMAND_FAST_FORWARD,COMMAND_REWIND};
    private VideoView mVideoView;
    private final int SEEKTIME=10000;
    public MovieControllerOverlay(Context context,VideoView videoView) {
    super(context);
    mContext = context;
    mVideoView=videoView;
    state = State.LOADING;

    LayoutParams wrapContent =
        new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    LayoutParams matchParent =
        new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

    LayoutInflater inflater = LayoutInflater.from(context);
    DisplayMetrics metrics = context.getResources().getDisplayMetrics();
    float textSizeInPx = metrics.density * TEXT_SIZE_IN_DP;
    background = new View(context);
    background.setBackgroundColor(context.getResources().getColor(R.color.darker_transparent));
    addView(background, matchParent);

    timeBar = new TimeBar(context, this);
    addView(timeBar, wrapContent);

    loadingView = new LinearLayout(context);
    loadingView.setOrientation(LinearLayout.VERTICAL);
    loadingView.setGravity(Gravity.CENTER_HORIZONTAL);
    ProgressBar spinner = new ProgressBar(context);
    spinner.setIndeterminate(true);
    loadingView.addView(spinner, wrapContent);
    addView(loadingView, wrapContent);

    timeView = new TextView(context);
    timeView.setTextColor(0xFFFFFFFF);
    timeView.setText("00:00:00");

    uriView = new TextView(context);
    uriView.setTextColor(0xFFFFFFFF);

    Display display = ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
    int height=display.getHeight();

    if(height >= 480){
	timeView.setTextSize(50);
	uriView.setTextSize(30);
    }else{
	timeView.setTextSize(30);
	uriView.setTextSize(20);
    }

    //uriView.setBackgroundColor(android.graphics.Color.RED);
    uriView.setEllipsize(TextUtils.TruncateAt.valueOf("END"));
    uriView.setSingleLine(true);

    addView(timeView,wrapContent);
    addView(uriView,wrapContent);

    seekView = new ImageView(context);
    seekView.setImageResource(R.drawable.ic_vidcontrol_seek_right);
    seekView.setScaleType(ScaleType.CENTER);
    addView(seekView, wrapContent);

    infoSeekViewLeft = new ImageView(context);
    infoSeekViewLeft.setImageResource(R.drawable.ic_vidcontrol_seek_left);
    infoSeekViewLeft.setScaleType(ScaleType.CENTER);
    addView(infoSeekViewLeft, wrapContent);

    infoSeekViewRight = new ImageView(context);
    infoSeekViewRight.setImageResource(R.drawable.ic_vidcontrol_seek_right);
    infoSeekViewRight.setScaleType(ScaleType.CENTER);
    addView(infoSeekViewRight, wrapContent);

    playPauseReplayView = new ImageView(context);
    playPauseReplayView.setImageResource(R.drawable.ic_vidcontrol_play);
    playPauseReplayView.setScaleType(ScaleType.CENTER);
    addView(playPauseReplayView, wrapContent);

    errorView = new TextView(context);
    errorView.setGravity(Gravity.CENTER);
    errorView.setBackgroundColor(0xCC000000);
    errorView.setTextColor(0xFFFFFFFF);
    addView(errorView, matchParent);

    handler = new Handler();
    startHidingRunnable = new Runnable() {
      public void run() {
        startHiding();
      }
    };

    hideAnimation = AnimationUtils.loadAnimation(context, R.anim.player_out);
    hideAnimation.setAnimationListener(this);

    RelativeLayout.LayoutParams params =
        new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    setLayoutParams(params);
    hide();
    for(int i=0;i<VOICE_CMDS.length;i++)
	addRecognizeCommand(VOICE_CMDS[i]);
    setUseTimeout(false);
  }

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  public void setCanReplay(boolean canReplay) {
    this.canReplay = canReplay;
  }

  public void setUri(Uri uri) {
    this.mUri = uri;
    String path = mUri.toString();
    int post = path.lastIndexOf('/');
    String title = path.substring(post+1,path.length());
    Log.d(TAG,"----uri="+uri+"   --title="+title);
    uriView.setText(title);
  }

  public View getView() {
    return this;
  }

  public void showPlaying() {
    state = State.PLAYING;
    showMainView(playPauseReplayView);
  }

  public void showPaused() {
    state = State.PAUSED;
    showMainView(playPauseReplayView);
  }

  public void showEnded() {
    state = State.ENDED;
    showMainView(playPauseReplayView);
  }

  public void showLoading() {
    state = State.LOADING;
    showMainView(loadingView);
  }

  public void showErrorMessage(String message) {
    state = State.ERROR;
    int padding = (int) (getMeasuredWidth() * ERROR_MESSAGE_RELATIVE_PADDING);
    errorView.setPadding(padding, 10, padding, 10);
    errorView.setText(message);
    showMainView(errorView);
  }

  public void resetTime() {
    timeBar.resetTime();
  }

  public void setTimes(int currentTime, int totalTime) {
    timeBar.setTime(currentTime, totalTime);
  }

  public void hide() {
    boolean wasHidden = hidden;
    hidden = true;
    playPauseReplayView.setVisibility(View.INVISIBLE);
    loadingView.setVisibility(View.INVISIBLE);
    background.setVisibility(View.INVISIBLE);
    timeBar.setVisibility(View.INVISIBLE);
    setVisibility(View.INVISIBLE);
    setFocusable(true);
    requestFocus();
    if (listener != null && wasHidden != hidden) {
      listener.onHidden();
    }
  }

  private void showMainView(View view) {
    mainView = view;
    errorView.setVisibility(mainView == errorView ? View.VISIBLE : View.INVISIBLE);
    loadingView.setVisibility(mainView == loadingView ? View.VISIBLE : View.INVISIBLE);
    playPauseReplayView.setVisibility(
        mainView == playPauseReplayView ? View.VISIBLE : View.INVISIBLE);
    show();
  }

  public void show() {
    boolean wasHidden = hidden;
    hidden = false;
    minfoSeeking = false;
    updateViews();
    setVisibility(View.VISIBLE);
    setFocusable(false);
    if (listener != null && wasHidden != hidden) {
      listener.onShown();
    }
    maybeStartHiding();
  }
    /*
  public boolean getStatus() {
      Log.d(TAG,"hidden = "+hidden);
      return hidden;
  }
    */
    public void infoSeek(){
	minfoSeeking = true;
	playPauseReplayView.setVisibility(View.INVISIBLE);
	infoSeekViewLeft.setVisibility(View.VISIBLE);
	infoSeekViewRight.setVisibility(View.VISIBLE);
	infoSeekViewLeft.requestLayout();
    }

    public void setSeek(int dvalue,MotionEvent event){
      mSeeking = true;
      minfoSeeking = false;
      if((dvalue) > 0){
	  if(DEBUG) Log.d(TAG,"--left horital touch");
	  seekView.setImageResource( R.drawable.ic_vidcontrol_seek_left);
	  mSeeking_right = false;
	  
      }else if((dvalue) < 0){
	  if(DEBUG) Log.d(TAG,"--right horital touch");	    
	  seekView.setImageResource( R.drawable.ic_vidcontrol_seek_right);
	  mSeeking_right = true;
      }

      timeBar.seekStart(event);
      timeView.setText(timeBar.getCurrentTime());
      timeView.setVisibility(View.VISIBLE);
      seekView.setVisibility(View.VISIBLE);

      playPauseReplayView.setVisibility(View.INVISIBLE);
      infoSeekViewLeft.setVisibility(View.INVISIBLE);
      infoSeekViewRight.setVisibility(View.INVISIBLE);
      seekView.requestLayout();
  }
  
  public void setSeekEnd(MotionEvent event){
      {
	  minfoSeeking = false;
	  infoSeekViewLeft.setVisibility(View.INVISIBLE);
	  infoSeekViewRight.setVisibility(View.INVISIBLE);
      }

      mSeeking = false;
      timeBar.seekEnd(event);
      timeView.setVisibility(View.INVISIBLE);
      seekView.setVisibility(View.INVISIBLE);
      playPauseReplayView.setVisibility(View.VISIBLE);
  }

  private void maybeStartHiding() {
    cancelHiding();
    if (state == State.PLAYING) {
      handler.postDelayed(startHidingRunnable, 2500);
    }
  }

  private void startHiding() {
    startHideAnimation(timeBar);
    startHideAnimation(playPauseReplayView);
  }

  private void startHideAnimation(View view) {
    if (view.getVisibility() == View.VISIBLE) {
      view.startAnimation(hideAnimation);
    }
  }

  private void cancelHiding() {
    handler.removeCallbacks(startHidingRunnable);
    background.setAnimation(null);
    timeBar.setAnimation(null);
    playPauseReplayView.setAnimation(null);
  }

  public void onAnimationStart(Animation animation) {
    // Do nothing.
  }

  public void onAnimationRepeat(Animation animation) {
    // Do nothing.
  }

  public void onAnimationEnd(Animation animation) {
    hide();
  }

  public void doClick() {

    if (listener != null) {
        if (state == State.ENDED) {
          if (canReplay) {
              listener.onReplay();
          }
        } else if (state == State.PAUSED || state == State.PLAYING) {
          listener.onPlayPause();
        }
    }
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int bw;
    int bh;
    int y;
    int h = b - t;
    int w = r - l;
    boolean error = errorView.getVisibility() == View.VISIBLE;
    if(error || hidden || state == State.PAUSED){
	return;
    }

    bw = timeBar.getPreferredHeight();
    bh = bw;
    y = b - bh;

    background.layout(l, y, r, b);

    if(mUri != null){
	uriView.layout(l+2,t,
		       l + 2 + uriView.getMeasuredWidth(),t + uriView.getMeasuredHeight());
	}

    //timeBar.layout(l+playPauseReplayView.getMeasuredWidth(), b - timeBar.getPreferredHeight(), r, b);
    timeBar.layout(l, b - timeBar.getPreferredHeight(), r, b);
    timeBar.requestLayout();

    // play pause buttons
    int cx = l + w / 2; // center x
    int playbackButtonsCenterline = t + h / 2;

    if(minfoSeeking){
    	bw = infoSeekViewLeft.getMeasuredWidth();
	bh = infoSeekViewLeft.getMeasuredHeight();
	infoSeekViewLeft.layout(cx - bw, playbackButtonsCenterline - bh / 2, 
			cx,playbackButtonsCenterline + bh / 2);
	infoSeekViewRight.layout(cx, playbackButtonsCenterline - bh / 2, 
			cx + bw,playbackButtonsCenterline + bh / 2);
    }else if(mSeeking){
        int time_bw = timeView.getMeasuredWidth();
	int time_bh = timeView.getMeasuredHeight();
	if(DEBUG) Log.d(TAG,"playPauseReplayView bw="+bw+" bh="+bh);
	timeView.layout(cx - time_bw / 2, playbackButtonsCenterline - time_bh / 2, 
			cx + time_bw / 2,playbackButtonsCenterline + time_bh / 2);

	bw = seekView.getMeasuredWidth();
	bh = seekView.getMeasuredHeight();
	if(mSeeking_right)
	    seekView.layout(cx + time_bw / 2,      playbackButtonsCenterline - bh / 2,
			    cx + time_bw / 2 + bw, playbackButtonsCenterline + bh / 2);
	else
	    seekView.layout(cx - time_bw / 2 - bw, playbackButtonsCenterline - bh / 2,
			    cx - time_bw / 2,      playbackButtonsCenterline + bh / 2);

	seekView.requestLayout();
    }else{
        bw = playPauseReplayView.getMeasuredWidth();
    bh = playPauseReplayView.getMeasuredHeight();
    //playPauseReplayView.layout(l, b-bh, l+bw,b);
    playPauseReplayView.layout(cx - bw / 2, playbackButtonsCenterline - bh / 2,
			       cx + bw / 2, playbackButtonsCenterline + bh / 2);
    }
  }
  @Override
  public boolean onTouchEvent(MotionEvent event) {
      return false;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    measureChildren(widthMeasureSpec, heightMeasureSpec);
  }

  private void updateViews() {
    if (hidden) {
      return;
    }
    background.setVisibility(View.VISIBLE);
    timeBar.setVisibility(View.VISIBLE);
    playPauseReplayView.setImageResource(
        state == State.PAUSED ? R.drawable.ic_vidcontrol_play :
          state == State.PLAYING ? R.drawable.ic_vidcontrol_pause :
            R.drawable.ic_vidcontrol_reload);
    playPauseReplayView.setVisibility(
        (state != State.LOADING && state != State.ERROR &&
                !(state == State.ENDED && !canReplay))
        ? View.VISIBLE : View.GONE);

    if(!mSeeking){
	timeView.setVisibility(View.INVISIBLE);
	seekView.setVisibility(View.INVISIBLE);
    }
    if(!minfoSeeking){
	infoSeekViewLeft.setVisibility(View.INVISIBLE);
	infoSeekViewRight.setVisibility(View.INVISIBLE);
    }
    requestLayout();
  }

  // TimeBar listener

  public void onScrubbingStart() {
    cancelHiding();
    listener.onSeekStart();
  }

  public void onScrubbingMove(int time) {
    cancelHiding();
    listener.onSeekMove(time);
  }

  public void onScrubbingEnd(int time) {
    maybeStartHiding();
    listener.onSeekEnd(time);
  }
    @Override
	protected boolean onCommandResult(String result, float score) {
	if(DEBUG)Log.d(TAG, "onCommandResult " + result + " " + score);
	if (score > -15) {
	    if(result.equals(COMMAND_START)){
		doClick();	
	    }else if(result.equals(COMMAND_PAUSE)){
		doClick();		
	    }else if(result.equals(COMMAND_FAST_FORWARD)){
		maybeStartHiding();
		onScrubbingEnd(mVideoView.getCurrentPosition()+SEEKTIME);
	    }else if(result.equals(COMMAND_REWIND)){
		maybeStartHiding();
		onScrubbingEnd(mVideoView.getCurrentPosition()-SEEKTIME);
	    }
	    return false;							    
	} else {								    	    
	    return false;							    
	}									    
    }
    @Override
	protected boolean onExit(float score) {
	if (score > -15) {
	     ((MovieActivity)getContext()).finish();
	    return true;
	} else
	    return false;
    }  
}
