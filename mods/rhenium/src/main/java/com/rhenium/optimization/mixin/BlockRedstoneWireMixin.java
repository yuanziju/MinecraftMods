package com.rhenium.optimization.mixin;

import com.rhenium.optimization.RheniumMod;
import com.rhenium.optimization.graph.RedstoneGraph;
import com.rhenium.optimization.optimization.UpdateResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 红石粉信号更新 Mixin
 *
 * <p>拦截 {@link RedStoneWireBlock#updatePowerStrength(Level, BlockPos, BlockState)} 方法，
 * 在 HEAD 处将信号传播委托给 Rhenium 图优化引擎处理。</p>
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li>当 {@code enableRedstoneWireOptimization} 为 true 时，由图优化引擎接管信号传播；</li>
 *   <li>当配置禁用、RheniumMod 未就绪或处理异常时，自动回退到原版逻辑；</li>
 *   <li>所有处理使用 {@code @Unique} 辅助方法封装，避免污染目标类。</li>
 * </ul>
 *
 * <p>注意：MC 1.21 Mojmap 中红石粉类为 {@code net.minecraft.world.level.block.RedStoneWireBlock}，
 * 信号传播方法名为 {@code updatePowerStrength}。如方法名在后续版本变化需同步调整。</p>
 */
@Mixin(RedStoneWireBlock.class)
public abstract class BlockRedstoneWireMixin {

    /**
     * 拦截红石粉信号更新方法的入口。
     *
     * <p>当优化启用时，将信号传播委托给图优化引擎；否则原版逻辑正常执行。</p>
     *
     * @param level  当前世界
     * @param pos    红石粉所在位置
     * @param state  红石粉方块状态
     * @param ci     回调信息，用于取消原方法
     */
    @Inject(method = "updatePowerStrength", at = @At("HEAD"), cancellable = true)
    private void rhenium$onUpdatePowerStrength(Level level, BlockPos pos, BlockState state, CallbackInfo ci) {
        // 优化未启用，回退原版
        if (!rhenium$isOptimizationEnabled()) {
            return;
        }
        try {
            // 委托给图优化引擎处理信号传播
            rhenium$delegateToGraphEngine(level, pos, state);
            // 已由引擎接管，取消原版逻辑
            ci.cancel();
        } catch (Throwable throwable) {
            // 任何异常情况均回退到原版逻辑，确保稳定性
            // 不抛出异常，避免破坏游戏
        }
    }

    /**
     * 检查红石粉优化是否启用。
     *
     * <p>使用 try/catch 防御 RheniumMod 未初始化完成的情况，
     * 此时返回 false 以使用原版逻辑。</p>
     *
     * @return true 表示启用优化，false 表示使用原版逻辑
     */
    @Unique
    private boolean rhenium$isOptimizationEnabled() {
        try {
            return RheniumMod.INSTANCE.getCONFIG().getEnableRedstoneWireOptimization();
        } catch (Throwable throwable) {
            // RheniumMod 可能尚未初始化，回退到原版
            return false;
        }
    }

    /**
     * 将红石粉信号传播委托给图优化引擎。
     *
     * <p>流程：</p>
     * <ol>
     *   <li>使用 GraphBuilder 构建红石图；</li>
     *   <li>调用 OptimizationEngine 处理图，得到更新结果；</li>
     *   <li>若结果时序安全，由引擎内部直接回写世界状态（引擎持有 Level 引用）。</li>
     * </ol>
     *
     * @param level  当前世界
     * @param pos    红石粉所在位置
     * @param state  红石粉方块状态
     */
    @Unique
    private void rhenium$delegateToGraphEngine(Level level, BlockPos pos, BlockState state) {
        // 构建红石图（基于触发位置）
        RedstoneGraph graph = RheniumMod.INSTANCE.getGRAPH_BUILDER().buildGraph(level, pos);

        // 委托给优化引擎处理，引擎内部根据图大小选择同步/异步处理
        // 引擎拿到 Level 引用后会直接将计算结果回写到世界状态
        UpdateResult result = RheniumMod.INSTANCE.getOPTIMIZATION_ENGINE().processGraph(graph, level);

        // 时序安全检查：若结果不满足时序安全，意味着可能破坏原版微时序
        // 此时图优化引擎应已经内部回退，此处仅记录状态
        if (result != null && !result.getTimingSafe()) {
            // 时序不安全：保留取消状态，因为引擎已自行回退到保守模式
            // 实际应用层由其他模块负责告警/降级
        }
    }
}
