package com.balilan.lightalarm;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
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

    private int startMin;
    private int endMin;

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
                new String[]{"闹铃外放", "媒体音（耳机）"});
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSoundMode.setAdapter(modeAdapter);
        spSoundMode.setSelection(cs.getSoundMode() == 1 ? 1 : 0);

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
        return 2; // 默认 15 分钟
    }

    private void save() {
        cs.setEnabled(switchEnable.isChecked());
        cs.setWindow(startMin, endMin);
        cs.setIntervalMin(new int[]{5, 10, 15, 20, 30}[spInterval.getSelectedItemPosition()]);
        cs.setSoundMode(spSoundMode.getSelectedItemPosition() == 1 ? 1 : 0);
        String msg = etMessage.getText().toString().trim();
        cs.setMessage(msg.length() == 0 ? "夜深了，手机可以明天再玩" : msg);

        AlarmScheduler.scheduleCurfew(this);
        Toast.makeText(this, switchEnable.isChecked() ? "宵禁模式已开启" : "宵禁模式已关闭",
                Toast.LENGTH_SHORT).show();
        finish();
    }
}
