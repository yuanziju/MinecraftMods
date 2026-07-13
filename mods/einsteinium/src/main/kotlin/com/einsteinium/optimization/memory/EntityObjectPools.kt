package com.einsteinium.optimization.memory

import com.einsteinium.optimization.EinsteiniumMod
import net.minecraft.entity.Entity
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.level.pathfinder.Node

object EntityObjectPools {
    lateinit var pathNodePool: ObjectPool<Node>
        private set

    lateinit var aabbPool: ObjectPool<Box>
        private set

    lateinit var collisionResultPool: ObjectPool<CollisionResult>
        private set

    lateinit var entityListPool: ObjectPool<MutableList<Entity>>
        private set

    lateinit var nbtPool: ObjectPool<CompoundTag>
        private set

    lateinit var vec3Pool: ObjectPool<Vec3d>
        private set

    fun initialize() {
        if (!EinsteiniumMod.config.memory.enablePools) return

        val poolSize = EinsteiniumMod.config.memory.poolSize

        pathNodePool = ObjectPool(
            creator = { Node(BlockPos.ZERO, 0, 0) },
            resetter = { node ->
                node.x = 0
                node.y = 0
                node.z = 0
                node.costMalus = 0.0f
                node.distanceToTarget = Float.MAX_VALUE
                node.distanceToNeighbor = Float.MAX_VALUE
                node.visited = false
                node.closed = false
                node.parent = null
                node.type = null
            },
            maxSize = poolSize * 2
        )

        aabbPool = ObjectPool(
            creator = { Box(0.0, 0.0, 0.0, 0.0, 0.0, 0.0) },
            resetter = { box ->
                box.minX = 0.0
                box.minY = 0.0
                box.minZ = 0.0
                box.maxX = 0.0
                box.maxY = 0.0
                box.maxZ = 0.0
            },
            maxSize = poolSize
        )

        collisionResultPool = ObjectPool(
            creator = { CollisionResult() },
            resetter = { result ->
                result.reset()
            },
            maxSize = poolSize
        )

        entityListPool = ObjectPool(
            creator = { mutableListOf() },
            resetter = { it.clear() },
            maxSize = poolSize / 4
        )

        nbtPool = ObjectPool(
            creator = { CompoundTag() },
            resetter = { it.clear() },
            maxSize = poolSize / 2
        )

        vec3Pool = ObjectPool(
            creator = { Vec3d(0.0, 0.0, 0.0) },
            resetter = { vec ->
                vec.x = 0.0
                vec.y = 0.0
                vec.z = 0.0
            },
            maxSize = poolSize * 2
        )

        EinsteiniumMod.LOGGER.info("[Einsteinium] 对象池初始化完成 - 总大小: ${poolSize * 6}")
    }

    fun shutdown() {
        pathNodePool.clear()
        aabbPool.clear()
        collisionResultPool.clear()
        entityListPool.clear()
        nbtPool.clear()
        vec3Pool.clear()
    }

    fun dumpStats() {
        EinsteiniumMod.LOGGER.info("[Einsteinium] 对象池统计:")
        EinsteiniumMod.LOGGER.info("  路径点池: ${pathNodePool.getStats()}")
        EinsteiniumMod.LOGGER.info("  AABB池: ${aabbPool.getStats()}")
        EinsteiniumMod.LOGGER.info("  碰撞结果池: ${collisionResultPool.getStats()}")
        EinsteiniumMod.LOGGER.info("  实体列表池: ${entityListPool.getStats()}")
        EinsteiniumMod.LOGGER.info("  NBT池: ${nbtPool.getStats()}")
        EinsteiniumMod.LOGGER.info("  向量池: ${vec3Pool.getStats()}")
    }
}

class CollisionResult {
    var collided = false
    var entity: Entity? = null
    var hitPos: Vec3d? = null
    var hitNormal: Vec3d? = null
    var penetration = 0.0

    fun reset() {
        collided = false
        entity = null
        hitPos = null
        hitNormal = null
        penetration = 0.0
    }
}