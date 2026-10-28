package com.balilan.lightalarm;

import android.content.Context;
import android.content.SharedPreferences;

/** 连续亮屏提醒 与 语音播报 的全局设置 */
public class FocusStore {

    private static final String PREF = "lightalarm_focus";

    private final SharedPreferences sp;

    public FocusStore(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /* ---------------- 连续亮屏提醒 ---------------- */

    public boolean isScreenEnabled() {
        return sp.getBoolean("screen_enabled", false);
    }

    public void setScreenEnabled(boolean enabled) {
        sp.edit().putBoolean("screen_enabled", enabled).apply();
    }

    /** 连续亮屏多少分钟后提醒，默认 45 分钟 */
    public int getThresholdMin() {
        return sp.getInt("screen_threshold", 45);
    }

    public void setThresholdMin(int minutes) {
        sp.edit().putInt("screen_threshold", minutes).apply();
    }

    public String getScreenMessage() {
        return sp.getString("screen_message", "你已经连续看了 %d 分钟手机，闭目养神一会儿吧");
    }

    public void setScreenMessage(String message) {
        sp.edit().putString("screen_message", message == null ? "" : message).apply();
    }

    /* ---------------- 语音播报（TTS） ---------------- */

    public boolean isVoiceEnabled() {
        return sp.getBoolean("voice_enabled", false);
    }

    public void setVoiceEnabled(boolean enabled) {
        sp.edit().putBoolean("voice_enabled", enabled).apply();
    }

    /** 转成伪闹铃，交给 AlarmService 走统一的弹窗 + 响铃流程 */
    public Alarm toScreenAlarm(int minutes) {
        Alarm a = new Alarm();
        a.id = AlarmScheduler.SCREEN_TIME_ID;
        a.label = "休息提醒";
        String msg = getScreenMessage().replace("%d", String.valueOf(minutes));
        a.message = msg.length() == 0 ? "闭目养神一会儿吧" : msg;
        a.enabled = true;
        a.days = 0;
        a.soundUri = "";
        a.soundName = "系统默认闹铃";
        a.ringSeconds = 15;
        a.volume = 60;
        a.snoozeMinutes = 10;
        a.soundMode = 1;
        return a;
    }
}
