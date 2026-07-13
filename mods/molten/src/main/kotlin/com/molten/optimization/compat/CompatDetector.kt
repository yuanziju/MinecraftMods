package com.molten.optimization.compat

import com.molten.optimization.MoltenMod
import net.fabricmc.loader.api.FabricLoader

object CompatDetector {
    private var sodiumDetected = false
    private var irisDetected = false

    fun detect() {
        sodiumDetected = FabricLoader.getInstance().isModLoaded("sodium")
        irisDetected = FabricLoader.getInstance().isModLoaded("iris")

        if (sodiumDetected) {
            MoltenMod.LOGGER.info("Sodium detected, applying compatibility mode")
            SodiumCompat.apply()
        }

        if (irisDetected) {
            MoltenMod.LOGGER.info("Iris detected")
        }
    }

    fun isSodiumLoaded(): Boolean = sodiumDetected

    fun isIrisLoaded(): Boolean = irisDetected
}
