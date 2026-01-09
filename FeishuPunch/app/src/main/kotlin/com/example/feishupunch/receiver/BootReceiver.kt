package com.example.feishupunch.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.feishupunch.service.PunchForegroundService
import com.example.feishupunch.util.AlarmHelper
import com.example.feishupunch.util.PreferenceHelper

/**
 * 开机广播接收器 - 开机后自动恢复定时任务
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "收到开机广播")
            
            val prefs = PreferenceHelper(context)
            
            // 如果定时已启用，重新设置闹钟
            if (prefs.isScheduleEnabled()) {
                Log.d(TAG, "恢复定时任务")
                
                AlarmHelper(context).apply {
                    setMorningAlarm(prefs.getMorningHour(), prefs.getMorningMinute())
                    setEveningAlarm(prefs.getEveningHour(), prefs.getEveningMinute())
                }
                
                // 启动前台服务
                val serviceIntent = Intent(context, PunchForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}

