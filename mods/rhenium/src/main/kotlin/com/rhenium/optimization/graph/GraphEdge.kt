package com.rhenium.optimization.graph

import net.minecraft.core.Direction

/**
 * 红石信号连接类型枚举。
 *
 * 区分不同强度的信号传递方式，影响缓存失效和优化策略的选择。
 */
enum class EdgeType {
    /**
     * 强充能连接。
     * 红石粉直接指向方块，或中继器/比较器的输出，
     * 信号强度完整传递，可激活相邻红石元件。
     */
    STRONG_POWER,

    /**
     * 弱充能连接。
     * 红石粉覆盖在方块上但未直接指向，
     * 信号强度-1传递，影响范围有限。
     */
    WEAK_POWER,

    /**
     * 直接信号连接。
     * 元件之间的直接信号传递（如红石粉相邻），
     * 不涉及方块充能过程。
     */
    DIRECT
}

/**
 * 红石信号图边。
 *
 * 每条有向边表示信号从一个节点到另一个节点的传递关系。
 * 边带有方向属性，表示信号在世界中的传播方向。
 *
 * @param from      信号源节点 ID
 * @param to        信号目标节点 ID
 * @param type      连接类型（强充能/弱充能/直接）
 * @param direction 信号在世界中的传播方向（用于时序排序）
 */
data class GraphEdge(
    val from: Long,
    val to: Long,
    val type: EdgeType,
    val direction: Direction
) {
    /** 是否为强充能边 */
    fun isStrongPower(): Boolean = type == EdgeType.STRONG_POWER

    /** 是否为弱充能边 */
    fun isWeakPower(): Boolean = type == EdgeType.WEAK_POWER

    /** 获取反向边（用于双向信号检测） */
    fun reversed(): GraphEdge = GraphEdge(to, from, type, direction.opposite)

    override fun toString(): String =
        "GraphEdge($from -> $to, type=$type, dir=$direction)"
}
