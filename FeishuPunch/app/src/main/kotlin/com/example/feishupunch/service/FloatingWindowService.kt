package com.example.feishupunch.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.example.feishupunch.R

/**
 * 坐标采集悬浮窗服务
 */
class FloatingWindowService : Service() {

    companion object {
        var instance: FloatingWindowService? = null
            private set
        
        // 坐标回调
        var onCoordinateSelected: ((Int, Int) -> Unit)? = null
        
        // 滑动回调 (起点和终点)
        var onSwipeSelected: ((Int, Int, Int, Int) -> Unit)? = null
        
        // 是否正在运行
        fun isRunning() = instance != null
    }
    
    enum class CaptureMode {
        IDLE,           // 空闲（悬浮球模式）
        SINGLE_POINT,   // 单点采集
        SWIPE           // 滑动采集
    }

    private lateinit var windowManager: WindowManager
    
    // 悬浮球
    private var floatingBall: View? = null
    private var floatingBallParams: WindowManager.LayoutParams? = null
    
    // 采集遮罩
    private var captureOverlay: View? = null
    
    // 当前模式
    private var currentMode = CaptureMode.IDLE
    
    // 滑动起点
    private var swipeStartX = 0
    private var swipeStartY = 0
    private var isSwipeStartSet = false
    
    // 最近采集的坐标（用于显示）
    private var lastCapturedCoord = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showFloatingBall()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_SINGLE" -> {
                currentMode = CaptureMode.SINGLE_POINT
                showCaptureOverlay()
            }
            "START_SWIPE" -> {
                currentMode = CaptureMode.SWIPE
                isSwipeStartSet = false
                showCaptureOverlay()
            }
            "STOP" -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    /**
     * 显示悬浮球
     */
    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun showFloatingBall() {
        if (floatingBall != null) return
        
        floatingBall = LayoutInflater.from(this).inflate(R.layout.floating_ball, null)
        
        floatingBallParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }
        
        val tvCoord = floatingBall!!.findViewById<TextView>(R.id.tv_last_coord)
        val btnSingle = floatingBall!!.findViewById<View>(R.id.btn_capture_single)
        val btnSwipe = floatingBall!!.findViewById<View>(R.id.btn_capture_swipe)
        val btnClose = floatingBall!!.findViewById<View>(R.id.btn_close_float)
        val dragHandle = floatingBall!!.findViewById<View>(R.id.drag_handle)
        
        // 单点采集
        btnSingle.setOnClickListener {
            currentMode = CaptureMode.SINGLE_POINT
            showCaptureOverlay()
        }
        
        // 滑动采集
        btnSwipe.setOnClickListener {
            currentMode = CaptureMode.SWIPE
            isSwipeStartSet = false
            showCaptureOverlay()
        }
        
        // 关闭
        btnClose.setOnClickListener {
            stopSelf()
        }
        
        // 拖动
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        
        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = floatingBallParams!!.x
                    initialY = floatingBallParams!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    floatingBallParams!!.x = initialX + (event.rawX - initialTouchX).toInt()
                    floatingBallParams!!.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingBall, floatingBallParams)
                    true
                }
                else -> false
            }
        }
        
        windowManager.addView(floatingBall, floatingBallParams)
    }

    /**
     * 显示坐标采集遮罩层
     */
    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun showCaptureOverlay() {
        if (captureOverlay != null) return
        
        // 隐藏悬浮球
        floatingBall?.visibility = View.GONE
        
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        
        captureOverlay = LayoutInflater.from(this).inflate(R.layout.overlay_coordinate_capture, null)
        
        val tvHint = captureOverlay!!.findViewById<TextView>(R.id.tv_capture_hint)
        val tvCoord = captureOverlay!!.findViewById<TextView>(R.id.tv_coordinate)
        val btnCancel = captureOverlay!!.findViewById<View>(R.id.btn_cancel_capture)
        
        // 更新提示文本
        updateHintText(tvHint)
        
        // 取消按钮
        btnCancel.setOnClickListener {
            hideCaptureOverlay()
        }
        
        // 触摸事件处理
        captureOverlay!!.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                
                when (currentMode) {
                    CaptureMode.SINGLE_POINT -> {
                        lastCapturedCoord = "($x, $y)"
                        tvCoord.text = lastCapturedCoord
                        tvCoord.visibility = View.VISIBLE
                        
                        // 回调坐标
                        onCoordinateSelected?.invoke(x, y)
                        
                        // 显示 Toast
                        Toast.makeText(this, "已采集: $lastCapturedCoord", Toast.LENGTH_SHORT).show()
                        
                        // 返回悬浮球模式
                        hideCaptureOverlay()
                    }
                    CaptureMode.SWIPE -> {
                        if (!isSwipeStartSet) {
                            // 设置起点
                            swipeStartX = x
                            swipeStartY = y
                            isSwipeStartSet = true
                            tvHint.text = "已设置起点: ($x, $y)\n\n点击屏幕设置终点"
                            tvCoord.text = "起点: ($x, $y)"
                            tvCoord.visibility = View.VISIBLE
                        } else {
                            // 设置终点
                            lastCapturedCoord = "($swipeStartX,$swipeStartY)→($x,$y)"
                            tvCoord.text = lastCapturedCoord
                            
                            // 回调滑动坐标
                            onSwipeSelected?.invoke(swipeStartX, swipeStartY, x, y)
                            
                            // 显示 Toast
                            Toast.makeText(this, "已采集滑动: $lastCapturedCoord", Toast.LENGTH_SHORT).show()
                            
                            // 返回悬浮球模式
                            hideCaptureOverlay()
                        }
                    }
                    else -> {}
                }
            }
            true
        }
        
        windowManager.addView(captureOverlay, layoutParams)
    }
    
    private fun updateHintText(tvHint: TextView) {
        tvHint.text = when (currentMode) {
            CaptureMode.SINGLE_POINT -> "📍 点击屏幕采集坐标"
            CaptureMode.SWIPE -> "📍 点击屏幕设置起点"
            else -> ""
        }
    }

    /**
     * 隐藏坐标采集遮罩
     */
    private fun hideCaptureOverlay() {
        captureOverlay?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            captureOverlay = null
        }
        isSwipeStartSet = false
        currentMode = CaptureMode.IDLE
        
        // 显示悬浮球并更新坐标显示
        floatingBall?.let {
            it.visibility = View.VISIBLE
            if (lastCapturedCoord.isNotEmpty()) {
                it.findViewById<TextView>(R.id.tv_last_coord)?.text = lastCapturedCoord
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        captureOverlay?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        floatingBall?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        
        captureOverlay = null
        floatingBall = null
        instance = null
        onCoordinateSelected = null
        onSwipeSelected = null
    }
}
