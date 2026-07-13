package com.einsteinium.optimization.mixin;

import com.einsteinium.optimization.config.EinsteiniumConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

@Mixin(DataTracker.class)
public abstract class DataTrackerMixin {
    
    @Shadow @Final private Entity entity;
    
    @Shadow @Final private List<DataTracker.Entry<?>> trackedValues;
    
    private boolean isOptimizedPacking = false;
    
    @Inject(method = "set(Lnet/minecraft/entity/data/TrackedData;Ljava/lang/Object;)V", at = @At("HEAD"))
    private <T> void onSet(TrackedData<T> data, T value, CallbackInfo ci) {
        if (!EinsteiniumConfig.sync.incremental) {
            return;
        }
        
        if (entity != null && !entity.isClient()) {
            int dataId = data.getId();
            for (DataTracker.Entry<?> entry : trackedValues) {
                if (entry.id == dataId) {
                    entry.setDirty(true);
                    break;
                }
            }
        }
    }
    
    @Inject(method = "packDirty", at = @At("HEAD"), cancellable = true)
    private void onPackDirty(CallbackInfo ci) {
        if (!EinsteiniumConfig.sync.incremental || isOptimizedPacking) {
            return;
        }
        
        isOptimizedPacking = true;
        
        try {
            List<DataTracker.Entry<?>> dirtyEntries = trackedValues.stream()
                .filter(DataTracker.Entry::isDirty)
                .toList();
            
            if (dirtyEntries.isEmpty()) {
                ci.cancel();
                return;
            }
            
            for (DataTracker.Entry<?> entry : dirtyEntries) {
                entry.setDirty(false);
            }
        } finally {
            isOptimizedPacking = false;
        }
    }
    
    @Inject(method = "copyDirty", at = @At("HEAD"), cancellable = true)
    private void onCopyDirty(CallbackInfo ci) {
        if (!EinsteiniumConfig.sync.incremental) {
            return;
        }
    }
    
    @Inject(method = "clearDirty", at = @At("HEAD"), cancellable = true)
    private void onClearDirty(CallbackInfo ci) {
        if (!EinsteiniumConfig.sync.incremental) {
            return;
        }
    }
    
    @Inject(method = "markDirty", at = @At("HEAD"))
    private void onMarkDirty(TrackedData<?> data, CallbackInfo ci) {
        if (!EinsteiniumConfig.sync.incremental) {
            return;
        }
        
        if (entity != null && !entity.isClient()) {
            for (DataTracker.Entry<?> entry : trackedValues) {
                if (entry.id == data.getId()) {
                    entry.setDirty(true);
                    break;
                }
            }
        }
    }
}