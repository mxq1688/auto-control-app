"auto";
// ============================================
// 定时自动打卡脚本
// 可设置上班和下班打卡时间
// ============================================

// ============ 配置 ============
const SCHEDULE_CONFIG = {
    // 上班打卡时间 (24小时制)
    morningCheckIn: {
        hour: 8,
        minute: 50,
    },
    
    // 下班打卡时间 (24小时制)
    eveningCheckIn: {
        hour: 18,
        minute: 5,
    },
    
    // 随机延迟范围（分钟），避免每天固定时间打卡
    randomDelayMinutes: 5,
    
    // APP配置
    app: {
        packageName: "com.alibaba.android.rimet",
        appName: "钉钉",
    }
};

// ============ 主程序 ============
function main() {
    console.show();
    log("===== 定时打卡服务启动 =====");
    log("上班打卡时间: " + formatTime(SCHEDULE_CONFIG.morningCheckIn));
    log("下班打卡时间: " + formatTime(SCHEDULE_CONFIG.eveningCheckIn));
    log("随机延迟: 0-" + SCHEDULE_CONFIG.randomDelayMinutes + "分钟");
    log("============================");
    
    // 保持脚本运行
    setInterval(function() {
        checkAndExecute();
    }, 60000); // 每分钟检查一次
    
    // 首次检查
    checkAndExecute();
}

function checkAndExecute() {
    let now = new Date();
    let currentHour = now.getHours();
    let currentMinute = now.getMinutes();
    
    // 检查是否到达上班打卡时间
    if (isTargetTime(currentHour, currentMinute, SCHEDULE_CONFIG.morningCheckIn)) {
        log("到达上班打卡时间");
        executeCheckIn("上班");
    }
    
    // 检查是否到达下班打卡时间
    if (isTargetTime(currentHour, currentMinute, SCHEDULE_CONFIG.eveningCheckIn)) {
        log("到达下班打卡时间");
        executeCheckIn("下班");
    }
}

function isTargetTime(currentHour, currentMinute, targetTime) {
    return currentHour === targetTime.hour && currentMinute === targetTime.minute;
}

function executeCheckIn(type) {
    // 添加随机延迟
    let delay = Math.floor(Math.random() * SCHEDULE_CONFIG.randomDelayMinutes * 60 * 1000);
    log(type + "打卡将在 " + Math.floor(delay / 1000) + " 秒后执行");
    
    setTimeout(function() {
        log("开始执行" + type + "打卡...");
        
        // 解锁屏幕
        wakeUpScreen();
        
        // 启动APP并打卡
        if (launchAndCheckIn()) {
            log("✅ " + type + "打卡成功！");
            // 发送通知
            toast(type + "打卡成功！");
        } else {
            log("❌ " + type + "打卡失败");
            toast(type + "打卡失败，请手动检查");
        }
        
        // 返回桌面
        home();
    }, delay);
}

function wakeUpScreen() {
    if (!device.isScreenOn()) {
        device.wakeUp();
        sleep(1000);
        swipe(device.width / 2, device.height * 0.7, device.width / 2, device.height * 0.3, 500);
        sleep(800);
    }
}

function launchAndCheckIn() {
    // 启动APP
    let launched = app.launchPackage(SCHEDULE_CONFIG.app.packageName);
    if (!launched) {
        launched = app.launchApp(SCHEDULE_CONFIG.app.appName);
    }
    
    if (!launched) {
        return false;
    }
    
    sleep(6000);
    
    // 进入工作台
    let workTab = text("工作台").findOne(3000);
    if (workTab) {
        workTab.click();
        sleep(3000);
    }
    
    // 进入考勤打卡
    let attendance = text("考勤打卡").findOne(5000);
    if (attendance) {
        attendance.click();
        sleep(3000);
    }
    
    // 执行打卡
    let checkInBtn = text("上班打卡").findOne(3000) || 
                     text("下班打卡").findOne(2000) ||
                     text("打卡").findOne(2000);
    
    if (checkInBtn) {
        let bounds = checkInBtn.bounds();
        click(bounds.centerX(), bounds.centerY());
        sleep(2000);
        return true;
    }
    
    // 检查是否已打卡
    if (text("已打卡").exists()) {
        log("检测到已打卡");
        return true;
    }
    
    return false;
}

function formatTime(timeObj) {
    return String(timeObj.hour).padStart(2, '0') + ":" + 
           String(timeObj.minute).padStart(2, '0');
}

// 启动
main();

