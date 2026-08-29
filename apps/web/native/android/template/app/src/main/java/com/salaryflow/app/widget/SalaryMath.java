package com.salaryflow.app.widget;

import java.util.Calendar;
import java.util.Locale;

/**
 * packages/core 中薪资计算逻辑的 1:1 Java 移植：
 * parseClock / duration / positionFromShiftStart / getPaidSecondsPerDay /
 * calculateRates / getWorkedPaidSeconds / calculateEarnedToday / formatDuration。
 * 显示格式与 Dashboard.tsx 保持一致。
 */
public final class SalaryMath {
    private static final int DAY = 24 * 60 * 60;

    private SalaryMath() {}

    public static final class Rates {
        public final double daily;
        public final double hourly;
        public final double minute;
        public final double second;
        public final double paidSecondsPerDay;

        Rates(double daily, double hourly, double minute, double second, double paidSecondsPerDay) {
            this.daily = daily;
            this.hourly = hourly;
            this.minute = minute;
            this.second = second;
            this.paidSecondsPerDay = paidSecondsPerDay;
        }
    }

    /** 抛出异常表示配置无效（与 core 一致），调用方需回落默认配置。 */
    public static Rates calculateRates(SalaryProfile p) {
        if (!(p.salary >= 0)) throw new IllegalArgumentException("Salary must be non-negative");
        if (!(p.monthlyWorkDays > 0)) throw new IllegalArgumentException("Monthly work days must be positive");
        double monthlyLivingCost = p.includeLivingCost ? p.monthlyLivingCost : 0;
        if (!(monthlyLivingCost >= 0)) throw new IllegalArgumentException("Living cost must be non-negative");

        double paidSecondsPerDay = getPaidSecondsPerDay(p);
        if (paidSecondsPerDay <= 0) throw new IllegalArgumentException("Paid work duration must be positive");

        double livingCostPerWorkDay = monthlyLivingCost / p.monthlyWorkDays;
        double grossDaily;
        if ("annual".equals(p.salaryType)) grossDaily = (p.salary / 12) / p.monthlyWorkDays;
        else if ("monthly".equals(p.salaryType)) grossDaily = p.salary / p.monthlyWorkDays;
        else if ("daily".equals(p.salaryType)) grossDaily = p.salary;
        else grossDaily = p.salary * (paidSecondsPerDay / 3600.0);

        double daily = Math.max(0, grossDaily - livingCostPerWorkDay);
        double second = daily / paidSecondsPerDay;
        return new Rates(daily, second * 3600, second * 60, second, paidSecondsPerDay);
    }

    public static double getPaidSecondsPerDay(SalaryProfile p) {
        int start = parseClock(p.workStartTime);
        int end = parseClock(p.workEndTime);
        int shift = duration(start, end);
        if (shift <= 0) return 0;
        if (p.paidBreak) return shift;

        int breakStartClock = parseClock(p.breakStartTime);
        int breakEndClock = parseClock(p.breakEndTime);
        int breakStart = positionFromShiftStart(breakStartClock, start);
        int breakDuration = duration(breakStartClock, breakEndClock);
        int breakEnd = breakStart + breakDuration;
        int overlap = (int) Math.max(0, Math.min(shift, breakEnd) - Math.max(0, breakStart));
        return Math.max(0, shift - overlap);
    }

    public static double getWorkedPaidSeconds(SalaryProfile p, Calendar now) {
        if (isRestDay(now, p)) return 0;
        int shiftStartClock = parseClock(p.workStartTime);
        int shiftEndClock = parseClock(p.workEndTime);
        int shiftDuration = duration(shiftStartClock, shiftEndClock);
        double nowClock = secondsSinceMidnight(now);
        double nowPos = positionFromShiftStart(nowClock, shiftStartClock);

        boolean crossesMidnight = shiftEndClock < shiftStartClock;
        boolean beforeStart = !crossesMidnight && nowClock < shiftStartClock;
        boolean afterEnd = !crossesMidnight && nowClock >= shiftEndClock;
        boolean inPreviousCalendarPart = crossesMidnight && nowClock < shiftEndClock;
        boolean inShift = crossesMidnight
                ? (nowClock >= shiftStartClock || inPreviousCalendarPart)
                : (!beforeStart && !afterEnd);

        if (!inShift) {
            if (afterEnd || (crossesMidnight && nowClock >= shiftEndClock && nowClock < shiftStartClock)) {
                return getPaidSecondsPerDay(p);
            }
            return 0;
        }

        double elapsed = Math.min(nowPos, shiftDuration);
        if (p.paidBreak) return elapsed;

        int breakStartClock = parseClock(p.breakStartTime);
        int breakEndClock = parseClock(p.breakEndTime);
        int breakStart = positionFromShiftStart(breakStartClock, shiftStartClock);
        int breakDuration = duration(breakStartClock, breakEndClock);
        int breakEnd = breakStart + breakDuration;
        double unpaidElapsed = Math.max(0, Math.min(elapsed, breakEnd) - Math.max(0, breakStart));
        return Math.max(0, Math.min(getPaidSecondsPerDay(p), elapsed - unpaidElapsed));
    }

