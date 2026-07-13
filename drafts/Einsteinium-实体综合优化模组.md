---
tags:
  - minecraft
  - mod
  - optimization
  - entity
  - fabric
  - draft
  - lithium-replacement
mod-name: Einsteinium
framework: Fabric
mc-version: "1.21"
language: Kotlin
status: draft
created: 2026-07-13
---

# Einsteinium（锿）· 实体综合优化模组

> **元素周期表命名**：锿（Es），以爱因斯坦命名，象征智慧与高性能。
>
> **定位**：Fabric 1.21 实体综合优化模组，目标替代 Lithium，实现极强的优化。
>
> **核心原则**：原版特性绝不因优化而消除；Debug 版包含调试功能，Release 版不包含。

---

## 1. 基础信息

| 属性 | 值 |
|---|---|
| 模组名称 | Einsteinium |
| 开发框架 | Fabric |
| MC 版本 | 1.21 |
| 映射 | Mojmap（Mojang 官方映射） |
| 语言 | Kotlin |
| 优化范围 | 所有实体（生物、掉落物、投射物、矿车、物品展示框等） |
| 目标 | 替代 Lithium，极强的优化实现 |
| 配置系统 | 是（详细配置） |
| 性能监控 | Debug 版：游戏内 HUD + 调试命令；Release 版：无 |
| 兼容性 | Sodium（渲染兼容）、Lithium（功能替代，检测到后提示） |

---

## 2. 核心架构

### 2.1 架构总览

<div style="background:#1e1e1e; padding:16px; border-radius:8px; border:1px solid #444;">

