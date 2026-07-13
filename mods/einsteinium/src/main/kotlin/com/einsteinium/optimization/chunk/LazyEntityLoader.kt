package com.einsteinium.optimization.chunk

import com.einsteinium.optimization.EinsteiniumMod
import com.einsteinium.optimization.config.EinsteiniumConfig
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.chunk.Chunk
import java.util.*

class LazyEntityLoader {
    private data class PendingEntity(
        val chunk: Chunk,
        val tag: CompoundTag,
        val priority: Int,
        val timestamp: Long
    )

    private val pendingQueue = PriorityQueue(Comparator.comparingInt<PendingEntity> { it.priority }.reversed())
    private var loadTickCounter = 0L

    fun queueEntities(chunk: Chunk, entityTags: List<CompoundTag>) {
        val now = System.currentTimeMillis()
        entityTags.forEachIndexed { index, tag ->
            val priority = calculatePriority(chunk, index)
            pendingQueue.add(PendingEntity(chunk, tag, priority, now))
        }
    }

    private fun calculatePriority(chunk: Chunk, index: Int): Int {
        var priority = 100

        val world = chunk.world
        if (!world.isClientSide && world.players.isNotEmpty()) {
            val chunkCenter = chunk.pos.getCenterPos()
            var minDistance = Double.MAX_VALUE

            for (player in world.players) {
                val distance = player.distanceToSqr(chunkCenter.x.toDouble(), chunkCenter.y.toDouble(), chunkCenter.z.toDouble())
                if (distance < minDistance) {
                    minDistance = distance
                }
            }

            when {
                minDistance < 16 * 16 -> priority += 300
                minDistance < 32 * 32 -> priority += 200
                minDistance < 64 * 64 -> priority += 100
                minDistance > 128 * 128 -> priority -= 100
            }
        }

        if (index < 4) {
            priority += 50
        }

        return priority
    }

    fun processLoadTick(): Int {
        if (pendingQueue.isEmpty()) return 0
        if (!EinsteiniumConfig.chunk.enableLazyLoading) return 0

        val config = EinsteiniumConfig.chunk
        val entitiesPerTick = config.entitiesPerTick

        var loadedCount = 0
        var attempts = 0
        val maxAttempts = entitiesPerTick * 2

        while (pendingQueue.isNotEmpty() && loadedCount < entitiesPerTick && attempts < maxAttempts) {
            attempts++
            val pending = pendingQueue.poll() ?: continue

            if (loadEntity(pending.chunk, pending.tag)) {
                loadedCount++
            }
        }

        loadTickCounter++
        return loadedCount
    }

    private fun loadEntity(chunk: Chunk, tag: CompoundTag): Boolean {
        return try {
            val entityType = EntityType.byString(tag.getString("id"))
            if (entityType == null) {
                EinsteiniumMod.LOGGER.warn("[Einsteinium] Unknown entity type in chunk ${chunk.pos}")
                return false
            }

            val entity = entityType.create(chunk.world)
            if (entity == null) {
                EinsteiniumMod.LOGGER.warn("[Einsteinium] Failed to create entity ${tag.getString("id")}")
                return false
            }

            entity.readNbt(tag)
            chunk.world.addEntity(entity)

            ChunkEntityManager.registerEntity(entity, chunk)
            true
        } catch (e: Exception) {
            EinsteiniumMod.LOGGER.warn("[Einsteinium] Failed to load entity from chunk ${chunk.pos}", e)
            false
        }
    }

    fun hasPendingEntities(): Boolean {
        return pendingQueue.isNotEmpty()
    }

    fun getPendingCount(): Int {
        return pendingQueue.size
    }

    fun clear() {
        pendingQueue.clear()
        loadTickCounter = 0L
    }

    fun getLoadProgress(): Double {
        return if (loadTickCounter == 0L) 0.0 else {
            (loadTickCounter.toDouble() / (pendingQueue.size + loadTickCounter).toDouble()) * 100.0
        }
    }
}