package com.balilan.lightalarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 常驻守护服务：显示一条低优先级常驻通知，让进程一直活着。
 * 进程活着，AlarmManager 里注册的闹钟就不会被系统连带清掉，
 * 这是接近微信"被杀也能收到消息"的唯一可行做法。
 */
public class GuardService extends Service {

    public static final String CHANNEL_GUARD = "alarm_guard";
    private static final int NOTIFICATION_ID = 3000;

    private static final String PREF = "lightalarm_prefs";
    private static final String KEY_GUARD = "guard_enabled";

    /* ---------- 连续亮屏计时（事件驱动，不做轮询） ---------- */
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver screenReceiver;
    private long screenOnAt;
    private boolean remindedThisStreak;

    private final Runnable remindRunnable = new Runnable() {
        @Override
        public void run() {
            onScreenTimeReached();
        }
    };

    public static boolean isEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_GUARD, false);
    }

    public static void setEnabled(Context ctx, boolean enabled) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_GUARD, enabled).apply();
        if (enabled) {
            start(ctx);
        } else {
            stop(ctx);
        }
    }

    public static void start(Context ctx) {
        try {
            ContextCompat.startForegroundService(ctx, new Intent(ctx, GuardService.class));
        } catch (Exception ignored) {
        }
    }

    public static void stop(Context ctx) {
        try {
            ctx.stopService(new Intent(ctx, GuardService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel(this);
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
        } catch (Exception ignored) {
        }
        // 服务每次被（重新）拉起时都重排一遍闹钟，相当于自动自检
        try {
            AlarmScheduler.rescheduleAll(this);
        } catch (Exception ignored) {
        }
        registerScreenTracking();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification());
        } catch (Exception ignored) {
        }
        // 阈值可能刚被改过，重新计时
        scheduleReminder();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Exception ignored) {
            }
            screenReceiver = null;
        }
        try {
            stopForeground(true);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    /* ---------------- 连续亮屏计时 ---------------- */

    private void registerScreenTracking() {
        if (screenReceiver != null) return;
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    screenOnAt = SystemClock.elapsedRealtime();
                    remindedThisStreak = false;
                    scheduleReminder();
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    handler.removeCallbacks(remindRunnable);
                    screenOnAt = 0;
                    remindedThisStreak = false;
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        try {
            registerReceiver(screenReceiver, filter);
        } catch (Exception ignored) {
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && pm.isInteractive()) {
            // 服务在亮屏状态下被拉起：从此刻开始计时（此前的时长无法回溯）
            screenOnAt = SystemClock.elapsedRealtime();
            scheduleReminder();
        }
    }

    private void scheduleReminder() {
        handler.removeCallbacks(remindRunnable);
        FocusStore fs = new FocusStore(this);
        if (!fs.isScreenEnabled() || remindedThisStreak || screenOnAt <= 0) return;
        long target = fs.getThresholdMin() * 60_000L;
        long used = SystemClock.elapsedRealtime() - screenOnAt;
        long remaining = target - used;
        if (remaining <= 0) {
            handler.post(remindRunnable);
        } else {
            handler.postDelayed(remindRunnable, remaining);
        }
    }

    private void onScreenTimeReached() {
        FocusStore fs = new FocusStore(this);
        if (!fs.isScreenEnabled() || remindedThisStreak) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null || !pm.isInteractive()) return;
        remindedThisStreak = true;

        Intent service = new Intent(this, AlarmService.class);
        service.putExtra(AlarmScheduler.EXTRA_ID, AlarmScheduler.SCREEN_TIME_ID);
        service.putExtra("screen_minutes", fs.getThresholdMin());
        try {
            ContextCompat.startForegroundService(this, service);
        } catch (Exception ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        try {
            if (nm.getNotificationChannel(CHANNEL_GUARD) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_GUARD, "后台守护",
                        NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("保持闹钟可靠触发的常驻通知，可随时关闭");
                ch.setSound(null, null);
                ch.enableVibration(false);
                nm.createNotificationChannel(ch);
            }
        } catch (Exception ignored) {
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 220, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_GUARD)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("亮屏闹铃守护中")
                .setContentText(nextAlarmText())
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false)
                .setShowWhen(false)
                .setContentIntent(pi)
                .build();
    }

    private String nextAlarmText() {
        long next = 0;
        List<Alarm> list = new AlarmStore(this).load();
        for (Alarm a : list) {
            if (!a.enabled) continue;
            long t = AlarmScheduler.nextTriggerAt(a, System.currentTimeMillis());
            if (t > 0 && (next == 0 || t < next)) next = t;
        }
        if (next == 0) return "暂无启用的闹铃";
        return "下一次 " + new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(next));
    }
}
