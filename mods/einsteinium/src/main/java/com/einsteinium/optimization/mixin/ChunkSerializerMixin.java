package com.einsteinium.optimization.mixin;

import com.einsteinium.optimization.EinsteiniumMod;
import com.einsteinium.optimization.persistence.AsyncSaveQueue;
import com.einsteinium.optimization.persistence.PersistenceOptimizer;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.world.chunk.ChunkSerializer.class)
public abstract class ChunkSerializerMixin {
    private static final PersistenceOptimizer optimizer = new PersistenceOptimizer();

    @Inject(method = "serialize", at = @At("HEAD"), cancellable = true)
    private static void onSerializeHead(Chunk chunk, CallbackInfoReturnable<NbtCompound> cir) {
        for (Entity entity : chunk.getEntities()) {
            optimizer.captureSnapshot(entity);
        }
    }

    @Inject(method = "serialize", at = @At("RETURN"))
    private static void onSerializeReturn(Chunk chunk, CallbackInfoReturnable<NbtCompound> cir) {
        NbtCompound result = cir.getReturnValue();
        if (result != null) {
            NbtList entityList = result.getList("Entities", 10);
            if (entityList != null && !entityList.isEmpty()) {
                for (int i = 0; i < entityList.size(); i++) {
                    NbtCompound entityTag = entityList.getCompound(i);
                    int entityId = entityTag.getInt("Id");

                    for (Entity entity : chunk.getEntities()) {
                        if (entity.getId() == entityId) {
                            NbtCompound previous = optimizer.getPreviousSnapshot(entity);
                            if (previous != null) {
                                NbtCompound diff = optimizer.diffNBT(entityTag, previous);

                                if (diff.getKeys().isEmpty()) {
                                    entityList.remove(i);
                                    i--;
                                    continue;
                                }

                                if (EinsteiniumMod.config.save.compress) {
                                    byte[] compressed = optimizer.compressNBT(diff);
                                    entityTag.putByteArray("EinsteiniumCompressed", compressed);
                                } else {
                                    entityList.set(i, diff);
                                }
                            }

                            optimizer.updateSnapshot(entity, entityTag);
                            break;
                        }
                    }
                }
            }
        }

        for (Entity entity : chunk.getEntities()) {
            AsyncSaveQueue.add(entity);
        }
    }

    @Inject(method = "readEntityTag", at = @At("HEAD"), cancellable = true)
    private static void onReadEntityTag(Chunk chunk, NbtCompound entityTag, CallbackInfoReturnable<Entity> cir) {
        if (entityTag.contains("EinsteiniumCompressed")) {
            byte[] compressed = entityTag.getByteArray("EinsteiniumCompressed");
            NbtCompound decompressed = optimizer.decompressNBT(compressed);

            for (String key : decompressed.getKeys()) {
                entityTag.put(key, decompressed.get(key));
            }

            entityTag.remove("EinsteiniumCompressed");
        }
    }
}