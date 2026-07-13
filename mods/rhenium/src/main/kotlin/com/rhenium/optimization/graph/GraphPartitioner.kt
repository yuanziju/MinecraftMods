package com.rhenium.optimization.graph

import net.minecraft.core.BlockPos

/**
 * 图划分器。
 *
 * 使用并查集（Union-Find）算法将相邻的红石元件位置分组，
 * 形成独立的连通分量（即独立的红石图）。
 */
class GraphPartitioner {

    /**
     * 将位置列表划分为多个连通分量。
     *
     * @param positions 红石元件的方块位置列表
     * @return 连通分量列表，每个分量是一组相连的位置
     */
    fun partition(positions: List<BlockPos>): List<Set<BlockPos>> {
        if (positions.isEmpty()) return emptyList()

        val unionFind = UnionFind(positions)

        // 遍历所有位置，将相邻位置合并
        for (pos in positions) {
            for (neighbor in getNeighbors(pos)) {
                if (positions.contains(neighbor)) {
                    unionFind.union(pos, neighbor)
                }
            }
        }

        // 收集连通分量
        val groups = mutableMapOf<BlockPos, MutableSet<BlockPos>>()
        for (pos in positions) {
            val root = unionFind.find(pos)
            groups.getOrPut(root) { mutableSetOf() }.add(pos)
        }

        return groups.values.toList()
    }

    /**
     * 获取一个位置的所有相邻位置（曼哈顿距离为 1 的 6 个方向）。
     */
    private fun getNeighbors(pos: BlockPos): List<BlockPos> {
        return listOf(
            pos.north(),
            pos.south(),
            pos.east(),
            pos.west(),
            pos.above(),
            pos.below()
        )
    }

    /**
     * 并查集数据结构。
     *
     * 支持路径压缩和按秩合并，时间复杂度接近 O(α(n))。
     *
     * @param positions 初始位置集合
     */
    private class UnionFind(positions: List<BlockPos>) {
        /** 父节点映射：位置 → 根位置 */
        private val parent: MutableMap<BlockPos, BlockPos> = mutableMapOf()

        /** 秩（树高度）映射 */
        private val rank: MutableMap<BlockPos, Int> = mutableMapOf()

        init {
            for (pos in positions) {
                parent[pos] = pos
                rank[pos] = 0
            }
        }

        /**
         * 查找根节点（带路径压缩）。
         */
        fun find(pos: BlockPos): BlockPos {
            val p = parent[pos]
                ?: return pos
            if (p != pos) {
                parent[pos] = find(p)
            }
            return parent[pos]!!
        }

        /**
         * 合并两个集合（按秩合并）。
         */
        fun union(a: BlockPos, b: BlockPos) {
            val rootA = find(a)
            val rootB = find(b)
            if (rootA == rootB) return

            val rankA = rank[rootA] ?: 0
            val rankB = rank[rootB] ?: 0

            if (rankA < rankB) {
                parent[rootA] = rootB
            } else if (rankA > rankB) {
                parent[rootB] = rootA
            } else {
                parent[rootB] = rootA
                rank[rootA] = rankA + 1
            }
        }

        /**
         * 检查两个位置是否在同一个集合中。
         */
        fun connected(a: BlockPos, b: BlockPos): Boolean {
            return find(a) == find(b)
        }
    }
}
