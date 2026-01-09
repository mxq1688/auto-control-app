package com.example.feishupunch.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.feishupunch.R
import com.example.feishupunch.WakeUpActivity
import com.example.feishupunch.service.PunchAccessibilityService
import com.example.feishupunch.util.AlarmHelper
import com.example.feishupunch.util.PreferenceHelper

/**
 * 闹钟广播接收器 - 触发工作
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        const val ACTION_MORNING_PUNCH = "com.example.feishupunch.MORNING_PUNCH"
        const val ACTION_EVENING_PUNCH = "com.example.feishupunch.EVENING_PUNCH"
        const val ACTION_CLOSE_FEISHU = "com.example.feishupunch.CLOSE_FEISHU"
        private const val CHANNEL_ID = "punch_alarm_channel"
        private const val NOTIFICATION_ID = 9999
        private const val FEISHU_PACKAGE = "com.ss.android.lark"
        
        // 静态 WakeLock 防止被回收
        @Volatile
        private var wakeLock: PowerManager.WakeLock? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "收到闹钟广播: $action")
        
        // 获取静态 WakeLock 保持唤醒
        acquireWakeLock(context)
        
        // 处理不同类型的闹钟
        val prefs = PreferenceHelper(context)
        when (action) {
            ACTION_MORNING_PUNCH -> {
                // 使用全屏通知唤醒屏幕
                showFullScreenNotification(context)
                // 重新设置明天的闹钟（时间范围随机）
                AlarmHelper(context).setMorningAlarm(
                    prefs.getMorningStartHour(), prefs.getMorningStartMinute(),
                    prefs.getMorningEndHour(), prefs.getMorningEndMinute()
                )
            }
            ACTION_EVENING_PUNCH -> {
                // 使用全屏通知唤醒屏幕
                showFullScreenNotification(context)
                // 重新设置明天的闹钟（时间范围随机）
                AlarmHelper(context).setEveningAlarm(
                    prefs.getEveningStartHour(), prefs.getEveningStartMinute(),
                    prefs.getEveningEndHour(), prefs.getEveningEndMinute()
                )
            }
            ACTION_CLOSE_FEISHU -> {
                // 关闭飞书
                closeFeishu(context)
                // 重新设置明天的闹钟（两个时间点）
                AlarmHelper(context).apply {
                    setCloseAppEveningAlarm(18, 20)
                    setCloseAppAlarm(19, 20)
                }
            }
        }
    }

    /**
     * 关闭飞书
     */
    private fun closeFeishu(context: Context) {
        Log.d(TAG, "执行关闭飞书")
        
        try {
            // 使用无障碍服务关闭飞书
            PunchAccessibilityService.instance?.closeApp(FEISHU_PACKAGE)
            Log.d(TAG, "已请求关闭飞书")
        } catch (e: Exception) {
            Log.e(TAG, "关闭飞书失败: ${e.message}")
        }
    }

    /**
     * 获取 WakeLock 保持屏幕唤醒
     */
    @Suppress("DEPRECATION")
    private fun acquireWakeLock(context: Context) {
        try {
            if (wakeLock == null || wakeLock?.isHeld != true) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                    "FeishuPunch:AlarmWakeLock"
                )
                wakeLock?.acquire(120000) // 2分钟
                Log.d(TAG, "WakeLock 已获取")
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取 WakeLock 失败: ${e.message}")
        }
    }

    /**
     * 显示全屏通知（类似闹钟/来电，可以在锁屏时点亮屏幕）
     */
    private fun showFullScreenNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "定时任务",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用于定时任务的通知"
                setBypassDnd(true)  // 绕过勿扰模式
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // 创建全屏 Intent
        val fullScreenIntent = Intent(context, WakeUpActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 构建通知
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("乐逍遥")
            .setContentText("正在执行定时任务...")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)  // 关键：全屏 Intent
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "已发送全屏通知")
        
        // 同时尝试直接启动 Activity
        try {
            context.startActivity(fullScreenIntent)
            Log.d(TAG, "已启动 WakeUpActivity")
        } catch (e: Exception) {
            Log.e(TAG, "启动 WakeUpActivity 失败: ${e.message}")
        }
    }
}

