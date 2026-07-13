package com.molten.optimization.debug

import com.molten.optimization.MoltenMod
import com.molten.optimization.config.MoltenConfig
import net.minecraft.client.util.math.MatrixStack

object DebugSystem {
    private val performanceCounters = PerformanceCounters()
    private val pipelineVisualizer = PipelineVisualizer()
    private val frameTimeAnalyzer = FrameTimeAnalyzer()
    private var enabled = false

    fun enable() {
        if (enabled) return
        enabled = true
        MoltenMod.LOGGER.info("Debug system enabled")
    }

    fun disable() {
        if (!enabled) return
        enabled = false
        MoltenMod.LOGGER.info("Debug system disabled")
    }

    fun renderHUD(poseStack: MatrixStack) {
        if (!enabled) return

        if (MoltenConfig.debugCounters) {
            performanceCounters.update()
        }

        if (MoltenConfig.debugFrameTime) {
            frameTimeAnalyzer.recordFrameTime(System.currentTimeMillis())
        }
    }

    fun getPerformanceCounters(): PerformanceCounters = performanceCounters

    fun getFrameTimeAnalyzer(): FrameTimeAnalyzer = frameTimeAnalyzer
}
