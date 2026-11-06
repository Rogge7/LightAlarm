package com.balilan.lightalarm;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
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

/** 新增 / 编辑闹铃 */
public class EditActivity extends AppCompatActivity {

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
    private CheckBox cbIgnore;

    private SoundPicker soundPicker;

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
        findViewById(R.id.btnPhrases).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Phrases.showPicker(EditActivity.this, etMessage);
            }
        });
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

        tvSound.setText(alarm.soundName == null || alarm.soundName.length() == 0
                ? "系统默认闹铃" : alarm.soundName);

        initSpinners();
        initVolume();

        cbIgnore = findViewById(R.id.cbIgnoreScreenCond);
        cbIgnore.setChecked(alarm.ignoreScreenCond);

        soundPicker = new SoundPicker(this,
                alarm.soundUri == null ? "" : alarm.soundUri,
                alarm.soundName,
                new SoundPicker.OnPicked() {
                    @Override
                    public void onPicked(String uri, String name) {
                        tvSound.setText(name);
                    }
                });

        findViewById(R.id.btnPickSound).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                soundPicker.show();
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        soundPicker.handleActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        soundPicker.onRequestPermissionsResult(requestCode, grantResults);
    }

    @Override
    protected void onDestroy() {
        if (soundPicker != null) soundPicker.destroy();
        super.onDestroy();
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
        alarm.label = label.length() == 0 ? "提醒" : label;

        String message = etMessage.getText().toString().trim();
        alarm.message = message.length() == 0 ? "今天到这儿吧，睡个好觉，明天见" : message;

        alarm.soundUri = soundPicker.getUri();
        alarm.soundName = soundPicker.getName();
        alarm.ringSeconds = new int[]{15, 30, 60}[spRingSeconds.getSelectedItemPosition()];
        alarm.snoozeMinutes = new int[]{5, 10, 15}[spSnooze.getSelectedItemPosition()];
        alarm.soundMode = spSoundMode.getSelectedItemPosition() == 1 ? 1 : 0;
        alarm.volume = seekVolume.getProgress();
        alarm.enabled = true;
        alarm.ignoreScreenCond = cbIgnore.isChecked();

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
