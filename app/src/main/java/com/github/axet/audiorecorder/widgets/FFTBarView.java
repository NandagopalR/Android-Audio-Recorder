package com.github.axet.audiorecorder.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.audiorecorder.app.RawSamples;

public class FFTBarView extends FFTView {
    public static final String TAG = FFTBarView.class.getSimpleName();

    int barCount;
    float barWidth;
    float barDeli;

    public FFTBarView(Context context) {
        this(context, null);
    }

    public FFTBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FFTBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        create();
    }

    void create() {
        super.create();
    }

    public void setBuffer(double[] buf) {
        super.setBuffer(buf);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // set initial width
        int w = ThemeUtils.dp2px(getContext(), 15);
        int d = ThemeUtils.dp2px(getContext(), 4);
        int s = w + d;

        int mw = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();

        // get count of bars and delimeters
        int dc = (mw - w) / s;
        int bc = dc + 1;

        // get rate
        float k = w / d;

        // get one part of (bar+del) size
        float e = mw / (bc * k + dc);

        barCount = bc;
        barWidth = e * k;
        barDeli = e;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (barCount == 0)
            return;

        int h = getHeight() - getPaddingTop() - getPaddingBottom();

        float left = getPaddingLeft();

        for (int i = 0; i < barCount; i++) {
            double max = 0;

            if (buffer != null) {
                int step = buffer.length / barCount;
                int offset = i * step;
                int end = Math.min(offset + step, buffer.length);
                for (int k = offset; k < end; k++) {
                    double s = buffer[k];
                    max = Math.max(max, s);
                }
            }

            float y = getPaddingTop() + h - h * ((float) max / 0x7fff) - ThemeUtils.dp2px(getContext(), 1);

            if (y < getPaddingTop())
                y = getPaddingTop();

            canvas.drawRect(left, y, left + barWidth, getPaddingTop() + h, paint);
            left += barWidth + barDeli;
        }
    }

}
