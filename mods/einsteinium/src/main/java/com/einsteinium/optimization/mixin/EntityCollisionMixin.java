package com.einsteinium.optimization.mixin;

import com.einsteinium.optimization.EinsteiniumMod;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityCollisionMixin {
    @Inject(method = "push", at = @At("HEAD"))
    private void onPush(Entity entity, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        EinsteiniumMod.collisionManager.updateEntityPosition(self);
    }
}