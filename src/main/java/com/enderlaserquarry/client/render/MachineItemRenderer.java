package com.enderlaserquarry.client.render;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.dish.SatelliteDishBlock;
import com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity;
import com.enderlaserquarry.init.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * 3D items of the dishes, the ground radar and the launch pad controller: the block with its dish or antenna on top.
 * Item definition type {@code ender_laser_quarry:machine_item} with the block id.
 */
@EventBusSubscriber(modid = EnderLaserQuarry.MOD_ID, value = Dist.CLIENT)
public final class MachineItemRenderer implements NoDataSpecialModelRenderer {
    @Nullable
    private final SatelliteDishBlock dish;
    @Nullable
    private final SatelliteDishItemRenderer dishRenderer;
    @Nullable
    private final GroundRadarItemRenderer radarRenderer;

    private MachineItemRenderer(SpecialModelRenderer.BakingContext context, Block block) {
        if (block instanceof SatelliteDishBlock dishBlock) {
            dish = dishBlock;
            dishRenderer = new SatelliteDishItemRenderer(context.entityModelSet());
            radarRenderer = null;
        } else {
            dish = null;
            dishRenderer = null;
            radarRenderer = new GroundRadarItemRenderer(context.entityModelSet(), () -> block, 1, 0.48f);
        }
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean hasFoil, int outlineColor) {
        DeferredBuffers buffers = new DeferredBuffers();
        if (dishRenderer != null && dish != null) {
            dishRenderer.render(dish, poseStack, buffers, light, overlay);
        } else if (radarRenderer != null) {
            radarRenderer.render(poseStack, buffers, light, overlay);
        }
        buffers.submit(collector);
    }

    // the scaled block plus what stands on it stays inside the unit cube
    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (int i = 0; i < 8; i++) {
            output.accept(new Vector3f(i & 1, (i >> 1) & 1, (i >> 2) & 1));
        }
    }

    public record Unbaked(Identifier block) implements NoDataSpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("block").forGetter(Unbaked::block)
        ).apply(i, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public @Nullable SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
            Block found = BuiltInRegistries.BLOCK.getValue(block);
            return new MachineItemRenderer(context, found);
        }
    }

    @SubscribeEvent
    public static void register(RegisterSpecialModelRendererEvent event) {
        event.register(EnderLaserQuarry.rl("machine_item"), Unbaked.MAP_CODEC);
    }
}