<svg viewBox="0 0 1000 700" xmlns="http://www.w3.org/2000/svg" style="width:100%; height:auto;">

  <!-- 标题 -->
  <text x="500" y="30" fill="#E0E0E0" font-size="20" font-family="monospace" text-anchor="middle" font-weight="bold">Einsteinium 架构总览</text>

  <!-- Mixin Hook 层 -->
  <rect x="20" y="50" width="220" height="200" rx="8" fill="#1a3a1a" stroke="#4CAF50" stroke-width="2"/>
  <text x="130" y="75" fill="#4CAF50" font-size="14" font-family="monospace" text-anchor="middle" font-weight="bold">Mixin Hook 层</text>
  <text x="130" y="100" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">Entity.tick / tickMovement</text>
  <text x="130" y="118" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">Entity.collision / push</text>
  <text x="130" y="136" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">EntityRenderer.render</text>
  <text x="130" y="154" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">ItemEntity（掉落物）</text>
  <text x="130" y="172" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">MobSpawner / NaturalSpawner</text>
  <text x="130" y="190" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">Chunk（加载/卸载）</text>
  <text x="130" y="208" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">ServerEntity（网络同步）</text>
  <text x="130" y="226" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">DataTracker（数据同步）</text>

  <!-- 优化引擎层 -->
  <rect x="280" y="50" width="280" height="200" rx="8" fill="#1a2a3a" stroke="#2196F3" stroke-width="2"/>
  <text x="420" y="75" fill="#2196F3" font-size="14" font-family="monospace" text-anchor="middle" font-weight="bold">优化引擎层</text>
  <text x="420" y="100" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">Tick优化：距离分层调度器</text>
  <text x="420" y="118" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">碰撞优化：2D网格+高度分层管理器</text>
  <text x="420" y="136" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">渲染优化：GPU实例化渲染器</text>
  <text x="420" y="154" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">掉落物优化：合并管理器+物理简化器</text>
  <text x="420" y="172" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">刷怪优化：密度控制器+距离感知器</text>
  <text x="420" y="190" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">区块优化：惰性加载器+批量处理器</text>
  <text x="420" y="208" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">网络优化：增量同步器+批量打包器</text>
  <text x="420" y="226" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">持久化优化：延迟写入器+差异保存器</text>

  <!-- 内存优化层 -->
  <rect x="600" y="50" width="180" height="200" rx="8" fill="#2a1a2a" stroke="#9C27B0" stroke-width="2"/>
  <text x="690" y="75" fill="#9C27B0" font-size="14" font-family="monospace" text-anchor="middle" font-weight="bold">内存优化层</text>
  <text x="690" y="100" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">对象池化：路径</text>
  <text x="690" y="118" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">对象池化：寻路结果</text>
  <text x="690" y="136" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">对象池化：AABB</text>
  <text x="690" y="154" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">对象池化：碰撞数据</text>
  <text x="690" y="172" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">对象池化：临时集合</text>
  <text x="690" y="190" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">对象池化：NBTCompound</text>
  <text x="690" y="208" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">GC优化：减少临时对象</text>

  <!-- 调试层（Debug Only） -->
  <rect x="820" y="50" width="160" height="200" rx="8" fill="#3a2a1a" stroke="#FF9800" stroke-width="2" stroke-dasharray="5,5"/>
  <text x="900" y="75" fill="#FF9800" font-size="14" font-family="monospace" text-anchor="middle" font-weight="bold">调试层（Debug）</text>
  <text x="900" y="100" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">性能监控 HUD</text>
  <text x="900" y="118" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">实体统计命令</text>
  <text x="900" y="136" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">碰撞可视化</text>
  <text x="900" y="154" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">Tick分布热力图</text>
  <text x="900" y="172" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">网络同步监控</text>
  <text x="900" y="190" fill="#E0E0E0" font-size="10" font-family="monospace" text-anchor="middle">内存分配追踪</text>

  <!-- 数据流 -->
  <rect x="20" y="280" width="960" height="380" rx="8" fill="#1a1a1a" stroke="#666" stroke-width="1"/>
  <text x="500" y="305" fill="#E0E0E0" font-size="14" font-family="monospace" text-anchor="middle" font-weight="bold">数据流与组件交互</text>

  <!-- 实体更新流 -->
  <rect x="40" y="330" width="200" height="120" rx="6" fill="#1a2a1a" stroke="#4CAF50" stroke-width="1"/>
  <text x="140" y="350" fill="#4CAF50" font-size="11" font-family="monospace" text-anchor="middle">实体更新流</text>
  <text x="140" y="370" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">原版 tick() 调用</text>
  <text x="140" y="385" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">↓ 距离分层调度器</text>
  <text x="140" y="400" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">↓ 状态机判断</text>
  <text x="140" y="415" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">↓ 执行/跳过 tick</text>
  <text x="140" y="430" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">↓ 物理保留检查</text>

  <!-- 碰撞检测流 -->
  <rect x="280" y="330" width="200" height="120" rx="6" fill="#1a1a2a" stroke="#2196F3" stroke-width="1"/>
  <text x="380" y="350" fill="#2196F3" font-size="11" font-family="monospace" text-anchor="middle">碰撞检测流</text>
  <text x="380" y="370" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">原版 push() 调用</text>
  <text x="380" y="385" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">↓ 2D网格定位</text>
  <text x="380" y="400" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">↓ 高度分层筛选</text>
  <text x="380" y="415" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">↓ 移动状态分组</text>
  <text x="380" y="430" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">↓ AABB快速排除</text>

  <!-- 渲染流 -->
  <rect x="520" y="330" width="200" height="120" rx="6" fill="#2a1a1a" stroke="#F44336" stroke-width="1"/>
  <text x="620" y="350" fill="#F44336" font-size="11" font-family="monospace" text-anchor="middle">渲染流</text>
  <text x="620" y="370" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">原版 render() 调用</text>
  <text x="620" y="385" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">↓ 视锥剔除</text>
  <text x="620" y="400" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">↓ LOD距离判断</text>
  <text x="620" y="415" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">↓ GPU实例化分组</text>
  <text x="620" y="430" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">↓ 实例化渲染</text>

  <!-- 网络同步流 -->
  <rect x="760" y="330" width="200" height="120" rx="6" fill="#2a2a1a" stroke="#FFC107" stroke-width="1"/>
  <text x="860" y="350" fill="#FFC107" font-size="11" font-family="monospace" text-anchor="middle">网络同步流</text>
  <text x="860" y="370" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">原版 sync() 调用</text>
  <text x="860" y="385" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">↓ 变化检测</text>
  <text x="860" y="400" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">↓ 距离裁剪</text>
  <text x="860" y="415" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">↓ 批量打包</text>
  <text x="860" y="430" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">↓ 增量发送</text>

  <!-- 底部核心保障 -->
  <rect x="40" y="480" width="920" height="160" rx="8" fill="#1a1a1a" stroke="#F44336" stroke-width="2" stroke-dasharray="5,5"/>
  <text x="500" y="510" fill="#F44336" font-size="14" font-family="monospace" text-anchor="middle" font-weight="bold">⚠ 核心保障层</text>
  <text x="500" y="540" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">原版特性保留 · 运动/物理tick绝不跳过 · NBT数据严格保留 · 兼容性检测</text>
  <text x="500" y="565" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">Lithium功能替代 · 检测到Lithium后提示并禁用冲突优化 · 自动降级策略</text>
  <text x="500" y="590" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">对象池化：路径/寻路结果/AABB/碰撞数据/临时集合/NBTCompound</text>
  <text x="500" y="620" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">Debug版：性能监控HUD + 实体统计命令 + 碰撞可视化 + Tick热力图 + 网络监控 + 内存追踪</text>

