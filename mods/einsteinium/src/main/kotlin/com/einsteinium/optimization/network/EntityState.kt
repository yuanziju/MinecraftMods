package com.einsteinium.optimization.network

import net.minecraft.entity.Entity
import net.minecraft.util.math.Vec3d

class EntityState(
    val pos: Vec3d,
    val pitch: Float,
    val yaw: Float,
    val onGround: Boolean,
    val velocity: Vec3d,
    val version: Long = 0,
    val dirtyMask: SyncMask = SyncMask(),
    val syncTier: SyncTier = SyncTier.FULL
) {
    companion object {
        fun fromEntity(entity: Entity, version: Long = 0, syncTier: SyncTier = SyncTier.FULL): EntityState {
            return EntityState(
                entity.pos,
                entity.pitch,
                entity.yaw,
                entity.isOnGround,
                entity.velocity,
                version,
                SyncMask().apply { markAll() },
                syncTier
            )
        }
    }

    fun compareAndUpdate(entity: Entity, mask: SyncMask): Boolean {
        var changed = false
        
        if (mask.hasPosition && (pos.x != entity.x || pos.y != entity.y || pos.z != entity.z)) {
            changed = true
        }
        if (mask.hasRotation && (pitch != entity.pitch || yaw != entity.yaw)) {
            changed = true
        }
        if (mask.hasVelocity && (velocity.x != entity.velocity.x || velocity.y != entity.velocity.y || velocity.z != entity.velocity.z)) {
            changed = true
        }
        if (mask.hasOnGround && onGround != entity.isOnGround) {
            changed = true
        }
        
        return changed
    }
}