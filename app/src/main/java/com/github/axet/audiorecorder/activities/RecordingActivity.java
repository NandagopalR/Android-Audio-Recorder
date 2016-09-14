package com.github.axet.audiorecorder.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.audiorecorder.R;
import com.github.axet.androidlibrary.animations.MarginBottomAnimation;
import com.github.axet.audiorecorder.app.MainApplication;
import com.github.axet.audiorecorder.app.RawSamples;
import com.github.axet.audiorecorder.app.Sound;
import com.github.axet.audiorecorder.app.Storage;
import com.github.axet.audiorecorder.encoders.Encoder;
import com.github.axet.audiorecorder.encoders.EncoderInfo;
import com.github.axet.audiorecorder.encoders.FileEncoder;
import com.github.axet.audiorecorder.encoders.Format3GP;
import com.github.axet.audiorecorder.encoders.FormatM4A;
import com.github.axet.audiorecorder.encoders.FormatWAV;
import com.github.axet.audiorecorder.services.RecordingService;
import com.github.axet.audiorecorder.widgets.PitchView;

import java.io.File;

public class RecordingActivity extends AppCompatActivity {
    public static final String TAG = RecordingActivity.class.getSimpleName();

    public static String START_PAUSE = RecordingActivity.class.getCanonicalName() + ".START_PAUSE";
    public static String PAUSE_BUTTON = RecordingActivity.class.getCanonicalName() + ".PAUSE_BUTTON";

    PhoneStateChangeListener pscl = new PhoneStateChangeListener();
    Handler handle = new Handler();
    FileEncoder encoder;

    // do we need to start recording immidiatly?
    boolean start = true;

    Thread thread;
    // lock for bufferSize
    final Object bufferSizeLock = new Object();
    // dynamic buffer size. big for backgound recording. small for realtime view updates.
    int bufferSize;
    // variable from settings. how may samples per second.
    int sampleRate;
    // pitch size in samples. how many samples count need to update view. 4410 for 100ms update.
    int samplesUpdate;
    // output target file 2016-01-01 01.01.01.wav
    File targetFile;
    // how many samples passed for current recording
    long samplesTime;
    // current cut position in samples from begining of file
    long editSample = -1;

    // current sample index in edit mode while playing;
    long playIndex;
    // send ui update every 'playUpdate' samples.
    int playUpdate;
    // current play sound track
    AudioTrack play;

    TextView title;
    TextView time;
    TextView state;
    ImageButton pause;
    PitchView pitch;

    Storage storage;
    Sound sound;
    RecordingReceiver receiver;

