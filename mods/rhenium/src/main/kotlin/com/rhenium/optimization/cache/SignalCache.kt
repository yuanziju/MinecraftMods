package com.rhenium.optimization.cache

import com.rhenium.optimization.graph.RedstoneGraph

/**
 * 红石信号缓存 —— 基于 LRU（最近最少使用）策略的节点信号缓存。
 *
 * 缓存内容：每个节点 [nodeId] 对应的当前红石信号强度。
 * 当节点输入发生变化时，需通过 [invalidate] 或 [invalidateSubtree] 使相关缓存失效，
 * 以保证后续计算读取到最新值。
 *
 * 线程安全：所有读写操作通过 [lock] 同步，保证多线程环境下的数据一致性。
 *
 * @param maxSize 缓存最大条目数，超过后淘汰最久未使用的条目
 */
class SignalCache(maxSize: Int) {

    /** 缓存最大容量 */
    private val maxSize: Int = maxSize

    /**
     * LRU 缓存底层 Map。
     * - accessOrder = true：每次 get/put 都会将访问的条目移到链表尾部，实现按访问顺序排序
     * - 重写 [removeEldestEntry]：当条目数超过 [maxSize] 时自动淘汰链表头部（最久未使用）的条目
     */
    private val cache: LinkedHashMap<Long, Int> = object : LinkedHashMap<Long, Int>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Int>?): Boolean {
            return size > maxSize
        }
    }

    /** 缓存命中次数（用于命中率统计） */
    private var hits: Long = 0L

    /** 缓存未命中次数（用于命中率统计） */
    private var misses: Long = 0L

    /** 同步锁，保护 [cache] / [hits] / [misses] 的并发访问 */
    private val lock = Any()

    /**
     * 获取节点缓存的红石信号强度。
     *
     * @param nodeId 红石元件节点 ID
     * @return 缓存的信号强度；null 表示未命中
     */
    fun get(nodeId: Long): Int? = synchronized(lock) {
        val value = cache[nodeId]
        if (value != null) {
            hits++
        } else {
            misses++
        }
        value
    }

    /**
     * 存入节点的信号强度到缓存。
     * 若缓存已满，会自动淘汰最久未使用的条目。
     *
     * @param nodeId 红石元件节点 ID
     * @param signal 该节点的信号强度
     */
    fun put(nodeId: Long, signal: Int) = synchronized(lock) {
        cache[nodeId] = signal
    }

    /**
     * 使单个节点的缓存失效。
     *
     * @param nodeId 需要失效的节点 ID
     */
    fun invalidate(nodeId: Long) = synchronized(lock) {
        cache.remove(nodeId)
    }

    /**
     * 使节点及其所有下游节点（通过边可达的节点）的缓存失效。
     *
     * 使用 BFS 遍历图，从 [nodeId] 出发，沿着 [RedstoneGraph.getNeighbors] 找出所有受影响节点，
     * 逐一从缓存中移除。这确保了节点信号变化后，所有依赖该信号的下游节点都会重新计算。
     *
     * @param graph 所属红石图，用于遍历节点连接关系
     * @param nodeId 发生变化的起始节点 ID
     */
    fun invalidateSubtree(graph: RedstoneGraph, nodeId: Long) = synchronized(lock) {
        // BFS 遍历所需的数据结构
        val visited = HashSet<Long>()
        val queue: ArrayDeque<Long> = ArrayDeque()

        // 从起始节点开始
        queue.add(nodeId)
        visited.add(nodeId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            // 当前节点的缓存失效
            cache.remove(current)

            // 遍历所有邻居（下游节点），加入队列继续处理
            graph.getNeighbors(current).forEach { neighbor ->
                if (neighbor.id !in visited) {
                    visited.add(neighbor.id)
                    queue.add(neighbor.id)
                }
            }
        }
    }

    /**
     * 获取当前缓存命中率。
     *
     * 命中率 = hits / (hits + misses)
     * 若无任何访问，返回 0.0。
     *
     * @return 命中率，范围 [0.0, 1.0]
     */
    fun getHitRate(): Double = synchronized(lock) {
        val total = hits + misses
        if (total == 0L) 0.0 else hits.toDouble() / total
    }

    /**
     * 清空所有缓存条目并重置命中统计。
     */
    fun clear() = synchronized(lock) {
        cache.clear()
        hits = 0
        misses = 0
    }
}
