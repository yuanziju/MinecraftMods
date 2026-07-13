package com.einsteinium.optimization.collision

import com.einsteinium.optimization.EinsteiniumMod
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos

class DensityLimiter {
    fun canAddEntity(pos: BlockPos, entityType: net.minecraft.entity.EntityType<*>): Boolean {
        val config = EinsteiniumMod.config.collision
        if (config.densityLimit <= 0) return true

        return !EinsteiniumMod.collisionManager.checkDensityLimit(pos, entityType)
    }

    fun enforceLimit(entity: Entity): Boolean {
        val config = EinsteiniumMod.config.collision
        if (config.densityLimit <= 0) return false

        val canAdd = canAddEntity(entity.blockPos, entity.type)
        if (!canAdd && !entity.isRemoved) {
            entity.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED)
            return true
        }

        return false
    }
}