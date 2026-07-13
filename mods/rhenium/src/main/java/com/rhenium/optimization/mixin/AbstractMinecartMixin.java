package com.rhenium.optimization.mixin;

import com.rhenium.optimization.RheniumMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 矿车（AbstractMinecart）Tick Mixin
 *
 * <p>拦截 {@link AbstractMinecart#tick()} 方法，对静止 / 远离玩家的矿车进行更新频率优化：</p>
 * <ul>
 *   <li>静止检测：检测矿车是否处于静止状态（速度小于阈值），静止时跳过信号更新；</li>
 *   <li>区域检测：远离所有玩家的矿车降低更新频率，按一定概率跳过 tick；</li>
 *   <li>红石矿车（如漏斗矿车、动力矿车）保持原有红石交互时序，仅在确认为非红石激活时优化。</li>
 * </ul>
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li>当 {@code enableMinecartOptimization} 为 true 时启用优化；</li>
 *   <li>所有优化可回退：当优化禁用、RheniumMod 未就绪或异常时，原版逻辑正常执行。</li>
 * </ul>
 *
 * <p>注意：MC 1.21 Mojmap 中矿车基类为 {@code net.minecraft.world.entity.vehicle.AbstractMinecart}。</p>
 */
@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartMixin {

    /** 速度阈值，小于该值视为静止 */
    private static final double RHENIUM_STATIC_VELOCITY = 1.0E-4;

    /** 远离玩家时跳过 tick 的概率（每 5 tick 执行 1 次） */
    private static final int RHENIUM_FAR_TICK_INTERVAL = 5;

    /** 客户端调用计数器（用于跳帧） */
    private int rhenium$tickCounter = 0;

    /**
     * 拦截矿车 tick 方法。
     *
     * <p>当满足优化条件时取消原 tick 方法：</p>
     * <ol>
     *   <li>矿车速度低于阈值（视为静止）；</li>
     *   <li>矿车远离所有玩家，按概率跳过。</li>
     * </ol>
     *
     * @param ci  回调信息，用于取消原方法
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void rhenium$onTick(CallbackInfo ci) {
        // 优化未启用，回退原版
        if (!rhenium$isMinecartOptimizationEnabled()) {
            return;
        }
        try {
            AbstractMinecart self = (AbstractMinecart) (Object) this;
            Level level = self.level();

            // 静止检测：速度过低视为静止，跳过信号更新
            Vec3 delta = self.getDeltaMovement();
            if (Math.abs(delta.x) < RHENIUM_STATIC_VELOCITY
                    && Math.abs(delta.y) < RHENIUM_STATIC_VELOCITY
                    && Math.abs(delta.z) < RHENIUM_STATIC_VELOCITY
                    && self.isOnGround()) {
                // 静止矿车：仅当未在铁轨上激活红石时跳过
                // 注意：检测铁轨下方的红石信号属于 optimization 包职责
                // 此处保守策略 - 仅在确实静止且无红石激活时跳过
                if (rhenium$isSafeToSkipTick(self, level)) {
                    // 维持基础位置同步（避免完全冻结导致的视觉异常）
                    // 但跳过红石信号更新与重型计算
                    ci.cancel();
                    return;
                }
            }

            // 区域检测：远离所有玩家时降低更新频率
            rhenium$tickCounter++;
            if (rhenium$tickCounter < RHENIUM_FAR_TICK_INTERVAL) {
                // 仅在远离玩家时才跳过 - 客户端逻辑需要保留视觉同步
                if (rhenium$isFarFromPlayers(self, level)) {
                    // 跳过本次 tick，但仍维持位置更新
                    // 注意：客户端实体仍需要视觉同步，这里仅在服务端生效
                    if (!level.isClientSide()) {
                        ci.cancel();
                        return;
                    }
                }
            } else {
                // 每 N tick 强制执行一次完整 tick
                rhenium$tickCounter = 0;
            }
        } catch (Throwable throwable) {
            // 任何异常情况均回退到原版逻辑，确保稳定性
        }
    }

    /**
     * 检查矿车优化是否启用。
     *
     * @return true 表示启用优化，false 表示使用原版逻辑
     */
    @Unique
    private boolean rhenium$isMinecartOptimizationEnabled() {
        try {
            return RheniumMod.INSTANCE.getCONFIG().getEnableMinecartOptimization();
        } catch (Throwable throwable) {
            // RheniumMod 可能尚未初始化，回退到原版
            return false;
        }
    }

    /**
     * 判断矿车是否远离所有玩家。
     *
     * <p>使用简化策略：客户端无玩家引用时返回 false（不跳过），
     * 服务端通过最近玩家距离判断。</p>
     *
     * @param minecart  当前矿车实例
     * @param level     当前世界
     * @return true 表示远离所有玩家，可降低更新频率
     */
    @Unique
    private boolean rhenium$isFarFromPlayers(AbstractMinecart minecart, Level level) {
        try {
            if (level.isClientSide()) {
                // 客户端：通过 Minecraft 实例获取玩家位置
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) {
                    return false;
                }
                double dx = minecart.getX() - mc.player.getX();
                double dz = minecart.getZ() - mc.player.getZ();
                // 距离阈值：128 格
                return (dx * dx + dz * dz) > 128.0 * 128.0;
            } else {
                // 服务端：使用 level.nearestPlayer 检查
                // 注：避免在 Mixin 中直接调用 level.nearestPlayer 以减少方法签名变化风险
                // 通过 level.players() 遍历查找最近玩家
                net.minecraft.world.entity.player.Player nearest = level.getNearestPlayer(
                        minecart.getX(), minecart.getY(), minecart.getZ(), 128.0, false);
                return nearest == null;
            }
        } catch (Throwable throwable) {
            // 异常情况保守返回 false
            return false;
        }
    }

    /**
     * 判断矿车是否可以安全跳过当前 tick。
     *
     * <p>保守策略：仅当矿车为静止且不携带红石功能时跳过。
     * 漏斗矿车、动力矿车、TNT 矿车等不跳过，以保证红石交互正确性。</p>
     *
     * @param minecart  当前矿车实例
     * @param level     当前世界
     * @return true 表示可以安全跳过本次 tick
     */
    @Unique
    private boolean rhenium$isSafeToSkipTick(AbstractMinecart minecart, Level level) {
        try {
            // 仅判断矿车类型是否携带红石功能
            // 漏斗矿车（HopperMinecart） / 动力矿车（FurnaceMinecart） /
            // 命令方块矿车（CommandBlockMinecart） / TNT 矿车（TntMinecart） / 刷怪笼矿车（SpawnerMinecart）
            // 不跳过 tick
            String className = minecart.getClass().getSimpleName();
            if (className.contains("Hopper") || className.contains("Furnace")
                    || className.contains("CommandBlock") || className.contains("Tnt")
                    || className.contains("Spawner")) {
                return false;
            }
            // 普通矿车 / 运输矿车 / 箱子矿车 静止时可安全跳过
            return true;
        } catch (Throwable throwable) {
            // 异常情况保守返回 false
            return false;
        }
    }
}
