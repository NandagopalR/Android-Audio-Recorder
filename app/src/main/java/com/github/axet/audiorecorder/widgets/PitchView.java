package com.github.axet.audiorecorder.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.audiorecorder.R;
import com.github.axet.audiorecorder.app.MainApplication;
import com.github.axet.audiorecorder.app.RawSamples;

import java.util.LinkedList;
import java.util.List;

public class PitchView extends ViewGroup {
    public static final String TAG = PitchView.class.getSimpleName();

    // pitch delimiter length in dp
    public static final float PITCH_DELIMITER = 1f;
    // pitch length in dp
    public static final float PITCH_WIDTH = 2f;

    // update pitchview in milliseconds
    public static final int UPDATE_SPEED = 10;

    // edit update time
    public static final int EDIT_UPDATE_SPEED = 250;

    // 'pitch length' in milliseconds (100ms)
    //
    // in other words how many milliseconds do we need to show whole pitch.
    int pitchTime;

    List<Double> data = new LinkedList<>();

    // how many pitches we can fit on screen
    int pitchScreenCount;
    // how many pitches we should fit in memory
    int pitchMemCount;
    // pitch delimiter length in px
    int pitchDlimiter;
    // pitch length in px
    int pitchWidth;
    // pitch length in pn + pitch delimiter length in px
    int pitchSize;

    PitchGraphView graph;
    PitchCurrentView current;

    long time = 0;

    // how many samples were cut from begining of 'data' list
    long samples = 0;

    Runnable edit;
    // index
    int editPos = -1;
    boolean editFlash = false;
    // current playing position in samples
    float playPos = -1;
    Runnable play;

    Runnable draw;
    float offset = 0;

    Handler handler;

    public static class HandlerUpdate implements Runnable {
        long start;
        long updateSpeed;
        Handler handler;
        Runnable run;

        public static HandlerUpdate start(Handler handler, Runnable run, long updateSpeed) {
            HandlerUpdate r = new HandlerUpdate();
            r.run = run;
            r.start = System.currentTimeMillis();
            r.updateSpeed = updateSpeed;
            r.handler = handler;
            // post instead of draw.run() so 'start' will measure actual queue time
            handler.postDelayed(r, updateSpeed);
            return r;
        }

        public static void stop(Handler handler, Runnable run) {
            handler.removeCallbacks(run);
        }

        @Override
        public void run() {
            this.run.run();

            long cur = System.currentTimeMillis();

            long diff = cur - start;

            start = cur;

            long delay = updateSpeed + (updateSpeed - diff);
            if (delay > updateSpeed)
                delay = updateSpeed;

            if (delay > 0)
                this.handler.postDelayed(this, delay);
            else
                this.handler.post(this);
        }
    }

    public class PitchGraphView extends View {
        Paint paint;
        Paint paintRed;
        Paint editPaint;
        Paint playPaint;
        Paint cutColor;

        public PitchGraphView(Context context) {
            this(context, null);
        }

        public PitchGraphView(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public PitchGraphView(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);

            paint = new Paint();
            paint.setColor(getThemeColor(R.attr.colorPrimary));
            paint.setStrokeWidth(pitchWidth);

            paintRed = new Paint();
            paintRed.setColor(Color.RED);
            paintRed.setStrokeWidth(pitchWidth);

            cutColor = new Paint();
            cutColor.setColor(getThemeColor(android.R.attr.textColorHint));
            cutColor.setStrokeWidth(pitchWidth);

            editPaint = new Paint();
            editPaint.setColor(getThemeColor(R.attr.colorPrimaryDark));
            editPaint.setStrokeWidth(pitchWidth);

            playPaint = new Paint();
            playPaint.setColor(getThemeColor(R.attr.colorPrimaryDark));
            playPaint.setStrokeWidth(pitchWidth / 2);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            int w = MeasureSpec.getSize(widthMeasureSpec);

            pitchScreenCount = w / pitchSize + 1;

            pitchMemCount = pitchScreenCount + 1;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);

            fit(pitchScreenCount);
        }

        public void calc() {
            if (data.size() >= pitchMemCount) {
                long cur = System.currentTimeMillis();

                float tick = (cur - time) / (float) pitchTime;

                // force clear queue
                if (data.size() > pitchMemCount + 1) {
                    tick = 0;
                    time = cur;
                    fit(pitchMemCount);
                }

                if (tick > 1) {
                    if (data.size() > pitchMemCount) {
                        tick -= 1;
                        time += pitchTime;
                    } else if (data.size() == pitchMemCount) {
                        tick = 0;
                        time = cur;
                    }
                    fit(data.size() - 1);
                }

                offset = pitchSize * tick;
            }
        }

