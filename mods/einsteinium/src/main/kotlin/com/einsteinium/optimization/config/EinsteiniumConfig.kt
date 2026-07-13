package com.einsteinium.optimization.config

import com.einsteinium.optimization.EinsteiniumMod
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Jankson
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.JsonObject
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.JsonPrimitive
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.io.FileReader
import java.io.FileWriter

object EinsteiniumConfig {
    private val configFile = File(FabricLoader.getInstance().configDir.toFile(), "einsteinium.json")
    private val jankson = Jankson.builder().build()

    var tick = TickConfig()
    var collision = CollisionConfig()
    var rendering = RenderingConfig()
    var item = ItemConfig()
    var spawn = SpawnConfig()
    var sync = SyncConfig()
    var save = SaveConfig()
    var memory = MemoryConfig()
    var chunk = ChunkConfig()
    var debug = DebugConfig()

    fun load(): EinsteiniumConfig {
        if (!configFile.exists()) {
            save()
            return this
        }

        try {
            FileReader(configFile).use { reader ->
                val json = jankson.load(reader) as JsonObject

                tick.distanceTier1 = json.getInt("tick.distance_tier1", 128)
                tick.distanceTier2 = json.getInt("tick.distance_tier2", 256)
                tick.distanceTier3 = json.getInt("tick.distance_tier3", 512)
                tick.skipStaticAI = json.getBoolean("tick.skip_static_ai", true)
                tick.preservePhysics = json.getBoolean("tick.preserve_physics", true)

                collision.gridSize = json.getInt("collision.grid_size", 16)
                collision.heightLayers = json.getInt("collision.height_layers", 3)
                collision.skipStatic = json.getBoolean("collision.skip_static", true)
                collision.densityLimit = json.getInt("collision.density_limit", 32)
                collision.enableAabbCull = json.getBoolean("collision.enable_aabb_cull", true)

                rendering.enableInstancing = json.getBoolean("rendering.enable_instancing", true)
                rendering.frustumCull = json.getBoolean("rendering.frustum_cull", true)
                rendering.enableLod = json.getBoolean("rendering.enable_lod", true)
                rendering.lodDistance1 = json.getInt("rendering.lod_distance1", 64)
                rendering.lodDistance2 = json.getInt("rendering.lod_distance2", 128)

                item.mergeRange = json.getInt("item.merge_range", 8)
                item.preserveNbt = json.getBoolean("item.preserve_nbt", true)
                item.simplifyPhysics = json.getBoolean("item.simplify_physics", true)
                item.densityLimit = json.getInt("item.density_limit", 64)
                item.despawnAcceleration = json.getInt("item.despawn_acceleration", 2)

                spawn.densityLimit = json.getInt("spawn.density_limit", 16)
                spawn.distanceFactor = json.getDouble("spawn.distance_factor", 0.5)
                spawn.cooldownAdjust = json.getBoolean("spawn.cooldown_adjust", true)

                sync.incremental = json.getBoolean("sync.incremental", true)
                sync.distanceTier1 = json.getInt("sync.distance_tier1", 64)
                sync.distanceTier2 = json.getInt("sync.distance_tier2", 128)
                sync.batchSize = json.getInt("sync.batch_size", 16)

                save.delayTicks = json.getInt("save.delay_ticks", 20)
                save.differential = json.getBoolean("save.differential", true)
                save.compress = json.getBoolean("save.compress", true)

                memory.enablePools = json.getBoolean("memory.enable_pools", true)
                memory.poolSize = json.getInt("memory.pool_size", 1024)

                chunk.entitiesPerTick = json.getInt("chunk.entities_per_tick", 8)
                chunk.chunksPerUnloadTick = json.getInt("chunk.chunks_per_unload_tick", 4)
                chunk.enableLazyLoading = json.getBoolean("chunk.enable_lazy_loading", true)
                chunk.enableBatchUnload = json.getBoolean("chunk.enable_batch_unload", true)

                debug.hud = json.getBoolean("debug.hud", false)
                debug.commands = json.getBoolean("debug.commands", false)
                debug.visualization = json.getBoolean("debug.visualization", false)
            }
        } catch (e: Exception) {
            EinsteiniumMod.LOGGER.warn("[Einsteinium] Failed to load config, using defaults", e)
            save()
        }

        return this
    }

