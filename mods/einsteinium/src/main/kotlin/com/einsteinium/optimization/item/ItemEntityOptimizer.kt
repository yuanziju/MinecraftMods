package com.einsteinium.optimization.item

import com.einsteinium.optimization.EinsteiniumMod
import net.minecraft.entity.ItemEntity
import net.minecraft.util.math.BlockPos

class ItemEntityOptimizer {
    fun tryMerge(a: ItemEntity, b: ItemEntity): Boolean {
        if (!EinsteiniumMod.config.item.preserveNbt) {
            return tryMergeIgnoreNbt(a, b)
        }

        return tryMergePreserveNbt(a, b)
    }

    fun simplifyPhysics(itemEntity: ItemEntity) {
        if (!EinsteiniumMod.config.item.simplifyPhysics) return

        if (isStatic(itemEntity)) {
            itemEntity.velocity = net.minecraft.util.math.Vec3d.ZERO
        }
    }

    fun enforceDensityLimit(pos: BlockPos) {
        val config = EinsteiniumMod.config.item
        if (config.densityLimit <= 0) return

        val world = pos.world
        if (world.isClient) return

        val nearbyItems = world.getEntitiesByClass(ItemEntity::class.java,
            net.minecraft.util.math.Box(pos).expand(config.mergeRange.toDouble()),
            { true })

        if (nearbyItems.size > config.densityLimit) {
            val excess = nearbyItems.size - config.densityLimit
            val toRemove = nearbyItems.take(excess)

            for (item in toRemove) {
                accelerateDespawn(item, config.despawnAcceleration)
            }
        }
    }

    fun accelerateDespawn(itemEntity: ItemEntity, factor: Int) {
        val config = EinsteiniumMod.config.item
        itemEntity.age += factor * 20
    }

    private fun tryMergePreserveNbt(a: ItemEntity, b: ItemEntity): Boolean {
        if (a == b || a.isRemoved || b.isRemoved) return false

        val stackA = a.stack
        val stackB = b.stack

        if (stackA.isEmpty || stackB.isEmpty) return false
        if (!stackA.isOf(stackB.item)) return false

        val tagA = stackA.tag
        val tagB = stackB.tag

        if ((tagA == null) != (tagB == null)) return false
        if (tagA != null && !tagA.equals(tagB)) return false

        val totalCount = stackA.count + stackB.count
        val maxCount = stackA.maxCount

        if (totalCount <= maxCount) {
            stackA.count = totalCount
            b.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED)
            return true
        }

        return false
    }

    private fun tryMergeIgnoreNbt(a: ItemEntity, b: ItemEntity): Boolean {
        if (a == b || a.isRemoved || b.isRemoved) return false

        val stackA = a.stack
        val stackB = b.stack

        if (stackA.isEmpty || stackB.isEmpty) return false
        if (!stackA.isOf(stackB.item)) return false

        val totalCount = stackA.count + stackB.count
        val maxCount = stackA.maxCount

        if (totalCount <= maxCount) {
            stackA.count = totalCount
            b.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED)
            return true
        }

        return false
    }

    private fun isStatic(itemEntity: ItemEntity): Boolean {
        return itemEntity.isOnGround && itemEntity.velocity.lengthSquared() < 0.001
    }
}