package com.einsteinium.optimization.compat

import com.einsteinium.optimization.EinsteiniumMod
import net.fabricmc.loader.api.FabricLoader

object CompatDetector {
    var lithiumPresent = false
        private set

    var sodiumPresent = false
        private set

    fun detect() {
        lithiumPresent = FabricLoader.getInstance().isModLoaded("lithium")
        sodiumPresent = FabricLoader.getInstance().isModLoaded("sodium")

        if (lithiumPresent) {
            EinsteiniumMod.LOGGER.warn("[Einsteinium] Lithium detected! Some optimizations may conflict.")
            LithiumCompat.handleLithiumConflict()
        }

        if (sodiumPresent) {
            EinsteiniumMod.LOGGER.info("[Einsteinium] Sodium detected, rendering optimizations compatible.")
        }
    }
}