/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ingenic.glass.camera;

import com.ingenic.glass.camera.R;
import android.util.Log;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.RelativeLayout;
/**
 * A layout which handles the preview aspect ratio.
 */
public class PreviewFrameLayout extends RelativeLayout {
    /** A callback to be invoked when the preview frame's size changes. */
    public interface OnSizeChangedListener {
        public void onSizeChanged();
    }

    private double mAspectRatio = 4.0 / 3.0;

    public PreviewFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setAspectRatio(double ratio) {
        if (ratio <= 0.0) throw new IllegalArgumentException();
        if (mAspectRatio != ratio) {
            mAspectRatio = ratio;
            requestLayout();
        }
    }

    public void showBorder(boolean enabled) {
        setActivated(enabled);
    }


    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int previewWidth = MeasureSpec.getSize(widthSpec);
        int previewHeight = MeasureSpec.getSize(heightSpec);
	Log.d("gysun","------previewWidth="+previewWidth+" previewHeight="+previewHeight);
/*
        // Get the padding of the border background.
        int hPadding = mPaddingLeft + mPaddingRight;
        int vPadding = mPaddingTop + mPaddingBottom;
	Log.d("gysun","------hPadding="+hPadding+" vPadding="+vPadding);
        // Resize the preview frame with correct aspect ratio.
        previewWidth -= hPadding;
        previewHeight -= vPadding;
        if (previewWidth > previewHeight * mAspectRatio) {
	    Log.d("gysun","------mAspectRatio="+mAspectRatio);
            previewWidth = (int) (previewHeight * mAspectRatio + .5);
        } else {
            previewHeight = (int) (previewWidth / mAspectRatio + .5);
        }

        // Add the padding of the border.
        previewWidth += hPadding;
        previewHeight += vPadding;
*/
	Log.d("gysun","-----2-previewWidth="+previewWidth+" previewHeight="+previewHeight);
        // Ask children to follow the new preview dimension.
        super.onMeasure(MeasureSpec.makeMeasureSpec(previewWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(previewHeight, MeasureSpec.EXACTLY));
    }
}
