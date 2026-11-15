package com.enderlaserquarry.mixin;

import com.enderlaserquarry.content.asteroid.sampling.SamplingDimensions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Region files of the sampling dimensions (chunks, entities, POI) are never opened: reading one would create it.
 * PORT: vanilla internals, recheck on every Minecraft update.
 */
@Mixin(RegionFileStorage.class)
public abstract class RegionFileStorageMixin {
    @Shadow
    @Final
    private RegionStorageInfo info;

    @Inject(method = "read", at = @At("HEAD"), cancellable = true)
    private void elq$skipSamplingRead(ChunkPos pos, CallbackInfoReturnable<CompoundTag> cir) {
        if (SamplingDimensions.isSampling(info.dimension())) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "scanChunk", at = @At("HEAD"), cancellable = true)
    private void elq$skipSamplingScan(ChunkPos pos, StreamTagVisitor visitor, CallbackInfo ci) {
        if (SamplingDimensions.isSampling(info.dimension())) {
            ci.cancel();
        }
    }

    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void elq$skipSamplingWrite(ChunkPos pos, CompoundTag data, CallbackInfo ci) {
        if (SamplingDimensions.isSampling(info.dimension())) {
            ci.cancel();
        }
    }
}
