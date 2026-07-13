package com.einsteinium.optimization.compat

import com.einsteinium.optimization.EinsteiniumMod

object LithiumCompat {
    fun handleLithiumConflict() {
        EinsteiniumMod.config.tick.skipStaticAI = false
        EinsteiniumMod.config.collision.skipStatic = false

        EinsteiniumMod.LOGGER.warn("[Einsteinium] Disabled conflicting optimizations due to Lithium presence")
        EinsteiniumMod.LOGGER.warn("[Einsteinium] Consider removing Lithium for full Einsteinium benefits")
    }
}