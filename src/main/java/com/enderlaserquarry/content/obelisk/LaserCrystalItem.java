package com.enderlaserquarry.content.obelisk;

import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.Tiered;
import com.enderlaserquarry.content.controller.MiningRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import com.enderlaserquarry.EnderLaserQuarry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import com.enderlaserquarry.config.QuarryConfig;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * Laser crystal, goes on the obelisk. Takes Silk Touch or Fortune, Unbreaking and Mending. Wears while mining and stops
 * at 1 durability, never breaks.
 */
public class LaserCrystalItem extends Item implements Tiered {
    private static final int ENCHANTMENT_VALUE = 15;
    public static final int PULSATING_DURABILITY = 3_000;
    public static final int LASER_DURABILITY = 7_500;
    public static final int VOID_DURABILITY = 26_000;

    private final QuarryTier tier;

    public LaserCrystalItem(QuarryTier tier, Properties properties) {
        // anvil repair material, one step up the Ender IO crystal ladder per tier
        super(properties.enchantable(ENCHANTMENT_VALUE)
            .repairable(TagKey.create(Registries.ITEM, EnderLaserQuarry.rl("crystal_repair/" + tier.id()))));
        this.tier = tier;
    }

    @Override
    public QuarryTier getTier() {
        return tier;
    }

    // Pulsating only runs Basic controllers, the Laser and Void crystals run any
    public boolean worksWith(QuarryTier controller) {
        return tier != QuarryTier.BASIC || controller == QuarryTier.BASIC;
    }

    // blocks per durability point (config per crystal)
    public int blocksPerPoint() {
        return switch (tier) {
            case BASIC -> QuarryConfig.PULSATING_BLOCKS_PER_POINT.get();
            case ENHANCED -> QuarryConfig.LASER_BLOCKS_PER_POINT.get();
            case ENDER -> QuarryConfig.VOID_BLOCKS_PER_POINT.get();
        };
    }

    // like an elytra: at 1 durability it stops working
    public static boolean isBroken(ItemStack stack) {
        return stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    public static boolean isWorn(ItemStack stack) {
        return stack.isDamageableItem()
            && (stack.getMaxDamage() - stack.getDamageValue()) * 100L <= (long) stack.getMaxDamage() * QuarryConfig.CRYSTAL_WORN_PERCENT.get();
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // enchanting table (Mending is treasure, anvil only)
    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return isLaserEnchantment(enchantment) && !enchantment.is(Enchantments.MENDING);
    }

    // anvil
    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return stack.getCount() == 1 && isLaserEnchantment(enchantment);
    }

    private static boolean isLaserEnchantment(Holder<Enchantment> enchantment) {
        return enchantment.is(Enchantments.SILK_TOUCH) || enchantment.is(Enchantments.FORTUNE)
            || enchantment.is(Enchantments.UNBREAKING) || enchantment.is(Enchantments.MENDING);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.laser_crystal").withStyle(ChatFormatting.GRAY));
        if (tier == QuarryTier.BASIC) {
            tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.pulsating_laser_crystal.tier").withStyle(ChatFormatting.YELLOW));
        }
        addEnchantHint(stack, tooltip);
        addWearHint(stack, tooltip);
    }

    protected static void addWearHint(ItemStack stack, Consumer<Component> tooltip) {
        if (isBroken(stack)) {
            tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.laser_crystal.broken").withStyle(ChatFormatting.RED));
        } else if (isWorn(stack)) {
            tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.laser_crystal.worn").withStyle(ChatFormatting.YELLOW));
        }
        if (stack.getItem() instanceof LaserCrystalItem crystal) {
            tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.laser_crystal.wear", crystal.blocksPerPoint())
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    protected static void addEnchantHint(ItemStack stack, Consumer<Component> tooltip) {
        if (stack.getEnchantments().isEmpty()) {
            tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.laser_crystal.enchantable").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.accept(Component.translatable("tooltip.ender_laser_quarry.laser_crystal.enchant_cost",
                formatMultiplier(MiningRules.enchantCostScale(stack.getEnchantments()))).withStyle(ChatFormatting.GOLD));
        }
    }

    public static String formatMultiplier(double multiplier) {
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(multiplier);
    }
}
