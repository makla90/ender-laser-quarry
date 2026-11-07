package com.enderlaserquarry.mixin;

import com.enderlaserquarry.content.asteroid.sampling.SamplingDimensions;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * C2ME reads and writes region files on its own thread, past the vanilla storage: keep the sampling dimensions off disk there too.
 */
@Pseudo
@Mixin(targets = "com.ishland.c2me.rewrites.chunkio.common.C2MEStorageThread", remap = false)
public abstract class C2meStorageThreadMixin {
    @Shadow
    public abstract RegionStorageInfo getStorageKey();

    @Inject(method = "getChunkData", at = @At("HEAD"), cancellable = true, require = 0)
    private void elq$skipSamplingRead(CallbackInfoReturnable<CompletableFuture<?>> cir) {
        if (SamplingDimensions.isSampling(getStorageKey().dimension())) {
            cir.setReturnValue(CompletableFuture.completedFuture(null));
        }
    }

    @Inject(method = {"setChunkData*", "setChunkDataRaw"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void elq$skipSamplingWrite(CallbackInfoReturnable<CompletableFuture<?>> cir) {
        if (SamplingDimensions.isSampling(getStorageKey().dimension())) {
            cir.setReturnValue(CompletableFuture.completedFuture(null));
        }
    }
}
