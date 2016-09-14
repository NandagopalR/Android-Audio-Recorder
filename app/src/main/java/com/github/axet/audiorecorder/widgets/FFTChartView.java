package com.github.axet.audiorecorder.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.github.axet.audiorecorder.app.RawSamples;

public class FFTChartView extends FFTView {
    public static final String TAG = FFTChartView.class.getSimpleName();

    public FFTChartView(Context context) {
        this(context, null);
    }

    public FFTChartView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FFTChartView(Context context, AttributeSet attrs, int defStyleAttr) {
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
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (buffer == null)
            return;

        canvas.drawColor(Color.RED);

        int h = getHeight();

        float startX = 0, startY = h;

        int w = getWidth() - getPaddingLeft() - getPaddingRight();

        float step = w / (float) buffer.length;

        double min = Integer.MAX_VALUE;
        double max = Integer.MIN_VALUE;

        for (int i = 0; i < buffer.length; i++) {
            double v = buffer[i];

            min = Math.min(v, min);
            max = Math.max(v, max);

            v = (RawSamples.MAXIMUM_DB + v) / RawSamples.MAXIMUM_DB;

            float endX = startX;
            float endY = (float) (h - h * v);

            canvas.drawLine(startX, startY, endX, endY, paint);

            startX = endX + step;
            startY = endY;
        }

        String tMin = "" + min;
        canvas.drawText(tMin, 0, getHeight(), textPaint);

        String tMax = "" + max;
        textPaint.getTextBounds(tMax, 0, tMax.length(), textBounds);
        canvas.drawText("" + max, w - textBounds.width(), getHeight(), textPaint);
    }

}
