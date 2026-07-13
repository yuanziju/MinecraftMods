---
tags:
  - minecraft
  - mod
  - optimization
  - rendering
  - metal
  - macos
  - apple-silicon
  - fabric
  - draft
mod-name: Molten
framework: Fabric
mc-version: "1.21"
language: Kotlin
status: draft
created: 2026-07-13
---

# Molten（熔化）· macOS 图形渲染极致优化模组

> **命名灵感**：液态金属的状态，象征流畅流动的渲染体验。
>
> **定位**：专为 macOS（尤其是 Apple Silicon M1/M2/M3）优化的图形渲染模组。
>
> **核心目标**：绕过 OpenGL → MoltenVK → Metal 的转译链，实现 Metal 原生渲染。

---

## 1. 基础信息

| 属性 | 值 |
|---|---|
| 模组名称 | Molten |
| 开发框架 | Fabric |
| MC 版本 | 1.21 |
| 映射 | Mojmap（Mojang 官方映射） |
| 语言 | Kotlin |
| 优化范围 | 图形渲染（仅客户端） |
| 目标硬件 | Apple Silicon（M1/M2/M3） |
| 配置系统 | 是 |
| 性能监控 | Debug 版：Metal 性能计数器 + 渲染管线可视化 + 帧时间分析 |
| 兼容性 | Sodium（检测到后禁用部分冲突优化） |

---

## 2. 核心架构

### 2.1 架构总览

<div style="background:#1e1e1e; padding:16px; border-radius:8px; border:1px solid #444;">

