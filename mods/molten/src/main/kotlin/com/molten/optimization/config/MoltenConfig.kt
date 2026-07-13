package com.molten.optimization.config

import com.molten.optimization.MoltenMod
import me.shedaniel.clothconfig2.api.ConfigBuilder
import me.shedaniel.clothconfig2.api.ConfigCategory
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object MoltenConfig {
    private lateinit var configFile: File

    var metalEnable = true
    var metalBackend = BackendMode.AUTO

    var shaderCache = true
    var shaderPrecompile = true

    var commandBatch = true
    var commandParallel = true
    var commandCompute = true

    var resourceCompression = true
    var resourceRingBuffer = true
    var resourceUnifiedMemory = true

    var tiledDeferred = true
    var tiledEarlyZ = true
    var tiledLighting = true

    var debugCounters = false
    var debugVisualizer = false
    var debugFrameTime = false

    enum class BackendMode {
        AUTO, METAL, OPENGL
    }

    fun load() {
        val configDir = Paths.get("config")
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir)
        }
        configFile = configDir.resolve("molten.properties").toFile()

        if (configFile.exists()) {
            loadFromFile()
        } else {
            save()
        }
    }

    private fun loadFromFile() {
        configFile.readLines().forEach { line ->
            if (line.isNotEmpty() && !line.startsWith("#")) {
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    parseValue(key, value)
                }
            }
        }
    }

    private fun parseValue(key: String, value: String) {
        when (key) {
            "metal.enable" -> metalEnable = value.toBoolean()
            "metal.backend" -> metalBackend = BackendMode.valueOf(value.uppercase())
            "shader.cache" -> shaderCache = value.toBoolean()
            "shader.precompile" -> shaderPrecompile = value.toBoolean()
            "command.batch" -> commandBatch = value.toBoolean()
            "command.parallel" -> commandParallel = value.toBoolean()
            "command.compute" -> commandCompute = value.toBoolean()
            "resource.compression" -> resourceCompression = value.toBoolean()
            "resource.ring_buffer" -> resourceRingBuffer = value.toBoolean()
            "resource.unified_memory" -> resourceUnifiedMemory = value.toBoolean()
            "tiled.deferred" -> tiledDeferred = value.toBoolean()
            "tiled.early_z" -> tiledEarlyZ = value.toBoolean()
            "tiled.lighting" -> tiledLighting = value.toBoolean()
            "debug.counters" -> debugCounters = value.toBoolean()
            "debug.visualizer" -> debugVisualizer = value.toBoolean()
            "debug.frame_time" -> debugFrameTime = value.toBoolean()
        }
    }

    fun save() {
        val content = buildString {
            appendLine("# Molten Configuration")
            appendLine()
            appendLine("# Metal Backend")
            appendLine("metal.enable=$metalEnable")
            appendLine("metal.backend=$metalBackend")
            appendLine()
            appendLine("# Shader Cache")
            appendLine("shader.cache=$shaderCache")
            appendLine("shader.precompile=$shaderPrecompile")
            appendLine()
            appendLine("# Command Queue")
            appendLine("command.batch=$commandBatch")
            appendLine("command.parallel=$commandParallel")
            appendLine("command.compute=$commandCompute")
            appendLine()
            appendLine("# Resource Management")
            appendLine("resource.compression=$resourceCompression")
            appendLine("resource.ring_buffer=$resourceRingBuffer")
            appendLine("resource.unified_memory=$resourceUnifiedMemory")
            appendLine()
            appendLine("# Tiled Rendering")
            appendLine("tiled.deferred=$tiledDeferred")
            appendLine("tiled.early_z=$tiledEarlyZ")
            appendLine("tiled.lighting=$tiledLighting")
            appendLine()
            appendLine("# Debug")
            appendLine("debug.counters=$debugCounters")
            appendLine("debug.visualizer=$debugVisualizer")
            appendLine("debug.frame_time=$debugFrameTime")
        }
        configFile.writeText(content)
    }

    fun createConfigScreen(parent: Screen?): Screen {
        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Text.translatable("molten.config.title"))

        val entryBuilder = builder.entryBuilder()

        val metalCategory = builder.getOrCreateCategory(Text.translatable("molten.config.metal.category"))
        addMetalEntries(metalCategory, entryBuilder)

        val shaderCategory = builder.getOrCreateCategory(Text.translatable("molten.config.shader.category"))
        addShaderEntries(shaderCategory, entryBuilder)

        val commandCategory = builder.getOrCreateCategory(Text.translatable("molten.config.command.category"))
        addCommandEntries(commandCategory, entryBuilder)

        val resourceCategory = builder.getOrCreateCategory(Text.translatable("molten.config.resource.category"))
        addResourceEntries(resourceCategory, entryBuilder)

        val tiledCategory = builder.getOrCreateCategory(Text.translatable("molten.config.tiled.category"))
        addTiledEntries(tiledCategory, entryBuilder)

        val debugCategory = builder.getOrCreateCategory(Text.translatable("molten.config.debug.category"))
        addDebugEntries(debugCategory, entryBuilder)

        builder.setSavingRunnable { save() }

        return builder.build()
    }

    private fun addMetalEntries(category: ConfigCategory, builder: ConfigEntryBuilder) {
        category.addEntry(
            builder.startBooleanToggle(Text.translatable("molten.config.metal.enable"), metalEnable)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("molten.config.metal.enable.tooltip"))
                .setSaveConsumer { metalEnable = it }
                .build()
        )
        category.addEntry(
            builder.startEnumSelector(Text.translatable("molten.config.metal.backend"), BackendMode::class.java, metalBackend)
                .setDefaultValue(BackendMode.AUTO)
                .setEnumNameProvider { Text.translatable("molten.config.metal.backend.${it.name.lowercase()}") }
                .setSaveConsumer { metalBackend = it }
                .build()
        )
    }

    private fun addShaderEntries(category: ConfigCategory, builder: ConfigEntryBuilder) {
        category.addEntry(
            builder.startBooleanToggle(Text.translatable("molten.config.shader.cache"), shaderCache)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("molten.config.shader.cache.tooltip"))
                .setSaveConsumer { shaderCache = it }
                .build()
        )
        category.addEntry(
            builder.startBooleanToggle(Text.translatable("molten.config.shader.precompile"), shaderPrecompile)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("molten.config.shader.precompile.tooltip"))
                .setSaveConsumer { shaderPrecompile = it }
                .build()
        )
    }

    private fun addCommandEntries(category: ConfigCategory, builder: ConfigEntryBuilder) {
        category.addEntry(
            builder.startBooleanToggle(Text.translatable("molten.config.command.batch"), commandBatch)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("molten.config.command.batch.tooltip"))
                .setSaveConsumer { commandBatch = it }
                .build()
        )
        category.addEntry(
            builder.startBooleanToggle(Text.translatable("molten.config.command.parallel"), commandParallel)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("molten.config.command.parallel.tooltip"))
                .setSaveConsumer { commandParallel = it }
                .build()
        )
        category.addEntry(
            builder.startBooleanToggle(Text.translatable("molten.config.command.compute"), commandCompute)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("molten.config.command.compute.tooltip"))
                .setSaveConsumer { commandCompute = it }
                .build()
        )
    }

    private fun addResourceEntries(category: ConfigCategory, builder: ConfigEntryBuilder) {
        category.addEntry(
            builder.startBooleanToggle(Text.translatable("molten.config.resource.compression"), resourceCompression)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("molten.config.resource.compression.tooltip"))
                .setSaveConsumer { resourceCompression = it }
                .build()
        )
        category.addEntry(
            builder.startBooleanToggle(Text.translatable("molten.config.resource.ring_buffer"), resourceRingBuffer)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("molten.config.resource.ring_buffer.tooltip"))
                .setSaveConsumer { resourceRingBuffer = it }
                .build()
        )
        category.addEntry(
            builder.startBooleanToggle(Text.translatable("molten.config.resource.unified_memory"), resourceUnifiedMemory)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("molten.config.resource.unified_memory.tooltip"))
                .setSaveConsumer { resourceUnifiedMemory = it }
                .build()
        )
    }

    private fun addTiledEntries(category: ConfigCategory, builder: ConfigEntryBuilder) {
        category.addEntry(
            builder.startBooleanToggle(Text.translatable("molten.config.tiled.deferred"), tiledDeferred)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("molten.config.tiled.deferred.tooltip"))
                .setSaveConsumer { tiledDeferred = it }
                .build()
        )
        category.addEntry(
            builder.startBooleanToggle(Text.translatable("molten.config.tiled.early_z"), tiledEarlyZ)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("molten.config.tiled.early_z.tooltip"))
                .setSaveConsumer { tiledEarlyZ = it }
                .build()
        )
        category.addEntry(
            builder.startBooleanToggle(Text.translatable("molten.config.tiled.lighting"), tiledLighting)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("molten.config.tiled.lighting.tooltip"))
                .setSaveConsumer { tiledLighting = it }
                .build()
        )
    }

    private fun addDebugEntries(category: ConfigCategory, builder: ConfigEntryBuilder) {
        category.addEntry(
            builder.startBooleanToggle(Text.translatable("molten.config.debug.counters"), debugCounters)
                .setDefaultValue(false)
                .setTooltip(Text.translatable("molten.config.debug.counters.tooltip"))
                .setSaveConsumer { debugCounters = it }
                .build()
        )
        category.addEntry(
            builder.startBooleanToggle(Text.translatable("molten.config.debug.visualizer"), debugVisualizer)
                .setDefaultValue(false)
                .setTooltip(Text.translatable("molten.config.debug.visualizer.tooltip"))
                .setSaveConsumer { debugVisualizer = it }
                .build()
        )
        category.addEntry(
            builder.startBooleanToggle(Text.translatable("molten.config.debug.frame_time"), debugFrameTime)
                .setDefaultValue(false)
                .setTooltip(Text.translatable("molten.config.debug.frame_time.tooltip"))
                .setSaveConsumer { debugFrameTime = it }
                .build()
        )
    }
}
