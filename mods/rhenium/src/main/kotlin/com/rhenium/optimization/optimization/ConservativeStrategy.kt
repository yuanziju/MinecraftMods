package com.rhenium.optimization.optimization

import com.rhenium.optimization.graph.EdgeType
import com.rhenium.optimization.graph.GraphNode
import com.rhenium.optimization.graph.NodeType
import com.rhenium.optimization.graph.RedstoneGraph
import net.minecraft.world.level.Level
import org.slf4j.LoggerFactory

/**
 * Level 1 - 保守优化策略。
 *
 * 设计文档 §4.3 Level 1：
 *  - 合并同一 tick 内对同一方块的多次更新
 *  - 不改变任何信号传播顺序
 *  - 不跳过任何检测步骤
 *  - 适用于：首次更新、未知电路
 *
 * 该策略对所有节点逐一执行与原版等价的信号计算（max 输入 + 类型映射），
 * 仅在产出 [GraphUpdate] 时做去重合并。任何节点都不会被跳过、任何边都不会被忽略。
 */
class ConservativeStrategy : OptimizationStrategy {

    companion object {
        private val LOGGER = LoggerFactory.getLogger("Rhenium/ConservativeStrategy")
    }

    /** 保守策略固定为 Level 1 */
    override val level: Int = 1

    /**
     * 对 [graph] 执行保守优化。
     *
     * @param level 当前世界（用于读取游戏 tick）
     * @return 更新结果，[UpdateResult.timingSafe] 永远为 true（保守策略不破坏时序）
     */
    override fun update(graph: RedstoneGraph, level: Level): UpdateResult {
        val tick = currentTick(level)
        // 使用 LinkedHashMap 保留首次插入顺序，同时合并同一节点在同一 tick 内的多次更新
        val mergedUpdates = LinkedHashMap<Long, GraphUpdate>()

        // 1. 先为所有节点计算新的输入功率（powerLevel）
        //    保守策略：不跳过任何节点，即使是「不活跃区域」也照常检测
        for (node in graph.nodes.values) {
            val newPower = computeIncomingPower(node, graph)
            node.powerLevel = newPower
        }

        // 2. 基于 powerLevel 计算每个节点应输出的 signalStrength
        for (node in graph.nodes.values) {
            val newSignal = computeOutputSignal(node)
            if (newSignal != node.signalStrength) {
                node.signalStrength = newSignal
                // 合并：同一节点的多次更新以最后一次为准
                mergedUpdates[node.id] = GraphUpdate(node.id, newSignal, tick)
            }
        }

        if (mergedUpdates.isNotEmpty() && LOGGER.isDebugEnabled) {
            LOGGER.debug(
                "保守策略处理图 {}：共 {} 个节点，产出 {} 条更新",
                graph.id,
                graph.getSize(),
                mergedUpdates.size
            )
        }
        // 保守策略不改顺序、不跳检测，时序恒安全
        return UpdateResult(mergedUpdates.values.toList(), timingSafe = true)
    }

    /**
     * 计算节点的输入功率：取所有入边传输信号的最大值。
     *
     * 信号传输规则（与原版一致）：
     *  - STRONG_POWER：直接传输，强度不变
     *  - WEAK_POWER：弱充能，强度 -1
     *  - DIRECT：直接连接，强度不变
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
     * 由 powerLevel 推导节点输出的 signalStrength。
     *
     * 不同节点类型的输出公式（与原版逻辑等价）：
     *  - REDSTONE_WIRE：直接透传 powerLevel（红石粉自身信号 = 接收功率）
     *  - REPEATER：>0 则输出 15，否则 0（数字信号）
     *  - COMPARATOR：>0 则输出 15（简化模型，实际减法/比较模式由 mixin 处理）
     *  - TORCH：反相输出，有输入则熄灭（0），无输入则点亮（15）
     *  - OBSERVER：透传脉冲信号
     *  - POWERED_BLOCK / DAYLIGHT_SENSOR：直接透传
     *  - DISPENSER / DROPPER / MINECART：不输出红石信号，恒为 0
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

    /**
     * 读取当前游戏 tick（限制在 Int 范围内避免溢出）。
     */
    private fun currentTick(level: Level): Int {
        return (level.gameTime and 0x7FFFFFFFL).toInt()
    }
}
