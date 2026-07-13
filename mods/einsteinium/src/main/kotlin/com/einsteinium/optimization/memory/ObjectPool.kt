package com.einsteinium.optimization.memory

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

class ObjectPool<T>(
    private val creator: () -> T,
    private val resetter: (T) -> Unit,
    private val validator: ((T) -> Boolean)? = null,
    private val maxSize: Int
) {
    private val pool = ConcurrentLinkedDeque<T>()

    private val acquireCount = AtomicLong(0)
    private val releaseCount = AtomicLong(0)
    private val missCount = AtomicLong(0)

    fun acquire(): T {
        acquireCount.incrementAndGet()
        val obj = pool.poll()
        return if (obj != null) {
            resetter(obj)
            if (validator?.invoke(obj) == false) {
                missCount.incrementAndGet()
                creator()
            } else {
                obj
            }
        } else {
            missCount.incrementAndGet()
            creator()
        }
    }

    fun release(obj: T) {
        releaseCount.incrementAndGet()
        if (pool.size < maxSize) {
            pool.offer(obj)
        }
    }

    fun clear() {
        pool.clear()
    }

    fun getPoolSize(): Int {
        return pool.size
    }

    fun getStats(): PoolStats {
        val acquired = acquireCount.get()
        val misses = missCount.get()
        val hitRate = if (acquired == 0L) 0.0 else ((acquired - misses).toDouble() / acquired.toDouble()) * 100
        return PoolStats(
            acquired = acquired,
            released = releaseCount.get(),
            misses = misses,
            hitRate = hitRate,
            currentPoolSize = pool.size,
            maxPoolSize = maxSize
        )
    }

    fun resetStats() {
        acquireCount.set(0)
        releaseCount.set(0)
        missCount.set(0)
    }

    data class PoolStats(
        val acquired: Long,
        val released: Long,
        val misses: Long,
        val hitRate: Double,
        val currentPoolSize: Int,
        val maxPoolSize: Int
    )
}