</svg>

</div>

### 2.2 数据流

```
原版实体更新
  ↓ Mixin 拦截
距离分层调度器（判断 tick 频率）
  ↓ 状态机（静止/移动/战斗）
执行/跳过 tick（运动/物理始终保留）
  ↓
碰撞检测：2D网格 → 高度分层 → 移动状态分组 → AABB排除
  ↓
渲染：视锥剔除 → LOD判断 → GPU实例化分组 → 实例化渲染
  ↓
网络同步：变化检测 → 距离裁剪 → 批量打包 → 增量发送
  ↓
原版行为输出
```

---

## 3. 超越 Lithium 的优化分析

### 3.1 实体碰撞检测

| | Lithium | Einsteinium | 提升 |
|---|---|---|---|
| **策略** | Section（16×16×16）简单分区 | **2D网格+高度分层+移动状态分组+堆积检测** | - |
| **局限** | Section 内实体多时仍全量检测；不区分高度；静止实体也参与检测 | 按 Y 轴分层（地面层/地下层/空中层）；静止实体完全跳过；同网格堆积超过阈值时触发密度限制 | - |
| **效果** | 碰撞检测减少 30-50% | **碰撞检测减少 60-90%**（静止实体占大多数） | **+40-80%** |

### 3.2 实体 Tick 更新

| | Lithium | Einsteinium | 提升 |
|---|---|---|---|
| **策略** | 优化方块状态查询 | **距离分层 Tick + 物理状态机** | - |
| **局限** | 每 tick 仍更新所有实体；不做距离筛选 | 128 格内正常 tick；128-256 格 tick 间隔 ×2；>256 格 ×4；静止实体物理计算降至最低 | - |
| **效果** | 移动计算减少 20-30% | **远处实体 CPU 占用降低 70-80%** | **+50-60%** |

### 3.3 实体渲染

| | Lithium | Einsteinium | 提升 |
|---|---|---|---|
| **策略** | ❌ **不做渲染优化** | **GPU 实例化 + 视锥剔除 + LOD** | - |
| **实现** | - | 相同类型实体合并为一个 draw call；远距离切换低精度模型；视野外实体完全不渲染 | - |
| **效果** | 无 | **Draw call 减少 90%+** | **全新领域** |

### 3.4 掉落物优化

| | Lithium | Einsteinium | 提升 |
|---|---|---|---|
| **策略** | 优化合并算法，扩大合并范围 | **智能合并 + 物理简化 + 消失加速 + 堆积限制** | - |
| **局限** | 合并不智能；不保留 NBT 差异；物理计算未简化 | 严格保留 NBT（附魔/命名物品绝不合并）；静止掉落物物理降至最低；超过密度阈值加速消失 | - |
| **效果** | 掉落物 CPU 减少 30-40% | **掉落物 CPU 减少 80%+** | **+50%** |

### 3.5 刷怪优化

