package com.rhenium.optimization.config

import com.rhenium.optimization.RheniumMod
import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import me.shedaniel.clothconfig2.api.ConfigBuilder
import me.shedaniel.clothconfig2.api.ConfigCategory
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory

/**
 * Mod Menu 集成入口
 *
 * 实现 [ModMenuApi] 接口，在 Mod Menu 的模组列表中提供"配置"按钮入口。
 * 配置界面通过 Cloth Config 构建，分为优化、线程、图划分、缓存、监控、兼容性六个分类。
 *
 * 兼容性处理：
 * - Mod Menu 为推荐依赖，若未安装则本入口不会被调用（Fabric 自动跳过）
 * - Cloth Config 为推荐依赖，运行时通过类加载检测判断可用性；
 *   若不可用则返回父屏幕（不做任何操作），避免 NoClassDefFoundError
 *
 * 安全策略：
 * - Cloth Config 相关代码集中在 [ClothConfigScreenBuilder] 中，利用 JVM
 *   方法体类引用惰性解析特性，确保仅在 Cloth Config 可用时才加载相关类
 */
class ModMenuIntegration : ModMenuApi {

    companion object {
        private val LOGGER = org.slf4j.LoggerFactory.getLogger("Rhenium ModMenu")

        /** Cloth Config ConfigBuilder 的全限定类名，用于运行时检测 */
        private const val CLOTH_CONFIG_CLASS = "me.shedaniel.clothconfig2.api.ConfigBuilder"
    }

    /**
     * 返回配置界面工厂。
     *
     * 当 Mod Menu 中点击"配置"按钮时调用。
     * 内部会检测 Cloth Config 是否可用，并据此决定构建真实配置界面或回退。
     */
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent ->
            buildConfigScreen(parent)
        }
    }

    /**
     * 构建配置界面。
     *
     * 安全流程：
     * 1. 检测 Cloth Config 类是否可加载
     * 2. 若可用，委托给 [ClothConfigScreenBuilder] 构建完整界面
     * 3. 若不可用，返回父屏幕（用户无感知）
     * 4. 额外 try-catch 捕获构建过程中的意外异常
     *
     * @param parent 父屏幕（返回时回到此屏幕）
     * @return 配置屏幕，或父屏幕（Cloth Config 不可用时）
     */
    private fun buildConfigScreen(parent: Screen): Screen {
        // 类加载检测：判断 Cloth Config 是否在运行时类路径中
        if (!isClothConfigAvailable()) {
            LOGGER.warn("Cloth Config 未安装，无法显示配置界面，请安装 cloth-config 以启用游戏内配置")
            return parent
        }

        // 双重保险：try-catch 防止构建过程中出现 NoClassDefFoundError 等意外
        return try {
            ClothConfigScreenBuilder.build(parent)
        } catch (e: Throwable) {
            LOGGER.error("配置界面构建失败，回退到父屏幕", e)
            parent
        }
    }

    /**
     * 检测 Cloth Config 是否可用。
     * 通过尝试加载 ConfigBuilder 类实现，不触发其初始化。
     */
    private fun isClothConfigAvailable(): Boolean {
        return try {
            Class.forName(CLOTH_CONFIG_CLASS, false, this::class.java.classLoader)
            true
        } catch (e: ClassNotFoundException) {
            false
        } catch (e: LinkageError) {
            false
        }
    }
}

/**
 * Cloth Config 配置界面构建器
 *
 * 将所有对 Cloth Config 类的引用集中在此 object 中。
 * 利用 JVM 的惰性类加载特性：只有当 [build] 方法被实际调用时，
 * 其方法体内引用的 Cloth Config 类才会被解析和加载。
 *
 * 这样即使 Cloth Config 未安装，只要不调用 [build]，
 * 本 object 不会触发 NoClassDefFoundError。
 */
private object ClothConfigScreenBuilder {

    private val LOGGER = LoggerFactory.getLogger("Rhenium ClothConfig")

    /**
     * 构建 Rhenium 配置界面。
     *
     * 界面结构：
     * - 优化选项：各红石元件优化开关
     * - 线程配置：异步计算、最大线程数
     * - 图划分：小图/大图阈值
     * - 缓存：启用开关、最大容量
     * - 性能监控：HUD 开关与坐标
     * - 兼容性：Lithium/Create 兼容开关
     *
     * @param parent 父屏幕
     * @return 配置界面屏幕
     */
    fun build(parent: Screen): Screen {
        val config = RheniumMod.CONFIG

        val builder: ConfigBuilder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.translatable("rhenium.config.title"))

        val entryBuilder: ConfigEntryBuilder = builder.entryBuilder()

        // 分类：优化选项
        val optimizationCategory: ConfigCategory =
            builder.getOrCreateCategory(Component.translatable("rhenium.config.category.optimization"))
        optimizationCategory.addEntry(
            entryBuilder.startBooleanToggle(
                Component.translatable("rhenium.config.enableRedstoneWireOptimization"),
                config.enableRedstoneWireOptimization
            ).setDefaultValue(true).setSaveConsumer { config.enableRedstoneWireOptimization = it }.build()
        )
        optimizationCategory.addEntry(
            entryBuilder.startBooleanToggle(
                Component.translatable("rhenium.config.enableRepeaterOptimization"),
                config.enableRepeaterOptimization
            ).setDefaultValue(true).setSaveConsumer { config.enableRepeaterOptimization = it }.build()
        )
        optimizationCategory.addEntry(
            entryBuilder.startBooleanToggle(
                Component.translatable("rhenium.config.enableComparatorOptimization"),
                config.enableComparatorOptimization
            ).setDefaultValue(true).setSaveConsumer { config.enableComparatorOptimization = it }.build()
        )
        optimizationCategory.addEntry(
            entryBuilder.startBooleanToggle(
                Component.translatable("rhenium.config.enableDropperDispenserOptimization"),
                config.enableDropperDispenserOptimization
            ).setDefaultValue(true).setSaveConsumer { config.enableDropperDispenserOptimization = it }.build()
        )
        optimizationCategory.addEntry(
            entryBuilder.startBooleanToggle(
                Component.translatable("rhenium.config.enableMinecartOptimization"),
                config.enableMinecartOptimization
            ).setDefaultValue(true).setSaveConsumer { config.enableMinecartOptimization = it }.build()
        )

