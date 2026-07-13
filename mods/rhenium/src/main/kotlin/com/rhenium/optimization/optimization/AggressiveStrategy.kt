package com.rhenium.optimization.optimization

import com.rhenium.optimization.bytecode.CompiledGraph
import com.rhenium.optimization.graph.EdgeType
import com.rhenium.optimization.graph.GraphNode
import com.rhenium.optimization.graph.NodeType
import com.rhenium.optimization.graph.RedstoneGraph
import net.minecraft.world.level.Level
import org.slf4j.LoggerFactory

/**
 * Level 3 - 激进优化策略。
 *
 * 设计文档 §4.3 Level 3：
 *  - 使用预编译的字节码直接计算（[CompiledGraph]）
 *  - 跳过所有安全检查
 *  - 最大程度合并更新
 *  - 假设电路行为符合标准模式
 *  - 适用于：简单/标准电路
 *
 * 该策略假设电路已通过 [AdaptiveLevelManager] 的多轮稳定性验证，
 * 不再执行任何逐节点检测、不再做缓存命中判断，而是直接调用预编译逻辑一次性算出
 * 全图的新信号。任何安全检查都由上层（[com.rhenium.optimization.timing.TimingPreserver]）
 * 在策略返回结果后统一执行；若 [CompiledGraph] 缺失或抛出异常，则回退到等价的解释执行。
 */
class AggressiveStrategy(
    /**
     * 预编译图获取函数：按需为指定图返回 [CompiledGraph]。
     *
     * 因为 [OptimizationEngine] 的策略注册表是按 level 单例化的，而 [CompiledGraph]
     * 与具体图绑定，所以这里采用 provider 模式：每次 [update] 调用时按 graph 获取。
     * 为 null 时退化为「全量解释执行 + 最大合并」模式。
     */
    private val compiledGraphProvider: ((RedstoneGraph) -> CompiledGraph?)? = null
) : OptimizationStrategy {

    companion object {
        private val LOGGER = LoggerFactory.getLogger("Rhenium/AggressiveStrategy")
    }

    /** 激进策略固定为 Level 3 */
    override val level: Int = 3

    override fun update(graph: RedstoneGraph, level: Level): UpdateResult {
        // 优先使用预编译逻辑一次性算出全图信号
        // CompiledGraph 约定接口：compute(graph, level) -> UpdateResult
        val compiledGraph = try {
            compiledGraphProvider?.invoke(graph)
        } catch (t: Throwable) {
            LOGGER.warn("获取图 {} 的 CompiledGraph 失败: {}", graph.id, t.message)
            null
        }

        if (compiledGraph != null) {
            try {
                val compiledResult = compiledGraph.compute(graph, level)
                // 最大程度合并：同一节点的多次更新合并为最后一次（CompiledGraph 已保证去重，
                // 这里再以 LinkedHashMap 兜底，确保节点级唯一性）
                val merged = LinkedHashMap<Long, GraphUpdate>()
                for (u in compiledResult.updates) {
                    merged[u.nodeId] = u
                    // 同步节点本地的 signalStrength，保持图状态一致
                    graph.getNode(u.nodeId)?.signalStrength = u.newSignal
                }
                if (merged.isNotEmpty() && LOGGER.isDebugEnabled) {
                    LOGGER.debug(
                        "激进策略(预编译)处理图 {}：{} 个节点，产出 {} 条更新",
                        graph.id,
                        graph.getSize(),
                        merged.size
                    )
                }
                // 激进策略本身不检查时序，timingSafe 由上层 TimingPreserver 复核
                return UpdateResult(merged.values.toList(), timingSafe = true)
            } catch (t: Throwable) {
                LOGGER.warn(
                    "CompiledGraph 在图 {} 上执行失败，回退到解释执行: {}",
                    graph.id,
                    t.message,
                    t
                )
            }
        }

        // 回退路径：解释执行 + 最大合并
        val tick = currentTick(level)
        val computed = computeAllInterpreted(graph)

        val mergedUpdates = LinkedHashMap<Long, GraphUpdate>()
        for (node in graph.nodes.values) {
            val newSignal = computed[node.id] ?: continue
            if (newSignal != node.signalStrength) {
                node.signalStrength = newSignal
                mergedUpdates[node.id] = GraphUpdate(node.id, newSignal, tick)
            }
        }

        if (mergedUpdates.isNotEmpty() && LOGGER.isDebugEnabled) {
            LOGGER.debug(
                "激进策略(解释)处理图 {}：{} 个节点，产出 {} 条更新",
                graph.id,
                graph.getSize(),
                mergedUpdates.size
            )
        }

        return UpdateResult(mergedUpdates.values.toList(), timingSafe = true)
    }

    /**
     * 解释执行回退路径：当 [CompiledGraph] 不可用或抛异常时使用。
     *
     * 单遍扫描：先按入边更新 powerLevel，再按出边更新 signalStrength。
     * 这是一种简化模型，假设电路拓扑为标准模式（无 0t 跨越延迟边界、无环路时序依赖）。
     */
    private fun computeAllInterpreted(graph: RedstoneGraph): Map<Long, Int> {
        // 1. 计算 powerLevel
        for (node in graph.nodes.values) {
            node.powerLevel = computeIncomingPower(node, graph)
        }
        // 2. 计算 signalStrength
        val result = HashMap<Long, Int>(graph.getSize())
        for (node in graph.nodes.values) {
            result[node.id] = computeOutputSignal(node)
        }
        return result
    }

    /**
     * 计算节点的输入功率（与 ConservativeStrategy 等价）。
     * 注意：激进策略下若需更高性能，可由 [CompiledGraph] 直接给出。
     */
    private fun computeIncomingPower(node: GraphNode, graph: RedstoneGraph): Int {
        var maxPower = 0
        for (edge in graph.edges) {
            if (edge.to != node.id) continue
            val source = graph.getNode(edge.from) ?: continue
            val sourceSignal = source.signalStrength
            val transmitted = when (edge.type) {
                EdgeType.STRONG_POWER -> sourceSignal
                EdgeType.WEAK_POWER -> maxOf(0, sourceSignal - 1)
                EdgeType.DIRECT -> sourceSignal
            }
            if (transmitted > maxPower) {
                maxPower = transmitted
            }
        }
        return maxPower
    }

    /**
     * 由 powerLevel 推导输出信号，公式与 ConservativeStrategy 完全一致。
     */
    private fun computeOutputSignal(node: GraphNode): Int {
        return when (node.type) {
            NodeType.REDSTONE_WIRE,
            NodeType.POWERED_BLOCK,
            NodeType.DAYLIGHT_SENSOR,
            NodeType.OBSERVER -> node.powerLevel

            NodeType.REPEATER,
            NodeType.COMPARATOR -> if (node.powerLevel > 0) 15 else 0

            NodeType.TORCH -> if (node.powerLevel > 0) 0 else 15

            NodeType.DISPENSER,
            NodeType.DROPPER,
            NodeType.MINECART -> 0
        }
    }

    private fun currentTick(level: Level): Int {
        return (level.gameTime and 0x7FFFFFFFL).toInt()
    }
}
