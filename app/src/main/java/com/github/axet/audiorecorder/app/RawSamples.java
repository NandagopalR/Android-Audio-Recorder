package com.github.axet.audiorecorder.app;

import android.media.AudioFormat;
import android.util.Log;

import com.github.axet.audiorecorder.activities.RecordingActivity;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.apache.commons.math3.util.MathArrays;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

public class RawSamples {
    public static int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    public static int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;

    // quite root gives me 20db
    public static int NOISE_DB = 20;
    // max 90 dB detection for android mic
    public static int MAXIMUM_DB = 90;

    File in;

    InputStream is;
    byte[] readBuffer;

    OutputStream os;

    public RawSamples(File in) {
        this.in = in;
    }

    // open for writing with specified offset to truncate file
    public void open(long writeOffset) {
        trunk(writeOffset);
        try {
            os = new BufferedOutputStream(new FileOutputStream(in, true));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // open for reading
    //
    // bufReadSize - samples count
    public void open(int bufReadSize) {
        try {
            readBuffer = new byte[(int) getBufferLen(bufReadSize)];
            is = new FileInputStream(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // open for read with initial offset and buffer read size
    //
    // offset - samples offset
    // bufReadSize - samples size
    public void open(long offset, int bufReadSize) {
        try {
            readBuffer = new byte[(int) getBufferLen(bufReadSize)];
            is = new FileInputStream(in);
            is.skip(offset * (AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT ? 2 : 1));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int read(short[] buf) {
        try {
            int len = is.read(readBuffer);
            if (len <= 0)
                return 0;
            ByteBuffer.wrap(readBuffer, 0, len).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(buf, 0, (int) getSamples(len));
            return (int) getSamples(len);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void write(short val) {
        try {
            ByteBuffer bb = ByteBuffer.allocate(Short.SIZE / Byte.SIZE);
            bb.order(ByteOrder.BIG_ENDIAN);
            bb.putShort(val);
            os.write(bb.array(), 0, bb.limit());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void write(short[] buf) {
        for (int i = 0; i < buf.length; i++) {
            write(buf[i]);
        }
    }

    public long getSamples() {
        return getSamples(in.length());
    }

    public static long getSamples(long len) {
        return len / (AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT ? 2 : 1);
    }

    public static long getBufferLen(long samples) {
        return samples * (AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT ? 2 : 1);
    }

    public void trunk(long pos) {
        try {
            FileChannel outChan = new FileOutputStream(in, true).getChannel();
            outChan.truncate(getBufferLen(pos));
            outChan.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static double getAmplitude(short[] buffer, int offset, int len) {
        double sum = 0;
        for (int i = offset; i < offset + len; i++) {
            sum += buffer[i] * buffer[i];
        }
        return Math.sqrt(sum / len);
    }

    public static double getDB(short[] buffer, int offset, int len) {
        return getDB(getAmplitude(buffer, offset, len));
    }

    public static double getDB(double amplitude) {
        // https://en.wikipedia.org/wiki/Sound_pressure
        return 20.0 * Math.log10(amplitude / 0x7FFF);
    }

    public void close() {
        try {
            if (is != null)
                is.close();
            is = null;

            if (os != null)
                os.close();
            os = null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
