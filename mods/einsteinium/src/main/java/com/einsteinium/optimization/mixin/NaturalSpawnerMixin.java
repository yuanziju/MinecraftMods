package com.einsteinium.optimization.mixin;

import com.einsteinium.optimization.EinsteiniumMod;
import com.einsteinium.optimization.spawn.DensityTracker;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(net.minecraft.world.spawner.NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
    @Inject(method = "spawnForChunk", at = @At("HEAD"), cancellable = true)
    private static void onSpawnForChunk(ServerWorld world, Chunk chunk, boolean spawnMonsters, boolean spawnAnimals, boolean spawnWaterCreatures, CallbackInfoReturnable<Integer> cir) {
        BlockPos pos = chunk.getPos().getStartPos();
        
        if (!EinsteiniumMod.spawnOptimizer.canSpawnAt(pos, EntityType.PIG)) {
            cir.setReturnValue(0);
        }
    }
}