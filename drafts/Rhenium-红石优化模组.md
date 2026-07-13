---
tags:
  - minecraft
  - mod
  - optimization
  - redstone
  - fabric
  - draft
mod-name: Rhenium
framework: Fabric
mc-version: "1.21"
language: Java
status: draft
created: 2026-07-13
---

# Rhenium（铼）· 红石综合优化模组

> **元素周期表命名**：铼（Re），稀有高熔点金属，象征高性能与稳定性。
>
> **定位**：Fabric 1.21 红石综合优化模组，在不破坏原版微时序与 0t 电路的前提下，最大化红石系统性能。

---

## 1. 基础信息

| 属性 | 值 |
|---|---|
| 模组名称 | Rhenium |
| 开发框架 | Fabric |
| MC 版本 | 1.21 |
| 映射 | Mojmap（Mojang 官方映射） |
| 优化范围 | 红石粉、红石中继器/比较器、投掷器/发射器、红石矿车 |
| 配置系统 | 游戏内菜单（Mod Menu 兼容） |
| 性能监控 | 游戏内 HUD |
| 兼容性 | Sodium、Lithium、Create |

---

## 2. 核心架构

### 2.1 架构总览

<div style="background:#1e1e1e; padding:16px; border-radius:8px; border:1px solid #444;">

<svg viewBox="0 0 900 600" xmlns="http://www.w3.org/2000/svg" style="width:100%; height:auto;">

  <!-- 标题 -->
  <text x="450" y="30" fill="#E0E0E0" font-size="18" font-family="monospace" text-anchor="middle" font-weight="bold">Rhenium 架构总览</text>

  <!-- Mixin 层 -->
  <rect x="20" y="60" width="200" height="160" rx="8" fill="#1a3a1a" stroke="#4CAF50" stroke-width="2"/>
  <text x="120" y="85" fill="#4CAF50" font-size="14" font-family="monospace" text-anchor="middle" font-weight="bold">Mixin Hook 层</text>
  <text x="120" y="110" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">BlockRedstoneWire</text>
  <text x="120" y="130" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">RedstoneComparatorLogic</text>
  <text x="120" y="150" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">Level.updateNeighborsAt</text>
  <text x="120" y="170" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">DropperDispenserBlock</text>
  <text x="120" y="190" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">MinecartEntity</text>

  <!-- 图管理层 -->
  <rect x="280" y="60" width="240" height="160" rx="8" fill="#1a2a3a" stroke="#2196F3" stroke-width="2"/>
  <text x="400" y="85" fill="#2196F3" font-size="14" font-family="monospace" text-anchor="middle" font-weight="bold">红石图管理层</text>
  <text x="400" y="110" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">RedstoneGraph</text>
  <text x="400" y="130" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">图构建 / 图划分</text>
  <text x="400" y="150" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">并查集 (Union-Find)</text>
  <text x="400" y="170" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">耦合关系处理</text>
  <text x="400" y="190" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">动态图更新</text>

  <!-- 优化引擎 -->
  <rect x="580" y="60" width="300" height="160" rx="8" fill="#3a1a1a" stroke="#FF5722" stroke-width="2"/>
  <text x="730" y="85" fill="#FF5722" font-size="14" font-family="monospace" text-anchor="middle" font-weight="bold">三级自适应优化引擎</text>
  <text x="730" y="110" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">Level 1: 保守（首次/未知电路）</text>
  <text x="730" y="130" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">Level 2: 中等（已验证电路）</text>
  <text x="730" y="150" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">Level 3: 激进（标准/简单电路）</text>
  <text x="730" y="175" fill="#FF9800" font-size="11" font-family="monospace" text-anchor="middle">字节码编译 → JIT 友好</text>
  <text x="730" y="195" fill="#FF9800" font-size="11" font-family="monospace" text-anchor="middle">热更新 / 电路变化重编译</text>

  <!-- 线程模型 -->
  <rect x="100" y="280" width="280" height="130" rx="8" fill="#2a2a1a" stroke="#FFC107" stroke-width="2"/>
  <text x="240" y="305" fill="#FFC107" font-size="14" font-family="monospace" text-anchor="middle" font-weight="bold">线程模型（按图大小分配）</text>
  <text x="240" y="330" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">大图（&gt;100节点）→ 独立线程</text>
  <text x="240" y="350" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">中图（20-100节点）→ 任务队列并行</text>
  <text x="240" y="370" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">小图（&lt;20节点）→ 主线程</text>
  <text x="240" y="395" fill="#FF9800" font-size="11" font-family="monospace" text-anchor="middle">时序安全：拓扑排序 + 0t 标记</text>

  <!-- 缓存 -->
  <rect x="440" y="280" width="200" height="130" rx="8" fill="#1a2a2a" stroke="#00BCD4" stroke-width="2"/>
  <text x="540" y="305" fill="#00BCD4" font-size="14" font-family="monospace" text-anchor="middle" font-weight="bold">失效缓存</text>
  <text x="540" y="330" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">状态缓存</text>
  <text x="540" y="350" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">增量更新</text>
  <text x="540" y="370" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">基于图结构</text>
  <text x="540" y="390" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">只在变化时重算</text>

  <!-- 配置/监控/兼容 -->
  <rect x="700" y="280" width="180" height="130" rx="8" fill="#2a1a2a" stroke="#9C27B0" stroke-width="2"/>
  <text x="790" y="305" fill="#9C27B0" font-size="14" font-family="monospace" text-anchor="middle" font-weight="bold">辅助系统</text>
  <text x="790" y="330" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">配置系统</text>
  <text x="790" y="350" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">性能监控 HUD</text>
  <text x="790" y="370" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">兼容性检测</text>

  <!-- 箭头 -->
  <line x1="220" y1="140" x2="280" y2="140" stroke="#666" stroke-width="2" marker-end="url(#arrow)"/>
  <line x1="520" y1="140" x2="580" y2="140" stroke="#666" stroke-width="2" marker-end="url(#arrow)"/>
  <line x1="400" y1="220" x2="240" y2="280" stroke="#666" stroke-width="2" marker-end="url(#arrow)"/>
  <line x1="500" y1="220" x2="540" y2="280" stroke="#666" stroke-width="2" marker-end="url(#arrow)"/>
  <line x1="700" y1="220" x2="790" y2="280" stroke="#666" stroke-width="2" marker-end="url(#arrow)"/>

  <defs>
    <marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto">
      <path d="M0,0 L0,6 L9,3 z" fill="#666"/>
    </marker>
  </defs>

  <!-- 底部时序保障 -->
  <rect x="200" y="470" width="500" height="80" rx="8" fill="#1a1a1a" stroke="#F44336" stroke-width="2" stroke-dasharray="5,5"/>
  <text x="450" y="500" fill="#F44336" font-size="14" font-family="monospace" text-anchor="middle" font-weight="bold">⚠ 时序保障层（贯穿所有模块）</text>
  <text x="450" y="525" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">微时序保持 · 0t 信号路径标记 · 跨图同步</text>
  <text x="450" y="540" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">拓扑排序保证原版更新顺序</text>

