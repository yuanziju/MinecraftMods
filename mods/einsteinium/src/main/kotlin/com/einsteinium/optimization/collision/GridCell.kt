package com.einsteinium.optimization.collision

import net.minecraft.entity.Entity

class GridCell {
    val groundEntities = mutableListOf<Entity>()
    val undergroundEntities = mutableListOf<Entity>()
    val skyEntities = mutableListOf<Entity>()
    val staticEntities = mutableListOf<Entity>()

    fun isEmpty(): Boolean {
        return groundEntities.isEmpty() && undergroundEntities.isEmpty() && skyEntities.isEmpty() && staticEntities.isEmpty()
    }

    fun getTotalCount(): Int {
        return groundEntities.size + undergroundEntities.size + skyEntities.size + staticEntities.size
    }
}