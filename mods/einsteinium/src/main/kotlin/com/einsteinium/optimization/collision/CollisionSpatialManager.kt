package com.einsteinium.optimization.collision

import com.einsteinium.optimization.EinsteiniumMod
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos

class CollisionSpatialManager {
    private val gridSize get() = EinsteiniumMod.config.collision.gridSize
    private val densityLimit get() = EinsteiniumMod.config.collision.densityLimit

    private val grid2D = mutableMapOf<Pair<Int, Int>, GridCell>()

    fun getPotentialCollisions(entity: Entity): List<Entity> {
        if (!EinsteiniumMod.config.collision.skipStatic) {
            return emptyList()
        }

        val gridPos = getGridPosition(entity.blockPos)
        val cell = grid2D.getOrPut(gridPos) { GridCell() }

        val heightLayer = getHeightLayer(entity.blockPos.y)
        val candidates = mutableListOf<Entity>()

        when (heightLayer) {
            HeightLayer.GROUND -> candidates.addAll(cell.groundEntities)
            HeightLayer.UNDERGROUND -> candidates.addAll(cell.undergroundEntities)
            HeightLayer.SKY -> candidates.addAll(cell.skyEntities)
        }

        if (!isStatic(entity)) {
            candidates.addAll(cell.staticEntities)
        }

        return candidates.filter { it != entity && !it.isRemoved }
    }

    fun checkDensityLimit(pos: BlockPos, entityType: net.minecraft.entity.EntityType<*>): Boolean {
        val gridPos = getGridPosition(pos)
        val cell = grid2D.getOrPut(gridPos) { GridCell() }

        val totalEntities = cell.groundEntities.size + cell.undergroundEntities.size + cell.skyEntities.size
        return totalEntities >= densityLimit
    }

    fun updateEntityPosition(entity: Entity) {
        val oldGridPos = getGridPosition(entity.lastBlockPos)
        val newGridPos = getGridPosition(entity.blockPos)

        if (oldGridPos != newGridPos) {
            removeEntityFromGrid(entity, oldGridPos)
            addEntityToGrid(entity, newGridPos)
        }
    }

    fun addEntity(entity: Entity) {
        val gridPos = getGridPosition(entity.blockPos)
        addEntityToGrid(entity, gridPos)
    }

    fun removeEntity(entity: Entity) {
        val gridPos = getGridPosition(entity.blockPos)
        removeEntityFromGrid(entity, gridPos)
    }

    private fun addEntityToGrid(entity: Entity, gridPos: Pair<Int, Int>) {
        val cell = grid2D.getOrPut(gridPos) { GridCell() }
        val heightLayer = getHeightLayer(entity.blockPos.y)

        if (isStatic(entity) && EinsteiniumMod.config.collision.skipStatic) {
            cell.staticEntities.add(entity)
        } else {
            when (heightLayer) {
                HeightLayer.GROUND -> cell.groundEntities.add(entity)
                HeightLayer.UNDERGROUND -> cell.undergroundEntities.add(entity)
                HeightLayer.SKY -> cell.skyEntities.add(entity)
            }
        }
    }

    private fun removeEntityFromGrid(entity: Entity, gridPos: Pair<Int, Int>) {
        val cell = grid2D[gridPos] ?: return

        cell.groundEntities.remove(entity)
        cell.undergroundEntities.remove(entity)
        cell.skyEntities.remove(entity)
        cell.staticEntities.remove(entity)

        if (cell.isEmpty()) {
            grid2D.remove(gridPos)
        }
    }

    private fun getGridPosition(pos: BlockPos): Pair<Int, Int> {
        return Pair(pos.x / gridSize, pos.z / gridSize)
    }

    private fun getHeightLayer(y: Int): HeightLayer {
        return when {
            y < 0 -> HeightLayer.UNDERGROUND
            y <= 64 -> HeightLayer.GROUND
            else -> HeightLayer.SKY
        }
    }

    private fun isStatic(entity: Entity): Boolean {
        return entity.velocity.lengthSquared() < 0.001 && !entity.isMoving
    }

    enum class HeightLayer {
        UNDERGROUND,
        GROUND,
        SKY
    }
}