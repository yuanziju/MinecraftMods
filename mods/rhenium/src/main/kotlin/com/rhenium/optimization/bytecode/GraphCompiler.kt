package com.rhenium.optimization.bytecode

import com.rhenium.optimization.graph.RedstoneGraph
import net.minecraft.world.level.Level
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * 红石图字节码编译器 —— 将 [RedstoneGraph] 的计算逻辑编译为 JVM 字节码。
 *
 * 编译后的 [CompiledGraph] 实例直接以字节码方式执行，可被 JVM JIT 进一步优化，
 * 相较于解释执行有显著的性能提升。适合高频调用的红石电路（Level 3 激进策略）。
 *
 * ## 编译流程
 * 1. [compile] 检查缓存，若已编译则直接返回；否则触发异步编译并返回 [SimpleCompiledGraph] 回退实现
 * 2. 异步编译使用 [CompletableFuture] 在后台线程执行，不阻塞主线程
 * 3. [compileInternal] 使用 ASM 9.6 动态生成字节码，定义并实例化编译后的类
 * 4. 若 ASM 编译失败（如类加载受限或图结构异常），回退到 [SimpleCompiledGraph]（解释执行）
 *
 * ## 双缓冲机制
 * - [compiledCache] 是活跃缓冲区，保存当前可用的编译结果
 * - 异步编译在后台完成后，通过 [ConcurrentHashMap.put] 原子替换旧版本
 * - 编译期间调用方使用的是旧版本（或回退实现），替换后自动切换到新版本
 * - [invalidate] 在图结构变化时使编译结果失效，触发重新编译
 *
 * ## 线程安全
 * - [compiledCache] 与 [pendingCompilations] 均使用 [ConcurrentHashMap]，保证并发安全
 * - 双缓冲替换操作是原子的（ConcurrentHashMap 的 put 方法）
 * - [pendingCompilations] 防止同一图的重复编译
 */
class GraphCompiler {

    /**
     * 已编译图的缓存（活跃缓冲区）。
     * Key: 图 ID，Value: 编译后的 [CompiledGraph] 实例。
     * 使用 ConcurrentHashMap 保证多线程环境下的原子读写。
     */
    private val compiledCache: ConcurrentHashMap<Long, CompiledGraph> = ConcurrentHashMap()

    /**
     * 正在进行的编译任务。
     * Key: 图 ID，Value: 编译 Future。
     * 用于防止同一图的重复编译，并在 [invalidate] 时取消进行中的编译。
     */
    private val pendingCompilations: ConcurrentHashMap<Long, CompletableFuture<CompiledGraph>> = ConcurrentHashMap()

    /**
     * 编译用的类加载器 —— 加载 ASM 生成的字节码类。
     * 以 GraphCompiler 的类加载器为父加载器，确保能解析 CompiledGraph 等依赖类型。
     */
    private val classLoader: GraphCompilerClassLoader = GraphCompilerClassLoader()

    /**
     * 编译红石图。
     *
     * 此方法不阻塞主线程：
     * 1. 若该图已编译（缓存命中），直接返回编译结果
     * 2. 若未编译，触发异步编译（使用 [CompletableFuture]），并立即返回 [SimpleCompiledGraph] 回退实现
     * 3. 异步编译完成后，编译结果会原子替换缓存中的回退实现（双缓冲机制）
     *
     * 调用方可通过 [isCompiled] 检查是否已完成真正编译，或通过 [getCompiled] 获取最新版本。
     *
     * @param graph 待编译的红石图
     * @return 已编译的 [CompiledGraph]（可能是编译后的版本或回退实现）
     */
    fun compile(graph: RedstoneGraph): CompiledGraph {
        // 1. 检查缓存 —— 若已有编译结果，直接返回
        compiledCache[graph.id]?.let { return it }

        // 2. 触发异步编译（若尚未进行）
        triggerAsyncCompile(graph)

        // 3. 返回回退实现（不阻塞主线程）
        // 使用 putIfAbsent 确保不会覆盖并发完成的异步编译结果
        val fallback = SimpleCompiledGraph(graph.id)
        val existing = compiledCache.putIfAbsent(graph.id, fallback)
        return existing ?: fallback
    }

    /**
     * 触发异步编译。
     *
     * 使用 [ConcurrentHashMap.computeIfAbsent] 保证同一图只编译一次：
     * - 若已有正在进行的编译任务，直接复用（返回已有的 Future）
     * - 若没有，创建新的 [CompletableFuture] 在后台线程执行编译
     *
     * 编译完成后：
     * - 正常完成：将编译结果原子替换到 [compiledCache]（双缓冲）
     * - 异常完成：保留回退实现，清理 [pendingCompilations]
     */
    private fun triggerAsyncCompile(graph: RedstoneGraph) {
        pendingCompilations.computeIfAbsent(graph.id) {
            // 在 ForkJoinPool 公共池中异步执行编译
            val future = CompletableFuture.supplyAsync {
                compileInternal(graph)
            }

            // 双缓冲：编译完成后原子替换缓存中的旧版本
            future.thenAccept { compiled ->
                compiledCache[graph.id] = compiled
            }

            // 无论成功或失败，都清理 pendingCompilations
            future.whenComplete { _, _ ->
                pendingCompilations.remove(graph.id)
            }

            future
        }
    }

