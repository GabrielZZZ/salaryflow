package com.salaryflow.app.widget;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Web 端通过 WidgetSync 插件同步过来的薪资配置（JSON 字符串）。
 * 小组件只依赖这份本地快照 + 当前时间即可完成计算，App 进程无需存活。
 */
public final class WidgetDataStore {
    private static final String PREFS = "salaryflow_widget";
    private static final String KEY_PROFILE_JSON = "profile_json";

    private WidgetDataStore() {}

    public static void saveProfileJson(Context context, String profileJson) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_PROFILE_JSON, profileJson).apply();
    }

    public static String loadProfileJson(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_PROFILE_JSON, null);
    }

    public static SalaryProfile loadProfile(Context context) {
        return SalaryProfile.fromJsonOrNull(loadProfileJson(context));
    }
}