<svg viewBox="0 0 1000 700" xmlns="http://www.w3.org/2000/svg" style="width:100%; height:auto;">

  <!-- 标题 -->
  <text x="500" y="30" fill="#E0E0E0" font-size="20" font-family="monospace" text-anchor="middle" font-weight="bold">Molten 架构总览</text>

  <!-- 当前渲染管线（问题） -->
  <rect x="20" y="50" width="200" height="120" rx="8" fill="#2a1a1a" stroke="#F44336" stroke-width="2"/>
  <text x="120" y="75" fill="#F44336" font-size="12" font-family="monospace" text-anchor="middle" font-weight="bold">当前（问题）</text>
  <text x="120" y="100" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">Minecraft 逻辑</text>
  <text x="120" y="118" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">Blaze3D (OpenGL)</text>
  <text x="120" y="136" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">MoltenVK (转译)</text>
  <text x="120" y="154" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">Metal API</text>
  <text x="120" y="162" fill="#FF9800" font-size="9" font-family="monospace" text-anchor="middle">← 性能损失 20-40%</text>

  <!-- 优化后渲染管线 -->
  <rect x="260" y="50" width="200" height="120" rx="8" fill="#1a2a1a" stroke="#4CAF50" stroke-width="2"/>
  <text x="360" y="75" fill="#4CAF50" font-size="12" font-family="monospace" text-anchor="middle" font-weight="bold">优化后</text>
  <text x="360" y="100" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">Minecraft 逻辑</text>
  <text x="360" y="118" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">Blaze3D (Metal)</text>
  <text x="360" y="136" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">Metal API</text>
  <text x="360" y="154" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">Apple Silicon GPU</text>
  <text x="360" y="162" fill="#4CAF50" font-size="9" font-family="monospace" text-anchor="middle">← 性能提升 30-50%</text>

  <!-- Mixin Hook 层 -->
  <rect x="500" y="50" width="200" height="120" rx="8" fill="#1a3a1a" stroke="#4CAF50" stroke-width="2"/>
  <text x="600" y="75" fill="#4CAF50" font-size="12" font-family="monospace" text-anchor="middle" font-weight="bold">Mixin Hook 层</text>
  <text x="600" y="100" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">Blaze3D RenderSystem</text>
  <text x="600" y="118" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">ShaderCompiler</text>
  <text x="600" y="136" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">EntityRenderer</text>
  <text x="600" y="154" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">BlockRenderer</text>

  <!-- 优化引擎层 -->
  <rect x="740" y="50" width="240" height="120" rx="8" fill="#1a2a3a" stroke="#2196F3" stroke-width="2"/>
  <text x="860" y="75" fill="#2196F3" font-size="12" font-family="monospace" text-anchor="middle" font-weight="bold">优化引擎层</text>
  <text x="860" y="100" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">MetalBackend（原生渲染）</text>
  <text x="860" y="118" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">ShaderCache（着色器缓存）</text>
  <text x="860" y="136" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">CommandQueue（命令队列）</text>
  <text x="860" y="154" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">ResourceManager（资源管理）</text>

  <!-- 数据流 -->
  <rect x="20" y="200" width="960" height="460" rx="8" fill="#1a1a1a" stroke="#666" stroke-width="1"/>
  <text x="500" y="220" fill="#E0E0E0" font-size="14" font-family="monospace" text-anchor="middle" font-weight="bold">数据流与组件交互</text>

  <!-- Metal Backend -->
  <rect x="40" y="250" width="200" height="140" rx="6" fill="#1a2a3a" stroke="#2196F3" stroke-width="1"/>
  <text x="140" y="270" fill="#2196F3" font-size="11" font-family="monospace" text-anchor="middle">MetalBackend</text>
  <text x="140" y="290" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">实现 RenderBackend 接口</text>
  <text x="140" y="305" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">创建 MTLDevice / CommandQueue</text>
  <text x="140" y="320" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">实现 VertexBuffer / IndexBuffer</text>
  <text x="140" y="335" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">实现 Shader / Texture / Framebuffer</text>
  <text x="140" y="350" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">实现 beginRender / endRender</text>
  <text x="140" y="365" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">根据硬件自动选择后端</text>

  <!-- Shader Cache -->
  <rect x="280" y="250" width="200" height="140" rx="6" fill="#2a2a1a" stroke="#FFC107" stroke-width="1"/>
  <text x="380" y="270" fill="#FFC107" font-size="11" font-family="monospace" text-anchor="middle">ShaderCache</text>
  <text x="380" y="290" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">首次运行时 GLSL → MSL 转换</text>
  <text x="380" y="305" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">计算着色器哈希码缓存</text>
  <text x="380" y="320" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">哈希码变化时重新编译</text>
  <text x="380" y="335" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">使用 glslang + SPIRV-Cross</text>
  <text x="380" y="350" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">利用 macOS 文件系统缓存</text>
  <text x="380" y="365" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">常用着色器预编译打包</text>

  <!-- Command Queue -->
  <rect x="520" y="250" width="200" height="140" rx="6" fill="#1a2a1a" stroke="#4CAF50" stroke-width="1"/>
  <text x="620" y="270" fill="#4CAF50" font-size="11" font-family="monospace" text-anchor="middle">CommandQueue</text>
  <text x="620" y="290" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">批量提交：多命令打包到一个 buffer</text>
  <text x="620" y="305" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">并行编码：多 encoder 并行工作</text>
  <text x="620" y="320" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">GPU 计算：粒子更新移到 GPU</text>
  <text x="620" y="335" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">使用 GCD 管理编码任务</text>
  <text x="620" y="350" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">优化 command buffer 生命周期</text>
  <text x="620" y="365" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">减少命令提交次数</text>

  <!-- Resource Manager -->
  <rect x="760" y="250" width="200" height="140" rx="6" fill="#2a1a2a" stroke="#9C27B0" stroke-width="1"/>
  <text x="860" y="270" fill="#9C27B0" font-size="11" font-family="monospace" text-anchor="middle">ResourceManager</text>
  <text x="860" y="290" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">纹理压缩：ASTC 格式</text>
  <text x="860" y="305" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">缓冲区优化：环形缓冲区</text>
  <text x="860" y="320" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">合理设置 storageMode</text>
  <text x="860" y="335" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">统一内存架构优化</text>
  <text x="860" y="350" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">优化 CPU/GPU 内存访问模式</text>
  <text x="860" y="365" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">减少显存分配和拷贝</text>

  <!-- Tiled Rendering -->
  <rect x="40" y="420" width="460" height="180" rx="8" fill="#1a1a2a" stroke="#2196F3" stroke-width="2"/>
  <text x="270" y="445" fill="#2196F3" font-size="14" font-family="monospace" text-anchor="middle" font-weight="bold">Tiled Rendering 优化</text>
  <text x="270" y="470" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">Tile-based Deferred Rendering</text>
  <text x="270" y="490" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">利用 Apple Silicon 的 tiled rendering 特性，优化深度测试和遮挡剔除</text>
  <text x="270" y="510" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">Early Z Culling</text>
  <text x="270" y="530" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">在 tile 内提前进行深度测试，减少不必要的像素着色</text>
  <text x="270" y="550" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">Tiled Lighting</text>
  <text x="270" y="570" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">在 tile 内计算光照，优化光照计算效率，减少带宽消耗</text>

  <!-- Debug System -->
  <rect x="540" y="420" width="440" height="180" rx="8" fill="#3a2a1a" stroke="#FF9800" stroke-width="2" stroke-dasharray="5,5"/>
  <text x="760" y="445" fill="#FF9800" font-size="14" font-family="monospace" text-anchor="middle" font-weight="bold">调试系统（Debug 版）</text>
  <text x="760" y="470" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">Metal 性能计数器</text>
  <text x="760" y="490" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">Metal API 调用次数、GPU 利用率、帧率、显存占用</text>
  <text x="760" y="510" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">渲染管线可视化</text>
  <text x="760" y="530" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">显示渲染管线的各个阶段（顶点着色→光栅化→像素着色）</text>
  <text x="760" y="550" fill="#E0E0E0" font-size="11" font-family="monospace" text-anchor="middle">帧时间分析</text>
  <text x="760" y="570" fill="#E0E0E0" font-size="9" font-family="monospace" text-anchor="middle">分析每帧的时间分布，找出耗时操作，定位性能瓶颈</text>

