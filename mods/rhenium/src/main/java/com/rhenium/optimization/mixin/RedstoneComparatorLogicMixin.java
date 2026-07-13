package com.rhenium.optimization.mixin;

import com.rhenium.optimization.RheniumMod;
import com.rhenium.optimization.cache.SignalCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 红石比较器输出计算 Mixin
 *
 * <p>拦截 {@link ComparatorBlock#getOutputSignal(Level, BlockPos, BlockState)} 方法，
 * 使用 Rhenium 信号缓存（{@link SignalCache}）来加速比较器输出计算。</p>
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li>当 {@code enableComparatorOptimization} 为 true 时，优先使用缓存结果；</li>
 *   <li>缓存未命中或 RheniumMod 未就绪时，回退到原版计算逻辑；</li>
 *   <li>缓存命中且时序安全时，通过 CIR 直接返回缓存值，跳过原版计算。</li>
 * </ul>
 *
 * <p>注意：MC 1.21 Mojmap 中比较器类为 {@code net.minecraft.world.level.block.ComparatorBlock}，
 * 输出信号计算方法为 {@code getOutputSignal}。</p>
 */
@Mixin(ComparatorBlock.class)
public abstract class RedstoneComparatorLogicMixin {

    /**
     * 拦截比较器输出信号计算方法的入口。
     *
     * <p>当优化启用且缓存命中时，直接通过 CIR 返回缓存值；否则原版逻辑正常执行。</p>
     *
     * @param level  当前世界
     * @param pos    比较器所在位置
     * @param state  比较器方块状态
     * @param cir    回调信息，用于设置返回值
     */
    @Inject(method = "getOutputSignal", at = @At("HEAD"), cancellable = true)
    private void rhenium$onGetOutputSignal(Level level, BlockPos pos, BlockState state,
                                            CallbackInfoReturnable<Integer> cir) {
        // 优化未启用，回退原版
        if (!rhenium$isComparatorOptimizationEnabled()) {
            return;
        }
        try {
            // 检查缓存是否命中
            Integer cached = rhenium$lookupCache(level, pos);
            if (cached != null) {
                // 缓存命中：直接返回缓存值，跳过原版计算
                cir.setReturnValue(cached);
            }
            // 缓存未命中：原版逻辑继续执行，由其他模块在计算后回填缓存
        } catch (Throwable throwable) {
            // 任何异常情况均回退到原版逻辑，确保稳定性
        }
    }

    /**
     * 检查比较器优化是否启用。
     *
     * @return true 表示启用优化，false 表示使用原版逻辑
     */
    @Unique
    private boolean rhenium$isComparatorOptimizationEnabled() {
        try {
            return RheniumMod.INSTANCE.getCONFIG().getEnableComparatorOptimization();
        } catch (Throwable throwable) {
            // RheniumMod 可能尚未初始化，回退到原版
            return false;
        }
    }

    /**
     * 查询信号缓存获取比较器输出值。
     *
     * <p>缓存键由世界维度 ID 与方块位置组合而成。具体缓存键格式由 cache 包内部约定，
     * 此处仅作为查询入口，命中时返回缓存值，未命中时返回 {@code null}。</p>
     *
     * <p>注意：SignalCache 在共享契约中仅暴露 {@code getHitRate()}，
     * 实际的"位置→信号"查询由 cache 包内部扩展接口提供。
     * 此处通过反射或扩展接口获取对应缓存值，由 cache 包代理实现。</p>
     *
     * @param level  当前世界
     * @param pos    比较器所在位置
     * @return 缓存的输出信号值；未命中返回 null
     */
    @Unique
    private Integer rhenium$lookupCache(Level level, BlockPos pos) {
        // 触发 RheniumMod 初始化，并取得缓存实例
        // 由于 SignalCache 共享契约仅暴露 getHitRate()，具体查询接口由 cache 包扩展提供
        // 此处保留查询入口，命中则返回 Integer，未命中返回 null
        // 由 cache 包代理实现 (CacheInvalidator / 内部 Map)
        try {
            SignalCache cache = RheniumMod.INSTANCE.getCACHE();
            // 委托给 cache 内部查询逻辑：
            // 此处使用 cache.getHitRate() 仅用于触发类初始化
            // 实际信号查询通过 cache 包扩展接口完成
            cache.getHitRate();
            // 等待 cache 包暴露 getOutput(level, pos) 接口后接入
            // 当前默认返回 null，表示缓存未命中，回退到原版计算
            return null;
        } catch (Throwable throwable) {
            return null;
        }
    }
}
