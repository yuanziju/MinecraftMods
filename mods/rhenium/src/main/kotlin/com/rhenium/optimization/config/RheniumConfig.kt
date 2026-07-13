package com.rhenium.optimization.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Rhenium 模组配置类
 *
 * 涵盖：优化开关、线程配置、图划分阈值、优化级别、缓存、性能监控、兼容性。
 * 通过 Gson 序列化为 JSON 文件持久化存储于 Fabric 配置目录。
 */
class RheniumConfig {
    // ============ 优化开关 ============

    /** 红石粉优化开关 */
    var enableRedstoneWireOptimization = true

    /** 红石中继器优化开关 */
    var enableRepeaterOptimization = true

    /** 红石比较器优化开关 */
    var enableComparatorOptimization = true

    /** 投掷器/发射器优化开关 */
    var enableDropperDispenserOptimization = true

    /** 红石矿车优化开关 */
    var enableMinecartOptimization = true

    // ============ 线程配置 ============

    /** 是否启用异步计算 */
    var asyncComputation = true

    /** 线程池最大线程数 */
    var maxThreads = 4

    // ============ 图划分阈值 ============

    /** 小图阈值（节点数小于此值视为小图） */
    var smallGraphThreshold = 20

    /** 大图阈值（节点数大于此值视为大图） */
    var largeGraphThreshold = 100

    // ============ 优化级别 ============

    /** 自动选择优化级别 */
    var autoOptimizationLevel = true

    /** 最大允许的优化级别（1-3） */
    var maxOptimizationLevel = 3

    // ============ 缓存配置 ============

    /** 是否启用失效缓存 */
    var enableCache = true

    /** 缓存最大容量 */
    var cacheMaxSize = 10000

    // ============ 区域检测 ============

    /** 玩家附近多少格内更新红石 */
    var playerRange = 128

    // ============ 性能监控 ============

    /** 是否启用 HUD 显示 */
    var enableHud = true

    /** HUD X 坐标 */
    var hudX = 4

    /** HUD Y 坐标 */
    var hudY = 4

    // ============ 兼容性 ============

    /** Lithium 兼容开关 */
    var compatLithium = true

    /** Create 兼容开关 */
    var compatCreate = true

    companion object {
        private val LOGGER = LoggerFactory.getLogger("Rhenium Config")

        /** 配置文件路径（位于 Fabric 配置目录下） */
        private val CONFIG_PATH: Path =
            FabricLoader.getInstance().configDir.resolve("rhenium.json")

        private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

        /**
         * 从 JSON 文件加载配置。
         * 若文件不存在或解析失败，返回默认配置并尝试持久化。
         */
        fun load(): RheniumConfig {
            return try {
                if (Files.exists(CONFIG_PATH)) {
                    Files.newBufferedReader(CONFIG_PATH).use { reader ->
                        GSON.fromJson(reader, RheniumConfig::class.java) ?: RheniumConfig()
                    }
                } else {
                    LOGGER.info("配置文件不存在，使用默认配置并写入磁盘: {}", CONFIG_PATH)
                    val defaultConfig = RheniumConfig()
                    save(defaultConfig)
                    defaultConfig
                }
            } catch (e: IOException) {
                LOGGER.error("读取配置文件失败，使用默认配置", e)
                RheniumConfig()
            } catch (e: com.google.gson.JsonParseException) {
                LOGGER.error("配置文件解析失败，使用默认配置", e)
                RheniumConfig()
            }
        }

        /**
         * 将配置保存到 JSON 文件。
         */
        fun save(config: RheniumConfig) {
            try {
                Files.createDirectories(CONFIG_PATH.parent)
                Files.newBufferWriter(CONFIG_PATH).use { writer ->
                    GSON.toJson(config, writer)
                }
            } catch (e: IOException) {
                LOGGER.error("保存配置文件失败", e)
            }
        }
    }
}
