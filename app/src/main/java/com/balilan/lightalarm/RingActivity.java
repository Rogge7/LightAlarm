package com.balilan.lightalarm;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 全屏提醒页：显示时间、标签、贪睡与关闭 */
public class RingActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Alarm alarm;
    private TextView tvCountdown;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (alarm == null || tvCountdown == null) return;
            long total = Math.max(5, alarm.ringSeconds) * 1000L;
            long left = total - (System.currentTimeMillis() - startAt);
            if (left > 0) {
                tvCountdown.setText("铃声将在 " + ((left + 999) / 1000) + " 秒后自动停止");
                handler.postDelayed(this, 500);
            } else {
                tvCountdown.setText("铃声已停止，请点击下方按钮");
            }
        }
    };

    private long startAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowOnLockScreen();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_ring);

        long id = getIntent().getLongExtra(AlarmScheduler.EXTRA_ID, -1);
        AlarmStore store = new AlarmStore(this);
        alarm = store.find(id);
        if (alarm == null) {
            alarm = new Alarm();
        }

        TextView tvTime = findViewById(R.id.tvRingTime);
        tvTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));

        TextView tvLabel = findViewById(R.id.tvRingLabel);
        tvLabel.setText(alarm.label);

        TextView tvDays = findViewById(R.id.tvRingDays);
        tvDays.setText(alarm.daysText());

        TextView tvMessage = findViewById(R.id.tvRingMessage);
        tvMessage.setText(alarm.message);

        tvCountdown = findViewById(R.id.tvRingCountdown);

        Button btnSnooze = findViewById(R.id.btnSnooze);
        btnSnooze.setText("延迟 " + alarm.snoozeMinutes + " 分钟");
        btnSnooze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (alarm != null) {
                    AlarmScheduler.snooze(RingActivity.this, alarm, alarm.snoozeMinutes);
                }
                stopRing();
                finish();
            }
        });

        Button btnAck = findViewById(R.id.btnAck);
        btnAck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRing();
                finish();
            }
        });

        startAt = System.currentTimeMillis();
        handler.post(tickRunnable);
    }

    private void setShowOnLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    private void stopRing() {
        try {
            stopService(new Intent(this, AlarmService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onBackPressed() {
        stopRing();
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(tickRunnable);
        super.onDestroy();
    }
}