</svg>

</div>

### 2.2 数据流

```
Minecraft 游戏逻辑
  ↓ Mixin 拦截 Blaze3D RenderSystem
RenderBackendFactory.create()
  ↓ 根据硬件检测选择后端
MetalBackend（Apple Silicon）/ OpenGLBackend（其他）
  ↓
MetalBackend 初始化：MTLDevice / CommandQueue / PipelineState
  ↓
渲染命令生成：VertexBuffer / IndexBuffer / UniformBuffer
  ↓
ShaderCache：检查缓存 → 命中则加载 → 未命中则编译
  ↓
CommandQueue：批量提交 + 并行编码 + GPU 计算
  ↓
Metal API：提交到 GPU
  ↓
Tiled Rendering：Tile-based Deferred + Early Z + Tiled Lighting
  ↓
Apple Silicon GPU：渲染输出
```

---

## 3. 核心模块设计

### 3.1 MetalBackend（Metal 原生渲染后端）

```kotlin
interface RenderBackend {
    fun init()
    fun destroy()
    fun createVertexBuffer(data: FloatArray): VertexBuffer
    fun createIndexBuffer(data: IntArray): IndexBuffer
    fun createShader(vertexSource: String, fragmentSource: String): Shader
    fun createTexture(image: Image): Texture
    fun createFramebuffer(width: Int, height: Int): Framebuffer
    fun beginRender()
    fun endRender()
}

class MetalBackend : RenderBackend {
    val device: MTLDevice
    val commandQueue: MTLCommandQueue
    val pipelineStates: Map<String, MTLRenderPipelineState>
    
    override fun init() {
        device = MTLCreateSystemDefaultDevice()
        commandQueue = device.makeCommandQueue()
    }
    
    override fun createVertexBuffer(data: FloatArray): VertexBuffer
    override fun createIndexBuffer(data: IntArray): IndexBuffer
    override fun createShader(vertexSource: String, fragmentSource: String): Shader
    override fun createTexture(image: Image): Texture
    override fun createFramebuffer(width: Int, height: Int): Framebuffer
    override fun beginRender()
    override fun endRender()
}

object RenderBackendFactory {
    fun create(): RenderBackend {
        return if (isAppleSilicon()) MetalBackend() else OpenGLBackend()
    }
}
```

### 3.2 ShaderCache（着色器缓存）

