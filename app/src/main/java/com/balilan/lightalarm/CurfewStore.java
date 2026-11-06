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

    /** 铃声 URI（空字符串=系统默认） */
    public String getSoundUri() {
        return sp.getString("soundUri", "");
    }

    public void setSoundUri(String uri) {
        sp.edit().putString("soundUri", uri == null ? "" : uri).apply();
    }

    public String getSoundName() {
        return sp.getString("soundName", "系统默认闹铃");
    }

    public void setSoundName(String name) {
        sp.edit().putString("soundName", name == null ? "" : name).apply();
    }

    /** 应用内音量 0~100，默认 50 */
    public int getVolume() {
        return sp.getInt("volume", 50);
    }

    public void setVolume(int v) {
        sp.edit().putInt("volume", Math.max(0, Math.min(100, v))).apply();
    }

    /** 响铃时长秒，默认 10 */
    public int getRingSeconds() {
        return sp.getInt("ringSeconds", 10);
    }

    public void setRingSeconds(int seconds) {
        sp.edit().putInt("ringSeconds", seconds).apply();
    }

    /** 宵禁是否启用 TTS 语音播报，默认 true */
    public boolean isVoiceEnabled() {
        return sp.getBoolean("voiceEnabled", true);
    }

    public void setVoiceEnabled(boolean enabled) {
        sp.edit().putBoolean("voiceEnabled", enabled).apply();
    }

    /** 忽略亮屏/锁屏条件：灭屏锁屏也响，番茄钟模式。默认 false */
    public boolean getIgnoreScreenCond() {
        return sp.getBoolean("ignoreScreenCond", false);
    }

    public void setIgnoreScreenCond(boolean ignore) {
        sp.edit().putBoolean("ignoreScreenCond", ignore).apply();
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
        a.soundUri = getSoundUri();
        a.soundName = getSoundName();
        a.ringSeconds = getRingSeconds();
        a.volume = getVolume();
        a.snoozeMinutes = getIntervalMin();
        a.soundMode = getSoundMode();
        // 宵禁独立控制 TTS 开关（不跟全局 FocusStore 绑死）
        a.voiceEnabled = isVoiceEnabled();
        return a;
    }
}