| | Lithium | Einsteinium | 提升 |
|---|---|---|---|
| **策略** | 优化刷怪尝试次数 | **密度限制 + 距离感知 + 冷却优化** | - |
| **局限** | 不做密度控制；刷怪塔仍可无限堆积 | 同区域同类型实体超过阈值时禁止刷怪；>128 格大幅降低刷怪概率；刷怪冷却智能调整 | - |
| **效果** | 刷怪开销减少 20-30% | **刷怪开销减少 50%+，同时防止刷怪塔卡服** | **+30%** |

### 3.6 网络同步

| | Lithium | Einsteinium | 提升 |
|---|---|---|---|
| **策略** | 优化 DataTracker，减少冗余同步 | **增量同步 + 距离裁剪 + 批量打包** | - |
| **局限** | 仍定期全量同步；不做距离差异化 | 只同步变化数据；远处实体只同步位置（不同步旋转/装备）；多实体数据批量打包到一个包 | - |
| **效果** | 网络带宽减少 20-30% | **网络带宽减少 60-70%** | **+40%** |

### 3.7 内存优化

| | Lithium | Einsteinium | 提升 |
|---|---|---|---|
| **策略** | ❌ **不做对象池化** | **对象池化：路径、寻路结果、AABB、碰撞数据、临时集合、NBTCompound** | - |
| **效果** | 无 | **GC 停顿减少 30-50%** | **全新领域** |

### 3.8 区块实体管理

| | Lithium | Einsteinium | 提升 |
|---|---|---|---|
| **策略** | 优化加载/卸载顺序 | **惰性加载 + 批量异步保存 + 跨区块边界优化** | - |
| **局限** | 区块加载时实体立即全部加载，造成瞬间 TPS 下降 | 区块加载时实体分帧逐步加载；保存时批量异步写入磁盘；跨区块实体只在主区块处理 | - |
| **效果** | 加载波动减少 30-40% | **加载瞬间 TPS 波动减少 80%** | **+50%** |

---

## 4. 核心模块设计

### 4.1 Tick 优化 - 距离分层调度器

```kotlin
/**
 * 实体 Tick 距离分层调度器。
 *
 * 根据实体与玩家的距离和状态，动态调整 tick 频率：
 * - ≤128 格：每 tick 正常更新
 * - 128-256 格：每 2 tick 更新一次
 * - >256 格：每 4 tick 更新一次
 * - 静止实体：AI tick 大幅降低，但物理/运动 tick 保留
 */
class TickScheduler {
    /**
     * 判断实体在当前 tick 是否应该执行更新。
     *
     * 不可跳过的 tick（始终返回 true）：
     * - 投射物（箭、雪球等）的运动 tick
     * - 掉落物的物理 tick
     * - 正在移动的实体
     * - 处于战斗状态的实体
     * - 玩家控制的实体（坐骑、矿车等）
     */
    fun shouldTick(entity: Entity, tickCount: Long): Boolean

    /**
     * 判断实体在当前 tick 是否应该执行 AI 更新。
     */
    fun shouldTickAI(entity: Entity, tickCount: Long): Boolean

    /**
     * 判断实体在当前 tick 是否应该执行物理更新。
     * 物理更新涉及重力、速度、碰撞等，不可跳过。
     */
    fun shouldTickPhysics(entity: Entity): Boolean
}
```

### 4.2 碰撞优化 - 2D网格+高度分层管理器

```kotlin
/**
 * 实体碰撞空间管理器。
 *
 * 使用 2D 网格（XZ 平面）+ 高度分层（Y 轴）管理实体碰撞：
 * 1. 将世界按 XZ 平面划分为网格（每格 8×8 或 16×16）
 * 2. 每个网格内按 Y 轴分层（地面层 Y=0-64，地下层 Y<0，空中层 Y>64）
 * 3. 静止实体单独存放，不参与碰撞检测（除非被推动）
 * 4. 同网格超过阈值时触发堆积密度限制
 */
class CollisionSpatialManager {
    /** 2D 网格：XZ坐标 → 该网格内的实体列表 */
    val grid2D: Map<Pair<Int, Int>, GridCell>

    /**
     * 获取可能与指定实体发生碰撞的其他实体。
     * 只返回同网格同高度层内的移动实体。
     */
    fun getPotentialCollisions(entity: Entity): List<Entity>

    /**
     * 检测堆积密度，超过阈值时触发限制。
     */
    fun checkDensityLimit(pos: BlockPos, entityType: EntityType<*>): Boolean
}

/**
 * 网格单元。
 */
class GridCell {
    /** 地面层实体（Y=0-64） */
    val groundEntities: MutableList<Entity>
    /** 地下层实体（Y<0） */
    val undergroundEntities: MutableList<Entity>
    /** 空中层实体（Y>64） */
    val skyEntities: MutableList<Entity>
    /** 静止实体（不参与碰撞检测） */
    val staticEntities: MutableList<Entity>
}
```