```kotlin
class ShaderCache {
    val cacheDir: File
    val compiledShaders: Map<String, CompiledShader>
    
    fun getOrCompile(vertexSource: String, fragmentSource: String): CompiledShader {
        val hash = computeHash(vertexSource, fragmentSource)
        val cached = compiledShaders[hash]
        if (cached != null) return cached
        
        val compiled = compile(vertexSource, fragmentSource)
        compiledShaders[hash] = compiled
        saveToDisk(hash, compiled)
        return compiled
    }
    
    fun computeHash(vertexSource: String, fragmentSource: String): String
    
    fun compile(vertexSource: String, fragmentSource: String): CompiledShader {
        val spirvVertex = glslang.compileVertex(vertexSource)
        val spirvFragment = glslang.compileFragment(fragmentSource)
        val mslVertex = spirvCross.convert(spirvVertex, Target.MSL)
        val mslFragment = spirvCross.convert(spirvFragment, Target.MSL)
        return CompiledShader(mslVertex, mslFragment)
    }
    
    fun saveToDisk(hash: String, shader: CompiledShader)
    
    fun loadFromDisk(hash: String): CompiledShader?
}
```

### 3.3 CommandQueue（命令队列优化）

```kotlin
class CommandQueueOptimizer(val commandQueue: MTLCommandQueue) {
    val commandBuffers: Queue<MTLCommandBuffer>
    val encoders: List<MTLRenderCommandEncoder>
    
    fun submitBatch(commands: List<RenderCommand>) {
        val buffer = commandQueue.makeCommandBuffer()
        val encoder = buffer.makeRenderCommandEncoder(descriptor)
        
        for (command in commands) {
            encoder.setVertexBuffer(command.vertexBuffer)
            encoder.setFragmentBuffer(command.fragmentBuffer)
            encoder.drawIndexedPrimitives(...)
        }
        
        encoder.endEncoding()
        buffer.commit()
    }
    
    fun submitParallel(commands: List<RenderCommand>) {
        val buffers = mutableListOf<MTLCommandBuffer>()
        
        val groups = commands.chunked(commands.size / numberOfCores)
        for (group in groups) {
            val buffer = commandQueue.makeCommandBuffer()
            val encoder = buffer.makeRenderCommandEncoder(descriptor)
            
            for (command in group) {
                encoder.drawIndexedPrimitives(...)
            }
            
            encoder.endEncoding()
            buffers.add(buffer)
        }
        
        for (buffer in buffers) {
            buffer.commit()
        }
    }
    
    fun submitCompute(computeCommand: ComputeCommand) {
        val buffer = commandQueue.makeCommandBuffer()
        val encoder = buffer.makeComputeCommandEncoder()
        
        encoder.setComputePipelineState(computeCommand.pipelineState)
        encoder.setBuffer(computeCommand.inputBuffer)
        encoder.setBuffer(computeCommand.outputBuffer)
        encoder.dispatchThreads(...)
        
        encoder.endEncoding()
        buffer.commit()
    }
}
```

### 3.4 ResourceManager（资源管理优化）

```kotlin
class ResourceManager(val device: MTLDevice) {
    fun createCompressedTexture(image: Image): MTLTexture {
        val descriptor = MTLTextureDescriptor.texture2DDescriptor(
            pixelFormat = MTLPixelFormat.astc_4x4_srgb,
            width = image.width,
            height = image.height,
            mipmapped = true
        )
        return device.makeTexture(descriptor)
    }
    
    fun createRingBuffer(size: Int): RingBuffer {
        val buffer = device.makeBuffer(
            length = size,
            options = MTLResourceOptions.storageModeShared
        )
        return RingBuffer(buffer)
    }
    
    fun optimizeMemoryAccess(buffer: MTLBuffer, accessMode: AccessMode) {
        buffer.setPurgeableState(MTLPurgeableState.keepCurrent)
    }
}

class RingBuffer(val buffer: MTLBuffer) {
    var readIndex: Int = 0
    var writeIndex: Int = 0
    
    fun write(data: ByteArray): Boolean
    fun read(size: Int): ByteArray?
    fun hasSpace(size: Int): Boolean
}
```

### 3.5 TiledRendering（Tiled 渲染优化）

