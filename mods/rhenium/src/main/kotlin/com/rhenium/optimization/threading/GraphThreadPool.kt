package com.rhenium.optimization.threading

import com.rhenium.optimization.graph.RedstoneGraph
import com.rhenium.optimization.optimization.UpdateResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 红石图线程池 —— 根据图大小分配不同的线程处理策略。
 *
 * 线程分配策略（对应设计文档 7.1 线程模型）：
 * - **大图（>100 节点）**：提交到单线程执行器（[largeGraphExecutor]），
 *   独立线程严格按原版顺序计算，避免并发干扰，保证时序安全。
 * - **中图（20-100 节点）**：通过 [MediumGraphQueue] 放入任务队列，
 *   由固定大小线程池（[mediumGraphExecutor]）多线程并行处理，图内保持拓扑顺序。
 * - **小图（<20 节点）**：直接在主线程同步处理，不创建线程，
 *   精确保持原版时序，避免线程切换开销。
 *
 * 线程命名：所有线程均为守护线程（daemon），命名为 `Rhenium-LargeGraph-N` / `Rhenium-MediumGraph-N`，
 * 便于调试与监控。
 *
 * @param maxThreads 中图线程池的最大线程数（通常取 CPU 核心数 - 1）
 */
class GraphThreadPool(maxThreads: Int) {

    /**
     * 大图执行器 —— 单线程执行器。
     *
     * 使用单线程保证大图的计算严格按顺序执行，不受其他图计算干扰。
     * 这对大图的时序安全至关重要：原版红石大电路的微时序必须被精确保持。
     */
    private val largeGraphExecutor: ExecutorService = Executors.newSingleThreadExecutor(
        NamedThreadFactory("Rhenium-LargeGraph")
    )

    /**
     * 中图执行器 —— 固定大小线程池。
     *
     * 多个中图可以并行计算，提升吞吐量。线程数由 [maxThreads] 决定，
     * 至少为 1 以避免空线程池。
     */
    private val mediumGraphExecutor: ExecutorService = Executors.newFixedThreadPool(
        maxThreads.coerceAtLeast(1),
        NamedThreadFactory("Rhenium-MediumGraph")
    )

    /**
     * 中图任务队列 —— 管理中图的提交与 work-stealing 调度。
     * 内部基于 [mediumGraphExecutor] 执行任务。
     */
    private val mediumGraphQueue: MediumGraphQueue = MediumGraphQueue(mediumGraphExecutor)

    /**
     * 提交大图计算任务。
     *
     * 大图（>100 节点，[RedstoneGraph.isLargeGraph]）通过 [LargeGraphTask] 封装后
     * 提交到单线程执行器 [largeGraphExecutor]。单线程保证计算不受并发干扰，
     * [LargeGraphTask] 内部会先进行拓扑排序再执行计算，确保时序安全。
     *
     * @param graph 待计算的大图
     * @param task 实际的计算逻辑
     * @return [Future]，可在未来获取计算结果
     */
    fun submitLargeGraph(graph: RedstoneGraph, task: () -> UpdateResult): Future<UpdateResult> {
        // LargeGraphTask 实现 Callable<UpdateResult>，可直接提交到 ExecutorService
        val largeTask = LargeGraphTask(graph, task)
        return largeGraphExecutor.submit(largeTask)
    }

    /**
     * 提交中图计算任务。
     *
     * 中图（20-100 节点，[RedstoneGraph.isMediumGraph]）通过 [MediumGraphQueue.submit] 提交，
     * 进入共享任务队列后由 [mediumGraphExecutor] 多线程并行处理。
     * work-stealing 机制确保任务均匀分配到各线程。
     *
     * @param graph 待计算的中图
     * @param task 实际的计算逻辑
     * @return [Future]，可在未来获取计算结果
     */
    fun submitMediumGraph(graph: RedstoneGraph, task: () -> UpdateResult): Future<UpdateResult> {
        return mediumGraphQueue.submit(graph, task)
    }

    /**
     * 处理小图计算任务。
     *
     * 小图（<20 节点，[RedstoneGraph.isSmallGraph]）直接在调用线程（通常是主线程）同步处理，
     * 不创建线程、不进入队列。这避免了线程切换开销，同时精确保持原版时序。
     *
     * @param graph 待计算的小图
     * @param task 实际的计算逻辑
     * @return 同步执行的计算结果
     */
    fun processSmallGraph(graph: RedstoneGraph, task: () -> UpdateResult): UpdateResult {
        // 直接在当前线程同步执行，无任何异步开销
        return task()
    }

    /**
     * 关闭所有线程池。
     *
     * 先发起优雅关闭（等待已提交任务完成），若超时未完成则强制关闭。
     * 关闭后所有待处理任务会被取消。
     */
    fun shutdown() {
        // 发起优雅关闭
        largeGraphExecutor.shutdown()
        mediumGraphExecutor.shutdown()

        try {
            // 等待大图执行器中的任务完成（最多 5 秒）
            if (!largeGraphExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                // 超时后强制关闭
                largeGraphExecutor.shutdownNow()
            }
            // 等待中图执行器中的任务完成（最多 5 秒）
            if (!mediumGraphExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                // 超时后强制关闭
                mediumGraphExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            // 等待过程中被中断，强制关闭所有执行器
            largeGraphExecutor.shutdownNow()
            mediumGraphExecutor.shutdownNow()
            // 恢复中断状态
            Thread.currentThread().interrupt()
        }
    }
}

/**
 * 命名线程工厂 —— 为线程池创建具名守护线程。
 *
 * 生成的线程：
 * - 名称为 `{prefix}-{counter}`，便于在日志和调试工具中识别
 * - 设置为守护线程（daemon=true），确保 JVM 退出时不会被这些线程阻塞
 *
 * @param name 线程名前缀
 */
private class NamedThreadFactory(private val name: String) : ThreadFactory {
    /** 线程计数器，用于生成唯一线程名 */
    private val counter = AtomicInteger(0)

    override fun newThread(r: Runnable): Thread {
        val thread = Thread(r, "$name-${counter.getAndIncrement()}")
        // 守护线程：不阻止 JVM 退出
        thread.isDaemon = true
        return thread
    }
}
