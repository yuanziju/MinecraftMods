package com.einsteinium.optimization.mixin;

import com.einsteinium.optimization.EinsteiniumClient;
import com.einsteinium.optimization.EinsteiniumMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.PoseStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(T entity, float yaw, float tickDelta, PoseStack poseStack, VertexConsumerProvider vertexConsumerProvider, int light, CallbackInfo ci) {
        if (!EinsteiniumMod.config.rendering.enableInstancing) {
            return;
        }

        Frustum frustum = MinecraftClient.getInstance().gameRenderer.frustum;
        if (!EinsteiniumClient.frustumCuller.isVisible(entity, frustum)) {
            ci.cancel();
            return;
        }

        EntityRenderer<T> renderer = (EntityRenderer<T>) (Object) this;

        EinsteiniumClient.instancedRenderer.collectEntity(entity, poseStack, light);
    }
}