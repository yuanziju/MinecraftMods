package com.rhenium.optimization.optimization

import com.rhenium.optimization.cache.SignalCache
import com.rhenium.optimization.graph.EdgeType
import com.rhenium.optimization.graph.GraphNode
import com.rhenium.optimization.graph.NodeType
import com.rhenium.optimization.graph.RedstoneGraph
import net.minecraft.world.level.Level
import org.slf4j.LoggerFactory

/**
 * Level 2 - 中等优化策略。
 *
 * 设计文档 §4.3 Level 2：
 *  - 移除对不活跃区域的重复检测
 *  - 优化常见信号传播路径
 *  - 启用失效缓存（[SignalCache]）
 *  - 不跳过微时序检测
 *  - 适用于：已验证无高级特性的电路
 *
 * 与 [ConservativeStrategy] 相比，本策略仅对「输入发生变化」的节点重新计算输出，
 * 输入未变的节点直接复用 [SignalCache] 中缓存的结果。这样在保证微时序不变的前提下，
 * 显著减少大规模电路的重复计算量。
 *
 * 重要：本策略不跳过任何微时序检测，[UpdateResult.timingSafe] 由调用方
 * （[OptimizationEngine] 通过 [TimingPreserver]）最终判定。
 */
class BalancedStrategy(
    /**
     * 失效缓存。若为 null（cache 包未启用），则降级为「逐节点重算」模式，
     * 行为等价于 [ConservativeStrategy]，仅保留 Level 2 的更新去重语义。
     */
    private val signalCache: SignalCache? = null
) : OptimizationStrategy {

    companion object {
        private val LOGGER = LoggerFactory.getLogger("Rhenium/BalancedStrategy")
    }

    /** 中等策略固定为 Level 2 */
    override val level: Int = 2

    override fun update(graph: RedstoneGraph, level: Level): UpdateResult {
        val tick = currentTick(level)
        val mergedUpdates = LinkedHashMap<Long, GraphUpdate>()

        // 1. 第一遍：识别脏节点（输入功率发生变化的节点）
        //    不活跃区域（输入未变）会被跳过，避免重复检测
        val dirtyNodeIds = HashSet<Long>()
        for (node in graph.nodes.values) {
            val newPower = computeIncomingPower(node, graph)
            if (newPower != node.powerLevel) {
                node.powerLevel = newPower
                dirtyNodeIds.add(node.id)
                // 输入变化 -> 该节点的下游缓存需要失效
                signalCache?.invalidate(node.id)
            }
        }

        if (dirtyNodeIds.isEmpty()) {
            // 没有任何输入变化：所有节点都使用缓存值，无需产生更新
            if (LOGGER.isTraceEnabled) {
                LOGGER.trace("图 {} 无脏节点，跳过计算", graph.id)
            }
            return UpdateResult(emptyList(), timingSafe = true)
        }

        // 2. 第二遍：传播失效到下游（基于图结构的增量更新）
        //    对每个脏节点的下游也标记为脏，确保一致性
        val allDirty = propagateDirty(graph, dirtyNodeIds)

        // 3. 第三遍：仅对脏节点重新计算输出信号
        for (nodeId in allDirty) {
            val node = graph.getNode(nodeId) ?: continue
            // 尝试命中缓存：如果该节点输入未变（已传播但本身输入不变），直接复用
            val cached = signalCache?.get(node.id)
            val newSignal = cached ?: computeOutputSignal(node)
            if (newSignal != node.signalStrength) {
                node.signalStrength = newSignal
                mergedUpdates[node.id] = GraphUpdate(node.id, newSignal, tick)
            }
            // 写回缓存
            signalCache?.put(node.id, newSignal)
        }

        if (mergedUpdates.isNotEmpty() && LOGGER.isDebugEnabled) {
            LOGGER.debug(
                "中等策略处理图 {}：总 {} 节点，脏 {} 节点，产出 {} 条更新",
                graph.id,
                graph.getSize(),
                allDirty.size,
                mergedUpdates.size
            )
        }

        // 中等策略不跳过微时序检测：交给 TimingPreserver 做最终判定
        // 这里返回 timingSafe=true 仅表示策略自身未发现时序问题，
        // 上层 [OptimizationEngine] 会再调用 TimingPreserver.checkTimingSafety 复核
        return UpdateResult(mergedUpdates.values.toList(), timingSafe = true)
    }

    /**
     * 增量失效传播：从 [dirtyNodeIds] 出发，沿出边把所有下游节点加入脏集合。
     *
     * 设计文档 §8.2：当节点的输入发生变化时，使该节点及其下游节点的缓存失效。
     */
    private fun propagateDirty(graph: RedstoneGraph, dirtyNodeIds: Set<Long>): Set<Long> {
        val result = HashSet(dirtyNodeIds)
        val queue = ArrayDeque(dirtyNodeIds)
        while (queue.isNotEmpty()) {
            val current = queue.removeLast()
            for (edge in graph.edges) {
                if (edge.from == current && edge.to !in result) {
                    result.add(edge.to)
                    queue.add(edge.to)
                }
            }
        }
        return result
    }

    /**
     * 计算节点输入功率：取所有入边传输信号的最大值。
     * 与 [ConservativeStrategy] 中的实现等价，保证语义一致。
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
     * 由 powerLevel 推导输出信号，公式与 [ConservativeStrategy] 完全一致，
     * 避免不同策略下产生不一致的结果。
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
