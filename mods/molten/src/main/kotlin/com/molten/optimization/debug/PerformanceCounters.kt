package com.molten.optimization.debug

class PerformanceCounters {
    var drawCalls: Int = 0
    var gpuUtilization: Float = 0f
    var frameTime: Long = 0L
    var memoryUsage: Long = 0L

    fun update() {
        drawCalls = 0
        gpuUtilization = 0f
        frameTime = 0L
        memoryUsage = 0L
    }

    fun reset() {
        drawCalls = 0
        gpuUtilization = 0f
        frameTime = 0L
        memoryUsage = 0L
    }
}
