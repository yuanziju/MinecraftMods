package com.rhenium.optimization.timing

import com.rhenium.optimization.graph.RedstoneGraph
import org.slf4j.LoggerFactory
import java.util.ArrayDeque

/**
 * 拓扑排序器：对红石图中的节点进行排序，保持与原版一致的更新顺序。
 *
 * 设计目标（参考设计文档 §10.2）：
 *  - 使用 Kahn's 算法（BFS 拓扑排序）保证 DAG 上的传播顺序与原版相同
 *  - 入度为 0 的节点按方块坐标（x, y, z）的字典序出队，确保同一优先级时
 *    与原版方块坐标顺序处理一致
 *  - 检测到环路时避免死循环，将环路节点附加到结果末尾并发出告警
 *
 * 该排序结果用于：
 *  - [TimingPreserver.preserveOrder] 中对更新列表重新排序
 *  - 确保信号传播顺序与原版完全一致（不破坏微时序）
 */
class TopologicalSorter {

    companion object {
        private val LOGGER = LoggerFactory.getLogger("Rhenium/TopologicalSorter")
    }

    /**
     * 对 [graph] 中的节点执行拓扑排序。
     *
     * @return 节点 ID 的有序列表；若图中存在环路，环路节点会被附加在末尾（顺序不保证语义正确，
     *         仅供保守策略回退使用）
     */
    fun sort(graph: RedstoneGraph): List<Long> {
        val nodeIds = graph.nodes.keys

        // 入度表：每个节点的入边数量
        val inDegree = HashMap<Long, Int>(nodeIds.size)
        // 邻接表：from -> [to1, to2, ...]
        val adjacency = HashMap<Long, MutableList<Long>>(nodeIds.size)

        for (id in nodeIds) {
            inDegree[id] = 0
            adjacency[id] = mutableListOf()
        }

        // 根据图中的有向边构建邻接表与入度
        for (edge in graph.edges) {
            // 仅当两端节点都在图中时计入，避免外部节点污染
            if (inDegree.containsKey(edge.from) && inDegree.containsKey(edge.to)) {
                adjacency[edge.from]!!.add(edge.to)
                inDegree[edge.to] = inDegree[edge.to]!! + 1
            }
        }

        // 队列：始终选取坐标最小的入度 0 节点，保持原版方块顺序
        val queue = ArrayDeque<Long>()
        // 先把所有入度 0 的节点按坐标排序后入队
        val zeroInDegree = inDegree.filter { it.value == 0 }.keys
            .sortedWith(compareBy(
                { graph.getNode(it)?.pos?.x ?: 0 },
                { graph.getNode(it)?.pos?.y ?: 0 },
                { graph.getNode(it)?.pos?.z ?: 0 }
            ))
        for (id in zeroInDegree) {
            queue.add(id)
        }

        val result = ArrayList<Long>(nodeIds.size)
        while (queue.isNotEmpty()) {
            val current = queue.pollFirst()!!
            result.add(current)

            // 处理该节点的所有下游节点，更新入度
            val neighbors = adjacency[current] ?: emptyList()
            // 对邻居按坐标排序，保证同一轮次的入队顺序稳定
            val newlyAvailable = neighbors
                .mapNotNull { to ->
                    val newDeg = inDegree[to]!! - 1
                    inDegree[to] = newDeg
                    if (newDeg == 0) to else null
                }
                .sortedWith(compareBy(
                    { graph.getNode(it)?.pos?.x ?: 0 },
                    { graph.getNode(it)?.pos?.y ?: 0 },
                    { graph.getNode(it)?.pos?.z ?: 0 }
                ))
            for (id in newlyAvailable) {
                queue.add(id)
            }
        }

        // 处理环路：拓扑序未能覆盖的节点意味着存在环
        if (result.size != nodeIds.size) {
            val remaining = nodeIds.filter { it !in result.toHashSet() }
            LOGGER.warn(
                "图 {} 检测到环路（{} 个节点无法参与拓扑排序），按坐标顺序附加到末尾以保证不死循环",
                graph.id,
                remaining.size
            )
            // 环路节点按坐标排序追加，避免彻底打乱
            remaining.sortedWith(compareBy(
                { graph.getNode(it)?.pos?.x ?: 0 },
                { graph.getNode(it)?.pos?.y ?: 0 },
                { graph.getNode(it)?.pos?.z ?: 0 }
            )).forEach { result.add(it) }
        }

        return result
    }
}
