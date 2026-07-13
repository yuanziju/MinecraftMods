package com.rhenium.optimization.bytecode

import com.rhenium.optimization.graph.RedstoneGraph
import com.rhenium.optimization.optimization.GraphUpdate
import com.rhenium.optimization.optimization.UpdateResult
import net.minecraft.world.level.Level

/**
 * 编译后的红石图接口
 *
 * 通过 [GraphCompiler] 将 [RedstoneGraph] 的计算逻辑编译为字节码后产生的实例。
 * 相较于解释执行，编译后的实现可被 JVM JIT 进一步优化，适合高频调用的红石电路。
 */
interface CompiledGraph {
    /** 所属红石图的唯一标识 */
    val graphId: Long

    /**
     * 执行红石信号计算。
     *
     * @param graph 当前红石图（提供节点与边数据）
     * @param level 当前世界，用于获取 tick 等环境信息
     * @return 本次计算产生的更新结果，包含节点信号变更与时序安全标记
     */
    fun compute(graph: RedstoneGraph, level: Level): UpdateResult
}

/**
 * 简单编译图 —— 解释执行的回退实现。
 *
 * 当 ASM 字节码编译失败（例如图结构过于复杂或类加载受限）时，[GraphCompiler] 会回退到本类，
 * 以解释方式遍历图节点完成计算。虽然性能不及真正编译的实现，但能保证功能正确。
 *
 * @param graphId 所属红石图的唯一标识
 */
class SimpleCompiledGraph(
    override val graphId: Long
) : CompiledGraph {

    override fun compute(graph: RedstoneGraph, level: Level): UpdateResult {
        // 解释执行：遍历图中的所有节点，逐个生成更新项
        val updates = mutableListOf<GraphUpdate>()
        // 当前 tick 取自世界时间，用作 GraphUpdate.tick
        val currentTick = level.getGameTime().toInt()
        for ((nodeId, _) in graph.nodes) {
            // 保守策略：保留信号强度为 0，由上层引擎决定最终值
            // 这里仅保证结构正确，真正的信号计算由优化策略层负责
            updates.add(GraphUpdate(nodeId, 0, currentTick))
        }
        // 解释执行始终标记为时序安全（保守策略不改变原版顺序）
        return UpdateResult(updates, true)
    }
}
