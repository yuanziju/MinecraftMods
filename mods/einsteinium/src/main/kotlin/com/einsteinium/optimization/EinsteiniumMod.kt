package com.einsteinium.optimization

import com.einsteinium.optimization.collision.CollisionSpatialManager
import com.einsteinium.optimization.config.EinsteiniumConfig
import com.einsteinium.optimization.network.BatchPacketBuilder
import com.einsteinium.optimization.network.NetworkSyncOptimizer
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object EinsteiniumMod : ModInitializer {
    const val MOD_ID = "einsteinium"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    private lateinit var collisionManager: CollisionSpatialManager
    private lateinit var networkOptimizer: NetworkSyncOptimizer
    private lateinit var batchPacketBuilder: BatchPacketBuilder

    override fun onInitialize() {
        LOGGER.info("Einsteinium - 实体综合优化模组加载中...")

        EinsteiniumConfig.load()

        collisionManager = CollisionSpatialManager().apply {
            gridSize = 16
            skipStatic = true
            enableAabbCull = true
        }

        networkOptimizer = NetworkSyncOptimizer()
        batchPacketBuilder = BatchPacketBuilder()

        ServerTickEvents.END_SERVER_TICK.register { server ->
            server.allLevels.forEach { level ->
                if (!level.isClientSide) {
                    level.entities.forEach { entity ->
                        collisionManager.updateEntityPosition(entity)
                        networkOptimizer.onEntityTick(entity)
                    }
                }
            }

            server.playerManager.playerList.forEach { player ->
                if (!player.isDisconnected) {
                    val packets = batchPacketBuilder.buildPackets(player)
                    packets.forEach { packet ->
                        batchPacketBuilder.queuePacket(player, packet)
                    }
                }
            }

            batchPacketBuilder.cleanupStaleEntities()
        }

        LOGGER.info("Einsteinium - 碰撞优化模块初始化完成")
        LOGGER.info("Einsteinium - 网络同步优化模块初始化完成")
    }

    fun getCollisionManager(): CollisionSpatialManager {
        return collisionManager
    }

    fun getNetworkOptimizer(): NetworkSyncOptimizer {
        return networkOptimizer
    }

    fun getBatchPacketBuilder(): BatchPacketBuilder {
        return batchPacketBuilder
    }
}