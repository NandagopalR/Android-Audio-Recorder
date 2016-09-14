package com.github.axet.audiorecorder.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.preference.PreferenceManager;

import com.github.axet.audiorecorder.activities.RecordingActivity;

public class Sound {
    Context context;

    int soundMode;

    public Sound(Context context) {
        this.context = context;
    }

    public void silent() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (shared.getBoolean(MainApplication.PREFERENCE_SILENT, false)) {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            soundMode = am.getRingerMode();

            if (soundMode == AudioManager.RINGER_MODE_SILENT) {
                // we already in SILENT mode. keep all unchanged.
                soundMode = -1;
                return;
            }

            am.setStreamVolume(AudioManager.STREAM_RING, am.getStreamVolume(AudioManager.STREAM_RING), AudioManager.FLAG_SHOW_UI);
            am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        }
    }

    public void unsilent() {
        // keep unchanged
        if (soundMode == -1)
            return;

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (shared.getBoolean(MainApplication.PREFERENCE_SILENT, false)) {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            int soundMode = am.getRingerMode();
            if (soundMode == AudioManager.RINGER_MODE_SILENT) {
                am.setRingerMode(this.soundMode);
                am.setStreamVolume(AudioManager.STREAM_RING, am.getStreamVolume(AudioManager.STREAM_RING), AudioManager.FLAG_SHOW_UI);
            }
        }
    }

    public AudioTrack generateTrack(int sampleRate, short[] buf, int len) {
        int end = len;

        int c = 0;

        if (RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO)
            c = AudioFormat.CHANNEL_OUT_MONO;

        if (RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_STEREO)
            c = AudioFormat.CHANNEL_OUT_STEREO;

        // old phones bug.
        // http://stackoverflow.com/questions/27602492
        //
        // with MODE_STATIC setNotificationMarkerPosition not called
        AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                c, RawSamples.AUDIO_FORMAT,
                len * (Short.SIZE / 8), AudioTrack.MODE_STREAM);
        track.write(buf, 0, len);
        if (track.setNotificationMarkerPosition(end) != AudioTrack.SUCCESS)
            throw new RuntimeException("unable to set marker");
        return track;
    }
}
