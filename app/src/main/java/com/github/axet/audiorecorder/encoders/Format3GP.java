package com.github.axet.audiorecorder.encoders;

import android.annotation.TargetApi;
import android.media.MediaCodecList;
import android.media.MediaFormat;

import java.io.File;

@TargetApi(21)
public class Format3GP extends MuxerMP4 {

    public Format3GP(EncoderInfo info, File out) {
        MediaFormat format = new MediaFormat();

        // for high bitrate AMR_WB
        {
//            final int kBitRates[] = {6600, 8850, 12650, 14250, 15850, 18250, 19850, 23050, 23850};

//            format.setString(MediaFormat.KEY_MIME, "audio/amr-wb");
//            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, info.sampleRate);
//            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, info.channels);
//            format.setInteger(MediaFormat.KEY_BIT_RATE, 23850); // set maximum
        }

        // for low bitrate, AMR_NB
        {
//            final int kBitRates[] = {4750, 5150, 5900, 6700, 7400, 7950, 10200, 12200};

            format.setString(MediaFormat.KEY_MIME, "audio/3gpp");
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, info.sampleRate); // 8000 only supported
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, info.channels);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 12200); // set maximum
        }

        create(info, format, out);
    }
}