```kotlin
class TiledRenderer(val device: MTLDevice) {
    val tileSize: Int = 16
    
    fun renderTiled(renderCommands: List<RenderCommand>, framebuffer: MTLTexture) {
        for (tileX in 0 until framebuffer.width step tileSize) {
            for (tileY in 0 until framebuffer.height step tileSize) {
                val tileCommands = cullToTile(renderCommands, tileX, tileY)
                renderTile(tileCommands, tileX, tileY)
            }
        }
    }
    
    fun cullToTile(commands: List<RenderCommand>, tileX: Int, tileY: Int): List<RenderCommand>
    
    fun renderTile(commands: List<RenderCommand>, tileX: Int, tileY: Int)
    
    fun earlyZCull(commands: List<RenderCommand>, depthBuffer: MTLTexture): List<RenderCommand>
    
    fun tiledLighting(commands: List<RenderCommand>, lights: List<Light>, tileX: Int, tileY: Int)
}
```

### 3.6 Debug System（调试系统）

```kotlin
class DebugSystem(val device: MTLDevice) {
    val performanceCounters: PerformanceCounters
    val pipelineVisualizer: PipelineVisualizer
    val frameTimeAnalyzer: FrameTimeAnalyzer
    
    fun enable()
    fun disable()
    fun renderHUD(poseStack: PoseStack)
}

class PerformanceCounters {
    var drawCalls: Int = 0
    var gpuUtilization: Float = 0f
    var frameTime: Long = 0L
    var memoryUsage: Long = 0L
    
    fun update()
}

class FrameTimeAnalyzer {
    val frameTimes: Deque<Long> = ArrayDeque()
    
    fun recordFrameTime(time: Long)
    fun getAverageFrameTime(): Long
    fun getMaxFrameTime(): Long
    fun getMinFrameTime(): Long
    fun getFrameTimeVariance(): Double
}
```

---

## 4. Mixin Hook 策略

### 4.1 Hook 位置

| Hook 位置 | 目的 | 优化方向 |
|---|---|---|
| `RenderSystem.init` | 拦截渲染系统初始化，替换为 MetalBackend | Metal 原生渲染 |
| `ShaderCompiler.compile` | 拦截着色器编译，使用 ShaderCache | 着色器缓存 |
| `VertexBuffer.upload` | 拦截顶点缓冲区上传，优化内存访问 | 资源管理 |
| `Texture.bind` | 拦截纹理绑定，优化纹理缓存 | 资源管理 |
| `EntityRenderer.render` | 拦截实体渲染，应用批量渲染 | 命令队列优化 |
| `BlockRenderer.render` | 拦截方块渲染，应用批量渲染 | 命令队列优化 |
| `ParticleRenderer.render` | 拦截粒子渲染，移到 GPU 计算 | GPU 计算 |

### 4.2 Hook 原则

- **非侵入式**：通过 Mixin 注入，不修改原版类文件
- **可回退**：不支持 Metal 时自动回退到 OpenGL
- **硬件检测**：只在 Apple Silicon 上启用 Metal 后端
- **兼容性**：检测到 Sodium 后禁用冲突优化

---

## 5. 配置系统

### 5.1 配置项

```properties
# Metal 后端
metal.enable=true              # 启用 Metal 原生渲染
metal.backend=auto            # auto / metal / opengl

# 着色器缓存
shader.cache=true             # 启用着色器缓存
shader.cache_dir=.cache/molten # 缓存目录
shader.precompile=true        # 启用预编译

# 命令队列优化
command.batch=true            # 启用批量提交
command.parallel=true         # 启用并行编码
command.compute=true          # 启用 GPU 计算

# 资源管理优化
resource.compression=true     # 启用纹理压缩
resource.ring_buffer=true     # 启用环形缓冲区
resource.unified_memory=true  # 启用统一内存优化

# Tiled 渲染优化
tiled.deferred=true          # 启用 Tile-based Deferred
tiled.early_z=true           # 启用 Early Z Culling
tiled.lighting=true          # 启用 Tiled Lighting

# 调试（仅 Debug 版有效）
debug.counters=true          # 启用性能计数器
debug.visualizer=true        # 启用渲染管线可视化
debug.frame_time=true        # 启用帧时间分析
```

