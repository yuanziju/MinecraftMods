package com.molten.optimization.backend

import com.molten.optimization.MoltenMod

class OpenGLBackend : RenderBackend {
    override val name: String = "OpenGL"

    override fun init() {
        MoltenMod.LOGGER.info("Initializing OpenGL backend")
    }

    override fun destroy() {
        MoltenMod.LOGGER.info("Destroying OpenGL backend")
    }

    override fun createVertexBuffer(data: FloatArray): VertexBuffer {
        return OpenGLVertexBuffer(data)
    }

    override fun createIndexBuffer(data: IntArray): IndexBuffer {
        return OpenGLIndexBuffer(data)
    }

    override fun createShader(vertexSource: String, fragmentSource: String): Shader {
        return OpenGLShader(vertexSource, fragmentSource)
    }

    override fun createTexture(image: Any): Texture {
        return OpenGLTexture(image)
    }

    override fun createFramebuffer(width: Int, height: Int): Framebuffer {
        return OpenGLFramebuffer(width, height)
    }

    override fun beginRender() {}

    override fun endRender() {}

    private class OpenGLVertexBuffer(data: FloatArray) : VertexBuffer {
        override fun upload(data: FloatArray) {}
        override fun bind() {}
        override fun unbind() {}
        override fun destroy() {}
    }

    private class OpenGLIndexBuffer(data: IntArray) : IndexBuffer {
        override fun upload(data: IntArray) {}
        override fun bind() {}
        override fun unbind() {}
        override fun destroy() {}
    }

    private class OpenGLShader(vertexSource: String, fragmentSource: String) : Shader {
        override fun bind() {}
        override fun unbind() {}
        override fun setUniform(name: String, value: Float) {}
        override fun setUniform(name: String, value: Int) {}
        override fun setUniform(name: String, values: FloatArray) {}
        override fun destroy() {}
    }

    private class OpenGLTexture(image: Any) : Texture {
        override fun bind(unit: Int) {}
        override fun unbind() {}
        override fun destroy() {}
    }

    private class OpenGLFramebuffer(width: Int, height: Int) : Framebuffer {
        override fun bind() {}
        override fun unbind() {}
        override fun resize(width: Int, height: Int) {}
        override fun destroy() {}
    }
}
