package com.ingenic.glass.camera.gallery;

import java.util.ArrayList;
import java.util.List;

import com.ingenic.glass.camera.CameraAppImpl;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.Media;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class ImageGetter {
    private static final String TAG = "ImageGetter";
    private static final boolean DEBUG = false;	
    // from ImageManager
    public static final int SORT_ASCENDING = 1;
    public static final int SORT_DESCENDING = 2;

    //from ImageList
    private static final int INDEX_ID = 0;
    private static final int INDEX_DATA_PATH = 1;
    private static final int INDEX_DATE_TAKEN = 2;
    private static final int INDEX_MINI_THUMB_MAGIC = 3;
    private static final int INDEX_ORIENTATION = 4;
    private static final int INDEX_TITLE = 5;
    private static final int INDEX_MIME_TYPE = 6;
    private static final int INDEX_DATE_MODIFIED = 7;

    ContentResolver mContentResolver;
    String mBucketId;
    Uri mImageUri;
    int mSort;

    //new ImageList
    public ImageGetter(ContentResolver resolver, Uri imageUri,
            int sort, String bucketId) {
        super();
	mContentResolver = resolver;
	mImageUri = imageUri;
	mSort = sort;
	mBucketId = bucketId;
    }

    static final String[] IMAGE_PROJECTION = new String[] {
            Media._ID,
            Media.DATA,
            Media.DATE_TAKEN,
            Media.MINI_THUMB_MAGIC,
            Media.ORIENTATION,
            Media.TITLE,
            Media.MIME_TYPE,
            Media.DATE_MODIFIED};

    private static final String[] ACCEPTABLE_IMAGE_TYPES =
            new String[] { "image/jpeg", "image/png", "image/gif" };

    private static final String WHERE_CLAUSE =
            "(" + Media.MIME_TYPE + " in (?, ?, ?))";
    private static final String WHERE_CLAUSE_WITH_BUCKET_ID =
            WHERE_CLAUSE + " AND " + Media.BUCKET_ID + " = ?";

    protected String whereClause() {
        return mBucketId == null ? WHERE_CLAUSE : WHERE_CLAUSE_WITH_BUCKET_ID;
    }

    protected String[] whereClauseArgs() {
        // TODO: Since mBucketId won't change, we should keep the array.
    	// Modify by dybai_bj 20150625 Add photo path condition
    	List<String> resultList = new ArrayList<String>();
    	String [] result = null;
    	for (int i = 0; i < ACCEPTABLE_IMAGE_TYPES.length; i++) {
    		resultList.add(ACCEPTABLE_IMAGE_TYPES[i]);
    	}
    	if (mBucketId != null) {
    		resultList.add(mBucketId);
    	}
    	resultList.add("%/IGlass/Pictures/%");
    	result = new String[resultList.size()];
    	resultList.toArray(result);
    	return result;
    }

    public Cursor createCursor(int gallerySelectMode){
    	// Modify by dybai_bj 20150625 Add photo path condition
    	String conditions = "";
    	if (CameraAppImpl.DCIM == gallerySelectMode) { // DCIM
    		conditions = whereClause() + " AND _data LIKE ? ";
    	} else { // Other
    		conditions = whereClause() + " AND _data NOT LIKE ? ";
    	}
    	Cursor cursor = Media.query(
			       mContentResolver, mImageUri, IMAGE_PROJECTION,
			       conditions, whereClauseArgs(), sortOrder());
	return cursor;
    }
    public Item getImage(Cursor cursor){
	long id = cursor.getLong(INDEX_ID);
	Uri uri = contentUri(id);
	String dataPath = cursor.getString(INDEX_DATA_PATH);
	long dateTaken = cursor.getLong(INDEX_DATE_TAKEN);
	if (dateTaken == 0) {
	    dateTaken = cursor.getLong(INDEX_DATE_MODIFIED) * 1000;
	}
	long miniThumbMagic = cursor.getLong(INDEX_MINI_THUMB_MAGIC);
	int orientation = cursor.getInt(INDEX_ORIENTATION);
	String title = cursor.getString(INDEX_TITLE);
	String mimeType = cursor.getString(INDEX_MIME_TYPE);

	if (title == null || title.length() == 0) {
	    title = dataPath;
	}
	if(DEBUG) Log.d(TAG,"--dataPath="+dataPath+" title="+title+" mimeType="+mimeType);
	if(DEBUG) Log.d(TAG,"--mImageUri="+mImageUri+" id="+id+"-- uri="+uri);

	Bitmap bmp = thumbBitmap(false,id,orientation);
	Item item = new Item(id,title,mimeType,dataPath,dateTaken,uri);
	item.setThumbBitmap(bmp);
	return item;
    }
    //from Image.java
    public Bitmap thumbBitmap(boolean rotateAsNeeded,long id,int orientation) {
        Bitmap bitmap = null;
	if(DEBUG) Log.d(TAG,"--get bmp in");

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inDither = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        bitmap = BitmapManager.instance().getThumbnail(mContentResolver, id,
                Images.Thumbnails.MINI_KIND, options, false);

        if (bitmap != null && rotateAsNeeded) {
            bitmap = Util.rotate(bitmap, orientation);
        }
        return bitmap;
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

}
