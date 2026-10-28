package com.balilan.lightalarm;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/** 宵禁时段模式的配置存储（SharedPreferences，全局限一个时段） */
public class CurfewStore {

    private static final String PREF = "lightalarm_curfew";

    private final SharedPreferences sp;

    public CurfewStore(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        return sp.getBoolean("enabled", false);
    }

    public void setEnabled(boolean enabled) {
        sp.edit().putBoolean("enabled", enabled).apply();
    }

    /** 开始时刻（分钟，0~1439），默认 23:00 */
    public int getStartMin() {
        return sp.getInt("startMin", 23 * 60);
    }

    /** 结束时刻（分钟），默认 06:00 */
    public int getEndMin() {
        return sp.getInt("endMin", 6 * 60);
    }

    public void setWindow(int startMin, int endMin) {
        sp.edit().putInt("startMin", startMin).putInt("endMin", endMin).apply();
    }

    /** 提醒间隔（分钟），默认 15 */
    public int getIntervalMin() {
        return sp.getInt("intervalMin", 15);
    }

    public void setIntervalMin(int minutes) {
        sp.edit().putInt("intervalMin", minutes).apply();
    }

    public String getMessage() {
        return sp.getString("message", "夜深了，手机可以明天再玩");
    }

    public void setMessage(String message) {
        sp.edit().putString("message", message == null ? "" : message).apply();
    }

    /** 0=闹铃外放，1=媒体音（蓝牙耳机） */
    public int getSoundMode() {
        return sp.getInt("soundMode", 1);
    }

    public void setSoundMode(int mode) {
        sp.edit().putInt("soundMode", mode).apply();
    }

    /** 当前时刻是否处于宵禁时段内（支持跨天，如 23:00–06:00） */
    public boolean inWindow(long nowMillis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(nowMillis);
        int nowMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int start = getStartMin();
        int end = getEndMin();
        if (start == end) return false;
        if (start < end) {
            return nowMin >= start && nowMin < end;
        }
        // 跨天时段
        return nowMin >= start || nowMin < end;
    }

    /** 距离 nowMillis 最近的下一个时段开始时刻 */
    public long nextWindowStart(long nowMillis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(nowMillis);
        c.set(Calendar.HOUR_OF_DAY, getStartMin() / 60);
        c.set(Calendar.MINUTE, getStartMin() % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= nowMillis) {
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return c.getTimeInMillis();
    }

    /** 转成伪闹铃，交给 AlarmService 按普通响铃流程播放 */
    public Alarm toAlarm() {
        Alarm a = new Alarm();
        a.id = AlarmScheduler.CURFEW_ID;
        a.label = "宵禁提醒";
        String msg = getMessage();
        a.message = msg.length() == 0 ? "夜深了，手机可以明天再玩" : msg;
        a.enabled = true;
        a.days = 0;
        a.soundUri = "";
        a.soundName = "系统默认闹铃";
        a.ringSeconds = 10;
        a.volume = 50;
        a.snoozeMinutes = getIntervalMin();
        a.soundMode = getSoundMode();
        return a;
    }
}
