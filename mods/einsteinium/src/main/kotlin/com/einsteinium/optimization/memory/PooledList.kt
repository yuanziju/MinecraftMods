package com.einsteinium.optimization.memory

import java.util.concurrent.ConcurrentLinkedDeque

class PooledList<T> private constructor(
    private val delegate: MutableList<T>,
    private val pool: PooledListPool<T>
) : MutableList<T> by delegate {

    var isReleased = false
        private set

    fun release() {
        if (!isReleased) {
            isReleased = true
            delegate.clear()
            pool.release(this)
        }
    }

    override fun toString(): String {
        return "PooledList(size=${size}, released=$isReleased)"
    }
}

class PooledListPool<T>(private val maxSize: Int = 128) {
    private val pool = ConcurrentLinkedDeque<PooledList<T>>()

    fun acquire(): PooledList<T> {
        val pooled = pool.poll()
        return pooled ?: PooledList(mutableListOf(), this)
    }

    fun release(list: PooledList<T>) {
        if (pool.size < maxSize) {
            pool.offer(list)
        }
    }

    fun clear() {
        pool.clear()
    }

    fun getPoolSize(): Int {
        return pool.size
    }
}

inline fun <T> PooledListPool<T>.use(block: (PooledList<T>) -> Unit) {
    val list = acquire()
    try {
        block(list)
    } finally {
        list.release()
    }
}