    public static double calculateEarnedToday(SalaryProfile p, Calendar now) {
        return getWorkedPaidSeconds(p, now) * calculateRates(p).second;
    }

    public static int parseClock(String value) {
        if (value == null) throw new IllegalArgumentException("Invalid time format");
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(\\d{1,2}):(\\d{2})$").matcher(value);
        if (!m.matches()) throw new IllegalArgumentException("Invalid time format");
        int h = Integer.parseInt(m.group(1));
        int min = Integer.parseInt(m.group(2));
        if (h < 0 || h > 23 || min < 0 || min > 59) throw new IllegalArgumentException("Invalid clock time");
        return h * 3600 + min * 60;
    }

    private static int duration(int start, int end) {
        return end >= start ? end - start : DAY - start + end;
    }

    private static double positionFromShiftStart(double clock, int shiftStart) {
        return clock >= shiftStart ? clock - shiftStart : DAY - shiftStart + clock;
    }

    private static int positionFromShiftStart(int clock, int shiftStart) {
        return clock >= shiftStart ? clock - shiftStart : DAY - shiftStart + clock;
    }

    private static double secondsSinceMidnight(Calendar c) {
        return c.get(Calendar.HOUR_OF_DAY) * 3600
                + c.get(Calendar.MINUTE) * 60
                + c.get(Calendar.SECOND)
                + c.get(Calendar.MILLISECOND) / 1000.0;
    }

    // ---------- 显示格式（与 Dashboard.tsx 完全一致） ----------

    /** money(n) = ¥ + toLocaleString('zh-CN', 两位小数千分位） */
    public static String money(double value) {
        return "¥" + moneyIntPart(value) + "." + moneyFracPart(value);
    }

    /** 金额整数部分（千分位），供混合字号排版使用 */
    public static String moneyIntPart(double value) {
        long cents = Math.round(Math.max(0, value) * 100);
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(Locale.CHINA);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(0);
        nf.setGroupingUsed(true);
        return nf.format(cents / 100);
    }

    /** 金额小数部分（固定两位），供混合字号排版使用 */
    public static String moneyFracPart(double value) {
        long cents = Math.round(Math.max(0, value) * 100);
        return String.format(Locale.US, "%02d", cents % 100);
    }

    /** rates.second.toFixed(5) */
    public static String rate5(double value) {
        return String.format(Locale.US, "%.5f", Math.max(0, value));
    }

    /** formatDuration：X天X小时X分钟[补秒] */
    public static String durationZh(double totalSeconds) {
        if (Double.isNaN(totalSeconds) || Double.isInfinite(totalSeconds)) return "∞";
        long s = Math.max(0, Math.round(totalSeconds));
        long days = s / 86400;
        long hours = (s % 86400) / 3600;
        long minutes = (s % 3600) / 60;
        long seconds = s % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("天");
        if (hours > 0) sb.append(hours).append("小时");
        if (minutes > 0) sb.append(minutes).append("分钟");
        if (days == 0 && hours == 0 && seconds > 0) sb.append(seconds).append("秒");
        return sb.length() == 0 ? "0秒" : sb.toString();
    }

    /** 换算胶囊用的紧凑时长：只保留最大单位（19分钟 / 19小时 / 3天）。 */
    public static String durationCompact(double totalSeconds) {
        if (Double.isNaN(totalSeconds) || Double.isInfinite(totalSeconds)) return "∞";
        long s = Math.max(0, Math.round(totalSeconds));
        if (s >= 86400) return (s / 86400) + "天";
        if (s >= 3600) return (s / 3600) + "小时";
        if (s >= 60) return (s / 60) + "分钟";
        return s + "秒";
    }

    /** progress.toFixed(0) */
    public static String percent0(double value) {
        return String.format(Locale.US, "%.0f", Math.max(0, value));
    }

    // ---------- 非工作时段的动态内容 ----------

    /** 月度总包（与 calculateRates 的 grossDaily 同口径反推到月）。 */
    public static double monthGross(SalaryProfile p, double paidSecondsPerDay) {
        double grossDaily;
        if ("annual".equals(p.salaryType)) grossDaily = (p.salary / 12) / p.monthlyWorkDays;
        else if ("monthly".equals(p.salaryType)) grossDaily = p.salary / p.monthlyWorkDays;
        else if ("daily".equals(p.salaryType)) grossDaily = p.salary;
        else grossDaily = p.salary * (paidSecondsPerDay / 3600.0);
        return Math.max(0, grossDaily) * p.monthlyWorkDays;
    }

    /** 月度个税（月度税率表速算，估算用）。 */
    public static double monthlyIncomeTax(double taxable) {
        double[][] brackets = {{3000, .03, 0}, {12000, .10, 210}, {25000, .20, 1410},
                {35000, .25, 2660}, {55000, .30, 4410}, {80000, .35, 7160}, {Double.MAX_VALUE, .45, 15160}};
        for (double[] b : brackets) {
            if (taxable <= b[0]) return Math.max(0, taxable * b[1] - b[2]);
        }
        return 0;
    }

