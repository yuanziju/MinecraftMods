package com.rhenium.optimization.graph

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.piston.PistonBaseBlock
import net.minecraft.world.level.block.state.BlockState

/**
 * 耦合关系检测器。
 *
 * 负责检测红石元件之间的各种耦合关系：
 * - 强充能 / 弱充能
 * - 活塞遮挡信号路径
 * - 光照敏感元件（阳光传感器）
 * - 实体方块（可被充能的方块）
 */
class CouplingDetector {

    /**
     * 检测指定位置是否被强充能。
     *
     * 强充能来源：
     * - 红石粉直接指向该方块
     * - 红石中继器输出面朝向该方块
     * - 红石比较器输出面朝向该方块
     * - 红石火把直接激活
     *
     * @param level 世界实例
     * @param pos   待检测位置
     * @return true 如果该位置被强充能
     */
    fun detectStrongPower(level: Level, pos: BlockPos): Boolean {
        for (dir in Direction.values()) {
            val neighborPos = pos.relative(dir)
            val state = level.getBlockState(neighborPos)
            val block = state.block

            // 红石粉直接指向
            if (block is RedStoneWireBlock) {
                val wireShape = state.getValue(RedStoneWireBlock.NORTH) !=
                        RedStoneWireBlock.Side.NONE ||
                        state.getValue(RedStoneWireBlock.SOUTH) !=
                                RedStoneWireBlock.Side.NONE ||
                        state.getValue(RedStoneWireBlock.EAST) !=
                                RedStoneWireBlock.Side.NONE ||
                        state.getValue(RedStoneWireBlock.WEST) !=
                                RedStoneWireBlock.Side.NONE
                if (wireShape) return true
            }

            // 中继器/比较器输出
            if (block is RepeaterBlock || block is ComparatorBlock) {
                val facing = state.getValue(DiodeBlock.FACING)
                if (facing == dir.opposite) return true
            }

            // 红石火把
            if (block is RedstoneTorchBlock) {
                return true
            }
        }
        return false
    }

    /**
     * 检测指定位置是否被弱充能。
     *
     * 弱充能来源：
     * - 红石粉覆盖在方块上但未直接指向
     *
     * @param level 世界实例
     * @param pos   待检测位置
     * @return true 如果该位置被弱充能
     */
    fun detectWeakPower(level: Level, pos: BlockPos): Boolean {
        val state = level.getBlockState(pos.above())
        return state.block is RedStoneWireBlock
    }

    /**
     * 检测活塞是否遮挡了该位置的信号路径。
     *
     * 活塞伸出时，其活塞头会阻挡红石信号的横向传播。
     *
     * @param level 世界实例
     * @param pos   待检测位置
     * @return true 如果该位置被活塞遮挡
     */
    fun isBlockedByPiston(level: Level, pos: BlockPos): Boolean {
        for (dir in Direction.values()) {
            val neighborPos = pos.relative(dir)
            val state = level.getBlockState(neighborPos)
            val block = state.block

            // 检查是否是活塞基座且朝向该位置
            if (block is PistonBaseBlock) {
                val facing = state.getValue(PistonBaseBlock.FACING)
                if (facing == dir.opposite && state.getValue(PistonBaseBlock.EXTENDED)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * 检测指定位置上的元件是否受光照影响。
     *
     * 目前只有阳光传感器受光照影响。
     *
     * @param pos 待检测位置
     * @return true 如果该位置有光照敏感元件
     */
    fun isLightSensitive(level: Level, pos: BlockPos): Boolean {
        val state = level.getBlockState(pos)
        return state.block is DaylightDetectorBlock
    }

    /**
     * 检测指定方块是否为可被红石充能的实体方块。
     *
     * 实体方块（opaque block）可被强/弱充能，充能后可激活相邻红石元件。
     *
     * @param level 世界实例
     * @param pos   待检测位置
     * @return true 如果是实体方块
     */
    fun isPoweredBlock(level: Level, pos: BlockPos): Boolean {
        val state = level.getBlockState(pos)
        return state.isRedstoneConductor(level, pos)
    }

    /**
     * 检测两个位置之间是否存在信号连接。
     *
     * 检查：
     * 1. 是否直接相邻
     * 2. 信号是否可达（输出面朝向）
     * 3. 是否被活塞阻挡
     *
     * @param level 世界实例
     * @param from  信号源位置
     * @param to    信号目标位置
     * @return EdgeType? 连接类型，null 表示无连接
     */
    fun detectConnection(
        level: Level,
        from: BlockPos,
        to: BlockPos
    ): EdgeType? {
        // 检查是否直接相邻
        if (!from.closerThan(to, 1.5)) return null

        // 检查是否被活塞遮挡
        if (isBlockedByPiston(level, from) || isBlockedByPiston(level, to)) {
            return null
        }

        val fromState = level.getBlockState(from)
        val toState = level.getBlockState(to)
        val fromBlock = fromState.block
        val toBlock = toState.block

        // 红石粉 → 相邻元件（直接连接）
        if (fromBlock is RedStoneWireBlock) {
            return EdgeType.DIRECT
        }

        // 中继器/比较器 → 输出方向元件（强充能）
        if (fromBlock is RepeaterBlock || fromBlock is ComparatorBlock) {
            val facing = fromState.getValue(DiodeBlock.FACING)
            val outputPos = from.relative(facing)
            if (outputPos == to) return EdgeType.STRONG_POWER
        }

        // 红石火把 → 上方/相邻（强充能）
        if (fromBlock is RedstoneTorchBlock) {
            return EdgeType.STRONG_POWER
        }

        // 实体方块被充能 → 相邻元件（强/弱充能）
        if (isPoweredBlock(level, from)) {
            return if (detectStrongPower(level, from)) EdgeType.STRONG_POWER
            else if (detectWeakPower(level, from)) EdgeType.WEAK_POWER
            else null
        }

        return null
    }

    /**
     * 获取指定位置的红石元件类型。
     *
     * @param state 方块状态
     * @return NodeType? 对应的节点类型，null 表示不是红石元件
     */
    fun getNodeType(state: BlockState): NodeType? {
        return when (state.block) {
            is RedStoneWireBlock -> NodeType.REDSTONE_WIRE
            is RepeaterBlock -> NodeType.REPEATER
            is ComparatorBlock -> NodeType.COMPARATOR
            is DropperBlock -> NodeType.DROPPER
            is DispenserBlock -> NodeType.DISPENSER
            is RedstoneTorchBlock -> NodeType.TORCH
            is ObserverBlock -> NodeType.OBSERVER
            is DaylightDetectorBlock -> NodeType.DAYLIGHT_SENSOR
            else -> null
        }
    }
}