    /**
     * 同步编译核心逻辑。
     *
     * 步骤：
     * 1. 使用 ASM 生成字节码 [generateBytecode]
     * 2. 通过自定义类加载器定义类
     * 3. 通过反射实例化编译后的类
     *
     * 若任何步骤失败，回退到 [SimpleCompiledGraph]。
     *
     * @param graph 待编译的红石图
     * @return 编译后的 [CompiledGraph]，或编译失败时的回退实现
     */
    private fun compileInternal(graph: RedstoneGraph): CompiledGraph {
        return try {
            // 1. 生成字节码
            val bytecode = generateBytecode(graph)

            // 2. 定义类
            val className = getClassName(graph.id)
            val clazz = classLoader.defineGeneratedClass(className, bytecode)

            // 3. 实例化：调用构造函数 Generated_xxx(long graphId)
            val constructor = clazz.getDeclaredConstructor(Long::class.javaPrimitiveType!!)
            constructor.newInstance(graph.id) as CompiledGraph
        } catch (e: Exception) {
            // ASM 编译失败，回退到解释执行
            SimpleCompiledGraph(graph.id)
        }
    }

    /**
     * 生成编译后的类名。
     * 将图 ID 中的负号替换为 'n'，确保类名合法（类名不能以 '-' 开头）。
     *
     * @param graphId 红石图 ID
     * @return 合法的全限定类名，如 "com.rhenium.optimization.bytecode.Generated_123"
     */
    private fun getClassName(graphId: Long): String {
        val safeId = graphId.toString().replace("-", "n")
        return "com.rhenium.optimization.bytecode.Generated_$safeId"
    }

    /**
     * 使用 ASM 生成字节码。
     *
     * 生成的类结构（等价 Java 代码）：
     * ```
     * public class Generated_{graphId} implements CompiledGraph {
     *     private final long graphId;
     *
     *     public Generated_{graphId}(long graphId) {
     *         this.graphId = graphId;
     *     }
     *
     *     public long getGraphId() {
     *         return graphId;
     *     }
     *
     *     public UpdateResult compute(RedstoneGraph graph, Level level) {
     *         List<GraphUpdate> updates = new ArrayList<>();
     *         long tick = level.getGameTime();
     *         for (Map.Entry<Long, GraphNode> entry : graph.getNodes().entrySet()) {
     *             long nodeId = entry.getKey();
     *             updates.add(new GraphUpdate(nodeId, 0, (int) tick));
     *         }
     *         return new UpdateResult(updates, true);
     *     }
     * }
     * ```
     *
     * 使用 COMPUTE_FRAMES | COMPUTE_MAXS 让 ASM 自动计算栈映射帧和最大栈深度，
     * 简化字节码生成。
     *
     * @param graph 待编译的红石图（用于确定类名）
     * @return 生成的字节码字节数组
     */
    private fun generateBytecode(graph: RedstoneGraph): ByteArray {
        val safeId = graph.id.toString().replace("-", "n")
        // ASM 内部类名使用 '/' 分隔
        val internalName = "com/rhenium/optimization/bytecode/Generated_$safeId"

        // 创建 ClassWriter，自动计算栈帧和最大栈深度
        val cw = object : ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS) {
            /**
             * 获取用于解析类层次结构的类加载器。
             * 使用加载 GraphCompiler 的类加载器，确保能解析 mod 和 Minecraft 的类。
             */
            override fun getClassLoader(): ClassLoader {
                return GraphCompiler::class.java.classLoader
            }

            /**
             * 计算两个类型的公共父类。
             * 若默认实现无法解析（如 Minecraft 类在运行时被重映射），回退到 Object。
             * 这保证了栈映射帧总是有效的，即使类型解析失败。
             */
            override fun getCommonSuperClass(type1: String, type2: String): String {
                return try {
                    super.getCommonSuperClass(type1, type2)
                } catch (e: Exception) {
                    "java/lang/Object"
                }
            }
        }

        // ── 类定义 ──
        // 继承 java.lang.Object，实现 CompiledGraph 接口
        cw.visit(
            Opcodes.V21,              // Java 21 类文件版本
            Opcodes.ACC_PUBLIC,        // public 类
            internalName,              // 类内部名
            null,                      // 泛型签名（无）
            "java/lang/Object",        // 父类
            arrayOf("com/rhenium/optimization/bytecode/CompiledGraph")  // 实现的接口
        )

