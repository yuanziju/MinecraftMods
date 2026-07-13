package com.einsteinium.optimization.debug

import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text

object PerformanceHud {
    fun render(context: DrawContext, tickDelta: Float) {
        context.drawText(context.font, Text.literal("[Einsteinium]"), 10, 10, 0xFFFFFF, false)
        context.drawText(context.font, Text.literal("Entity Count: 0"), 10, 22, 0xFFFFFF, false)
        context.drawText(context.font, Text.literal("Tick Time: 0ms"), 10, 34, 0xFFFFFF, false)
        context.drawText(context.font, Text.literal("Collision Checks: 0"), 10, 46, 0xFFFFFF, false)
    }
}