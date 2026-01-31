package com.example.feishupunch

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.feishupunch.databinding.ActivityMainBinding
import com.example.feishupunch.model.Flow
import com.example.feishupunch.model.FlowStep
import com.example.feishupunch.model.StepType
import com.example.feishupunch.service.PunchAccessibilityService
import com.example.feishupunch.service.PunchForegroundService
import com.example.feishupunch.util.AlarmHelper
import com.example.feishupunch.util.CloseTime
import com.example.feishupunch.util.PreferenceHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferenceHelper
    private lateinit var alarmHelper: AlarmHelper
    private lateinit var currentFlow: Flow

    // 接收工作结果
    private val punchResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val success = intent?.getBooleanExtra("success", false) ?: false
            val message = intent?.getStringExtra("message") ?: ""
            
            runOnUiThread {
                updateStatus(if (success) "✅ $message" else "❌ $message")
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 允许在锁屏上显示，用于唤醒工作
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceHelper(this)
        alarmHelper = AlarmHelper(this)

        initViews()
        loadSettings()
        checkPermissions()
        
        // 自动开启定时（如果无障碍服务已启用）
        autoEnableSchedule()
        
        // 检查是否是闹钟触发的自动工作
        handleAutoPunchIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleAutoPunchIntent(it) }
    }
    
    /**
     * 处理自动工作意图
     */
    private fun handleAutoPunchIntent(intent: Intent) {
        if (intent.getBooleanExtra("auto_punch", false)) {
            android.util.Log.d("MainActivity", "收到自动工作请求")
            
            // 延迟2秒等待屏幕完全亮起后执行工作
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val service = PunchAccessibilityService.instance
                if (service != null) {
                    android.util.Log.d("MainActivity", "开始自动工作")
                    updateStatus("正在自动工作...")
                    service.startPunchProcess()
                } else {
                    android.util.Log.e("MainActivity", "无障碍服务未启动")
                    updateStatus("❌ 无障碍服务未启动")
                }
                
                // 工作后最小化窗口
                moveTaskToBack(true)
            }, 2000)
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        
        // 从设置页面返回后，检查是否可以自动开启定时
        autoEnableSchedule()
        
        // 注册广播接收器
        val filter = IntentFilter("com.example.feishupunch.PUNCH_RESULT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(punchResultReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(punchResultReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(punchResultReceiver)
        } catch (e: Exception) {
            // ignored
        }
    }

    private fun initViews() {
        // 开启无障碍服务按钮
        binding.btnAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        // 立即按钮
        binding.btnPunchNow.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            PunchAccessibilityService.instance?.startPunchProcess()
            updateStatus("正在执行工作...")
        }

        // 上班时间设置
        binding.layoutMorningTime.setOnClickListener {
            showTimePicker(true)
        }

        // 下班时间设置
        binding.layoutEveningTime.setOnClickListener {
            showTimePicker(false)
        }

        // 定时开关
        binding.switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            // 记录用户已手动操作过
            prefs.setUserHasToggled(true)
            
            if (isChecked) {
                if (!isAccessibilityServiceEnabled()) {
                    Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
                    binding.switchSchedule.isChecked = false
                    return@setOnCheckedChangeListener
                }
                enableSchedule()
            } else {
                disableSchedule()
            }
        }

        // 检查飞书是否安装
        binding.btnCheckFeishu.setOnClickListener {
            checkFeishuInstalled()
        }
        
        // 星期选择监听
        val dayChipListener = { _: android.widget.CompoundButton, _: Boolean ->
            saveDaySelection()
        }
        binding.chipMonday.setOnCheckedChangeListener(dayChipListener)
        binding.chipTuesday.setOnCheckedChangeListener(dayChipListener)
        binding.chipWednesday.setOnCheckedChangeListener(dayChipListener)
        binding.chipThursday.setOnCheckedChangeListener(dayChipListener)
        binding.chipFriday.setOnCheckedChangeListener(dayChipListener)
        binding.chipSaturday.setOnCheckedChangeListener(dayChipListener)
        binding.chipSunday.setOnCheckedChangeListener(dayChipListener)
        
        // 添加关闭时间按钮
        binding.btnAddCloseTime.setOnClickListener {
            showAddCloseTimePicker()
        }
        
        // 目标APP展开/折叠
        binding.layoutAppHeader.setOnClickListener {
            val isExpanded = binding.layoutAppContent.visibility == android.view.View.VISIBLE
            if (isExpanded) {
                binding.layoutAppContent.visibility = android.view.View.GONE
                binding.ivExpandArrow.rotation = 0f
            } else {
                binding.layoutAppContent.visibility = android.view.View.VISIBLE
                binding.ivExpandArrow.rotation = 180f
            }
        }
        
        // APP选择监听
        binding.radioGroupApp.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_feishu -> {
                    prefs.setTargetAppType(PreferenceHelper.APP_TYPE_FEISHU)
                    binding.layoutCustomPackage.visibility = android.view.View.GONE
                }
                R.id.radio_dingtalk -> {
                    prefs.setTargetAppType(PreferenceHelper.APP_TYPE_DINGTALK)
                    binding.layoutCustomPackage.visibility = android.view.View.GONE
                }
                R.id.radio_custom -> {
                    prefs.setTargetAppType(PreferenceHelper.APP_TYPE_CUSTOM)
                    binding.layoutCustomPackage.visibility = android.view.View.VISIBLE
                }
            }
            updateCurrentPackageDisplay()
        }
        
        // 自定义包名输入监听
        binding.etCustomPackage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                prefs.setCustomPackage(s?.toString() ?: "")
                updateCurrentPackageDisplay()
            }
        })
        
        // 选择APP按钮
        binding.btnSelectApp.setOnClickListener {
            showAppListDialog()
        }
        
        // 流程展开/折叠
        binding.layoutFlowHeader.setOnClickListener {
            val isExpanded = binding.layoutFlowContent.visibility == View.VISIBLE
            if (isExpanded) {
                binding.layoutFlowContent.visibility = View.GONE
                binding.ivFlowExpandArrow.rotation = 0f
            } else {
                binding.layoutFlowContent.visibility = View.VISIBLE
                binding.ivFlowExpandArrow.rotation = 180f
            }
        }
        
        // 添加步骤按钮
        binding.btnAddStep.setOnClickListener {
            showAddStepDialog()
        }
    }
    
    /**
     * APP信息数据类
     */
    data class AppInfo(
        val name: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable?
    )
    
    /**
     * 显示已安装APP列表对话框
     */
    private fun showAppListDialog() {
        // 显示加载提示
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("加载中...")
            .setMessage("正在获取已安装的APP列表")
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        // 在后台线程获取APP列表
        Thread {
            val apps = getInstalledApps()
            
            runOnUiThread {
                progressDialog.dismiss()
                
                if (apps.isEmpty()) {
                    Toast.makeText(this, "未找到已安装的APP", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                
                // 创建APP名称数组
                val appNames = apps.map { "${it.name}\n${it.packageName}" }.toTypedArray()
                
                AlertDialog.Builder(this)
                    .setTitle("选择目标APP (${apps.size}个)")
                    .setItems(appNames) { _, which ->
                        val selectedApp = apps[which]
                        binding.etCustomPackage.setText(selectedApp.packageName)
                        Toast.makeText(this, "已选择: ${selectedApp.name}", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }.start()
    }
    
    /**
     * 获取已安装的APP列表（排除系统APP，按名称排序）
     */
    private fun getInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val apps = mutableListOf<AppInfo>()
        
        try {
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            
            for (appInfo in packages) {
                // 排除系统APP（可选：保留用户可能需要的系统APP）
                val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                
                // 只保留非系统APP或已更新的系统APP（如微信、支付宝等预装但用户更新过的APP）
                if (!isSystemApp || isUpdatedSystemApp) {
                    val name = pm.getApplicationLabel(appInfo).toString()
                    val icon = try {
                        pm.getApplicationIcon(appInfo)
                    } catch (e: Exception) {
                        null
                    }
                    apps.add(AppInfo(name, appInfo.packageName, icon))
                }
            }
            
            // 按名称排序
            apps.sortBy { it.name }
            
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "获取APP列表失败: ${e.message}")
        }
        
        return apps
    }

    private fun loadSettings() {
        // 加载保存的时间设置（范围）
        updateMorningTimeDisplay()
        updateEveningTimeDisplay()
        
        // 加载关闭飞书时间
        updateCloseTimeDisplay()
        
        // 加载星期选择
        loadDaySelection()
        
        // 加载目标APP选择
        loadAppSelection()
        
        // 加载流程
        loadFlow()
        
        // 加载开关状态
        binding.switchSchedule.isChecked = prefs.isScheduleEnabled()
    }
    
    /**
     * 加载目标APP选择
     */
    private fun loadAppSelection() {
        when (prefs.getTargetAppType()) {
            PreferenceHelper.APP_TYPE_FEISHU -> {
                binding.radioFeishu.isChecked = true
                binding.layoutCustomPackage.visibility = android.view.View.GONE
            }
            PreferenceHelper.APP_TYPE_DINGTALK -> {
                binding.radioDingtalk.isChecked = true
                binding.layoutCustomPackage.visibility = android.view.View.GONE
            }
            PreferenceHelper.APP_TYPE_CUSTOM -> {
                binding.radioCustom.isChecked = true
                binding.layoutCustomPackage.visibility = android.view.View.VISIBLE
                binding.etCustomPackage.setText(prefs.getCustomPackage())
            }
        }
        updateCurrentPackageDisplay()
    }
    
    /**
     * 更新当前包名显示
     */
    private fun updateCurrentPackageDisplay() {
        binding.tvCurrentPackage.text = prefs.getTargetPackage()
    }
    
    /**
     * 加载星期选择状态
     */
    private fun loadDaySelection() {
        val selectedDays = prefs.getSelectedDays()
        // Calendar: 1=周日, 2=周一, 3=周二, 4=周三, 5=周四, 6=周五, 7=周六
        binding.chipSunday.isChecked = selectedDays.contains(1)
        binding.chipMonday.isChecked = selectedDays.contains(2)
        binding.chipTuesday.isChecked = selectedDays.contains(3)
        binding.chipWednesday.isChecked = selectedDays.contains(4)
        binding.chipThursday.isChecked = selectedDays.contains(5)
        binding.chipFriday.isChecked = selectedDays.contains(6)
        binding.chipSaturday.isChecked = selectedDays.contains(7)
    }
    
    /**
     * 保存星期选择
     */
    private fun saveDaySelection() {
        val selectedDays = mutableSetOf<Int>()
        // Calendar: 1=周日, 2=周一, 3=周二, 4=周三, 5=周四, 6=周五, 7=周六
        if (binding.chipSunday.isChecked) selectedDays.add(1)
        if (binding.chipMonday.isChecked) selectedDays.add(2)
        if (binding.chipTuesday.isChecked) selectedDays.add(3)
        if (binding.chipWednesday.isChecked) selectedDays.add(4)
        if (binding.chipThursday.isChecked) selectedDays.add(5)
        if (binding.chipFriday.isChecked) selectedDays.add(6)
        if (binding.chipSaturday.isChecked) selectedDays.add(7)
        
        prefs.setSelectedDays(selectedDays)
        updateDaySelectionStatus()
    }
    
    /**
     * 更新星期选择显示状态
     */
    private fun updateDaySelectionStatus() {
        val selectedDays = prefs.getSelectedDays()
        val dayNames = mutableListOf<String>()
        if (selectedDays.contains(2)) dayNames.add("周一")
        if (selectedDays.contains(3)) dayNames.add("周二")
        if (selectedDays.contains(4)) dayNames.add("周三")
        if (selectedDays.contains(5)) dayNames.add("周四")
        if (selectedDays.contains(6)) dayNames.add("周五")
        if (selectedDays.contains(7)) dayNames.add("周六")
        if (selectedDays.contains(1)) dayNames.add("周日")
        
        val statusText = if (dayNames.isEmpty()) {
            "未选择执行日期"
        } else if (dayNames.size == 7) {
            "每天执行"
        } else if (selectedDays.containsAll(listOf(2, 3, 4, 5, 6)) && selectedDays.size == 5) {
            "工作日执行"
        } else {
            dayNames.joinToString("、") + " 执行"
        }
        updateStatus(statusText)
    }

    /**
     * 自动开启定时功能（仅首次）
     */
    private fun autoEnableSchedule() {
        // 如果用户手动操作过，不再自动开启
        if (prefs.hasUserToggled()) {
            return
        }
        
        // 如果已经开启了，不重复操作
        if (prefs.isScheduleEnabled()) {
            return
        }
        
        // 如果无障碍服务已开启，自动开启定时
        if (isAccessibilityServiceEnabled()) {
            binding.switchSchedule.isChecked = true
            // enableSchedule() 会通过 OnCheckedChangeListener 自动调用
        }
    }
    
    private fun updateMorningTimeDisplay() {
        val startTime = String.format("%02d:%02d", prefs.getMorningStartHour(), prefs.getMorningStartMinute())
        val endTime = String.format("%02d:%02d", prefs.getMorningEndHour(), prefs.getMorningEndMinute())
        binding.tvMorningTime.text = "$startTime-$endTime"
    }
    
    private fun updateEveningTimeDisplay() {
        val startTime = String.format("%02d:%02d", prefs.getEveningStartHour(), prefs.getEveningStartMinute())
        val endTime = String.format("%02d:%02d", prefs.getEveningEndHour(), prefs.getEveningEndMinute())
        binding.tvEveningTime.text = "$startTime-$endTime"
    }
    
    private fun updateCloseTimeDisplay() {
        val container = binding.containerCloseTimes
        container.removeAllViews()
        
        val times = prefs.getCloseTimes()
        for (time in times) {
            addCloseTimeItemView(time)
        }
    }
    
    private fun addCloseTimeItemView(time: CloseTime) {
        val container = binding.containerCloseTimes
        
        val itemLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (44 * resources.displayMetrics.density).toInt()
            )
            setPadding(
                (8 * resources.displayMetrics.density).toInt(), 0,
                (8 * resources.displayMetrics.density).toInt(), 0
            )
            setBackgroundResource(android.R.drawable.list_selector_background)
        }
        
        val timeText = android.widget.TextView(this).apply {
            text = time.toDisplayString()
            textSize = 15f
            setTextColor(android.graphics.Color.parseColor("#E91E63"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        
        val deleteBtn = android.widget.TextView(this).apply {
            text = "删除"
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#F44336"))
            setPadding(
                (12 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt()
            )
            setOnClickListener {
                prefs.removeCloseTime(time)
                updateCloseTimeDisplay()
                if (prefs.isScheduleEnabled()) {
                    updateAlarms()
                }
                Toast.makeText(this@MainActivity, "已删除 ${time.toDisplayString()}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 点击时间可以修改
        itemLayout.setOnClickListener {
            TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                val newTime = CloseTime(selectedHour, selectedMinute)
                prefs.updateCloseTime(time, newTime)
                updateCloseTimeDisplay()
                if (prefs.isScheduleEnabled()) {
                    updateAlarms()
                }
                Toast.makeText(this, "时间已更新", Toast.LENGTH_SHORT).show()
            }, time.hour, time.minute, true).apply {
                setTitle("修改关闭时间")
            }.show()
        }
        
        itemLayout.addView(timeText)
        itemLayout.addView(deleteBtn)
        container.addView(itemLayout)
    }
    
    private fun showAddCloseTimePicker() {
        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val newTime = CloseTime(selectedHour, selectedMinute)
            prefs.addCloseTime(newTime)
            updateCloseTimeDisplay()
            
            if (prefs.isScheduleEnabled()) {
                updateAlarms()
            }
            
            Toast.makeText(this, "已添加 ${newTime.toDisplayString()}", Toast.LENGTH_SHORT).show()
        }, 12, 0, true).apply {
            setTitle("添加关闭时间")
        }.show()
    }

    private fun checkPermissions() {
        // 检查通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }

        // 检查精确闹钟权限 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("需要权限")
                    .setMessage("为确保定时准确，需要开启精确闹钟权限")
                    .setPositiveButton("去设置") { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    private fun updateServiceStatus() {
        val isEnabled = isAccessibilityServiceEnabled()
        
        binding.tvServiceStatus.text = if (isEnabled) "已开启" else "未开启"
        binding.tvServiceStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (isEnabled) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            )
        )
        
        binding.btnAccessibility.text = if (isEnabled) "无障碍服务已开启" else "开启无障碍服务"
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == packageName) {
                return true
            }
        }
        return false
    }

    private fun openAccessibilitySettings() {
        AlertDialog.Builder(this)
            .setTitle("开启无障碍服务")
            .setMessage("请在设置中找到「乐逍遥」并开启无障碍服务权限")
            .setPositiveButton("去设置") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showTimePicker(isMorning: Boolean) {
        // 先选择开始时间
        val startHour = if (isMorning) prefs.getMorningStartHour() else prefs.getEveningStartHour()
        val startMinute = if (isMorning) prefs.getMorningStartMinute() else prefs.getEveningStartMinute()

        TimePickerDialog(this, { _, h1, m1 ->
            // 再选择结束时间
            val endHour = if (isMorning) prefs.getMorningEndHour() else prefs.getEveningEndHour()
            val endMinute = if (isMorning) prefs.getMorningEndMinute() else prefs.getEveningEndMinute()
            
            TimePickerDialog(this, { _, h2, m2 ->
                if (isMorning) {
                    prefs.setMorningStartTime(h1, m1)
                    prefs.setMorningEndTime(h2, m2)
                    updateMorningTimeDisplay()
                } else {
                    prefs.setEveningStartTime(h1, m1)
                    prefs.setEveningEndTime(h2, m2)
                    updateEveningTimeDisplay()
                }
                
                // 如果定时已开启，更新闹钟
                if (prefs.isScheduleEnabled()) {
                    updateAlarms()
                }
                
                Toast.makeText(this, "时间范围已设置，将在范围内随机触发", Toast.LENGTH_SHORT).show()
            }, endHour, endMinute, true).apply {
                setTitle("选择结束时间")
            }.show()
        }, startHour, startMinute, true).apply {
            setTitle("选择开始时间")
        }.show()
    }
    
    private fun updateAlarms() {
        alarmHelper.setMorningAlarm(
            prefs.getMorningStartHour(), prefs.getMorningStartMinute(),
            prefs.getMorningEndHour(), prefs.getMorningEndMinute()
        )
        alarmHelper.setEveningAlarm(
            prefs.getEveningStartHour(), prefs.getEveningStartMinute(),
            prefs.getEveningEndHour(), prefs.getEveningEndMinute()
        )
        // 关闭飞书（使用配置的时间列表）
        alarmHelper.setCloseAppAlarms(prefs.getCloseTimes())
    }

    private fun enableSchedule() {
        // Android 12+ 检查精确闹钟权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("需要闹钟权限")
                    .setMessage("定时功能需要精确闹钟权限才能准时触发，请授予权限")
                    .setPositiveButton("去设置") { _, _ ->
                        val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        startActivity(intent)
                    }
                    .setNegativeButton("取消") { _, _ ->
                        binding.switchSchedule.isChecked = false
                    }
                    .show()
                return
            }
        }
        
        prefs.setScheduleEnabled(true)
        
        // 设置闹钟（时间范围随机）
        updateAlarms()
        
        // 启动前台服务
        val intent = Intent(this, PunchForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        val morningStart = String.format("%02d:%02d", prefs.getMorningStartHour(), prefs.getMorningStartMinute())
        val morningEnd = String.format("%02d:%02d", prefs.getMorningEndHour(), prefs.getMorningEndMinute())
        val eveningStart = String.format("%02d:%02d", prefs.getEveningStartHour(), prefs.getEveningStartMinute())
        val eveningEnd = String.format("%02d:%02d", prefs.getEveningEndHour(), prefs.getEveningEndMinute())
        Toast.makeText(this, "定时已开启(随机触发)\n$morningStart-$morningEnd\n$eveningStart-$eveningEnd", Toast.LENGTH_LONG).show()
        updateStatus("定时已开启，将在时间范围内随机触发")
    }

    private fun disableSchedule() {
        prefs.setScheduleEnabled(false)
        
        // 取消闹钟
        alarmHelper.cancelAllAlarms()
        
        // 停止前台服务
        stopService(Intent(this, PunchForegroundService::class.java))
        
        Toast.makeText(this, "定时已关闭", Toast.LENGTH_SHORT).show()
        updateStatus("定时已关闭")
    }

    private fun updateStatus(message: String) {
        binding.tvStatus.text = message
    }

    private fun checkFeishuInstalled() {
        val targetPackage = prefs.getTargetPackage()
        val targetName = prefs.getTargetAppName()
        
        if (targetPackage.isEmpty()) {
            Toast.makeText(this, "❌ 请先设置目标APP包名", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            packageManager.getPackageInfo(targetPackage, 0)
            Toast.makeText(this, "✅ $targetName 已安装 ($targetPackage)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            val downloadUrl = when (prefs.getTargetAppType()) {
                PreferenceHelper.APP_TYPE_FEISHU -> "https://www.feishu.cn/download"
                PreferenceHelper.APP_TYPE_DINGTALK -> "https://www.dingtalk.com/download"
                else -> null
            }
            
            val builder = AlertDialog.Builder(this)
                .setTitle("未检测到$targetName")
                .setMessage("包名: $targetPackage\n\n请先安装该APP")
                .setNegativeButton("取消", null)
            
            if (downloadUrl != null) {
                builder.setPositiveButton("去下载") { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(downloadUrl)
                    }
                    startActivity(intent)
                }
            }
            
            builder.show()
        }
    }
    
    // ==================== 流程管理 ====================
    
    /**
     * 加载流程
     */
    private fun loadFlow() {
        currentFlow = prefs.getFlow()
        updateFlowDisplay()
    }
    
    /**
     * 更新流程显示
     */
    private fun updateFlowDisplay() {
        binding.tvFlowStepCount.text = "${currentFlow.steps.size} 步"
        
        binding.layoutFlowSteps.removeAllViews()
        currentFlow.steps.forEachIndexed { index, step ->
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_flow_step, binding.layoutFlowSteps, false)
            
            itemView.findViewById<TextView>(R.id.tv_step_number).text = "${index + 1}"
            itemView.findViewById<TextView>(R.id.tv_step_desc).text = step.getDescription()
            
            itemView.findViewById<ImageButton>(R.id.btn_edit_step).setOnClickListener {
                showEditStepDialog(index, step)
            }
            
            itemView.findViewById<ImageButton>(R.id.btn_delete_step).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("删除步骤")
                    .setMessage("确定删除「${step.getDescription()}」?")
                    .setPositiveButton("删除") { _, _ ->
                        currentFlow.steps.removeAt(index)
                        prefs.saveFlow(currentFlow)
                        updateFlowDisplay()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = (8 * resources.displayMetrics.density).toInt()
            itemView.layoutParams = params
            
            binding.layoutFlowSteps.addView(itemView)
        }
    }
    
    /**
     * 显示添加步骤对话框
     */
    private fun showAddStepDialog() {
        val stepTypes = StepType.values()
        val typeNames = stepTypes.map { it.displayName }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("选择步骤类型")
            .setItems(typeNames) { _, which ->
                val selectedType = stepTypes[which]
                showStepConfigDialog(selectedType) { newStep ->
                    currentFlow.steps.add(newStep)
                    prefs.saveFlow(currentFlow)
                    updateFlowDisplay()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示编辑步骤对话框
     */
    private fun showEditStepDialog(index: Int, step: FlowStep) {
        val stepTypes = StepType.values()
        val typeNames = stepTypes.map { it.displayName }.toTypedArray()
        val currentIndex = stepTypes.indexOf(step.type)
        
        AlertDialog.Builder(this)
            .setTitle("编辑步骤")
            .setSingleChoiceItems(typeNames, currentIndex) { dialog, which ->
                dialog.dismiss()
                val selectedType = stepTypes[which]
                showStepConfigDialog(selectedType, step) { updatedStep ->
                    currentFlow.steps[index] = updatedStep
                    prefs.saveFlow(currentFlow)
                    updateFlowDisplay()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示步骤配置对话框
     */
    private fun showStepConfigDialog(type: StepType, existingStep: FlowStep? = null, onConfirm: (FlowStep) -> Unit) {
        when (type) {
            StepType.OPEN_APP, StepType.BACK, StepType.HOME, 
            StepType.RECENT_APPS, StepType.NOTIFICATIONS -> {
                // 这些类型不需要额外配置
                onConfirm(FlowStep(type = type))
            }
            StepType.CLICK_XY -> {
                showClickXYDialog(existingStep, onConfirm)
            }
            StepType.CLICK_TEXT -> {
                showClickTextDialog(existingStep, onConfirm)
            }
            StepType.LONG_PRESS -> {
                showLongPressDialog(existingStep, onConfirm)
            }
            StepType.DOUBLE_CLICK -> {
                showDoubleClickDialog(existingStep, onConfirm)
            }
            StepType.SWIPE -> {
                showSwipeDialog(existingStep, onConfirm)
            }
            StepType.DELAY -> {
                showDelayDialog(existingStep, onConfirm)
            }
        }
    }
    
    /**
     * 点击坐标配置对话框
     */
    private fun showClickXYDialog(existingStep: FlowStep?, onConfirm: (FlowStep) -> Unit) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
        }
        
        val etX = EditText(this).apply {
            hint = "X 坐标"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existingStep?.x?.toString() ?: "")
        }
        val etY = EditText(this).apply {
            hint = "Y 坐标"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existingStep?.y?.toString() ?: "")
        }
        
        layout.addView(TextView(this).apply { text = "X 坐标:" })
        layout.addView(etX)
        layout.addView(TextView(this).apply { 
            text = "Y 坐标:"
            setPadding(0, 16, 0, 0)
        })
        layout.addView(etY)
        
        // 采集坐标按钮
        val btnCapture = com.google.android.material.button.MaterialButton(this).apply {
            text = "📍 采集坐标"
            setOnClickListener {
                startCoordinateCapture { x, y ->
                    etX.setText(x.toString())
                    etY.setText(y.toString())
                }
            }
        }
        layout.addView(btnCapture)
        
        AlertDialog.Builder(this)
            .setTitle("设置点击坐标")
            .setView(layout)
            .setPositiveButton("确定") { _, _ ->
                val x = etX.text.toString().toIntOrNull() ?: 0
                val y = etY.text.toString().toIntOrNull() ?: 0
                onConfirm(FlowStep(type = StepType.CLICK_XY, x = x, y = y))
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 点击文本配置对话框
     */
    private fun showClickTextDialog(existingStep: FlowStep?, onConfirm: (FlowStep) -> Unit) {
        val etText = EditText(this).apply {
            hint = "输入要点击的文本"
            setText(existingStep?.text ?: "")
        }
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
            addView(etText)
        }
        
        AlertDialog.Builder(this)
            .setTitle("设置点击文本")
            .setView(layout)
            .setPositiveButton("确定") { _, _ ->
                val text = etText.text.toString()
                if (text.isNotBlank()) {
                    onConfirm(FlowStep(type = StepType.CLICK_TEXT, text = text))
                } else {
                    Toast.makeText(this, "请输入文本", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 等待时间配置对话框
     */
    private fun showDelayDialog(existingStep: FlowStep?, onConfirm: (FlowStep) -> Unit) {
        val etDelay = EditText(this).apply {
            hint = "等待时间（毫秒）"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existingStep?.delay?.toString() ?: "1000")
        }
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
            addView(etDelay)
        }
        
        AlertDialog.Builder(this)
            .setTitle("设置等待时间")
            .setView(layout)
            .setPositiveButton("确定") { _, _ ->
                val delay = etDelay.text.toString().toLongOrNull() ?: 1000
                onConfirm(FlowStep(type = StepType.DELAY, delay = delay))
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 长按配置对话框
     */
    private fun showLongPressDialog(existingStep: FlowStep?, onConfirm: (FlowStep) -> Unit) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
        }
        
        val etX = EditText(this).apply {
            hint = "X 坐标"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existingStep?.x?.toString() ?: "")
        }
        val etY = EditText(this).apply {
            hint = "Y 坐标"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existingStep?.y?.toString() ?: "")
        }
        val etDuration = EditText(this).apply {
            hint = "长按时间（毫秒）"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existingStep?.duration?.toString() ?: "500")
        }
        
        layout.addView(TextView(this).apply { text = "X 坐标:" })
        layout.addView(etX)
        layout.addView(TextView(this).apply { text = "Y 坐标:"; setPadding(0, 16, 0, 0) })
        layout.addView(etY)
        layout.addView(TextView(this).apply { text = "长按时间(ms):"; setPadding(0, 16, 0, 0) })
        layout.addView(etDuration)
        
        // 采集坐标按钮
        val btnCapture = com.google.android.material.button.MaterialButton(this).apply {
            text = "📍 采集坐标"
            setOnClickListener {
                startCoordinateCapture { x, y ->
                    etX.setText(x.toString())
                    etY.setText(y.toString())
                }
            }
        }
        layout.addView(btnCapture)
        
        AlertDialog.Builder(this)
            .setTitle("设置长按")
            .setView(layout)
            .setPositiveButton("确定") { _, _ ->
                val x = etX.text.toString().toIntOrNull() ?: 0
                val y = etY.text.toString().toIntOrNull() ?: 0
                val duration = etDuration.text.toString().toLongOrNull() ?: 500
                onConfirm(FlowStep(type = StepType.LONG_PRESS, x = x, y = y, duration = duration))
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 双击配置对话框
     */
    private fun showDoubleClickDialog(existingStep: FlowStep?, onConfirm: (FlowStep) -> Unit) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
        }
        
        val etX = EditText(this).apply {
            hint = "X 坐标"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existingStep?.x?.toString() ?: "")
        }
        val etY = EditText(this).apply {
            hint = "Y 坐标"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existingStep?.y?.toString() ?: "")
        }
        
        layout.addView(TextView(this).apply { text = "X 坐标:" })
        layout.addView(etX)
        layout.addView(TextView(this).apply { text = "Y 坐标:"; setPadding(0, 16, 0, 0) })
        layout.addView(etY)
        
        // 采集坐标按钮
        val btnCapture = com.google.android.material.button.MaterialButton(this).apply {
            text = "📍 采集坐标"
            setOnClickListener {
                startCoordinateCapture { x, y ->
                    etX.setText(x.toString())
                    etY.setText(y.toString())
                }
            }
        }
        layout.addView(btnCapture)
        
        AlertDialog.Builder(this)
            .setTitle("设置双击")
            .setView(layout)
            .setPositiveButton("确定") { _, _ ->
                val x = etX.text.toString().toIntOrNull() ?: 0
                val y = etY.text.toString().toIntOrNull() ?: 0
                onConfirm(FlowStep(type = StepType.DOUBLE_CLICK, x = x, y = y))
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 滑动配置对话框
     */
    private fun showSwipeDialog(existingStep: FlowStep?, onConfirm: (FlowStep) -> Unit) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
        }
        
        val etX1 = EditText(this).apply {
            hint = "起点 X"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existingStep?.x?.toString() ?: "")
        }
        val etY1 = EditText(this).apply {
            hint = "起点 Y"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existingStep?.y?.toString() ?: "")
        }
        val etX2 = EditText(this).apply {
            hint = "终点 X"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existingStep?.x2?.toString() ?: "")
        }
        val etY2 = EditText(this).apply {
            hint = "终点 Y"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existingStep?.y2?.toString() ?: "")
        }
        val etDuration = EditText(this).apply {
            hint = "滑动时间（毫秒）"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existingStep?.duration?.toString() ?: "300")
        }
        
        layout.addView(TextView(this).apply { text = "起点 X:" })
        layout.addView(etX1)
        layout.addView(TextView(this).apply { text = "起点 Y:"; setPadding(0, 8, 0, 0) })
        layout.addView(etY1)
        layout.addView(TextView(this).apply { text = "终点 X:"; setPadding(0, 16, 0, 0) })
        layout.addView(etX2)
        layout.addView(TextView(this).apply { text = "终点 Y:"; setPadding(0, 8, 0, 0) })
        layout.addView(etY2)
        layout.addView(TextView(this).apply { text = "滑动时间(ms):"; setPadding(0, 16, 0, 0) })
        layout.addView(etDuration)
        
        // 采集滑动坐标按钮
        val btnCapture = com.google.android.material.button.MaterialButton(this).apply {
            text = "📍 采集滑动轨迹"
            setOnClickListener {
                startSwipeCapture { x1, y1, x2, y2 ->
                    etX1.setText(x1.toString())
                    etY1.setText(y1.toString())
                    etX2.setText(x2.toString())
                    etY2.setText(y2.toString())
                }
            }
        }
        layout.addView(btnCapture)
        
        AlertDialog.Builder(this)
            .setTitle("设置滑动")
            .setView(layout)
            .setPositiveButton("确定") { _, _ ->
                val x1 = etX1.text.toString().toIntOrNull() ?: 0
                val y1 = etY1.text.toString().toIntOrNull() ?: 0
                val x2 = etX2.text.toString().toIntOrNull() ?: 0
                val y2 = etY2.text.toString().toIntOrNull() ?: 0
                val duration = etDuration.text.toString().toLongOrNull() ?: 300
                onConfirm(FlowStep(type = StepType.SWIPE, x = x1, y = y1, x2 = x2, y2 = y2, duration = duration))
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    // ==================== 坐标采集 ====================
    
    /**
     * 检查悬浮窗权限
     */
    private fun checkOverlayPermission(): Boolean {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("需要悬浮窗权限")
                .setMessage("坐标采集功能需要悬浮窗权限，点击确定前往设置")
                .setPositiveButton("确定") { _, _ ->
                    val intent = Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("取消", null)
                .show()
            return false
        }
        return true
    }
    
    /**
     * 启动坐标采集悬浮窗
     */
    private fun startCoordinateCapture(onCaptured: (Int, Int) -> Unit) {
        if (!checkOverlayPermission()) return
        
        com.example.feishupunch.service.FloatingWindowService.onCoordinateSelected = onCaptured
        val intent = Intent(this, com.example.feishupunch.service.FloatingWindowService::class.java).apply {
            action = "START_SINGLE"
        }
        startService(intent)
        
        Toast.makeText(this, "悬浮窗已启动，点击屏幕采集坐标", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 启动滑动轨迹采集
     */
    private fun startSwipeCapture(onCaptured: (Int, Int, Int, Int) -> Unit) {
        if (!checkOverlayPermission()) return
        
        com.example.feishupunch.service.FloatingWindowService.onSwipeSelected = onCaptured
        val intent = Intent(this, com.example.feishupunch.service.FloatingWindowService::class.java).apply {
            action = "START_SWIPE"
        }
        startService(intent)
        
        Toast.makeText(this, "悬浮窗已启动，先点起点再点终点", Toast.LENGTH_SHORT).show()
    }
}