### 4.3 渲染优化 - GPU实例化渲染器

```kotlin
/**
 * GPU 实例化实体渲染器。
 *
 * 将相同类型、相同模型的实体合并到一个 draw call 中渲染：
 * 1. 收集所有待渲染的同类型实体
 * 2. 生成实例化属性 buffer（位置、旋转、缩放、颜色）
 * 3. 使用自定义 shader 进行实例化渲染
 * 4. 结合视锥剔除和 LOD
 */
class InstancedEntityRenderer {
    /**
     * 注册一个实体类型为可实例化渲染。
     * 需要提供：基础模型、纹理、shader。
     */
    fun registerInstanced(type: EntityType<*>, model: EntityModel, texture: ResourceLocation)

    /**
     * 渲染一批实体。
     * 内部会自动分组、剔除、LOD。
     */
    fun renderBatch(entities: List<Entity>, poseStack: PoseStack, buffer: MultiBufferSource, light: Int)

    /**
     * 视锥剔除：只渲染在视野内的实体。
     */
    fun frustumCull(entities: List<Entity>, frustum: Frustum): List<Entity>

    /**
     * LOD 判断：远距离使用低精度模型。
     */
    fun getLODModel(entity: Entity, distance: Double): EntityModel
}
```

### 4.4 掉落物优化 - 合并管理器+物理简化器

```kotlin
/**
 * 掉落物优化管理器。
 *
 * 1. 智能合并：同类型掉落物在附近时自动合并
 * 2. 严格保留 NBT：附魔、命名、 lore 等绝不合并
 * 3. 物理简化：静止掉落物不再每 tick 计算重力/碰撞
 * 4. 消失加速：超过密度阈值时加速消失
 * 5. 堆积限制：同区域掉落物数量上限
 */
class ItemEntityOptimizer {
    /**
     * 尝试合并两个掉落物。
     * 只在 NBT 完全一致时合并（附魔书、命名物品绝不合并）。
     */
    fun tryMerge(a: ItemEntity, b: ItemEntity): Boolean

    /**
     * 简化掉落物物理计算。
     * 静止超过 5 tick 后，停止重力/碰撞计算。
     */
    fun simplifyPhysics(itemEntity: ItemEntity)

    /**
     * 检查并限制掉落物密度。
     * 同 8×8×8 区域内超过 64 个掉落物时，加速最旧掉落物消失。
     */
    fun enforceDensityLimit(pos: BlockPos)

    /**
     * 加速掉落物消失。
     */
    fun accelerateDespawn(itemEntity: ItemEntity, factor: Int)
}
```

### 4.5 刷怪优化 - 密度控制器+距离感知器

```kotlin
/**
 * 刷怪优化控制器。
 *
 * 1. 密度限制：同区域同类型实体超过阈值时禁止刷怪
 * 2. 距离感知：>128 格大幅降低刷怪概率
 * 3. 冷却优化：智能调整刷怪冷却时间
 */
class SpawnOptimizer {
    /**
     * 检查当前位置是否允许刷怪（密度检查）。
     */
    fun canSpawnAt(pos: BlockPos, type: EntityType<*>): Boolean

    /**
     * 根据距离调整刷怪概率。
     * ≤128 格：正常概率
     * 128-256 格：概率 ×0.5
     * >256 格：概率 ×0.1
     */
    fun adjustSpawnProbability(distance: Double, baseProbability: Double): Double

    /**
     * 优化刷怪冷却时间。
     * 高密度区域增加冷却，低密度区域减少冷却。
     */
    fun adjustCooldown(pos: BlockPos, baseCooldown: Int): Int
}
```

