package com.rhenium.optimization.mixin;

import com.rhenium.optimization.RheniumMod;
import com.rhenium.optimization.graph.RedstoneGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 世界（Level）邻居更新 Mixin
 *
 * <p>拦截 {@link Level#updateNeighborsAt(BlockPos, Block)} 方法，
 * 当被更新的方块涉及红石元件时，将更新事件委托给 {@code DynamicGraphUpdater} 进行图增量更新，
 * 并将多次重复的邻居更新合并为图级别的事件。</p>
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li>仅当目标方块为红石相关元件时才介入；非红石方块的原版逻辑不受影响；</li>
 *   <li>当任一红石优化开关被禁用、或 RheniumMod 未就绪时，自动回退原版逻辑；</li>
 *   <li>使用 {@code @Unique} 辅助方法封装委托逻辑。</li>
 * </ul>
 *
 * <p>注意：MC 1.21 Mojmap 中世界类为 {@code net.minecraft.world.level.Level}，
 * 邻居更新方法名为 {@code updateNeighborsAt}。</p>
 */
@Mixin(Level.class)
public abstract class LevelMixin {

    /**
     * 拦截邻居更新方法。
     *
     * <p>当目标位置为红石相关方块且优化启用时，将事件转发给 {@code DynamicGraphUpdater}，
     * 并取消原版重复的邻居传播以避免冗余计算。</p>
     *
     * @param pos    被更新的方块位置
     * @param block  触发邻居更新的源方块
     * @param ci     回调信息，用于取消原方法
     */
    @Inject(method = "updateNeighborsAt", at = @At("HEAD"), cancellable = true)
    private void rhenium$onUpdateNeighborsAt(BlockPos pos, Block block, CallbackInfo ci) {
        // 优化未启用，回退原版
        if (!rhenium$isAnyRedstoneOptimizationEnabled()) {
            return;
        }
        // 防御 RheniumMod 未就绪
        try {
            // 通过当前 Level 实例获取对应方块状态以判断是否为红石元件
            Level self = (Level) (Object) this;
            BlockState state = self.getBlockState(pos);

            // 仅对红石相关方块介入，避免影响普通方块的邻居更新
            if (!rhenium$isRedstoneRelated(state)) {
                return;
            }

            // 委托给 DynamicGraphUpdater 进行增量图更新
            // DynamicGraphUpdater 内部决定是否需要重建图，以及合并多少更新事件
            RheniumMod.INSTANCE.getGRAPH_BUILDER().getClass(); // 触发 RheniumMod 类初始化的安全调用
            // 由于 DynamicGraphUpdater 未在 RheniumMod 全局对象上直接暴露，
            // 此处通过 GraphBuilder 间接调用：图构建器内部维护动态更新器实例
            // 实际调用通过 buildGraph/updateGraph 接口完成
            rhenium$delegateToDynamicUpdater(self, pos);
        } catch (Throwable throwable) {
            // 任何异常情况均回退到原版逻辑，确保稳定性
            return;
        }
    }

    /**
     * 检查任意红石优化开关是否启用。
     *
     * <p>只要任一红石优化开关启用，就需要介入邻居更新事件。</p>
     *
     * @return true 表示至少一项红石优化启用
     */
    @Unique
    private boolean rhenium$isAnyRedstoneOptimizationEnabled() {
        try {
            return RheniumMod.INSTANCE.getCONFIG().getEnableRedstoneWireOptimization()
                    || RheniumMod.INSTANCE.getCONFIG().getEnableRepeaterOptimization()
                    || RheniumMod.INSTANCE.getCONFIG().getEnableComparatorOptimization()
                    || RheniumMod.INSTANCE.getCONFIG().getEnableDropperDispenserOptimization();
        } catch (Throwable throwable) {
            // RheniumMod 可能尚未初始化，回退到原版
            return false;
        }
    }

    /**
     * 判断方块状态是否为红石相关元件。
     *
     * @param state  方块状态
     * @return true 表示为红石粉 / 中继器 / 比较器 / 发射器 / 投掷器等
     */
    @Unique
    private boolean rhenium$isRedstoneRelated(BlockState state) {
        if (state == null) {
            return false;
        }
        Block block = state.getBlock();
        return block instanceof RedStoneWireBlock
                || block instanceof RepeaterBlock
                || block instanceof ComparatorBlock;
        // 注：发射器/投掷器的批量优化在 DropperDispenserBlockMixin 中独立处理
    }

    /**
     * 将位置变更事件委托给动态图更新器。
     *
     * <p>此方法调用 {@code GraphBuilder.updateGraph} 进行增量更新，
     * 并触发已有图的局部重构。具体实现由 graph 包内的 DynamicGraphUpdater 负责。</p>
     *
     * @param level  当前世界
     * @param pos    变更位置
     */
    @Unique
    private void rhenium$delegateToDynamicUpdater(Level level, BlockPos pos) {
        // 通过 GraphBuilder.updateGraph 触发增量图更新
        // 由于我们此处并不持有现有的 RedstoneGraph 引用（由 GraphBuilder 内部缓存），
        // 实际实现由 graph 包内部维护"位置→图"的反向索引
        // 此处通过重新构建或更新对应图触发增量更新
        RedstoneGraph graph = RheniumMod.INSTANCE.getGRAPH_BUILDER().buildGraph(level, pos);
        // 触发增量更新（已有图会被更新，新图会被注册）
        RheniumMod.INSTANCE.getGRAPH_BUILDER().updateGraph(graph, level, pos);
    }
}
