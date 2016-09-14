package com.github.axet.audiorecorder.encoders;

// based on http://soundfile.sapp.org/doc/WaveFormat/

import android.util.Log;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FormatWAV implements Encoder {
    int NumSamples;
    EncoderInfo info;
    int BytesPerSample;
    RandomAccessFile outFile;

    ByteOrder order = ByteOrder.LITTLE_ENDIAN;

    public FormatWAV(EncoderInfo info, File out) {
        this.info = info;
        NumSamples = 0;

        BytesPerSample = info.bps / 8;

        try {
            outFile = new RandomAccessFile(out, "rw");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        save();
    }

    public void save() {
        int SubChunk1Size = 16;
        int SubChunk2Size = NumSamples * info.channels * BytesPerSample;
        int ChunkSize = 4 + (8 + SubChunk1Size) + (8 + SubChunk2Size);

        write("RIFF", ByteOrder.BIG_ENDIAN);
        write(ChunkSize, order);
        write("WAVE", ByteOrder.BIG_ENDIAN);

        int ByteRate = info.sampleRate * info.channels * BytesPerSample;
        short AudioFormat = 1; // PCM = 1 (i.e. Linear quantization)
        int BlockAlign = BytesPerSample * info.channels;

        write("fmt ", ByteOrder.BIG_ENDIAN);
        write(SubChunk1Size, order);
        write((short)AudioFormat, order); //short
        write((short) info.channels, order); // short
        write(info.sampleRate, order);
        write(ByteRate, order);
        write((short)BlockAlign, order); // short
        write((short)info.bps, order); // short

        write("data", ByteOrder.BIG_ENDIAN);
        write(SubChunk2Size, order);
    }

    void write(String str, ByteOrder order) {
        try {
            byte[] cc = str.getBytes("UTF-8");
            ByteBuffer bb = ByteBuffer.allocate(cc.length);
            bb.order(order);
            bb.put(cc);
            bb.flip();

            outFile.write(bb.array());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void write(int i, ByteOrder order) {
        ByteBuffer bb = ByteBuffer.allocate(Integer.SIZE / Byte.SIZE);
        bb.order(order);
        bb.putInt(i);
        bb.flip();

        try {
            outFile.write(bb.array());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void write(short i, ByteOrder order) {
        ByteBuffer bb = ByteBuffer.allocate(Short.SIZE / Byte.SIZE);
        bb.order(order);
        bb.putShort(i);
        bb.flip();

        try {
            outFile.write(bb.array());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void encode(short[] buf) {
        NumSamples += buf.length / info.channels;
        try {
            ByteBuffer bb = ByteBuffer.allocate(buf.length * (Short.SIZE / Byte.SIZE));
            bb.order(order);
            for (int i = 0; i < buf.length; i++)
                bb.putShort(buf[i]);
            bb.flip();
            outFile.write(bb.array());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        try {
            outFile.seek(0);
            save();
            outFile.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public EncoderInfo getInfo() {
        return info;
    }

}