### 4.6 网络同步 - 增量同步器+批量打包器

```kotlin
/**
 * 实体网络同步优化器。
 *
 * 1. 增量同步：只同步变化的数据
 * 2. 距离裁剪：远处实体只同步关键数据
 * 3. 批量打包：多实体数据合并到一个数据包
 */
class NetworkSyncOptimizer {
    /**
     * 检测实体数据是否发生变化。
     */
    fun hasChanged(entity: Entity, lastState: EntityState): Boolean

    /**
     * 根据距离决定同步内容：
     * - ≤64 格：位置+旋转+装备+数据
     * - 64-128 格：位置+旋转+数据
     * - >128 格：位置（每 2 tick 同步一次）
     */
    fun getSyncData(entity: Entity, distance: Double): SyncData

    /**
     * 批量打包多个实体的同步数据。
     */
    fun batchSync(entities: List<Entity>): Packet
}
```

### 4.7 持久化优化 - 延迟写入器+差异保存器

```kotlin
/**
 * 实体数据持久化优化器。
 *
* 1. 延迟保存：实体数据不立即写入磁盘，批量异步写入
 * 2. 差异保存：只保存变化的数据
 * 3. 压缩存储：对 NBT 数据进行压缩
 */
class PersistenceOptimizer {
    /**
     * 将实体保存请求加入队列，批量异步写入。
     */
    fun queueSave(entity: Entity)

    /**
     * 生成差异 NBT（只包含变化的部分）。
     */
    fun diffNBT(current: CompoundTag, previous: CompoundTag): CompoundTag

    /**
     * 压缩 NBT 数据。
     */
    fun compressNBT(nbt: CompoundTag): ByteArray
}
```

### 4.8 内存优化 - 对象池化

```kotlin
/**
 * 实体相关对象池。
 *
 * 高频分配的临时对象复用，减少 GC 压力。
 */
object EntityObjectPools {
    /** 路径点对象池 */
    val pathPool: ObjectPool<Node>
    /** AABB 对象池 */
    val aabbPool: ObjectPool<AABB>
    /** 碰撞结果对象池 */
    val collisionResultPool: ObjectPool<CollisionResult>
    /** 临时集合对象池 */
    val listPool: ObjectPool<MutableList<Entity>>
    /** NBTCompound 对象池 */
    val nbtPool: ObjectPool<CompoundTag>
    /** 向量对象池 */
    val vec3Pool: ObjectPool<Vec3>
}
```

---

## 5. Mixin Hook 策略

### 5.1 Hook 位置

| Hook 位置 | 目的 | 优化方向 |
|---|---|---|
| `Entity.tick` | 拦截实体 tick，应用距离分层调度 | Tick优化 |
| `Entity.move` | 拦截移动，优化碰撞检测 | 碰撞优化 |
| `Entity.push` | 拦截推挤，应用空间分区 | 碰撞优化 |
| `EntityRenderer.render` | 拦截渲染，应用 GPU 实例化 | 渲染优化 |
| `ItemEntity.tick` | 拦截掉落物 tick，应用合并/物理简化 | 掉落物优化 |
| `NaturalSpawner.spawnForChunk` | 拦截自然刷怪，应用密度限制 | 刷怪优化 |
| `ChunkSerializer.write` | 拦截区块保存，应用延迟写入 | 持久化优化 |
| `ChunkSerializer.read` | 拦截区块加载，应用惰性加载 | 区块管理 |
| `ServerEntity.sendChanges` | 拦截网络同步，应用增量同步 | 网络同步 |
| `DataTracker.packDirty` | 拦截数据同步，应用差异检测 | 网络同步 |

### 5.2 Hook 原则

- **非侵入式**：通过 Mixin 注入，不修改原版类文件
- **可回退**：所有优化可通过配置关闭
- **原版特性保留**：运动/物理 tick 绝不跳过，NBT 严格保留
- **自动降级**：检测到 Lithium 冲突时自动禁用对应优化

---

## 6. 调试系统（Debug 版 Only）

