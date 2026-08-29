package com.salaryflow.app.widget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.salaryflow.app.MainActivity;
import com.salaryflow.app.R;

import java.util.Calendar;

/**
 * 秒级实时跳动服务：只要有小组件就在屏幕点亮期间运行。
 *
 * 资源策略：
 * - 每秒对齐秒边界推送一次 RemoteViews：计薪中金额跳动，非计薪时段
 *   （上班前/下班后/周末）显示逐秒倒计时，常驻通知同步刷新（每 5 秒节流）。
 * - 无 wakelock —— 设备休眠时 Handler 自动停转，醒来后第一次 tick 直接
 *   渲染当前值，不需要追赶。
 * - 移除全部小组件时自动 stopSelf，零后台占用。
 */
public class WidgetLiveTickerService extends Service {

    private static final String TAG = "SalaryFlowWidget";
    private static final String CHANNEL_ID = "salaryflow_widget_live";
    private static final int NOTIFICATION_ID = 41;
    private static final int NOTIFICATION_EVERY_N_TICKS = 5;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean ticking = false;
    private int tickCount = 0;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!ticking) return;
            try {
                WidgetUpdater.updateAll(WidgetLiveTickerService.this);
                tickCount++;
                if (tickCount % NOTIFICATION_EVERY_N_TICKS == 0) {
                    refreshNotification();
                }
            } catch (Exception e) {
                Log.w(TAG, "tick failed", e);
            }
            long now = SystemClock.uptimeMillis();
            long next = (now / 1000 + 1) * 1000;
            handler.postDelayed(this, next - now);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForegroundInternal();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundInternal();
        if (!WidgetUpdater.shouldTickerRun(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ticking) {
            ticking = true;
            handler.post(tickRunnable);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        ticking = false;
        handler.removeCallbacks(tickRunnable);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForegroundInternal() {
        Notification notification = buildNotification(
                WidgetRenderer.notificationLine(this));
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    }

    private void refreshNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(
                WidgetRenderer.notificationLine(this)));
    }

    private Notification buildNotification(String amountText) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.widget_sparkle)
                .setContentTitle("今日已经赚了")
                .setContentText(amountText)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "实时薪资小组件",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("计薪时段内，桌面小组件的金额按秒实时跳动");
        channel.setShowBadge(false);
        nm.createNotificationChannel(channel);
    }
}
