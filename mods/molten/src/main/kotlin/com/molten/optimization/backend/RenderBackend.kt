package com.molten.optimization.backend

interface RenderBackend {
    fun init()
    fun destroy()
    fun createVertexBuffer(data: FloatArray): VertexBuffer
    fun createIndexBuffer(data: IntArray): IndexBuffer
    fun createShader(vertexSource: String, fragmentSource: String): Shader
    fun createTexture(image: Any): Texture
    fun createFramebuffer(width: Int, height: Int): Framebuffer
    fun beginRender()
    fun endRender()
    val name: String
}

interface VertexBuffer {
    fun upload(data: FloatArray)
    fun bind()
    fun unbind()
    fun destroy()
}

interface IndexBuffer {
    fun upload(data: IntArray)
    fun bind()
    fun unbind()
    fun destroy()
}

interface Shader {
    fun bind()
    fun unbind()
    fun setUniform(name: String, value: Float)
    fun setUniform(name: String, value: Int)
    fun setUniform(name: String, values: FloatArray)
    fun destroy()
}

interface Texture {
    fun bind(unit: Int)
    fun unbind()
    fun destroy()
}

interface Framebuffer {
    fun bind()
    fun unbind()
    fun resize(width: Int, height: Int)
    fun destroy()
}
