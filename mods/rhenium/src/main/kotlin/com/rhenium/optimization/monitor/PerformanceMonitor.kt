package com.rhenium.optimization.monitor

import com.rhenium.optimization.RheniumMod
import com.rhenium.optimization.graph.RedstoneGraph
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * 性能监控器
 *
 * 负责收集与查询 Rhenium 运行时性能指标，包括：
 * - 图处理耗时
 * - 当前图数量与总节点数
 * - 缓存命中率（委托给 [RheniumMod.CACHE]）
 * - 各优化级别的图数量分布
 *
 * 使用最近 100 tick 的滑动窗口计算平均值，线程安全通过
 * [AtomicLong] 与 [ConcurrentLinkedQueue] / [ConcurrentHashMap] 提供。
 *
 * 使用方式：
 * 1. 每次图处理完成时调用 [onGraphProcessed]，传入图对象与处理耗时（纳秒）；
 * 2. 每个 game tick 结束时调用 [endTick]，将累计数据归档到滑动窗口；
 * 3. HUD 通过 [getAverageTickTime] / [getGraphCount] 等方法查询指标。
 */
object PerformanceMonitor {

    /** 滑动窗口大小：保留最近 100 tick 的数据 */
    private const val WINDOW_SIZE = 100

    /** 当前 tick 内累计图处理耗时（纳秒） */
    private val currentTickTimeNanos = AtomicLong(0L)

    /** 当前 tick 内已处理图数量 */
    private val currentTickGraphCount = AtomicLong(0L)

    /** 当前 tick 内累计节点数 */
    private val currentTickNodeCount = AtomicLong(0L)

    /** 当前 tick 内各优化级别的图数量分布（key = optimizationLevel, value = count） */
    private val currentTickLevelDist = ConcurrentHashMap<Int, AtomicLong>()

    /**
     * 滑动窗口中的历史 tick 摘要。
     * 每个元素是一个 [TickSummary]，记录单 tick 的统计信息。
     */
    private val tickWindow = ConcurrentLinkedQueue<TickSummary>()

    /** 当前活动图数量（与 tick 无关，由其他模块通过 [setActiveGraphCount] 维护） */
    private val activeGraphCount = AtomicLong(0L)

    /** 当前活动图的总节点数（与 tick 无关，由其他模块通过 [setActiveGraphCount] 维护） */
    private val activeNodeCount = AtomicLong(0L)

    /** 当前活动图按优化级别的分布 */
    private val activeLevelDist = ConcurrentHashMap<Int, AtomicLong>()

    /**
     * 单个 tick 的统计摘要
     *
     * @property timeNanos        该 tick 内累计红石图处理耗时（纳秒）
     * @property graphCount       该 tick 内处理的图数量
     * @property nodeCount        该 tick 内处理的总节点数
     * @property levelDistribution 该 tick 内各优化级别的图数量分布
     */
    private data class TickSummary(
        val timeNanos: Long,
        val graphCount: Long,
        val nodeCount: Long,
        val levelDistribution: Map<Int, Long>
    )

    /**
     * 记录一次图处理事件。
     *
     * 由 OptimizationEngine 在每次处理完一个图后调用。
     * 在当前 tick 内累加：处理耗时、图数量、节点数与优化级别分布。
     *
     * @param graph       被处理的红石图
     * @param timeNanos   本次处理耗时（纳秒）
     */
    fun onGraphProcessed(graph: RedstoneGraph, timeNanos: Long) {
        currentTickTimeNanos.addAndGet(timeNanos)
        currentTickGraphCount.incrementAndGet()
        currentTickNodeCount.addAndGet(graph.getSize().toLong())
        currentTickLevelDist
            .computeIfAbsent(graph.optimizationLevel) { AtomicLong(0L) }
            .incrementAndGet()
    }

