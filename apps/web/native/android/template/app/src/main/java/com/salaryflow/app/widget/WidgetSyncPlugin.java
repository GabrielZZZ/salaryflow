package com.salaryflow.app.widget;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONObject;

/**
 * Web → 原生 的唯一桥：把 WebView localStorage 里的薪资配置快照落到
 * SharedPreferences，并立刻刷新桌面小组件。配置不变化时 App 打开一次
 * 只写一次；小组件的分钟级刷新完全由原生闹钟承担。
 */
@CapacitorPlugin(name = "WidgetSync")
public class WidgetSyncPlugin extends Plugin {

    @PluginMethod
    public void sync(PluginCall call) {
        String profileJson = call.getString("profileJson");
        if (profileJson == null || profileJson.trim().isEmpty()) {
            call.reject("profileJson is required");
            return;
        }
        try {
            new JSONObject(profileJson);
        } catch (Exception e) {
            call.reject("profileJson is not valid JSON");
            return;
        }
        WidgetDataStore.saveProfileJson(getContext(), profileJson);
        WidgetUpdater.updateAll(getContext());
        WidgetUpdater.scheduleNext(getContext());
        WidgetUpdater.ensureTicker(getContext());
        call.resolve();
    }
}
