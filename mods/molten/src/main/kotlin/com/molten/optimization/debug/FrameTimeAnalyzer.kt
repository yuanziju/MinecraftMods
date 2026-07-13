package com.molten.optimization.debug

import java.util.ArrayDeque

class FrameTimeAnalyzer {
    private val frameTimes: ArrayDeque<Long> = ArrayDeque()
    private val maxSize = 60

    fun recordFrameTime(time: Long) {
        frameTimes.addLast(time)
        if (frameTimes.size > maxSize) {
            frameTimes.removeFirst()
        }
    }

    fun getAverageFrameTime(): Long {
        if (frameTimes.isEmpty()) return 0
        return frameTimes.sum() / frameTimes.size
    }

    fun getMaxFrameTime(): Long {
        return frameTimes.maxOrNull() ?: 0
    }

    fun getMinFrameTime(): Long {
        return frameTimes.minOrNull() ?: 0
    }

    fun getFrameTimeVariance(): Double {
        if (frameTimes.isEmpty()) return 0.0
        val average = getAverageFrameTime().toDouble()
        var sum = 0.0
        for (time in frameTimes) {
            sum += Math.pow(time.toDouble() - average, 2.0)
        }
        return sum / frameTimes.size
    }

    fun reset() {
        frameTimes.clear()
    }
}
