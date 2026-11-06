package com.balilan.lightalarm;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 铃声选择公共组件：从系统铃声/本地文件/我的录音里选，自带试听 + 录音。
 * 用法：
 *   picker = new SoundPicker(activity, currentUri, currentName, callback);
 *   picker.show();        // 弹出选择框
 *   picker.destroy();     // Activity onDestroy 时调，释放播放器/录音器
 */
public class SoundPicker {

    private static final int REQ_AUDIO = 101;
    private static final int REQ_RECORD = 102;

    public interface OnPicked {
        void onPicked(String uri, String name);
    }

    private final Activity activity;
    private final OnPicked callback;
    private String soundUri;
    private String soundName;
    private MediaPlayer previewPlayer;
    private MediaRecorder recorder;
    private File recordingFile;

    /**
     * @param activity  用于弹 Dialog / 跳转文件选择器 / 请求权限
     * @param uri       当前铃声 URI（空字符串=系统默认）
     * @param name      当前铃声显示名
     * @param callback  选定后回调
     */
    public SoundPicker(Activity activity, String uri, String name, OnPicked callback) {
        this.activity = activity;
        this.callback = callback;
        this.soundUri = uri == null ? "" : uri;
        this.soundName = (name == null || name.length() == 0) ? "系统默认闹铃" : name;
    }

    public String getUri() { return soundUri; }
    public String getName() { return soundName; }

