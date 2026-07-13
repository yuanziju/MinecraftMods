package com.molten.optimization.command

import com.molten.optimization.MoltenMod
import com.molten.optimization.config.MoltenConfig

class CommandQueueOptimizer {
    fun submitBatch(commands: List<RenderCommand>) {
        if (!MoltenConfig.commandBatch) {
            commands.forEach { submitSingle(it) }
            return
        }
        MoltenMod.LOGGER.debug("Submitting batch of ${commands.size} commands")
    }

    fun submitParallel(commands: List<RenderCommand>) {
        if (!MoltenConfig.commandParallel) {
            submitBatch(commands)
            return
        }
        MoltenMod.LOGGER.debug("Submitting ${commands.size} commands in parallel")
    }

    fun submitCompute(command: ComputeCommand) {
        if (!MoltenConfig.commandCompute) return
        MoltenMod.LOGGER.debug("Submitting compute command")
    }

    private fun submitSingle(command: RenderCommand) {
        MoltenMod.LOGGER.debug("Submitting single command")
    }
}
