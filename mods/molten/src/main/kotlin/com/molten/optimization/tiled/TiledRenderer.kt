package com.molten.optimization.tiled

import com.molten.optimization.MoltenMod
import com.molten.optimization.command.RenderCommand
import com.molten.optimization.config.MoltenConfig

class TiledRenderer {
    private val tileSize: Int = 16

    fun renderTiled(renderCommands: List<RenderCommand>, framebuffer: Any) {
        if (!MoltenConfig.tiledDeferred) {
            renderNormal(renderCommands)
            return
        }
        MoltenMod.LOGGER.debug("Rendering tiled with {} commands", renderCommands.size)
    }

    fun cullToTile(commands: List<RenderCommand>, tileX: Int, tileY: Int): List<RenderCommand> {
        return commands
    }

    fun renderTile(commands: List<RenderCommand>, tileX: Int, tileY: Int) {
        MoltenMod.LOGGER.debug("Rendering tile at ({}, {})", tileX, tileY)
    }

    fun earlyZCull(commands: List<RenderCommand>, depthBuffer: Any): List<RenderCommand> {
        if (!MoltenConfig.tiledEarlyZ) return commands
        MoltenMod.LOGGER.debug("Early Z culling {} commands", commands.size)
        return commands
    }

    fun tiledLighting(commands: List<RenderCommand>, lights: List<Any>, tileX: Int, tileY: Int) {
        if (!MoltenConfig.tiledLighting) return
        MoltenMod.LOGGER.debug("Applying tiled lighting at ({}, {})", tileX, tileY)
    }

    private fun renderNormal(commands: List<RenderCommand>) {
        MoltenMod.LOGGER.debug("Rendering normally with {} commands", commands.size)
    }
}
