package com.salaryflow.app.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.util.Calendar;

/**
 * 刷新调度：小组件金额是「时间的一元函数」，渲染时按当前时间现算，
 * 因此只需在金额真正可能变化的时刻唤醒一次：
 * 计薪中 → 每分钟（对齐到分钟边界）；免费午休 / 上班前 → 定到变化恢复点；下班后 → 明天上班。
 * 闹钟使用 RTC 非唤醒型，屏幕关闭期间完全不唤醒设备；另有 30 分钟 updatePeriodMillis 兜底。
 * 精确闹钟依赖已声明的 SCHEDULE_EXACT_ALARM（Android 12+ 默认授予）：
 * 它既保证分钟边界准时触发，也让 WidgetRefreshReceiver 获得系统豁免，
 * 能在 App 被划掉后从后台重建秒级跳动服务。
 */
public final class WidgetUpdater {
    private static final String TAG = "SalaryFlowWidget";
    public static final String ACTION_REFRESH = "com.salaryflow.app.widget.REFRESH";

    private static final Class<?>[] PROVIDERS = {
            Widget22Provider.class, Widget42Provider.class, Widget43Provider.class
    };

    private WidgetUpdater() {}

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (Class<?> provider : PROVIDERS) {
            updateProvider(context, manager, provider);
        }
    }

    private static void updateProvider(Context context, AppWidgetManager manager, Class<?> provider) {
        ComponentName component = new ComponentName(context, provider);
        int[] ids = manager.getAppWidgetIds(component);
        int defaultLayout = defaultLayoutFor(provider);
        for (int id : ids) {
            try {
                int layout = layoutFor(manager, id, defaultLayout);
                manager.updateAppWidget(id, WidgetRenderer.build(context, layout));
            } catch (Exception e) {
                Log.w(TAG, "render widget " + id + " failed", e);
            }
        }
    }

    private static int defaultLayoutFor(Class<?> provider) {
        if (provider == Widget22Provider.class) return WidgetRenderer.LAYOUT_2X2;
        if (provider == Widget42Provider.class) return WidgetRenderer.LAYOUT_4X2;
        return WidgetRenderer.LAYOUT_4X3;
    }

    /**
     * 按系统分配的实际宽高选择布局：窄 → 2x2；扁 → 4x2；高 → 4x3。
     * ponytail: 用宽高比而非绝对高度——各启动器对同一格子上报的 dp 差异极大
     * （ColorOS 的 4×2 高度会报 ≥140dp，绝对阈值会误选 4×3，高布局塞进矮格子
     * 下半截全被裁掉）。比例落在模糊区间时信任用户添加的组件入口，不做覆盖。
     */
    public static int layoutFor(AppWidgetManager manager, int appWidgetId, int defaultLayout) {
        Bundle options = manager.getAppWidgetOptions(appWidgetId);
        int widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
        int chosen;
        if (widthDp <= 0 || heightDp <= 0) chosen = defaultLayout;
        else if (widthDp < 220) chosen = WidgetRenderer.LAYOUT_2X2;
        else {
            int ratio10 = widthDp * 10 / heightDp;
            if (ratio10 >= 30) chosen = WidgetRenderer.LAYOUT_4X2;
            else if (ratio10 <= 20) chosen = WidgetRenderer.LAYOUT_4X3;
            else chosen = defaultLayout;
        }
        Log.d(TAG, "layoutFor id=" + appWidgetId + " w=" + widthDp + " h=" + heightDp
                + " def=" + defaultLayout + " -> " + chosen);
        return chosen;
    }

    public static void scheduleNext(Context context) {
        long next = nextChangeTime(context, System.currentTimeMillis());
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            // RTC（非唤醒）：设备休眠期间不触发，唤醒后立即补发；渲染时现算金额，无需追帧
            // canScheduleExactAlarms 是 API 31+ 方法；Android 12 以下精确闹钟本就无需权限
            boolean canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                    || am.canScheduleExactAlarms();
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC, next, refreshPendingIntent(context));
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC, next, refreshPendingIntent(context));
            }
        } catch (Exception e) {
            Log.w(TAG, "schedule alarm failed", e);
        }
    }

    /**
     * 秒级服务运行条件：存在小组件且配置可计算。内容随状态自适应——
     * 计薪中金额每秒跳动，非计薪时段（上班前/下班后/周末）显示逐秒倒计时，
     * 屏幕熄灭时 Handler 随设备挂起自动停转，零唤醒。
     */
    public static boolean shouldTickerRun(Context context) {
        if (!hasWidgets(context)) return false;
        SalaryProfile p = WidgetDataStore.loadProfile(context);
        try {
            return SalaryMath.getPaidSecondsPerDay(p) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 让秒级跳动服务与当前状态对齐：有小组件则运行，否则停止。 */
    public static void ensureTicker(Context context) {
        Intent intent = new Intent(context, WidgetLiveTickerService.class);
        if (shouldTickerRun(context)) {
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, intent);
            } catch (Exception e) {
                // 后台启动被系统限制时退回分钟级闹钟刷新，下次 App 打开时自动恢复秒级
                Log.w(TAG, "ticker start deferred: " + e.getMessage());
            }
        } else {
            context.stopService(intent);
        }
    }

    private static boolean hasWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (manager == null) return false;
        for (Class<?> provider : PROVIDERS) {
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, provider));
            if (ids != null && ids.length > 0) return true;
        }
        return false;
    }

    public static void cancel(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(refreshPendingIntent(context));
    }

    private static PendingIntent refreshPendingIntent(Context context) {
        Intent intent = new Intent(ACTION_REFRESH);
        intent.setComponent(new ComponentName(context, WidgetRefreshReceiver.class));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }

    /** 下一个金额可能变化的时刻（epoch 毫秒）。 */
    static long nextChangeTime(Context context, long nowMs) {
        SalaryProfile p = WidgetDataStore.loadProfile(context);
        double paidSecondsPerDay;
        try {
            paidSecondsPerDay = SalaryMath.getPaidSecondsPerDay(p);
        } catch (Exception e) {
            paidSecondsPerDay = 0;
        }
        if (paidSecondsPerDay <= 0) return nowMs + 60_000;

        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMs);
        // 休息日不计薪：金额静止，下一次变化是下一个工作日的上班时刻
        if (SalaryMath.isRestDay(now, p)) {
            return SalaryMath.nextShiftStartMillis(p, now);
        }
        int nowClock = now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60;

        int start = SalaryMath.parseClock(p.workStartTime);
        int end = SalaryMath.parseClock(p.workEndTime);
        boolean crossesMidnight = end < start;
        boolean inShift = crossesMidnight
                ? (nowClock >= start || nowClock < end)
                : (nowClock >= start && nowClock < end);

        if (inShift) {
            if (p.paidBreak) return minuteAligned(nowMs);
            int breakStart = SalaryMath.parseClock(p.breakStartTime);
            int breakEnd = SalaryMath.parseClock(p.breakEndTime);
            boolean inUnpaidBreak = nowClock >= breakStart && nowClock < breakEnd;
            if (inUnpaidBreak) {
                long resume = Math.min(clockToEpochMs(now, breakEnd, nowClock), clockToEpochMs(now, end, nowClock));
                return resume > nowMs ? resume : minuteAligned(nowMs);
            }
            return minuteAligned(nowMs);
        }

        // 非班次内：金额静止，下一次变化是下一次上班
        boolean afterEnd = !crossesMidnight && nowClock >= end;
        if (afterEnd || crossesMidnight) {
            // crossesMidnight 时 nowClock ∈ [end, start)，下次上班是今天的 start
            return clockToEpochMs(now, start, nowClock);
        }
        // 上班前（nowClock < start）
        return clockToEpochMs(now, start, nowClock);
    }

    private static long minuteAligned(long nowMs) {
        return nowMs / 60_000 * 60_000 + 60_000;
    }

    /** 今天 clock 秒对应的 epoch；若已过去（>nowClock 之外）则顺延到明天。 */
    private static long clockToEpochMs(Calendar now, int clock, int nowClock) {
        Calendar target = (Calendar) now.clone();
        target.set(Calendar.HOUR_OF_DAY, clock / 3600);
        target.set(Calendar.MINUTE, clock % 3600 / 60);
        target.set(Calendar.SECOND, clock % 60);
        target.set(Calendar.MILLISECOND, 0);
        long t = target.getTimeInMillis();
        if (t <= now.getTimeInMillis()) {
            target.add(Calendar.DATE, 1);
            t = target.getTimeInMillis();
        }
        // 长时间跨度（如下班后定到明早上班）对非唤醒闹钟安全；确保至少大于当前 1 秒
        return Math.max(t, now.getTimeInMillis() + 1_000);
    }
}
