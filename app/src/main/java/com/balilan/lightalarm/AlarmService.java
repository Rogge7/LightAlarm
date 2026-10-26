package com.balilan.lightalarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 前台服务：播放铃声并展示全屏提醒通知 */
public class AlarmService extends Service {

    public static final String CHANNEL_RING = "alarm_ring";
    public static final String CHANNEL_FALLBACK = "alarm_fallback";
    public static final String ACTION_STOP = "com.balilan.lightalarm.STOP_RING";

    private static final int NOTIFICATION_ID = 3001;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private PowerManager.WakeLock wakeLock;
    private View overlayView;

    private final Runnable stopRunnable = new Runnable() {
        @Override
        public void run() {
            stopSelf();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        long id = intent == null ? -1 : intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1);
        AlarmStore store = new AlarmStore(this);
        Alarm alarm = store.find(id);
        if (alarm == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification(alarm));
        acquireWakeLock();
        playSound(alarm);
        // 亮屏解锁时系统通常不会自动拉起全屏页：优先用悬浮窗，其次尝试直接拉起页面
        showPopup(alarm);

        int seconds = Math.max(5, alarm.ringSeconds);
        handler.removeCallbacks(stopRunnable);
        handler.postDelayed(stopRunnable, seconds * 1000L);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        hideOverlay();
        stopPlayer();
        releaseWakeLock();
        try {
            stopForeground(true);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** 创建两个通知渠道：正常响铃（无系统提示音，声音由 MediaPlayer 播放）与兜底响铃 */
    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        try {
            if (nm.getNotificationChannel(CHANNEL_RING) == null) {
                NotificationChannel ring = new NotificationChannel(CHANNEL_RING, "闹铃播放",
                        NotificationManager.IMPORTANCE_HIGH);
                ring.setDescription("亮屏闹铃响铃时的常驻通知");
                ring.setSound(null, null);
                ring.enableVibration(false);
                ring.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                try {
                    ring.setBypassDnd(true);
                } catch (Exception ignored) {
                }
                nm.createNotificationChannel(ring);
            }
            if (nm.getNotificationChannel(CHANNEL_FALLBACK) == null) {
                NotificationChannel fb = new NotificationChannel(CHANNEL_FALLBACK, "闹铃兜底",
                        NotificationManager.IMPORTANCE_HIGH);
                fb.setDescription("系统限制后台服务时的备份提醒");
                fb.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
                fb.enableVibration(false);
                fb.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(fb);
            }
        } catch (Exception ignored) {
        }
    }

    private Notification buildNotification(Alarm alarm) {
        Intent full = new Intent(this, RingActivity.class);
        full.putExtra(AlarmScheduler.EXTRA_ID, alarm.id);
        full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullPi = PendingIntent.getActivity(this, 200, full,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, AlarmService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 201, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String time = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute);
        return new NotificationCompat.Builder(this, CHANNEL_RING)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("该睡觉了 · " + time)
                .setContentText(alarm.label)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(fullPi, true)
                .setContentIntent(fullPi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭铃声", stopPi)
                .build();
    }

    /** 有悬浮窗权限时用悬浮窗弹全屏（最可靠），否则尝试直接拉起提醒页 */
    private void showPopup(Alarm alarm) {
        boolean canOverlay = false;
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                canOverlay = Settings.canDrawOverlays(this);
            } catch (Exception ignored) {
            }
        }
        if (canOverlay) {
            showOverlay(alarm);
        } else {
            startRingActivity(alarm);
        }
    }

    private void showOverlay(Alarm alarm) {
        hideOverlay();
        try {
            android.view.ContextThemeWrapper themed =
                    new android.view.ContextThemeWrapper(this, R.style.Theme_LightAlarm_Ring);
            View view = LayoutInflater.from(themed).inflate(R.layout.activity_ring, null, false);

            TextView tvTime = view.findViewById(R.id.tvRingTime);
            tvTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));

            TextView tvLabel = view.findViewById(R.id.tvRingLabel);
            tvLabel.setText(alarm.label);

            TextView tvDays = view.findViewById(R.id.tvRingDays);
            tvDays.setText(alarm.daysText());

            TextView tvCountdown = view.findViewById(R.id.tvRingCountdown);
            tvCountdown.setText("铃声将在 " + Math.max(5, alarm.ringSeconds) + " 秒后自动停止");

            Button btnSnooze = view.findViewById(R.id.btnSnooze);
            btnSnooze.setText("延迟 " + alarm.snoozeMinutes + " 分钟");
            btnSnooze.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlarmScheduler.snooze(AlarmService.this, alarm, alarm.snoozeMinutes);
                    stopSelf();
                }
            });

            Button btnAck = view.findViewById(R.id.btnAck);
            btnAck.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopSelf();
                }
            });

            int type;
            if (Build.VERSION.SDK_INT >= 26) {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                type = WindowManager.LayoutParams.TYPE_PHONE;
            }
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            if (Build.VERSION.SDK_INT >= 28) {
                lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }

            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) {
                startRingActivity(alarm);
                return;
            }
            wm.addView(view, lp);
            overlayView = view;
        } catch (Exception e) {
            startRingActivity(alarm);
        }
    }

    private void hideOverlay() {
        if (overlayView != null) {
            try {
                WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                if (wm != null) wm.removeView(overlayView);
            } catch (Exception ignored) {
            }
            overlayView = null;
        }
    }

    private void startRingActivity(Alarm alarm) {
        try {
            Intent i = new Intent(this, RingActivity.class);
            i.putExtra(AlarmScheduler.EXTRA_ID, alarm.id);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private void playSound(Alarm alarm) {
        stopPlayer();
        Uri uri = resolveUri(alarm);
        if (uri == null) return;

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        MediaPlayer p = null;
        try {
            p = MediaPlayer.create(this, uri, null, attrs);
        } catch (Exception ignored) {
        }
        if (p == null) {
            // 自定义铃声失效时退回系统默认闹铃
            Uri fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (fallback != null) {
                try {
                    p = MediaPlayer.create(this, fallback, null, attrs);
                } catch (Exception ignored) {
                }
            }
        }
        if (p == null) return;

        float volume = Math.max(0, Math.min(100, alarm.volume)) / 100f;
        p.setVolume(volume, volume);
        p.setLooping(true);
        try {
            p.start();
            player = p;
        } catch (Exception e) {
            try {
                p.release();
            } catch (Exception ignored) {
            }
        }
    }

    private Uri resolveUri(Alarm alarm) {
        if (alarm.soundUri != null && alarm.soundUri.length() > 0) {
            try {
                return Uri.parse(alarm.soundUri);
            } catch (Exception ignored) {
            }
        }
        Uri d = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (d == null) d = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (d == null) d = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        return d;
    }

    private void stopPlayer() {
        if (player != null) {
            try {
                if (player.isPlaying()) player.stop();
            } catch (Exception ignored) {
            }
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LightAlarm:ring");
                wakeLock.setReferenceCounted(false);
            }
            wakeLock.acquire(120_000L);
        } catch (Exception ignored) {
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) {
        }
    }
}
