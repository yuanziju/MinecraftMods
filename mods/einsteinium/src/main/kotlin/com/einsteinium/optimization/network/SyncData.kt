package com.einsteinium.optimization.network

import net.minecraft.entity.Entity
import net.minecraft.entity.data.DataTracker
import net.minecraft.util.math.Vec3d

enum class SyncTier {
    FULL,
    PARTIAL,
    MINIMAL
}

class SyncMask {
    var hasPosition = false
    var hasRotation = false
    var hasVelocity = false
    var hasOnGround = false
    var hasDataTracker = false
    var hasMetadata = false

    fun isEmpty(): Boolean {
        return !hasPosition && !hasRotation && !hasVelocity && !hasOnGround && !hasDataTracker && !hasMetadata
    }

    fun clear() {
        hasPosition = false
        hasRotation = false
        hasVelocity = false
        hasOnGround = false
        hasDataTracker = false
        hasMetadata = false
    }

    fun markAll() {
        hasPosition = true
        hasRotation = true
        hasVelocity = true
        hasOnGround = true
        hasDataTracker = true
        hasMetadata = true
    }

    fun markPosition() { hasPosition = true }
    fun markRotation() { hasRotation = true }
    fun markVelocity() { hasVelocity = true }
    fun markOnGround() { hasOnGround = true }
    fun markDataTracker() { hasDataTracker = true }
    fun markMetadata() { hasMetadata = true }
}

class EntitySyncSnapshot(
    val entityId: Int,
    val pos: Vec3d,
    val pitch: Float,
    val yaw: Float,
    val velocity: Vec3d,
    val onGround: Boolean,
    val dirtyEntries: List<DataTracker.Entry<*>>,
    val mask: SyncMask,
    val syncTier: SyncTier,
    val version: Long
) {
    companion object {
        fun fromEntity(entity: Entity, mask: SyncMask, syncTier: SyncTier, version: Long): EntitySyncSnapshot {
            val dirtyEntries = if (mask.hasDataTracker) {
                entity.dataTracker.trackedValues
                    .filter { it.isDirty }
                    .map { it.copy() }
            } else {
                emptyList()
            }
            return EntitySyncSnapshot(
                entity.id,
                entity.pos,
                entity.pitch,
                entity.yaw,
                entity.velocity,
                entity.isOnGround,
                dirtyEntries,
                mask,
                syncTier,
                version
            )
        }
    }
}