package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.Bitmap;

import org.telegram.ui.Components.SizeNotifierFrameLayout;

public class WindowViewBase extends SizeNotifierFrameLayout {

    public WindowViewBase(Context context) {
        super(context);
    }

    public int getBottomPadding2() {
        return 0;
    }

    public int getPaddingUnderContainer() {
        return 0;
    }

    public void drawBlurBitmap(Bitmap bitmap, float amount) {}
}