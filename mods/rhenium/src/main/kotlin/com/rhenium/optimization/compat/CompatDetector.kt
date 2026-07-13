package com.rhenium.optimization.compat

import net.fabricmc.loader.api.FabricLoader

/**
 * 兼容性检测器
 *
 * 在游戏初始化阶段检测当前已安装的兼容性目标模组，
 * 为后续兼容性策略（如 Lithium / Create）提供判定依据。
 *
 * 检测使用 [FabricLoader.getInstance().isModLoaded(modId)] 完成，
 * 不依赖任何反射或其他高风险方式。
 *
 * 支持检测的模组 ID：
 * - `lithium`  → Lithium（服务端通用优化）
 * - `sodium`   → Sodium（客户端渲染优化）
 * - `create`   → Create（机械与红石扩展）
 */
object CompatDetector {

    /** Lithium 模组 ID */
    private const val MOD_ID_LITHIUM = "lithium"

    /** Sodium 模组 ID */
    private const val MOD_ID_SODIUM = "sodium"

    /** Create 模组 ID */
    private const val MOD_ID_CREATE = "create"

    /** 检测结果缓存，避免重复调用 FabricLoader */
    @Volatile
    private var initialized = false

    @Volatile
    private var lithiumInstalled = false

    @Volatile
    private var sodiumInstalled = false

    @Volatile
    private var createInstalled = false

    /**
     * 检测所有兼容性目标模组。
     *
     * 应在 Rhenium 初始化阶段调用一次（如 RheniumMod.onInitialize），
     * 检测结果会缓存到 [lithiumInstalled] / [sodiumInstalled] / [createInstalled]。
     *
     * 多次调用安全：内部使用 [initialized] 标志防止重复检测。
     */
    fun detectAll() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val loader = FabricLoader.getInstance()
            lithiumInstalled = loader.isModLoaded(MOD_ID_LITHIUM)
            sodiumInstalled = loader.isModLoaded(MOD_ID_SODIUM)
            createInstalled = loader.isModLoaded(MOD_ID_CREATE)
            initialized = true
        }
    }

    /**
     * 检测 Lithium 是否已安装。
     *
     * 若 [detectAll] 未被调用过，会自动触发一次检测。
     *
     * @return true 表示 Lithium 已安装
     */
    fun isLithiumInstalled(): Boolean {
        ensureInitialized()
        return lithiumInstalled
    }

    /**
     * 检测 Sodium 是否已安装。
     *
     * 若 [detectAll] 未被调用过，会自动触发一次检测。
     *
     * @return true 表示 Sodium 已安装
     */
    fun isSodiumInstalled(): Boolean {
        ensureInitialized()
        return sodiumInstalled
    }

    /**
     * 检测 Create 是否已安装。
     *
     * 若 [detectAll] 未被调用过，会自动触发一次检测。
     *
     * @return true 表示 Create 已安装
     */
    fun isCreateInstalled(): Boolean {
        ensureInitialized()
        return createInstalled
    }

    /**
     * 确保检测结果已初始化。
     */
    private fun ensureInitialized() {
        if (!initialized) {
            detectAll()
        }
    }

    /**
     * 获取所有兼容性目标模组的检测摘要。
     *
     * 用于调试日志与 HUD 显示。
     *
     * @return 模组名 → 是否安装 的映射
     */
    fun getSummary(): Map<String, Boolean> {
        ensureInitialized()
        return mapOf(
            "Lithium" to lithiumInstalled,
            "Sodium" to sodiumInstalled,
            "Create" to createInstalled
        )
    }
}
