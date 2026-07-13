package com.rhenium.optimization.optimization

import com.rhenium.optimization.cache.SignalCache
import com.rhenium.optimization.bytecode.CompiledGraph
import com.rhenium.optimization.graph.RedstoneGraph
import com.rhenium.optimization.timing.TimingPreserver
import net.minecraft.world.level.Level
import org.slf4j.LoggerFactory

// ──────────────────────────────────────────────────────────────────────────────
// optimization 包的共享契约类型（GraphUpdate / UpdateResult / OptimizationStrategy）
// 这里集中定义是为了让本包内其它文件（ConservativeStrategy 等）只需同包引用，
// 无需额外创建独立的契约文件。
// ──────────────────────────────────────────────────────────────────────────────

/**
 * 一次图节点信号更新的原子记录。
 *
 * @property nodeId    被更新的节点 ID
 * @property newSignal 新的信号强度
 * @property tick      该更新所属的游戏 tick（来自 [Level.getGameTime]）
 */
data class GraphUpdate(
    val nodeId: Long,
    val newSignal: Int,
    val tick: Int
)

/**
 * 优化策略执行结果。
 *
 * @property updates    本 tick 内需要回写到主线程的更新列表
 * @property timingSafe 时序是否安全。true 表示策略自身判断未破坏时序；
 *                      最终是否真的安全由 [OptimizationEngine] 通过
 *                      [TimingPreserver.checkTimingSafety] 复核后决定
 */
data class UpdateResult(
    val updates: List<GraphUpdate>,
    val timingSafe: Boolean
)

/**
 * 红石图优化策略接口。所有具体策略（[ConservativeStrategy] / [BalancedStrategy]
 * / [AggressiveStrategy]）均实现本接口。
 */
interface OptimizationStrategy {
    /** 该策略对应的优化级别（1 / 2 / 3） */
    val level: Int

    /**
     * 对 [graph] 执行优化计算，返回需要回写的更新列表与时序安全标志。
     *
     * @param graph 目标红石图
     * @param level 当前世界（用于读取游戏 tick、方块状态等）
     * @return 优化结果
     */
    fun update(graph: RedstoneGraph, level: Level): UpdateResult
}

// ──────────────────────────────────────────────────────────────────────────────
// OptimizationEngine
// ──────────────────────────────────────────────────────────────────────────────

/**
 * 优化引擎：根据图的优化级别选择对应策略执行，并集成 [TimingPreserver]
 * 确保所有优化不破坏原版时序。
 *
 * 设计文档 §2.1 / §10：
 *  - 三级自适应优化引擎（Level 1 保守 / Level 2 中等 / Level 3 激进）
 *  - 所有优化不得改变原版红石信号传播的相对顺序和时序行为
 *
 * 调用流程：
 *  1. [processGraph] 接收图与世界
 *  2. [selectStrategy] 按 [RedstoneGraph.optimizationLevel] 选取策略
 *  3. 调用策略 [OptimizationStrategy.update] 获得原始更新列表
 *  4. 调用 [TimingPreserver.preserveOrder] 按拓扑序重排，保持原版顺序
 *  5. 调用 [TimingPreserver.checkTimingSafety] 复核时序安全性
 *  6. 返回最终 [UpdateResult]
 */
