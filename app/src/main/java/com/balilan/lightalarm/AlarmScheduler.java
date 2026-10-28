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

    /** 宵禁提醒使用的伪闹铃 ID（正常闹铃 ID 是时间戳，不会撞） */
    public static final long CURFEW_ID = -1000L;
    /** 连续亮屏提醒使用的伪闹铃 ID */
    public static final long SCREEN_TIME_ID = -2000L;
    public static final String ACTION_CURFEW = "com.balilan.lightalarm.CURFEW_TICK";
    public static final String ACTION_HEALTH = "com.balilan.lightalarm.HEALTH_CHECK";
    /** 健康检查间隔：6 小时（自检 + 自动补排被系统清掉的闹铃） */
    private static final long HEALTH_INTERVAL = 6L * 3600_000L;

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
        setExactSafely(am, ctx, at, pendingIntent(ctx, a.id, false));
    }

    /** 贪睡：排一个独立的单次闹钟，不影响原闹铃的下一次时间 */
    public static void snooze(Context ctx, Alarm a, int minutes) {
        long at = System.currentTimeMillis() + Math.max(1, minutes) * 60_000L;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        setExactSafely(am, ctx, at, pendingIntent(ctx, a.id, true));
    }

    public static void cancelAll(Context ctx, long id) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(pendingIntent(ctx, id, false));
        am.cancel(pendingIntent(ctx, id, true));
    }

    /** 通知上“延迟”按钮使用：到点后与普通贪睡逻辑一致 */
    public static PendingIntent snoozePendingIntent(Context ctx, Alarm a) {
        return pendingIntent(ctx, a.id, true);
    }

    public static void rescheduleAll(Context ctx) {
        AlarmStore store = new AlarmStore(ctx);
        for (Alarm a : store.load()) {
            if (a.enabled) schedule(ctx, a);
            else cancelAll(ctx, a.id);
        }
        scheduleCurfew(ctx);
        scheduleHealthCheck(ctx);
    }

    /** 该闹铃是否仍注册在 AlarmManager 中（被杀后台/重启后会被系统清掉） */
    public static boolean isScheduled(Context ctx, long id) {
        Intent i = new Intent(ctx, AlarmReceiver.class);
        i.setAction(ACTION_FIRE);
        i.setData(Uri.parse("lightalarm://alarm/" + id));
        return PendingIntent.getBroadcast(ctx, 0, i,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE) != null;
    }

    /* ---------------- 宵禁时段模式 ---------------- */

    /** 按当前是否在时段内，排下一次宵禁检查点（时段内=现在+间隔；时段外=下一个开始时刻） */
    public static void scheduleCurfew(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = curfewIntent(ctx, false);
        CurfewStore cs = new CurfewStore(ctx);
        if (!cs.isEnabled()) {
            am.cancel(pi);
            return;
        }
        long now = System.currentTimeMillis();
        long next = cs.inWindow(now) ? now + cs.getIntervalMin() * 60_000L : cs.nextWindowStart(now);
        setExactSafely(am, ctx, next, pi);
    }

    public static void cancelCurfew(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(curfewIntent(ctx, false));
    }

    public static boolean isCurfewScheduled(Context ctx) {
        return curfewIntent(ctx, true) != null;
    }

    private static PendingIntent curfewIntent(Context ctx, boolean noCreate) {
        Intent i = new Intent(ctx, CurfewReceiver.class);
        i.setAction(ACTION_CURFEW);
        i.setData(Uri.parse("lightalarm://curfew"));
        int flags = PendingIntent.FLAG_IMMUTABLE
                | (noCreate ? PendingIntent.FLAG_NO_CREATE : PendingIntent.FLAG_UPDATE_CURRENT);
        return PendingIntent.getBroadcast(ctx, 0, i, flags);
    }

    /* ---------------- 每 4 小时健康检查 ---------------- */

    public static void scheduleHealthCheck(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(ctx, HealthReceiver.class);
        i.setAction(ACTION_HEALTH);
        i.setData(Uri.parse("lightalarm://health"));
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long at = System.currentTimeMillis() + HEALTH_INTERVAL;
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                // 自检不需要精确到秒，用省电的非精确闹钟即可
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Exception ignored) {
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

    private static void setExactSafely(AlarmManager am, Context ctx, long at, PendingIntent pi) {
        boolean exact = true;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                exact = am.canScheduleExactAlarms();
            } catch (Exception ignored) {
                exact = false;
            }
        }
        try {
            if (exact && Build.VERSION.SDK_INT >= 21) {
                // setAlarmClock 是系统时钟 App 同款 API：优先级最高，
                // 状态栏显示闹钟图标，国产 ROM 清理后台后存活率明显高于普通闹钟
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, showIntent(ctx)), pi);
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

    /** setAlarmClock 需要的“查看闹钟”入口：点状态栏闹钟图标时打开主界面 */
    private static PendingIntent showIntent(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(ctx, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
