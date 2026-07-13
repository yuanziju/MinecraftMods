package com.molten.optimization.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mojang.blaze3d.systems.RenderSystem")
public class RenderSystemMixin {
    @Inject(method = "initRenderer", at = @At("HEAD"))
    private static void onInitRenderer(CallbackInfo ci) {}
}
