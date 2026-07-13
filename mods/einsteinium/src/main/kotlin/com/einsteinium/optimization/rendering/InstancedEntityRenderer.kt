package com.einsteinium.optimization.rendering

import com.einsteinium.optimization.EinsteiniumClient
import com.einsteinium.optimization.EinsteiniumMod
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.PoseStack
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.EntityModel
import net.minecraft.util.Identifier
import org.joml.Matrix4f
import java.util.concurrent.ConcurrentHashMap

class InstancedEntityRenderer {
    private val instancedTypes = ConcurrentHashMap<EntityType<*>, InstancedRenderInfo>()
    private val entityBuffers = ConcurrentHashMap<EntityType<*>, MutableList<EntityInstanceData>>()
    private var frameCount = 0

    fun registerInstanced(type: EntityType<*>, model: EntityModel<*>, texture: Identifier) {
        instancedTypes[type] = InstancedRenderInfo(model, texture)
    }

    fun collectEntity(entity: Entity, poseStack: PoseStack, light: Int) {
        if (!EinsteiniumMod.config.rendering.enableInstancing) return

        val type = entity.type
        if (!instancedTypes.containsKey(type)) return

        val frustum = MinecraftClient.getInstance().gameRenderer.frustum
        if (!EinsteiniumClient.frustumCuller.isVisible(entity, frustum)) return

        val player = MinecraftClient.getInstance().player ?: return
        val distance = entity.squaredDistanceTo(player)

        val lodLevel = EinsteiniumClient.lodManager.getLODLevel(entity, distance)
        if (lodLevel >= 2) return

        val instanceData = EntityInstanceData(
            entity.id,
            entity.pos.x,
            entity.pos.y,
            entity.pos.z,
            entity.yaw,
            entity.pitch,
            light,
            lodLevel
        )

        entityBuffers.computeIfAbsent(type) { mutableListOf() }.add(instanceData)
    }

    fun onWorldRenderEnd(context: WorldRenderContext) {
        if (!EinsteiniumMod.config.rendering.enableInstancing) {
            entityBuffers.clear()
            return
        }

        val poseStack = context.matrixStack()
        val buffer = context.consumers()

        poseStack.push()

        for ((type, instances) in entityBuffers) {
            if (instances.isEmpty()) continue

            val info = instancedTypes[type] ?: continue
            renderInstancedBatch(type, instances, poseStack, buffer, info)
        }

        poseStack.pop()

        if (frameCount++ % 3 == 0) {
            entityBuffers.clear()
        }
    }

    private fun renderInstancedBatch(
        type: EntityType<*>,
        instances: List<EntityInstanceData>,
        poseStack: PoseStack,
        buffer: VertexConsumerProvider,
        info: InstancedRenderInfo
    ) {
        val model = info.model
        val vertexConsumer = buffer.getBuffer(model.layer)

        for (instance in instances) {
            poseStack.push()

            poseStack.translate(instance.x, instance.y, instance.z)
            poseStack.multiply(instance.rotationMatrix())

            model.render(poseStack, vertexConsumer, instance.light, 0, 1.0f, 1.0f, 1.0f, 1.0f)

            poseStack.pop()
        }

        vertexConsumer.draw()
    }

    fun getModelForEntity(entity: Entity, distance: Double): EntityModel<*> {
        val type = entity.type
        return instancedTypes[type]?.model ?: createDefaultModel()
    }

    private fun createDefaultModel(): EntityModel<*> {
        return object : EntityModel<Entity>(null) {
            override fun setAngles(entity: Entity, limbAngle: Float, limbDistance: Float, animationProgress: Float, headYaw: Float, headPitch: Float) {}
            override fun render(poseStack: PoseStack, vertexConsumer: net.minecraft.client.render.VertexConsumer, light: Int, overlay: Int, red: Float, green: Float, blue: Float, alpha: Float) {}
        }
    }

    data class InstancedRenderInfo(
        val model: EntityModel<*>,
        val texture: Identifier
    )

    data class EntityInstanceData(
        val entityId: Int,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float,
        val light: Int,
        val lodLevel: Int
    ) {
        fun rotationMatrix(): Matrix4f {
            return Matrix4f().rotateY((yaw * Math.PI / 180f).toFloat())
        }
    }
}