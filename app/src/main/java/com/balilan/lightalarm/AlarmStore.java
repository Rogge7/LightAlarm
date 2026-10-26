package com.balilan.lightalarm;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 闹铃的本地存储（SharedPreferences + JSON，不依赖数据库） */
public class AlarmStore {

    private static final String PREF = "light_alarm";
    private static final String KEY_ALARMS = "alarms";

    private final SharedPreferences sp;

    public AlarmStore(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public synchronized List<Alarm> load() {
        List<Alarm> list = new ArrayList<>();
        String raw = sp.getString(KEY_ALARMS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) list.add(Alarm.fromJson(o));
            }
        } catch (Exception ignored) {
        }
        Collections.sort(list, new Comparator<Alarm>() {
            @Override
            public int compare(Alarm a, Alarm b) {
                return (a.hour * 60 + a.minute) - (b.hour * 60 + b.minute);
            }
        });
        return list;
    }

    public synchronized void save(List<Alarm> list) {
        JSONArray arr = new JSONArray();
        for (Alarm a : list) arr.put(a.toJson());
        sp.edit().putString(KEY_ALARMS, arr.toString()).apply();
    }

    public synchronized Alarm find(long id) {
        for (Alarm a : load()) {
            if (a.id == id) return a;
        }
        return null;
    }

    public synchronized void upsert(Alarm alarm) {
        List<Alarm> list = load();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == alarm.id) {
                list.set(i, alarm);
                found = true;
                break;
            }
        }
        if (!found) list.add(alarm);
        save(list);
    }

    public synchronized void delete(long id) {
        List<Alarm> list = load();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id == id) list.remove(i);
        }
        save(list);
    }

    public synchronized void setEnabled(long id, boolean enabled) {
        List<Alarm> list = load();
        for (Alarm a : list) {
            if (a.id == id) {
                a.enabled = enabled;
                break;
            }
        }
        save(list);
    }

    /** 记录一次触发结果，供主界面展示上次是响铃还是静默 */
    public void recordTrigger(String label, boolean silent) {
        sp.edit()
                .putLong("last_trigger_at", System.currentTimeMillis())
                .putBoolean("last_trigger_silent", silent)
                .putString("last_trigger_label", label == null ? "" : label)
                .apply();
    }

    public long getLastTriggerAt() {
        return sp.getLong("last_trigger_at", 0);
    }

    public boolean wasLastSilent() {
        return sp.getBoolean("last_trigger_silent", false);
    }

    public String getLastTriggerLabel() {
        return sp.getString("last_trigger_label", "");
    }
}