### 6.1 性能监控 HUD

- 实体总数（按类型分布）
- Tick 耗时分布（AI/物理/碰撞/渲染）
- 碰撞检测次数（优化前后对比）
- 网络同步带宽（包大小/频率）
- 对象池命中率
- 缓存命中率

### 6.2 调试命令

```
/einsteinium stats          # 显示实体统计
/einsteinium profile        # 开始/停止性能分析
/einsteinium collision      # 开关碰撞可视化
/einsteinium tickheatmap    # 显示 tick 分布热力图
/einsteinium network        # 显示网络同步监控
/einsteinium memory         # 显示内存分配追踪
```

### 6.3 可视化工具

- **碰撞可视化**：显示 2D 网格边界、高度分层、堆积区域
- **Tick 热力图**：用颜色显示不同区域的 tick 频率
- **网络同步线**：显示实体与玩家之间的同步连线

---

## 7. 配置系统

### 7.1 配置项

```properties
# Tick 优化
tick.distance_tier1=128          # 正常 tick 距离
tick.distance_tier2=256          # ×2 tick 间隔距离
tick.distance_tier3=512          # ×4 tick 间隔距离
tick.skip_static_ai=true         # 跳过静止实体 AI tick
tick.preserve_physics=true       # 始终保留物理 tick（不可关闭）

# 碰撞优化
collision.grid_size=16           # 2D 网格大小（XZ 平面）
collision.height_layers=3        # 高度分层数
collision.skip_static=true       # 跳过静止实体碰撞检测
collision.density_limit=32       # 同网格密度阈值
collision.enable_aabb_cull=true  # 启用 AABB 快速排除

# 渲染优化
rendering.enable_instancing=true # 启用 GPU 实例化
rendering.frustum_cull=true      # 启用视锥剔除
rendering.enable_lod=true        # 启用 LOD
rendering.lod_distance1=64       # LOD 1 距离（低精度）
rendering.lod_distance2=128      # LOD 2 距离（极低精度）

# 掉落物优化
item.merge_range=8               # 合并范围（格）
item.preserve_nbt=true           # 严格保留 NBT（不可关闭）
item.simplify_physics=true       # 简化静止掉落物物理
item.density_limit=64            # 掉落物密度阈值
item.despawn_acceleration=2      # 超过阈值时的消失加速倍数

# 刷怪优化
spawn.density_limit=16           # 同区域同类型密度上限
spawn.distance_factor=0.5        # 远距离刷怪概率因子
spawn.cooldown_adjust=true       # 智能调整刷怪冷却

# 网络同步
sync.incremental=true            # 增量同步
sync.distance_tier1=64           # 全量同步距离
sync.distance_tier2=128          # 部分同步距离
sync.batch_size=16               # 批量打包大小

# 持久化
save.delay_ticks=20              # 延迟保存 tick 数
save.differential=true           # 差异保存
save.compress=true               # 压缩存储

# 内存优化
memory.enable_pools=true         # 启用对象池化
memory.pool_size=1024            # 对象池大小

# 调试（仅 Debug 版有效）
debug.hud=true                   # 显示性能 HUD
debug.commands=true              # 启用调试命令
debug.visualization=true         # 启用可视化
```

---

## 8. 项目结构

