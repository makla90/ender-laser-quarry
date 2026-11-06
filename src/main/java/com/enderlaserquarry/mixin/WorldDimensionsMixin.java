package com.enderlaserquarry.mixin;

import com.enderlaserquarry.content.asteroid.sampling.SamplingDimensions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.stream.Stream;

/**
 * The sampling dimensions don't count as custom world settings, or every world would open with the
 * "experimental settings" warning.
 */
@Mixin(WorldDimensions.class)
public abstract class WorldDimensionsMixin {
    @Shadow
    @Final
    private Map<ResourceKey<LevelStem>, LevelStem> dimensions;

    @Inject(method = "checkStability", at = @At("HEAD"), cancellable = true)
    private static void elq$samplingIsStable(ResourceKey<LevelStem> key, LevelStem stem, CallbackInfoReturnable<Lifecycle> cir) {
        if (SamplingDimensions.isSamplingStem(key)) {
            cir.setReturnValue(Lifecycle.stable());
        }
    }

    // bake calls a world stable only with exactly the 3 vanilla dimensions: leave ours out of that count
    @ModifyExpressionValue(method = "bake", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
    private int elq$countWithoutSampling(int size, Registry<LevelStem> stems) {
        long ours = Stream.concat(stems.registryKeySet().stream(), dimensions.keySet().stream())
            .distinct()
            .filter(SamplingDimensions::isSamplingStem)
            .count();
        return size - (int) ours;
    }
}
