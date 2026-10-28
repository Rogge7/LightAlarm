package com.balilan.lightalarm;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 一条闹铃的设置。
 * days 用位图保存重复星期：bit0=周一 ... bit6=周日，0 表示只在下次响一次。
 */
public class Alarm {

    public long id;
    public int hour = 23;
    public int minute = 0;
    public int days = 0;
    public String label = "提醒";
    /** 响铃页显示的提醒语，可在编辑页修改 */
    public String message = "今天到这儿吧，睡个好觉，明天见";
    public boolean enabled = true;
    /** 空字符串表示使用系统默认闹铃 */
    public String soundUri = "";
    public String soundName = "系统默认闹铃";
    public int ringSeconds = 60;
    /** 响铃方式：0=闹铃外放（走扬声器），1=媒体音（连蓝牙耳机时走耳机） */
    public int soundMode = 1;
    /** 应用内音量 0~100（不改系统音量） */
    public int volume = 80;
    public int snoozeMinutes = 10;

    public Alarm() {
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("hour", hour);
            o.put("minute", minute);
            o.put("days", days);
            o.put("label", label == null ? "" : label);
            o.put("message", message == null ? "" : message);
            o.put("enabled", enabled);
            o.put("soundUri", soundUri == null ? "" : soundUri);
            o.put("soundName", soundName == null ? "" : soundName);
            o.put("ringSeconds", ringSeconds);
            o.put("soundMode", soundMode);
            o.put("volume", volume);
            o.put("snoozeMinutes", snoozeMinutes);
        } catch (JSONException ignored) {
        }
        return o;
    }

    public static Alarm fromJson(JSONObject o) {
        Alarm a = new Alarm();
        a.id = o.optLong("id", 0);
        a.hour = o.optInt("hour", 23);
        a.minute = o.optInt("minute", 0);
        a.days = o.optInt("days", 0);
        a.label = o.optString("label", "提醒");
        a.message = o.optString("message", "今天到这儿吧，睡个好觉，明天见");
        a.enabled = o.optBoolean("enabled", true);
        a.soundUri = o.optString("soundUri", "");
        a.soundName = o.optString("soundName", "系统默认闹铃");
        a.ringSeconds = o.optInt("ringSeconds", 60);
        a.soundMode = o.optInt("soundMode", 1);
        a.volume = o.optInt("volume", 80);
        a.snoozeMinutes = o.optInt("snoozeMinutes", 10);
        return a;
    }

    /** 重复星期的中文描述 */
    public String daysText() {
        if (days == 0) return "仅响一次";
        if (days == 0x7F) return "每天";
        if (days == 0x1F) return "工作日（周一至周五）";
        if (days == 0x60) return "周末（周六、周日）";
        String[] names = {"一", "二", "三", "四", "五", "六", "日"};
        StringBuilder sb = new StringBuilder("每周 ");
        for (int i = 0; i < 7; i++) {
            if ((days & (1 << i)) != 0) {
                if (sb.length() > 3) sb.append("、");
                sb.append("周").append(names[i]);
            }
        }
        return sb.toString();
    }
}
