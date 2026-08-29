package com.salaryflow.app.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 系统闹钟/时间变化驱动的轻量刷新入口。
 * 处理：内部 REFRESH 闹钟、时间/时区/日期变化、开机、APK 更新。
 * 每次只做一次计算 + 一次 RemoteViews 推送 + 一次闹钟重排，无常驻服务、无轮询。
 */
public class WidgetRefreshReceiver extends BroadcastReceiver {
    private static final String TAG = "SalaryFlowWidget";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        try {
            WidgetUpdater.updateAll(context);
            WidgetUpdater.scheduleNext(context);
            WidgetUpdater.ensureTicker(context);
        } catch (Exception e) {
            Log.w(TAG, "refresh failed", e);
        }
    }
}