</svg>

</div>

### 2.2 数据流

```
原版红石更新
  ↓ Mixin 拦截
图构建 / 更新（检测耦合关系）
  ↓ 按图大小分配线程
三级优化引擎（自适应选择级别）
  ↓ 失效缓存检查
字节码编译（高频路径）
  ↓ 计算结果
回写主线程（保持时序）
  ↓
原版行为输出
```

---

## 3. 红石信号图（RedstoneGraph）

### 3.1 图的定义

| 概念 | 说明 |
|---|---|
| **节点（Node）** | 一个红石元件（红石粉、中继器、比较器、投掷器、发射器、红石矿车等） |
| **边（Edge）** | 两个元件之间的信号连接关系，带方向 |
| **图（Graph）** | 一组相互连通的红石元件集合 |

### 3.2 连接判定规则

两个红石元件 A 和 B 相连的条件：

1. **直接相邻**：曼哈顿距离 ≤ 1（上下左右前后）
2. **信号可达**：A 的输出面朝向 B，或 B 的输入面朝向 A
3. **无阻挡**：连接路径上没有不透光方块阻挡信号

### 3.3 耦合关系处理

#### 强充能 vs 弱充能

| 类型 | 来源 | 特点 |
|---|---|---|
| **强充能** | 红石粉直接指向方块 / 中继器/比较器输出 | 信号强度完整传递，可激活相邻红石元件 |
| **弱充能** | 红石粉间接激活（覆盖在方块上） | 信号强度-1 传递 |

图构建时区分强充能和弱充能连接，作为边的属性记录。

