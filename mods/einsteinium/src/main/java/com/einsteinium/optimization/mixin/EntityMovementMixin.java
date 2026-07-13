package com.einsteinium.optimization.mixin;

import com.einsteinium.optimization.EinsteiniumMod;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMovementMixin {
    @Inject(method = "move", at = @At("HEAD"))
    private void onMove(Entity.MovementType type, net.minecraft.util.math.Vec3d movement, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        EinsteiniumMod.collisionManager.updateEntityPosition(entity);
    }
}