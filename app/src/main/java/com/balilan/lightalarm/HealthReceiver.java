package com.balilan.lightalarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import java.util.List;

/**
 * 每 6 小时自检一次：发现启用中的闹铃在系统里消失了（被清理/重启后未恢复），
 * 自动重新排程，并发通知告诉用户修了几个；一个都不少时保持安静。
 */
public class HealthReceiver extends BroadcastReceiver {

    private static final String CHANNEL_HEALTH = "alarm_health";
    private static final int NOTIFICATION_ID = 3003;

    @Override
    public void onReceive(Context context, Intent intent) {
        // 先排下一次自检，保证链路不断
        try {
            AlarmScheduler.scheduleHealthCheck(context);
        } catch (Exception ignored) {
        }

        int fixed = checkNow(context);
        if (fixed > 0) {
            notifyFixed(context, fixed);
        }
    }

    /** 立即自检：把系统里丢掉的闹铃补排回来，返回修好的数量 */
    public static int checkNow(Context context) {
        int fixed = 0;
        try {
            AlarmStore store = new AlarmStore(context);
            List<Alarm> list = store.load();
            for (Alarm a : list) {
                if (a.enabled && !AlarmScheduler.isScheduled(context, a.id)) {
                    AlarmScheduler.schedule(context, a);
                    fixed++;
                }
            }
            CurfewStore cs = new CurfewStore(context);
            if (cs.isEnabled() && !AlarmScheduler.isCurfewScheduled(context)) {
                AlarmScheduler.scheduleCurfew(context);
                fixed++;
            }
        } catch (Exception ignored) {
        }
        return fixed;
    }

    private void notifyFixed(Context context, int fixed) {
        ensureChannel(context);
        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 230, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new NotificationCompat.Builder(context, CHANNEL_HEALTH)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("闹钟健康检查")
                .setContentText("检测到 " + fixed + " 个提醒被系统清理，已自动恢复")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        "检测到 " + fixed + " 个提醒被系统清理，已自动恢复。\n"
                                + "若频繁出现，建议在任务列表中锁定本应用并开启「后台常驻守护」。"))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            try {
                nm.notify(NOTIFICATION_ID, n);
            } catch (Exception ignored) {
            }
        }
    }

    private void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        try {
            if (nm.getNotificationChannel(CHANNEL_HEALTH) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_HEALTH, "闹钟健康检查",
                        NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("自动修复被系统清理的闹钟时的提示");
                ch.setSound(null, null);
                ch.enableVibration(false);
                nm.createNotificationChannel(ch);
            }
        } catch (Exception ignored) {
        }
    }
}
