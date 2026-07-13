package com.molten.optimization.resource

import com.molten.optimization.MoltenMod
import com.molten.optimization.config.MoltenConfig

class ResourceManager {
    fun createCompressedTexture(image: Any): Any {
        if (!MoltenConfig.resourceCompression) {
            return createUncompressedTexture(image)
        }
        MoltenMod.LOGGER.debug("Creating compressed texture")
        return image
    }

    fun createUncompressedTexture(image: Any): Any {
        return image
    }

    fun createRingBuffer(size: Int): RingBuffer {
        if (!MoltenConfig.resourceRingBuffer) {
            return SimpleBuffer(size)
        }
        return RingBufferImpl(size)
    }

    fun optimizeMemoryAccess(buffer: Any) {
        if (!MoltenConfig.resourceUnifiedMemory) return
        MoltenMod.LOGGER.debug("Optimizing memory access")
    }

    private interface RingBuffer {
        fun write(data: ByteArray): Boolean
        fun read(size: Int): ByteArray?
        fun hasSpace(size: Int): Boolean
    }

    private class RingBufferImpl(size: Int) : RingBuffer {
        private val buffer = ByteArray(size)
        private var readIndex = 0
        private var writeIndex = 0

        override fun write(data: ByteArray): Boolean {
            if (!hasSpace(data.size)) return false
            data.copyInto(buffer, writeIndex)
            writeIndex = (writeIndex + data.size) % buffer.size
            return true
        }

        override fun read(size: Int): ByteArray? {
            if (size > buffer.size - (writeIndex - readIndex + buffer.size) % buffer.size) return null
            val result = ByteArray(size)
            buffer.copyInto(result, 0, readIndex, readIndex + size)
            readIndex = (readIndex + size) % buffer.size
            return result
        }

        override fun hasSpace(size: Int): Boolean {
            return size <= buffer.size - ((writeIndex - readIndex + buffer.size) % buffer.size)
        }
    }

    private class SimpleBuffer(size: Int) : RingBuffer {
        private val buffer = ByteArray(size)
        private var position = 0

        override fun write(data: ByteArray): Boolean {
            if (position + data.size > buffer.size) return false
            data.copyInto(buffer, position)
            position += data.size
            return true
        }

        override fun read(size: Int): ByteArray? {
            if (size > position) return null
            val result = ByteArray(size)
            buffer.copyInto(result, 0, 0, size)
            buffer.copyInto(buffer, 0, size, position)
            position -= size
            return result
        }

        override fun hasSpace(size: Int): Boolean {
            return position + size <= buffer.size
        }
    }
}
