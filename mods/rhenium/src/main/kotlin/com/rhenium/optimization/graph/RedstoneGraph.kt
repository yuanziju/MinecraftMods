package com.rhenium.optimization.graph

import net.minecraft.core.BlockPos

/**
 * 红石信号图。
 *
 * 一组相互连通的红石元件及其信号连接关系的集合。
 * 每个图独立管理，支持按大小进行线程分配和分级优化。
 *
 * @param id 图的全局唯一标识
 */
class RedstoneGraph(val id: Long) {
    /** 节点集合，键为节点 ID */
    val nodes: MutableMap<Long, GraphNode> = mutableMapOf()

    /** 边集合 */
    val edges: MutableList<GraphEdge> = mutableListOf()

    /**
     * 当前优化级别。
     * 1 = 保守（首次更新/未知电路）
     * 2 = 中等（已验证无高级特性）
     * 3 = 激进（简单/标准电路）
     */
    var optimizationLevel: Int = 1

    /** 图中是否包含 0t 信号路径（一旦标记不再降级） */
    var hasZeroTickPath: Boolean = false

    /** 图中是否包含复杂时序（微时序、复杂中继器链等） */
    var hasComplexTiming: Boolean = false

    /** 当前图所在的 tick 计数（用于自适应升级） */
    var stableTickCount: Int = 0

    /** 节点 ID → 出边列表 的索引（加速查询） */
    private val outEdgesIndex: MutableMap<Long, MutableList<GraphEdge>> = mutableMapOf()

    /** 节点 ID → 入边列表 的索引 */
    private val inEdgesIndex: MutableMap<Long, MutableList<GraphEdge>> = mutableMapOf()

    /** 获取图的节点数量 */
    fun getSize(): Int = nodes.size

    /** 是否为大图（>100 节点） */
    fun isLargeGraph(): Boolean = nodes.size > 100

    /** 是否为中图（20-100 节点） */
    fun isMediumGraph(): Boolean = nodes.size in 20..100

    /** 是否为小图（<20 节点） */
    fun isSmallGraph(): Boolean = nodes.size < 20

    /** 根据 ID 获取节点 */
    fun getNode(id: Long): GraphNode? = nodes[id]

    /** 根据位置获取节点 */
    fun getNodeByPos(pos: BlockPos): GraphNode? = nodes.values.find { it.pos == pos }

    /** 添加节点 */
    fun addNode(node: GraphNode) {
        nodes[node.id] = node
    }

    /** 移除节点及其所有边 */
    fun removeNode(nodeId: Long) {
        nodes.remove(nodeId)
        // 移除所有与该节点相关的边
        edges.removeIf { it.from == nodeId || it.to == nodeId }
        rebuildIndex()
    }

    /** 添加边（自动更新索引） */
    fun addEdge(edge: GraphEdge) {
        edges.add(edge)
        outEdgesIndex.getOrPut(edge.from) { mutableListOf() }.add(edge)
        inEdgesIndex.getOrPut(edge.to) { mutableListOf() }.add(edge)
    }

    /** 移除边 */
    fun removeEdge(from: Long, to: Long) {
        edges.removeIf { it.from == from && it.to == to }
        rebuildIndex()
    }

    /** 获取从指定节点出发的所有边 */
    fun getEdgesFrom(nodeId: Long): List<GraphEdge> = outEdgesIndex[nodeId] ?: emptyList()

    /** 获取指向指定节点的所有边 */
    fun getEdgesTo(nodeId: Long): List<GraphEdge> = inEdgesIndex[nodeId] ?: emptyList()

    /** 获取邻居节点（通过边连接的节点） */
    fun getNeighbors(nodeId: Long): List<GraphNode> {
        val neighborIds = edges
            .filter { it.from == nodeId || it.to == nodeId }
            .map { if (it.from == nodeId) it.to else it.from }
            .distinct()
        return neighborIds.mapNotNull { nodes[it] }
    }

    /** 获取所有出邻居（信号传播到的节点） */
    fun getOutNeighbors(nodeId: Long): List<GraphNode> {
        return getEdgesFrom(nodeId).mapNotNull { nodes[it.to] }
    }

    /** 获取所有入邻居（信号来源节点） */
    fun getInNeighbors(nodeId: Long): List<GraphNode> {
        return getEdgesTo(nodeId).mapNotNull { nodes[it.from] }
    }

    /** 检查两个节点是否相连 */
    fun isConnected(from: Long, to: Long): Boolean {
        return edges.any { it.from == from && it.to == to }
    }

    /** 重建边索引 */
    private fun rebuildIndex() {
        outEdgesIndex.clear()
        inEdgesIndex.clear()
        for (edge in edges) {
            outEdgesIndex.getOrPut(edge.from) { mutableListOf() }.add(edge)
            inEdgesIndex.getOrPut(edge.to) { mutableListOf() }.add(edge)
        }
    }

    /** 清空图 */
    fun clear() {
        nodes.clear()
        edges.clear()
        outEdgesIndex.clear()
        inEdgesIndex.clear()
    }

    override fun toString(): String =
        "RedstoneGraph(id=$id, nodes=${nodes.size}, edges=${edges.size}, level=$optimizationLevel)"
}
