package com.rhenium.optimization.optimization

import com.rhenium.optimization.graph.NodeType
import com.rhenium.optimization.graph.RedstoneGraph
import com.rhenium.optimization.timing.ZeroTickDetector
import org.slf4j.LoggerFactory

/**
 * 自适应级别管理器：根据图的运行时表现自动调整优化级别。
 *
 * 设计文档 §4.2 自适应升级机制：
 *  - 首次更新 → Level 1（保守）
 *  - 不依赖 0t / 微时序 / 复杂中继器链 / 活塞遮挡动态 → 升级到 Level 2
 *  - Level 2 运行稳定 N tick 无异常 + 再次检测无高级特性 → 升级到 Level 3
 *  - 检测到异常或新特性 → 降级到 Level 2 或 Level 1
 *
 * 「高级特性」定义（设计文档 §4.2）：
 *  1. 0t 信号反馈环路（[ZeroTickDetector] 检测到的环路径）
 *  2. 微时序依赖（[RedstoneGraph.hasComplexTiming] 标记）
 *  3. 复杂中继器链（连续 [NodeType.REPEATER] 数量超过阈值）
 *  4. 活塞遮挡动态变化（无 GraphNode 直接标记，通过 hasComplexTiming 间接体现）
 *
 * 调用约定：调用方每 tick 调用一次 [shouldUpgrade] 与 [shouldDowngrade]，
 * 内部会维护稳定 tick 计数器；切勿在一 tick 内多次调用以免计数器被污染。
 */
class AdaptiveLevelManager {

    companion object {
        private val LOGGER = LoggerFactory.getLogger("Rhenium/AdaptiveLevelManager")

        /** 升级到下一级别所需的连续稳定 tick 数。 */
        private const val REQUIRED_STABLE_TICKS_FOR_UPGRADE = 100

        /** 中继器链被认为「复杂」的最小长度。 */
        private const val COMPLEX_REPEATER_CHAIN_THRESHOLD = 5
    }

    /** 每个图的稳定 tick 计数器：连续无异常的 tick 数。出现异常会归零。 */
    private val stableTickCount: MutableMap<Long, Int> = mutableMapOf()

    /** 0t 检测器，用于判断图是否依赖 0t 反馈环路。 */
    private val zeroTickDetector = ZeroTickDetector()

    /**
     * 判断 [graph] 是否可以升级到下一级。
     *
     * 判定流程：
     *  1. 已在 Level 3 → 直接返回 false
     *  2. 当前 tick 检测到异常 → 计数器归零，返回 false
     *  3. 计数器自增；未达 [REQUIRED_STABLE_TICKS_FOR_UPGRADE] → 返回 false
     *  4. 仍依赖高级特性（0t 环路 / 复杂时序 / 复杂中继器链 / 活塞遮挡）→ 返回 false
     *  5. 否则返回 true
     *
     * 注意：本方法有副作用（更新 [stableTickCount]）。每 tick 仅应调用一次。
     */
    fun shouldUpgrade(graph: RedstoneGraph): Boolean {
        // 已在最高级
        if (graph.optimizationLevel >= 3) return false

        // 检测异常：任何高级特性的存在都视为「不稳定」
        if (hasAnomaly(graph)) {
            stableTickCount[graph.id] = 0
            return false
        }

        // 计数器自增
        val newCount = (stableTickCount[graph.id] ?: 0) + 1
        stableTickCount[graph.id] = newCount

        if (newCount < REQUIRED_STABLE_TICKS_FOR_UPGRADE) {
            if (LOGGER.isTraceEnabled) {
                LOGGER.trace(
                    "图 {} 稳定计数 {}/{}，暂不升级",
                    graph.id,
                    newCount,
                    REQUIRED_STABLE_TICKS_FOR_UPGRADE
                )
            }
            return false
        }

        // 计数达标后再次确认无高级特性依赖
        if (dependsOnZeroTick(graph)) {
            LOGGER.debug("图 {} 稳定 {} tick 但仍依赖 0t，拒绝升级", graph.id, newCount)
            return false
        }
        if (graph.hasComplexTiming) {
            LOGGER.debug("图 {} 稳定 {} tick 但仍标记为复杂时序，拒绝升级", graph.id, newCount)
            return false
        }
        if (hasComplexRepeaterChain(graph)) {
            LOGGER.debug("图 {} 稳定 {} tick 但存在复杂中继器链，拒绝升级", graph.id, newCount)
            return false
        }
        if (hasPistonOcclusionDynamics(graph)) {
            LOGGER.debug("图 {} 稳定 {} tick 但存在活塞遮挡动态，拒绝升级", graph.id, newCount)
            return false
        }

        return true
    }

