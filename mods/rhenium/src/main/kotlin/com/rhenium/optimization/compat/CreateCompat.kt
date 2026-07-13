package com.rhenium.optimization.compat

import com.rhenium.optimization.RheniumMod
import net.minecraft.world.level.block.state.BlockState

/**
 * Create 兼容处理器
 *
 * Create 模组添加了大量机械与红石元件（如转速计、压力计、机械轴承等），
 * 这些元件的红石交互通常依赖精确的 tick 时序与自定义信号路径。
 * 当检测到 Create 已安装时，Rhenium 需要：
 *
 * 1. 对 Create 的自定义红石元件保持原版行为，不进行任何优化；
 * 2. 确保 Create 的红石信号交互不被破坏；
 * 3. 通过 [shouldOptimizeBlock] 接口供其他模块查询某个方块是否可被优化。
 *
 * 通过 [com.rhenium.optimization.config.RheniumConfig.compatCreate] 控制是否启用兼容。
 *
 * 检测方式：通过方块的注册命名空间（namespace）判断是否为 Create 模组方块。
 */
object CreateCompat {

    /** Create 模组的注册命名空间 */
    private const val CREATE_NAMESPACE = "create"

    /** 是否已初始化 */
    @Volatile
    private var initialized = false

    /** 是否实际激活了 Create 兼容（即 Create 已安装且 compat 开关已开启） */
    @Volatile
    private var activated: Boolean = false

    /** 不优化的方块 ID 集合（仅记录用户/其他模块主动注册的特殊方块） */
    private val skippedBlockIds: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())

    /**
     * 初始化 Create 兼容。
     *
     * 应在 Rhenium 初始化阶段（RheniumMod.onInitialize）调用：
     * 1. 检测 Create 是否安装；
     * 2. 若已安装且 [RheniumConfig.compatCreate] 为 true，激活兼容。
     *
     * 多次调用安全：内部使用 [initialized] 标志防止重复处理。
     */
    fun onInitialize() {
        if (initialized) return
        initialized = true

        // 1. 检测 Create 是否安装
        val installed = CompatDetector.isCreateInstalled()
        if (!installed) {
            activated = false
            return
        }

        // 2. 检测兼容开关是否启用
        val compatEnabled = try {
            RheniumMod.CONFIG.compatCreate
        } catch (throwable: Throwable) {
            // RheniumMod 可能尚未就绪，默认不激活兼容
            false
        }

        activated = compatEnabled
    }

    /**
     * 判断指定的方块状态是否可以应用 Rhenium 优化。
     *
     * 当 Create 兼容激活时，所有来自 Create 命名空间的方块都返回 false，
     * 以保持 Create 自定义红石元件的原版行为。
     *
     * 其他模块在图构建 / 优化引擎处理前应调用此方法判断：
     * ```kotlin
     * if (!CreateCompat.shouldOptimizeBlock(state)) {
     *     // 走原版逻辑，不加入 Rhenium 图
     * }
     * ```
     *
     * @param state  待检查的方块状态
     * @return true 表示可应用 Rhenium 优化，false 表示应保持原版逻辑
     */
    fun shouldOptimizeBlock(state: BlockState?): Boolean {
        if (state == null) return true
        if (!isActivated()) return true

        // 通过方块注册命名空间判断是否为 Create 模组方块
        val registryName = state.block.descriptionId
        // descriptionId 形如 "block.create.speedometer"，从中提取命名空间
        // 更稳健的做法是使用 Registry.BLOCK.getKey(block)，
        // 但为避免在 Mixin 加载早期访问 Registry，此处使用 descriptionId 启发式判断
        if (isCreateBlock(state)) {
            return false
        }

        // 检查用户主动注册的跳过列表
        val blockId = state.block.toString()
        if (skippedBlockIds.contains(blockId)) {
            return false
        }

        return true
    }

    /**
     * 判断方块是否来自 Create 模组。
     *
     * 通过方块的注册 ResourceLocation 命名空间判断。
     *
     * @param state  方块状态
     * @return true 表示该方块属于 Create 模组
     */
    private fun isCreateBlock(state: BlockState): Boolean {
        return try {
            // 通过 Registry 获取方块的注册 ResourceLocation
            val key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.block)
            key != null && CREATE_NAMESPACE == key.namespace
        } catch (throwable: Throwable) {
            // 异常情况保守返回 false（不视为 Create 方块，允许优化）
            // 此分支不会破坏 Create 行为，因为优化失败时会自动回退原版
            false
        }
    }

    /**
     * 当前是否实际激活了 Create 兼容。
     *
     * @return true 表示 Create 已安装且兼容开关已开启
     */
    fun isActivated(): Boolean {
        ensureInitialized()
        return activated
    }

    /**
     * 注册一个不参与优化的方块 ID。
     *
     * 用于其他模块（如兼容性扩展）主动告知 Rhenium：某方块需保持原版行为。
     *
     * @param blockId  方块 ID（与 Block.toString() 一致）
     */
    fun skipOptimizationFor(blockId: String) {
        skippedBlockIds.add(blockId)
    }

    /**
     * 取消注册不参与优化的方块 ID。
     *
     * @param blockId  方块 ID
     */
    fun unskipOptimizationFor(blockId: String) {
        skippedBlockIds.remove(blockId)
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
