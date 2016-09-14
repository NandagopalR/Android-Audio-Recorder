package com.github.axet.audiorecorder.encoders;

public interface Encoder {
    public void encode(short[] buf);

    public void close();
}
