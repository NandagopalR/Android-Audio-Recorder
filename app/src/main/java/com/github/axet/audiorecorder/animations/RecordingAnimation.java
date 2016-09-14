package com.github.axet.audiorecorder.animations;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.animation.Transformation;
import android.widget.ListView;

import com.github.axet.androidlibrary.animations.MarginAnimation;
import com.github.axet.audiorecorder.R;

public class RecordingAnimation extends MarginAnimation {
    ListView list;

    View convertView;

    boolean partial;
    Handler handler;

    // if we have two concurrent animations on the same listview
    // the only one 'expand' should have control of showChild function.
    static RecordingAnimation atomicExpander;

    public static void apply(final ListView list, final View v, final boolean expand, boolean animate) {
        apply(new LateCreator() {
            @Override
            public MarginAnimation create() {
                RecordingAnimation a = new RecordingAnimation(list, v, expand);
                if (expand)
                    atomicExpander = a;
                return a;
            }
        }, v, expand, animate);
    }

    public RecordingAnimation(ListView list, View v, boolean expand) {
        super(v.findViewById(R.id.recording_player), expand);

        handler = new Handler();

        this.convertView = v;
        this.list = list;
    }

    @Override
    public void init() {
        super.init();

        {
            final int paddedTop = list.getListPaddingTop();
            final int paddedBottom = list.getHeight() - list.getListPaddingTop() - list.getListPaddingBottom();

            partial = false;

            partial |= convertView.getTop() < paddedTop;
            partial |= convertView.getBottom() > paddedBottom;
        }
    }

    @Override
    public void calc(final float i, Transformation t) {
        super.calc(i, t);

        float ii = expand ? i : 1 - i;

        // ViewGroup will crash on null pointer without this post pone.
        // seems like some views are removed by RecyvingView when they
        // gone off screen.
        if (Build.VERSION.SDK_INT >= 19) {
            if (!expand && atomicExpander != null && !atomicExpander.hasEnded()) {
                // do not showChild;
            } else {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        showChild(i);
                    }
                });
            }
        }
    }

    @TargetApi(19)
    void showChild(float i) {
        final int paddedTop = list.getListPaddingTop();
        final int paddedBottom = list.getHeight() - list.getListPaddingTop() - list.getListPaddingBottom();

        if (convertView.getTop() < paddedTop) {
            int off = convertView.getTop() - paddedTop;
            if (partial)
                off = (int) (off * i);
            list.scrollListBy(off);
        }

        if (convertView.getBottom() > paddedBottom) {
            int off = convertView.getBottom() - paddedBottom;
            if (partial)
                off = (int) (off * i);
            list.scrollListBy(off);
        }
    }

    @Override
    public void restore() {
        super.restore();
    }

    @Override
    public void end() {
        super.end();
    }
}
