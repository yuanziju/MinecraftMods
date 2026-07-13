package com.einsteinium.optimization.rendering

import com.einsteinium.optimization.EinsteiniumMod
import net.minecraft.entity.Entity
import net.minecraft.entity.projectile.ProjectileEntity
import net.minecraft.entity.item.ItemEntity
import net.minecraft.entity.player.PlayerEntity
import org.joml.FrustumIntersection
import org.joml.Matrix4f
import org.joml.Vector3f
import java.util.concurrent.ConcurrentHashMap

class FrustumCuller {
    private val frustum = FrustumIntersection()
    private val visibleCache = ConcurrentHashMap<Int, Boolean>()
    private var lastFrustumUpdate = 0L
    private var cacheValid = false

    fun cull(entities: List<Entity>, frustum: net.minecraft.client.render.Frustum): List<Entity> {
        if (!EinsteiniumMod.config.rendering.frustumCull) return entities

        updateFrustum(frustum)
        return entities.filter { isVisible(it, frustum) }
    }

    fun isVisible(entity: Entity, frustum: net.minecraft.client.render.Frustum): Boolean {
        if (!EinsteiniumMod.config.rendering.frustumCull) return true

        if (entity is PlayerEntity && !entity.isSpectator) {
            return true
        }

        if (!entity.isAlive || entity.isInvisible) {
            return false
        }

        if (!cacheValid) {
            updateFrustum(frustum)
        }

        val entityId = entity.id
        val cached = visibleCache[entityId]
        if (cached != null) {
            return cached
        }

        val isVisible = calculateVisibility(entity, frustum)
        visibleCache[entityId] = isVisible

        return isVisible
    }

    private fun updateFrustum(mcFrustum: net.minecraft.client.render.Frustum) {
        val projectionMatrix = mcFrustum.projectionMatrix
        val modelViewMatrix = mcFrustum.modelViewMatrix

        val clipMatrix = Matrix4f(projectionMatrix)
            .mul(Matrix4f(modelViewMatrix))

        frustum.set(clipMatrix)
        cacheValid = true
        lastFrustumUpdate = System.currentTimeMillis()
    }

    private fun calculateVisibility(entity: Entity, mcFrustum: net.minecraft.client.render.Frustum): Boolean {
        val boundingBox = entity.boundingBox

        if (mcFrustum.isVisible(boundingBox)) {
            return true
        }

        if (entity is ProjectileEntity) {
            val velocity = entity.velocity
            val futurePos = Vector3f(
                (entity.pos.x + velocity.x * 0.5).toFloat(),
                (entity.pos.y + velocity.y * 0.5).toFloat(),
                (entity.pos.z + velocity.z * 0.5).toFloat()
            )
            return frustum.testPoint(futurePos)
        }

        if (entity is ItemEntity) {
            val centerX = (boundingBox.minX + boundingBox.maxX) / 2.0
            val centerY = (boundingBox.minY + boundingBox.maxY) / 2.0
            val centerZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0

            val center = Vector3f(centerX.toFloat(), centerY.toFloat(), centerZ.toFloat())
            val radius = maxOf(
                boundingBox.maxX - boundingBox.minX,
                boundingBox.maxY - boundingBox.minY,
                boundingBox.maxZ - boundingBox.minZ
            ) / 2.0f

            return frustum.testSphere(center, radius)
        }

        return false
    }

    fun invalidateCache() {
        cacheValid = false
        visibleCache.clear()
    }

    fun cleanupOldCache(maxAgeMs: Long = 5000) {
        val now = System.currentTimeMillis()
        visibleCache.entries.removeIf { now - lastFrustumUpdate > maxAgeMs }
    }

    fun getVisibleCount(): Int {
        return visibleCache.count { it.value }
    }

    fun getTotalChecked(): Int {
        return visibleCache.size
    }
}