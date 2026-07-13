package com.molten.optimization.command

data class RenderCommand(
    val vertexBuffer: Any,
    val fragmentBuffer: Any,
    val primitiveType: Int
)
