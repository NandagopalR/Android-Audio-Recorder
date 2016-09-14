package com.github.axet.audiorecorder.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

import com.github.axet.audiorecorder.R;
import com.github.axet.audiorecorder.activities.RecordingActivity;
import com.github.axet.audiorecorder.app.MainApplication;

/**
 * RecordingActivity more likly to be removed from memory when paused then service. Notification button
 * does not handle getActvity without unlocking screen. The only option is to have Service.
 * <p/>
 * So, lets have it.
 * <p/>
 * Maybe later this class will be converted for fully feature recording service with recording thread.
 */
public class RecordingService extends Service {
    public static final String TAG = RecordingService.class.getSimpleName();

    public static final int NOTIFICATION_RECORDING_ICON = 1;

    public static String SHOW_ACTIVITY = RecordingService.class.getCanonicalName() + ".SHOW_ACTIVITY";
    public static String PAUSE_BUTTON = RecordingService.class.getCanonicalName() + ".PAUSE_BUTTON";

    RecordingReceiver receiver;

    String targetFile;
    boolean recording;

    public class RecordingReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                // showRecordingActivity();
            }
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                // do nothing. do not annoy user. he will see alarm screen on next screen on event.
            }
        }
    }

    public static void startService(Context context, String targetFile, boolean recording) {
        context.startService(new Intent(context, RecordingService.class)
                .putExtra("targetFile", targetFile)
                .putExtra("recording", recording));
    }

    public static void stopService(Context context) {
        context.stopService(new Intent(context, RecordingService.class));
    }

    public RecordingService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        receiver = new RecordingReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(receiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        if (intent != null) {
            String a = intent.getAction();

            if (a == null) {
                targetFile = intent.getStringExtra("targetFile");
                recording = intent.getBooleanExtra("recording", false);
                showNotificationAlarm(true);
            } else if (a.equals(PAUSE_BUTTON)) {
                Intent i = new Intent(RecordingActivity.PAUSE_BUTTON);
                sendBroadcast(i);
            } else if (a.equals(SHOW_ACTIVITY)) {
                RecordingActivity.startActivity(this, false);
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public class Binder extends android.os.Binder {
        public RecordingService getService() {
            return RecordingService.this;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestory");

        showNotificationAlarm(false);

        unregisterReceiver(receiver);
    }

    // alarm dismiss button
    public void showNotificationAlarm(boolean show) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (!show) {
            notificationManager.cancel(NOTIFICATION_RECORDING_ICON);
        } else {
            PendingIntent main = PendingIntent.getService(this, 0,
                    new Intent(this, RecordingService.class).setAction(SHOW_ACTIVITY),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            PendingIntent pe = PendingIntent.getService(this, 0,
                    new Intent(this, RecordingService.class).setAction(PAUSE_BUTTON),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            RemoteViews view = new RemoteViews(getPackageName(), MainApplication.getTheme(getBaseContext(),
                    R.layout.notifictaion_recording_light,
                    R.layout.notifictaion_recording_dark));

            view.setOnClickPendingIntent(R.id.status_bar_latest_event_content, main);
            view.setTextViewText(R.id.notification_text, ".../" + targetFile);
            view.setOnClickPendingIntent(R.id.notification_pause, pe);
            view.setImageViewResource(R.id.notification_pause, !recording ? R.drawable.play : R.drawable.pause);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setOngoing(true)
                    .setContentTitle(getString(R.string.recording_title))
                    .setSmallIcon(R.drawable.ic_mic_24dp)
                    .setContent(view);

            if (Build.VERSION.SDK_INT >= 21)
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            notificationManager.notify(NOTIFICATION_RECORDING_ICON, builder.build());
        }
    }
}

