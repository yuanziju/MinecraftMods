package com.rhenium.optimization.timing

import com.rhenium.optimization.graph.EdgeType
import com.rhenium.optimization.graph.GraphEdge
import com.rhenium.optimization.graph.GraphNode
import com.rhenium.optimization.graph.NodeType
import com.rhenium.optimization.graph.RedstoneGraph
import org.slf4j.LoggerFactory

/**
 * 0t（零延迟）信号路径检测器。
 *
 * 设计文档 §10.3 描述：
 *  - 0t 路径是指同一游戏 tick 内信号可以从输入端传播到输出端的路径
 *  - 0t 路径上的节点必须强制使用 Level 1（保守）优化，避免任何顺序合并破坏 0t 语义
 *
 * 实现要点：
 *  - 仅遍历「即时传播」的边（不穿越延迟边界如中继器、火把、侦测器）
 *  - 通过 DFS 在「即时传播子图」中搜索所有可达路径
 *  - 检测到的路径会同步更新 [RedstoneGraph.hasZeroTickPath] 字段
 *  - 闭环与长度 ≥ 2 的开路径都会被识别为 0t 路径
 *
 * 上层调用者（[TimingPreserver]、AdaptiveLevelManager 等）可依据返回的节点路径列表
 * 将对应节点强制标记为 Level 1。
 */
class ZeroTickDetector {

    companion object {
        private val LOGGER = LoggerFactory.getLogger("Rhenium/ZeroTickDetector")

        /**
         * 即时传播节点类型集合：这些节点在邻居变化时不会引入游戏 tick 延迟，
         * 因此可能构成 0t 路径。
         *
         * 注意：REPEATER / TORCH / OBSERVER 会引入 1 个或多个游戏 tick 的延迟，
         * 因此作为 0t 路径的「边界」处理，不被穿透。
         */
        private val IMMEDIATE_PROPAGATORS: Set<NodeType> = setOf(
            NodeType.REDSTONE_WIRE,
            NodeType.COMPARATOR,
            NodeType.POWERED_BLOCK,
            NodeType.DAYLIGHT_SENSOR,
            NodeType.DISPENSER,
            NodeType.DROPPER,
            NodeType.MINECART
        )
    }

    /**
     * 检测 [graph] 中的所有 0t 信号路径。
     *
     * @return 0t 路径列表，每条路径由节点 ID 按传播顺序组成；同时会更新 [RedstoneGraph.hasZeroTickPath]
     */
    fun detect(graph: RedstoneGraph): List<List<Long>> {
        val paths = mutableListOf<List<Long>>()

        // 即时传播节点集合（用于快速过滤）
        val immediateNodes = graph.nodes.values.filter { isImmediateNode(it) }
        if (immediateNodes.isEmpty()) {
            graph.hasZeroTickPath = false
            return emptyList()
        }

        // 即时边邻接表：仅保留 from/to 均为即时传播节点的边
        val adjacency = HashMap<Long, MutableList<Long>>()
        for (node in immediateNodes) {
            adjacency[node.id] = mutableListOf()
        }
        for (edge in graph.edges) {
            if (isZeroTickEdge(edge, adjacency)) {
                adjacency[edge.from]!!.add(edge.to)
            }
        }

        // 从每个即时节点出发，DFS 搜索所有 0t 可达路径
        // 注意：这里允许节点重复出现在不同路径中，但同一 DFS 路径中禁止重复（避免环）
        val globalVisited = HashSet<Long>()
        for (startNode in immediateNodes) {
            val path = mutableListOf<Long>()
            dfsFindPaths(startNode.id, adjacency, path, mutableSetOf(), paths)
            globalVisited.add(startNode.id)
        }

        // 同步更新图级别的 0t 标记
        graph.hasZeroTickPath = paths.isNotEmpty()

        if (paths.isNotEmpty()) {
            LOGGER.debug("图 {} 检测到 {} 条 0t 路径", graph.id, paths.size)
        }

        return paths
    }

