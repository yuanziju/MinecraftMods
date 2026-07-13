package com.molten.optimization.backend

import com.molten.optimization.MoltenMod
import com.molten.optimization.config.MoltenConfig

object RenderBackendFactory {
    lateinit var currentBackend: RenderBackend
        private set

    fun initialize() {
        currentBackend = createBackend()
        currentBackend.init()
    }

    fun destroy() {
        currentBackend.destroy()
    }

    private fun createBackend(): RenderBackend {
        return when (MoltenConfig.metalBackend) {
            MoltenConfig.BackendMode.AUTO -> if (isAppleSilicon() && MoltenConfig.metalEnable) {
                MetalBackend()
            } else {
                OpenGLBackend()
            }
            MoltenConfig.BackendMode.METAL -> MetalBackend()
            MoltenConfig.BackendMode.OPENGL -> OpenGLBackend()
        }
    }

    private fun isAppleSilicon(): Boolean {
        val osName = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        return osName.contains("mac") && (arch == "aarch64" || arch == "arm64")
    }
}
