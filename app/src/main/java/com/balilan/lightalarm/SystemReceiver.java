package com.balilan.lightalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机、改时间、时区变化、应用更新后恢复所有闹铃 */
public class SystemReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            AlarmScheduler.rescheduleAll(context);
        } catch (Exception ignored) {
        }
        // 开机后按用户选择恢复常驻守护
        try {
            if (GuardService.isEnabled(context)) {
                GuardService.start(context);
            }
        } catch (Exception ignored) {
        }
    }
}
