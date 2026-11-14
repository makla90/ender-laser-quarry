package com.enderlaserquarry.mixin;

import com.enderio.enderio.client.foundation.widgets.ioconfig.IOConfigOverlay;
import com.enderio.enderio.client.foundation.widgets.ioconfig.IOConfigSceneCamera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Comparator;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * WORKAROUND: face picking in Ender IO's IO view for multiblocks with many ports. Ender IO's mouse ray is only a few
 * blocks long and the port nearest the scene middle wins instead of the front-most one.
 * PORT: targets Ender IO internals, recheck on every Ender IO update.
 */
@Mixin(value = IOConfigOverlay.class, remap = false)
public abstract class IOConfigOverlayMixin {
    @Shadow
    @Final
    private IOConfigSceneCamera camera;

    @ModifyConstant(method = "raycast", constant = @Constant(floatValue = -1.0f), require = 0)
    private float elq$rayStart(float start) {
        return -256f;
    }

    @ModifyConstant(method = "raycast", constant = @Constant(floatValue = 3.5f), require = 0)
    private float elq$rayEnd(float end) {
        return 256f;
    }

    // the ray runs along +direction from the viewer's side, so the smallest distance along it is in front
    @Redirect(method = "extractWidgetRenderState", require = 0, at = @At(value = "INVOKE",
        target = "Ljava/util/Comparator;comparingDouble(Ljava/util/function/ToDoubleFunction;)Ljava/util/Comparator;"))
    private Comparator<Map.Entry<BlockHitResult, BlockPos>> elq$frontMost(ToDoubleFunction<?> byDistanceToMiddle) {
        Vector3f dir = new Matrix4f(camera.viewMatrix()).invert().transformDirection(new Vector3f(0, 0, 1)).normalize();
        return Comparator.comparingDouble(entry -> {
            Vec3 hit = entry.getKey().getLocation();
            return hit.x * dir.x + hit.y * dir.y + hit.z * dir.z;
        });
    }
}
