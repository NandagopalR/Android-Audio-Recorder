package com.github.axet.audiorecorder.activities;

import android.Manifest;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.audiorecorder.R;
import com.github.axet.audiorecorder.animations.RecordingAnimation;
import com.github.axet.androidlibrary.animations.RemoveItemAnimation;
import com.github.axet.audiorecorder.app.MainApplication;
import com.github.axet.audiorecorder.app.Storage;
import com.github.axet.androidlibrary.widgets.PopupShareActionProvider;
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends AppCompatActivity implements AbsListView.OnScrollListener {
    public final static String TAG = MainActivity.class.getSimpleName();

    static final int TYPE_COLLAPSED = 0;
    static final int TYPE_EXPANDED = 1;
    static final int TYPE_DELETED = 2;

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    final int[] ALL = {TYPE_COLLAPSED, TYPE_EXPANDED};

    int scrollState;

    Recordings recordings;
    Storage storage;
    ListView list;
    Handler handler;
    PopupShareActionProvider shareProvider;

    int themeId;

    public static void startActivity(Context context) {
        Intent i = new Intent(context, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(i);
    }

    static class SortFiles implements Comparator<File> {
        @Override
        public int compare(File file, File file2) {
            if (file.isDirectory() && file2.isFile())
                return -1;
            else if (file.isFile() && file2.isDirectory())
                return 1;
            else
                return file.getPath().compareTo(file2.getPath());
        }
    }

    public class Recordings extends ArrayAdapter<File> {
        MediaPlayer player;
        Runnable updatePlayer;
        int selected = -1;

        Map<File, Integer> durations = new TreeMap<>();

        public Recordings(Context context) {
            super(context, 0);
        }

        public void scan(File dir) {
            setNotifyOnChange(false);
            clear();
            durations.clear();

            List<File> ff = storage.scan(dir);

            for (File f : ff) {
                if (f.isFile()) {
                    MediaPlayer mp = MediaPlayer.create(getContext(), Uri.fromFile(f));
                    if (mp != null) {
                        int d = mp.getDuration();
                        mp.release();
                        durations.put(f, d);
                        add(f);
                    } else {
                        Log.e(TAG, f.toString());
                    }
                }
            }

            sort(new SortFiles());
            notifyDataSetChanged();
        }

        public void close() {
            if (player != null) {
                player.release();
                player = null;
            }
            if (updatePlayer != null) {
                handler.removeCallbacks(updatePlayer);
                updatePlayer = null;
            }
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(getContext());

            if (convertView == null) {
                convertView = inflater.inflate(R.layout.recording, parent, false);
                convertView.setTag(-1);
            }

            final View view = convertView;
            final View base = convertView.findViewById(R.id.recording_base);

            if ((int) convertView.getTag() == TYPE_DELETED) {
                RemoveItemAnimation.restore(base);
                convertView.setTag(-1);
            }

            final File f = getItem(position);

            TextView title = (TextView) convertView.findViewById(R.id.recording_title);
            title.setText(f.getName());

            SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            TextView time = (TextView) convertView.findViewById(R.id.recording_time);
            time.setText(s.format(new Date(f.lastModified())));

            TextView dur = (TextView) convertView.findViewById(R.id.recording_duration);
            dur.setText(MainApplication.formatDuration(getContext(), durations.get(f)));

            TextView size = (TextView) convertView.findViewById(R.id.recording_size);
            size.setText(MainApplication.formatSize(getContext(), f.length()));

            final View playerBase = convertView.findViewById(R.id.recording_player);
            playerBase.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                }
            });

            final Runnable delete = new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle(R.string.delete_recording);
                    builder.setMessage("...\\" + f.getName() + "\n\n" + getString(R.string.are_you_sure));
                    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            playerStop();
                            dialog.cancel();
                            RemoveItemAnimation.apply(list, base, new Runnable() {
                                @Override
                                public void run() {
                                    f.delete();
                                    view.setTag(TYPE_DELETED);
                                    select(-1);
                                    load();
                                }
                            });
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
            };

            final Runnable rename = new Runnable() {
                @Override
                public void run() {
                    final OpenFileDialog.EditTextDialog e = new OpenFileDialog.EditTextDialog(getContext());
                    e.setTitle(getString(R.string.rename_recording));
                    e.setText(Storage.getNameNoExt(f));
                    e.setPositiveButton(new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String ext = Storage.getExt(f);
                            String s = String.format("%s.%s", e.getText(), ext);
                            File ff = new File(f.getParent(), s);
                            f.renameTo(ff);
                            load();
                        }
                    });
                    e.show();

                }
            };

            if (selected == position) {
                RecordingAnimation.apply(list, convertView, true, scrollState == SCROLL_STATE_IDLE && (int) convertView.getTag() == TYPE_COLLAPSED);
                convertView.setTag(TYPE_EXPANDED);

                updatePlayerText(convertView, f);

                final View play = convertView.findViewById(R.id.recording_player_play);
                play.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (player == null) {
                            playerPlay(playerBase, f);
                        } else if (player.isPlaying()) {
                            playerPause(playerBase, f);
                        } else {
                            playerPlay(playerBase, f);
                        }
                    }
                });

                final View edit = convertView.findViewById(R.id.recording_player_edit);
                edit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        rename.run();
                    }
                });

                final View share = convertView.findViewById(R.id.recording_player_share);
                share.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        shareProvider = new PopupShareActionProvider(getContext(), share);

                        Intent emailIntent = new Intent(Intent.ACTION_SEND);
                        emailIntent.setType("audio/mp4a-latm");
                        emailIntent.putExtra(Intent.EXTRA_EMAIL, "");
                        emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
                        emailIntent.putExtra(Intent.EXTRA_SUBJECT, f.getName());
                        emailIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.shared_via, getString(R.string.app_name)));

                        shareProvider.setShareIntent(emailIntent);

                        shareProvider.show();
                    }
                });

                View trash = convertView.findViewById(R.id.recording_player_trash);
                trash.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        delete.run();
                    }
                });

                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        select(-1);
                    }
                });
            } else {
                RecordingAnimation.apply(list, convertView, false, scrollState == SCROLL_STATE_IDLE && (int) convertView.getTag() == TYPE_EXPANDED);
                convertView.setTag(TYPE_COLLAPSED);

                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        select(position);
                    }
                });
            }

            convertView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    PopupMenu popup = new PopupMenu(getContext(), v);
                    MenuInflater inflater = popup.getMenuInflater();
                    inflater.inflate(R.menu.menu_context, popup.getMenu());
                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            if (item.getItemId() == R.id.action_delete) {
                                delete.run();
                                return true;
                            }
                            if (item.getItemId() == R.id.action_rename) {
                                rename.run();
                                return true;
                            }
                            return false;
                        }
                    });
                    popup.show();
                    return true;
                }
            });

            return convertView;
        }

        void playerPlay(View v, File f) {
            if (player == null)
                player = MediaPlayer.create(getContext(), Uri.fromFile(f));
            if (player == null) {
                Toast.makeText(MainActivity.this, R.string.file_not_found, Toast.LENGTH_SHORT).show();
                return;
            }
            player.start();

            updatePlayerRun(v, f);
        }

        void playerPause(View v, File f) {
            if (player != null) {
                player.pause();
            }
            if (updatePlayer != null) {
                handler.removeCallbacks(updatePlayer);
                updatePlayer = null;
            }
            updatePlayerText(v, f);
        }

        void playerStop() {
            if (updatePlayer != null) {
                handler.removeCallbacks(updatePlayer);
                updatePlayer = null;
            }
            if (player != null) {
                player.stop();
                player.release();
                player = null;
            }
        }

        void updatePlayerRun(final View v, final File f) {
            boolean playing = updatePlayerText(v, f);

            if (updatePlayer != null) {
                handler.removeCallbacks(updatePlayer);
                updatePlayer = null;
            }

            if (!playing) {
                return;
            }

            updatePlayer = new Runnable() {
                @Override
                public void run() {
                    updatePlayerRun(v, f);
                }
            };
            handler.postDelayed(updatePlayer, 200);
        }

        boolean updatePlayerText(final View v, final File f) {
            ImageView i = (ImageView) v.findViewById(R.id.recording_player_play);

            final boolean playing = player != null && player.isPlaying();

            i.setImageResource(playing ? R.drawable.pause : R.drawable.play);

            TextView start = (TextView) v.findViewById(R.id.recording_player_start);
            SeekBar bar = (SeekBar) v.findViewById(R.id.recording_player_seek);
            TextView end = (TextView) v.findViewById(R.id.recording_player_end);

            int c = 0;
            int d = durations.get(f);

            if (player != null) {
                c = player.getCurrentPosition();
                d = player.getDuration();
            }

            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser)
                        return;

                    if (player == null)
                        playerPlay(v, f);

                    if (player != null) {
                        player.seekTo(progress);
                        if (!player.isPlaying())
                            playerPlay(v, f);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });

            start.setText(MainApplication.formatDuration(getContext(), c));
            bar.setMax(d);
            bar.setKeyProgressIncrement(1);
            bar.setProgress(c);
            end.setText("-" + MainApplication.formatDuration(getContext(), d - c));

            return playing;
        }

        public void select(int pos) {
            selected = pos;
            notifyDataSetChanged();
            playerStop();
        }
    }

    public void setAppTheme(int id) {
        super.setTheme(id);

        themeId = id;
    }

    int getAppTheme() {
        return MainApplication.getTheme(this, R.style.AppThemeLight_NoActionBar, R.style.AppThemeDark_NoActionBar);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setAppTheme(getAppTheme());

        setContentView(R.layout.activity_main);

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();

        storage = new Storage(this);
        handler = new Handler();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        if (Build.VERSION.SDK_INT >= 16)
            toolbar.setBackground(new ColorDrawable(MainApplication.getActionbarColor(this)));
        else
            toolbar.setBackgroundDrawable(new ColorDrawable(MainApplication.getActionbarColor(this)));

        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recordings.select(-1);
                RecordingActivity.startActivity(MainActivity.this, false);
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
            }
        });

        recordings = new Recordings(this);

        list = (ListView) findViewById(R.id.list);
        list.setOnScrollListener(this);
        list.setAdapter(recordings);
        list.setEmptyView(findViewById(R.id.empty_list));

        if (permitted()) {
            storage.migrateLocalStorage();
        }
    }

    void checkPending() {
        if (storage.recordingPending()) {
            RecordingActivity.startActivity(MainActivity.this, true);
            return;
        }
    }

    // load recordings
    void load() {
        recordings.scan(storage.getStoragePath());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.menu_main, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar base clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        if (id == R.id.action_show_folder) {
            Uri selectedUri = Uri.fromFile(storage.getStoragePath());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(selectedUri, "resource/folder");
            if (intent.resolveActivityInfo(getPackageManager(), 0) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, R.string.no_folder_app, Toast.LENGTH_SHORT).show();
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        if (themeId != getAppTheme()) {
            finish();
            MainActivity.startActivity(this);
            return;
        }

        if (permitted(PERMISSIONS))
            load();
        else
            load();

        checkPending();

        updateHeader();

        final int selected = getLastRecording();
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (selected != -1) {
                    recordings.select(selected);
                    list.smoothScrollToPosition(selected);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            list.setSelection(selected);
                        }
                    });
                }
            }
        });
    }

    int getLastRecording() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        String last = shared.getString(MainApplication.PREFERENCE_LAST, "");
        last = last.toLowerCase();

        for (int i = 0; i < recordings.getCount(); i++) {
            File f = recordings.getItem(i);
            String n = f.getName().toLowerCase();
            if (n.equals(last)) {
                SharedPreferences.Editor edit = shared.edit();
                edit.putString(MainApplication.PREFERENCE_LAST, "");
                edit.commit();
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                if (permitted(permissions)) {
                    storage.migrateLocalStorage();
                    load();
                    checkPending();
                } else {
                    Toast.makeText(this, R.string.not_permitted, Toast.LENGTH_SHORT).show();
                }
        }
    }

    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

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

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        this.scrollState = scrollState;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        handler.post(new Runnable() {
            @Override
            public void run() {
                list.smoothScrollToPosition(recordings.selected);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        recordings.close();
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://com.github.axet.audiorecorder/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }


    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://com.github.axet.audiorecorder/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }

    void updateHeader() {
        File f = storage.getStoragePath();
        long free = storage.getFree(f);
        long sec = storage.average(free);
        TextView text = (TextView) findViewById(R.id.space_left);
        text.setText(((MainApplication) getApplication()).formatFree(free, sec));
    }
}
