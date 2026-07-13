package com.einsteinium.optimization.chunk

import com.einsteinium.optimization.EinsteiniumMod
import com.einsteinium.optimization.config.EinsteiniumConfig
import net.minecraft.entity.Entity
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.chunk.Chunk
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object ChunkEntityManager {
    private val entityToChunk = ConcurrentHashMap<Entity, Chunk>()
    private val chunkToEntities = ConcurrentHashMap<Chunk, CopyOnWriteArrayList<Entity>>()
    private val pendingUnloadQueue = CopyOnWriteArrayList<Chunk>()
    private var unloadTickCounter = 0L

    fun registerEntity(entity: Entity, chunk: Chunk) {
        val mainChunk = determineMainChunk(entity)
        entityToChunk[entity] = mainChunk

        chunkToEntities.computeIfAbsent(mainChunk) { CopyOnWriteArrayList() }.add(entity)
    }

    fun unregisterEntity(entity: Entity) {
        val mainChunk = entityToChunk.remove(entity)
        mainChunk?.let { chunk ->
            chunkToEntities[chunk]?.remove(entity)
        }
    }

    fun getMainChunk(entity: Entity): Chunk? {
        return entityToChunk[entity]
    }

    fun isInMainChunk(entity: Entity): Boolean {
        val mainChunk = getMainChunk(entity) ?: return false
        val currentChunk = entity.world.getChunk(entity.blockPos)
        return mainChunk.pos == currentChunk.pos
    }

    fun updateEntityPosition(entity: Entity) {
        val currentMainChunk = entityToChunk[entity]
        val newMainChunk = determineMainChunk(entity)

        if (currentMainChunk != null && currentMainChunk != newMainChunk) {
            chunkToEntities[currentMainChunk]?.remove(entity)
            chunkToEntities.computeIfAbsent(newMainChunk) { CopyOnWriteArrayList() }.add(entity)
            entityToChunk[entity] = newMainChunk
        }
    }

    private fun determineMainChunk(entity: Entity): Chunk {
        val centerPos = entity.blockPos
        return entity.world.getChunk(centerPos)
    }

    fun queueChunkForUnload(chunk: Chunk) {
        if (!pendingUnloadQueue.contains(chunk)) {
            pendingUnloadQueue.add(chunk)
        }
    }

    fun processUnloadTick(): Int {
        if (pendingUnloadQueue.isEmpty()) return 0
        if (!EinsteiniumConfig.chunk.enableBatchUnload) return 0

        val config = EinsteiniumConfig.chunk
        val chunksPerTick = config.chunksPerUnloadTick

        var processedCount = 0
        var index = 0

        while (index < pendingUnloadQueue.size && processedCount < chunksPerTick) {
            val chunk = pendingUnloadQueue[index]

            if (isChunkSafeToUnload(chunk)) {
                batchSaveAndRemoveEntities(chunk)
                pendingUnloadQueue.removeAt(index)
                processedCount++
            } else {
                index++
            }
        }

        unloadTickCounter++
        return processedCount
    }

    private fun isChunkSafeToUnload(chunk: Chunk): Boolean {
        val entities = chunkToEntities[chunk] ?: return true

        for (entity in entities) {
            if (!entity.isRemoved && !entity.isDead) {
                val currentChunk = entity.world.getChunk(entity.blockPos)
                if (currentChunk.pos == chunk.pos) {
                    return false
                }
            }
        }

        return true
    }

    private fun batchSaveAndRemoveEntities(chunk: Chunk) {
        val entities = chunkToEntities.remove(chunk) ?: return
        val savedTags = mutableListOf<CompoundTag>()

        entities.forEach { entity ->
            if (!entity.isRemoved && !entity.isDead) {
                val tag = CompoundTag()
                entity.saveNbt(tag)
                savedTags.add(tag)
                unregisterEntity(entity)
            }
        }

        if (savedTags.isNotEmpty()) {
            EinsteiniumMod.LOGGER.debug("[Einsteinium] Saved ${savedTags.size} entities from chunk ${chunk.pos}")
        }
    }

    fun getEntitiesInChunk(chunk: Chunk): List<Entity> {
        return chunkToEntities[chunk]?.toList() ?: emptyList()
    }

    fun getEntityCountInChunk(chunk: Chunk): Int {
        return chunkToEntities[chunk]?.size ?: 0
    }

    fun clear() {
        entityToChunk.clear()
        chunkToEntities.clear()
        pendingUnloadQueue.clear()
        unloadTickCounter = 0L
    }

    fun getPendingUnloadCount(): Int {
        return pendingUnloadQueue.size
    }

    fun getUnloadProgress(): Double {
        return if (unloadTickCounter == 0L) 0.0 else {
            val total = pendingUnloadQueue.size.toDouble() + unloadTickCounter.toDouble()
            (unloadTickCounter.toDouble() / total) * 100.0
        }
    }

    fun shouldProcessEntity(entity: Entity): Boolean {
        val mainChunk = getMainChunk(entity)
        if (mainChunk == null) {
            registerEntity(entity, determineMainChunk(entity))
            return true
        }

        val currentChunk = entity.world.getChunk(entity.blockPos)

        if (currentChunk.pos != mainChunk.pos) {
            val dx = currentChunk.pos.x - mainChunk.pos.x
            val dz = currentChunk.pos.z - mainChunk.pos.z

            return dx * dx + dz * dz <= 1
        }

        return true
    }
}