package com.balilan.lightalarm;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;

import java.util.Calendar;
import java.util.List;

/** 负责计算下一次触发时间并用 AlarmManager 排程 */
public class AlarmScheduler {

    public static final String ACTION_FIRE = "com.balilan.lightalarm.ALARM_FIRE";
    public static final String ACTION_FIRE_SNOOZE = "com.balilan.lightalarm.ALARM_FIRE_SNOOZE";
    public static final String EXTRA_ID = "alarm_id";
    public static final String EXTRA_SNOOZE = "is_snooze";

    /** 下一次触发时间（毫秒）；无法计算时返回 -1 */
    public static long nextTriggerAt(Alarm a, long fromMillis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(fromMillis);
        c.set(Calendar.HOUR_OF_DAY, a.hour);
        c.set(Calendar.MINUTE, a.minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        if (a.days == 0) {
            if (c.getTimeInMillis() <= fromMillis) {
                c.add(Calendar.DAY_OF_YEAR, 1);
            }
            return c.getTimeInMillis();
        }

        // 最多向后找 8 天，覆盖任意星期组合
        for (int i = 0; i < 8; i++) {
            Calendar t = (Calendar) c.clone();
            t.add(Calendar.DAY_OF_YEAR, i);
            int index = (t.get(Calendar.DAY_OF_WEEK) + 5) % 7; // 周一=0 ... 周日=6
            if ((a.days & (1 << index)) != 0 && t.getTimeInMillis() > fromMillis) {
                return t.getTimeInMillis();
            }
        }
        return -1;
    }

    public static void schedule(Context ctx, Alarm a) {
        if (!a.enabled) {
            cancelAll(ctx, a.id);
            return;
        }
        long at = nextTriggerAt(a, System.currentTimeMillis());
        if (at <= 0) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        setExactSafely(am, at, pendingIntent(ctx, a.id, false));
    }

    /** 贪睡：排一个独立的单次闹钟，不影响原闹铃的下一次时间 */
    public static void snooze(Context ctx, Alarm a, int minutes) {
        long at = System.currentTimeMillis() + Math.max(1, minutes) * 60_000L;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        setExactSafely(am, at, pendingIntent(ctx, a.id, true));
    }

    public static void cancelAll(Context ctx, long id) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(pendingIntent(ctx, id, false));
        am.cancel(pendingIntent(ctx, id, true));
    }

    public static void rescheduleAll(Context ctx) {
        AlarmStore store = new AlarmStore(ctx);
        for (Alarm a : store.load()) {
            if (a.enabled) schedule(ctx, a);
            else cancelAll(ctx, a.id);
        }
    }

    /** 屏幕是否点亮且已解锁 —— 只有这种情况才允许响铃 */
    public static boolean isScreenOnAndUnlocked(Context ctx) {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm == null) return false;
        boolean interactive;
        if (Build.VERSION.SDK_INT >= 20) {
            interactive = pm.isInteractive();
        } else {
            interactive = pm.isScreenOn();
        }
        if (!interactive) return false;
        KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
        return km == null || !km.isKeyguardLocked();
    }

    private static PendingIntent pendingIntent(Context ctx, long id, boolean snooze) {
        Intent i = new Intent(ctx, AlarmReceiver.class);
        i.setAction(snooze ? ACTION_FIRE_SNOOZE : ACTION_FIRE);
        // 用 data 区分不同闹铃，避免 PendingIntent 因 requestCode 截断而互相覆盖
        i.setData(Uri.parse("lightalarm://alarm/" + id + (snooze ? "/snooze" : "")));
        i.putExtra(EXTRA_ID, id);
        i.putExtra(EXTRA_SNOOZE, snooze);
        return PendingIntent.getBroadcast(ctx, snooze ? 1 : 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void setExactSafely(AlarmManager am, long at, PendingIntent pi) {
        boolean exact = true;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                exact = am.canScheduleExactAlarms();
            } catch (Exception ignored) {
                exact = false;
            }
        }
        try {
            if (exact && Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else if (exact) {
                am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            // 没有精确闹钟权限时退化为非精确，可能延迟几分钟
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, at, pi);
                }
            } catch (Exception ignored) {
            }
        }
    }
}
