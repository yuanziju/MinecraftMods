package com.rhenium.optimization.graph

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.*
import java.util.concurrent.atomic.AtomicLong

/**
 * 红石图构建器。
 *
 * 负责从世界中的红石元件扫描并构建 RedstoneGraph，
 * 支持全量构建和增量更新。
 */
class GraphBuilder(
    private val couplingDetector: CouplingDetector
) {
    /** 节点 ID 生成器 */
    private val nodeIdGenerator = AtomicLong(0)

    /** 图 ID 生成器 */
    private val graphIdGenerator = AtomicLong(0)

    /**
     * 从给定点开始，BFS 遍历世界中的红石元件，构建完整的图。
     *
     * @param level    世界实例
     * @param startPos 起始扫描位置
     * @return 构建完成的红石图
     */
    fun buildGraph(level: Level, startPos: BlockPos): RedstoneGraph {
        val graph = RedstoneGraph(graphIdGenerator.incrementAndGet())
        val visited = mutableSetOf<BlockPos>()
        val queue = ArrayDeque<BlockPos>()

        queue.add(startPos)
        visited.add(startPos)

        while (queue.isNotEmpty()) {
            val currentPos = queue.removeFirst()
            val state = level.getBlockState(currentPos)
            val nodeType = couplingDetector.getNodeType(state)
                ?: couplingDetector.isPoweredBlock(level, currentPos)
                .let { if (it) NodeType.POWERED_BLOCK else null }
                ?: continue

            // 创建节点
            val node = GraphNode(
                id = nodeIdGenerator.incrementAndGet(),
                pos = currentPos,
                type = nodeType
            )
            graph.addNode(node)

            // 扫描相邻位置，建立连接
            for (dir in Direction.values()) {
                val neighborPos = currentPos.relative(dir)
                if (visited.contains(neighborPos)) continue

                val edgeType = couplingDetector.detectConnection(level, currentPos, neighborPos)
                if (edgeType != null) {
                    // 邻居是红石元件，加入队列继续扫描
                    val neighborState = level.getBlockState(neighborPos)
                    if (couplingDetector.getNodeType(neighborState) != null ||
                        couplingDetector.isPoweredBlock(level, neighborPos)) {
                        queue.add(neighborPos)
                        visited.add(neighborPos)
                    }
                }
            }
        }

        // 第二次遍历：建立所有节点之间的边
        buildEdges(graph, level)

        return graph
    }

    /**
     * 增量更新图：只重新计算受影响的部分。
     *
     * 当某个方块变化时，检查该位置所在的图，
     * 只更新与该位置相关的节点和边。
     *
     * @param graph      待更新的图
     * @param level      世界实例
     * @param changedPos 发生变化的方块位置
     */
    fun updateGraph(graph: RedstoneGraph, level: Level, changedPos: BlockPos) {
        val changedNode = graph.getNodeByPos(changedPos)

        if (changedNode == null) {
            // 该位置之前不在图中，检查是否是新增的红石元件
            val state = level.getBlockState(changedPos)
            val nodeType = couplingDetector.getNodeType(state)
            if (nodeType != null) {
                // 新增节点
                val newNode = GraphNode(
                    id = nodeIdGenerator.incrementAndGet(),
                    pos = changedPos,
                    type = nodeType
                )
                graph.addNode(newNode)
                // 重新建立与新节点相关的边
                rebuildEdgesForNode(graph, level, newNode)
            }
            return
        }

        val state = level.getBlockState(changedPos)
        val newType = couplingDetector.getNodeType(state)

        if (newType == null && !couplingDetector.isPoweredBlock(level, changedPos)) {
            // 该位置的红石元件被移除，从图中删除
            graph.removeNode(changedNode.id)
            return
        }

        // 节点类型可能变化，更新类型
        if (newType != null && newType != changedNode.type) {
            // 创建新节点替换旧节点（因为 data class 不可变）
            val updatedNode = changedNode.copy(type = newType)
            graph.nodes[changedNode.id] = updatedNode
        }

        // 重新建立与该节点相关的边
        rebuildEdgesForNode(graph, level, changedNode)
    }

    /**
     * 为图中所有节点建立边连接。
     */
    private fun buildEdges(graph: RedstoneGraph, level: Level) {
        val positions = graph.nodes.values.map { it.pos }.toSet()

        for (node in graph.nodes.values) {
            for (dir in Direction.values()) {
                val neighborPos = node.pos.relative(dir)
                if (!positions.contains(neighborPos)) continue

                val edgeType = couplingDetector.detectConnection(level, node.pos, neighborPos)
                if (edgeType != null) {
                    val neighborNode = graph.getNodeByPos(neighborPos) ?: continue
                    val edge = GraphEdge(
                        from = node.id,
                        to = neighborNode.id,
                        type = edgeType,
                        direction = dir
                    )
                    graph.addEdge(edge)
                }
            }
        }
    }

    /**
     * 只为指定节点重建边连接（增量更新）。
     */
    private fun rebuildEdgesForNode(graph: RedstoneGraph, level: Level, node: GraphNode) {
        // 移除该节点相关的所有旧边
        graph.edges.removeIf { it.from == node.id || it.to == node.id }
        graph.nodes.values.firstOrNull { it.id == node.id }?.let {
            // 重建索引
        }

        // 重新建立边
        for (dir in Direction.values()) {
            val neighborPos = node.pos.relative(dir)
            val neighborNode = graph.getNodeByPos(neighborPos) ?: continue

            val edgeType = couplingDetector.detectConnection(level, node.pos, neighborPos)
            if (edgeType != null) {
                val edge = GraphEdge(
                    from = node.id,
                    to = neighborNode.id,
                    type = edgeType,
                    direction = dir
                )
                graph.addEdge(edge)
            }

            // 反向边
            val reverseEdgeType = couplingDetector.detectConnection(level, neighborPos, node.pos)
            if (reverseEdgeType != null) {
                val reverseEdge = GraphEdge(
                    from = neighborNode.id,
                    to = node.id,
                    type = reverseEdgeType,
                    direction = dir.opposite
                )
                graph.addEdge(reverseEdge)
            }
        }
    }
}
