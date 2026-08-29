package com.salaryflow.app.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import com.salaryflow.app.MainActivity;
import com.salaryflow.app.R;

import java.util.Calendar;

/**
 * 把「今日已赚」快照渲染成 RemoteViews。
 *
 * 五种班次状态驱动内容形态：
 * - 工作中 / 午休：今日金额实时跳动 + 距下班/距开工倒计时
 * - 上班前 / 下班后 / 休息日：秒薪行变成逐秒的「距上班 HH:MM:SS」倒计时
 * - 休息日（用户配置的每周工作日之外）：大数字切换为「本周已经赚了」
 *
 * 倒计时使用 Chronometer 的倒计时模式：由桌面启动器进程驱动，
 * App 被划掉甚至强停（Android 停止态收不到任何闹钟/广播）后依然逐秒跳动。
 * 金额与状态文字依赖 App 进程刷新，强停后冻结、打开 App 自动恢复。
 */
public final class WidgetRenderer {

    public static final int LAYOUT_2X2 = R.layout.widget_2x2;
    public static final int LAYOUT_4X2 = R.layout.widget_4x2;
    public static final int LAYOUT_4X3 = R.layout.widget_4x3;

    private static final int STATE_WORKING = 0;
    private static final int STATE_LUNCH = 1;
    private static final int STATE_BEFORE = 2;
    private static final int STATE_AFTER = 3;
    private static final int STATE_WEEKEND = 4;

    private WidgetRenderer() {}

    // ---------------------------------------------------------------- 渲染

    public static RemoteViews build(Context context, int layoutId) {
        SalaryProfile profile = WidgetDataStore.loadProfile(context);
        Calendar now = Calendar.getInstance();

        SalaryProfile p = profile;
        SalaryMath.Rates rates;
        double worked;
        double earned;
        try {
            rates = SalaryMath.calculateRates(p);
            worked = SalaryMath.getWorkedPaidSeconds(p, now);
            earned = worked * rates.second;
        } catch (Exception e) {
            // 配置无效：回落默认配置，与 Web 端展示保持一致
            p = SalaryProfile.defaults();
            rates = SalaryMath.calculateRates(p);
            worked = SalaryMath.getWorkedPaidSeconds(p, now);
            earned = worked * rates.second;
        }

        double progress = rates.paidSecondsPerDay > 0
                ? Math.max(0, Math.min(100, worked / rates.paidSecondsPerDay * 100))
                : 0;

        Model m = buildModel(p, rates, earned, worked, progress, now);

        double[] breakdown = null;
        try {
            breakdown = SalaryMath.payBreakdownMonthly(p, rates.paidSecondsPerDay);
        } catch (Exception ignored) {
            // 拆分失败等同未启用
        }
        // 休息日 → 本周累计因子；工作日 → 当日已计薪因子（占全月计薪秒比例）
        boolean showBreakdown = breakdown != null && m.state != STATE_BEFORE;
        double wjyjToday = 0;
        double taxToday = 0;
        if (showBreakdown) {
            double factor = m.state == STATE_WEEKEND
                    ? SalaryMath.weekWorkdaysDone(now, p) / p.monthlyWorkDays
                    : worked / (p.monthlyWorkDays * rates.paidSecondsPerDay);
            wjyjToday = (breakdown[1] + breakdown[2]) * factor;
            taxToday = breakdown[3] * factor;
        }

        RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);

        // 金额（混合字号：¥ 与小数弱化）
        views.setTextViewText(R.id.widget_label, m.heroLabel);
        views.setTextViewText(R.id.widget_cur, "¥");
        views.setTextViewText(R.id.widget_amount, SalaryMath.moneyIntPart(m.displayAmount));
        views.setTextViewText(R.id.widget_frac, "." + SalaryMath.moneyFracPart(m.displayAmount));

        // 秒薪 / 倒计时行
        if (m.rateCountdownTargetMs != null) {
            applyCountdown(views, R.id.widget_rate, m.rateCountdownTargetMs, m.rateCountdownPrefix, now);
        } else {
            stopChronometer(views, R.id.widget_rate);
            views.setTextViewText(R.id.widget_rate, m.rateText);
        }
        views.setProgressBar(R.id.widget_progress, 10000, (int) Math.round(m.progress * 100), false);

        // 状态徽章
        views.setTextViewText(R.id.widget_state, m.badgeText);
        views.setInt(R.id.widget_state, "setBackgroundResource", m.badgePill);
        views.setInt(R.id.widget_state, "setTextColor", m.badgeColor);

