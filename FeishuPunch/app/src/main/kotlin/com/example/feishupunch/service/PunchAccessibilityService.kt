package com.example.feishupunch.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.feishupunch.model.Flow
import com.example.feishupunch.model.FlowStep
import com.example.feishupunch.model.StepType
import com.example.feishupunch.util.PreferenceHelper

/**
 * 无障碍服务 - 用于自动操作打卡
 */
class PunchAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PunchService"
        
        // 服务实例（用于外部调用）
        var instance: PunchAccessibilityService? = null
            private set
        
        // 是否正在执行工作
        var isPunching = false
    }
    
    private lateinit var prefs: PreferenceHelper
    
    // 当前目标包名
    private var targetPackage: String = PreferenceHelper.PACKAGE_FEISHU

    private val handler = Handler(Looper.getMainLooper())
    
    // 流程执行相关
    private var currentFlow: Flow? = null
    private var currentStepIndex = 0
    private var retryCount = 0
    private val maxRetry = 3

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = PreferenceHelper(this)
        Log.d(TAG, "无障碍服务已连接")
        Toast.makeText(this, "乐逍遥服务已启动", Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 流程执行由 handler 控制，不再依赖事件
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "无障碍服务已销毁")
    }

    /**
     * 开始执行工作流程
     */
    fun startPunchProcess() {
        if (isPunching) {
            Log.d(TAG, "正在工作中，请勿重复操作")
            return
        }
        
        // 获取当前目标包名
        targetPackage = prefs.getTargetPackage()
        Log.d(TAG, "目标APP包名: $targetPackage")
        
        if (targetPackage.isEmpty()) {
            finishPunch(false, "未设置目标APP")
            return
        }
        
        // 获取流程
        currentFlow = prefs.getFlow()
        if (currentFlow == null || currentFlow!!.steps.isEmpty()) {
            finishPunch(false, "未配置执行流程")
            return
        }
        
        isPunching = true
        currentStepIndex = 0
        retryCount = 0
        
        Log.d(TAG, "开始执行流程: ${currentFlow!!.name}, 共 ${currentFlow!!.steps.size} 步")
        
        // 先唤醒屏幕
        wakeUpScreen()
        
        // 延迟执行第一步
        handler.postDelayed({
            executeCurrentStep()
        }, 500)
        
        // 设置超时
        handler.postDelayed({
            if (isPunching) {
                Log.d(TAG, "工作超时")
                finishPunch(false, "工作超时")
            }
        }, 120000) // 120秒超时
    }
    
    /**
     * 执行当前步骤
     */
    private fun executeCurrentStep() {
        val flow = currentFlow ?: return
        
        if (currentStepIndex >= flow.steps.size) {
            finishPunch(true, "流程执行完成")
            return
        }
        
        val step = flow.steps[currentStepIndex]
        Log.d(TAG, "执行步骤 ${currentStepIndex + 1}/${flow.steps.size}: ${step.getDescription()}")
        
        when (step.type) {
            StepType.OPEN_APP -> executeOpenApp()
            StepType.CLICK_XY -> executeClickXY(step.x, step.y)
            StepType.CLICK_TEXT -> executeClickText(step.text)
            StepType.LONG_PRESS -> executeLongPress(step.x, step.y, step.duration)
            StepType.DOUBLE_CLICK -> executeDoubleClick(step.x, step.y)
            StepType.SWIPE -> executeSwipe(step.x, step.y, step.x2, step.y2, step.duration)
            StepType.DELAY -> executeDelay(step.delay)
            StepType.BACK -> executeBack()
            StepType.HOME -> executeHome()
            StepType.RECENT_APPS -> executeRecentApps()
            StepType.NOTIFICATIONS -> executeNotifications()
        }
    }
    
    /**
     * 执行：打开APP
     */
    private fun executeOpenApp() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(targetPackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                Log.d(TAG, "启动APP成功: $targetPackage")
                
                // 等待APP启动
                handler.postDelayed({
                    moveToNextStep()
                }, 3000)
            } else {
                Log.e(TAG, "未找到APP: $targetPackage")
                retryOrFail("未找到目标APP")
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动APP失败: ${e.message}")
            retryOrFail("启动APP失败")
        }
    }
    
    /**
     * 执行：点击坐标
     */
    private fun executeClickXY(x: Int, y: Int) {
        Log.d(TAG, "点击坐标: ($x, $y)")
        val success = performClick(x.toFloat(), y.toFloat())
        
        handler.postDelayed({
            if (success) {
                moveToNextStep()
            } else {
                retryOrFail("点击坐标失败")
            }
        }, 500)
    }
    
    /**
     * 执行：点击文本
     */
    private fun executeClickText(text: String) {
        Log.d(TAG, "查找并点击文本: $text")
        
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                if (clickNode(node)) {
                    Log.d(TAG, "点击文本成功: $text")
                    handler.postDelayed({
                        moveToNextStep()
                    }, 1000)
                    rootNode.recycle()
                    return
                }
            }
            rootNode.recycle()
        }
        
        // 未找到，重试
        handler.postDelayed({
            retryOrFail("未找到文本: $text")
        }, 500)
    }
    
    /**
     * 执行：等待
     */
    private fun executeDelay(delay: Long) {
        Log.d(TAG, "等待 ${delay}ms")
        handler.postDelayed({
            moveToNextStep()
        }, delay)
    }
    
    /**
     * 执行：返回
     */
    private fun executeBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({
            moveToNextStep()
        }, 500)
    }
    
    /**
     * 执行：回到桌面
     */
    private fun executeHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({
            moveToNextStep()
        }, 500)
    }
    
    /**
     * 执行：最近任务
     */
    private fun executeRecentApps() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
        handler.postDelayed({
            moveToNextStep()
        }, 500)
    }
    
    /**
     * 执行：通知栏
     */
    private fun executeNotifications() {
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        handler.postDelayed({
            moveToNextStep()
        }, 500)
    }
    
    /**
     * 执行：长按
     */
    private fun executeLongPress(x: Int, y: Int, duration: Long) {
        Log.d(TAG, "长按: ($x, $y), 持续 ${duration}ms")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path()
            path.moveTo(x.toFloat(), y.toFloat())
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    handler.postDelayed({ moveToNextStep() }, 300)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    retryOrFail("长按手势取消")
                }
            }, null)
        } else {
            retryOrFail("系统版本过低")
        }
    }
    
    /**
     * 执行：双击
     */
    private fun executeDoubleClick(x: Int, y: Int) {
        Log.d(TAG, "双击: ($x, $y)")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path1 = Path()
            path1.moveTo(x.toFloat(), y.toFloat())
            
            val gesture1 = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path1, 0, 50))
                .build()
            
            dispatchGesture(gesture1, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    // 短暂延迟后第二次点击
                    handler.postDelayed({
                        val path2 = Path()
                        path2.moveTo(x.toFloat(), y.toFloat())
                        
                        val gesture2 = GestureDescription.Builder()
                            .addStroke(GestureDescription.StrokeDescription(path2, 0, 50))
                            .build()
                        
                        dispatchGesture(gesture2, object : GestureResultCallback() {
                            override fun onCompleted(gestureDescription: GestureDescription?) {
                                handler.postDelayed({ moveToNextStep() }, 300)
                            }
                            override fun onCancelled(gestureDescription: GestureDescription?) {
                                retryOrFail("双击第二次取消")
                            }
                        }, null)
                    }, 80)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    retryOrFail("双击第一次取消")
                }
            }, null)
        } else {
            retryOrFail("系统版本过低")
        }
    }
    
    /**
     * 执行：滑动
     */
    private fun executeSwipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long) {
        Log.d(TAG, "滑动: ($x1, $y1) → ($x2, $y2), 持续 ${duration}ms")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path()
            path.moveTo(x1.toFloat(), y1.toFloat())
            path.lineTo(x2.toFloat(), y2.toFloat())
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    handler.postDelayed({ moveToNextStep() }, 300)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    retryOrFail("滑动手势取消")
                }
            }, null)
        } else {
            retryOrFail("系统版本过低")
        }
    }
    
    /**
     * 进入下一步
     */
    private fun moveToNextStep() {
        currentStepIndex++
        retryCount = 0
        executeCurrentStep()
    }
    
    /**
     * 重试或失败
     */
    private fun retryOrFail(message: String) {
        retryCount++
        if (retryCount >= maxRetry) {
            Log.d(TAG, "重试次数过多，跳过当前步骤: $message")
            // 跳过当前步骤继续执行
            moveToNextStep()
        } else {
            Log.d(TAG, "重试步骤 ($retryCount/$maxRetry): $message")
            handler.postDelayed({
                executeCurrentStep()
            }, 1500)
        }
    }

    
    /**
     * 唤醒屏幕
     */
    @Suppress("DEPRECATION")
    private fun wakeUpScreen() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isInteractive) {
                val wakeLock = powerManager.newWakeLock(
                    android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    android.os.PowerManager.ON_AFTER_RELEASE,
                    "FeishuPunch:WakeLock"
                )
                wakeLock.acquire(30000) // 30秒
                Log.d(TAG, "屏幕已唤醒")
            }
        } catch (e: Exception) {
            Log.e(TAG, "唤醒屏幕失败: ${e.message}")
        }
    }


    /**
     * 点击节点
     */
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        // 尝试直接点击
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        
        // 尝试点击父节点
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent.recycle()
                return result
            }
            val temp = parent.parent
            parent.recycle()
            parent = temp
        }
        
        // 使用手势点击
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        return performClick(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    /**
     * 使用手势执行点击
     */
    private fun performClick(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }
        
        val path = Path()
        path.moveTo(x, y)
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        
        return dispatchGesture(gesture, null, null)
    }

    /**
     * 向下滚动
     */
    private fun scrollDown(rootNode: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val displayMetrics = resources.displayMetrics
            val startX = displayMetrics.widthPixels / 2f
            val startY = displayMetrics.heightPixels * 0.7f
            val endY = displayMetrics.heightPixels * 0.3f
            
            val path = Path()
            path.moveTo(startX, startY)
            path.lineTo(startX, endY)
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 500))
                .build()
            
            dispatchGesture(gesture, null, null)
        }
    }

    /**
     * 双击屏幕唤醒
     */
    fun doubleTapToWake() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Log.d(TAG, "执行双击唤醒")
            val displayMetrics = resources.displayMetrics
            val centerX = displayMetrics.widthPixels / 2f
            val centerY = displayMetrics.heightPixels / 2f
            
            // 第一次点击
            val path1 = Path()
            path1.moveTo(centerX, centerY)
            
            val gesture1 = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path1, 0, 50))
                .build()
            
            dispatchGesture(gesture1, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "第一次点击完成")
                    
                    // 短暂延迟后第二次点击
                    handler.postDelayed({
                        val path2 = Path()
                        path2.moveTo(centerX, centerY)
                        
                        val gesture2 = GestureDescription.Builder()
                            .addStroke(GestureDescription.StrokeDescription(path2, 0, 50))
                            .build()
                        
                        dispatchGesture(gesture2, object : GestureResultCallback() {
                            override fun onCompleted(gestureDescription: GestureDescription?) {
                                Log.d(TAG, "双击唤醒完成")
                            }
                            override fun onCancelled(gestureDescription: GestureDescription?) {
                                Log.d(TAG, "第二次点击取消")
                            }
                        }, null)
                    }, 100) // 100ms间隔
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "第一次点击取消")
                }
            }, null)
        } else {
            Log.d(TAG, "系统版本过低，不支持手势操作")
        }
    }

    /**
     * 关闭指定包名的应用
     */
    fun closeApp(packageName: String) {
        Log.d(TAG, "关闭应用: $packageName")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 使用全局操作返回主屏幕
            performGlobalAction(GLOBAL_ACTION_HOME)
            Log.d(TAG, "已返回主屏幕")
            
            // 然后打开最近任务
            handler.postDelayed({
                performGlobalAction(GLOBAL_ACTION_RECENTS)
                Log.d(TAG, "已打开最近任务")
                
                // 然后清除所有任务（关闭所有应用）
                handler.postDelayed({
                    // 尝试找到并点击"全部清除"按钮
                    val rootNode = rootInActiveWindow
                    if (rootNode != null) {
                        val clearAllTexts = listOf("全部清除", "清除全部", "全部结束", "一键清理", "清理全部")
                        var clicked = false
                        for (text in clearAllTexts) {
                            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
                            if (nodes.isNotEmpty()) {
                                for (node in nodes) {
                                    if (node.isClickable) {
                                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                        clicked = true
                                        Log.d(TAG, "点击了: $text")
                                        break
                                    }
                                }
                                if (clicked) break
                            }
                        }
                        
                        if (!clicked) {
                            // 找不到清除按钮，滑动关闭当前应用
                            Log.d(TAG, "未找到清除按钮，尝试滑动关闭")
                            swipeUpToClose()
                        }
                    }
                }, 1500)
            }, 500)
        }
    }

    /**
     * 上滑关闭当前应用卡片
     */
    private fun swipeUpToClose() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val displayMetrics = resources.displayMetrics
            val centerX = displayMetrics.widthPixels / 2f
            val startY = displayMetrics.heightPixels / 2f
            val endY = displayMetrics.heightPixels * 0.1f
            
            val path = Path()
            path.moveTo(centerX, startY)
            path.lineTo(centerX, endY)
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 200))
                .build()
            
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "上滑关闭完成")
                    // 返回主屏幕
                    handler.postDelayed({
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }, 500)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "上滑关闭取消")
                }
            }, null)
        }
    }

    /**
     * 上滑解锁屏幕
     */
    private fun swipeUpToUnlock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Log.d(TAG, "执行上滑解锁")
            val displayMetrics = resources.displayMetrics
            val startX = displayMetrics.widthPixels / 2f
            val startY = displayMetrics.heightPixels * 0.85f  // 从屏幕底部开始
            val endY = displayMetrics.heightPixels * 0.2f     // 滑到屏幕上方
            
            val path = Path()
            path.moveTo(startX, startY)
            path.lineTo(startX, endY)
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
                .build()
            
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "上滑解锁手势完成")
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "上滑解锁手势取消")
                }
            }, null)
        } else {
            Log.d(TAG, "系统版本过低，不支持手势操作")
        }
    }


    /**
     * 完成工作
     */
    private fun finishPunch(success: Boolean, message: String) {
        isPunching = false
        currentFlow = null
        currentStepIndex = 0
        retryCount = 0
        
        handler.removeCallbacksAndMessages(null)
        
        val text = if (success) "✅ $message" else "❌ $message"
        Log.d(TAG, text)
        
        handler.post {
            Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
        
        // 发送广播通知主界面
        val intent = Intent("com.example.feishupunch.PUNCH_RESULT")
        intent.putExtra("success", success)
        intent.putExtra("message", message)
        sendBroadcast(intent)
        
        // 工作完成后返回桌面
        handler.postDelayed({
            goHome()
        }, 2000)
    }
    
    /**
     * 返回桌面
     */
    private fun goHome() {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(homeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "返回桌面失败: ${e.message}")
        }
    }
}