#### 活塞遮挡

- 活塞伸出时会遮挡红石信号路径
- 图构建时检测活塞状态，动态调整连接关系
- 活塞状态变化时触发局部图重构

#### 阳光传感器

- 输出受光照影响
- 图中标记为光照敏感节点
- 光照变化时重新计算相关节点的输出

#### 可被充能的方块

- 实体方块（石头、铁块等）可被红石充能
- 充能后的方块可激活相邻的红石元件
- 图中将这些方块作为"中继节点"，传递强/弱充能信号

### 3.4 图构建流程

```
1. 遍历世界中的红石元件（由 Mixin 触发）
2. 对每个元件，查找相邻的红石元件和可被充能的方块
3. 建立边时记录：
   - 连接类型（强充能/弱充能）
   - 信号方向
   - 是否受活塞/光照影响
4. 使用并查集（Union-Find）将相连的元件合并到同一个图
5. 为每个图标号，记录节点数量和耦合类型
6. 检测 0t 信号路径并标记
```

### 3.5 图大小划分

| 类型 | 节点数量 | 处理方式 | 时序策略 |
|---|---|---|---|
| **小图** | < 20 | 主线程处理 | 精确保持原版时序 |
| **中图** | 20 - 100 | 任务队列，多线程并行 | 图内拓扑排序 |
| **大图** | > 100 | 单独线程完整处理 | 严格按原版顺序计算 |

### 3.6 图边界处理

- **边界节点**：与其他图中元件相邻的节点
- **跨图信号**：在图边界处，信号传播需要同步到相邻图
- **边界缓冲区**：每个图保留边界节点的最新状态，供相邻图查询
- **跨图时序**：按区块坐标顺序处理不同图，与原版一致

### 3.7 动态图更新

当以下事件发生时，触发局部图重构：

- 活塞状态变化
- 光照变化
- 方块破坏 / 放置
- 红石元件被激活 / 失活

重构采用增量更新，只重新计算受影响的部分。

---

## 4. 三级自适应优化策略

### 4.1 策略概览

| 级别 | 策略 | 特点 | 适用场景 |
|---|---|---|---|
| **Level 1 - 保守** | 保持所有原版行为，仅合并无关更新 | 完全兼容所有红石电路 | 首次更新、未知电路 |
| **Level 2 - 中等** | 移除非关键特性检测，优化常见路径 | 兼容 99% 红石电路 | 已验证无高级特性的电路 |
| **Level 3 - 激进** | 最大程度优化，假设电路规则已知 | 极致性能，可能不兼容边缘 case | 简单/标准电路 |

### 4.2 自适应升级机制

```
首次更新 → Level 1（保守）
  ↓ 检测电路是否依赖：
    - 0t 信号
    - 微时序
    - 复杂中继器链
    - 活塞遮挡动态变化
  ↓ 不依赖 → 升级到 Level 2
Level 2（中等）
  ↓ 运行稳定 N tick 无异常
  ↓ 再次检测无高级特性
  ↓ → 升级到 Level 3
Level 3（激进）
  ↓ 如果检测到异常或新特性
  ↓ → 降级回 Level 2 或 Level 1
```

### 4.3 各级别具体优化内容

#### Level 1 - 保守

- 合并同一 tick 内对同一方块的多次更新
- 不改变任何信号传播顺序
- 不跳过任何检测步骤
- 适用于：首次更新、未知电路

#### Level 2 - 中等

- 移除对不活跃区域的重复检测
- 优化常见信号传播路径
- 启用失效缓存
- 不跳过微时序检测

#### Level 3 - 激进

- 使用预编译的字节码直接计算
- 跳过所有安全检查
- 最大程度合并更新
- 假设电路行为符合标准模式

---

## 5. 字节码编译机制

### 5.1 目标

- 将红石图的计算逻辑编译成 Java 字节码
- 高频路径可被 JVM JIT 编译优化
- 支持热更新，电路变化时重新编译

### 5.2 实现方案

```
RedstoneGraph
  ↓ 分析图结构
  ↓ 生成计算逻辑（Java 源码或 ASM 字节码）
  ↓ 使用 ASM 或 Javassist 动态生成 Class
  ↓ 实例化并缓存
  ↓ 高频调用时 JIT 自动优化
```

### 5.3 编译时机

