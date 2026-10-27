package com.balilan.lightalarm;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.Ringtone;
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

import java.util.ArrayList;
import java.util.List;

/** 新增 / 编辑闹铃 */
public class EditActivity extends AppCompatActivity {

    private static final int REQ_AUDIO = 101;

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
    private final CheckBox[] dayBoxes = new CheckBox[7];

    private String soundUri = "";
    private String soundName = "系统默认闹铃";
    /** 试听用的铃声对象 */
    private Ringtone previewRingtone;

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
        final String[] items = {"系统默认闹铃（点选试听）", "从系统铃声中选择（可试听）", "从本地音频文件中选择"};
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
                        } else {
                            stopPreview();
                            pickLocalAudio();
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
        try {
            Ringtone r = RingtoneManager.getRingtone(this, uri);
            if (r == null) return;
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            r.setAudioAttributes(attrs);
            r.play();
            previewRingtone = r;
        } catch (Exception ignored) {
        }
    }

    private void stopPreview() {
        if (previewRingtone != null) {
            try {
                if (previewRingtone.isPlaying()) previewRingtone.stop();
            } catch (Exception ignored) {
            }
            previewRingtone = null;
        }
    }

    @Override
    protected void onDestroy() {
        stopPreview();
        super.onDestroy();
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
