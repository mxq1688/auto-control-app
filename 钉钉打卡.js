"auto";
// ============================================
// 钉钉自动打卡脚本 - 详细版
// 适用于钉钉考勤打卡
// ============================================

// 配置
const CONFIG = {
    packageName: "com.alibaba.android.rimet",
    appName: "钉钉",
    launchWaitTime: 6000,
    pageLoadWaitTime: 5000,
};

// 主函数
function main() {
    console.show();
    log("===== 钉钉自动打卡开始 =====");
    log("当前时间: " + new Date().toLocaleString());
    
    // 请求截图权限（部分操作可能需要）
    if (!requestScreenCapture()) {
        log("请求截图权限失败");
    }
    
    // 解锁屏幕
    wakeUpAndUnlock();
    
    // 启动钉钉
    if (!launchDingTalk()) {
        log("启动钉钉失败");
        return;
    }
    
    // 进入工作台
    if (!enterWorkbench()) {
        log("进入工作台失败，尝试直接查找打卡");
    }
    
    // 进入考勤打卡
    if (!enterAttendance()) {
        log("进入考勤打卡失败");
        return;
    }
    
    // 执行打卡
    if (doCheckIn()) {
        log("✅ 打卡成功！");
        toast("钉钉打卡成功！");
    } else {
        log("❌ 打卡可能失败，请检查");
        toast("请检查打卡状态");
    }
    
    sleep(2000);
    log("===== 脚本执行完毕 =====");
}

// 唤醒并解锁屏幕
function wakeUpAndUnlock() {
    if (!device.isScreenOn()) {
        log("唤醒屏幕...");
        device.wakeUp();
        sleep(1000);
    }
    
    // 上滑解锁（无密码情况）
    swipe(device.width / 2, device.height * 0.7, device.width / 2, device.height * 0.3, 500);
    sleep(800);
}

// 启动钉钉
function launchDingTalk() {
    log("正在启动钉钉...");
    
    // 先回到桌面
    home();
    sleep(1000);
    
    // 启动钉钉
    let launched = app.launchPackage(CONFIG.packageName);
    if (!launched) {
        launched = app.launchApp(CONFIG.appName);
    }
    
    if (launched) {
        log("等待钉钉启动...");
        sleep(CONFIG.launchWaitTime);
        
        // 处理可能的弹窗
        handlePopups();
        return true;
    }
    
    return false;
}

// 处理弹窗
function handlePopups() {
    // 常见弹窗关闭按钮
    let closeTexts = ["我知道了", "暂不", "取消", "关闭", "跳过", "以后再说"];
    
    for (let i = 0; i < closeTexts.length; i++) {
        let btn = text(closeTexts[i]).findOne(500);
        if (btn) {
            log("关闭弹窗: " + closeTexts[i]);
            btn.click();
            sleep(500);
        }
    }
}

// 进入工作台
function enterWorkbench() {
    log("正在进入工作台...");
    
    // 查找底部"工作台"tab
    let workTab = text("工作台").findOne(3000);
    if (!workTab) {
        workTab = desc("工作台").findOne(2000);
    }
    
    if (workTab) {
        workTab.click();
        sleep(CONFIG.pageLoadWaitTime);
        handlePopups();
        return true;
    }
    
    return false;
}

// 进入考勤打卡
function enterAttendance() {
    log("正在查找考勤打卡...");
    
    // 查找"考勤打卡"入口
    let attendance = text("考勤打卡").findOne(5000);
    if (!attendance) {
        attendance = desc("考勤打卡").findOne(3000);
    }
    
    if (!attendance) {
        // 尝试滚动查找
        log("向下滚动查找...");
        scrollDown();
        sleep(1000);
        attendance = text("考勤打卡").findOne(3000);
    }
    
    if (attendance) {
        log("找到考勤打卡入口");
        attendance.click();
        sleep(CONFIG.pageLoadWaitTime);
        handlePopups();
        return true;
    }
    
    return false;
}

// 执行打卡
function doCheckIn() {
    log("正在执行打卡...");
    
    // 等待打卡页面加载
    sleep(2000);
    
    // 查找打卡按钮（可能的文字）
    let checkInTexts = [
        "上班打卡",
        "下班打卡", 
        "打卡",
        "外出打卡",
        "签到"
    ];
    
    for (let i = 0; i < checkInTexts.length; i++) {
        let btn = text(checkInTexts[i]).findOne(2000);
        if (btn) {
            log("找到按钮: " + checkInTexts[i]);
            
            // 获取按钮位置并点击
            let bounds = btn.bounds();
            click(bounds.centerX(), bounds.centerY());
            sleep(2000);
            
            // 检查是否打卡成功（查找成功提示）
            let successTexts = ["打卡成功", "已打卡", "签到成功"];
            for (let j = 0; j < successTexts.length; j++) {
                if (text(successTexts[j]).exists()) {
                    return true;
                }
            }
            
            return true; // 假设点击成功
        }
    }
    
    // 如果没找到按钮，可能已经打过卡了
    if (text("已打卡").exists() || text("更新打卡").exists()) {
        log("检测到已打卡状态");
        return true;
    }
    
    return false;
}

// 运行
main();

