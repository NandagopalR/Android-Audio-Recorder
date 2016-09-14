package com.github.axet.audiorecorder.app;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.os.Build;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.github.axet.audiorecorder.R;
import com.github.axet.audiorecorder.activities.RecordingActivity;
import com.github.axet.audiorecorder.encoders.FormatM4A;
import com.github.axet.audiorecorder.encoders.FormatWAV;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Storage {
    public static final String TMP_REC = "recorind.data";
    public static final String RECORDINGS = "recordings";

    Context context;

    public Storage(Context context) {
        this.context = context;
    }

    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    public boolean permitted(String[] ss) {
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(context, s) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public File getLocalStorage() {
        return new File(context.getApplicationInfo().dataDir, RECORDINGS);
    }

    public boolean isLocalStorageEmpty() {
        return getLocalStorage().listFiles().length == 0;
    }

    public boolean isExternalStoragePermitted() {
        return permitted(PERMISSIONS);
    }

    public boolean recordingPending() {
        return getTempRecording().exists();
    }

    public File getStoragePath() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String path = shared.getString(MainApplication.PREFERENCE_STORAGE, "");
        if (permitted(PERMISSIONS)) {
            return new File(path);
        } else {
            return getLocalStorage();
        }
    }

    public void migrateLocalStorage() {
        if (!permitted(PERMISSIONS)) {
            return;
        }

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String path = shared.getString(MainApplication.PREFERENCE_STORAGE, "");

        File l = getLocalStorage();
        File t = new File(path);

        t.mkdirs();

        File[] ff = l.listFiles();

        if (ff == null)
            return;

        for (File f : ff) {
            File tt = getNextFile(t, f);
            move(f, tt);
        }
    }

    public File getNewFile() {
        SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String ext = shared.getString(MainApplication.PREFERENCE_ENCODING, "");

        File parent = getStoragePath();
        if (!parent.exists()) {
            if (!parent.mkdirs())
                throw new RuntimeException("Unable to create: " + parent);
        }

        return getNextFile(parent, s.format(new Date()), ext);
    }

    public static String getNameNoExt(File f) {
        String fileName = f.getName();

        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            fileName = fileName.substring(0, i);
        }
        return fileName;
    }

    public static String getExt(File f) {
        String fileName = f.getName();

        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            return fileName.substring(i + 1);
        }
        return "";
    }

    File getNextFile(File parent, File f) {
        String fileName = f.getName();

        String extension = "";

        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            extension = fileName.substring(i + 1);
            fileName = fileName.substring(0, i);
        }

        return getNextFile(parent, fileName, extension);
    }

    File getNextFile(File parent, String name, String ext) {
        String fileName;
        if (ext.isEmpty())
            fileName = name;
        else
            fileName = String.format("%s.%s", name, ext);

        File file = new File(parent, fileName);

        int i = 1;
        while (file.exists()) {
            fileName = String.format("%s (%d).%s", name, i, ext);
            file = new File(parent, fileName);
            i++;
        }

//        try {
//            file.createNewFile();
//        } catch (IOException e) {
//            throw new RuntimeException("Unable to create: " + file, e);
//        }

        return file;
    }

    public List<File> scan(File dir) {
        ArrayList<File> list = new ArrayList<>();

        File[] ff = dir.listFiles();
        if (ff == null)
            return list;

        for (File f : ff) {
            if (f.length() > 0) {
                String[] ee = context.getResources().getStringArray(R.array.encodings_values);
                String n = f.getName().toLowerCase();
                for (String e : ee) {
                    if (n.endsWith("." + e))
                        list.add(f);
                }
            }
        }

        return list;
    }

    // get average recording miliseconds based on compression format
    public long average(long free) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        int rate = Integer.parseInt(shared.getString(MainApplication.PREFERENCE_RATE, ""));
        String ext = shared.getString(MainApplication.PREFERENCE_ENCODING, "");

        if (ext.equals("m4a")) {
            long y1 = 365723; // one minute sample 16000Hz
            long x1 = 16000; // at 16000
            long y2 = 493743; // one minute sample
            long x2 = 44000; // at 44000
            long x = rate;
            long y = (x - x1) * (y2 - y1) / (x2 - x1) + y1;

            int m = RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO ? 1 : 2;
            long perSec = (y / 60) * m;
            return free / perSec * 1000;
        }

        // default raw
        int m = RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO ? 1 : 2;
        int c = RawSamples.AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT ? 2 : 1;
        long perSec = (c * m * rate);
        return free / perSec * 1000;
    }

    public long getFree(File f) {
        while (!f.exists())
            f = f.getParentFile();

        StatFs fsi = new StatFs(f.getPath());
        if (Build.VERSION.SDK_INT < 18)
            return fsi.getBlockSize() * fsi.getAvailableBlocks();
        else
            return fsi.getBlockSizeLong() * fsi.getAvailableBlocksLong();
    }

    public File getTempRecording() {
        File internal = new File(context.getApplicationInfo().dataDir, TMP_REC);

        if (internal.exists())
            return internal;

        // Starting in KITKAT, no permissions are required to read or write to the returned path;
        // it's always accessible to the calling app.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            if (!permitted(PERMISSIONS))
                return internal;
        }

        File external = new File(context.getExternalCacheDir(), TMP_REC);

        if (external.exists())
            return external;

        long freeI = getFree(internal);
        long freeE = getFree(external);

        if (freeI > freeE)
            return internal;
        else
            return external;
    }

    public FileOutputStream open(File f) {
        File tmp = f;
        File parent = tmp.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new RuntimeException("unable to create: " + parent);
        }
        if (!parent.isDirectory())
            throw new RuntimeException("target is not a dir: " + parent);
        try {
            return new FileOutputStream(tmp, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(File f) {
        f.delete();
    }

    public void move(File f, File to) {
        try {
            InputStream in = new FileInputStream(f);
            OutputStream out = new FileOutputStream(to);

            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
            f.delete();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