    /**
     * 快速判断 [graph] 中是否存在任何 0t 路径（不收集路径详情）。
     *
     * 该方法仅做存在性判定，避免 [detect] 的全量 DFS 开销，适合作为热路径的快速预检。
     */
    fun hasZeroTickPath(graph: RedstoneGraph): Boolean {
        // 优先信任图上已缓存的标记（由 detect 写入）
        if (graph.hasZeroTickPath) return true

        // 即时传播节点集合
        val immediateNodes = graph.nodes.values.filter { isImmediateNode(it) }
        if (immediateNodes.isEmpty()) return false

        val adjacency = HashMap<Long, MutableList<Long>>()
        for (node in immediateNodes) {
            adjacency[node.id] = mutableListOf()
        }
        for (edge in graph.edges) {
            if (isZeroTickEdge(edge, adjacency)) {
                adjacency[edge.from]!!.add(edge.to)
            }
        }

        // 任一节点存在「即时下游」（边数 ≥ 1）即构成至少一条 0t 路径
        for (entry in adjacency) {
            if (entry.value.isNotEmpty()) {
                graph.hasZeroTickPath = true
                return true
            }
        }
        graph.hasZeroTickPath = false
        return false
    }

    /**
     * DFS 搜索从 [currentId] 出发，在即时邻接表 [adjacency] 中可达的所有 0t 路径。
     *
     * @param currentId 当前节点 ID
     * @param adjacency 即时边邻接表
     * @param path 当前路径（用于回溯）
     * @param onPath 当前路径上的节点集合（用于环检测）
     * @param results 收集到的路径列表
     */
    private fun dfsFindPaths(
        currentId: Long,
        adjacency: Map<Long, List<Long>>,
        path: MutableList<Long>,
        onPath: MutableSet<Long>,
        results: MutableList<List<Long>>
    ) {
        // 环检测：当前节点已在路径上 -> 形成环，记录闭环并回溯
        if (currentId in onPath) {
            val cycleStart = path.indexOf(currentId)
            if (cycleStart in 0 until path.size) {
                val cyclePath = path.subList(cycleStart, path.size).toList() + currentId
                results.add(cyclePath)
            }
            return
        }

        onPath.add(currentId)
        path.add(currentId)

        val next = adjacency[currentId] ?: emptyList()
        if (next.isEmpty()) {
            // 没有即时下游：若路径长度 ≥ 2，视为一条完整的 0t 路径
            if (path.size >= 2) {
                results.add(path.toList())
            }
        } else {
            var extendedAny = false
            for (nextId in next) {
                // 避免无意义的自环重复记录
                if (nextId == currentId) continue
                extendedAny = true
                dfsFindPaths(nextId, adjacency, path, onPath, results)
            }
            // 若所有下游都是自环/已访问导致无法继续，则当前路径也作为一条 0t 路径
            if (!extendedAny && path.size >= 2) {
                results.add(path.toList())
            }
        }

        // 回溯
        path.removeAt(path.size - 1)
        onPath.remove(currentId)
    }

    /**
     * 判断节点是否为「即时传播」类型。
     */
    private fun isImmediateNode(node: GraphNode): Boolean {
        return node.type in IMMEDIATE_PROPAGATORS
    }

    /**
     * 判断一条边是否属于 0t 路径的一部分。
     *
     * 判定条件：
     *  - 边的端点必须都在即时传播节点集合中（[adjacency] 的 keys 即为该集合）
     *  - 边类型必须是 STRONG_POWER / WEAK_POWER / DIRECT 之一
     *    （这三类边都不引入游戏 tick 延迟）
     */
    private fun isZeroTickEdge(edge: GraphEdge, adjacency: Map<Long, List<Long>>): Boolean {
        if (edge.from !in adjacency || edge.to !in adjacency) return false
        return edge.type == EdgeType.STRONG_POWER ||
            edge.type == EdgeType.WEAK_POWER ||
            edge.type == EdgeType.DIRECT
    }
}
