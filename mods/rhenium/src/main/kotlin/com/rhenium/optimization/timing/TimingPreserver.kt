package com.rhenium.optimization.timing

import com.rhenium.optimization.graph.RedstoneGraph
import com.rhenium.optimization.optimization.GraphUpdate
import org.slf4j.LoggerFactory

/**
 * 时序保障器：确保优化过程不破坏原版红石信号的微时序。
 *
 * 设计文档 §10 核心原则：
 *  > 所有优化不得改变原版红石信号传播的相对顺序和时序行为。
 *
 * 职责：
 *  1. [preserveOrder]：使用拓扑排序重新排列待提交的更新列表，
 *     使其顺序与原版方块更新顺序一致
 *  2. [checkTimingSafety]：检查图当前的状态是否会因为优化导致时序问题
 *
 * 该类组合使用了 [TopologicalSorter]（保序）与 [ZeroTickDetector]（0t 完整性保障），
 * 是优化策略产出 [com.rhenium.optimization.optimization.UpdateResult] 后的最后一道时序防线。
 */
class TimingPreserver {

    companion object {
        private val LOGGER = LoggerFactory.getLogger("Rhenium/TimingPreserver")
    }

    private val topologicalSorter = TopologicalSorter()
    private val zeroTickDetector = ZeroTickDetector()

    /**
     * 对一批更新按拓扑序重排，保持与原版一致的传播顺序。
     *
     * 算法：
     *  1. 对 [graph] 跑一次拓扑排序，得到 node id -> 排序下标的映射
     *  2. 对 [updates] 稳定排序：先按拓扑下标升序，下标缺失的排到末尾
     *  3. 同一节点多次更新按 tick 升序
     *
     * 这种做法不合并、不丢弃任何更新，仅重排，符合 Level 1「不改变传播顺序」的承诺，
     * 也兼容 Level 2/3 在拓扑序内做局部合并的语义。
     *
     * @return 重排后的更新列表
     */
    fun preserveOrder(graph: RedstoneGraph, updates: List<GraphUpdate>): List<GraphUpdate> {
        if (updates.isEmpty()) return emptyList()

        val sortedNodeIds = topologicalSorter.sort(graph)
        // 节点 id -> 拓扑序下标（数值越小越早被处理）
        val orderIndex = HashMap<Long, Int>(sortedNodeIds.size)
        for ((index, id) in sortedNodeIds.withIndex()) {
            orderIndex[id] = index
        }

        // 稳定排序：拓扑下标优先，其次按 tick，再次按节点 id 兜底
        val ordered = updates.sortedWith(
            compareBy(
                { orderIndex[it.nodeId] ?: Int.MAX_VALUE },
                { it.tick },
                { it.nodeId }
            )
        )

        if (LOGGER.isDebugEnabled && ordered != updates) {
            LOGGER.debug("图 {} 的 {} 条更新被时序保障器重排以保持原版顺序", graph.id, ordered.size)
        }
        return ordered
    }

    /**
     * 检查 [graph] 当前的时序状态是否安全（可以继续使用当前优化级别）。
     *
     * 判定逻辑：
     *  1. 任何 0t 路径上的节点必须使用 Level 1。当图已升级到 Level 2/3 但仍存在 0t 路径时，
     *     视为不安全 -> 触发降级
     *  2. 图被显式标记为复杂时序（hasComplexTiming=true）时，仅 Level 1 视为安全
     *  3. 否则视为安全
     *
     * @return true 表示当前级别与时序约束一致，可继续使用；false 表示存在冲突，需要降级
     */
    fun checkTimingSafety(graph: RedstoneGraph): Boolean {
        // 复杂时序电路：仅保守策略安全
        if (graph.hasComplexTiming && graph.optimizationLevel > 1) {
            LOGGER.warn(
                "图 {} 标记为复杂时序，但当前优化级别为 {}（应回退到 Level 1）",
                graph.id,
                graph.optimizationLevel
            )
            return false
        }

        // 0t 路径上的节点强制 Level 1。若图已升级但仍有 0t 路径 -> 不安全
        if (zeroTickDetector.hasZeroTickPath(graph) && graph.optimizationLevel > 1) {
            LOGGER.warn(
                "图 {} 存在 0t 信号路径，但优化级别为 {}（应回退到 Level 1）",
                graph.id,
                graph.optimizationLevel
            )
            return false
        }

        return true
    }
}