- 图首次构建完成后，预编译 Level 2/3 计算逻辑
- 图结构变化时，增量更新或重新编译
- 编译过程异步执行，不阻塞主线程

### 5.4 热更新

- 当图结构发生变化（如活塞状态变化）时
- 重新编译受影响的子图计算逻辑
- 使用双缓冲机制，新逻辑编译完成后原子替换

---

## 6. Mixin Hook 策略

### 6.1 Hook 位置

| Hook 位置 | 目的 | 优化级别 |
|---|---|---|
| `BlockRedstoneWire.tick` | 拦截红石粉 tick，替换为图驱动更新 | L1-L3 |
| `BlockRedstoneWire.updateShape` | 拦截形状更新，合并红石更新 | L1-L3 |
| `Level.updateNeighborsAt` | 拦截邻居更新，合并传播 | L1-L3 |
| `RedstoneComparatorLogic.computeOutputLevel` | 拦截比较器计算 | L2-L3 |
| `RedstoneTorchBlock.onPlace` / `tick` | 优化红石火把更新 | L2-L3 |
| `DropperDispenserBlock` | 批量处理投掷器/发射器 | L2-L3 |
| `AbstractMinecart` | 优化红石矿车移动检测 | L2-L3 |

### 6.2 Hook 原则

- **非侵入式**：通过 Mixin 注入，不修改原版类文件
- **可回退**：所有优化可通过配置关闭
- **时序安全**：所有 Hook 保持原版信号传播顺序
- **增量应用**：只对已识别的图结构应用优化，未识别的走原版逻辑

---

## 7. 线程模型

### 7.1 线程分配

```
主线程
  ├── 小图（<20节点）直接处理
  ├── 收集中图/大图的更新请求
  └── 在 tick 末尾合并结果

异步线程池（n = CPU 核心数 - 1）
  ├── 大图独立线程（>100节点）
  │     └── 严格按原版顺序计算
  └── 中图任务队列（20-100节点）
        └── 多线程并行，图内保持顺序
```

### 7.2 时序安全

- **图内**：使用拓扑排序保持原版更新顺序
- **跨图**：按区块坐标顺序处理不同图
- **0t 信号**：在图边界处特别处理，确保 0t 信号完整传播
- **结果回写**：在下一 tick 开始时合并到主线程

### 7.3 线程安全

- 图数据结构使用读写锁保护
- 计算结果使用不可变对象传递
- 主线程通过 double-buffer 获取最新结果

---

## 8. 缓存策略 - 失效缓存

### 8.1 缓存内容

- 红石信号状态（每个节点的当前信号强度）
- 信号传播路径
- 计算中间结果

### 8.2 失效规则

- 当节点的输入发生变化时，使该节点及其下游节点的缓存失效
- 基于图结构的增量更新，只重新计算受影响的子图
- 使用版本号标记缓存有效性

### 8.3 缓存淘汰

- LRU 策略淘汰长期不活跃的缓存
- 按区块卸载时清理对应缓存
- 最大缓存大小可配置

---

## 9. 具体元件优化方案

### 9.1 红石粉

| 优化点 | 策略 |
|---|---|
| 信号传播 | 基于图的 BFS/DFS 计算传播路径 |
| 更新合并 | 同一 tick 内对相邻红石粉的更新合并 |
| 区域检测 | 只在玩家附近（可配置范围）更新红石粉 |
| 缓存 | 缓存信号状态，输入未变时直接返回缓存 |

### 9.2 红石中继器

| 优化点 | 策略 |
|---|---|
| 延迟计算 | 使用高效状态机替代原版逻辑 |
| 更新合并 | 多个中继器的延迟更新合并到同一队列 |
| 状态预计算 | 预计算常见状态，减少运行时计算 |
| 跳帧优化 | 高延迟时跳过不必要的中间状态（L3） |

### 9.3 红石比较器

| 优化点 | 策略 |
|---|---|
| 计算拦截 | Hook `computeOutputLevel`，使用缓存结果 |
| 减法模式 | 缓存减法模式的结果，输入未变时直接返回 |
| 比较模式 | 缓存比较模式的结果 |
| 容器检测 | 缓存容器物品数量，只在变化时重算 |

### 9.4 投掷器/发射器

| 优化点 | 策略 |
|---|---|
| 批量处理 | 将多个投掷器的物品处理合并 |
| 冷却优化 | 优化冷却时间计算，减少重复检查 |
| 实体池化 | 使用对象池复用实体对象 |
| 智能跳过 | 当目标位置不可达时跳过发射 |

