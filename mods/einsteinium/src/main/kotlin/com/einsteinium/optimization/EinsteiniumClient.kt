package com.einsteinium.optimization

import com.einsteinium.optimization.rendering.FrustumCuller
import com.einsteinium.optimization.rendering.InstancedEntityRenderer
import com.einsteinium.optimization.rendering.LODManager
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents

object EinsteiniumClient : ClientModInitializer {
    lateinit var instancedRenderer: InstancedEntityRenderer
    lateinit var lodManager: LODManager
    lateinit var frustumCuller: FrustumCuller

    override fun onInitializeClient() {
        instancedRenderer = InstancedEntityRenderer()
        lodManager = LODManager()
        frustumCuller = FrustumCuller()

        WorldRenderEvents.END.register { context ->
            instancedRenderer.onWorldRenderEnd(context)
        }

        EinsteiniumMod.LOGGER.info("Einsteinium Client - 渲染优化模块初始化完成")
    }
}