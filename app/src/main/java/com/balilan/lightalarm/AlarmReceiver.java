package com.balilan.lightalarm;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/**
 * 闹钟触发入口：
 * 屏幕亮且已解锁 -> 启动前台服务响铃；
 * 屏幕灭或在锁屏 -> 完全静默，只记录状态。
 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        long id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1);
        boolean snooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false);
        AlarmStore store = new AlarmStore(context);
        Alarm alarm = store.find(id);
        if (alarm == null) return;
        // 已手动关闭的闹钟即使收到残留广播也不响（贪睡不受影响）
        if (!alarm.enabled && !snooze) return;

        boolean usable = AlarmScheduler.isScreenOnAndUnlocked(context);
        store.recordTrigger(alarm.label, !usable);

        // 不管响不响，都要把下一次排好
        if (!snooze) {
            if (alarm.days == 0) {
                store.setEnabled(alarm.id, false); // 一次性闹铃响完即关闭
            } else {
                AlarmScheduler.schedule(context, alarm);
            }
        }

        if (!usable) {
            // 按需求：灭屏/锁屏时完全静默，不响、不振动、不亮屏、不发通知
            return;
        }

        Intent service = new Intent(context, AlarmService.class);
        service.putExtra(AlarmScheduler.EXTRA_ID, alarm.id);
        service.putExtra(AlarmScheduler.EXTRA_SNOOZE, snooze);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
        } catch (Exception e) {
            // 极少数机型不允许后台启动前台服务，退化为带声音的通知
            fallbackNotify(context, alarm);
        }
    }

    private void fallbackNotify(Context context, Alarm alarm) {
        try {
            AlarmService.ensureChannels(context);
            Intent full = new Intent(context, RingActivity.class);
            full.putExtra(AlarmScheduler.EXTRA_ID, alarm.id);
            full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(context, 200, full,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification n = new NotificationCompat.Builder(context, AlarmService.CHANNEL_FALLBACK)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("提醒")
                    .setContentText(alarm.label)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setFullScreenIntent(pi, true)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(1001, n);
        } catch (Exception ignored) {
        }
    }
}