### 9.5 红石矿车

| 优化点 | 策略 |
|---|---|
| 区域检测 | 只在玩家附近处理矿车 |
| 静止检测 | 静止的矿车不进行信号更新 |
| 路径缓存 | 缓存矿车的移动路径 |
| 信号预计算 | 预计算矿车的信号输出 |

---

## 10. 时序保障机制

### 10.1 核心原则

> **所有优化不得改变原版红石信号传播的相对顺序和时序行为。**

### 10.2 微时序保持

- tick 内执行顺序与原版完全一致
- 使用拓扑排序确保图内节点的更新顺序
- 同一优先级的更新按原版方块坐标顺序处理

### 10.3 0t 兼容

- 检测 0t 信号路径（同一 tick 内信号从输入到输出）
- 标记 0t 路径，确保信号完整传播
- 在图边界处特别处理 0t 信号的跨图传播
- 0t 路径上的节点强制使用 Level 1 优化

### 10.4 跨图同步

- 不同图之间的信号传播按区块坐标顺序处理
- 边界缓冲区保存最新状态
- 跨图信号传播不跨 tick（保持 0t 兼容）

---

## 11. 配置系统

### 11.1 配置方式

- 游戏内配置菜单（Mod Menu 兼容）
- JSON 文件持久化

### 11.2 配置项

```
# 优化开关
enable_redstone_wire_optimization = true
enable_repeater_optimization = true
enable_comparator_optimization = true
enable_dropper_dispenser_optimization = true
enable_minecart_optimization = true

# 线程配置
async_computation = true
max_threads = 4

# 图划分阈值
small_graph_threshold = 20
large_graph_threshold = 100

# 优化级别
auto_optimization_level = true    # 自动选择优化级别
max_optimization_level = 3        # 最大允许的优化级别

# 缓存配置
enable_cache = true
cache_max_size = 10000

# 区域检测
player_range = 128                # 玩家附近多少格内更新红石

# 性能监控
enable_hud = true
hud_x = 4
hud_y = 4
```

---

## 12. 性能监控 HUD

### 12.1 显示内容

- 红石图数量和总节点数
- 各大小图分布
- 当前优化级别分布
- Tick 耗时（红石部分）
- 缓存命中率
- 线程池状态

### 12.2 实现方式

- 使用 Fabric HUD 渲染回调
- 可通过快捷键开关
- 数据每 tick 更新，显示每秒平均值

---

## 13. 兼容性策略

### 13.1 Sodium / Lithium

- 检测是否安装 Sodium/Lithium
- 对于 Lithium 已优化的部分（如红石 tick），使用更保守的策略
- 避免与 Lithium 的红石优化冲突
- 提供 `compat_lithium` 配置项，可手动调整

### 13.2 Create

- 检测是否安装 Create
- Create 的机械结构大量使用红石，确保不破坏 Create 的红石交互
- 对 Create 的自定义红石元件（如转速计）保持原版行为
- 提供 `compat_create` 配置项

### 13.3 通用兼容策略

- 所有 Mixin 使用 `@Inject` 优先于 `@Redirect` / `@Overwrite`
- 提供配置项让用户禁用可能冲突的优化
- 检测到冲突时自动降级到保守模式

---

## 14. 项目结构

