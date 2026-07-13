package com.molten.optimization

import com.molten.optimization.backend.RenderBackendFactory
import com.molten.optimization.compat.CompatDetector
import com.molten.optimization.debug.DebugSystem
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents

object MoltenClient : ClientModInitializer {
    override fun onInitializeClient() {
        MoltenMod.LOGGER.info("Initializing Molten Client")

        CompatDetector.detect()

        RenderBackendFactory.initialize()

        ClientLifecycleEvents.CLIENT_STARTED.register {
            MoltenMod.LOGGER.info("Molten Client loaded successfully")
            MoltenMod.LOGGER.info("Using backend: ${RenderBackendFactory.currentBackend}")
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            DebugSystem.disable()
            RenderBackendFactory.destroy()
        }
    }
}
