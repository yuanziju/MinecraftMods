package com.einsteinium.optimization.network

import com.einsteinium.optimization.config.EinsteiniumConfig
import net.minecraft.entity.Entity
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket
import net.minecraft.server.network.ServerPlayerEntity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class BatchPacketBuilder {
    private val entityBuffers = ConcurrentHashMap<Int, MutableList<EntitySyncSnapshot>>()
    private val playerQueues = ConcurrentHashMap<ServerPlayerEntity, ConcurrentLinkedQueue<SyncPacket>>()
    private val lastSendTimes = ConcurrentHashMap<Int, Long>()
    
    private val config: EinsteiniumConfig.SyncConfig
        get() = EinsteiniumConfig.sync

    fun addEntityUpdate(entity: Entity, snapshot: EntitySyncSnapshot) {
        entityBuffers.compute(entity.id) { _, list ->
            (list ?: mutableListOf()).also { it.add(snapshot) }
        }
    }

    fun buildPackets(player: ServerPlayerEntity): List<SyncPacket> {
        val packets = mutableListOf<SyncPacket>()
        val playerPos = player.pos
        val currentTime = System.currentTimeMillis()
        
        val distanceTier1 = config.distanceTier1.toDouble()
        val distanceTier2 = config.distanceTier2.toDouble()
        
        val entitiesToSync = mutableListOf<EntitySyncSnapshot>()
        
        entityBuffers.forEach { (entityId, snapshots) ->
            val entity = player.world.getEntityById(entityId) ?: return@forEach
            
            val distance = playerPos.distanceTo(entity.pos)
            val syncTier = when {
                distance <= distanceTier1 -> SyncTier.FULL
                distance <= distanceTier2 -> SyncTier.PARTIAL
                else -> SyncTier.MINIMAL
            }
            
            val lastSendTime = lastSendTimes.getOrDefault(entityId, 0L)
            if (syncTier == SyncTier.MINIMAL) {
                val interval = SyncPacket.MINIMAL_SYNC_INTERVAL_TICKS * 50L
                if (currentTime - lastSendTime < interval) {
                    return@forEach
                }
            }
            
            snapshots.forEach { snapshot ->
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
            
            lastSendTimes[entityId] = currentTime
        }
        
        entityBuffers.clear()
        
        entitiesToSync.chunked(config.batchSize).forEach { chunk ->
            packets.add(SyncPacket(chunk))
        }
        
        return packets
    }

    fun buildPacketBuffer(packet: SyncPacket): PacketByteBuf {
        val buf = PacketByteBuf(net.minecraft.network.PacketByteBufAllocator.DEFAULT.buffer())
        packet.write(buf)
        return buf
    }

    fun queuePacket(player: ServerPlayerEntity, packet: SyncPacket) {
        playerQueues.compute(player) { _, queue ->
            (queue ?: ConcurrentLinkedQueue()).also { it.offer(packet) }
        }
    }

    fun getQueuedPackets(player: ServerPlayerEntity): List<SyncPacket> {
        val queue = playerQueues[player] ?: return emptyList()
        val packets = mutableListOf<SyncPacket>()
        
        while (queue.isNotEmpty()) {
            queue.poll()?.let { packets.add(it) }
        }
        
        return packets
    }

    fun hasQueuedPackets(player: ServerPlayerEntity): Boolean {
        return playerQueues[player]?.isNotEmpty() ?: false
    }

    fun getQueueSize(player: ServerPlayerEntity): Int {
        return playerQueues[player]?.size ?: 0
    }

    fun flushQueue(player: ServerPlayerEntity): List<PacketByteBuf> {
        val packets = getQueuedPackets(player)
        return packets.map { buildPacketBuffer(it) }
    }

    fun onPlayerDisconnect(player: ServerPlayerEntity) {
        playerQueues.remove(player)
    }

    fun cleanupStaleEntities(maxAgeMs: Long = 5000) {
        val currentTime = System.currentTimeMillis()
        lastSendTimes.entries.removeIf { currentTime - it.value > maxAgeMs }
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

    fun mergePackets(packets: List<SyncPacket>): SyncPacket {
        val allSnapshots = packets.flatMap { it.snapshots }
        return SyncPacket(allSnapshots)
    }

    fun optimizePackets(packets: List<SyncPacket>): List<SyncPacket> {
        if (packets.isEmpty()) return emptyList()
        
        val optimized = mutableListOf<SyncPacket>()
        var current = mutableListOf<EntitySyncSnapshot>()
        
        packets.forEach { packet ->
            packet.snapshots.forEach { snapshot ->
                if (current.size >= config.batchSize) {
                    optimized.add(SyncPacket(current))
                    current = mutableListOf()
                }
                current.add(snapshot)
            }
        }
        
        if (current.isNotEmpty()) {
            optimized.add(SyncPacket(current))
        }
        
        return optimized
    }
}