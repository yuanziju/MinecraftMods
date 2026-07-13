package com.einsteinium.optimization.spawn

import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos

object DensityTracker {
    private val densityMap = mutableMapOf<Pair<Int, Int>, MutableMap<net.minecraft.entity.EntityType<*>, Int>>()

    fun isOverLimit(pos: BlockPos, type: net.minecraft.entity.EntityType<*>, limit: Int): Boolean {
        val regionKey = getRegionKey(pos)
        val typeCount = densityMap.getOrElse(regionKey) { return false }.getOrDefault(type, 0)

        return typeCount >= limit
    }

    fun getDensity(pos: BlockPos): Int {
        val regionKey = getRegionKey(pos)
        val typeMap = densityMap.getOrElse(regionKey) { return 0 }

        return typeMap.values.sum()
    }

    fun addEntity(entity: Entity) {
        val regionKey = getRegionKey(entity.blockPos)
        val type = entity.type

        densityMap.getOrPut(regionKey) { mutableMapOf() }.merge(type, 1, Integer::sum)
    }

    fun removeEntity(entity: Entity) {
        val regionKey = getRegionKey(entity.blockPos)
        val type = entity.type

        val typeMap = densityMap[regionKey] ?: return
        val count = typeMap[type] ?: return

        if (count <= 1) {
            typeMap.remove(type)
            if (typeMap.isEmpty()) {
                densityMap.remove(regionKey)
            }
        } else {
            typeMap[type] = count - 1
        }
    }

    fun clear() {
        densityMap.clear()
    }

    private fun getRegionKey(pos: BlockPos): Pair<Int, Int> {
        return Pair(pos.x shr 4, pos.z shr 4)
    }
}