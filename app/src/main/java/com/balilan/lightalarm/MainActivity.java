package com.balilan.lightalarm;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 主界面：闹铃列表 + 运行权限引导 + 测试 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_NOTIFICATION = 10;

    private AlarmStore store;
    private LinearLayout alarmContainer;
    private LinearLayout permContainer;
    private TextView tvEmpty;
    private TextView tvLastStatus;
    private TextView tvNextAlarm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        store = new AlarmStore(this);
        alarmContainer = findViewById(R.id.alarmContainer);
        permContainer = findViewById(R.id.permContainer);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvLastStatus = findViewById(R.id.tvLastStatus);
        tvNextAlarm = findViewById(R.id.tvNextAlarm);

        Button btnAdd = findViewById(R.id.btnAdd);
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, EditActivity.class));
            }
        });

        Button btnTest = findViewById(R.id.btnTest);
        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testNow();
            }
        });

        AlarmService.ensureChannels(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refresh();
    }

    private void refresh() {
        renderAlarms();
        renderPermissions();
        renderStatus();
    }

    private void renderAlarms() {
        alarmContainer.removeAllViews();
        List<Alarm> list = store.load();
        tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Alarm a : list) {
            View row = inflater.inflate(R.layout.item_alarm, alarmContainer, false);
            TextView tvTime = row.findViewById(R.id.tvAlarmTime);
            TextView tvDays = row.findViewById(R.id.tvAlarmDays);
            TextView tvInfo = row.findViewById(R.id.tvAlarmInfo);
            Switch switchOn = row.findViewById(R.id.switchAlarm);

            tvTime.setText(String.format(Locale.getDefault(), "%02d:%02d", a.hour, a.minute));
            tvDays.setText(a.daysText() + (a.enabled ? " · 下一次 " + friendlyTime(nextOf(a)) : " · 已关闭"));
            String info = a.label;
            if (a.soundName != null && a.soundName.length() > 0) {
                info = info + " · " + a.soundName;
            }
            tvInfo.setText(info + " · " + a.ringSeconds + " 秒");

            switchOn.setChecked(a.enabled);
            switchOn.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                    a.enabled = isChecked;
                    store.upsert(a);
                    if (isChecked) {
                        AlarmScheduler.schedule(MainActivity.this, a);
                    } else {
                        AlarmScheduler.cancelAll(MainActivity.this, a.id);
                    }
                    refresh();
                }
            });

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(MainActivity.this, EditActivity.class);
                    i.putExtra("alarm_id", a.id);
                    startActivity(i);
                }
            });

            alarmContainer.addView(row);
        }
    }

    private long nextOf(Alarm a) {
        if (!a.enabled) return 0;
        return AlarmScheduler.nextTriggerAt(a, System.currentTimeMillis());
    }

    private void renderStatus() {
        long last = store.getLastTriggerAt();
        if (last == 0) {
            tvLastStatus.setText("还没有触发记录");
        } else {
            String when = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(last));
            String label = store.getLastTriggerLabel();
            String result = store.wasLastSilent() ? "静默（屏幕未点亮或已锁屏）" : "已响铃";
            tvLastStatus.setText(when + " · " + (label.length() == 0 ? "闹铃" : label) + " · " + result);
        }

        long next = 0;
        for (Alarm a : store.load()) {
            if (!a.enabled) continue;
            long t = AlarmScheduler.nextTriggerAt(a, System.currentTimeMillis());
            if (t > 0 && (next == 0 || t < next)) next = t;
        }
        if (next == 0) {
            tvNextAlarm.setText("下一次：未设置");
        } else {
            tvNextAlarm.setText("下一次：" + friendlyTime(next));
        }
    }

    private String friendlyTime(long millis) {
        if (millis <= 0) return "无";
        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        String day;
        if (sameDay(now, c)) {
            day = "今天";
        } else {
            Calendar tomorrow = (Calendar) now.clone();
            tomorrow.add(Calendar.DAY_OF_YEAR, 1);
            if (sameDay(tomorrow, c)) {
                day = "明天";
            } else {
                String[] week = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
                day = week[c.get(Calendar.DAY_OF_WEEK) - 1];
            }
        }
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(millis));
        return day + " " + time;
    }

    private boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private void renderPermissions() {
        permContainer.removeAllViews();

        boolean notificationOk;
        if (Build.VERSION.SDK_INT >= 33) {
            notificationOk = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
                    && NotificationManagerCompat.from(this).areNotificationsEnabled();
        } else {
            notificationOk = NotificationManagerCompat.from(this).areNotificationsEnabled();
        }

        boolean exactOk = true;
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            try {
                exactOk = am != null && am.canScheduleExactAlarms();
            } catch (Exception ignored) {
                exactOk = false;
            }
        }

        boolean batteryOk = true;
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            try {
                batteryOk = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
            } catch (Exception ignored) {
                batteryOk = false;
            }
        }

        boolean fullScreenOk = true;
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            try {
                fullScreenOk = nm != null && nm.canUseFullScreenIntent();
            } catch (Exception ignored) {
                fullScreenOk = false;
            }
        }

        boolean overlayOk = true;
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                overlayOk = Settings.canDrawOverlays(this);
            } catch (Exception ignored) {
                overlayOk = false;
            }
        }

        addPermRow("通知权限", notificationOk, notificationOk ? "去看看" : "去开启",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openNotificationSettings();
                    }
                });
        addPermRow("精确闹钟", exactOk, exactOk ? "去看看" : "去开启",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openExactAlarmSettings();
                    }
                });
        addPermRow("忽略电池优化", batteryOk, batteryOk ? "去看看" : "去开启",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openBatterySettings();
                    }
                });
        addPermRow("全屏提醒", fullScreenOk, fullScreenOk ? "去看看" : "去允许",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openFullScreenSettings();
                    }
                });
        addPermRow("悬浮窗（保证在别的 App 上弹窗）", overlayOk, overlayOk ? "去看看" : "去开启",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openOverlaySettings();
                    }
                });
        addPermRow("允许自启动与后台弹窗（国产手机必开）", false, "去设置",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openAppDetails();
                    }
                });
    }

    private void addPermRow(String title, boolean ok, String buttonText, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView name = new TextView(this);
        if ("允许自启动与后台弹窗（国产手机必开）".equals(title)) {
            name.setText("•  " + title);
            name.setTextColor(0xFF6B7785);
        } else {
            name.setText((ok ? "✓  " : "✗  ") + title);
            name.setTextColor(ok ? 0xFF2E7D32 : 0xFFD32F2F);
        }
        name.setTextSize(14);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(name);

        TextView btn = new TextView(this);
        btn.setText(buttonText);
        btn.setTextSize(13);
        btn.setTextColor(0xFF1F5FA8);
        btn.setPadding(dp(10), dp(4), dp(4), dp(4));
        btn.setOnClickListener(listener);
        row.addView(btn);

        permContainer.addView(row);
    }

    private void testNow() {
        List<Alarm> list = store.load();
        if (list.isEmpty()) {
            Toast.makeText(this, "请先添加一个闹铃再测试", Toast.LENGTH_SHORT).show();
            return;
        }
        Alarm a = list.get(0);
        if (!AlarmScheduler.isScreenOnAndUnlocked(this)) {
            store.recordTrigger(a.label, true);
            Toast.makeText(this, "当前屏幕未点亮或已锁屏：按设计静默，不会响铃", Toast.LENGTH_LONG).show();
            refresh();
            return;
        }
        Intent service = new Intent(this, AlarmService.class);
        service.putExtra(AlarmScheduler.EXTRA_ID, a.id);
        try {
            ContextCompat.startForegroundService(this, service);
            Toast.makeText(this, "屏幕已点亮：正在响铃 " + a.ringSeconds + " 秒", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "启动失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
                return;
            } catch (Exception ignored) {
            }
        }
        openAppDetails();
    }

    private void openBatterySettings() {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())));
                return;
            } catch (Exception ignored) {
            }
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                return;
            } catch (Exception ignored) {
            }
        }
        openAppDetails();
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
                return;
            } catch (Exception ignored) {
            }
        }
        openAppDetails();
    }

    private void openFullScreenSettings() {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:" + getPackageName())));
                return;
            } catch (Exception ignored) {
            }
        }
        openAppDetails();
    }

    private void openAppDetails() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开系统设置，请手动在设置中允许本应用", Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
