package com.salaryflow.app;

import android.os.Bundle;

import com.salaryflow.app.widget.WidgetSyncPlugin;
import com.salaryflow.app.widget.WidgetUpdater;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    /**
     * Capacitor 8 在 super.onCreate() 内部即创建 Bridge，
     * 因此本地插件必须在 super.load() 之前加入 bridgeBuilder。
     */
    @Override
    protected void load() {
        registerPlugin(WidgetSyncPlugin.class);
        super.load();
    }

    @Override
    public void onResume() {
        super.onResume();
        // App 在前台时启动秒级跳动服务不受后台限制，作为最可靠的启动路径
        WidgetUpdater.ensureTicker(this);
    }
}
