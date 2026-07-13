package com.rhenium.optimization.threading

import com.rhenium.optimization.graph.RedstoneGraph
import com.rhenium.optimization.optimization.UpdateResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 中图任务队列 —— 管理中等规模红石图（20-100 节点）的计算任务。
 *
 * 中图数量可能较多，使用任务队列配合多线程并行处理可显著提升吞吐量。
 * 本队列基于 [ConcurrentLinkedQueue] 实现无锁的任务入队/出队，
 * 并支持 work-stealing（工作窃取）模式：空闲线程从共享队列中窃取待处理任务，
 * 避免某些线程空闲而其他线程过载的情况。
 *
 * @param executor 线程池（由 [GraphThreadPool] 创建的固定大小线程池），用于执行窃取到的任务
 */
class MediumGraphQueue(private val executor: ExecutorService) {

    /**
     * 无锁任务队列，存放待处理的中图任务。
     * 所有工作线程共享此队列，实现 work-stealing。
     */
    private val taskQueue: ConcurrentLinkedQueue<MediumTask> = ConcurrentLinkedQueue()

    /**
     * 提交中图计算任务到队列。
     *
     * 将任务包装为 [MediumTask] 后加入共享队列，并立即向线程池提交一个工作窃取处理器。
     * 该处理器会循环从队列中取出任务执行，直到队列为空。
     *
     * @param graph 待计算的中等规模红石图
     * @param task 实际的计算逻辑
     * @return [Future]，可在未来获取计算结果
     */
    fun submit(graph: RedstoneGraph, task: () -> UpdateResult): Future<UpdateResult> {
        val mediumTask = MediumTask(graph, task)
        taskQueue.add(mediumTask)

        // 向线程池提交工作窃取处理器：循环处理队列中的所有任务
        // 多个 submit 调用会触发多个处理器，实现并行 work-stealing
        executor.execute {
            processQueueWorkStealing()
        }

        return mediumTask.future
    }

    /**
     * 从队列中取出下一个任务并执行。
     *
     * 手动触发单次任务处理。若队列为空，返回 null。
     * 在 work-stealing 模式下，此方法通常由 [processQueueWorkStealing] 内部循环调用，
     * 但也可单独调用以处理单个任务。
     *
     * @return 执行结果；null 表示队列为空或任务已被其他线程处理
     */
    fun processNext(): UpdateResult? {
        val task = taskQueue.poll() ?: return null
        return task.execute()
    }

    /**
     * 工作窃取循环 —— 持续从共享队列中窃取并执行任务，直到队列清空。
     *
     * 这是 work-stealing 的核心实现：
     * - 当前线程处理完自己的任务后，会检查共享队列
     * - 如果队列中有待处理任务（可能是其他线程提交的），则窃取并执行
     * - 循环直到队列为空，确保所有任务都被及时处理
     *
     * [MediumTask.execute] 内部使用 [AtomicBoolean] 保证每个任务只执行一次，
     * 即使多个线程同时尝试窃取同一任务也不会重复执行。
     */
    private fun processQueueWorkStealing() {
        while (true) {
            // 从共享队列窃取一个任务
            val task = taskQueue.poll() ?: break
            task.execute()
        }
    }

    /**
     * 中图任务包装类。
     *
     * 封装红石图与计算逻辑，并通过 [CompletableFuture] 向调用方提供异步结果。
     * [executed] 标志保证任务在并发环境下只执行一次（防重复窃取）。
     *
     * @param graph 待计算的红石图
     * @param task 实际计算逻辑
     */
    private class MediumTask(
        val graph: RedstoneGraph,
        private val task: () -> UpdateResult
    ) {
        /** 异步结果载体，任务完成后通过 [complete] 或 [completeExceptionally] 设置结果 */
        val future: CompletableFuture<UpdateResult> = CompletableFuture()

        /** 执行标志，CAS 操作保证任务只执行一次 */
        private val executed: AtomicBoolean = AtomicBoolean(false)

        /**
         * 执行任务。
         *
         * 使用 CAS 保证幂等性：若任务已被其他线程执行，直接返回 null。
         * 执行成功时完成 [future]，异常时以异常完成 [future]。
         *
         * @return 执行结果；null 表示任务已被其他线程处理或执行失败
         */
        fun execute(): UpdateResult? {
            // CAS 保证只执行一次：若已执行，直接返回
            if (!executed.compareAndSet(false, true)) return null
            return try {
                val result = task()
                future.complete(result)
                result
            } catch (e: Throwable) {
                future.completeExceptionally(e)
                null
            }
        }
    }
}