---

## 6. 项目结构

```
mods/molten/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/molten/optimization/mixin/
│       │       ├── RenderSystemMixin.java
│       │       ├── ShaderCompilerMixin.java
│       │       ├── VertexBufferMixin.java
│       │       ├── TextureMixin.java
│       │       ├── EntityRendererMixin.java
│       │       ├── BlockRendererMixin.java
│       │       └── ParticleRendererMixin.java
│       ├── kotlin/
│       │   └── com/molten/optimization/
│       │       ├── MoltenMod.kt
│       │       ├── MoltenClient.kt
│       │       ├── config/
│       │       │   ├── MoltenConfig.kt
│       │       │   └── ModMenuIntegration.kt
│       │       ├── backend/
│       │       │   ├── RenderBackend.kt
│       │       │   ├── MetalBackend.kt
│       │       │   ├── OpenGLBackend.kt
│       │       │   └── RenderBackendFactory.kt
│       │       ├── shader/
│       │       │   ├── ShaderCache.kt
│       │       │   ├── ShaderCompiler.kt
│       │       │   └── CompiledShader.kt
│       │       ├── command/
│       │       │   ├── CommandQueueOptimizer.kt
│       │       │   ├── RenderCommand.kt
│       │       │   └── ComputeCommand.kt
│       │       ├── resource/
│       │       │   ├── ResourceManager.kt
│       │       │   ├── RingBuffer.kt
│       │       │   └── TextureCompressor.kt
│       │       ├── tiled/
│       │       │   ├── TiledRenderer.kt
│       │       │   └── TileCuller.kt
│       │       ├── debug/
│       │       │   ├── DebugSystem.kt
│       │       │   ├── PerformanceCounters.kt
│       │       │   ├── PipelineVisualizer.kt
│       │       │   └── FrameTimeAnalyzer.kt
│       │       └── compat/
│       │           ├── CompatDetector.kt
│       │           └── SodiumCompat.kt
│       └── resources/
│           ├── fabric.mod.json
│           ├── molten.mixins.json
│           └── assets/molten/lang/
├── build.gradle.kts
├── gradle.properties
└── settings.gradle.kts
```

---

## 7. 构建配置

### 7.1 gradle.properties

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
maven_group=com.molten
archives_base_name=molten

# Dependencies
mod_menu_version=11.0.0-beta.1
cloth_config_version=15.0.127

# Debug flag
debug_build=false
```

---

## 8. 风险评估

| 风险 | 等级 | 缓解措施 |
|---|---|---|
| Metal API 兼容性 | 高 | 不支持时自动回退到 OpenGL；检测硬件支持 |
| 着色器转换错误 | 高 | 缓存失败时回退到运行时转换；错误处理 |
| 命令队列同步问题 | 中 | 使用 fence 同步；合理设置 command buffer 生命周期 |
| 资源泄漏 | 中 | 统一资源管理；使用 ARC（Automatic Reference Counting） |
| 与 Sodium 冲突 | 高 | 检测到 Sodium 后禁用冲突优化；自动降级 |
| Tiled Rendering 实现复杂 | 中 | 先实现基础功能，逐步添加优化 |

---

## 9. 实现优先级

1. **P0**：MetalBackend + RenderBackendFactory（核心）
2. **P0**：ShaderCache（着色器缓存+哈希校验）
3. **P1**：CommandQueueOptimizer（批量提交+并行编码+GPU计算）
4. **P1**：ResourceManager（纹理压缩+环形缓冲区+统一内存）
5. **P2**：TiledRenderer（Tile-based Deferred + Early Z + Tiled Lighting）
6. **P2**：DebugSystem（性能计数器+渲染管线可视化+帧时间分析）
7. **P3**：兼容性处理（Sodium 检测+自动降级）

---

## 10. 性能预期

| 优化项 | 预期提升 |
|---|---|
| Metal 原生渲染路径 | **30-50%** |
| 着色器缓存 | **80%+**（编译时间） |
| 命令队列优化 | **20-30%** |
| 资源管理优化 | **15-25%** |
| Tiled Rendering | **30-40%** |
| **总计** | **50-80%** |
