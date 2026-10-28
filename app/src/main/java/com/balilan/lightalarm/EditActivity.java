package com.balilan.lightalarm;

import android.app.AlertDialog;
import android.Manifest;
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
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 新增 / 编辑闹铃 */
public class EditActivity extends AppCompatActivity {

    private static final int REQ_AUDIO = 101;
    private static final int REQ_RECORD = 102;

    private AlarmStore store;
    private Alarm alarm;
    private TimePicker timePicker;
    private EditText etLabel;
    private EditText etMessage;
    private TextView tvSound;
    private TextView tvVolumeLabel;
    private SeekBar seekVolume;
    private Spinner spRingSeconds;
    private Spinner spSnooze;
    private Spinner spSoundMode;
    private final CheckBox[] dayBoxes = new CheckBox[7];

    private String soundUri = "";
    private String soundName = "系统默认闹铃";
    /** 试听播放器 */
    private MediaPlayer previewPlayer;
    /** 录音 */
    private MediaRecorder recorder;
    private File recordingFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

        store = new AlarmStore(this);
        long id = getIntent().getLongExtra("alarm_id", 0);
        if (id > 0) {
            alarm = store.find(id);
        }
        if (alarm == null) {
            alarm = new Alarm();
            alarm.id = System.currentTimeMillis();
            alarm.hour = 23;
            alarm.minute = 30;
        }

        TextView tvTitle = findViewById(R.id.tvEditTitle);
        tvTitle.setText(id > 0 ? "编辑闹铃" : "新增闹铃");

        timePicker = findViewById(R.id.timePicker);
        etLabel = findViewById(R.id.etLabel);
        etMessage = findViewById(R.id.etMessage);
        tvSound = findViewById(R.id.tvSound);
        tvVolumeLabel = findViewById(R.id.tvVolumeLabel);
        seekVolume = findViewById(R.id.seekVolume);
        spRingSeconds = findViewById(R.id.spRingSeconds);
        spSnooze = findViewById(R.id.spSnooze);
        spSoundMode = findViewById(R.id.spSoundMode);

        dayBoxes[0] = findViewById(R.id.day0);
        dayBoxes[1] = findViewById(R.id.day1);
        dayBoxes[2] = findViewById(R.id.day2);
        dayBoxes[3] = findViewById(R.id.day3);
        dayBoxes[4] = findViewById(R.id.day4);
        dayBoxes[5] = findViewById(R.id.day5);
        dayBoxes[6] = findViewById(R.id.day6);

        initTime();
        initDays();
        etLabel.setText(alarm.label);
        etMessage.setText(alarm.message);

        soundUri = alarm.soundUri == null ? "" : alarm.soundUri;
        soundName = alarm.soundName == null || alarm.soundName.length() == 0 ? "系统默认闹铃" : alarm.soundName;
        tvSound.setText(soundName);

        initSpinners();
        initVolume();

