package com.ingenic.glass.incall.aidl;

interface IInCallListener {
	  void onPreModeStateChanged(int mode);
	  void onPostModeStateChanged(int mode);	
}