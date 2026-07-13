package com.einsteinium.optimization.persistence

import com.einsteinium.optimization.EinsteiniumMod
import net.minecraft.entity.Entity
import net.minecraft.nbt.CompoundTag
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

class PersistenceOptimizer {
    private val entitySnapshots = mutableMapOf<Entity, CompoundTag>()

    fun queueSave(entity: Entity) {
        AsyncSaveQueue.add(entity)
    }

    fun captureSnapshot(entity: Entity) {
        val tag = CompoundTag()
        entity.save(tag)
        entitySnapshots[entity] = tag.copy()
    }

    fun diffNBT(current: CompoundTag, previous: CompoundTag): CompoundTag {
        if (!EinsteiniumMod.config.save.differential) return current

        val diff = CompoundTag()

        for (key in current.allKeys) {
            val currentValue = current.get(key)
            val previousValue = previous.get(key)

            if (currentValue == null && previousValue != null) {
                diff.put(key, CompoundTag())
            } else if (currentValue != null && previousValue == null) {
                diff.put(key, currentValue)
            } else if (currentValue != null && !currentValue.equals(previousValue)) {
                diff.put(key, currentValue)
            }
        }

        return diff
    }

    fun compressNBT(nbt: CompoundTag): ByteArray {
        if (!EinsteiniumMod.config.save.compress) {
            return nbt.asByteArray()
        }

        val rawBytes = nbt.asByteArray()
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(rawBytes)
        deflater.finish()

        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            output.write(buffer, 0, count)
        }
        deflater.end()

        return output.toByteArray()
    }

    fun decompressNBT(compressed: ByteArray): CompoundTag {
        val inflater = Inflater()
        inflater.setInput(compressed)

        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            output.write(buffer, 0, count)
        }
        inflater.end()

        return CompoundTag().apply {
            load(output.toByteArray())
        }
    }

    fun getPreviousSnapshot(entity: Entity): CompoundTag? {
        return entitySnapshots[entity]
    }

    fun updateSnapshot(entity: Entity, tag: CompoundTag) {
        entitySnapshots[entity] = tag.copy()
    }

    fun removeSnapshot(entity: Entity) {
        entitySnapshots.remove(entity)
    }

    fun clearAllSnapshots() {
        entitySnapshots.clear()
    }
}