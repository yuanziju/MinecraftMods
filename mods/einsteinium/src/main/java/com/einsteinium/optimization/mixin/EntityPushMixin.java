package com.einsteinium.optimization.mixin;

import com.einsteinium.optimization.collision.CollisionSpatialManager;
import com.einsteinium.optimization.EinsteiniumMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntityPushMixin {

    @Shadow
    public Level level;

    @Shadow
    public abstract EntityType<?> getType();

    @Shadow
    public abstract BlockPos blockPosition();

    @Redirect(method = "push", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<Entity> redirectGetEntities(Level level, Class<? extends Entity> clazz, net.minecraft.world.phys.AABB aabb) {
        Entity entity = (Entity) (Object) this;
        if (entity.level.isClientSide) {
            return level.getEntitiesOfClass(clazz, aabb);
        }

        CollisionSpatialManager manager = EinsteiniumMod.getCollisionManager();
        if (manager == null) {
            return level.getEntitiesOfClass(clazz, aabb);
        }

        if (manager.checkDensityLimit(entity.blockPosition(), entity)) {
            return List.of();
        }

        return manager.getPotentialCollisions(entity);
    }
}