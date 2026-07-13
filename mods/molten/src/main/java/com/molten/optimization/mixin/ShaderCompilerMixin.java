package com.molten.optimization.mixin;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "com.mojang.blaze3d.platform.GlStateManager")
public class ShaderCompilerMixin {}