class OptimizationEngine(
    /**
     * 可选的信号缓存实例。若提供，将注入到 [BalancedStrategy] 中启用失效缓存。
     * 为 null 时 BalancedStrategy 退化为不缓存模式。
     */
    signalCache: SignalCache? = null,
    /**
     * 可选的预编译图实例工厂。AggressiveStrategy 需要为每个图获取对应的
     * [CompiledGraph]；通过该函数按需获取，避免在引擎启动时即创建全部编译产物。
     */
    private val compiledGraphProvider: ((RedstoneGraph) -> CompiledGraph?)? = null
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger("Rhenium/OptimizationEngine")
    }

    /**
     * 策略注册表：优化级别 -> 策略实例。
     *
     * ConservativeStrategy 为无状态策略，可全局复用；
     * BalancedStrategy 持有 [signalCache] 引用，按注入的缓存实例创建一次；
     * AggressiveStrategy 持有 [compiledGraphProvider] 引用，每次 update 时按图获取
     * [CompiledGraph]。
     */
    private val strategyRegistry: Map<Int, OptimizationStrategy> = buildMap {
        put(1, ConservativeStrategy())
        put(2, BalancedStrategy(signalCache))
        put(3, AggressiveStrategy(compiledGraphProvider))
    }

    /**
     * 兜底策略：当注册表中找不到对应级别时使用 ConservativeStrategy。
     */
    private val fallbackStrategy: OptimizationStrategy = ConservativeStrategy()

    /** 时序保障器：负责保序与时序安全复核。 */
    private val timingPreserver = TimingPreserver()

    /**
     * 对 [graph] 执行优化处理。
     *
     * 处理步骤：
     *  1. 选取策略
     *  2. 执行策略，得到原始更新
     *  3. 通过 [TimingPreserver] 按拓扑序重排更新
     *  4. 复核时序安全性
     *  5. 返回最终结果
     *
     * @param graph 目标红石图
     * @param level 当前世界
     * @return 最终的更新结果；[UpdateResult.timingSafe] 为 false 时调用方应记录告警
     */
    fun processGraph(graph: RedstoneGraph, level: Level): UpdateResult {
        val strategy = selectStrategy(graph)

        if (LOGGER.isDebugEnabled) {
            LOGGER.debug(
                "处理图 {} ({} 节点，Level {}) 使用策略 {}",
                graph.id,
                graph.getSize(),
                graph.optimizationLevel,
                strategy::class.simpleName
            )
        }

        // 1. 执行策略获取原始更新
        val rawResult = try {
            strategy.update(graph, level)
        } catch (t: Throwable) {
            LOGGER.error(
                "策略 {} 在图 {} 上抛出异常，回退到保守策略: {}",
                strategy::class.simpleName,
                graph.id,
                t.message,
                t
            )
            // 任何策略异常都回退到保守策略，保证不破坏游戏
            fallbackStrategy.update(graph, level)
        }

        // 2. 按拓扑序重排，保持与原版一致的传播顺序
        val orderedUpdates = timingPreserver.preserveOrder(graph, rawResult.updates)

        // 3. 复核时序安全性
        val timingSafe = rawResult.timingSafe && timingPreserver.checkTimingSafety(graph)

        if (!timingSafe) {
            LOGGER.warn(
                "图 {} 时序不安全（策略={}, 更新数={}），调用方应考虑降级",
                graph.id,
                strategy::class.simpleName,
                orderedUpdates.size
            )
        }

        return UpdateResult(orderedUpdates, timingSafe)
    }

    /**
     * 根据 [graph] 当前的优化级别选择策略。
     *
     * 级别映射：
     *  - 1 → [ConservativeStrategy]
     *  - 2 → [BalancedStrategy]
     *  - 3 → [AggressiveStrategy]
     *
     * 若级别越界（<1 或 >3），将钳制到 [1, 3] 后再查找；
     * 仍未命中时回退到 [fallbackStrategy]（ConservativeStrategy）。
     */
    fun selectStrategy(graph: RedstoneGraph): OptimizationStrategy {
        val level = graph.optimizationLevel.coerceIn(1, 3)
        return strategyRegistry[level] ?: fallbackStrategy.also {
            LOGGER.warn("未找到 Level {} 对应的策略，回退到保守策略", level)
        }
    }

    /**
     * 为 [graph] 获取对应的 [CompiledGraph]（若 provider 已配置）。
     *
     * AggressiveStrategy 在需要预编译逻辑时可调用本方法按需获取。
     * 返回 null 表示无可用编译产物，调用方应回退到解释执行。
     */
    fun getCompiledGraph(graph: RedstoneGraph): CompiledGraph? {
        return try {
            compiledGraphProvider?.invoke(graph)
        } catch (t: Throwable) {
            LOGGER.warn("获取图 {} 的 CompiledGraph 失败: {}", graph.id, t.message)
            null
        }
    }
}
