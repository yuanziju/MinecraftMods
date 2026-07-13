package com.molten.optimization.resource

class RingBuffer(val buffer: ByteArray) {
    var readIndex: Int = 0
    var writeIndex: Int = 0

    fun write(data: ByteArray): Boolean {
        if (!hasSpace(data.size)) return false
        data.copyInto(buffer, writeIndex)
        writeIndex = (writeIndex + data.size) % buffer.size
        return true
    }

    fun read(size: Int): ByteArray? {
        if (size > (writeIndex - readIndex + buffer.size) % buffer.size) return null
        val result = ByteArray(size)
        buffer.copyInto(result, 0, readIndex, readIndex + size)
        readIndex = (readIndex + size) % buffer.size
        return result
    }

    fun hasSpace(size: Int): Boolean {
        return size <= buffer.size - ((writeIndex - readIndex + buffer.size) % buffer.size)
    }
}
