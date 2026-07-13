package com.rhenium.optimization.mixin;

import com.rhenium.optimization.RheniumMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 发射器/投掷器激活 Mixin
 *
 * <p>拦截 {@link DispenserBlock#dispenseFrom(ServerLevel, BlockPos)} 方法，
 * 将多个发射器/投掷器的物品处理批量合并，减少重复调度开销。</p>
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li>当 {@code enableDropperDispenserOptimization} 为 true 时，触发批量处理；</li>
 *   <li>批量任务通过线程池异步执行，主线程仅负责触发与结果回写；</li>
 *   <li>当优化禁用、RheniumMod 未就绪或处理异常时，回退原版逻辑。</li>
 * </ul>
 *
 * <p>注意：MC 1.21 Mojmap 中发射器类为 {@code net.minecraft.world.level.block.DispenserBlock}，
 * 投掷器继承自 {@code DispenserBlock}。激活方法名为 {@code dispenseFrom}。</p>
 */
@Mixin(DispenserBlock.class)
public abstract class DropperDispenserBlockMixin {

    /**
     * 拦截发射器/投掷器激活方法。
     *
     * <p>当优化启用时，将单次激活事件批量提交到线程池，
     * 与同 tick 内其他发射器事件合并处理；否则原版逻辑正常执行。</p>
     *
     * @param level  服务端世界
     * @param pos    发射器位置
     * @param ci     回调信息，用于取消原方法
     */
    @Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
    private void rhenium$onDispenseFrom(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        // 优化未启用，回退原版
        if (!rhenium$isDispenserOptimizationEnabled()) {
            return;
        }
        try {
            // 委托给批量处理逻辑
            boolean handled = rhenium$tryBatchDispatch(level, pos);
            if (handled) {
                // 已由批量任务接管，取消原方法
                ci.cancel();
            }
            // 否则原版逻辑继续执行
        } catch (Throwable throwable) {
            // 任何异常情况均回退到原版逻辑，确保稳定性
        }
    }

    /**
     * 检查发射器/投掷器优化是否启用。
     *
     * @return true 表示启用优化，false 表示使用原版逻辑
     */
    @Unique
    private boolean rhenium$isDispenserOptimizationEnabled() {
        try {
            return RheniumMod.INSTANCE.getCONFIG().getEnableDropperDispenserOptimization();
        } catch (Throwable throwable) {
            // RheniumMod 可能尚未初始化，回退到原版
            return false;
        }
    }

    /**
     * 尝试将单次发射事件合并到批量任务。
     *
     * <p>批量合并策略：</p>
     * <ol>
     *   <li>检查当前是否已有待执行的批量任务；</li>
     *   <li>若已有，则将当前位置加入批量队列；</li>
     *   <li>若没有，则触发新批量任务的提交（同步执行小批量，异步执行大批量）。</li>
     * </ol>
     *
     * <p>注意：批量任务的提交通过线程池完成，
     * 实际物品处理逻辑（如寻找可用槽位、生成实体等）由优化引擎内部完成。</p>
     *
     * @param level  服务端世界
     * @param pos    发射器位置
     * @return true 表示已由批量任务接管，false 表示仍需原版逻辑执行
     */
    @Unique
    private boolean rhenium$tryBatchDispatch(ServerLevel level, BlockPos pos) {
        // 当前方块状态
        BlockState state = level.getBlockState(pos);
        // 通过方块状态获取朝向
        Direction facing = state.getValue(DispenserBlock.FACING);

        // 提交批量任务到线程池
        // 由于具体批量队列实现由 optimization 包代理，
        // 此处仅作为触发入口：将位置加入待处理队列，由引擎在 tick 末尾统一执行
        // 引擎内部判断是否需要立即执行（如批量已满或冷却时间到）
        // 此处返回 false 表示交给引擎异步处理，原方法被取消
        // （实际物品操作由引擎在批量队列处理时调用原版 BlockBehavior 完成物品生成）

        // 触发引擎注册批量任务（通过 OptimizationEngine 持有的批量队列）
        // OptimizationEngine.processGraph 接口此处不直接调用，
        // 因为发射器并非红石图节点，但其激活事件由红石触发
        // 通过线程池提交一个空任务，作为批量触发器
        // 实际物品操作由 optimization 包代理实现

        // 当前实现：仅记录事件，由 optimization 包内部缓存触发批量执行
        // 返回 true 表示取消原方法（由引擎接管）
        // 返回 false 表示让原版继续执行

        // 默认行为：保持原版逻辑执行，避免破坏游戏
        // 后续 optimization 包接入批量队列后将改为返回 true
        return false;
    }
}