    /**
     * 月度总包拆分：{gross, social, fund, tax, net}。
     * 假设社保个人 10.5%，公积金比例可配，起征点 5000，专项附加可配，月度税率表速算。
     * deductionsEnabled 关闭时返回 null。
     */
    public static double[] payBreakdownMonthly(SalaryProfile p, double paidSecondsPerDay) {
        if (!p.deductionsEnabled) return null;
        double gross = monthGross(p, paidSecondsPerDay);
        double social = gross * 0.105;
        double fund = gross * Math.min(0.15, Math.max(0, p.housingFundRate));
        double taxable = Math.max(0, gross - social - fund - 5000 - Math.max(0, p.specialDeduction));
        double tax = monthlyIncomeTax(taxable);
        double net = Math.max(0, gross - social - fund - tax);
        return new double[]{gross, social, fund, tax, net};
    }

    /** 休息日：该天不在用户配置的工作日内，不计薪。 */
    public static boolean isRestDay(Calendar c, SalaryProfile p) {
        int index = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; // 周一=0
        boolean[] workdays = p.workdays != null && p.workdays.length == 7 ? p.workdays : new boolean[]{true, true, true, true, true, false, false};
        return !workdays[index];
    }

    /** 本周已完成的工作日天数（不含今天；周末分支里今天必为休息日）。 */
    public static int weekWorkdaysDone(Calendar now, SalaryProfile p) {
        Calendar day = (Calendar) now.clone();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        Calendar monday = (Calendar) day.clone();
        monday.add(Calendar.DATE, -((now.get(Calendar.DAY_OF_WEEK) + 5) % 7));
        int done = 0;
        while (day.getTimeInMillis() > monday.getTimeInMillis()) {
            day.add(Calendar.DATE, -1);
            if (!isRestDay(day, p)) done++;
        }
        return done;
    }

    /** 本月已完成的工作日天数（不含今天）。 */
    public static int monthWorkdaysDone(Calendar now, SalaryProfile p) {
        int done = 0;
        Calendar c = (Calendar) now.clone();
        while (c.get(Calendar.DAY_OF_MONTH) > 1) {
            c.add(Calendar.DATE, -1);
            if (!isRestDay(c, p)) done++;
        }
        return done;
    }

    /** 逐秒跳动的倒计时文案：HH:MM:SS，跨天时带 X天 前缀。 */
    public static String countdown(long totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        long days = totalSeconds / 86400;
        long rem = totalSeconds % 86400;
        String hms = String.format(Locale.US, "%02d:%02d:%02d",
                rem / 3600, rem % 3600 / 60, rem % 60);
        return days > 0 ? days + "天 " + hms : hms;
    }

    /** 下一个工作日上班时刻的毫秒时间戳（跨休息日安全）。 */
    public static long nextShiftStartMillis(SalaryProfile p, Calendar now) {
        Calendar c = (Calendar) now.clone();
        int start = parseClock(p.workStartTime);
        for (int d = 1; d <= 8; d++) {
            c.add(Calendar.DATE, 1);
            if (isRestDay(c, p)) continue;
            c.set(Calendar.HOUR_OF_DAY, start / 3600);
            c.set(Calendar.MINUTE, start % 3600 / 60);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            if (c.getTimeInMillis() > now.getTimeInMillis()) return c.getTimeInMillis();
        }
        return now.getTimeInMillis() + 86_400_000L;
    }

    /** 距下一个工作日上班时刻的秒数（含当天时钟差），跨周末安全。 */
    public static long secondsToNextShiftStart(SalaryProfile p, Calendar now) {
        int start = parseClock(p.workStartTime);
        Calendar c = (Calendar) now.clone();
        for (int d = 1; d <= 8; d++) {
            c.add(Calendar.DATE, 1);
            if (isRestDay(c, p)) continue;
            Calendar target = (Calendar) c.clone();
            target.set(Calendar.HOUR_OF_DAY, start / 3600);
            target.set(Calendar.MINUTE, start % 3600 / 60);
            target.set(Calendar.SECOND, start % 60);
            target.set(Calendar.MILLISECOND, 0);
            long diff = (target.getTimeInMillis() - now.getTimeInMillis()) / 1000;
            if (diff > 0) return diff;
        }
        return 86400;
    }

    /** 本周（截至今天之前的看薪日）已入账：休息日=本周全部看薪日；金额=日薪×天数。 */
    public static double weekEarned(SalaryMath.Rates rates, SalaryProfile p, Calendar now) {
        int done = 0;
        Calendar c = (Calendar) now.clone();
        for (int d = 1; d <= 7; d++) {
            c.add(Calendar.DATE, -1);
            if (isRestDay(c, p)) continue;
            done++;
            if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) break;
        }
        return rates.daily * Math.max(0, done);
    }
}