        // 分类：线程配置
        val threadingCategory: ConfigCategory =
            builder.getOrCreateCategory(Component.translatable("rhenium.config.category.threading"))
        threadingCategory.addEntry(
            entryBuilder.startBooleanToggle(
                Component.translatable("rhenium.config.asyncComputation"),
                config.asyncComputation
            ).setDefaultValue(true).setSaveConsumer { config.asyncComputation = it }.build()
        )
        threadingCategory.addEntry(
            entryBuilder.startIntField(
                Component.translatable("rhenium.config.maxThreads"),
                config.maxThreads
            ).setDefaultValue(4).setMin(1).setMax(32)
                .setSaveConsumer { config.maxThreads = it }.build()
        )

        // 分类：图划分
        val graphCategory: ConfigCategory =
            builder.getOrCreateCategory(Component.translatable("rhenium.config.category.graph"))
        graphCategory.addEntry(
            entryBuilder.startIntField(
                Component.translatable("rhenium.config.smallGraphThreshold"),
                config.smallGraphThreshold
            ).setDefaultValue(20).setMin(1).setSaveConsumer { config.smallGraphThreshold = it }.build()
        )
        graphCategory.addEntry(
            entryBuilder.startIntField(
                Component.translatable("rhenium.config.largeGraphThreshold"),
                config.largeGraphThreshold
            ).setDefaultValue(100).setMin(1).setSaveConsumer { config.largeGraphThreshold = it }.build()
        )
        graphCategory.addEntry(
            entryBuilder.startBooleanToggle(
                Component.translatable("rhenium.config.autoOptimizationLevel"),
                config.autoOptimizationLevel
            ).setDefaultValue(true).setSaveConsumer { config.autoOptimizationLevel = it }.build()
        )
        graphCategory.addEntry(
            entryBuilder.startIntField(
                Component.translatable("rhenium.config.maxOptimizationLevel"),
                config.maxOptimizationLevel
            ).setDefaultValue(3).setMin(1).setMax(3)
                .setSaveConsumer { config.maxOptimizationLevel = it }.build()
        )

        // 分类：缓存配置
        val cacheCategory: ConfigCategory =
            builder.getOrCreateCategory(Component.translatable("rhenium.config.category.cache"))
        cacheCategory.addEntry(
            entryBuilder.startBooleanToggle(
                Component.translatable("rhenium.config.enableCache"),
                config.enableCache
            ).setDefaultValue(true).setSaveConsumer { config.enableCache = it }.build()
        )
        cacheCategory.addEntry(
            entryBuilder.startIntField(
                Component.translatable("rhenium.config.cacheMaxSize"),
                config.cacheMaxSize
            ).setDefaultValue(10000).setMin(0).setSaveConsumer { config.cacheMaxSize = it }.build()
        )

        // 分类：性能监控
        val monitorCategory: ConfigCategory =
            builder.getOrCreateCategory(Component.translatable("rhenium.config.category.monitor"))
        monitorCategory.addEntry(
            entryBuilder.startBooleanToggle(
                Component.translatable("rhenium.config.enableHud"),
                config.enableHud
            ).setDefaultValue(true).setSaveConsumer { config.enableHud = it }.build()
        )
        monitorCategory.addEntry(
            entryBuilder.startIntField(
                Component.translatable("rhenium.config.hudX"),
                config.hudX
            ).setDefaultValue(4).setSaveConsumer { config.hudX = it }.build()
        )
        monitorCategory.addEntry(
            entryBuilder.startIntField(
                Component.translatable("rhenium.config.hudY"),
                config.hudY
            ).setDefaultValue(4).setSaveConsumer { config.hudY = it }.build()
        )

        // 分类：兼容性
        val compatCategory: ConfigCategory =
            builder.getOrCreateCategory(Component.translatable("rhenium.config.category.compat"))
        compatCategory.addEntry(
            entryBuilder.startBooleanToggle(
                Component.translatable("rhenium.config.compatLithium"),
                config.compatLithium
            ).setDefaultValue(true).setSaveConsumer { config.compatLithium = it }.build()
        )
        compatCategory.addEntry(
            entryBuilder.startBooleanToggle(
                Component.translatable("rhenium.config.compatCreate"),
                config.compatCreate
            ).setDefaultValue(true).setSaveConsumer { config.compatCreate = it }.build()
        )

        // 区域检测配置（放在图划分分类下）
        graphCategory.addEntry(
            entryBuilder.startIntField(
                Component.translatable("rhenium.config.playerRange"),
                config.playerRange
            ).setDefaultValue(128).setMin(16).setSaveConsumer { config.playerRange = it }.build()
        )

        // 保存回调：用户点击"完成"时持久化配置
        builder.setSavingRunnable {
            RheniumConfig.save(config)
            LOGGER.info("配置已通过 Cloth Config 保存")
        }

        return builder.build()
    }
}