        findViewById(R.id.btnPickSound).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSoundDialog();
            }
        });

        Button btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });

        Button btnDelete = findViewById(R.id.btnDelete);
        btnDelete.setVisibility(id > 0 ? View.VISIBLE : View.GONE);
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete();
            }
        });
    }

    private void initTime() {
        timePicker.setIs24HourView(true);
        if (Build.VERSION.SDK_INT >= 23) {
            timePicker.setHour(alarm.hour);
            timePicker.setMinute(alarm.minute);
        } else {
            timePicker.setCurrentHour(alarm.hour);
            timePicker.setCurrentMinute(alarm.minute);
        }
    }

    private void initDays() {
        for (int i = 0; i < 7; i++) {
            dayBoxes[i].setChecked((alarm.days & (1 << i)) != 0);
        }
    }

    private void initSpinners() {
        ArrayAdapter<String> ringAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new String[]{"15 秒", "30 秒", "60 秒"});
        ringAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spRingSeconds.setAdapter(ringAdapter);
        spRingSeconds.setSelection(indexOf(new int[]{15, 30, 60}, alarm.ringSeconds));

        ArrayAdapter<String> snoozeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new String[]{"5 分钟", "10 分钟", "15 分钟"});
        snoozeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSnooze.setAdapter(snoozeAdapter);
        spSnooze.setSelection(indexOf(new int[]{5, 10, 15}, alarm.snoozeMinutes));

        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"闹铃外放（像系统闹钟，走扬声器）", "媒体音（连蓝牙耳机时走耳机，不外放）"});
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSoundMode.setAdapter(modeAdapter);
        spSoundMode.setSelection(alarm.soundMode == 1 ? 1 : 0);
    }

    private int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) return i;
        }
        return 0;
    }

    private void initVolume() {
        int volume = Math.max(0, Math.min(100, alarm.volume));
        seekVolume.setProgress(volume);
        tvVolumeLabel.setText("音量 " + volume + "%");
        seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvVolumeLabel.setText("音量 " + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void showSoundDialog() {
        final String[] items = {"系统默认闹铃（点选试听）", "从系统铃声中选择（可试听）",
                "从本地音频文件中选择", "我的录音（可自己录一段话）"};
        new AlertDialog.Builder(this)
                .setTitle("选择铃声")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            soundUri = "";
                            soundName = "系统默认闹铃";
                            tvSound.setText(soundName);
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

    /** 列出系统闹铃，点击任意一条立即试听，点“确定”采用 */
    private void showRingtoneList() {
        final List<Uri> uris = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        try {
            RingtoneManager rm = new RingtoneManager(this);
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
            Toast.makeText(this, "系统里没有可用的闹铃，请从本地音频选择", Toast.LENGTH_LONG).show();
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

        AlertDialog dlg = new AlertDialog.Builder(this)
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
                            tvSound.setText(soundName);
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

    /** 试听：切换到新铃声时先停掉旧的再播放 */
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
                p.setDataSource(this, uri);
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
            try {
                previewPlayer.stop();
            } catch (Exception ignored) {
            }
            try {
                previewPlayer.release();
            } catch (Exception ignored) {
            }
            previewPlayer = null;
        }
    }

    @Override
    protected void onDestroy() {
        stopPreview();
        cleanupRecorder(false);
        super.onDestroy();
    }

    /* ---------------- 我的录音 ---------------- */

    private File recordingsDir() {
        // 存到外部存储的应用目录，方便用文件管理器查找：
        // /storage/emulated/0/Android/data/com.balilan.lightalarm/files/recordings
        File dir = getExternalFilesDir("recordings");
        if (dir == null) dir = new File(getFilesDir(), "recordings");
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

    /** 录音列表：点选试听并采用，长按删除 */
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
        names.add("＋ 录一段新的话");
        for (File f : recs) names.add(displayNameOf(f));

        final AlertDialog dlg = new AlertDialog.Builder(this)
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
                        tvSound.setText(soundName);
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
                new AlertDialog.Builder(EditActivity.this)
                        .setMessage("删除这条录音「" + displayNameOf(f) + "」？")
                        .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d2, int w2) {
                                try {
                                    f.delete();
                                } catch (Exception ignored) {
                                }
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
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD);
            return;
        }
        showRecordDialog();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showRecordDialog();
            } else {
                Toast.makeText(this, "没有麦克风权限，无法录音", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showRecordDialog() {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        recordingFile = new File(recordingsDir(), "rec_" + stamp + ".m4a");
        try {
            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setOutputFile(recordingFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
        } catch (Exception e) {
            Toast.makeText(this, "无法开始录音：" + e.getMessage(), Toast.LENGTH_LONG).show();
            cleanupRecorder(false);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("正在录音…")
                .setMessage("说完后点「保存」")
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
                try {
                    f.delete();
                } catch (Exception ignored) {
                }
            }
            if (save) Toast.makeText(this, "录音太短，未保存", Toast.LENGTH_SHORT).show();
            return;
        }
        soundUri = Uri.fromFile(f).toString();
        soundName = displayNameOf(f);
        tvSound.setText(soundName);
        previewUri(Uri.fromFile(f));
        Toast.makeText(this, "录音已保存并选用", Toast.LENGTH_SHORT).show();
    }

    /** 停止并释放录音器（不动文件） */
    private void stopRecorder() {
        if (recorder == null) return;
        try {
            recorder.stop();
        } catch (Exception ignored) {
        }
        try {
            recorder.release();
        } catch (Exception ignored) {
        }
        recorder = null;
    }

    /** 异常/退出路径：丢掉正在录制的临时文件 */
    private void cleanupRecorder(boolean keepFile) {
        File f = recordingFile;
        recordingFile = null;
        stopRecorder();
        if (!keepFile && f != null) {
            try {
                f.delete();
            } catch (Exception ignored) {
            }
        }
    }

    private void pickLocalAudio() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("audio/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i, REQ_AUDIO);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQ_AUDIO) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            soundUri = uri.toString();
            soundName = queryDisplayName(uri);
            tvSound.setText(soundName);
            previewUri(uri);
        }
    }

    private String queryDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
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

    private void save() {
        int hour;
        int minute;
        if (Build.VERSION.SDK_INT >= 23) {
            hour = timePicker.getHour();
            minute = timePicker.getMinute();
        } else {
            hour = timePicker.getCurrentHour();
            minute = timePicker.getCurrentMinute();
        }
        alarm.hour = hour;
        alarm.minute = minute;

        int days = 0;
        for (int i = 0; i < 7; i++) {
            if (dayBoxes[i].isChecked()) days |= (1 << i);
        }
        alarm.days = days;

        String label = etLabel.getText().toString().trim();
        alarm.label = label.length() == 0 ? "该睡觉了" : label;

        String message = etMessage.getText().toString().trim();
        alarm.message = message.length() == 0 ? "放下手机吧，明天还要早起" : message;

        alarm.soundUri = soundUri;
        alarm.soundName = soundName;
        alarm.ringSeconds = new int[]{15, 30, 60}[spRingSeconds.getSelectedItemPosition()];
        alarm.snoozeMinutes = new int[]{5, 10, 15}[spSnooze.getSelectedItemPosition()];
        alarm.soundMode = spSoundMode.getSelectedItemPosition() == 1 ? 1 : 0;
        alarm.volume = seekVolume.getProgress();
        alarm.enabled = true;

        store.upsert(alarm);
        AlarmScheduler.cancelAll(this, alarm.id);
        AlarmScheduler.schedule(this, alarm);

        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setMessage("删除这个闹铃？")
                .setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        AlarmScheduler.cancelAll(EditActivity.this, alarm.id);
                        store.delete(alarm.id);
                        finish();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
