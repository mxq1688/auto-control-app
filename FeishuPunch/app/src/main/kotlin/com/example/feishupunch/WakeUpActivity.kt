package com.example.feishupunch

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.feishupunch.service.PunchAccessibilityService

/**
 * 唤醒屏幕并打开飞书
 */
class WakeUpActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WakeUpActivity"
        private const val FEISHU_PACKAGE = "com.ss.android.lark"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        // 在 super.onCreate 之前获取 WakeLock
        acquireWakeLock()
        
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "WakeUpActivity 启动 - 息屏唤醒")
        
        // 设置所有可能的窗口标志来唤醒屏幕
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }
        
        // 设置一个简单的视图
        val textView = TextView(this).apply {
            text = "正在打开飞书..."
            textSize = 24f
            setPadding(100, 200, 100, 200)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        setContentView(textView)
        
        // 先让无障碍服务双击唤醒屏幕
        PunchAccessibilityService.instance?.doubleTapToWake()
        
        // 2秒后打开飞书
        handler.postDelayed({
            launchFeishu()
        }, 2000)
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "FeishuPunch:WakeUpActivity"
            )
            wakeLock?.acquire(60000) // 1分钟
            Log.d(TAG, "WakeLock 已获取")
        } catch (e: Exception) {
            Log.e(TAG, "获取 WakeLock 失败: ${e.message}")
        }
    }

    private fun launchFeishu() {
        Log.d(TAG, "打开飞书")
        
        try {
            val intent = packageManager.getLaunchIntentForPackage(FEISHU_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
                Log.d(TAG, "飞书已启动")
            } else {
                Log.e(TAG, "未找到飞书")
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动飞书失败: ${e.message}")
        }
        
        // 关闭自己
        handler.postDelayed({
            finishAndRemoveTask()
        }, 1000)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try {
            wakeLock?.release()
        } catch (e: Exception) {
            // ignored
        }
    }
}

