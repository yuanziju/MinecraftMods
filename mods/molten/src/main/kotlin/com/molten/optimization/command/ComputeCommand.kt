package com.molten.optimization.command

data class ComputeCommand(
    val pipelineState: Any,
    val inputBuffer: Any,
    val outputBuffer: Any,
    val threadGroupSize: Int
)
