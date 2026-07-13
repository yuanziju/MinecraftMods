package com.einsteinium.optimization.rendering

import com.einsteinium.optimization.EinsteiniumMod
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityModel
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.passive.AnimalEntity
import net.minecraft.entity.monster.Monster

class LODManager {
    private val lodModelCache = mutableMapOf<Class<*>, Map<Int, EntityModel<*>>>()

    fun getLODLevel(entity: Entity, distance: Double): Int {
        if (!EinsteiniumMod.config.rendering.enableLod) return 0

        val config = EinsteiniumMod.config.rendering
        val distanceSq = distance * distance

        val lodDistance1Sq = config.lodDistance1 * config.lodDistance1
        val lodDistance2Sq = config.lodDistance2 * config.lodDistance2

        return when {
            distanceSq > lodDistance2Sq -> 2
            distanceSq > lodDistance1Sq -> 1
            else -> 0
        }
    }

    fun getModel(entity: Entity, distance: Double): EntityModel<*> {
        val lodLevel = getLODLevel(entity, distance)

        val entityClass = entity.javaClass
        val cachedModels = lodModelCache.computeIfAbsent(entityClass) { buildLODModels(entity) }

        return cachedModels.getOrDefault(lodLevel, getDefaultModel(entity))
    }

    private fun buildLODModels(entity: Entity): Map<Int, EntityModel<*>> {
        val models = mutableMapOf<Int, EntityModel<*>>()
        val defaultModel = getDefaultModel(entity)

        models[0] = defaultModel
        models[1] = createMediumLODModel(entity, defaultModel)
        models[2] = createLowLODModel(entity)

        return models
    }

    private fun getDefaultModel(entity: Entity): EntityModel<*> {
        return EinsteiniumClient.instancedRenderer.getModelForEntity(entity, 0.0)
    }

    private fun createMediumLODModel(entity: Entity, baseModel: EntityModel<*>): EntityModel<*> {
        return object : EntityModel<Entity>(baseModel.texture) {
            init {
                this.textureWidth = baseModel.textureWidth
                this.textureHeight = baseModel.textureHeight
            }

            override fun setAngles(entity: Entity, limbAngle: Float, limbDistance: Float, animationProgress: Float, headYaw: Float, headPitch: Float) {
                if (entity is LivingEntity) {
                    baseModel.setAngles(entity, limbAngle, limbDistance * 0.5f, animationProgress, headYaw, headPitch)
                }
            }

            override fun render(poseStack: net.minecraft.client.util.math.PoseStack, vertexConsumer: net.minecraft.client.render.VertexConsumer, light: Int, overlay: Int, red: Float, green: Float, blue: Float, alpha: Float) {
                poseStack.scale(0.8f, 0.8f, 0.8f)
                baseModel.render(poseStack, vertexConsumer, light, overlay, red, green, blue, alpha * 0.8f)
            }
        }
    }

    private fun createLowLODModel(entity: Entity): EntityModel<*> {
        return object : EntityModel<Entity>(null) {
            override fun setAngles(entity: Entity, limbAngle: Float, limbDistance: Float, animationProgress: Float, headYaw: Float, headPitch: Float) {}

            override fun render(poseStack: net.minecraft.client.util.math.PoseStack, vertexConsumer: net.minecraft.client.render.VertexConsumer, light: Int, overlay: Int, red: Float, green: Float, blue: Float, alpha: Float) {
                poseStack.scale(0.5f, 0.5f, 0.5f)

                val color = when {
                    entity is Monster -> 0xFF4444
                    entity is AnimalEntity -> 0x44FF44
                    else -> 0x888888
                }

                val r = ((color shr 16) and 0xFF) / 255.0f
                val g = ((color shr 8) and 0xFF) / 255.0f
                val b = (color and 0xFF) / 255.0f

                drawSimpleCube(poseStack, vertexConsumer, light, r, g, b, alpha * 0.6f)
            }

            private fun drawSimpleCube(
                poseStack: net.minecraft.client.util.math.PoseStack,
                vertexConsumer: net.minecraft.client.render.VertexConsumer,
                light: Int,
                r: Float,
                g: Float,
                b: Float,
                a: Float
            ) {
                val halfSize = 0.3f

                val vertices = arrayOf(
                    floatArrayOf(-halfSize, -halfSize, -halfSize),
                    floatArrayOf(halfSize, -halfSize, -halfSize),
                    floatArrayOf(halfSize, halfSize, -halfSize),
                    floatArrayOf(-halfSize, halfSize, -halfSize),
                    floatArrayOf(-halfSize, -halfSize, halfSize),
                    floatArrayOf(halfSize, -halfSize, halfSize),
                    floatArrayOf(halfSize, halfSize, halfSize),
                    floatArrayOf(-halfSize, halfSize, halfSize)
                )

                val faces = arrayOf(
                    intArrayOf(0, 1, 2, 3),
                    intArrayOf(4, 5, 6, 7),
                    intArrayOf(0, 4, 7, 3),
                    intArrayOf(1, 5, 6, 2),
                    intArrayOf(0, 1, 5, 4),
                    intArrayOf(3, 2, 6, 7)
                )

                val normals = arrayOf(
                    floatArrayOf(0f, 0f, -1f),
                    floatArrayOf(0f, 0f, 1f),
                    floatArrayOf(-1f, 0f, 0f),
                    floatArrayOf(1f, 0f, 0f),
                    floatArrayOf(0f, -1f, 0f),
                    floatArrayOf(0f, 1f, 0f)
                )

                val matrix = poseStack.peek().positionMatrix

                for (i in faces.indices) {
                    val face = faces[i]
                    val normal = normals[i]

                    vertexConsumer.vertex(matrix, vertices[face[0]][0], vertices[face[0]][1], vertices[face[0]][2])
                        .color(r, g, b, a)
                        .texture(0f, 0f)
                        .overlay(0)
                        .light(light)
                        .normal(normal[0], normal[1], normal[2])
                        .next()

                    vertexConsumer.vertex(matrix, vertices[face[1]][0], vertices[face[1]][1], vertices[face[1]][2])
                        .color(r, g, b, a)
                        .texture(1f, 0f)
                        .overlay(0)
                        .light(light)
                        .normal(normal[0], normal[1], normal[2])
                        .next()

                    vertexConsumer.vertex(matrix, vertices[face[2]][0], vertices[face[2]][1], vertices[face[2]][2])
                        .color(r, g, b, a)
                        .texture(1f, 1f)
                        .overlay(0)
                        .light(light)
                        .normal(normal[0], normal[1], normal[2])
                        .next()

                    vertexConsumer.vertex(matrix, vertices[face[3]][0], vertices[face[3]][1], vertices[face[3]][2])
                        .color(r, g, b, a)
                        .texture(0f, 1f)
                        .overlay(0)
                        .light(light)
                        .normal(normal[0], normal[1], normal[2])
                        .next()
                }
            }
        }
    }
}