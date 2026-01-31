package com.example.feishupunch.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 流程步骤类型
 */
enum class StepType(val displayName: String) {
    OPEN_APP("打开APP"),
    CLICK_XY("点击坐标"),
    CLICK_TEXT("点击文本"),
    LONG_PRESS("长按"),
    DOUBLE_CLICK("双击"),
    SWIPE("滑动"),
    DELAY("等待"),
    BACK("返回"),
    HOME("回到桌面"),
    RECENT_APPS("最近任务"),
    NOTIFICATIONS("通知栏")
}

/**
 * 流程步骤数据类
 */
data class FlowStep(
    val id: String = System.currentTimeMillis().toString(),
    val type: StepType,
    val x: Int = 0,           // 起始坐标X / 点击坐标X
    val y: Int = 0,           // 起始坐标Y / 点击坐标Y
    val x2: Int = 0,          // 终点坐标X (滑动用)
    val y2: Int = 0,          // 终点坐标Y (滑动用)
    val text: String = "",    // 点击文本内容
    val delay: Long = 1000,   // 等待时间(毫秒)
    val duration: Long = 300  // 手势持续时间(毫秒)
) {
    /**
     * 获取步骤描述
     */
    fun getDescription(): String {
        return when (type) {
            StepType.OPEN_APP -> "打开目标APP"
            StepType.CLICK_XY -> "点击 ($x, $y)"
            StepType.CLICK_TEXT -> "点击「$text」"
            StepType.LONG_PRESS -> "长按 ($x, $y) ${duration}ms"
            StepType.DOUBLE_CLICK -> "双击 ($x, $y)"
            StepType.SWIPE -> "滑动 ($x,$y) → ($x2,$y2)"
            StepType.DELAY -> "等待 ${delay}ms"
            StepType.BACK -> "返回"
            StepType.HOME -> "桌面"
            StepType.RECENT_APPS -> "最近任务"
            StepType.NOTIFICATIONS -> "通知栏"
        }
    }

    /**
     * 转为JSON
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("type", type.name)
            put("x", x)
            put("y", y)
            put("x2", x2)
            put("y2", y2)
            put("text", text)
            put("delay", delay)
            put("duration", duration)
        }
    }

    companion object {
        /**
         * 从JSON解析
         */
        fun fromJson(json: JSONObject): FlowStep? {
            return try {
                FlowStep(
                    id = json.optString("id", System.currentTimeMillis().toString()),
                    type = StepType.valueOf(json.getString("type")),
                    x = json.optInt("x", 0),
                    y = json.optInt("y", 0),
                    x2 = json.optInt("x2", 0),
                    y2 = json.optInt("y2", 0),
                    text = json.optString("text", ""),
                    delay = json.optLong("delay", 1000),
                    duration = json.optLong("duration", 300)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * 流程数据类
 */
data class Flow(
    val id: String = System.currentTimeMillis().toString(),
    var name: String = "默认流程",
    val steps: MutableList<FlowStep> = mutableListOf()
) {
    /**
     * 转为JSON
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("steps", JSONArray().apply {
                steps.forEach { put(it.toJson()) }
            })
        }
    }

    companion object {
        /**
         * 从JSON解析
         */
        fun fromJson(json: JSONObject): Flow? {
            return try {
                val flow = Flow(
                    id = json.optString("id", System.currentTimeMillis().toString()),
                    name = json.optString("name", "默认流程")
                )
                val stepsArray = json.optJSONArray("steps")
                if (stepsArray != null) {
                    for (i in 0 until stepsArray.length()) {
                        FlowStep.fromJson(stepsArray.getJSONObject(i))?.let {
                            flow.steps.add(it)
                        }
                    }
                }
                flow
            } catch (e: Exception) {
                null
            }
        }

        /**
         * 创建默认流程
         */
        fun createDefault(): Flow {
            return Flow(
                name = "默认流程",
                steps = mutableListOf(
                    FlowStep(type = StepType.OPEN_APP),
                    FlowStep(type = StepType.DELAY, delay = 2000),
                    FlowStep(type = StepType.CLICK_TEXT, text = "工作台"),
                    FlowStep(type = StepType.DELAY, delay = 1000),
                    FlowStep(type = StepType.CLICK_TEXT, text = "假勤"),
                    FlowStep(type = StepType.DELAY, delay = 1000)
                )
            )
        }
    }
}

