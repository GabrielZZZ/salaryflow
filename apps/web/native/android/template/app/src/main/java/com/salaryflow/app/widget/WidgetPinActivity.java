package com.salaryflow.app.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * 调试辅助：免拖拽把小组件钉到主屏幕（requestPinAppWidget），供模拟器/自动化演示使用。
 *
 * 用法：
 *   adb shell am start -n com.salaryflow.app/.widget.WidgetPinActivity --es size 4x2
 *   size ∈ {2x2, 4x2, 4x3}
 *
 * 调试签名构建（debuggable）下额外支持：
 *   adb push profile.json /data/local/tmp/salaryflow-widget-profile.json
 *   adb shell am start ... --ez syncFromFile true
 * 会把该 JSON 合并进当前配置并立即刷新全部小组件，便于验证不同班次状态。
 * Release 构建仅执行钉组件请求，不读写任何文件。
 */
public class WidgetPinActivity extends Activity {

    private static final String DEBUG_PROFILE_FILE = "/data/local/tmp/salaryflow-widget-profile.json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean debuggable = (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (debuggable && getIntent().getBooleanExtra("syncFromFile", false)) {
            syncFromDebugFile();
        }

        String size = getIntent().getStringExtra("size");
        if (!"skip".equals(size)) {
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            if (manager.isRequestPinAppWidgetSupported()) {
                manager.requestPinAppWidget(new ComponentName(this, providerFor(size)), null, null);
            }
        }
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 300);
    }

    private Class<?> providerFor(String size) {
        if ("2x2".equals(size)) return Widget22Provider.class;
        if ("4x3".equals(size)) return Widget43Provider.class;
        return Widget42Provider.class;
    }

    private void syncFromDebugFile() {
        try {
            File file = new File(DEBUG_PROFILE_FILE);
            if (!file.exists()) return;
            FileInputStream in = new FileInputStream(file);
            byte[] buffer = new byte[(int) file.length()];
            int read = in.read(buffer);
            in.close();
            if (read <= 0) return;
            String patchJson = new String(buffer, 0, read, StandardCharsets.UTF_8).trim();

            JSONObject stored;
            String current = WidgetDataStore.loadProfileJson(this);
            stored = (current == null || current.trim().isEmpty())
                    ? new JSONObject(SalaryProfile.DEFAULT_JSON)
                    : new JSONObject(current);
            JSONObject patch = new JSONObject(patchJson);
            Iterator<String> keys = patch.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                stored.put(key, patch.get(key));
            }
            String merged = stored.toString();
            WidgetDataStore.saveProfileJson(this, merged);
            WidgetUpdater.updateAll(this);
            WidgetUpdater.scheduleNext(this);
            WidgetUpdater.ensureTicker(this);
        } catch (Exception ignored) {
            // 调试辅助，失败静默
        }
    }
}
