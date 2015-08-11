/*
 */

package com.ingenic.glass.camera.gallery;

import android.view.View;
import android.view.MotionEvent;
import android.net.Uri;
public interface ControllerOverlay {

  interface Listener {
    void onPlayPause();
    void onSeekStart();
    void onSeekMove(int time);
    void onSeekEnd(int time);
    void onShown();
    void onHidden();
    void onReplay();
    void onBackPressed();
  }

  void setListener(Listener listener);

  void setCanReplay(boolean canReplay);

  void setUri(Uri uri);

  /**
   * @return The overlay view that should be added to the player.
   */
  View getView();

  void show();
    
    //boolean getStatus();
    void infoSeek();
    void setSeek(int dvalue,MotionEvent event);
    void setSeekEnd(MotionEvent event);
    void doClick();
    
  void showPlaying();

  void showPaused();

  void showEnded();

  void showLoading();

  void showErrorMessage(String message);

  void hide();

  void setTimes(int currentTime, int totalTime);

  void resetTime();

}
