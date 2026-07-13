package com.molten.optimization.shader

import com.molten.optimization.MoltenMod

object ShaderCompiler {
    fun compile(vertexSource: String, fragmentSource: String): CompiledShader {
        MoltenMod.LOGGER.debug("Compiling shader")
        return CompiledShader(vertexSource, fragmentSource)
    }
}
