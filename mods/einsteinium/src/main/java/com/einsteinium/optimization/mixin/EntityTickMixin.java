package com.einsteinium.optimization.mixin;

import com.einsteinium.optimization.EinsteiniumMod;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityTickMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        
        if (!EinsteiniumMod.tickScheduler.shouldTick(entity, entity.world.getTime())) {
            if (EinsteiniumMod.tickScheduler.shouldTickPhysics(entity)) {
                return;
            }
            ci.cancel();
        }
    }
}