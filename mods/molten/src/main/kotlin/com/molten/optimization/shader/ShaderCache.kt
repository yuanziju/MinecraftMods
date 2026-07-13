package com.molten.optimization.shader

import com.molten.optimization.MoltenMod
import com.molten.optimization.config.MoltenConfig
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

object ShaderCache {
    private val cacheDir: Path = Paths.get(".cache", "molten", "shaders")
    private val compiledShaders = mutableMapOf<String, CompiledShader>()

    init {
        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir)
        }
    }

    fun getOrCompile(vertexSource: String, fragmentSource: String): CompiledShader {
        if (!MoltenConfig.shaderCache) {
            return ShaderCompiler.compile(vertexSource, fragmentSource)
        }

        val hash = computeHash(vertexSource, fragmentSource)
        compiledShaders[hash]?.let { return it }

        val cached = loadFromDisk(hash)
        if (cached != null) {
            compiledShaders[hash] = cached
            return cached
        }

        val compiled = ShaderCompiler.compile(vertexSource, fragmentSource)
        compiledShaders[hash] = compiled
        saveToDisk(hash, compiled)
        return compiled
    }

    fun computeHash(vertexSource: String, fragmentSource: String): String {
        val input = "$vertexSource|$fragmentSource"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun saveToDisk(hash: String, shader: CompiledShader) {
        val file = cacheDir.resolve("$hash.mtl").toFile()
        try {
            file.writeText("${shader.vertexCode}\n---MOLTEN_SEPARATOR---\n${shader.fragmentCode}")
        } catch (e: Exception) {
            MoltenMod.LOGGER.warn("Failed to save shader cache: ${e.message}")
        }
    }

    private fun loadFromDisk(hash: String): CompiledShader? {
        val file = cacheDir.resolve("$hash.mtl").toFile()
        if (!file.exists()) return null

        try {
            val content = file.readText()
            val parts = content.split("---MOLTEN_SEPARATOR---")
            if (parts.size == 2) {
                return CompiledShader(parts[0], parts[1])
            }
        } catch (e: Exception) {
            MoltenMod.LOGGER.warn("Failed to load shader cache: ${e.message}")
        }
        return null
    }
}
