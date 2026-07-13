package com.einsteinium.optimization.tick

import com.einsteinium.optimization.EinsteiniumMod
import net.minecraft.entity.Entity
import net.minecraft.entity.projectile.ProjectileEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.mob.MobEntity

class TickScheduler {
    fun onServerTick(tickCount: Long) {
    }

    fun shouldTick(entity: Entity, tickCount: Long): Boolean {
        if (isAlwaysTick(entity)) return true

        val config = EinsteiniumMod.config.tick
        if (!config.skipStaticAI) return true

        val distance = getDistanceToPlayer(entity)

        val tickInterval = when {
            distance <= config.distanceTier1 -> 1
            distance <= config.distanceTier2 -> 2
            distance <= config.distanceTier3 -> 4
            else -> 8
        }

        return tickCount % tickInterval == 0L || isMoving(entity) || isInCombat(entity)
    }

    fun shouldTickAI(entity: Entity, tickCount: Long): Boolean {
        if (!EinsteiniumMod.config.tick.skipStaticAI) return true

        if (entity is MobEntity && entity.isInCombat) return true
        if (isMoving(entity)) return true

        val distance = getDistanceToPlayer(entity)
        val config = EinsteiniumMod.config.tick

        if (distance > config.distanceTier3) return false

        val tickInterval = when {
            distance <= config.distanceTier1 -> 1
            distance <= config.distanceTier2 -> 2
            else -> 4
        }

        return tickCount % tickInterval == 0L
    }

    fun shouldTickPhysics(entity: Entity): Boolean {
        return EinsteiniumMod.config.tick.preservePhysics || !isStatic(entity)
    }

    private fun isAlwaysTick(entity: Entity): Boolean {
        return entity is ProjectileEntity || entity.isPlayer || entity.isControlledByLocalInstance
    }

    private fun isMoving(entity: Entity): Boolean {
        return entity.velocity.lengthSquared() > 0.001 || entity.isOnGround && entity.isMoving
    }

    private fun isInCombat(entity: Entity): Boolean {
        return entity is MobEntity && entity.isInCombat
    }

    private fun isStatic(entity: Entity): Boolean {
        if (entity is ItemEntity) {
            return !entity.isOnGround || entity.velocity.lengthSquared() < 0.001
        }
        return entity.velocity.lengthSquared() < 0.001 && !entity.isMoving
    }

    private fun getDistanceToPlayer(entity: Entity): Double {
        val world = entity.world
        if (world.isClient) return 0.0

        val players = world.players
        if (players.isEmpty()) return Double.MAX_VALUE

        return players.minOf { it.squaredDistanceTo(entity) }.toDouble().coerceAtLeast(0.0)
    }
}