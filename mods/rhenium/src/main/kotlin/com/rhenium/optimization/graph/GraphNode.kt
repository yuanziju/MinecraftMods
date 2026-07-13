package com.rhenium.optimization.graph

import net.minecraft.core.BlockPos

/**
 * 红石元件节点类型枚举。
 *
 * 涵盖原版所有可与红石系统交互的方块类型，
 * 用于图构建时识别节点性质并选择对应的优化策略。
 */
enum class NodeType {
    /** 红石粉 — 最基础的信号传输元件 */
    REDSTONE_WIRE,
    /** 红石中继器 — 带延迟的信号中继 */
    REPEATER,
    /** 红石比较器 — 可比较/减法模式的信号元件 */
    COMPARATOR,
    /** 投掷器 — 红石激活时投掷物品 */
    DROPPER,
    /** 发射器 — 红石激活时发射物品/实体 */
    DISPENSER,
    /** 红石矿车 — 铁轨上的移动信号源 */
    MINECART,
    /** 被充能的实体方块 — 强/弱充能后激活相邻元件 */
    POWERED_BLOCK,
    /** 红石火把 — 信号反转器 */
    TORCH,
    /** 侦测器 — 检测相邻方块状态变化 */
    OBSERVER,
    /** 阳光传感器 — 输出与光照强度相关的信号 */
    DAYLIGHT_SENSOR
}

/**
 * 红石信号图节点。
 *
 * 每个节点对应世界中的一个红石元件或可被充能的方块。
 * 节点持有其在世界中的位置、类型以及当前的信号状态。
 *
 * @param id            全局唯一节点标识（由 AtomicLong 递增生成）
 * @param pos           世界中的方块坐标
 * @param type          节点类型（决定优化策略）
 * @param signalStrength 当前信号强度（0-15）
 * @param powerLevel    当前充能等级（0-15，用于比较器等）
 */
data class GraphNode(
    val id: Long,
    val pos: BlockPos,
    val type: NodeType,
    var signalStrength: Int = 0,
    var powerLevel: Int = 0
) {
    /** 当前是否被激活（信号强度 > 0） */
    fun isPowered(): Boolean = signalStrength > 0

    /** 当前信号强度是否为满强度（15） */
    fun isFullyPowered(): Boolean = signalStrength >= 15

    /** 判断是否为延迟类元件（中继器、比较器） */
    fun isDelayed(): Boolean = type == NodeType.REPEATER || type == NodeType.COMPARATOR

    /** 判断是否为移动类元件（矿车） */
    fun isMoving(): Boolean = type == NodeType.MINECART

    /** 判断是否为可直接处理物品的元件（投掷器、发射器） */
    fun isDispenserLike(): Boolean = type == NodeType.DROPPER || type == NodeType.DISPENSER

    /** 判断是否为被动信号源（火把、阳光传感器） */
    fun isPassiveSource(): Boolean = type == NodeType.TORCH || type == NodeType.DAYLIGHT_SENSOR

    override fun toString(): String =
        "GraphNode(id=$id, pos=$pos, type=$type, signal=$signalStrength)"
}
