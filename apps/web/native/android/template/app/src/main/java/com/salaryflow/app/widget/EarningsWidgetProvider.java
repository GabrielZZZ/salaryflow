package com.salaryflow.app.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;

/**
 * 三个小组件（2x2 / 4x2 / 4x3）共用的基类。
 * 尺寸相关的布局选择在 WidgetUpdater.layoutFor 内按实际分配的宽高完成，
 * 因此用户把小组件拖大/拖小后无需重新添加。
 */
public abstract class EarningsWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            try {
                int layout = WidgetUpdater.layoutFor(manager, id, defaultLayout());
                manager.updateAppWidget(id, WidgetRenderer.build(context, layout));
            } catch (Exception ignored) {
                // 渲染失败不崩溃，下次刷新自愈
            }
        }
        WidgetUpdater.scheduleNext(context);
        WidgetUpdater.ensureTicker(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int appWidgetId, Bundle newOptions) {
        try {
            int layout = WidgetUpdater.layoutFor(manager, appWidgetId, defaultLayout());
            manager.updateAppWidget(appWidgetId, WidgetRenderer.build(context, layout));
        } catch (Exception ignored) {
            // 同上
        }
        WidgetUpdater.scheduleNext(context);
        WidgetUpdater.ensureTicker(context);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // 移除后可能已没有任何小组件：让服务状态与实际绑定情况对齐
        WidgetUpdater.ensureTicker(context);
    }

    @Override
    public void onEnabled(Context context) {
        WidgetUpdater.scheduleNext(context);
        WidgetUpdater.ensureTicker(context);
    }

    @Override
    public void onDisabled(Context context) {
        // 最后一个小组件被移除：取消闹钟与秒级服务，零后台占用
        WidgetUpdater.cancel(context);
        WidgetUpdater.ensureTicker(context);
    }

    protected abstract int defaultLayout();
}
