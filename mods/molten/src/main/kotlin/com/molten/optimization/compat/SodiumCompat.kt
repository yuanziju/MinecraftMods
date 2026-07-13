package com.molten.optimization.compat

import com.molten.optimization.MoltenMod
import com.molten.optimization.config.MoltenConfig

object SodiumCompat {
    fun apply() {
        MoltenConfig.commandBatch = false
        MoltenConfig.commandParallel = false
        MoltenMod.LOGGER.info("Disabled batch and parallel command submission due to Sodium compatibility")
    }
}
