package com.rhenium.optimization.compat

import com.rhenium.optimization.RheniumMod

/**
 * Lithium 兼容处理器
 *
 * Lithium 是常见的服务端通用优化模组，对红石 tick 等部分有原生优化。
 * 当检测到 Lithium 已安装时，Rhenium 需采用更保守的策略，避免与 Lithium
 * 的红石优化产生冲突或重复计算。
 *
 * 兼容策略：
 * 1. 自动将最大允许优化级别限制为 2（Level 2 - 中等），
 *    不使用 Level 3 激进优化，避免与 Lithium 的红石 tick 优化重叠；
 * 2. 对 Lithium 已优化的部分（如红石粉信号传播）使用更保守的策略；
 * 3. 其他模块通过 [getMaxAllowedLevel] 查询当前允许的最大级别，
 *    在创建/更新图时按此级别进行上限裁剪。
 *
 * 通过 [com.rhenium.optimization.config.RheniumConfig.compatLithium] 控制是否启用兼容。
 */
object LithiumCompat {

    /** Lithium 未安装时的最大允许优化级别（与设计文档一致：Level 3） */
    private const val MAX_LEVEL_DEFAULT = 3

    /** Lithium 已安装时的最大允许优化级别（Level 2 - 中等，避免冲突） */
    private const val MAX_LEVEL_WITH_LITHIUM = 2

    /** 当前允许的最大优化级别（默认 3，与 Lithium 共存时降为 2） */
    @Volatile
    private var maxAllowedLevel: Int = MAX_LEVEL_DEFAULT

    /** 是否已初始化 */
    @Volatile
    private var initialized = false

    /** 是否实际激活了 Lithium 兼容（即 Lithium 已安装且 compat 开关已开启） */
    @Volatile
    private var activated: Boolean = false

    /**
     * 初始化 Lithium 兼容。
     *
     * 应在 Rhenium 初始化阶段（RheniumMod.onInitialize）调用：
     * 1. 检测 Lithium 是否安装；
     * 2. 若已安装且 [RheniumConfig.compatLithium] 为 true，激活兼容并下调最大优化级别。
     *
     * 多次调用安全：内部使用 [initialized] 标志防止重复处理。
     */
    fun onInitialize() {
        if (initialized) return
        initialized = true

        // 1. 检测 Lithium 是否安装
        val installed = CompatDetector.isLithiumInstalled()
        if (!installed) {
            // Lithium 未安装，保持默认最大级别
            maxAllowedLevel = MAX_LEVEL_DEFAULT
            activated = false
            return
        }

        // 2. 检测兼容开关是否启用
        val compatEnabled = try {
            RheniumMod.CONFIG.compatLithium
        } catch (throwable: Throwable) {
            // RheniumMod 可能尚未就绪，默认不激活兼容
            false
        }

        if (compatEnabled) {
            // 激活兼容：下调最大优化级别为 2
            maxAllowedLevel = MAX_LEVEL_WITH_LITHIUM
            activated = true
        } else {
            // 兼容未启用但 Lithium 已安装：仍保持默认最大级别
            // 用户需自行承担潜在冲突风险
            maxAllowedLevel = MAX_LEVEL_DEFAULT
            activated = false
        }
    }

    /**
     * 获取当前允许的最大优化级别。
     *
     * 其他模块（如 OptimizationEngine / AdaptiveLevelManager）在
     * 选择优化级别时，应通过此方法查询上限，避免超出兼容性约束。
     *
     * @return 当前允许的最大优化级别（1 ~ 3）
     */
    fun getMaxAllowedLevel(): Int {
        ensureInitialized()
        return maxAllowedLevel
    }

    /**
     * 当前是否实际激活了 Lithium 兼容。
     *
     * @return true 表示 Lithium 已安装且兼容开关已开启
     */
    fun isActivated(): Boolean {
        ensureInitialized()
        return activated
    }

    /**
     * 判断指定的优化级别是否在当前允许范围内。
     *
     * 用于图创建 / 自适应升级时的级别校验。
     *
     * @param level  待校验的优化级别
     * @return true 表示该级别在允许范围内
     */
    fun isLevelAllowed(level: Int): Boolean {
        ensureInitialized()
        return level in 1..maxAllowedLevel
    }

    /**
     * 将指定的优化级别裁剪到允许范围内。
     *
     * @param level 期望的优化级别
     * @return 裁剪后的优化级别（1 ~ maxAllowedLevel）
     */
    fun clampLevel(level: Int): Int {
        ensureInitialized()
        return when {
            level < 1 -> 1
            level > maxAllowedLevel -> maxAllowedLevel
            else -> level
        }
    }

    /**
     * 确保已初始化。
     *
     * 若 [onInitialize] 未被显式调用，懒加载触发一次。
     */
    private fun ensureInitialized() {
        if (!initialized) {
            onInitialize()
        }
    }
}
