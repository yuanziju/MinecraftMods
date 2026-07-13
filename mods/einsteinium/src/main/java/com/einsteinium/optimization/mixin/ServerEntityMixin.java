package com.einsteinium.optimization.mixin;

import com.einsteinium.optimization.EinsteiniumMod;
import com.einsteinium.optimization.config.EinsteiniumConfig;
import com.einsteinium.optimization.network.EntitySyncSnapshot;
import com.einsteinium.optimization.network.SyncMask;
import com.einsteinium.optimization.network.SyncTier;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ServerEntity.class)
public abstract class ServerEntityMixin {
    
    @Shadow @Final private Entity entity;
    
    @Shadow @Final private Set<ServerPlayerEntity> trackedPlayers;
    
    @Shadow private int ticksSinceLastSync;
    
    @Inject(method = "sendChanges", at = @At("HEAD"), cancellable = true)
    private void onSendChanges(CallbackInfo ci) {
        if (!EinsteiniumConfig.sync.incremental) {
            return;
        }
        
        ci.cancel();
        
        Entity syncEntity = this.entity;
        if (syncEntity.isRemoved()) {
            return;
        }
        
        double tier1 = EinsteiniumConfig.sync.distanceTier1;
        double tier2 = EinsteiniumConfig.sync.distanceTier2;
        
        for (ServerPlayerEntity player : trackedPlayers) {
            if (!player.isDisconnected()) {
                double distance = player.squaredDistanceTo(syncEntity);
                SyncTier syncTier;
                
                if (distance <= tier1 * tier1) {
                    syncTier = SyncTier.FULL;
                } else if (distance <= tier2 * tier2) {
                    syncTier = SyncTier.PARTIAL;
                } else {
                    if (ticksSinceLastSync % 2 != 0) {
                        continue;
                    }
                    syncTier = SyncTier.MINIMAL;
                }
                
                SyncMask mask = new SyncMask();
                applyTierMask(mask, syncTier);
                
                long version = EinsteiniumMod.getNetworkOptimizer().getEntityVersion(syncEntity.getId());
                
                EntitySyncSnapshot snapshot = EntitySyncSnapshot.fromEntity(
                    syncEntity, mask, syncTier, version + 1
                );
                
                EinsteiniumMod.getBatchPacketBuilder().addEntityUpdate(syncEntity, snapshot);
            }
        }
        
        ticksSinceLastSync++;
    }
    
    private void applyTierMask(SyncMask mask, SyncTier tier) {
        mask.clear();
        
        switch (tier) {
            case FULL -> {
                mask.markPosition();
                mask.markRotation();
                mask.markVelocity();
                mask.markOnGround();
                mask.markDataTracker();
                mask.markMetadata();
            }
            case PARTIAL -> {
                mask.markPosition();
                mask.markRotation();
                mask.markOnGround();
            }
            case MINIMAL -> {
                mask.markPosition();
            }
        }
    }
    
    @Inject(method = "addPlayer", at = @At("TAIL"))
    private void onAddPlayer(ServerPlayerEntity player, CallbackInfo ci) {
        EinsteiniumMod.getNetworkOptimizer().onEntityTick(entity);
    }
    
    @Inject(method = "removePlayer", at = @At("TAIL"))
    private void onRemovePlayer(ServerPlayerEntity player, CallbackInfo ci) {
        EinsteiniumMod.getBatchPacketBuilder().onPlayerDisconnect(player);
    }
}