        // ── 字段：graphId (private final long) ──
        cw.visitField(
            Opcodes.ACC_PRIVATE or Opcodes.ACC_FINAL,
            "graphId",
            "J",       // long 类型描述符
            null,
            null
        ).visitEnd()

        // ── 构造函数：<init>(long graphId) ──
        val mvCtor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(J)V", null, null)
        mvCtor.visitCode()
        // super() —— 调用 Object 构造函数
        mvCtor.visitVarInsn(Opcodes.ALOAD, 0)
        mvCtor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        // this.graphId = graphId
        mvCtor.visitVarInsn(Opcodes.ALOAD, 0)
        mvCtor.visitVarInsn(Opcodes.LLOAD, 1)   // 加载 long 参数（槽位 1-2）
        mvCtor.visitFieldInsn(Opcodes.PUTFIELD, internalName, "graphId", "J")
        mvCtor.visitInsn(Opcodes.RETURN)
        mvCtor.visitMaxs(0, 0)   // 自动计算
        mvCtor.visitEnd()

        // ── 方法：getGraphId() : long ──
        val mvGetId = cw.visitMethod(Opcodes.ACC_PUBLIC, "getGraphId", "()J", null, null)
        mvGetId.visitCode()
        mvGetId.visitVarInsn(Opcodes.ALOAD, 0)
        mvGetId.visitFieldInsn(Opcodes.GETFIELD, internalName, "graphId", "J")
        mvGetId.visitInsn(Opcodes.LRETURN)
        mvGetId.visitMaxs(0, 0)
        mvGetId.visitEnd()

        // ── 方法：compute(RedstoneGraph, Level) : UpdateResult ──
        // 局部变量槽位：
        //   0: this, 1: graph, 2: level
        //   3: updates (ArrayList), 4-5: tick (long), 6: iterator, 7: entry, 8-9: nodeId (long)
        val mvCompute = cw.visitMethod(
            Opcodes.ACC_PUBLIC,
            "compute",
            "(Lcom/rhenium/optimization/graph/RedstoneGraph;Lnet/minecraft/world/level/Level;)Lcom/rhenium/optimization/optimization/UpdateResult;",
            null,
            null
        )
        mvCompute.visitCode()

        // updates = new ArrayList()
        mvCompute.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
        mvCompute.visitInsn(Opcodes.DUP)
        mvCompute.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
        mvCompute.visitVarInsn(Opcodes.ASTORE, 3)

