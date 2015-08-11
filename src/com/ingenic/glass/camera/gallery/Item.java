package com.ingenic.glass.camera.gallery;

import android.net.Uri;
import android.graphics.Bitmap;
import android.text.format.DateFormat;

public class Item {
    public final String Id;
    public final String Title;
    public final String MimeType;
    public final String DataPath;
    public final String DateTaken;
    public final Uri ContentUri;

    public Bitmap ThumbBitmap;  // the thumbnail bitmap for the image list

    public Item(){
	Id = null;
        Title = null;
	MimeType = null;
	DataPath = null;
	DateTaken=null;
	ContentUri = null;
    }

    public Item(long id,String title,String mimeType,String dataPath,long dateTaken,Uri uri) {
	Id = Long.toString(id);
        Title = title;
	MimeType = mimeType;
	DataPath = dataPath;
	DateTaken=DateFormat.format("yyyy-MM-dd  kk:mm:ss",dateTaken).toString();
	ContentUri = uri;
    }

    public void setThumbBitmap(Bitmap thumbBitmap) {
        ThumbBitmap = thumbBitmap;
    }
}