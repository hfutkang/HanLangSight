package com.ingenic.glass.camera.gallery;

import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.ingenic.glass.camera.R;
import android.util.Log;
public class GalleryAdapter extends BaseAdapter {
	private Context mContext;
	private ArrayList<Item> mList;

	public GalleryAdapter(Context context, ArrayList<Item> list) {
		// TODO Auto-generated constructor stub
		mContext = context;
		mList = list;
	}

	@Override
	public int getCount() {
		// TODO Auto-generated method stub
		return mList.size();
	}

	@Override
	public Object getItem(int position) {
		// TODO Auto-generated method stub
		return position;
	}

	@Override
	public long getItemId(int position) {
		// TODO Auto-generated method stub
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		// TODO Auto-generated method stub
		ViewHolder holder;
		if (convertView == null) {
			holder = new ViewHolder();
			convertView = LayoutInflater.from(mContext).inflate(
					R.layout.gallery, null);
			holder.create_date = (TextView) convertView
					.findViewById(R.id.create_date);
			holder.media_id = (TextView) convertView
					.findViewById(R.id.media_id);
			holder.thumbnail = (ImageView) convertView
					.findViewById(R.id.thumbnail);
			holder.video_mark = (ImageView) convertView
					.findViewById(R.id.video_mark);
			convertView.setTag(holder);
		} else {
			holder = (ViewHolder) convertView.getTag();
		}		
		int windowsHeight = mContext.getResources().getDisplayMetrics().heightPixels;
		if (windowsHeight >= 480) {
			holder.create_date.setTextSize(50);
		} else {
			holder.create_date.setTextSize(25);
		}
		if(null!=mList.get(position).Id)
		    holder.media_id.setText(mList.get(position).Id);
		if(null!=mList.get(position).DateTaken)
		    holder.create_date.setText(mList.get(position).DateTaken);		
		if (mList.get(position).MimeType.indexOf("video/") != -1)
		    holder.video_mark.setVisibility(View.VISIBLE);
		else 
		    holder.video_mark.setVisibility(View.GONE);
		if (mList.get(position).ThumbBitmap == null) {
		    if (mList.get(position).MimeType.indexOf("video/") != -1)
			holder.thumbnail.setImageResource(R.drawable.ic_launcher_gallery480);			
		    else
			holder.thumbnail.setImageResource(R.color.white_color);	
		} else 
		    holder.thumbnail.setImageBitmap(mList.get(position).ThumbBitmap);
		return convertView;
	}

	private class ViewHolder {
		private TextView create_date;
		private TextView media_id;
		private ImageView thumbnail;
		private ImageView video_mark;
	}

}
