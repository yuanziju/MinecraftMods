package com.rhenium.optimization.graph

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.piston.PistonBaseBlock
import java.util.concurrent.ConcurrentHashMap

/**
 * 动态图更新器。
 *
 * 监听世界中的方块变化事件，增量更新红石信号图，
 * 避免每次方块变化时全量重建图。
 */
class DynamicGraphUpdater(
    private val graphBuilder: GraphBuilder,
    private val couplingDetector: CouplingDetector
) {
    /** 图注册表：图 ID → RedstoneGraph */
    private val graphRegistry = ConcurrentHashMap<Long, RedstoneGraph>()

    /** 位置 → 图 ID 的反向索引（加速查找） */
    private val posToGraphId = ConcurrentHashMap<BlockPos, Long>()

    /** 节点 ID 生成器 */
    private val nodeIdGenerator = java.util.concurrent.atomic.AtomicLong(0)

    /** 图 ID 生成器 */
    private val graphIdGenerator = java.util.concurrent.atomic.AtomicLong(0)

    /**
     * 当方块发生变化时触发增量更新。
     *
     * @param level 世界实例
     * @param pos   变化的方块位置
     */
    fun onBlockChanged(level: Level, pos: BlockPos) {
        val graphId = posToGraphId[pos]
        if (graphId != null) {
            val graph = graphRegistry[graphId]
            if (graph != null) {
                // 增量更新：只重新计算受影响的部分
                graphBuilder.updateGraph(graph, level, pos)
                // 使缓存失效
                invalidateCacheForNode(graph, pos)
            }
        } else {
            // 该位置不在任何已知图中，检查是否需要新建图
            val state = level.getBlockState(pos)
            if (couplingDetector.getNodeType(state) != null) {
                val newGraph = graphBuilder.buildGraph(level, pos)
                registerGraph(newGraph)
            }
        }
    }

    /**
     * 当活塞状态变化时触发连接关系调整。
     *
     * 活塞伸出/缩回会改变信号传播路径，需要重新评估图的连通性。
     *
     * @param level 世界实例
     * @param pos   活塞位置
     */
    fun onPistonMoved(level: Level, pos: BlockPos) {
        // 活塞影响范围为周围 2 格（活塞头伸出/缩回）
        val affectedPositions = mutableSetOf<BlockPos>()
        for (dx in -2..2) {
            for (dy in -2..2) {
                for (dz in -2..2) {
                    affectedPositions.add(pos.offset(dx, dy, dz))
                }
            }
        }

        // 找到受影响的所有图
        val affectedGraphIds = affectedPositions.mapNotNull { posToGraphId[it] }.toSet()

        for (graphId in affectedGraphIds) {
            val graph = graphRegistry[graphId] ?: continue

            // 检查图的连通性是否被破坏
            if (shouldRebuildGraph(graph, level)) {
                // 重新构建图
                val firstPos = graph.nodes.values.firstOrNull()?.pos ?: continue
                unregisterGraph(graphId)
                val newGraph = graphBuilder.buildGraph(level, firstPos)
                registerGraph(newGraph)
            } else {
                // 只更新受影响的部分
                for (affectedPos in affectedPositions) {
                    graphBuilder.updateGraph(graph, level, affectedPos)
                }
            }
        }
    }

    /**
     * 当光照变化时触发相关节点重新计算。
     *
     * @param level 世界实例
     * @param pos   光照变化的位置
     */
    fun onLightChanged(level: Level, pos: BlockPos) {
        val graphId = posToGraphId[pos]
        if (graphId != null) {
            val graph = graphRegistry[graphId] ?: return
            val node = graph.getNodeByPos(pos)
            if (node != null && couplingDetector.isLightSensitive(level, pos)) {
                // 阳光传感器等光照敏感元件需要重新计算输出
                graphBuilder.updateGraph(graph, level, pos)
                invalidateCacheForNode(graph, pos)
            }
        }
    }

    /**
     * 注册一个新图到注册表。
     */
    fun registerGraph(graph: RedstoneGraph) {
        graphRegistry[graph.id] = graph
        for (node in graph.nodes.values) {
            posToGraphId[node.pos] = graph.id
        }
    }

    /**
     * 从注册表中注销一个图。
     */
    fun unregisterGraph(graphId: Long) {
        val graph = graphRegistry.remove(graphId) ?: return
        for (node in graph.nodes.values) {
            posToGraphId.remove(node.pos)
        }
    }

    /**
     * 获取指定位置所在的图。
     */
    fun getGraphAt(pos: BlockPos): RedstoneGraph? {
        val graphId = posToGraphId[pos] ?: return null
        return graphRegistry[graphId]
    }

    /**
     * 获取所有已注册的图。
     */
    fun getAllGraphs(): Collection<RedstoneGraph> = graphRegistry.values

    /**
     * 生成新的节点 ID。
     */
    fun nextNodeId(): Long = nodeIdGenerator.incrementAndGet()

    /**
     * 生成新的图 ID。
     */
    fun nextGraphId(): Long = graphIdGenerator.incrementAndGet()

    /**
     * 检查图是否需要重建（连通性被破坏）。
     *
     * 简单启发式：如果图的大小变化超过阈值（如节点增减 > 20%），则重建。
     */
    private fun shouldRebuildGraph(graph: RedstoneGraph, level: Level): Boolean {
        // 检查图中每个节点的位置是否仍然存在
        var missingCount = 0
        for (node in graph.nodes.values) {
            val state = level.getBlockState(node.pos)
            if (state.isAir) {
                missingCount++
            }
        }
        // 如果超过 20% 的节点消失，重建图
        return missingCount > graph.nodes.size * 0.2
    }

    /**
     * 使指定节点的缓存失效。
     * 实际调用由外部（如 SignalCache）提供。
     */
    private fun invalidateCacheForNode(graph: RedstoneGraph, pos: BlockPos) {
        // TODO: 当 cache 包提供 CacheInvalidator 后，委托给 CacheInvalidator
        // 目前仅为占位，实际逻辑在 CacheInvalidator.invalidateOnChange() 中实现
    }
}
