package com.rhenium.optimization.monitor

import com.mojang.blaze3d.systems.RenderSystem
import com.rhenium.optimization.RheniumMod
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component

/**
 * 性能监控 HUD 渲染器
 *
 * 通过 Fabric [HudRenderCallback] 在屏幕上叠加显示 Rhenium 运行时性能数据，
 * 包括：图数量、总节点数、平均 tick 耗时、缓存命中率、各优化级别分布、线程池状态。
 *
 * 显示位置通过 [com.rhenium.optimization.config.RheniumConfig.hudX] 与
 * [com.rhenium.optimization.config.RheniumConfig.hudY] 配置。
 * 通过 [com.rhenium.optimization.config.RheniumConfig.enableHud] 控制是否显示。
 *
 * 注意：MC 1.21 Mojmap 中 DrawContext 类已被重命名为 [GuiGraphics]。
 */
object HudRenderer {

    /** 文字颜色：白色（不透明） */
    private const val COLOR_TEXT = 0xFFFFFFFF.toInt()

    /** 标题颜色：浅黄色（不透明） */
    private const val COLOR_TITLE = 0xFFFFFF66.toInt()

    /** 背景颜色：半透明黑色（A=0xC0, R=0, G=0, B=0） */
    private const val COLOR_BACKGROUND = 0xC0000000.toInt()

    /** 每行文字高度（包含行距） */
    private const val LINE_HEIGHT = 11

    /** HUD 边距 */
    private const val PADDING = 4

    /** 是否已注册到 Fabric HUD 回调 */
    private var registered = false

    /**
     * 注册到 Fabric HUD 渲染回调。
     *
     * 应在客户端初始化时调用一次（如 ClientModInitializer.onInitializeClient）。
     * 多次调用安全：内部使用 [registered] 标志防止重复注册。
     */
    fun register() {
        if (registered) return
        registered = true
        HudRenderCallback.EVENT.register { context, _ ->
            render(context)
        }
    }

    /**
     * 渲染 HUD 内容。
     *
     * 当 HUD 被禁用或 RheniumMod 未就绪时直接返回。
     * 渲染流程：
     * 1. 从 [RheniumMod.CONFIG] 读取 HUD 位置与开关；
     * 2. 收集 [PerformanceMonitor] 各项指标；
     * 3. 在屏幕上绘制半透明背景与多行文字。
     *
     * @param context  图形上下文（MC 1.21 中为 [GuiGraphics]）
     */
    fun render(context: GuiGraphics) {
        val config = try {
            RheniumMod.CONFIG
        } catch (throwable: Throwable) {
            // RheniumMod 尚未初始化，跳过本次渲染
            return
        }

        // HUD 全局开关
        if (!config.enableHud) return

        val mc = Minecraft.getInstance()
        val font = mc.font
        if (font == null) return

        // 收集监控指标（任何异常均跳过本次渲染）
        val lines: List<Pair<String, Int>> = try {
            collectLines()
        } catch (throwable: Throwable) {
            return
        }

        // 计算 HUD 矩形尺寸
        val maxWidth = lines.maxOf { font.width(it.first) }
        val boxWidth = maxWidth + PADDING * 2
        val boxHeight = lines.size * LINE_HEIGHT + PADDING * 2

        // HUD 位置（从配置读取）
        val x = config.hudX
        val y = config.hudY

        // 启用混合以保证半透明背景正常显示
        RenderSystem.enableBlend()

        // 1. 绘制半透明黑色背景
        context.fill(x, y, x + boxWidth, y + boxHeight, COLOR_BACKGROUND)

        // 2. 绘制文字内容
        var lineY = y + PADDING
        for ((text, color) in lines) {
            context.drawString(
                font,
                text,
                x + PADDING,
                lineY,
                color,
                false /* shadow */
            )
            lineY += LINE_HEIGHT
        }

        RenderSystem.disableBlend()
    }

    /**
     * 收集 HUD 显示的所有行。
     *
     * 返回每行的文本与颜色对，标题行使用 [COLOR_TITLE]，内容行使用 [COLOR_TEXT]。
     *
     * @return 行列表
     */
    private fun collectLines(): List<Pair<String, Int>> {
        val lines = mutableListOf<Pair<String, Int>>()

        // 标题
        lines += "§l§eRhenium 性能监控" to COLOR_TITLE

        // 图数量 / 总节点数
        val graphCount = PerformanceMonitor.getGraphCount()
        val nodeCount = PerformanceMonitor.getTotalNodeCount()
        lines += "图数量: $graphCount" to COLOR_TEXT
        lines += "总节点数: $nodeCount" to COLOR_TEXT

        // 平均 tick 耗时（毫秒）
        val avgMillis = PerformanceMonitor.getAverageTickTimeMillis()
        lines += "平均 tick 耗时: ${"%.3f".format(avgMillis)} ms" to COLOR_TEXT

        // 缓存命中率
        val hitRate = PerformanceMonitor.getCacheHitRate()
        lines += "缓存命中率: ${"%.2f".format(hitRate * 100)}%" to COLOR_TEXT

        // 各优化级别分布
        val levelDist = PerformanceMonitor.getOptimizationLevelDistribution()
        if (levelDist.isEmpty()) {
            lines += "优化级别分布: (暂无数据)" to COLOR_TEXT
        } else {
            val distStr = levelDist.entries
                .sortedBy { it.key }
                .joinToString(", ") { (level, count) -> "L$level=$count" }
            lines += "优化级别分布: $distStr" to COLOR_TEXT
        }

        // 线程池状态（基于活动图数量推断）
        val threadPoolStatus = if (graphCount > 0) "活跃" else "空闲"
        lines += "线程池状态: $threadPoolStatus" to COLOR_TEXT

        // 滑动窗口大小（诊断信息）
        val windowSize = PerformanceMonitor.getWindowSize()
        lines += "样本窗口: $windowSize / 100" to COLOR_TEXT

        return lines
    }
}