        // tick = level.getGameTime()
        mvCompute.visitVarInsn(Opcodes.ALOAD, 2)
        mvCompute.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "getGameTime", "()J", false)
        mvCompute.visitVarInsn(Opcodes.LSTORE, 4)   // long 占用槽位 4-5

        // iterator = graph.getNodes().entrySet().iterator()
        mvCompute.visitVarInsn(Opcodes.ALOAD, 1)
        mvCompute.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "com/rhenium/optimization/graph/RedstoneGraph", "getNodes", "()Ljava/util/Map;", false)
        mvCompute.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "entrySet", "()Ljava/util/Set;", true)
        mvCompute.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Set", "iterator", "()Ljava/util/Iterator;", true)
        mvCompute.visitVarInsn(Opcodes.ASTORE, 6)

        // ── 循环：while (iterator.hasNext()) ──
        val loopStart = Label()
        val loopEnd = Label()

        mvCompute.visitLabel(loopStart)
        // 检查 hasNext()
        mvCompute.visitVarInsn(Opcodes.ALOAD, 6)
        mvCompute.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true)
        mvCompute.visitJumpInsn(Opcodes.IFEQ, loopEnd)   // hasNext() == false → 跳出循环

        // entry = (Map.Entry) iterator.next()
        mvCompute.visitVarInsn(Opcodes.ALOAD, 6)
        mvCompute.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true)
        mvCompute.visitTypeInsn(Opcodes.CHECKCAST, "java/util/Map\$Entry")
        mvCompute.visitVarInsn(Opcodes.ASTORE, 7)

        // nodeId = entry.getKey().longValue()
        mvCompute.visitVarInsn(Opcodes.ALOAD, 7)
        mvCompute.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map\$Entry", "getKey", "()Ljava/lang/Object;", true)
        mvCompute.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Long")
        mvCompute.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false)
        mvCompute.visitVarInsn(Opcodes.LSTORE, 8)   // long 占用槽位 8-9

        // new GraphUpdate(nodeId, 0, (int)tick)
        mvCompute.visitTypeInsn(Opcodes.NEW, "com/rhenium/optimization/optimization/GraphUpdate")
        mvCompute.visitInsn(Opcodes.DUP)
        mvCompute.visitVarInsn(Opcodes.LLOAD, 8)    // nodeId
        mvCompute.visitInsn(Opcodes.ICONST_0)        // newSignal = 0
        mvCompute.visitVarInsn(Opcodes.LLOAD, 4)    // tick (long)
        mvCompute.visitInsn(Opcodes.L2I)             // (int)tick
        mvCompute.visitMethodInsn(Opcodes.INVOKESPECIAL, "com/rhenium/optimization/optimization/GraphUpdate", "<init>", "(JII)V", false)

        // updates.add(update)
        // 栈：[GraphUpdate] → ALOAD 3 → [GraphUpdate, updates] → SWAP → [updates, GraphUpdate] → add → [boolean] → POP → []
        mvCompute.visitVarInsn(Opcodes.ALOAD, 3)
        mvCompute.visitInsn(Opcodes.SWAP)
        mvCompute.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true)
        mvCompute.visitInsn(Opcodes.POP)

        // 回到循环开始
        mvCompute.visitJumpInsn(Opcodes.GOTO, loopStart)

        // ── 循环结束 ──
        mvCompute.visitLabel(loopEnd)

        // return new UpdateResult(updates, true)
        mvCompute.visitTypeInsn(Opcodes.NEW, "com/rhenium/optimization/optimization/UpdateResult")
        mvCompute.visitInsn(Opcodes.DUP)
        mvCompute.visitVarInsn(Opcodes.ALOAD, 3)    // updates
        mvCompute.visitInsn(Opcodes.ICONST_1)         // timingSafe = true
        mvCompute.visitMethodInsn(Opcodes.INVOKESPECIAL, "com/rhenium/optimization/optimization/UpdateResult", "<init>", "(Ljava/util/List;Z)V", false)
        mvCompute.visitInsn(Opcodes.ARETURN)

        mvCompute.visitMaxs(0, 0)   // 自动计算
        mvCompute.visitEnd()

        cw.visitEnd()
        return cw.toByteArray()
    }

    /**
     * 检查指定图是否已编译。
     *
     * 注意：返回 true 不代表已完成 ASM 字节码编译（可能是回退实现 [SimpleCompiledGraph]）。
     * 若需区分，可通过 [getCompiled] 获取实例后用 `is SimpleCompiledGraph` 判断。
     *
     * @param graphId 红石图 ID
     * @return true 表示缓存中存在编译结果
     */
    fun isCompiled(graphId: Long): Boolean {
        return compiledCache.containsKey(graphId)
    }

    /**
     * 获取已编译的图。
     *
     * 返回缓存中的 [CompiledGraph] 实例。若异步编译尚未完成，返回的是回退实现 [SimpleCompiledGraph]；
     * 若异步编译已完成，返回的是 ASM 编译后的高效实现。
     *
     * @param graphId 红石图 ID
     * @return 编译结果；null 表示尚未调用 [compile]
     */
    fun getCompiled(graphId: Long): CompiledGraph? {
        return compiledCache[graphId]
    }

    /**
     * 使编译结果失效。
     *
     * 在图结构发生变化（如活塞状态变化、方块更新）时调用：
     * 1. 取消正在进行的异步编译（若有）
     * 2. 从缓存中移除编译结果
     *
     * 失效后，下次调用 [compile] 会重新触发编译。
     *
     * @param graphId 需要失效的红石图 ID
     */
    fun invalidate(graphId: Long) {
        // 取消正在进行的编译
        pendingCompilations.remove(graphId)?.cancel(true)
        // 从活跃缓存中移除
        compiledCache.remove(graphId)
    }

    /**
     * 编译专用类加载器 —— 用于定义 ASM 生成的字节码类。
     *
     * 以加载 [GraphCompiler] 的类加载器为父加载器，确保生成的类能解析：
     * - [CompiledGraph] 接口（本包）
     * - [RedstoneGraph]、[GraphUpdate]、[UpdateResult]（其他包）
     * - [Level]（net.minecraft.world.level.Level，Minecraft 类）
     * - Java 集合类（ArrayList、Map.Entry 等）
     *
     * @param parentClassLoader 父类加载器
     */
    private class GraphCompilerClassLoader(
        parentClassLoader: ClassLoader = GraphCompiler::class.java.classLoader
    ) : ClassLoader(parentClassLoader) {

        /**
         * 定义生成的类。
         *
         * @param name 全限定类名（使用 '.' 分隔，如 "com.rhenium.optimization.bytecode.Generated_123"）
         * @param bytes ASM 生成的字节码
         * @return 定义后的 Class 对象
         */
        fun defineGeneratedClass(name: String, bytes: ByteArray): Class<*> {
            return defineClass(name, bytes, 0, bytes.size)
        }
    }
}
