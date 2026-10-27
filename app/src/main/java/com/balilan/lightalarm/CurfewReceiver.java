package com.balilan.lightalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

/**
 * 宵禁检查点：时段内每间隔触发一次。
 * 屏幕亮着且已解锁 → 走 AlarmService 响铃弹窗；否则静默，只记录。
 */
public class CurfewReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // 无论这次用不用，都先排好下一次检查点，保证时段持续推进
        try {
            AlarmScheduler.scheduleCurfew(context);
        } catch (Exception ignored) {
        }

        CurfewStore cs = new CurfewStore(context);
        if (!cs.isEnabled() || !cs.inWindow(System.currentTimeMillis())) return;

        boolean usable = AlarmScheduler.isScreenOnAndUnlocked(context);
        new AlarmStore(context).recordTrigger("宵禁提醒", !usable);
        if (!usable) return;

        Intent service = new Intent(context, AlarmService.class);
        service.putExtra(AlarmScheduler.EXTRA_ID, AlarmScheduler.CURFEW_ID);
        try {
            ContextCompat.startForegroundService(context, service);
        } catch (Exception ignored) {
        }
    }
}
