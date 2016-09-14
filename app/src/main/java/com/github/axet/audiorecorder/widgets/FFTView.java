package com.github.axet.audiorecorder.widgets;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.audiorecorder.app.RawSamples;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

public class FFTView extends View {
    public static final String TAG = FFTView.class.getSimpleName();

    Paint paint;
    double[] buffer;

    Paint textPaint;
    Rect textBounds;

    public FFTView(Context context) {
        this(context, null);
    }

    public FFTView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FFTView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        create();
    }

    void create() {
        paint = new Paint();
        paint.setColor(0xff0433AE);
        paint.setStrokeWidth(ThemeUtils.dp2px(getContext(), 1));

        textBounds = new Rect();

        textPaint = new Paint();
        textPaint.setColor(Color.GRAY);
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(20f);

        if (isInEditMode()) {
            short[] b = simple();
            b = generateSound(16000, 4000, 100);
            buffer = fft(b, 0, b.length);
            //buffer = RawSamples.generateSound(16000, 4000, 100);
            //buffer = RawSamples.fft(buffer, 0, buffer.length);
        }
    }

    public void setBuffer(double[] buf) {
        buffer = buf;
    }

    public static short[] generateSound(int sampleRate, int freqHz, int durationMs) {
        int count = sampleRate * durationMs / 1000;
        short[] samples = new short[count];
        for (int i = 0; i < count; i++) {
            short sample = (short) (Math.sin(2 * Math.PI * i / (sampleRate / freqHz)) * 0x7FFF);
            samples[i] = sample;
        }
        return samples;
    }

    public static double[] asDouble(short[] buffer, int offset, int len) {
        double[] dd = new double[len];
        for (int i = 0; i < len; i++) {
            dd[i] = buffer[offset + i] / (float) 0x7fff;
        }
        return dd;
    }

    public static double[] fft(short[] buffer, int offset, int len) {
        int len2 = (int) Math.pow(2, Math.ceil(Math.log(len) / Math.log(2)));

        final double[][] dataRI = new double[][]{
                new double[len2], new double[len2]
        };

        double[] dataR = dataRI[0];
        double[] dataI = dataRI[1];

        double powerInput = 0;
        for (int i = 0; i < len; i++) {
            dataR[i] = buffer[offset + i] / (float) 0x7fff;
            powerInput += dataR[i] * dataR[i];
        }
        powerInput = Math.sqrt(powerInput / len);

        FastFourierTransformer.transformInPlace(dataRI, DftNormalization.STANDARD, TransformType.FORWARD);

        double[] data = new double[len2 / 2];

        data[0] = 10 * Math.log10(Math.pow(new Complex(dataR[0], dataI[0]).abs() / len2, 2));

        double powerOutput = 0;
        for (int i = 1; i < data.length; i++) {
            Complex c = new Complex(dataR[i], dataI[i]);
            double p = c.abs();
            p = p / len2;
            p = p * p;
            p = p * 2;
            double dB = 10 * Math.log10(p);

            powerOutput += p;
            data[i] = dB;
        }
        powerOutput = Math.sqrt(powerOutput);

//        if(powerInput != powerOutput) {
//            throw new RuntimeException("in " + powerInput + " out " + powerOutput);
//        }

        return data;
    }

    public static short[] simple() {
        int sampleRate = 1000;
        int count = sampleRate;
        short[] samples = new short[count];
        for (int i = 0; i < count; i++) {
            double x = i / (double) count;
            double y = 0;
            //y += 0.6 * Math.sin(20 * 2 * Math.PI * x);
            //y += 0.4 * Math.sin(50 * 2 * Math.PI * x);
            //y += 0.2 * Math.sin(80 * 2 * Math.PI * x);
            y += Math.sin(100 * 2 * Math.PI * x);
            y += Math.sin(200 * 2 * Math.PI * x);
            y += Math.sin(300 * 2 * Math.PI * x);
            // max = 2.2;
            samples[i] = (short) (y / 3 * 0x7fff);
        }
        return samples;
    }

}
