package com.enderlaserquarry.content.dish;

import com.enderlaserquarry.content.QuarryTier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.List;

/**
 * Capacitors from the Ender IO Endergy addon (optional): they only work in Ender dishes.
 */
public final class EndergyCapacitors {
    public static final String MOD_ID = "enderio_endergy";

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isEndergy(ItemStack stack) {
        return !stack.isEmpty() && MOD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
    }

    public static boolean fits(QuarryTier dishTier, ItemStack stack) {
        return dishTier == QuarryTier.ENDER || !isEndergy(stack);
    }

    // Dish strip colour index: 4 + position in this list, 0 = not one of these
    private static final List<String> STRIP_ORDER = List.of("grainy_capacitor", "vivid_capacitor", "crystalline_capacitor",
        "melodic_capacitor", "stellar_capacitor", "totemic_capacitor");
    public static final int FIRST_STRIP_INDEX = 4;

    public static int stripIndex(ItemStack stack) {
        if (!isEndergy(stack)) {
            return 0;
        }
        int i = STRIP_ORDER.indexOf(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
        return i < 0 ? 0 : FIRST_STRIP_INDEX + i;
    }

    private EndergyCapacitors() {
    }
}
