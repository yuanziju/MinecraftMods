package com.einsteinium.optimization.config

import com.einsteinium.optimization.EinsteiniumMod
import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import me.shedaniel.clothconfig2.api.ConfigBuilder
import me.shedaniel.clothconfig2.api.ConfigCategory
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

object ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent ->
            createConfigScreen(parent)
        }
    }

    private fun createConfigScreen(parent: Screen?): Screen {
        val config = EinsteiniumMod.config
        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Text.literal("Einsteinium Configuration"))

        val entryBuilder = builder.entryBuilder()

        buildTickCategory(builder, entryBuilder, config)
        buildCollisionCategory(builder, entryBuilder, config)
        buildRenderingCategory(builder, entryBuilder, config)
        buildItemCategory(builder, entryBuilder, config)
        buildSpawnCategory(builder, entryBuilder, config)
        buildSyncCategory(builder, entryBuilder, config)
        buildSaveCategory(builder, entryBuilder, config)
        buildMemoryCategory(builder, entryBuilder, config)
        buildDebugCategory(builder, entryBuilder, config)

        builder.setSavingRunnable {
            config.save()
        }

        return builder.build()
    }

    private fun buildTickCategory(builder: ConfigBuilder, entryBuilder: ConfigEntryBuilder, config: EinsteiniumConfig) {
        val category = builder.getOrCreateCategory(Text.literal("Tick Optimization"))
        addIntEntry(category, entryBuilder, "Distance Tier 1", "tick.distance_tier1", config.tick::distanceTier1, 0, 512)
        addIntEntry(category, entryBuilder, "Distance Tier 2", "tick.distance_tier2", config.tick::distanceTier2, 0, 512)
        addIntEntry(category, entryBuilder, "Distance Tier 3", "tick.distance_tier3", config.tick::distanceTier3, 0, 1024)
        addBooleanEntry(category, entryBuilder, "Skip Static AI", "tick.skip_static_ai", config.tick::skipStaticAI)
        addBooleanEntry(category, entryBuilder, "Preserve Physics", "tick.preserve_physics", config.tick::preservePhysics)
    }

    private fun buildCollisionCategory(builder: ConfigBuilder, entryBuilder: ConfigEntryBuilder, config: EinsteiniumConfig) {
        val category = builder.getOrCreateCategory(Text.literal("Collision Optimization"))
        addIntEntry(category, entryBuilder, "Grid Size", "collision.grid_size", config.collision::gridSize, 4, 64)
        addIntEntry(category, entryBuilder, "Height Layers", "collision.height_layers", config.collision::heightLayers, 1, 10)
        addBooleanEntry(category, entryBuilder, "Skip Static", "collision.skip_static", config.collision::skipStatic)
        addIntEntry(category, entryBuilder, "Density Limit", "collision.density_limit", config.collision::densityLimit, 1, 128)
        addBooleanEntry(category, entryBuilder, "Enable AABB Cull", "collision.enable_aabb_cull", config.collision::enableAabbCull)
    }

    private fun buildRenderingCategory(builder: ConfigBuilder, entryBuilder: ConfigEntryBuilder, config: EinsteiniumConfig) {
        val category = builder.getOrCreateCategory(Text.literal("Rendering Optimization"))
        addBooleanEntry(category, entryBuilder, "Enable Instancing", "rendering.enable_instancing", config.rendering::enableInstancing)
        addBooleanEntry(category, entryBuilder, "Frustum Cull", "rendering.frustum_cull", config.rendering::frustumCull)
        addBooleanEntry(category, entryBuilder, "Enable LOD", "rendering.enable_lod", config.rendering::enableLod)
        addIntEntry(category, entryBuilder, "LOD Distance 1", "rendering.lod_distance1", config.rendering::lodDistance1, 0, 512)
        addIntEntry(category, entryBuilder, "LOD Distance 2", "rendering.lod_distance2", config.rendering::lodDistance2, 0, 512)
    }

    private fun buildItemCategory(builder: ConfigBuilder, entryBuilder: ConfigEntryBuilder, config: EinsteiniumConfig) {
        val category = builder.getOrCreateCategory(Text.literal("Item Optimization"))
        addIntEntry(category, entryBuilder, "Merge Range", "item.merge_range", config.item::mergeRange, 1, 32)
        addBooleanEntry(category, entryBuilder, "Preserve NBT", "item.preserve_nbt", config.item::preserveNbt)
        addBooleanEntry(category, entryBuilder, "Simplify Physics", "item.simplify_physics", config.item::simplifyPhysics)
        addIntEntry(category, entryBuilder, "Density Limit", "item.density_limit", config.item::densityLimit, 1, 256)
        addIntEntry(category, entryBuilder, "Despawn Acceleration", "item.despawn_acceleration", config.item::despawnAcceleration, 1, 10)
    }

    private fun buildSpawnCategory(builder: ConfigBuilder, entryBuilder: ConfigEntryBuilder, config: EinsteiniumConfig) {
        val category = builder.getOrCreateCategory(Text.literal("Spawn Optimization"))
        addIntEntry(category, entryBuilder, "Density Limit", "spawn.density_limit", config.spawn::densityLimit, 1, 128)
        addDoubleEntry(category, entryBuilder, "Distance Factor", "spawn.distance_factor", config.spawn::distanceFactor, 0.0, 1.0)
        addBooleanEntry(category, entryBuilder, "Cooldown Adjust", "spawn.cooldown_adjust", config.spawn::cooldownAdjust)
    }

    private fun buildSyncCategory(builder: ConfigBuilder, entryBuilder: ConfigEntryBuilder, config: EinsteiniumConfig) {
        val category = builder.getOrCreateCategory(Text.literal("Network Sync"))
        addBooleanEntry(category, entryBuilder, "Incremental", "sync.incremental", config.sync::incremental)
        addIntEntry(category, entryBuilder, "Distance Tier 1", "sync.distance_tier1", config.sync::distanceTier1, 0, 256)
        addIntEntry(category, entryBuilder, "Distance Tier 2", "sync.distance_tier2", config.sync::distanceTier2, 0, 256)
        addIntEntry(category, entryBuilder, "Batch Size", "sync.batch_size", config.sync::batchSize, 1, 64)
    }

    private fun buildSaveCategory(builder: ConfigBuilder, entryBuilder: ConfigEntryBuilder, config: EinsteiniumConfig) {
        val category = builder.getOrCreateCategory(Text.literal("Persistence"))
        addIntEntry(category, entryBuilder, "Delay Ticks", "save.delay_ticks", config.save::delayTicks, 0, 100)
        addBooleanEntry(category, entryBuilder, "Differential", "save.differential", config.save::differential)
        addBooleanEntry(category, entryBuilder, "Compress", "save.compress", config.save::compress)
    }

    private fun buildMemoryCategory(builder: ConfigBuilder, entryBuilder: ConfigEntryBuilder, config: EinsteiniumConfig) {
        val category = builder.getOrCreateCategory(Text.literal("Memory Optimization"))
        addBooleanEntry(category, entryBuilder, "Enable Pools", "memory.enable_pools", config.memory::enablePools)
        addIntEntry(category, entryBuilder, "Pool Size", "memory.pool_size", config.memory::poolSize, 64, 8192)
    }

    private fun buildDebugCategory(builder: ConfigBuilder, entryBuilder: ConfigEntryBuilder, config: EinsteiniumConfig) {
        val category = builder.getOrCreateCategory(Text.literal("Debug"))
        addBooleanEntry(category, entryBuilder, "HUD", "debug.hud", config.debug::hud)
        addBooleanEntry(category, entryBuilder, "Commands", "debug.commands", config.debug::commands)
        addBooleanEntry(category, entryBuilder, "Visualization", "debug.visualization", config.debug::visualization)
    }

    private fun addIntEntry(category: ConfigCategory, entryBuilder: ConfigEntryBuilder, name: String, tooltip: String, getter: () -> Int, min: Int, max: Int) {
        category.addEntry(entryBuilder.startIntField(Text.literal(name), getter())
            .setTooltip(Text.literal(tooltip))
            .setMin(min)
            .setMax(max)
            .setSaveConsumer { getter as (Int) -> Unit; getter(it) }
            .build())
    }

    private fun addBooleanEntry(category: ConfigCategory, entryBuilder: ConfigEntryBuilder, name: String, tooltip: String, getter: () -> Boolean) {
        category.addEntry(entryBuilder.startBooleanToggle(Text.literal(name), getter())
            .setTooltip(Text.literal(tooltip))
            .setSaveConsumer { getter as (Boolean) -> Unit; getter(it) }
            .build())
    }

    private fun addDoubleEntry(category: ConfigCategory, entryBuilder: ConfigEntryBuilder, name: String, tooltip: String, getter: () -> Double, min: Double, max: Double) {
        category.addEntry(entryBuilder.startDoubleField(Text.literal(name), getter())
            .setTooltip(Text.literal(tooltip))
            .setMin(min)
            .setMax(max)
            .setSaveConsumer { getter as (Double) -> Unit; getter(it) }
            .build())
    }
}