"auto";
// ============================================
// Auto.js 自动打卡脚本
// 使用前请确保：
// 1. 已开启 Auto.js 的无障碍服务
// 2. 已授予悬浮窗权限
// 3. 根据实际APP修改下方配置
// ============================================

// ============ 配置区域 ============
const CONFIG = {
    // 目标APP包名（可以通过 Auto.js 的"布局分析"功能获取）
    // 常见APP包名示例：
    // 钉钉: "com.alibaba.android.rimet"
    // 企业微信: "com.tencent.wework"
    // 飞书: "com.ss.android.lark"
    packageName: "com.alibaba.android.rimet",
    
    // APP名称（用于通过名称启动）
    appName: "钉钉",
    
    // 打卡按钮的文字（根据实际APP修改）
    checkInButtonText: "打卡",
    
    // 等待APP启动的时间（毫秒）
    launchWaitTime: 5000,
    
    // 等待页面加载的时间（毫秒）
    pageLoadWaitTime: 3000,
    
    // 操作间隔时间（毫秒）
    actionInterval: 1000,
};

// ============ 主函数 ============
function main() {
    console.show();
    log("===== 自动打卡脚本启动 =====");
    
    // 1. 解锁屏幕（如果需要）
    unlockScreen();
    
    // 2. 打开目标APP
    if (!launchApp()) {
        log("启动APP失败，脚本退出");
        return;
    }
    
    // 3. 执行打卡操作
    if (doCheckIn()) {
        log("✅ 打卡成功！");
        toast("打卡成功！");
    } else {
        log("❌ 打卡失败，请手动检查");
        toast("打卡失败，请手动检查");
    }
    
    log("===== 脚本执行完毕 =====");
}

// ============ 解锁屏幕 ============
function unlockScreen() {
    if (!device.isScreenOn()) {
        log("屏幕已关闭，正在唤醒...");
        device.wakeUp();
        sleep(1000);
    }
    
    // 如果有锁屏，向上滑动解锁（适用于无密码锁屏）
    if (device.isScreenOn()) {
        swipe(device.width / 2, device.height * 0.8, device.width / 2, device.height * 0.2, 300);
        sleep(500);
    }
}

// ============ 启动APP ============
function launchApp() {
    log("正在启动: " + CONFIG.appName);
    
    // 方式1: 通过包名启动（推荐）
    let result = app.launchPackage(CONFIG.packageName);
    
    // 方式2: 如果包名启动失败，尝试通过名称启动
    if (!result) {
        log("通过包名启动失败，尝试通过名称启动...");
        result = app.launchApp(CONFIG.appName);
    }
    
    if (result) {
        log("APP启动命令已发送，等待加载...");
        sleep(CONFIG.launchWaitTime);
        return true;
    }
    
    return false;
}

// ============ 执行打卡 ============
function doCheckIn() {
    log("正在查找打卡入口...");
    
    // ====== 方式1: 通过文字查找打卡按钮 ======
    let checkInBtn = text(CONFIG.checkInButtonText).findOne(CONFIG.pageLoadWaitTime);
    if (checkInBtn) {
        log("找到打卡按钮，正在点击...");
        sleep(CONFIG.actionInterval);
        checkInBtn.click();
        sleep(CONFIG.actionInterval);
        return true;
    }
    
    // ====== 方式2: 通过描述(desc)查找 ======
    checkInBtn = desc(CONFIG.checkInButtonText).findOne(CONFIG.pageLoadWaitTime);
    if (checkInBtn) {
        log("通过描述找到打卡按钮，正在点击...");
        sleep(CONFIG.actionInterval);
        checkInBtn.click();
        sleep(CONFIG.actionInterval);
        return true;
    }
    
    // ====== 方式3: 通过ID查找（需要先分析布局获取ID）======
    // 示例: let checkInBtn = id("com.xxx.xxx:id/check_in_btn").findOne(3000);
    
    // ====== 方式4: 通过坐标点击（最后手段，不同设备可能不同）======
    // 示例: click(540, 1200);
    
    log("未找到打卡按钮");
    return false;
}

// ============ 辅助函数 ============

/**
 * 等待某个控件出现
 */
function waitForElement(selector, timeout) {
    timeout = timeout || 10000;
    let element = selector.findOne(timeout);
    return element;
}

/**
 * 安全点击（先判断控件是否可点击）
 */
function safeClick(element) {
    if (element) {
        if (element.clickable()) {
            return element.click();
        } else {
            // 如果控件本身不可点击，尝试点击其父控件或使用坐标点击
            let bounds = element.bounds();
            return click(bounds.centerX(), bounds.centerY());
        }
    }
    return false;
}

/**
 * 返回桌面
 */
function goHome() {
    home();
    sleep(1000);
}

// ============ 运行脚本 ============
main();