    /** 弹出铃声选择对话框 */
    public void show() {
        final String[] items = {"系统默认闹铃（点选试听）", "从系统铃声中选择（可试听）",
                "从本地音频文件中选择", "我的录音（可自己录一段话）"};
        new AlertDialog.Builder(activity)
                .setTitle("选择铃声")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            soundUri = "";
                            soundName = "系统默认闹铃";
                            callback.onPicked(soundUri, soundName);
                            previewUri(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
                        } else if (which == 1) {
                            showRingtoneList();
                        } else if (which == 2) {
                            stopPreview();
                            pickLocalAudio();
                        } else {
                            showRecordingsDialog();
                        }
                    }
                })
                .show();
    }

    public void destroy() {
        stopPreview();
        cleanupRecorder(false);
    }

    /* ---------------- 系统铃声列表 ---------------- */

    private void showRingtoneList() {
        final List<Uri> uris = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        try {
            RingtoneManager rm = new RingtoneManager(activity);
            rm.setType(RingtoneManager.TYPE_ALARM);
            Cursor c = rm.getCursor();
            if (c != null) {
                while (c.moveToNext()) {
                    Uri u = rm.getRingtoneUri(c.getPosition());
                    if (u == null) continue;
                    String title = c.getString(RingtoneManager.TITLE_COLUMN_INDEX);
                    if (title == null || title.length() == 0) title = "铃声 " + (uris.size() + 1);
                    uris.add(u);
                    names.add(title);
                }
                c.close();
            }
        } catch (Exception ignored) {
        }
        if (uris.isEmpty()) {
            Toast.makeText(activity, "系统里没有可用的闹铃，请从本地音频选择", Toast.LENGTH_LONG).show();
            return;
        }

        int checked = -1;
        for (int i = 0; i < uris.size(); i++) {
            if (uris.get(i).toString().equals(soundUri)) {
                checked = i;
                break;
            }
        }
        final int[] selected = {checked};

        AlertDialog dlg = new AlertDialog.Builder(activity)
                .setTitle("选择闹铃（点一下试听）")
                .setSingleChoiceItems(names.toArray(new String[0]), checked,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                selected[0] = which;
                                previewUri(uris.get(which));
                            }
                        })
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (selected[0] >= 0) {
                            soundUri = uris.get(selected[0]).toString();
                            soundName = names.get(selected[0]);
                            callback.onPicked(soundUri, soundName);
                        }
                        stopPreview();
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        stopPreview();
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface d) {
                        stopPreview();
                    }
                })
                .create();
        dlg.show();
        if (checked >= 0) previewUri(uris.get(checked));
    }

    /* ---------------- 本地文件 ---------------- */

    private void pickLocalAudio() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("audio/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            activity.startActivityForResult(i, REQ_AUDIO);
        } catch (Exception e) {
            Toast.makeText(activity, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_AUDIO && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    activity.getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }
                soundUri = uri.toString();
                soundName = queryDisplayName(uri);
                callback.onPicked(soundUri, soundName);
                previewUri(uri);
            }
            return true;
        }
        if (requestCode == REQ_RECORD && resultCode == Activity.RESULT_OK) {
            // REQ_RECORD 权限请求结果在 onRequestPermissionsResult 里处理
            return true;
        }
        return false;
    }

    private String queryDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && name.length() > 0) return name;
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return "已选音频";
    }

    /* ---------------- 试听 ---------------- */

    private void previewUri(Uri uri) {
        stopPreview();
        if (uri == null) return;
        MediaPlayer p = new MediaPlayer();
        try {
            p.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            if ("file".equals(uri.getScheme())) {
                p.setDataSource(uri.getPath());
            } else {
                p.setDataSource(activity, uri);
            }
            p.prepare();
            p.start();
            previewPlayer = p;
        } catch (Exception e) {
            try {
                p.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void stopPreview() {
        if (previewPlayer != null) {
            try { previewPlayer.stop(); } catch (Exception ignored) {}
            try { previewPlayer.release(); } catch (Exception ignored) {}
            previewPlayer = null;
        }
    }

    /* ---------------- 录音 ---------------- */

    private File recordingsDir() {
        File dir = activity.getExternalFilesDir("recordings");
        if (dir == null) dir = new File(activity.getFilesDir(), "recordings");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private String displayNameOf(File f) {
        String n = f.getName();
        try {
            if (n.startsWith("rec_") && n.endsWith(".m4a")) {
                String s = n.substring(4, n.length() - 4);
                Date d = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(s);
                if (d != null) {
                    return "录音 " + new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(d);
                }
            }
        } catch (Exception ignored) {
        }
        return n;
    }

    private void showRecordingsDialog() {
        File[] files = recordingsDir().listFiles();
        final List<File> recs = new ArrayList<>();
        if (files != null) {
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return Long.compare(b.lastModified(), a.lastModified());
                }
            });
            for (File f : files) {
                if (f.isFile() && f.getName().endsWith(".m4a")) recs.add(f);
            }
        }
        List<String> names = new ArrayList<>();
        names.add("\uff0b 录一段新的话");
        for (File f : recs) names.add(displayNameOf(f));

        final AlertDialog dlg = new AlertDialog.Builder(activity)
                .setTitle("我的录音（点一下试听，长按可删除）")
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            startRecordFlow();
                            return;
                        }
                        File f = recs.get(which - 1);
                        soundUri = Uri.fromFile(f).toString();
                        soundName = displayNameOf(f);
                        callback.onPicked(soundUri, soundName);
                        previewUri(Uri.fromFile(f));
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface d) {
                        stopPreview();
                    }
                })
                .create();
        dlg.show();
        final List<File> recsRef = recs;
        dlg.getListView().setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) return true;
                final File f = recsRef.get(position - 1);
                new AlertDialog.Builder(activity)
                        .setMessage("删除这条录音\u201c" + displayNameOf(f) + "\u201d？")
                        .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d2, int w2) {
                                try { f.delete(); } catch (Exception ignored) {}
                                dlg.dismiss();
                                showRecordingsDialog();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return true;
            }
        });
    }

    private void startRecordFlow() {
        if (Build.VERSION.SDK_INT >= 23
                && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD);
            return;
        }
        showRecordDialog();
    }

    public boolean onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode == REQ_RECORD) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showRecordDialog();
            } else {
                Toast.makeText(activity, "没有麦克风权限，无法录音", Toast.LENGTH_LONG).show();
            }
            return true;
        }
        return false;
    }

    private void showRecordDialog() {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        recordingFile = new File(recordingsDir(), "rec_" + stamp + ".m4a");
        try {
            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(activity) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setOutputFile(recordingFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
        } catch (Exception e) {
            Toast.makeText(activity, "无法开始录音：" + e.getMessage(), Toast.LENGTH_LONG).show();
            cleanupRecorder(false);
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("正在录音\u2026")
                .setMessage("说完后点\u201c保存\u201d")
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        finishRecording(true);
                    }
                })
                .setNegativeButton("放弃", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        finishRecording(false);
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void finishRecording(boolean save) {
        File f = recordingFile;
        recordingFile = null;
        stopRecorder();
        boolean ok = save && f != null && f.exists() && f.length() > 0;
        if (!ok) {
            if (f != null) {
                try { f.delete(); } catch (Exception ignored) {}
            }
            if (save) Toast.makeText(activity, "录音太短，未保存", Toast.LENGTH_SHORT).show();
            return;
        }
        soundUri = Uri.fromFile(f).toString();
        soundName = displayNameOf(f);
        callback.onPicked(soundUri, soundName);
        previewUri(Uri.fromFile(f));
        Toast.makeText(activity, "录音已保存并选用", Toast.LENGTH_SHORT).show();
    }

    private void stopRecorder() {
        if (recorder == null) return;
        try { recorder.stop(); } catch (Exception ignored) {}
        try { recorder.release(); } catch (Exception ignored) {}
        recorder = null;
    }

    private void cleanupRecorder(boolean keepFile) {
        File f = recordingFile;
        recordingFile = null;
        stopRecorder();
        if (!keepFile && f != null) {
            try { f.delete(); } catch (Exception ignored) {}
        }
    }
}
