package com.einsteinium.optimization.item

import com.einsteinium.optimization.EinsteiniumMod
import net.minecraft.entity.ItemEntity

class ItemMergeLogic {
    fun processNearbyItems(itemEntity: ItemEntity) {
        val config = EinsteiniumMod.config.item
        if (config.mergeRange <= 0) return

        val world = itemEntity.world
        if (world.isClient) return

        val nearbyItems = world.getEntitiesByClass(ItemEntity::class.java,
            net.minecraft.util.math.Box(itemEntity.blockPos).expand(config.mergeRange.toDouble()),
            { it != itemEntity && !it.isRemoved })

        for (other in nearbyItems) {
            if (EinsteiniumMod.itemOptimizer.tryMerge(itemEntity, other)) {
                break
            }
        }
    }
}