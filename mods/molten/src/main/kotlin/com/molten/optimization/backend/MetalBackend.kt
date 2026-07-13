package com.molten.optimization.backend

import com.molten.optimization.MoltenMod
import com.molten.optimization.config.MoltenConfig

class MetalBackend : RenderBackend {
    override val name: String = "Metal"

    override fun init() {
        MoltenMod.LOGGER.info("Initializing Metal backend")
    }

    override fun destroy() {
        MoltenMod.LOGGER.info("Destroying Metal backend")
    }

    override fun createVertexBuffer(data: FloatArray): VertexBuffer {
        return MetalVertexBuffer(data)
    }

    override fun createIndexBuffer(data: IntArray): IndexBuffer {
        return MetalIndexBuffer(data)
    }

    override fun createShader(vertexSource: String, fragmentSource: String): Shader {
        return MetalShader(vertexSource, fragmentSource)
    }

    override fun createTexture(image: Any): Texture {
        return MetalTexture(image)
    }

    override fun createFramebuffer(width: Int, height: Int): Framebuffer {
        return MetalFramebuffer(width, height)
    }

    override fun beginRender() {}

    override fun endRender() {}

    private class MetalVertexBuffer(data: FloatArray) : VertexBuffer {
        override fun upload(data: FloatArray) {}
        override fun bind() {}
        override fun unbind() {}
        override fun destroy() {}
    }

    private class MetalIndexBuffer(data: IntArray) : IndexBuffer {
        override fun upload(data: IntArray) {}
        override fun bind() {}
        override fun unbind() {}
        override fun destroy() {}
    }

    private class MetalShader(vertexSource: String, fragmentSource: String) : Shader {
        override fun bind() {}
        override fun unbind() {}
        override fun setUniform(name: String, value: Float) {}
        override fun setUniform(name: String, value: Int) {}
        override fun setUniform(name: String, values: FloatArray) {}
        override fun destroy() {}
    }

    private class MetalTexture(image: Any) : Texture {
        override fun bind(unit: Int) {}
        override fun unbind() {}
        override fun destroy() {}
    }

    private class MetalFramebuffer(width: Int, height: Int) : Framebuffer {
        override fun bind() {}
        override fun unbind() {}
        override fun resize(width: Int, height: Int) {}
        override fun destroy() {}
    }
}
