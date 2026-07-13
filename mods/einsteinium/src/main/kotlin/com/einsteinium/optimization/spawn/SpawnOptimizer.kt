package com.einsteinium.optimization.spawn

import com.einsteinium.optimization.EinsteiniumMod
import net.minecraft.util.math.BlockPos

class SpawnOptimizer {
    fun canSpawnAt(pos: BlockPos, type: net.minecraft.entity.EntityType<*>): Boolean {
        val config = EinsteiniumMod.config.spawn
        if (config.densityLimit <= 0) return true

        return !DensityTracker.isOverLimit(pos, type, config.densityLimit)
    }

    fun adjustSpawnProbability(distance: Double, baseProbability: Double): Double {
        val config = EinsteiniumMod.config.spawn

        if (distance <= 128.0) return baseProbability

        val factor = when {
            distance <= 256.0 -> config.distanceFactor
            else -> config.distanceFactor * 0.2
        }

        return baseProbability * factor
    }

    fun adjustCooldown(pos: BlockPos, baseCooldown: Int): Int {
        val config = EinsteiniumMod.config.spawn
        if (!config.cooldownAdjust) return baseCooldown

        val density = DensityTracker.getDensity(pos)
        val densityLimit = config.densityLimit.toDouble()

        val multiplier = 1.0 + (density / densityLimit) * 0.5

        return (baseCooldown * multiplier).toInt()
    }
}