    public static void startActivity(Context context, boolean pause) {
        Intent i = new Intent(context, RecordingActivity.class);
        if (pause) {
            i.setAction(RecordingActivity.START_PAUSE);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(i);
    }

    class RecordingReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(PAUSE_BUTTON)) {
                pauseButton();
            }
        }
    }

    class PhoneStateChangeListener extends PhoneStateListener {
        public boolean wasRinging;
        public boolean pausedByCall;

        @Override
        public void onCallStateChanged(int s, String incomingNumber) {
            switch (s) {
                case TelephonyManager.CALL_STATE_RINGING:
                    wasRinging = true;
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    wasRinging = true;
                    if (thread != null) {
                        stopRecording(getString(R.string.hold_by_call));
                        pausedByCall = true;
                    }
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    if (pausedByCall) {
                        startRecording();
                    }
                    wasRinging = false;
                    pausedByCall = false;
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTheme(((MainApplication) getApplication()).getUserTheme());

        setContentView(R.layout.activity_recording);

        setupActionBar();

        pitch = (PitchView) findViewById(R.id.recording_pitch);
        time = (TextView) findViewById(R.id.recording_time);
        state = (TextView) findViewById(R.id.recording_state);
        title = (TextView) findViewById(R.id.recording_title);

        storage = new Storage(this);
        sound = new Sound(this);

        edit(false, false);

        try {
            targetFile = storage.getNewFile();
        } catch (RuntimeException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        title.setText(targetFile.getName());

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);

        if (shared.getBoolean(MainApplication.PREFERENCE_CALL, false)) {
            TelephonyManager tm = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(pscl, PhoneStateListener.LISTEN_CALL_STATE);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        sampleRate = Integer.parseInt(shared.getString(MainApplication.PREFERENCE_RATE, ""));

        if (Build.VERSION.SDK_INT < 23 && isEmulator()) {
            // old emulators are not going to record on high sample rate.
            Toast.makeText(this, "Emulator Detected. Reducing Sample Rate to 8000 Hz", Toast.LENGTH_SHORT).show();
            sampleRate = 8000;
        }

        samplesUpdate = (int) (pitch.getPitchTime() * sampleRate / 1000.0);

        updateBufferSize(false);

        loadSamples();

        View cancel = findViewById(R.id.recording_cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelDialog(new Runnable() {
                    @Override
                    public void run() {
                        stopRecording();
                        storage.delete(storage.getTempRecording());
                        finish();
                    }
                });
            }
        });

        pause = (ImageButton) findViewById(R.id.recording_pause);
        pause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pauseButton();
            }
        });

        View done = findViewById(R.id.recording_done);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording(getString(R.string.encoding));
                encoding(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                });
            }
        });

        String a = getIntent().getAction();
        if (a != null && a.equals(START_PAUSE)) {
            // pretend we already start it
            start = false;
            stopRecording(getString(R.string.pause));
        }

        receiver = new RecordingReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(PAUSE_BUTTON);
        registerReceiver(receiver, filter);
    }

    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setBackgroundDrawable(new ColorDrawable(MainApplication.getActionbarColor(this)));
        }
    }

    void loadSamples() {
        if (!storage.getTempRecording().exists()) {
            updateSamples(0);
            return;
        }

        RawSamples rs = new RawSamples(storage.getTempRecording());
        samplesTime = rs.getSamples();

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        int count = pitch.getMaxPitchCount(metrics.widthPixels);

        short[] buf = new short[count * samplesUpdate];
        long cut = samplesTime - buf.length;

        if (cut < 0)
            cut = 0;

        rs.open(cut, buf.length);
        int len = rs.read(buf);
        rs.close();

        pitch.clear(cut / samplesUpdate);
        for (int i = 0; i < len; i += samplesUpdate) {
            double dB = RawSamples.getDB(buf, i, samplesUpdate);
            pitch.add(dB);
        }
        updateSamples(samplesTime);
    }

    boolean isEmulator() {
        return "goldfish".equals(Build.HARDWARE);
    }

    void pauseButton() {
        if (thread != null) {
            stopRecording(getString(R.string.pause));
        } else {
            editCut();

            startRecording();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        updateBufferSize(false);

        // start once
        if (start) {
            start = false;
            if (permitted()) {
                startRecording();
            }
        }

        boolean recording = thread != null;

        RecordingService.startService(this, targetFile.getName(), recording);

        if (recording) {
            pitch.record();
        } else {
            if (editSample != -1)
                edit(true, false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        updateBufferSize(true);
        editPlay(false);
        pitch.stop();
    }

    void stopRecording(String status) {
        setState(status);
        pause.setImageResource(R.drawable.ic_mic_24dp);

        stopRecording();

        RecordingService.startService(this, targetFile.getName(), thread != null);

        pitch.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                edit(true, true);
                float x = event.getX();
                if (x < 0)
                    x = 0;
                editSample = pitch.edit(x) * samplesUpdate;
                return true;
            }
        });
    }

    void stopRecording() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        pitch.stop();
        sound.unsilent();
    }

    void edit(boolean show, boolean animate) {
        if (show) {
            setState(getString(R.string.edit));
            editPlay(false);

            View box = findViewById(R.id.recording_edit_box);
            MarginBottomAnimation.apply(box, true, animate);

            View cut = box.findViewById(R.id.recording_cut);
            cut.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editCut();
                }
            });

            final ImageView playButton = (ImageView) box.findViewById(R.id.recording_play);
            playButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (play != null) {
                        editPlay(false);
                    } else {
                        editPlay(true);
                    }
                }
            });

            View done = box.findViewById(R.id.recording_edit_done);
            done.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    edit(false, true);
                }
            });
        } else {
            editSample = -1;
            setState(getString(R.string.pause));
            editPlay(false);
            pitch.edit(-1);
            pitch.stop();

            View box = findViewById(R.id.recording_edit_box);
            MarginBottomAnimation.apply(box, false, animate);
        }
    }

    void setState(String s) {
        long free = storage.getFree(storage.getTempRecording());

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);

        int rate = Integer.parseInt(shared.getString(MainApplication.PREFERENCE_RATE, ""));
        int m = RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO ? 1 : 2;
        int c = RawSamples.AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT ? 2 : 1;

        long perSec = (c * m * rate);
        long sec = free / perSec * 1000;

        state.setText(s + "\n(" + ((MainApplication) getApplication()).formatFree(free, sec) + ")");
    }

    void editPlay(boolean show) {
        View box = findViewById(R.id.recording_edit_box);
        final ImageView playButton = (ImageView) box.findViewById(R.id.recording_play);

        if (show) {
            playButton.setImageResource(R.drawable.pause);

            playIndex = editSample;

            playUpdate = PitchView.UPDATE_SPEED * sampleRate / 1000;

            final Handler handler = new Handler();

            AudioTrack.OnPlaybackPositionUpdateListener listener = new AudioTrack.OnPlaybackPositionUpdateListener() {
                @Override
                public void onMarkerReached(AudioTrack track) {
                    editPlay(false);
                }

                @Override
                public void onPeriodicNotification(AudioTrack track) {
                    if (play != null) {
                        playIndex += playUpdate;
                        float p = playIndex / (float) samplesUpdate;
                        pitch.play(p);
                    }
                }
            };

            RawSamples rs = new RawSamples(storage.getTempRecording());
            int len = (int) (rs.getSamples() - editSample);
            short[] buf = new short[len];
            rs.open(editSample, buf.length);
            int r = rs.read(buf);
            play = sound.generateTrack(sampleRate, buf, r);
            play.play();
            play.setPositionNotificationPeriod(playUpdate);
            play.setPlaybackPositionUpdateListener(listener, handler);
        } else {
            if (play != null) {
                play.release();
                play = null;
            }
            pitch.play(-1);
            playButton.setImageResource(R.drawable.play);
        }
    }

    void editCut() {
        if (editSample == -1)
            return;

        RawSamples rs = new RawSamples(storage.getTempRecording());
        rs.trunk(editSample + samplesUpdate);
        rs.close();

        edit(false, true);
        loadSamples();
        pitch.drawCalc();
    }

    @Override
    public void onBackPressed() {
        cancelDialog(new Runnable() {
            @Override
            public void run() {
                stopRecording();
                storage.delete(storage.getTempRecording());
                finish();
            }
        });
    }

    void cancelDialog(final Runnable run) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.confirm_cancel);
        builder.setMessage(R.string.are_you_sure_cancel);
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                run.run();
            }
        });
        builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestory");

        stopRecording();

        if (receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }

        RecordingService.stopService(this);

        if (pscl != null) {
            TelephonyManager tm = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(pscl, PhoneStateListener.LISTEN_NONE);
            pscl = null;
        }
    }

    void startRecording() {
        edit(false, true);
        pitch.setOnTouchListener(null);

        setState(getString(R.string.recording));

        sound.silent();

        pause.setImageResource(R.drawable.ic_pause_24dp);

        pitch.record();

        if (thread != null) {
            thread.interrupt();
        }

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                int p = android.os.Process.getThreadPriority(android.os.Process.myTid());

                if (p != android.os.Process.THREAD_PRIORITY_URGENT_AUDIO) {
                    Log.e(TAG, "Unable to set Thread Priority " + android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                }

                RawSamples rs = null;
                AudioRecord recorder = null;
                try {
                    rs = new RawSamples(storage.getTempRecording());

                    rs.open(samplesTime);

                    int min = AudioRecord.getMinBufferSize(sampleRate, RawSamples.CHANNEL_CONFIG, RawSamples.AUDIO_FORMAT);
                    if (min <= 0) {
                        throw new RuntimeException("Unable to initialize AudioRecord: Bad audio values");
                    }

                    // min = 1 sec
                    min = Math.max(sampleRate * (RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO ? 1 : 2), min);

                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, RawSamples.CHANNEL_CONFIG, RawSamples.AUDIO_FORMAT, min);
                    if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                        throw new RuntimeException("Unable to initialize AudioRecord");
                    }

                    long start = System.currentTimeMillis();
                    recorder.startRecording();

                    int samplesTimeCount = 0;
                    // how many samples we need to update 'samples'. time clock. every 1000ms.
                    int samplesTimeUpdate = 1000 / 1000 * sampleRate * (RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO ? 1 : 2);

                    short[] buffer = null;

                    boolean stableRefresh = false;

                    while (!Thread.currentThread().isInterrupted()) {
                        synchronized (bufferSizeLock) {
                            if (buffer == null || buffer.length != bufferSize)
                                buffer = new short[bufferSize];
                        }

                        final int readSize = recorder.read(buffer, 0, buffer.length);
                        if (readSize <= 0) {
                            break;
                        }
                        long end = System.currentTimeMillis();

                        long diff = (end - start) * sampleRate / 1000;

                        start = end;

                        int s = RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO ? readSize : readSize / 2;

                        if (stableRefresh || diff >= s) {
                            stableRefresh = true;

                            rs.write(buffer);

                            for (int i = 0; i < readSize; i += samplesUpdate) {
                                final double dB = RawSamples.getDB(buffer, i, samplesUpdate);
                                handle.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        pitch.add(dB);
                                    }
                                });
                            }

                            samplesTime += s;
                            samplesTimeCount += s;
                            if (samplesTimeCount > samplesTimeUpdate) {
                                final long m = samplesTime;
                                handle.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        updateSamples(m);
                                    }
                                });
                                samplesTimeCount -= samplesTimeUpdate;
                            }
                        }
                    }
                } catch (final RuntimeException e) {
                    handle.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.e(TAG, Log.getStackTraceString(e));
                            Toast.makeText(RecordingActivity.this, "AudioRecord error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
                } finally {
                    // redraw view, we may add one last pich which is not been drawen because draw tread already interrupted.
                    // to prevent resume recording jump - draw last added pitch here.
                    handle.post(new Runnable() {
                        @Override
                        public void run() {
                            pitch.drawEnd();
                        }
                    });

                    if (rs != null)
                        rs.close();

                    if (recorder != null)
                        recorder.release();
                }
            }
        }, "RecordingThread");
        thread.start();

        RecordingService.startService(this, targetFile.getName(), thread != null);
    }

    // calcuale buffer length dynamically, this way we can reduce thread cycles when activity in background
    // or phone screen is off.
    void updateBufferSize(boolean pause) {
        synchronized (bufferSizeLock) {
            int samplesUpdate;

            if (pause) {
                // we need make buffer multiply of pitch.getPitchTime() (100 ms).
                // to prevent missing blocks from view otherwise:

                // file may contain not multiply 'samplesUpdate' count of samples. it is about 100ms.
                // we can't show on pitchView sorter then 100ms samples. we can't add partial sample because on
                // resumeRecording we have to apply rest of samplesUpdate or reload all samples again
                // from file. better then confusing user we cut them on next resumeRecording.

                long l = 1000;
                l = l / pitch.getPitchTime() * pitch.getPitchTime();
                samplesUpdate = (int) (l * sampleRate / 1000.0);
            } else {
                samplesUpdate = this.samplesUpdate;
            }

            bufferSize = RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO ? samplesUpdate : samplesUpdate * 2;
        }
    }

    void updateSamples(long samplesTime) {
        long ms = samplesTime / sampleRate * 1000;

        time.setText(MainApplication.formatDuration(this, ms));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 1:
                if (permitted(permissions)) {
                    startRecording();
                } else {
                    Toast.makeText(this, R.string.not_permitted, Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    public static final String[] PERMISSIONS = new String[]{
            Manifest.permission.RECORD_AUDIO
    };

    boolean permitted(String[] ss) {
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    boolean permitted() {
        for (String s : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
                return false;
            }
        }
        return true;
    }

    EncoderInfo getInfo() {
        final int channels = RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1;
        final int bps = RawSamples.AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT ? 16 : 8;

        return new EncoderInfo(channels, sampleRate, bps);
    }

    void encoding(final Runnable run) {
        final File in = storage.getTempRecording();
        final File out = targetFile;

        EncoderInfo info = getInfo();

        Encoder e = null;

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        String ext = shared.getString(MainApplication.PREFERENCE_ENCODING, "");

        if (ext.equals("wav")) {
            e = new FormatWAV(info, out);
        }
        if (ext.equals("m4a")) {
            e = new FormatM4A(info, out);
        }
        if (ext.equals("3gp")) {
            e = new Format3GP(info, out);
        }

        encoder = new FileEncoder(this, in, e);

        final ProgressDialog d = new ProgressDialog(this);
        d.setTitle(getString(R.string.encoding_title));
        d.setMessage(".../" + targetFile.getName());
        d.setMax(100);
        d.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        d.setIndeterminate(false);
        d.show();

        encoder.run(new Runnable() {
            @Override
            public void run() {
                d.setProgress(encoder.getProgress());
            }
        }, new Runnable() {
            @Override
            public void run() {
                d.cancel();
                storage.delete(in);

                SharedPreferences.Editor edit = shared.edit();
                edit.putString(MainApplication.PREFERENCE_LAST, out.getName());
                edit.commit();

                run.run();
            }
        }, new Runnable() {
            @Override
            public void run() {
                Toast.makeText(RecordingActivity.this, encoder.getException().getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    @Override
    public void finish() {
        super.finish();

        MainActivity.startActivity(this);
    }
}