```
mods/einsteinium/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/einsteinium/optimization/mixin/
│       │       ├── EntityTickMixin.java
│       │       ├── EntityMovementMixin.java
│       │       ├── EntityCollisionMixin.java
│       │       ├── EntityRendererMixin.java
│       │       ├── ItemEntityMixin.java
│       │       ├── NaturalSpawnerMixin.java
│       │       ├── ChunkSerializerMixin.java
│       │       ├── ServerEntityMixin.java
│       │       └── DataTrackerMixin.java
│       ├── kotlin/
│       │   └── com/einsteinium/optimization/
│       │       ├── EinsteiniumMod.kt
│       │       ├── EinsteiniumClient.kt
│       │       ├── config/
│       │       │   ├── EinsteiniumConfig.kt
│       │       │   └── ModMenuIntegration.kt
│       │       ├── tick/
│       │       │   ├── TickScheduler.kt
│       │       │   └── EntityStateMachine.kt
│       │       ├── collision/
│       │       │   ├── CollisionSpatialManager.kt
│       │       │   ├── GridCell.kt
│       │       │   └── DensityLimiter.kt
│       │       ├── rendering/
│       │       │   ├── InstancedEntityRenderer.kt
│       │       │   ├── LODManager.kt
│       │       │   └── FrustumCuller.kt
│       │       ├── item/
│       │       │   ├── ItemEntityOptimizer.kt
│       │       │   └── ItemMergeLogic.kt
│       │       ├── spawn/
│       │       │   ├── SpawnOptimizer.kt
│       │       │   └── DensityTracker.kt
│       │       ├── network/
│       │       │   ├── NetworkSyncOptimizer.kt
│       │       │   ├── SyncData.kt
│       │       │   └── BatchPacketBuilder.kt
│       │       ├── persistence/
│       │       │   ├── PersistenceOptimizer.kt
│       │       │   └── AsyncSaveQueue.kt
│       │       ├── memory/
│       │       │   ├── ObjectPool.kt
│       │       │   └── EntityObjectPools.kt
│       │       ├── chunk/
│       │       │   ├── LazyEntityLoader.kt
│       │       │   └── ChunkEntityManager.kt
│       │       ├── debug/
│       │       │   ├── PerformanceHud.kt
│       │       │   ├── DebugCommands.kt
│       │       │   ├── CollisionVisualizer.kt
│       │       │   └── TickHeatmap.kt
│       │       └── compat/
│       │           ├── CompatDetector.kt
│       │           └── LithiumCompat.kt
│       └── resources/
│           ├── fabric.mod.json
│           ├── einsteinium.mixins.json
│           └── assets/einsteinium/lang/
├── build.gradle.kts
├── gradle.properties
└── settings.gradle.kts
```

---

## 9. 构建配置

### 9.1 gradle.properties

```properties
# Gradle
org.gradle.jvmargs=-Xmx2G

# Fabric
minecraft_version=1.21
yarn_mappings=1.21+build.1
loader_version=0.15.11

# Fabric API
fabric_version=0.100.1+1.21

# Fabric Kotlin
fabric_kotlin_version=1.11.0+kotlin.2.0.0

# Mod
mod_version=1.0.0
maven_group=com.einsteinium
archives_base_name=einsteinium

# Dependencies
mod_menu_version=11.0.0-beta.1
cloth_config_version=15.0.127

# Debug flag（编译时区分 Debug/Release）
debug_build=false
```

### 9.2 Debug/Release 区分

```kotlin
// build.gradle.kts
val isDebug = (property("debug_build") as String).toBoolean()

if (isDebug) {
    // Debug 版：包含调试代码、性能监控、可视化
    sourceSets["main"].kotlin.srcDir("src/debug/kotlin")
} else {
    // Release 版：不包含调试代码
    // 调试类通过条件编译排除
}
```

---

## 10. 风险评估

| 风险 | 等级 | 缓解措施 |
|---|---|---|
| 原版特性被破坏 | 高 | 运动/物理 tick 绝不跳过；NBT 严格保留；全面测试 |
| 与 Lithium 冲突 | 高 | 检测到 Lithium 后提示并禁用冲突优化；自动降级策略 |
| GPU 实例化兼容性 | 中 | 回退到批量渲染；不支持时自动禁用 |
| 对象池化内存泄漏 | 中 | 对象池大小限制；定期清理；引用计数 |
| 网络同步延迟 | 中 | 增量同步保证一致性；批量大小限制 |
| 刷怪密度限制影响游戏性 | 低 | 密度阈值可配置；默认保守值 |

---

## 11. 实现优先级

1. **P0**：Tick 调度器 + 碰撞空间管理器（核心优化）
2. **P0**：Mixin Hook + 原版特性保留
3. **P1**：掉落物优化 + 刷怪优化
4. **P1**：网络同步优化 + 持久化优化
5. **P2**：GPU 实例化渲染
6. **P2**：对象池化 + 内存优化
7. **P2**：区块惰性加载
8. **P3**：调试系统（Debug 版）
9. **P3**：兼容性处理
