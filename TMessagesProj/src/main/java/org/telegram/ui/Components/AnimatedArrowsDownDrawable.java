package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;

public class AnimatedArrowsDownDrawable extends Drawable {

    private int strokeWidth = AndroidUtilities.dp(1);
    private int iconSize = AndroidUtilities.dp(26);
    private int arrowHeight = AndroidUtilities.dp(5);
    private int arrowWidth = AndroidUtilities.dp(10);

    private long lastTime = 0;
    private long duration = 1_000;

    private Paint paint;
    private Path arrowPath = new Path();

    // 0..1
    private float translationFactor;

    public AnimatedArrowsDownDrawable(int color) {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(color);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        updatePath();
    }

    @Override
    public void draw(Canvas c) {
        calculateTranslate();

        c.save();
        c.translate(getBounds().left, getBounds().top);

        c.save();
        float firstStartYPosition = ((float) getBounds().height() / 2) - arrowHeight;
        c.translate(0, firstStartYPosition + calculateTranslateY(translationFactor, AndroidUtilities.dp(6)));
        c.drawPath(arrowPath, paint);
        c.restore();

        c.save();
        float secondStartYPosition = ((float) getBounds().height() / 2);
        c.translate(0, secondStartYPosition + calculateTranslateY(translationFactor, AndroidUtilities.dp(4)));
        c.drawPath(arrowPath, paint);
        c.restore();

        c.restore();

        invalidateSelf();
    }

    private float calculateTranslateY(float translateFactor, int maxTranslateY) {
        if (translateFactor > 0.5) {
            return maxTranslateY * (1 - translateFactor);
        } else {
            return maxTranslateY * translateFactor;
        }
    }

    private void updatePath() {
        arrowPath.reset();
        int center = iconSize / 2;
        int halfArrowWidth = arrowWidth / 2;
        arrowPath.moveTo(center - halfArrowWidth, 0);
        arrowPath.lineTo(center, arrowHeight);
        arrowPath.lineTo(center + halfArrowWidth, 0);
    }

    private void calculateTranslate() {
        if (lastTime == 0) {
            lastTime = System.currentTimeMillis();
            translationFactor = 0;
            return;
        }
        long currentTime = System.currentTimeMillis();
        float offset = (currentTime - lastTime) * 1f / duration;
        translationFactor += offset;
        if (translationFactor > 1) {
            translationFactor = translationFactor - 1;
        }
        lastTime = currentTime;
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidateSelf();
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return iconSize;
    }

    @Override
    public int getIntrinsicHeight() {
        return iconSize;
    }
}
