/*
 */
package com.ingenic.glass.camera.other;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.provider.MediaStore;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
/**  */
public class CruiseReceiver extends BroadcastReceiver{
        @Override
	public void onReceive(Context context, Intent intent) {
		SharedPreferences sharedPreferences = context.getSharedPreferences("com.ingenic.glass.video_record_preferences", Activity.MODE_PRIVATE);
		Editor editor = sharedPreferences.edit();
		if (intent.getAction().equals("com.ingenic.glass.camera.other.subsection")) {
			String subsection = intent.getStringExtra("value");
			Log.d("CruiseReceiver","---level="+subsection+"--int="+Integer.parseInt(subsection));
			
			editor.putInt("SubsectionTimed", Integer.parseInt(subsection));
			editor.commit();
		}else if (intent.getAction().equals("com.ingenic.glass.camera.other.storage_mode")) {
			boolean mode = intent.getBooleanExtra("value", false);
			Log.d("CruiseReceiver","---mode="+mode);
			
			editor.putInt("CarMode", mode?1:0);
			editor.commit();
		}
	}
}
