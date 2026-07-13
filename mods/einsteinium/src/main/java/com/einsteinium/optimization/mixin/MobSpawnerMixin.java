package com.einsteinium.optimization.mixin;

import com.einsteinium.optimization.spawn.SpawnOptimizer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BaseSpawner.class)
public abstract class MobSpawnerMixin {

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void einsteinium$onServerTick(Level level, BlockPos pos, CallbackInfo ci) {
        if (!level.isClientSide) {
            BaseSpawner self = (BaseSpawner) (Object) this;
            EntityType<?> entityType = self.getSpawnerEntityType();

            if (entityType != null && !SpawnOptimizer.INSTANCE.canSpawnAt(level, pos, entityType)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void einsteinium$onTick(Level level, BlockPos pos, CallbackInfo ci) {
        if (!level.isClientSide) {
            BaseSpawner self = (BaseSpawner) (Object) this;
            EntityType<?> entityType = self.getSpawnerEntityType();

            if (entityType != null) {
                int adjustedCooldown = SpawnOptimizer.INSTANCE.adjustCooldown(level, pos, self.getSpawnDelay());
                if (adjustedCooldown > self.getSpawnDelay()) {
                    self.setSpawnDelay(adjustedCooldown);
                }
            }
        }
    }
}