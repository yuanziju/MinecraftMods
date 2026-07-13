package com.einsteinium.optimization.tick

import net.minecraft.entity.Entity

enum class EntityState {
    STATIC,
    MOVING,
    IN_COMBAT,
    PROJECTILE,
    PLAYER_CONTROLLED
}

class EntityStateMachine {
    private val entityStates = mutableMapOf<Int, EntityState>()

    fun getState(entity: Entity): EntityState {
        val id = entity.id
        return entityStates.getOrPut(id) { determineState(entity) }
    }

    fun updateState(entity: Entity) {
        val id = entity.id
        val newState = determineState(entity)
        entityStates[id] = newState
    }

    fun removeEntity(entity: Entity) {
        entityStates.remove(entity.id)
    }

    fun clear() {
        entityStates.clear()
    }

    private fun determineState(entity: Entity): EntityState {
        if (entity.isPlayer || entity.isControlledByLocalInstance) {
            return EntityState.PLAYER_CONTROLLED
        }

        if (entity.type.isProjectile) {
            return EntityState.PROJECTILE
        }

        if (isInCombat(entity)) {
            return EntityState.IN_COMBAT
        }

        if (isMoving(entity)) {
            return EntityState.MOVING
        }

        return EntityState.STATIC
    }

    private fun isMoving(entity: Entity): Boolean {
        return entity.velocity.lengthSquared() > 0.001 || entity.isMoving
    }

    private fun isInCombat(entity: Entity): Boolean {
        if (entity is net.minecraft.entity.mob.MobEntity) {
            return entity.isInCombat
        }
        return false
    }
}