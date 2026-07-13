package com.molten.optimization

import com.molten.optimization.config.MoltenConfig
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object MoltenMod : ModInitializer {
    const val MOD_ID = "molten"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        LOGGER.info("Initializing Molten v{}", "1.0.0")
        LOGGER.info("macOS Graphics Rendering Optimization")

        MoltenConfig.load()

        ServerLifecycleEvents.SERVER_STARTING.register {
            LOGGER.info("Molten loaded successfully")
        }
    }
}