    fun save() {
        try {
            val json = JsonObject().apply {
                put("tick.distance_tier1", JsonPrimitive(tick.distanceTier1))
                put("tick.distance_tier2", JsonPrimitive(tick.distanceTier2))
                put("tick.distance_tier3", JsonPrimitive(tick.distanceTier3))
                put("tick.skip_static_ai", JsonPrimitive(tick.skipStaticAI))
                put("tick.preserve_physics", JsonPrimitive(tick.preservePhysics))

                put("collision.grid_size", JsonPrimitive(collision.gridSize))
                put("collision.height_layers", JsonPrimitive(collision.heightLayers))
                put("collision.skip_static", JsonPrimitive(collision.skipStatic))
                put("collision.density_limit", JsonPrimitive(collision.densityLimit))
                put("collision.enable_aabb_cull", JsonPrimitive(collision.enableAabbCull))

                put("rendering.enable_instancing", JsonPrimitive(rendering.enableInstancing))
                put("rendering.frustum_cull", JsonPrimitive(rendering.frustumCull))
                put("rendering.enable_lod", JsonPrimitive(rendering.enableLod))
                put("rendering.lod_distance1", JsonPrimitive(rendering.lodDistance1))
                put("rendering.lod_distance2", JsonPrimitive(rendering.lodDistance2))

                put("item.merge_range", JsonPrimitive(item.mergeRange))
                put("item.preserve_nbt", JsonPrimitive(item.preserveNbt))
                put("item.simplify_physics", JsonPrimitive(item.simplifyPhysics))
                put("item.density_limit", JsonPrimitive(item.densityLimit))
                put("item.despawn_acceleration", JsonPrimitive(item.despawnAcceleration))

                put("spawn.density_limit", JsonPrimitive(spawn.densityLimit))
                put("spawn.distance_factor", JsonPrimitive(spawn.distanceFactor))
                put("spawn.cooldown_adjust", JsonPrimitive(spawn.cooldownAdjust))

                put("sync.incremental", JsonPrimitive(sync.incremental))
                put("sync.distance_tier1", JsonPrimitive(sync.distanceTier1))
                put("sync.distance_tier2", JsonPrimitive(sync.distanceTier2))
                put("sync.batch_size", JsonPrimitive(sync.batchSize))

                put("save.delay_ticks", JsonPrimitive(save.delayTicks))
                put("save.differential", JsonPrimitive(save.differential))
                put("save.compress", JsonPrimitive(save.compress))

                put("memory.enable_pools", JsonPrimitive(memory.enablePools))
                put("memory.pool_size", JsonPrimitive(memory.poolSize))

                put("chunk.entities_per_tick", JsonPrimitive(chunk.entitiesPerTick))
                put("chunk.chunks_per_unload_tick", JsonPrimitive(chunk.chunksPerUnloadTick))
                put("chunk.enable_lazy_loading", JsonPrimitive(chunk.enableLazyLoading))
                put("chunk.enable_batch_unload", JsonPrimitive(chunk.enableBatchUnload))

                put("debug.hud", JsonPrimitive(debug.hud))
                put("debug.commands", JsonPrimitive(debug.commands))
                put("debug.visualization", JsonPrimitive(debug.visualization))
            }

            FileWriter(configFile).use { writer ->
                writer.write(jankson.toJson(json).toPrettyString())
            }
        } catch (e: Exception) {
            EinsteiniumMod.LOGGER.warn("[Einsteinium] Failed to save config", e)
        }
    }

    class TickConfig {
        var distanceTier1 = 128
        var distanceTier2 = 256
        var distanceTier3 = 512
        var skipStaticAI = true
        var preservePhysics = true
    }

    class CollisionConfig {
        var gridSize = 16
        var heightLayers = 3
        var skipStatic = true
        var densityLimit = 32
        var enableAabbCull = true
    }

    class RenderingConfig {
        var enableInstancing = true
        var frustumCull = true
        var enableLod = true
        var lodDistance1 = 64
        var lodDistance2 = 128
    }

    class ItemConfig {
        var mergeRange = 8
        var preserveNbt = true
        var simplifyPhysics = true
        var densityLimit = 64
        var despawnAcceleration = 2
    }

    class SpawnConfig {
        var densityLimit = 16
        var distanceFactor = 0.5
        var cooldownAdjust = true
    }

    class SyncConfig {
        var incremental = true
        var distanceTier1 = 64
        var distanceTier2 = 128
        var batchSize = 16
    }

    class SaveConfig {
        var delayTicks = 20
        var differential = true
        var compress = true
    }

    class MemoryConfig {
        var enablePools = true
        var poolSize = 1024
    }

    class ChunkConfig {
        var entitiesPerTick = 8
        var chunksPerUnloadTick = 4
        var enableLazyLoading = true
        var enableBatchUnload = true
    }

    class DebugConfig {
        var hud = false
        var commands = false
        var visualization = false
    }
}