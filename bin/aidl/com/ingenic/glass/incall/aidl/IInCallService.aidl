package com.ingenic.glass.incall.aidl;

import com.ingenic.glass.incall.aidl.IInCallListener;

interface IInCallService {
	void setAudioMode(int mode);

	long registerInCallListener(IInCallListener l);
	void unregisterInCallListener(long id);
}

