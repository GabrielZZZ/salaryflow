package com.salaryflow.app.widget;

import org.json.JSONObject;

/**
 * 镜像 packages/core 的 SalaryProfile。字段解析失败时整体回落 DEFAULT，
 * 与 Web 端 loadProfile 的 {...DEFAULT_PROFILE, ...stored} 行为保持一致。
 */
public final class SalaryProfile {
    public static final String DEFAULT_JSON =
            "{\"salary\":15000,\"salaryType\":\"monthly\",\"workStartTime\":\"09:00\","
            + "\"workEndTime\":\"18:00\",\"breakStartTime\":\"12:00\",\"breakEndTime\":\"13:00\","
            + "\"paidBreak\":false,\"includeLivingCost\":false,\"monthlyLivingCost\":0,"
            + "\"monthlyWorkDays\":21.75,\"currency\":\"CNY\"}";

    public final double salary;
    public final String salaryType;
    public final String workStartTime;
    public final String workEndTime;
    public final String breakStartTime;
    public final String breakEndTime;
    public final boolean paidBreak;
    public final boolean includeLivingCost;
    public final double monthlyLivingCost;
    public final double monthlyWorkDays;
    public final int workDaysPerWeek;
    /** 一周七天的看薪标记，index 0 = 周一 */
    public final boolean[] workdays;
    public final boolean deductionsEnabled;
    public final double housingFundRate;
    public final double specialDeduction;

    private SalaryProfile(double salary, String salaryType, String workStartTime, String workEndTime,
                          String breakStartTime, String breakEndTime, boolean paidBreak,
                          boolean includeLivingCost, double monthlyLivingCost, double monthlyWorkDays,
                          int workDaysPerWeek, boolean[] workdays, boolean deductionsEnabled,
                          double housingFundRate, double specialDeduction) {
        this.salary = salary;
        this.salaryType = salaryType;
        this.workStartTime = workStartTime;
        this.workEndTime = workEndTime;
        this.breakStartTime = breakStartTime;
        this.breakEndTime = breakEndTime;
        this.paidBreak = paidBreak;
        this.includeLivingCost = includeLivingCost;
        this.monthlyLivingCost = monthlyLivingCost;
        this.monthlyWorkDays = monthlyWorkDays;
        this.workDaysPerWeek = workDaysPerWeek;
        this.workdays = workdays;
        this.deductionsEnabled = deductionsEnabled;
        this.housingFundRate = housingFundRate;
        this.specialDeduction = specialDeduction;
    }

    public static SalaryProfile defaults() {
        return new SalaryProfile(15000, "monthly", "09:00", "18:00", "12:00", "13:00",
                false, false, 0, 21.75, 5, new boolean[]{true, true, true, true, true, false, false},
                true, 0.12, 0);
    }

    /** profileJson 为 null 或解析失败时回落默认配置，保证小组件永不崩溃。 */
    public static SalaryProfile fromJsonOrNull(String profileJson) {
        SalaryProfile fallback = defaults();
        if (profileJson == null || profileJson.trim().isEmpty()) return fallback;
        try {
            JSONObject o = new JSONObject(profileJson);
            boolean[] workdays = fallback.workdays;
            org.json.JSONArray arr = o.optJSONArray("workdays");
            if (arr != null && arr.length() == 7) {
                boolean[] parsed = new boolean[7];
                for (int i = 0; i < 7; i++) parsed[i] = arr.optBoolean(i, fallback.workdays[i]);
                workdays = parsed;
            }
            return new SalaryProfile(
                    o.optDouble("salary", fallback.salary),
                    o.optString("salaryType", fallback.salaryType),
                    o.optString("workStartTime", fallback.workStartTime),
                    o.optString("workEndTime", fallback.workEndTime),
                    o.optString("breakStartTime", fallback.breakStartTime),
                    o.optString("breakEndTime", fallback.breakEndTime),
                    o.optBoolean("paidBreak", fallback.paidBreak),
                    o.optBoolean("includeLivingCost", fallback.includeLivingCost),
                    o.optDouble("monthlyLivingCost", fallback.monthlyLivingCost),
                    o.optDouble("monthlyWorkDays", fallback.monthlyWorkDays),
                    (int) o.optDouble("workDaysPerWeek", fallback.workDaysPerWeek),
                    workdays,
                    o.optBoolean("deductionsEnabled", fallback.deductionsEnabled),
                    o.optDouble("housingFundRate", fallback.housingFundRate),
                    o.optDouble("specialDeduction", fallback.specialDeduction));
        } catch (Exception e) {
            return fallback;
        }
    }
}