```
mods/rhenium/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/rhenium/optimization/
│       │       ├── RheniumMod.java              # 模组主类
│       │       ├── config/
│       │       │   ├── RheniumConfig.java       # 配置类
│       │       │   └── RheniumScreen.java       # 游戏内配置界面
│       │       ├── graph/
│       │       │   ├── RedstoneGraph.java        # 红石图
│       │       │   ├── GraphNode.java            # 图节点
│       │       │   ├── GraphEdge.java            # 图边
│       │       │   ├── GraphBuilder.java        # 图构建器
│       │       │   ├── GraphPartitioner.java     # 图划分（并查集）
│       │       │   ├── CouplingDetector.java     # 耦合关系检测
│       │       │   └── DynamicGraphUpdater.java  # 动态图更新
│       │       ├── optimization/
│       │       │   ├── OptimizationEngine.java  # 优化引擎
│       │       │   ├── ConservativeStrategy.java # Level 1
│       │       │   ├── BalancedStrategy.java     # Level 2
│       │       │   ├── AggressiveStrategy.java   # Level 3
│       │       │   └── AdaptiveLevelManager.java # 自适应级别管理
│       │       ├── bytecode/
│       │       │   ├── GraphCompiler.java       # 字节码编译器
│       │       │   └── CompiledGraph.java        # 编译后的图
│       │       ├── threading/
│       │       │   ├── GraphThreadPool.java      # 线程池
│       │       │   ├── LargeGraphTask.java       # 大图任务
│       │       │   └── MediumGraphQueue.java     # 中图任务队列
│       │       ├── cache/
│       │       │   ├── SignalCache.java          # 信号缓存
│       │       │   └── CacheInvalidator.java     # 缓存失效管理
│       │       ├── timing/
│       │       │   ├── TimingPreserver.java      # 时序保障
│       │       │   ├── ZeroTickDetector.java     # 0t 检测
│       │       │   └── TopologicalSorter.java    # 拓扑排序
│       │       ├── monitor/
│       │       │   ├── PerformanceMonitor.java   # 性能监控
│       │       │   └── HudRenderer.java          # HUD 渲染
│       │       ├── compat/
│       │       │   ├── CompatDetector.java       # 兼容性检测
│       │       │   ├── LithiumCompat.java        # Lithium 兼容
│       │       │   └── CreateCompat.java         # Create 兼容
│       │       └── mixin/
│       │           ├── BlockRedstoneWireMixin.java
│       │           ├── LevelMixin.java
│       │           ├── RedstoneComparatorLogicMixin.java
│       │           ├── DropperDispenserBlockMixin.java
│       │           └── AbstractMinecartMixin.java
│       └── resources/
│           ├── fabric.mod.json
│           └── rhenium.mixins.json
├── build.gradle
├── gradle.properties
└── settings.gradle
```

---

## 15. 构建配置

### 15.1 gradle.properties

```properties
# Gradle
org.gradle.jvmargs=-Xmx2G

# Fabric
minecraft_version=1.21
yarn_mappings=1.21+build.1
loader_version=0.15.11

# Fabric API
fabric_version=0.100.1+1.21

# Mod
mod_version=1.0.0
maven_group=com.rhenium
archives_base_name=rhenium

# Dependencies
mod_menu_version=11.0.0-beta.1
cloth_config_version=15.0.127
asm_version=9.6
```

### 15.2 fabric.mod.json

```json
{
  "schemaVersion": 1,
  "id": "rhenium",
  "version": "${version}",
  "name": "Rhenium",
  "description": "红石综合优化模组 - 不破坏微时序与0t电路",
  "authors": ["Rhenium Team"],
  "contact": {},
  "license": "MIT",
  "icon": "assets/rhenium/icon.png",
  "environment": "*",
  "entrypoints": {
    "main": ["com.rhenium.optimization.RheniumMod"],
    "client": ["com.rhenium.optimization.client.RheniumClient"],
    "modmenu": ["com.rhenium.optimization.config.ModMenuIntegration"]
  },
  "mixins": ["rhenium.mixins.json"],
  "depends": {
    "fabricloader": ">=0.15.11",
    "minecraft": "~1.21",
    "java": ">=21",
    "fabric-api": "*"
  },
  "recommends": {
    "modmenu": "*",
    "cloth-config": "*"
  }
}
```

---

## 16. 风险评估

| 风险 | 等级 | 缓解措施 |
|---|---|---|
| 微时序被破坏 | 高 | 拓扑排序 + Level 1 默认 + 0t 检测 |
| 线程安全问题 | 高 | 读写锁 + 不可变对象 + double-buffer |
| 与 Lithium 冲突 | 中 | 检测 + 自动降级 + 配置项 |
| 字节码编译失败 | 中 | 回退到解释执行 |
| 内存占用增加 | 中 | LRU 淘汰 + 区块卸载清理 |
| 图重构开销 | 中 | 增量更新 + 异步重构 |

---

## 17. 实现优先级

1. **P0**：图数据结构 + 图构建 + 时序保障
2. **P0**：Mixin Hook + Level 1 保守优化
3. **P1**：Level 2/3 优化 + 自适应升级
4. **P1**：线程模型 + 缓存
5. **P2**：字节码编译
6. **P2**：配置系统 + 性能监控
7. **P3**：兼容性处理
