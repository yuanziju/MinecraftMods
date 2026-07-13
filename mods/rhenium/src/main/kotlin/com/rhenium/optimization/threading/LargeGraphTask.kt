package com.rhenium.optimization.threading

import com.rhenium.optimization.graph.RedstoneGraph
import com.rhenium.optimization.optimization.UpdateResult
import java.util.concurrent.Callable

/**
 * 大图计算任务 —— 封装大图（>100 节点）的红石信号计算。
 *
 * 大图由于节点数量多、计算复杂，需要在独立线程中执行以避免阻塞主线程。
 * 本任务严格按原版更新顺序计算：在执行实际计算前，先对图进行拓扑排序，
 * 确保节点的处理顺序与原版红石信号传播顺序一致，从而保持微时序和 0t 电路兼容性。
 *
 * @param graph 待计算的大图
 * @param task 实际的计算逻辑（由优化策略层提供），返回 [UpdateResult]
 */
class LargeGraphTask(
    private val graph: RedstoneGraph,
    private val task: () -> UpdateResult
) : Callable<UpdateResult> {

    /**
     * 执行大图计算。
     *
     * 步骤：
     * 1. 对图进行拓扑排序，确定节点的处理顺序（与原版一致）
     * 2. 调用 [task] 执行实际的红石信号计算
     *
     * @return 计算产生的更新结果
     */
    fun run(): UpdateResult {
        // 先进行拓扑排序，确保严格按原版顺序计算
        // 拓扑排序结果确定了节点的处理顺序，保证微时序安全
        topologicalSort()
        // 执行实际计算任务（task 内部按拓扑顺序处理节点）
        return task()
    }

    /**
     * [Callable] 接口实现，委托给 [run]。
     * 使本任务可直接提交到 [java.util.concurrent.ExecutorService]。
     */
    override fun call(): UpdateResult = run()

    /**
     * 对图进行拓扑排序。
     *
     * 使用 DFS（深度优先搜索）后序遍历实现拓扑排序：
     * - 先递归访问所有下游节点，再将当前节点加入结果列表
     * - 最终将结果列表反转，得到拓扑序（依赖在前，被依赖在后）
     *
     * 处理环：红石电路可能存在反馈回路（如 0t 电路），当检测到回边（节点已在当前递归栈中）时跳过，
     * 避免无限递归。这保证了即便图中存在环，也能生成一个合理的处理顺序。
     *
     * @return 拓扑排序后的节点 ID 列表
     */
    private fun topologicalSort(): List<Long> {
        val result = mutableListOf<Long>()
        // 已完全处理的节点（已加入结果列表）
        val visited = HashSet<Long>()
        // 当前递归栈中的节点（用于环检测）
        val inStack = HashSet<Long>()

        /**
         * 递归 DFS 访问节点。
         * - 若节点已完全处理，直接返回
         * - 若节点在当前递归栈中（回边），说明存在环，跳过以避免无限递归
         * - 先递归处理所有邻居（下游节点），再将当前节点加入结果列表
         */
        fun dfs(nodeId: Long) {
            if (nodeId in visited) return
            if (nodeId in inStack) return  // 检测到环，跳过回边

            inStack.add(nodeId)
            // 递归访问所有下游节点
            graph.getNeighbors(nodeId).forEach { neighbor ->
                dfs(neighbor.id)
            }
            inStack.remove(nodeId)

            visited.add(nodeId)
            // 后序：当前节点在所有下游节点之后加入
            result.add(nodeId)
        }

        // 遍历图中的所有节点，确保每个节点都被处理
        graph.nodes.keys.forEach { nodeId -> dfs(nodeId) }

        // 反转后序结果，得到拓扑序
        result.reverse()
        return result
    }
}