    /**
     * 判断 [graph] 是否需要降级。
     *
     * 判定流程：
     *  1. 已在 Level 1 → 直接返回 false
     *  2. 出现任何异常或新出现的高级特性 → 返回 true
     *
     * 「异常或新特性」包括：
     *  - 新出现的 0t 反馈环路
     *  - [RedstoneGraph.hasComplexTiming] 被标记为 true
     *  - 出现复杂中继器链
     *  - 出现活塞遮挡动态变化
     */
    fun shouldDowngrade(graph: RedstoneGraph): Boolean {
        // 已在最低级
        if (graph.optimizationLevel <= 1) return false

        // 检测异常或新特性
        if (graph.hasComplexTiming) {
            LOGGER.info("图 {} 标记为复杂时序，需要从 Level {} 降级", graph.id, graph.optimizationLevel)
            return true
        }
        if (dependsOnZeroTick(graph)) {
            LOGGER.info("图 {} 出现 0t 反馈环路，需要从 Level {} 降级", graph.id, graph.optimizationLevel)
            return true
        }
        if (hasComplexRepeaterChain(graph)) {
            LOGGER.info("图 {} 出现复杂中继器链，需要从 Level {} 降级", graph.id, graph.optimizationLevel)
            return true
        }
        if (hasPistonOcclusionDynamics(graph)) {
            LOGGER.info("图 {} 出现活塞遮挡动态，需要从 Level {} 降级", graph.id, graph.optimizationLevel)
            return true
        }

        return false
    }

    /**
     * 将 [graph] 的优化级别提升一级（1 → 2 → 3）。
     *
     * 调用前提：调用方应先调用 [shouldUpgrade] 确认可升级。
     * 升级后重置该图的稳定 tick 计数器，给新级别一个重新积累稳定性的窗口。
     */
    fun upgrade(graph: RedstoneGraph) {
        if (graph.optimizationLevel >= 3) {
            LOGGER.warn("图 {} 已在 Level 3，无法继续升级", graph.id)
            return
        }
        val oldLevel = graph.optimizationLevel
        graph.optimizationLevel = oldLevel + 1
        // 升级后重置计数器，重新积累稳定性
        stableTickCount[graph.id] = 0
        LOGGER.info(
            "图 {} 优化级别升级：Level {} -> Level {}（共 {} 个节点）",
            graph.id,
            oldLevel,
            graph.optimizationLevel,
            graph.getSize()
        )
    }

    /**
     * 将 [graph] 的优化级别降低一级（3 → 2 → 1）。
     *
     * 调用前提：调用方应先调用 [shouldDowngrade] 确认需要降级。
     * 降级后重置稳定 tick 计数器，避免立即再次升级。
     */
    fun downgrade(graph: RedstoneGraph) {
        if (graph.optimizationLevel <= 1) {
            LOGGER.warn("图 {} 已在 Level 1，无法继续降级", graph.id)
            return
        }
        val oldLevel = graph.optimizationLevel
        graph.optimizationLevel = oldLevel - 1
        // 降级后重置计数器，避免立刻又升回去
        stableTickCount[graph.id] = 0
        LOGGER.warn(
            "图 {} 优化级别降级：Level {} -> Level {}（共 {} 个节点）",
            graph.id,
            oldLevel,
            graph.optimizationLevel,
            graph.getSize()
        )
    }

    /**
     * 检测当前 tick 是否存在异常（用于 [shouldUpgrade] 中判断稳定性）。
     *
     * 异常来源与 [shouldDowngrade] 一致：任何高级特性的存在都视为异常。
     */
    private fun hasAnomaly(graph: RedstoneGraph): Boolean {
        if (graph.hasComplexTiming) return true
        if (dependsOnZeroTick(graph)) return true
        if (hasComplexRepeaterChain(graph)) return true
        if (hasPistonOcclusionDynamics(graph)) return true
        return false
    }

    /**
     * 判断图是否依赖 0t 反馈环路。
     *
     * 这里取 [ZeroTickDetector.detect] 的结果并筛选出真正的环路
     * （首尾节点相同的路径）。线性 0t 路径（如相邻红石粉）是常规传播，
     * 不视为「依赖 0t」，因此不会阻止升级。
     */
    private fun dependsOnZeroTick(graph: RedstoneGraph): Boolean {
        val paths = zeroTickDetector.detect(graph)
        return paths.any { it.size > 1 && it.first() == it.last() }
    }

    /**
     * 检测是否存在「复杂中继器链」。
     *
     * 简化判定：图中 [NodeType.REPEATER] 节点总数超过 [COMPLEX_REPEATER_CHAIN_THRESHOLD]
     * 即视为复杂。实际工程中可进一步通过 DFS 求最长中继器链长度，这里为降低开销使用计数。
     */
    private fun hasComplexRepeaterChain(graph: RedstoneGraph): Boolean {
        val repeaterCount = graph.nodes.values.count { it.type == NodeType.REPEATER }
        return repeaterCount > COMPLEX_REPEATER_CHAIN_THRESHOLD
    }

    /**
     * 检测是否存在活塞遮挡动态变化。
     *
     * 当前 [com.rhenium.optimization.graph.GraphNode] 契约未直接携带活塞状态字段，
     * 这里以 [RedstoneGraph.hasComplexTiming] 作为代理信号：若图被标记为复杂时序，
     * 则保守认为可能存在活塞遮挡动态。
     *
     * 后续若 graph 包增加活塞相关字段，可在此替换为精确判定。
     */
    private fun hasPistonOcclusionDynamics(graph: RedstoneGraph): Boolean {
        return graph.hasComplexTiming
    }
}
