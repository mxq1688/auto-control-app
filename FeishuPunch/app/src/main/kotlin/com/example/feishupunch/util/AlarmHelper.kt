package com.example.feishupunch.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.feishupunch.receiver.AlarmReceiver
import java.util.Calendar
import kotlin.random.Random

/**
 * 闹钟管理工具类
 */
class AlarmHelper(private val context: Context) {

    companion object {
        private const val TAG = "AlarmHelper"
        private const val MORNING_REQUEST_CODE = 1001
        private const val EVENING_REQUEST_CODE = 1002
        private const val CLOSE_APP_REQUEST_CODE = 1003
        private const val CLOSE_APP_EVENING_REQUEST_CODE = 1004
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * 设置上班工作闹钟（时间范围随机）
     */
    fun setMorningAlarm(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        val randomTime = getRandomTime(startHour, startMinute, endHour, endMinute)
        val calendar = getNextAlarmTime(randomTime.first, randomTime.second)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_MORNING_PUNCH
        }
        
        setAlarm(calendar.timeInMillis, intent, MORNING_REQUEST_CODE)
        Log.d(TAG, "上班闹钟已设置: 范围 ${formatTime(startHour, startMinute)}-${formatTime(endHour, endMinute)}，随机时间: ${formatTime(randomTime.first, randomTime.second)}")
    }

    /**
     * 设置下班工作闹钟（时间范围随机）
     */
    fun setEveningAlarm(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        val randomTime = getRandomTime(startHour, startMinute, endHour, endMinute)
        val calendar = getNextAlarmTime(randomTime.first, randomTime.second)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_EVENING_PUNCH
        }
        
        setAlarm(calendar.timeInMillis, intent, EVENING_REQUEST_CODE)
        Log.d(TAG, "下班闹钟已设置: 范围 ${formatTime(startHour, startMinute)}-${formatTime(endHour, endMinute)}，随机时间: ${formatTime(randomTime.first, randomTime.second)}")
    }
    
    /**
     * 在时间范围内生成随机时间
     */
    private fun getRandomTime(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int): Pair<Int, Int> {
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute
        
        // 如果结束时间小于等于开始时间，直接返回开始时间
        if (endMinutes <= startMinutes) {
            return Pair(startHour, startMinute)
        }
        
        val randomMinutes = Random.nextInt(startMinutes, endMinutes + 1)
        return Pair(randomMinutes / 60, randomMinutes % 60)
    }
    
    // 兼容旧接口
    fun setMorningAlarm(hour: Int, minute: Int) {
        setMorningAlarm(hour, minute, hour, minute)
    }
    
    fun setEveningAlarm(hour: Int, minute: Int) {
        setEveningAlarm(hour, minute, hour, minute)
    }

    /**
     * 设置关闭飞书闹钟（晚上23:00）
     */
    fun setCloseAppAlarm(hour: Int = 23, minute: Int = 0) {
        val calendar = getNextAlarmTime(hour, minute)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_CLOSE_FEISHU
        }
        
        setAlarm(calendar.timeInMillis, intent, CLOSE_APP_REQUEST_CODE)
        Log.d(TAG, "关闭飞书闹钟已设置: ${formatTime(hour, minute)}")
    }

    /**
     * 设置下班前关闭飞书闹钟（18:20）
     */
    fun setCloseAppEveningAlarm(hour: Int = 18, minute: Int = 20) {
        val calendar = getNextAlarmTime(hour, minute)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_CLOSE_FEISHU
        }
        
        setAlarm(calendar.timeInMillis, intent, CLOSE_APP_EVENING_REQUEST_CODE)
        Log.d(TAG, "下班前关闭飞书闹钟已设置: ${formatTime(hour, minute)}")
    }

    /**
     * 取消所有闹钟
     */
    fun cancelAllAlarms() {
        cancelAlarm(AlarmReceiver.ACTION_MORNING_PUNCH, MORNING_REQUEST_CODE)
        cancelAlarm(AlarmReceiver.ACTION_EVENING_PUNCH, EVENING_REQUEST_CODE)
        cancelAlarm(AlarmReceiver.ACTION_CLOSE_FEISHU, CLOSE_APP_REQUEST_CODE)
        cancelAlarm(AlarmReceiver.ACTION_CLOSE_FEISHU, CLOSE_APP_EVENING_REQUEST_CODE)
        Log.d(TAG, "所有闹钟已取消")
    }

    private fun setAlarm(triggerTime: Long, intent: Intent, requestCode: Int) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // 使用 setAlarmClock - 最强力的闹钟方式，和系统闹钟一样可以唤醒屏幕
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val alarmInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
                alarmManager.setAlarmClock(alarmInfo, pendingIntent)
                Log.d(TAG, "使用 setAlarmClock 设置闹钟")
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "设置闹钟失败: ${e.message}")
            // 降级到普通闹钟
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } catch (e2: Exception) {
                Log.e(TAG, "降级闹钟也失败: ${e2.message}")
            }
        }
    }

    private fun cancelAlarm(action: String, requestCode: Int) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * 获取下一次闹钟时间
     */
    private fun getNextAlarmTime(hour: Int, minute: Int): Calendar {
        val now = Calendar.getInstance()
        val alarm = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 如果设定时间已过，设置为明天
        if (alarm.before(now) || alarm == now) {
            alarm.add(Calendar.DAY_OF_MONTH, 1)
        }

        return alarm
    }

    private fun formatTime(hour: Int, minute: Int): String {
        return String.format("%02d:%02d", hour, minute)
    }
}

