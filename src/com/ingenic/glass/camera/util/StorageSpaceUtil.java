package com.ingenic.glass.camera.util;

import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

public class StorageSpaceUtil {

	private static final String TAG = "StorageSpaceUtil";

	public static final long UNAVAILABLE = -1L;
    public static final long PREPARING = -2L;
    public static final long UNKNOWN_SIZE = -3L;
    public static final long LOW_STORAGE_THRESHOLD= 50000000;

    private static final String BASE_DIR = Environment.getExternalStorageDirectory().toString();

    public static long getAvailableSpace() {
        String state = Environment.getExternalStorageState();
        Log.d(TAG, "External storage state=" + state);
        if (Environment.MEDIA_CHECKING.equals(state)) {
            return PREPARING;
        }
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            return UNAVAILABLE;
        }

        try {
            StatFs stat = new StatFs(BASE_DIR);
            return stat.getAvailableBlocks() * (long) stat.getBlockSize() - LOW_STORAGE_THRESHOLD;
        } catch (Exception e) {
            Log.i(TAG, "Fail to access external storage", e);
        }
        return UNKNOWN_SIZE;
    }

}
