package com.github.axet.audiorecorder.encoders;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.github.axet.audiorecorder.app.RawSamples;

import java.io.File;

public class FileEncoder {
    public static final String TAG = FileEncoder.class.getSimpleName();

    Context context;
    Handler handler;

    File in;
    Encoder encoder;
    Thread thread;
    long samples;
    long cur;
    Throwable t;

    public FileEncoder(Context context, File in, Encoder encoder) {
        this.context = context;
        this.in = in;
        this.encoder = encoder;

        handler = new Handler();
    }

    public void run(final Runnable progress, final Runnable done, final Runnable error) {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                cur = 0;

                RawSamples rs = new RawSamples(in);

                samples = rs.getSamples();

                short[] buf = new short[1000];

                rs.open(buf.length);

                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        long len = rs.read(buf);
                        if (len <= 0) {
                            handler.post(done);
                            return;
                        } else {
                            encoder.encode(buf);
                            handler.post(progress);
                            synchronized (thread) {
                                cur += len;
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    Log.e(TAG, "Exception", e);
                    t = e;
                    handler.post(error);
                } finally {
                    encoder.close();
                    if (rs != null) {
                        rs.close();
                    }
                }
            }
        });
        thread.start();
    }

    public int getProgress() {
        synchronized (thread) {
            return (int) (cur * 100 / samples);
        }
    }

    public Throwable getException() {
        return t;
    }

    public void close() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }
}
