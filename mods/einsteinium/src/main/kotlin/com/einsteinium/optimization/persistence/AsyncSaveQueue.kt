package com.einsteinium.optimization.persistence

import com.einsteinium.optimization.EinsteiniumMod
import net.minecraft.entity.Entity
import net.minecraft.nbt.CompoundTag
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object AsyncSaveQueue {
    private val queue = ConcurrentLinkedQueue<Entity>()
    private val executor: ExecutorService = Executors.newFixedThreadPool(2)
    private val tickCounter = AtomicInteger(0)
    private val optimization = PersistenceOptimizer()

    fun add(entity: Entity) {
        if (!EinsteiniumMod.config.save.differential) return

        if (!queue.contains(entity)) {
            queue.add(entity)
        }
    }

    fun process() {
        val config = EinsteiniumMod.config.save
        tickCounter.incrementAndGet()

        if (tickCounter.get() >= config.delayTicks || queue.size >= 100) {
            tickCounter.set(0)
            flushQueue()
        }
    }

    fun flushQueue() {
        if (queue.isEmpty()) return

        val toSave = mutableListOf<Entity>()
        var entity: Entity? = queue.poll()
        while (entity != null) {
            toSave.add(entity)
            entity = queue.poll()
        }

        executor.submit {
            for (entity in toSave) {
                if (!entity.isRemoved && entity.level != null && !entity.level.isClientSide) {
                    try {
                        saveEntity(entity)
                    } catch (e: Exception) {
                        EinsteiniumMod.LOGGER.warn("[Einsteinium] Failed to save entity ${entity.uuid}", e)
                    }
                }
            }
        }
    }

    private fun saveEntity(entity: Entity) {
        val currentTag = CompoundTag()
        entity.save(currentTag)

        val previousTag = optimization.getPreviousSnapshot(entity)

        val diff = if (previousTag != null) {
            optimization.diffNBT(currentTag, previousTag)
        } else {
            currentTag
        }

        if (diff.allKeys.isNotEmpty()) {
            optimization.updateSnapshot(entity, currentTag)
        }
    }

    fun clear() {
        queue.clear()
        optimization.clearAllSnapshots()
    }

    fun shutdown() {
        flushQueue()
        executor.shutdown()
    }

    fun getQueueSize(): Int {
        return queue.size
    }
}