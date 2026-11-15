package com.enderlaserquarry.content.demo;

import com.enderio.enderio.api.EnderIORegistries;
import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.content.conduits.ConduitBlockItem;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.content.conduits.type.fluid.FluidConduitConnectionConfig;
import com.enderio.enderio.init.EIOBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.UUID;

/** The /elq demo's plumbing: Ender IO conduits and tanks placed like a player would, plus signs that explain them. */
public final class DemoWiring {
    private DemoWiring() {
    }

    // like a player placing it: connects to neighbours with the default config; into the bundle if one is there
    static void conduit(ServerLevel level, BlockPos pos, ResourceKey<Conduit<?, ?>> key) {
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "demo_builder"));
        if (level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity bundle) {
            bundle.addConduit(holder(level, key), Direction.UP, player);
            return;
        }
        ItemStack stack = ConduitBlockItem.getStackFor(holder(level, key), 1);
        if (stack.getItem() instanceof BlockItem item) {
            item.place(new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(pos.getCenter(), Direction.DOWN, pos, false)));
        }
    }

    static void conduitLine(ServerLevel level, BlockPos from, Direction direction, int length, ResourceKey<Conduit<?, ?>> key) {
        for (int i = 0; i < length; i++) {
            conduit(level, from.relative(direction, i), key);
        }
    }

    private static Holder<Conduit<?, ?>> holder(ServerLevel level, ResourceKey<Conduit<?, ?>> key) {
        return level.registryAccess().registryOrThrow(EnderIORegistries.Keys.CONDUIT).getHolderOrThrow(key);
    }

    // a new conduit connection to a block only extracts, with redstone control "never": extract = pull out of that block,
    // else only insert into it
    static void fluidSide(ServerLevel level, BlockPos pos, Direction side, boolean extract, ResourceKey<Conduit<?, ?>> key) {
        if (level.getBlockEntity(pos) instanceof ConduitBundleBlockEntity bundle) {
            Holder<Conduit<?, ?>> conduit = holder(level, key);
            if (bundle.getConnectionConfig(conduit, side) instanceof FluidConduitConnectionConfig config) {
                bundle.setConnectionConfig(conduit, side, config.withIsExtract(extract).withIsInsert(!extract)
                    .withExtractRedstoneControl(RedstoneControl.ALWAYS_ACTIVE));
            }
        }
    }

    static void tank(ServerLevel level, BlockPos pos, FluidStack fluid) {
        level.setBlockAndUpdate(pos, EIOBlocks.PRESSURIZED_FLUID_TANK.get().defaultBlockState());
        var tank = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (tank != null) {
            tank.fill(fluid, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        }
    }

    public static net.minecraft.world.level.block.Block creativePower() {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("enderio", "creative_power"));
    }

    /** A standing sign facing south (towards where the player starts), up to 4 short lines, glowing so it reads at night. */
    static void sign(ServerLevel level, BlockPos pos, String... lines) {
        sign(level, pos, false, lines);
    }

    /** headline = dark oak with glowing white text, the one sign the player should read first. */
    static void sign(ServerLevel level, BlockPos pos, boolean headline, String... lines) {
        sign(level, pos, headline, 0, lines);
    }

    /** rotation 0 faces south, 8 north. */
    static void sign(ServerLevel level, BlockPos pos, boolean headline, int rotation, String... lines) {
        level.setBlockAndUpdate(pos, (headline ? Blocks.DARK_OAK_SIGN : Blocks.OAK_SIGN).defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation));
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            SignText text = headline ? new SignText().setColor(DyeColor.WHITE).setHasGlowingText(true) : new SignText().setColor(DyeColor.BLACK);
            for (int i = 0; i < Math.min(4, lines.length); i++) {
                text = text.setMessage(i, Component.literal(lines[i]));
            }
            sign.setText(text, true);
            sign.setWaxed(true);
        }
    }
}