    /**
     * 标记一个 game tick 结束。
     *
     * 将当前 tick 内累计的统计归档到滑动窗口，并重置累计器。
     * 应由主线程在 tick 末尾调用。
     */
    fun endTick() {
        val summary = TickSummary(
            timeNanos = currentTickTimeNanos.getAndSet(0L),
            graphCount = currentTickGraphCount.getAndSet(0L),
            nodeCount = currentTickNodeCount.getAndSet(0L),
            levelDistribution = currentTickLevelDist.entries.associate { (k, v) -> k to v.getAndSet(0L) }
        )
        tickWindow.add(summary)
        // 维护滑动窗口大小
        while (tickWindow.size > WINDOW_SIZE) {
            tickWindow.poll()
        }
    }

    /**
     * 设置当前活动图数量与总节点数。
     *
     * 由 graph 包在图被创建/销毁时调用，用于反映当前内存中活动图的总览。
     *
     * @param graphCount  当前活动图数量
     * @param nodeCount   当前活动图的总节点数
     */
    fun setActiveGraphCount(graphCount: Long, nodeCount: Long) {
        activeGraphCount.set(graphCount)
        activeNodeCount.set(nodeCount)
    }

    /**
     * 设置当前活动图按优化级别的分布。
     *
     * 由 graph 包在图被创建/销毁时调用。
     *
     * @param distribution  优化级别 → 图数量 的映射
     */
    fun setActiveLevelDistribution(distribution: Map<Int, Long>) {
        activeLevelDist.clear()
        distribution.forEach { (level, count) ->
            activeLevelDist[level] = AtomicLong(count)
        }
    }

    /**
     * 获取最近 100 tick 的平均红石处理耗时（纳秒）。
     *
     * @return 平均耗时（纳秒）；无数据时返回 0
     */
    fun getAverageTickTime(): Long {
        var total = 0L
        var count = 0L
        for (summary in tickWindow) {
            total += summary.timeNanos
            count++
        }
        return if (count == 0L) 0L else total / count
    }

    /**
     * 获取平均红石处理耗时（毫秒），便于 HUD 显示。
     *
     * @return 平均耗时（毫秒）；无数据时返回 0.0
     */
    fun getAverageTickTimeMillis(): Double {
        val nanos = getAverageTickTime()
        return nanos / 1_000_000.0
    }

    /**
     * 获取当前活动图数量。
     *
     * @return 当前活动图数量
     */
    fun getGraphCount(): Long = activeGraphCount.get()

    /**
     * 获取当前活动图的总节点数。
     *
     * @return 当前活动图的总节点数
     */
    fun getTotalNodeCount(): Long = activeNodeCount.get()

    /**
     * 获取缓存命中率。
     *
     * 委托给 [RheniumMod.CACHE]，若 RheniumMod 未就绪则返回 0.0。
     *
     * @return 缓存命中率（0.0 ~ 1.0）
     */
    fun getCacheHitRate(): Double {
        return try {
            RheniumMod.CACHE.getHitRate()
        } catch (throwable: Throwable) {
            // RheniumMod 可能尚未初始化
            0.0
        }
    }

    /**
     * 获取当前活动图按优化级别的分布。
     *
     * @return 优化级别 → 图数量 的不可变映射
     */
    fun getOptimizationLevelDistribution(): Map<Int, Long> {
        return activeLevelDist.entries.associate { (k, v) -> k to v.get() }
    }

    /**
     * 获取滑动窗口中保留的 tick 数量。
     *
     * 用于诊断：当返回值小于 [WINDOW_SIZE] 时表示模组启动后尚未跑满 100 tick。
     *
     * @return 滑动窗口内 tick 摘要数量
     */
    fun getWindowSize(): Int = tickWindow.size

    /**
     * 重置所有监控数据。
     *
     * 用于配置变更或调试场景，清空所有累计与历史数据。
     */
    fun reset() {
        currentTickTimeNanos.set(0L)
        currentTickGraphCount.set(0L)
        currentTickNodeCount.set(0L)
        currentTickLevelDist.clear()
        tickWindow.clear()
        activeGraphCount.set(0L)
        activeNodeCount.set(0L)
        activeLevelDist.clear()
    }
}
