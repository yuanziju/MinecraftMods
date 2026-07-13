package com.rhenium.optimization.cache

import com.rhenium.optimization.graph.RedstoneGraph

/**
 * 缓存失效管理器 —— 负责在红石节点变化时计算受影响范围并触发缓存失效。
 *
 * 当某个节点的输入信号发生变化时，其下游所有节点（通过边可达的节点）的缓存都需要失效，
 * 因为它们的计算结果可能依赖于变化了的节点。本类通过 BFS 遍历图结构来确定受影响节点集合，
 * 然后委托 [SignalCache] 执行实际的缓存失效操作。
 *
 * @param cache 实际存储信号缓存的 [SignalCache] 实例
 */
class CacheInvalidator(private val cache: SignalCache) {

    /**
     * 当节点输入变化时，使该节点及其所有下游节点的缓存失效。
     *
     * 内部直接委托 [SignalCache.invalidateSubtree]，由缓存层完成 BFS 遍历与失效。
     *
     * @param graph 所属红石图，用于遍历节点连接关系
     * @param changedNodeId 发生输入变化的节点 ID
     */
    fun invalidateOnChange(graph: RedstoneGraph, changedNodeId: Long) {
        cache.invalidateSubtree(graph, changedNodeId)
    }

    /**
     * 获取受影响的节点列表（BFS 遍历图的边）。
     *
     * 从 [changedNodeId] 出发，沿着 [RedstoneGraph.getNeighbors] 返回的边进行广度优先搜索，
     * 收集所有可达的节点 ID。返回的列表包含起始节点本身。
     *
     * 该方法可用于：
     * - 预估缓存失效范围
     * - 调试与监控
     * - 确定需要重新计算的节点集合
     *
     * @param graph 所属红石图
     * @param changedNodeId 发生变化的起始节点 ID
     * @return 所有受影响节点的 ID 列表（包含起始节点），按 BFS 访问顺序排列
     */
    fun getAffectedNodes(graph: RedstoneGraph, changedNodeId: Long): List<Long> {
        // 已访问节点集合，避免重复处理（图中可能存在环）
        val visited = HashSet<Long>()
        // BFS 队列
        val queue: ArrayDeque<Long> = ArrayDeque()
        // 结果列表，按 BFS 访问顺序记录受影响节点
        val result = mutableListOf<Long>()

        // 从起始节点开始遍历
        queue.add(changedNodeId)
        visited.add(changedNodeId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result.add(current)

            // 遍历当前节点的所有邻居（通过图的边），加入队列
            graph.getNeighbors(current).forEach { neighbor ->
                if (neighbor.id !in visited) {
                    visited.add(neighbor.id)
                    queue.add(neighbor.id)
                }
            }
        }

        return result
    }
}
