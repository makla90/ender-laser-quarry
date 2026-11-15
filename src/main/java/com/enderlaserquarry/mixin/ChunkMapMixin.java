package com.enderlaserquarry.mixin;

import com.enderlaserquarry.content.asteroid.sampling.SamplingDimensions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Sampling dimensions never touch the disk: nothing is read, nothing is saved, unloading a chunk deletes it.
 * PORT: vanilla internals, recheck on every Minecraft update.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow
    @Final
    ServerLevel level;

    @Inject(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At("HEAD"), cancellable = true)
    private void elq$skipSamplingSave(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        if (SamplingDimensions.isSampling(level.dimension())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "readChunk", at = @At("HEAD"), cancellable = true)
    private void elq$skipSamplingRead(ChunkPos pos, CallbackInfoReturnable<CompletableFuture<Optional<CompoundTag>>> cir) {
        if (SamplingDimensions.isSampling(level.dimension())) {
            cir.setReturnValue(CompletableFuture.completedFuture(Optional.empty()));
        }
    }
}
