package com.ingenic.glass.camera.gallery;

import com.ingenic.glass.camera.CameraAppImpl;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Video.Media;
import android.media.ThumbnailUtils;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class VideoGetter {
    private static final String TAG = "VideoImageGetter";
    private static final boolean DEBUG = false;	
    // from ImageManager
    public static final int SORT_ASCENDING = 1;
    public static final int SORT_DESCENDING = 2;

    //from VideoList
    private static final int INDEX_ID = 0;
    private static final int INDEX_DATA_PATH = 1;
    private static final int INDEX_DATE_TAKEN = 2;
    private static final int INDEX_TITLE = 3;
    private static final int INDEX_MIMI_THUMB_MAGIC = 4;
    private static final int INDEX_MIME_TYPE = 5;
    private static final int INDEX_DATE_MODIFIED = 6;

    private static final String[] VIDEO_PROJECTION = new String[] {
            Media._ID,
            Media.DATA,
            Media.DATE_TAKEN,
            Media.TITLE,
            Media.MINI_THUMB_MAGIC,
            Media.MIME_TYPE,
            Media.DATE_MODIFIED};

    protected String whereClause() {
        return mBucketId != null
                ? Images.Media.BUCKET_ID + " = '" + mBucketId + "'"
                : "1 = 1 ";
    }

    protected String[] whereClauseArgs() {
    	String [] result = {"%/IGlass/Video/%"};
        return result;
    }

    ContentResolver mContentResolver;
    String mBucketId;
    Uri mImageUri;
    int mSort;

    //new VideoList
    public VideoGetter(ContentResolver resolver, Uri imageUri,
            int sort, String bucketId) {
        super();
	mContentResolver = resolver;
	mImageUri = imageUri;
	mSort = sort;
	mBucketId = bucketId;
    }
    
    public Cursor createCursor(int gallerySelectMode){
    	// Modify by dybai_bj 20150625 Add photo path condition
    	String conditions = "";
    	if (CameraAppImpl.DCIM == gallerySelectMode) { // DCIM
    		conditions = whereClause() + " AND _data LIKE ? ";
    	} else { // Other
    		conditions = whereClause() + " AND _data NOT LIKE ? ";
    	}
        Cursor c = Images.Media.query(
                mContentResolver, mImageUri, VIDEO_PROJECTION,
                conditions, whereClauseArgs(), sortOrder());

	return c;
    }
    public Item getImage(Cursor cursor){
        long id = cursor.getLong(INDEX_ID);
	Uri uri = contentUri(id);
        String dataPath = cursor.getString(INDEX_DATA_PATH);
        long dateTaken = cursor.getLong(INDEX_DATE_TAKEN);
        if (dateTaken == 0) {
            dateTaken = cursor.getLong(INDEX_DATE_MODIFIED) * 1000;
        }
        long miniThumbMagic = cursor.getLong(INDEX_MIMI_THUMB_MAGIC);
        String title = cursor.getString(INDEX_TITLE);
        String mimeType = cursor.getString(INDEX_MIME_TYPE);
        if (title == null || title.length() == 0) {
            title = dataPath;
        }
	if(DEBUG) Log.d(TAG,"--dataPath="+dataPath+" title="+title+" mimeType="+mimeType+" dateTaken="+dateTaken);
	if(DEBUG) Log.d(TAG,"--mImageUri="+mImageUri+" id="+id+"-- uri="+uri);
	
	Bitmap bmp = null;
	if(dataPath.startsWith("/system/media/")){
	    if(DEBUG) Log.d(TAG,"----------1");
	    bmp = fullSizeBitmap(dataPath);
	}else{
	    if(DEBUG) Log.d(TAG,"----------2");
	    bmp = miniThumbBitmap(id); //miniThumbBitmap can insert to videothumbnails in external.db
	}
	Item item = new Item(id,title,mimeType,dataPath,dateTaken,uri);
	item.setThumbBitmap(bmp);
	return item;
    }

    //from VideoObject
    public Bitmap miniThumbBitmap(long id) {
	if(DEBUG) Log.d(TAG,"--miniThumbBitmap");
        try {
            return BitmapManager.instance().getThumbnail(mContentResolver,
                    id, Images.Thumbnails.MINI_KIND, null, true);
        } catch (Throwable ex) {
            Log.e(TAG, "miniThumbBitmap got exception", ex);
            return null;
        }
    }
    //from VideoObject

    public Bitmap fullSizeBitmap(String dataPath) {
        return ThumbnailUtils.createVideoThumbnail(dataPath,
                Video.Thumbnails.MINI_KIND);
    }

    protected String sortOrder() {
        String ascending =
                (mSort == SORT_ASCENDING)
                ? " ASC"
                : " DESC";

        String dateExpr =
                "case ifnull(datetaken,0)" +
                " when 0 then date_modified*1000" +
                " else datetaken" +
                " end";

        return dateExpr + ascending + ", _id" + ascending;
    }
    public Uri contentUri(long id) {
        // TODO: avoid using exception for most cases
        try {
            // does our uri already have an id (single image query)?
            // if so just return it
            long existingId = ContentUris.parseId(mImageUri);
            if (existingId != id) Log.e(TAG, "id mismatch");
            return mImageUri;
        } catch (NumberFormatException ex) {
            // otherwise tack on the id
            return ContentUris.withAppendedId(mImageUri, id);
        }
    }

}
