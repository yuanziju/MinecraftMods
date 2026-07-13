package com.rhenium.optimization

import com.rhenium.optimization.config.RheniumConfig
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Rhenium 客户端入口
 *
 * 负责注册客户端侧回调，包括：
 * - HUD 渲染回调（显示红石图统计、优化级别分布、Tick 耗时、缓存命中率、线程池状态）
 * - 客户端事件监听
 *
 * 引用 PerformanceMonitor（通过 RheniumMod.PERFORMANCE_MONITOR）与 HudRenderer
 * （本类持有，由其他代理实例化后注入）。
 */
object RheniumClient : ClientModInitializer {

    /** 客户端日志器 */
    private val LOGGER: Logger = LoggerFactory.getLogger("Rhenium Client")

    /**
     * HUD 渲染器（由 com.rhenium.optimization.monitor.HudRenderer 提供）。
     * 类型将在其他代理创建 HudRenderer 后替换为具体类型。
     */
    lateinit var HUD_RENDERER: Any
        private set

    override fun onInitializeClient() {
        LOGGER.info("Rhenium 客户端初始化开始...")

        registerHudCallback()

        LOGGER.info("Rhenium 客户端初始化完成 - HUD 已就绪")
    }

    /**
     * 注册 HUD 渲染回调。
     *
     * 渲染条件：
     * - 配置中 enableHud=true
     * - HUD_RENDERER 已被注入
     *
     * 实际渲染委托给 HudRenderer，本类仅负责触发与坐标传递。
     */
    private fun registerHudCallback() {
        HudRenderCallback.EVENT.register { drawContext, tickDelta ->
            val config: RheniumConfig = RheniumMod.CONFIG

            // 配置检查：未启用 HUD 时直接返回
            if (!config.enableHud) {
                return@register
            }

            // 性能监控器检查：未初始化时跳过渲染
            // 使用 try-catch 而非 isInitialized，因为后者对跨 object 的属性引用存在作用域限制
            val performanceMonitor: Any = try {
                RheniumMod.PERFORMANCE_MONITOR
            } catch (e: UninitializedPropertyAccessException) {
                return@register
            }

            // 渲染 HUD
            // TODO: 由其他代理实例化 com.rhenium.optimization.monitor.HudRenderer 后，
            //       替换下方调用为真实渲染逻辑，例如：
            //   HUD_RENDERER.render(drawContext, performanceMonitor, config.hudX, config.hudY)
            renderHud(drawContext, tickDelta, performanceMonitor, config)
        }
        LOGGER.debug("HUD 渲染回调已注册")
    }

    /**
     * HUD 渲染占位实现。
     *
     * 实际渲染逻辑由 HudRenderer 实现，此处仅作为接入点。
     * 当 HudRenderer 由其他代理创建后，本方法将被替换为对 HUD_RENDERER 的委托调用。
     *
     * @param drawContext MC 1.21 的 DrawContext
     * @param tickDelta 当前 tick 偏移
     * @param performanceMonitor 性能监控器实例
     * @param config 当前配置
     */
    private fun renderHud(
        drawContext: Any,
        tickDelta: Float,
        performanceMonitor: Any,
        config: RheniumConfig
    ) {
        // 占位：渲染逻辑由其他代理在 HudRenderer 中实现
        // 届时将变为：HUD_RENDERER.render(drawContext, performanceMonitor, config.hudX, config.hudY)
    }

    /**
     * 注入 HUD 渲染器实例。
     * 供其他代理在创建 HudRenderer 后调用。
     */
    fun injectHudRenderer(renderer: Any) {
        HUD_RENDERER = renderer
        LOGGER.debug("HUD 渲染器已注入")
    }
}