        boolean full = layoutId != LAYOUT_2X2;
        if (full) {
            views.setTextViewText(R.id.widget_start, p.workStartTime);
            views.setTextViewText(R.id.widget_end, p.workEndTime);
        }

        if (layoutId == LAYOUT_4X2) {
            if (m.state == STATE_WEEKEND) {
                views.setTextViewText(R.id.widget_m1_label, "本周");
                views.setTextViewText(R.id.widget_pct, SalaryMath.weekWorkdaysDone(now, p) + "天");
                views.setTextViewText(R.id.widget_m2_label, "五险一金");
                views.setTextViewText(R.id.widget_worked, SalaryMath.money(wjyjToday));
                views.setTextViewText(R.id.widget_m3_label, "个税");
                views.setTextViewText(R.id.widget_daily, SalaryMath.money(taxToday));
            } else if (showBreakdown) {
                views.setViewVisibility(R.id.widget_m1_label, View.GONE);
                views.setViewVisibility(R.id.widget_pct, View.GONE);
                views.setTextViewText(R.id.widget_m2_label, "已计薪");
                views.setTextViewText(R.id.widget_worked, SalaryMath.durationZh(worked));
                views.setTextViewText(R.id.widget_m3_label, "五险一金");
                views.setTextViewText(R.id.widget_daily, SalaryMath.money(wjyjToday));
            } else {
                views.setTextViewText(R.id.widget_m1_label, "工作进度");
                views.setTextViewText(R.id.widget_pct, SalaryMath.percent0(m.progress) + "%");
                views.setTextViewText(R.id.widget_m2_label, "已计薪");
                views.setTextViewText(R.id.widget_worked, SalaryMath.durationZh(worked));
                views.setViewVisibility(R.id.widget_m1_label, View.VISIBLE);
                views.setViewVisibility(R.id.widget_pct, View.VISIBLE);
                views.setTextViewText(R.id.widget_m3_label, "今日预计");
                views.setTextViewText(R.id.widget_daily, SalaryMath.money(rates.daily));
            }
        }

        if (layoutId == LAYOUT_4X3) {
            views.setTextViewText(R.id.widget_stat_hourly, SalaryMath.money(rates.hourly));
            views.setTextViewText(R.id.widget_stat_minute, SalaryMath.money(rates.minute));
            if (showBreakdown) {
                views.setTextViewText(R.id.widget_chips, "五险一金 " + SalaryMath.money(wjyjToday)
                        + " · 个税 " + SalaryMath.money(taxToday));
            } else {
                // 人生时间换算（与 App 仪表盘快捷卡片一致：咖啡 ¥32 / AirPods ¥1,899 / 手机 ¥7,999）
                views.setTextViewText(R.id.widget_chips, "☕ " + SalaryMath.durationCompact(32 / rates.second)
                        + " · 🎧 " + SalaryMath.durationCompact(1899 / rates.second)
                        + " · 📱 " + SalaryMath.durationCompact(7999 / rates.second));
            }
            if (m.state == STATE_WEEKEND) {
                views.setTextViewText(R.id.widget_s3_label, "本月已入账");
                views.setTextViewText(R.id.widget_daily, SalaryMath.money(
                        rates.daily * SalaryMath.monthWorkdaysDone(now, p)));
                views.setTextViewText(R.id.widget_m1_label, "本周");
                views.setTextViewText(R.id.widget_pct, SalaryMath.weekWorkdaysDone(now, p) + "天");
                views.setTextViewText(R.id.widget_m2_label, "五险一金");
                views.setTextViewText(R.id.widget_worked, SalaryMath.money(wjyjToday));
                views.setViewVisibility(R.id.widget_extra_label, View.VISIBLE);
                views.setViewVisibility(R.id.widget_extra_value, View.VISIBLE);
                views.setTextViewText(R.id.widget_extra_label, "个税");
                views.setTextViewText(R.id.widget_extra_value, SalaryMath.money(taxToday));
            } else {
                views.setTextViewText(R.id.widget_s3_label, "今日预计");
                views.setTextViewText(R.id.widget_daily, SalaryMath.money(rates.daily));
                views.setTextViewText(R.id.widget_m1_label, "工作进度");
                views.setTextViewText(R.id.widget_pct, SalaryMath.percent0(m.progress) + "%");
                views.setTextViewText(R.id.widget_m2_label, "已计薪");
                views.setTextViewText(R.id.widget_worked, SalaryMath.durationZh(worked));
                if (m.extraCountdownTargetMs != null) {
                    views.setViewVisibility(R.id.widget_extra_label, View.VISIBLE);
                    views.setViewVisibility(R.id.widget_extra_value, View.VISIBLE);
                    views.setTextViewText(R.id.widget_extra_label, m.extraLabel);
                    applyCountdown(views, R.id.widget_extra_value, m.extraCountdownTargetMs, "", now);
                } else {
                    stopChronometer(views, R.id.widget_extra_value);
                    views.setTextViewText(R.id.widget_extra_value, "—");
                    hideExtra(views);
                }
            }
        }

