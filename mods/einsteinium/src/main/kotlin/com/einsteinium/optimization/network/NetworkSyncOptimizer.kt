package com.einsteinium.optimization.network

import com.einsteinium.optimization.EinsteiniumMod
import com.einsteinium.optimization.config.EinsteiniumConfig
import net.minecraft.entity.Entity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.Vec3d
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class NetworkSyncOptimizer {
    private val entityStates = ConcurrentHashMap<Int, EntityState>()
    private val entityVersions = ConcurrentHashMap<Int, Long>()
    private val pendingUpdates = ConcurrentLinkedQueue<EntitySyncSnapshot>()
    private val tickCounters = ConcurrentHashMap<Int, Int>()
    private var globalVersion = 0L

    private val config: EinsteiniumConfig.SyncConfig
        get() = EinsteiniumConfig.sync

    fun onEntityTick(entity: Entity) {
        if (entity.isClient) return
        
        val entityId = entity.id
        val currentVersion = entityVersions.getOrDefault(entityId, 0L)
        val lastState = entityStates[entityId]
        
        if (lastState != null && config.incremental) {
            val mask = determineSyncMask(entity, lastState)
            if (!mask.isEmpty()) {
                val newState = EntityState.fromEntity(entity, currentVersion + 1, lastState.syncTier)
                entityStates[entityId] = newState
                entityVersions[entityId] = currentVersion + 1
                
                val snapshot = EntitySyncSnapshot.fromEntity(entity, mask, lastState.syncTier, currentVersion + 1)
                pendingUpdates.offer(snapshot)
            }
        } else {
            val syncTier = determineSyncTier(entity, 0.0)
            val newState = EntityState.fromEntity(entity, currentVersion + 1, syncTier)
            entityStates[entityId] = newState
            entityVersions[entityId] = currentVersion + 1
        }
    }

    fun getSyncTier(entity: Entity, distance: Double): SyncTier {
        return when {
            distance <= config.distanceTier1 -> SyncTier.FULL
            distance <= config.distanceTier2 -> SyncTier.PARTIAL
            else -> SyncTier.MINIMAL
        }
    }

    fun determineSyncTier(entity: Entity, distance: Double): SyncTier {
        return getSyncTier(entity, distance)
    }

    fun determineSyncMask(entity: Entity, lastState: EntityState): SyncMask {
        val mask = SyncMask()
        
        if (entity.x != lastState.pos.x || entity.y != lastState.pos.y || entity.z != lastState.pos.z) {
            mask.markPosition()
        }
        if (entity.pitch != lastState.pitch || entity.yaw != lastState.yaw) {
            mask.markRotation()
        }
        if (entity.velocity.x != lastState.velocity.x || 
            entity.velocity.y != lastState.velocity.y || 
            entity.velocity.z != lastState.velocity.z) {
            mask.markVelocity()
        }
        if (entity.isOnGround != lastState.onGround) {
            mask.markOnGround()
        }
        
        val hasDirtyData = entity.dataTracker.trackedValues.any { it.isDirty }
        if (hasDirtyData) {
            mask.markDataTracker()
        }
        
        return mask
    }

    fun shouldSync(entity: Entity, player: ServerPlayerEntity, tickCount: Int): Boolean {
        val distance = player.squaredDistanceTo(entity)
        val distanceSq = config.distanceTier2.toDouble() * config.distanceTier2.toDouble()
        
        if (distance > distanceSq) {
            val counter = tickCounters.compute(entity.id) { _, v -> (v ?: 0) + 1 }
            return counter != null && counter % SyncPacket.MINIMAL_SYNC_INTERVAL_TICKS == 0
        }
        
        return true
    }

    fun generateSyncPackets(player: ServerPlayerEntity): List<SyncPacket> {
        val playerPos = player.pos
        val entitiesToSync = mutableListOf<EntitySyncSnapshot>()
        
        while (!pendingUpdates.isEmpty()) {
            val snapshot = pendingUpdates.poll() ?: continue
            val entity = player.world.getEntityById(snapshot.entityId) ?: continue
            
            val distance = playerPos.distanceTo(entity.pos)
            val syncTier = determineSyncTier(entity, distance)
            
            if (syncTier == SyncTier.MINIMAL) {
                val counter = tickCounters.compute(entity.id) { _, v -> (v ?: 0) + 1 }
                if (counter == null || counter % SyncPacket.MINIMAL_SYNC_INTERVAL_TICKS != 0) {
                    continue
                }
            }
            
            val effectiveMask = applyTierMask(snapshot.mask, syncTier)
            if (!effectiveMask.isEmpty()) {
                entitiesToSync.add(EntitySyncSnapshot(
                    snapshot.entityId,
                    snapshot.pos,
                    snapshot.pitch,
                    snapshot.yaw,
                    snapshot.velocity,
                    snapshot.onGround,
                    if (effectiveMask.hasDataTracker) snapshot.dirtyEntries else emptyList(),
                    effectiveMask,
                    syncTier,
                    snapshot.version
                ))
            }
        }
        
        return entitiesToSync.chunked(config.batchSize)
            .map { SyncPacket(it) }
            .filter { !it.isEmpty() }
    }

    private fun applyTierMask(mask: SyncMask, tier: SyncTier): SyncMask {
        val result = SyncMask()
        
        when (tier) {
            SyncTier.FULL -> {
                result.hasPosition = mask.hasPosition
                result.hasRotation = mask.hasRotation
                result.hasVelocity = mask.hasVelocity
                result.hasOnGround = mask.hasOnGround
                result.hasDataTracker = mask.hasDataTracker
                result.hasMetadata = mask.hasMetadata
            }
            SyncTier.PARTIAL -> {
                result.hasPosition = mask.hasPosition
                result.hasRotation = mask.hasRotation
                result.hasOnGround = mask.hasOnGround
            }
            SyncTier.MINIMAL -> {
                result.hasPosition = true
            }
        }
        
        return result
    }

    fun onEntityRemoved(entityId: Int) {
        entityStates.remove(entityId)
        entityVersions.remove(entityId)
        tickCounters.remove(entityId)
    }

    fun hasPendingUpdates(): Boolean {
        return !pendingUpdates.isEmpty()
    }

    fun getPendingUpdateCount(): Int {
        return pendingUpdates.size
    }

    fun reset() {
        entityStates.clear()
        entityVersions.clear()
        pendingUpdates.clear()
        tickCounters.clear()
        globalVersion = 0L
    }

    fun getEntityState(entityId: Int): EntityState? {
        return entityStates[entityId]
    }

    fun getEntityVersion(entityId: Int): Long {
        return entityVersions.getOrDefault(entityId, 0L)
    }
}