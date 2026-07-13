package com.einsteinium.optimization.mixin;

import com.einsteinium.optimization.EinsteiniumMod;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        EinsteiniumMod.itemOptimizer.simplifyPhysics(itemEntity);
        EinsteiniumMod.itemOptimizer.enforceDensityLimit(itemEntity.blockPos);
    }
}