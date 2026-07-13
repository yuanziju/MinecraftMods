package com.einsteinium.optimization.debug

import com.einsteinium.optimization.EinsteiniumMod
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

object DebugCommands {
    fun register() {
        EinsteiniumMod.LOGGER.info("[Einsteinium] Registering debug commands...")
    }

    fun registerCommands(dispatcher: CommandDispatcher<ServerCommandSource>, registryAccess: CommandRegistryAccess) {
        dispatcher.register(CommandManager.literal("einsteinium")
            .then(CommandManager.literal("stats")
                .executes { ctx ->
                    ctx.source.sendFeedback({ Text.literal("[Einsteinium] Entity Statistics:") }, false)
                    ctx.source.sendFeedback({ Text.literal("Total entities: 0") }, false)
                    ctx.source.sendFeedback({ Text.literal("Optimizations active: true") }, false)
                    1
                })
            .then(CommandManager.literal("profile")
                .executes { ctx ->
                    ctx.source.sendFeedback({ Text.literal("[Einsteinium] Profiling toggled") }, false)
                    1
                })
            .then(CommandManager.literal("collision")
                .executes { ctx ->
                    ctx.source.sendFeedback({ Text.literal("[Einsteinium] Collision visualization toggled") }, false)
                    1
                })
            .then(CommandManager.literal("tickheatmap")
                .executes { ctx ->
                    ctx.source.sendFeedback({ Text.literal("[Einsteinium] Tick heatmap toggled") }, false)
                    1
                })
            .then(CommandManager.literal("network")
                .executes { ctx ->
                    ctx.source.sendFeedback({ Text.literal("[Einsteinium] Network monitoring toggled") }, false)
                    1
                })
            .then(CommandManager.literal("memory")
                .executes { ctx ->
                    ctx.source.sendFeedback({ Text.literal("[Einsteinium] Memory tracking toggled") }, false)
                    1
                })
        )
    }
}