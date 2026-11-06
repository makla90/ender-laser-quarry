package com.enderlaserquarry.content.linker;

import com.enderlaserquarry.content.PlayerMessages;
import com.enderlaserquarry.content.MultiblockFrames;
import com.enderlaserquarry.content.controller.QuarryControllerBlockEntity;
import com.enderlaserquarry.content.dish.SatelliteDishBlockEntity;
import com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity;
import com.enderlaserquarry.content.obelisk.CrystalObeliskBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Links dish <-> obelisk and quarry controller <-> launch pad controller, either order. Frame blocks count as their core. Sneak + use in the
 * air clears it.
 */
public class LaserLinkerItem extends Item {
    private static final String SOURCE = "LinkSource";

    public LaserLinkerItem(Properties properties) {
        super(properties);
    }

    // onItemUseFirst, otherwise the machines open their GUI first
    @Override
    public InteractionResult onItemUseFirst(ItemStack itemStack, UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockEntity clicked = level.getBlockEntity(context.getClickedPos());
        if (!isLinkable(clicked)) {
            clicked = MultiblockFrames.findCore(level, context.getClickedPos());
        }

        if (!isLinkable(clicked)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide() || player == null) {
            return InteractionResult.SUCCESS;
        }

        Optional<GlobalPos> source = getSource(stack);
        if (source.isEmpty() || source.get().pos().equals(clicked.getBlockPos())) {
            setSource(stack, GlobalPos.of(level.dimension(), clicked.getBlockPos()));
            PlayerMessages.show(player, Component.translatable("message.ender_laser_quarry.linker.stored." + storedKey(clicked),
                clicked.getBlockState().getBlock().getName()));
            return InteractionResult.SUCCESS;
        }

        if (!source.get().dimension().equals(level.dimension())) {
            fail(player, "other_dimension");
            return InteractionResult.SUCCESS;
        }

        BlockEntity stored = level.isLoaded(source.get().pos()) ? level.getBlockEntity(source.get().pos()) : null;
        if (!isLinkable(stored)) {
            clearSource(stack);
            fail(player, "source_missing");
            return InteractionResult.SUCCESS;
        }

        Component result = link(stored, clicked);
        if (result == null) {
            fail(player, "invalid_pair");
        } else {
            clearSource(stack);
            PlayerMessages.show(player, result);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && getSource(stack).isPresent()) {
            if (!level.isClientSide()) {
                clearSource(stack);
                PlayerMessages.show(player, Component.translatable("message.ender_laser_quarry.linker.cleared"));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return super.use(level, player, hand);
    }

    @Nullable
    public static Component link(BlockEntity a, BlockEntity b) {
        Component result = linkOrdered(a, b);
        return result != null ? result : linkOrdered(b, a);
    }

    @Nullable
    private static Component linkOrdered(BlockEntity a, BlockEntity b) {
        if (a instanceof SatelliteDishBlockEntity dish && b instanceof CrystalObeliskBlockEntity obelisk) {
            Component error = obelisk.addDish(dish);
            if (error != null) {
                return error.copy().withStyle(ChatFormatting.RED);
            }
            dish.setObeliskPos(obelisk.getBlockPos());
            return Component.translatable("message.ender_laser_quarry.linker.dish_obelisk").withStyle(ChatFormatting.GREEN);
        }

        // works before the satellite is up too
        if (a instanceof QuarryControllerBlockEntity controller && b instanceof LaunchPadBlockEntity pad) {
            Component error = controller.linkPad(pad);
            if (error != null) {
                return error.copy().withStyle(ChatFormatting.RED);
            }
            return Component.translatable("message.ender_laser_quarry.linker.controller_pad").withStyle(ChatFormatting.GREEN);
        }

        // hints for links that don't exist
        if (a instanceof CrystalObeliskBlockEntity && b instanceof LaunchPadBlockEntity) {
            return Component.translatable("message.ender_laser_quarry.linker.obelisk_pad").withStyle(ChatFormatting.RED);
        }

        if (a instanceof SatelliteDishBlockEntity && b instanceof LaunchPadBlockEntity) {
            return Component.translatable("message.ender_laser_quarry.linker.dish_needs_obelisk").withStyle(ChatFormatting.RED);
        }

        if (a instanceof SatelliteDishBlockEntity && b instanceof QuarryControllerBlockEntity) {
            return Component.translatable("message.ender_laser_quarry.linker.dish_controller").withStyle(ChatFormatting.RED);
        }

        if (a instanceof CrystalObeliskBlockEntity && b instanceof QuarryControllerBlockEntity) {
            return Component.translatable("message.ender_laser_quarry.linker.controller_on_ring").withStyle(ChatFormatting.RED);
        }

        return null;
    }

    // lang key for "now click X"
    private static String storedKey(BlockEntity be) {
        if (be instanceof SatelliteDishBlockEntity) {
            return "dish";
        }
        if (be instanceof CrystalObeliskBlockEntity) {
            return "obelisk";
        }
        return be instanceof LaunchPadBlockEntity ? "launch_pad" : "controller";
    }

    private static boolean isLinkable(BlockEntity be) {
        return be instanceof SatelliteDishBlockEntity || be instanceof CrystalObeliskBlockEntity || be instanceof LaunchPadBlockEntity
            || be instanceof QuarryControllerBlockEntity;
    }

    private static void fail(Player player, String key) {
        PlayerMessages.show(player, Component.translatable("message.ender_laser_quarry.linker." + key)
            .withStyle(ChatFormatting.RED));
    }

    // region Stored source

    private static Optional<GlobalPos> getSource(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(SOURCE)) {
            return Optional.empty();
        }
        return GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get(SOURCE)).result();
    }

    private static void setSource(ItemStack stack, GlobalPos pos) {
        GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, pos).result()
            .ifPresent(encoded -> CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(SOURCE, encoded)));
    }

    private static void clearSource(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(SOURCE));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getSource(stack).isPresent();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        getSource(stack).ifPresentOrElse(source -> {
            BlockPos pos = source.pos();
            tooltip.add(Component.translatable("tooltip.ender_laser_quarry.linker.source", pos.getX(), pos.getY(), pos.getZ())
                .withStyle(ChatFormatting.AQUA));
        }, () -> tooltip.add(Component.translatable("tooltip.ender_laser_quarry.linker.usage").withStyle(ChatFormatting.GRAY)));
    }

    // endregion
}