        @Override
        public void onDraw(Canvas canvas) {
            int m = Math.min(pitchMemCount, data.size());

//            if (edit != null) {
//                float x = editPos * pitchSize + pitchSize / 2f;
//                canvas.drawRect(x, 0, getWidth(), getHeight(), bg_cut);
//            }

            for (int i = 0; i < m; i++) {
                double dB = filterDB(i);

                float left = (float) dB;
                float right = (float) dB;

                float mid = getHeight() / 2f;

                float x = -offset + i * pitchSize + pitchSize / 2f;

                Paint p = paint;

                if (getDB(i) < 0) {
                    p = paintRed;
                    left = 1;
                    right = 1;
                }

                if (editPos != -1 && i >= editPos)
                    p = cutColor;

                // left channel pitch
                canvas.drawLine(x, mid, x, mid - mid * left - 1, p);
                // right channel pitch
                canvas.drawLine(x, mid, x, mid + mid * right + 1, p);
            }

            // paint edit mark
            if (editPos != -1 && editFlash) {
                float x = editPos * pitchSize + pitchSize / 2f;
                canvas.drawLine(x, 0, x, getHeight(), editPaint);
            }

            // paint play mark
            if (playPos > 0) {
                float x = playPos * pitchSize + pitchSize / 2f;
                canvas.drawLine(x, 0, x, getHeight(), playPaint);
            }
        }
    }

    public class PitchCurrentView extends View {
        Paint paint;
        Paint textPaint;
        String text;
        Rect textBounds;

        double dB;

        public PitchCurrentView(Context context) {
            this(context, null);
        }

        public PitchCurrentView(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public PitchCurrentView(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);

            text = "100 " + getContext().getString(R.string.db);
            textBounds = new Rect();

            textPaint = new Paint();
            textPaint.setColor(Color.GRAY);
            textPaint.setAntiAlias(true);
            textPaint.setTextSize(20f);

            paint = new Paint();
            paint.setColor(getThemeColor(R.attr.colorPrimary));
            paint.setStrokeWidth(pitchWidth);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            textPaint.getTextBounds(this.text, 0, this.text.length(), textBounds);

            int h = getPaddingTop();
            h += textBounds.height();
            h += ThemeUtils.dp2px(getContext(), 2);
            h += pitchWidth + getPaddingBottom();

            setMeasuredDimension(w, h);
        }

        public void setText(String text) {
            this.text = text;
            textPaint.getTextBounds(this.text, 0, this.text.length(), textBounds);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
        }

        void update(int end) {
            dB = getDB(end) / RawSamples.MAXIMUM_DB;

            String str = "";

            str = Integer.toString((int) getDB(end)) + " " + getContext().getString(R.string.db);

            setText(str);
        }

        @Override
        public void onDraw(Canvas canvas) {
            if (data.size() > 0) {
                current.update(getEnd());
            }

            float y = getPaddingTop() + textBounds.height();

            int x = getWidth() / 2 - textBounds.width() / 2;
            canvas.drawText(text, x, y, textPaint);

            y += ThemeUtils.dp2px(getContext(), 2);

            float left = (float) dB;
            float right = (float) dB;

            float mid = getWidth() / 2f;

            y += pitchWidth / 2;

            canvas.drawLine(mid, y, mid - mid * left - 1, y, paint);
            canvas.drawLine(mid, y, mid + mid * right + 1, y, paint);
        }
    }

    public PitchView(Context context) {
        this(context, null);
    }

    public PitchView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PitchView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        create();
    }

    void create() {
        handler = new Handler();

        pitchDlimiter = ThemeUtils.dp2px(getContext(), PITCH_DELIMITER);
        pitchWidth = ThemeUtils.dp2px(getContext(), PITCH_WIDTH);
        pitchSize = pitchWidth + pitchDlimiter;

        pitchTime = pitchSize * UPDATE_SPEED;

        graph = new PitchGraphView(getContext());
        addView(graph);

//        fft = new FFTChartView(getContext()) {
//            @Override
//            public void onDraw(Canvas canvas) {
//                if (data.size() > 0) {
//                    short[] buf = dataSamples.get(getEnd());
//                    double[] d = FFTView.fft(buf, 0, buf.length);
//                    //double[] d = asDouble(buf, 0, buf.length);
//                    fft.setBuffer(d);
//                }
//
//                super.onDraw(canvas);
//            }
//        };
//        fft.setPadding(0, ThemeUtils.dp2px(2), 0, 0);
//        addView(fft);

        current = new PitchCurrentView(getContext());
        current.setPadding(0, ThemeUtils.dp2px(getContext(), 2), 0, 0);
        addView(current);

        if (isInEditMode()) {
            for (int i = 0; i < 3000; i++) {
                data.add(-Math.sin(i) * RawSamples.MAXIMUM_DB);
            }
        }

        time = System.currentTimeMillis();
    }

    public int getThemeColor(int id) {
        return ThemeUtils.getThemeColor(getContext(), id);
    }

    public int getMaxPitchCount(int width) {
        int pitchScreenCount = width / pitchSize + 1;

        int pitchMemCount = pitchScreenCount + 1;

        return pitchMemCount;
    }

    public void clear(long s) {
        data.clear();
        samples = s;
        offset = 0;
        edit = null;
        draw = null;
        play = null;
    }

    public void fit(int max) {
        if (data.size() > max) {
            int cut = data.size() - max;
            data.subList(0, cut).clear();
            samples += cut;

            int m = data.size() - 1;
            // screen rotate may cause play/edit offsets off screen
            if (editPos > m)
                editPos = m;
            if (playPos > m)
                playPos = m;
        }
    }

    public void add(double a) {
        data.add(a);
    }

    public void drawCalc() {
        graph.calc();
        draw();
    }

    public void drawEnd() {
        fit(pitchMemCount);
        offset = 0;
        draw();
    }

    public int getEnd() {
        int end = data.size() - 1;

        if (editPos != -1) {
            end = editPos;
        }
        if (playPos > 0) {
            end = (int) playPos;
        }

        return end;
    }

    public double getDB(int i) {
        double db = data.get(i);

        db = RawSamples.MAXIMUM_DB + db;

        return db;
    }

    public double filterDB(int i) {
        double db = getDB(i);

        // do not show below NOISE_DB
        db = db - RawSamples.NOISE_DB;

        if (db < 0)
            db = 0;

        int rest = RawSamples.MAXIMUM_DB - RawSamples.NOISE_DB;

        db = db / rest;

        return db;
    }

    public void draw() {
        graph.invalidate();
        current.invalidate();
    }

    public int getPitchTime() {
        return pitchTime;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int ww = getMeasuredWidth() - getPaddingRight() - getPaddingLeft();
        int hh = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();

        current.measure(MeasureSpec.makeMeasureSpec(ww, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(hh, MeasureSpec.AT_MOST));

        hh = hh - current.getMeasuredHeight();

        graph.measure(MeasureSpec.makeMeasureSpec(ww, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(hh, MeasureSpec.AT_MOST));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        graph.layout(getPaddingLeft(), getPaddingTop(),
                getPaddingLeft() + graph.getMeasuredWidth(), getPaddingTop() + graph.getMeasuredHeight());

        current.layout(getPaddingLeft(), graph.getBottom(),
                getPaddingLeft() + current.getMeasuredWidth(), graph.getBottom() + current.getMeasuredHeight());
    }

    public void stop() {
        if (edit != null)
            HandlerUpdate.stop(handler, edit);
        edit = null;

        if (draw != null)
            HandlerUpdate.stop(handler, draw);
        draw = null;

        if (play != null)
            HandlerUpdate.stop(handler, play);
        play = null;

        draw();
    }

    public long edit(float offset) {
        if (offset < 0)
            editPos = -1;
        else
            editPos = ((int) offset) / pitchSize;

        playPos = -1;

        if (editPos >= pitchScreenCount)
            editPos = pitchScreenCount - 1;

        if (editPos >= data.size())
            editPos = data.size() - 1;

        if (draw != null) {
            HandlerUpdate.stop(handler, draw);
            draw = null;
        }

        if (play != null) {
            HandlerUpdate.stop(handler, play);
            play = null;
        }

        draw();

        edit();

        return samples + editPos;
    }

    public void edit() {
        if (edit == null) {
            editFlash = true;

            edit = HandlerUpdate.start(handler, new Runnable() {
                @Override
                public void run() {
                    draw();
                    editFlash = !editFlash;
                }
            }, EDIT_UPDATE_SPEED);
        }
    }

    public void record() {
        if (edit != null)
            HandlerUpdate.stop(handler, edit);
        edit = null;
        editPos = -1;

        if (play != null)
            HandlerUpdate.stop(handler, play);
        play = null;
        playPos = -1;

        if (draw == null) {
            time = System.currentTimeMillis();

            draw = HandlerUpdate.start(handler, new Runnable() {
                @Override
                public void run() {
                    drawCalc();
                }
            }, UPDATE_SPEED);
        }
    }

    // current paying pos in actual samples
    public void play(float pos) {
        if (pos < 0) {
            playPos = -1;
            if (play != null) {
                HandlerUpdate.stop(handler, play);
                play = null;
            }
            if (edit == null) {
                edit();
            }
            return;
        }

        playPos = pos - samples;

        editFlash = true;

        int max = data.size() - 1;

        if (playPos > max)
            playPos = max;

        if (edit != null)
            HandlerUpdate.stop(handler, edit);
        edit = null;

        if (draw != null)
            HandlerUpdate.stop(handler, draw);
        draw = null;

        if (play == null) {
            time = System.currentTimeMillis();
            play = HandlerUpdate.start(handler, new Runnable() {
                @Override
                public void run() {
                    draw();
                }
            }, UPDATE_SPEED);
        }
    }
}
