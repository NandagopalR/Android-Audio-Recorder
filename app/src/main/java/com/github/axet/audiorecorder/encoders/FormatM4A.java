package com.github.axet.audiorecorder.encoders;

import android.annotation.TargetApi;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import java.io.File;

@TargetApi(21)
public class FormatM4A extends MuxerMP4 {

    public FormatM4A(EncoderInfo info, File out) {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectHE);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, info.sampleRate);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, info.channels);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64000);

        create(info, format, out);
    }
}
