package com.balilan.lightalarm;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/** 宵禁时段模式设置页 */
public class CurfewActivity extends AppCompatActivity {

    private CurfewStore cs;
    private Switch switchEnable;
    private TextView btnStart;
    private TextView btnEnd;
    private Spinner spInterval;
    private Spinner spSoundMode;
    private EditText etMessage;

    private TextView tvSound;
    private Spinner spRingSeconds;
    private SeekBar seekVolume;
    private TextView tvVolumeLabel;
    private CheckBox cbVoice;
    private CheckBox cbIgnore;

    private int startMin;
    private int endMin;

    private SoundPicker soundPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_curfew);

        cs = new CurfewStore(this);
        startMin = cs.getStartMin();
        endMin = cs.getEndMin();

        switchEnable = findViewById(R.id.switchCurfewEnable);
        btnStart = findViewById(R.id.btnCurfewStart);
        btnEnd = findViewById(R.id.btnCurfewEnd);
        spInterval = findViewById(R.id.spCurfewInterval);
        spSoundMode = findViewById(R.id.spCurfewSoundMode);
        etMessage = findViewById(R.id.etCurfewMessage);

        // 新增的声音控件
        tvSound = findViewById(R.id.tvCurfewSound);
        spRingSeconds = findViewById(R.id.spCurfewRingSeconds);
        seekVolume = findViewById(R.id.seekCurfewVolume);
        tvVolumeLabel = findViewById(R.id.tvCurfewVolumeLabel);
        cbVoice = findViewById(R.id.cbCurfewVoice);
        cbIgnore = findViewById(R.id.cbCurfewIgnoreScreen);

        findViewById(R.id.btnCurfewPhrases).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Phrases.showPicker(CurfewActivity.this, etMessage);
            }
        });

        switchEnable.setChecked(cs.isEnabled());
        etMessage.setText(cs.getMessage());

        ArrayAdapter<String> intervalAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"每 5 分钟", "每 10 分钟", "每 15 分钟", "每 20 分钟", "每 30 分钟"});
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spInterval.setAdapter(intervalAdapter);
        spInterval.setSelection(indexOf(new int[]{5, 10, 15, 20, 30}, cs.getIntervalMin()));

        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"闹铃外放（像系统闹钟，走扬声器）", "媒体音（连蓝牙耳机时走耳机，不外放）"});
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSoundMode.setAdapter(modeAdapter);
        spSoundMode.setSelection(cs.getSoundMode() == 1 ? 1 : 0);

        // 响铃时长 spinner
        ArrayAdapter<String> ringAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"5 秒", "10 秒", "15 秒", "30 秒", "60 秒"});
        ringAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spRingSeconds.setAdapter(ringAdapter);
        spRingSeconds.setSelection(indexOf(new int[]{5, 10, 15, 30, 60}, cs.getRingSeconds()));

        // 音量条
        int vol = Math.max(0, Math.min(100, cs.getVolume()));
        seekVolume.setProgress(vol);
        tvVolumeLabel.setText("音量 " + vol + "%");
        seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvVolumeLabel.setText("音量 " + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        cbVoice.setChecked(cs.isVoiceEnabled());
        cbIgnore.setChecked(cs.getIgnoreScreenCond());

        tvSound.setText(cs.getSoundName());

        soundPicker = new SoundPicker(this,
                cs.getSoundUri(),
                cs.getSoundName(),
                new SoundPicker.OnPicked() {
                    @Override
                    public void onPicked(String uri, String name) {
                        tvSound.setText(name);
                    }
                });

        findViewById(R.id.btnCurfewPickSound).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                soundPicker.show();
            }
        });

        renderTimes();
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickTime(true);
            }
        });
        btnEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickTime(false);
            }
        });

        Button btnSave = findViewById(R.id.btnCurfewSave);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
    }

    private void renderTimes() {
        btnStart.setText(String.format(Locale.getDefault(), "%02d:%02d", startMin / 60, startMin % 60));
        btnEnd.setText(String.format(Locale.getDefault(), "%02d:%02d", endMin / 60, endMin % 60));
    }

    private void pickTime(final boolean isStart) {
        int initial = isStart ? startMin : endMin;
        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                if (isStart) {
                    startMin = hourOfDay * 60 + minute;
                } else {
                    endMin = hourOfDay * 60 + minute;
                }
                renderTimes();
            }
        }, initial / 60, initial % 60, true).show();
    }

    private int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) return i;
        }
        return 1; // 默认 10 秒 / 10 分钟（取决于传入数组）
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
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
        cs.setEnabled(switchEnable.isChecked());
        cs.setWindow(startMin, endMin);
        cs.setIntervalMin(new int[]{5, 10, 15, 20, 30}[spInterval.getSelectedItemPosition()]);
        cs.setSoundMode(spSoundMode.getSelectedItemPosition() == 1 ? 1 : 0);

        cs.setSoundUri(soundPicker.getUri());
        cs.setSoundName(soundPicker.getName());
        cs.setRingSeconds(new int[]{5, 10, 15, 30, 60}[spRingSeconds.getSelectedItemPosition()]);
        cs.setVolume(seekVolume.getProgress());
        cs.setVoiceEnabled(cbVoice.isChecked());
        cs.setIgnoreScreenCond(cbIgnore.isChecked());

        String msg = etMessage.getText().toString().trim();
        cs.setMessage(msg.length() == 0 ? "夜深了，手机可以明天再玩" : msg);

        AlarmScheduler.scheduleCurfew(this);
        Toast.makeText(this, switchEnable.isChecked() ? "宵禁模式已开启" : "宵禁模式已关闭",
                Toast.LENGTH_SHORT).show();
        finish();
    }
}