        views.setOnClickPendingIntent(R.id.widget_root, mainActivityPendingIntent(context));

        // 2x2：启用拆分时显示 五险一金/个税（休息日为本周口径），否则显示人生换算
        if (layoutId == LAYOUT_2X2) {
            if (showBreakdown) {
                views.setTextViewText(R.id.widget_chips, "五险一金 " + SalaryMath.money(wjyjToday)
                        + " · 个税 " + SalaryMath.money(taxToday));
            } else {
                views.setTextViewText(R.id.widget_chips, "☕ " + SalaryMath.durationCompact(32 / rates.second)
                        + " · 🎧 " + SalaryMath.durationCompact(1899 / rates.second));
            }
        }
        return views;
    }

    // ---------------------------------------------------------------- Chronometer 倒计时

    /** 目标时刻 → Chronometer 倒计时模式。由启动器进程驱动，App 死活无关。 */
    private static void applyCountdown(RemoteViews views, int viewId, long targetEpochMs,
                                       String prefix, Calendar now) {
        long deltaMs = Math.max(0, targetEpochMs - now.getTimeInMillis());
        long base = SystemClock.elapsedRealtime() + deltaMs;
        views.setChronometerCountDown(viewId, true);
        views.setChronometer(viewId, base, prefix + " %s", true);
    }

    private static void stopChronometer(RemoteViews views, int viewId) {
        views.setChronometerCountDown(viewId, false);
        views.setChronometer(viewId, SystemClock.elapsedRealtime(), null, false);
    }

    private static void hideExtra(RemoteViews views) {
        views.setViewVisibility(R.id.widget_extra_label, View.GONE);
        views.setViewVisibility(R.id.widget_extra_value, View.GONE);
    }

    // ---------------------------------------------------------------- 状态模型

    private static final class Model {
        String heroLabel;
        double displayAmount;
        String rateText;                 // 静态文案（工作中/午休的秒薪）
        Long rateCountdownTargetMs;      // 非空 = 该行渲染为 Chronometer 倒计时
        String rateCountdownPrefix;
        double progress;
        int state;
        String badgeText;
        int badgePill;
        int badgeColor;
        String extraLabel;               // null = 隐藏（仅 4x3）
        Long extraCountdownTargetMs;
    }

    private static Model buildModel(SalaryProfile p, SalaryMath.Rates rates,
                                    double earned, double worked, double progress, Calendar now) {
        Model m = new Model();
        m.progress = progress;

        int nowClock = now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60;
        int start;
        int end;
        try {
            start = SalaryMath.parseClock(p.workStartTime);
            end = SalaryMath.parseClock(p.workEndTime);
        } catch (Exception e) {
            p = SalaryProfile.defaults();
            start = SalaryMath.parseClock(p.workStartTime);
            end = SalaryMath.parseClock(p.workEndTime);
        }
        boolean crossesMidnight = end < start;
        boolean inShift = crossesMidnight
                ? (nowClock >= start || nowClock < end)
                : (nowClock >= start && nowClock < end);

        boolean inLunch = false;
        if (inShift && !p.paidBreak) {
            int breakStart = SalaryMath.parseClock(p.breakStartTime);
            int breakEnd = SalaryMath.parseClock(p.breakEndTime);
            inLunch = nowClock >= breakStart && nowClock < breakEnd;
        }

        m.state = resolveState(p, now, inShift, inLunch, nowClock, start);
        switch (m.state) {
            case STATE_WORKING:
                m.heroLabel = "今日已经赚了";
                m.displayAmount = earned;
                m.rateText = "+ ¥" + SalaryMath.rate5(rates.second) + " / 秒";
                m.badgeText = "● 实时中";
                m.badgePill = R.drawable.widget_state_pill_live;
                m.badgeColor = R.color.widget_state_live_text;
                m.extraLabel = "距下班";
                m.extraCountdownTargetMs = todayClockEpoch(now, end);
                break;
            case STATE_LUNCH:
                m.heroLabel = "今日已经赚了";
                m.displayAmount = earned;
                m.rateText = "+ ¥" + SalaryMath.rate5(rates.second) + " / 秒";
                m.badgeText = "午休中";
                m.badgePill = R.drawable.widget_state_pill_lunch;
                m.badgeColor = R.color.widget_state_lunch_text;
                m.extraLabel = "距开工";
                m.extraCountdownTargetMs = todayClockEpoch(now,
                        SalaryMath.parseClock(p.breakEndTime));
                break;
            case STATE_BEFORE:
                m.heroLabel = "今日已经赚了";
                m.displayAmount = earned;
                m.rateCountdownTargetMs = todayClockEpoch(now, start);
                m.rateCountdownPrefix = "距上班";
                m.badgeText = "上班前";
                m.badgePill = R.drawable.widget_state_pill_dim;
                m.badgeColor = R.color.widget_state_rest_text;
                m.extraLabel = null;
                break;
            case STATE_AFTER:
                m.heroLabel = "今日已经赚了";
                m.displayAmount = earned;
                m.rateCountdownTargetMs = SalaryMath.nextShiftStartMillis(p, now);
                m.rateCountdownPrefix = "距上班";
                m.badgeText = "已收工";
                m.badgePill = R.drawable.widget_state_pill_dim;
                m.badgeColor = R.color.widget_state_rest_text;
                m.extraLabel = null;
                break;
            default: // STATE_WEEKEND
                m.heroLabel = "本周已经赚了";
                m.displayAmount = SalaryMath.weekEarned(rates, p, now);
                m.rateCountdownTargetMs = SalaryMath.nextShiftStartMillis(p, now);
                m.rateCountdownPrefix = "距上班";
                m.badgeText = "休息日";
                m.badgePill = R.drawable.widget_state_pill_dim;
                m.badgeColor = R.color.widget_state_rest_text;
                m.extraLabel = null;
                break;
        }
        return m;
    }

    private static int resolveState(SalaryProfile p, Calendar now, boolean inShift,
                                    boolean inLunch, int nowClock, int start) {
        if (SalaryMath.isRestDay(now, p)) return STATE_WEEKEND;
        if (inShift) return inLunch ? STATE_LUNCH : STATE_WORKING;
        boolean crossesMidnight = SalaryMath.parseClock(p.workEndTime) < start;
        boolean beforeStart = !crossesMidnight && nowClock < start;
        return beforeStart ? STATE_BEFORE : STATE_AFTER;
    }

    /** 今天 clockSec 对应的 epoch 毫秒；若已过则顺延到明天。 */
    private static long todayClockEpoch(Calendar now, int clockSec) {
        Calendar c = (Calendar) now.clone();
        c.set(Calendar.HOUR_OF_DAY, clockSec / 3600);
        c.set(Calendar.MINUTE, clockSec % 3600 / 60);
        c.set(Calendar.SECOND, clockSec % 60);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= now.getTimeInMillis()) c.add(Calendar.DATE, 1);
        return c.getTimeInMillis();
    }

    /** 通知栏一行文案：工作中显示金额，其余显示倒计时。 */
    public static String notificationLine(Context context) {
        try {
            SalaryProfile p = WidgetDataStore.loadProfile(context);
            SalaryMath.Rates rates = SalaryMath.calculateRates(p);
            Calendar now = Calendar.getInstance();
            double worked = SalaryMath.getWorkedPaidSeconds(p, now);
            Model m = buildModel(p, rates, worked * rates.second, worked, 0, now);
            if (m.state == STATE_WORKING) {
                return SalaryMath.money(m.displayAmount) + " · +" + SalaryMath.rate5(rates.second) + "/秒";
            }
            if (m.rateCountdownTargetMs != null) {
                long seconds = Math.max(0, (m.rateCountdownTargetMs - System.currentTimeMillis()) / 1000);
                return m.rateCountdownPrefix + " " + SalaryMath.countdown(seconds);
            }
            return m.rateText;
        } catch (Exception e) {
            return "…";
        }
    }

    private static PendingIntent mainActivityPendingIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, 0, intent, flags);
    }
}
