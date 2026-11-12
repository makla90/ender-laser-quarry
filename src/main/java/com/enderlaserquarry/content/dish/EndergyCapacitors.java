package com.enderlaserquarry.content.dish;

import com.enderio.enderio.foundation.state.MachineState;
import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.QuarryStates;
import com.enderlaserquarry.content.QuarryTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Capacitors from the Ender IO Endergy addon (optional): they only work in Ender dishes, and the Totemic one not at all yet.
 */
@EventBusSubscriber(modid = EnderLaserQuarry.MOD_ID)
public final class EndergyCapacitors {
    public static final String MOD_ID = "enderio_endergy";
    private static final String TOTEMIC = "totemic_capacitor";

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isEndergy(ItemStack stack) {
        return !stack.isEmpty() && MOD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
    }

    public static boolean fits(QuarryTier dishTier, ItemStack stack) {
        return dishTier == QuarryTier.ENDER || !isEndergy(stack);
    }

    // the Totemic one wears as it's used: not supported yet, in dishes or the controller
    public static boolean isUnsupported(ItemStack stack) {
        return isEndergy(stack) && TOTEMIC.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
    }

    // why this capacitor won't run a dish of that tier, null if it does
    public static @Nullable MachineState dishProblem(QuarryTier dishTier, ItemStack stack) {
        if (isUnsupported(stack)) {
            return QuarryStates.CAPACITOR_UNSUPPORTED;
        }
        return fits(dishTier, stack) ? null : QuarryStates.ENDERGY_NEEDS_ENDER_DISH;
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (isUnsupported(event.getItemStack())) {
            event.getToolTip().add(Component.translatable("tooltip.ender_laser_quarry.capacitor_unsupported").withStyle(ChatFormatting.RED));
        }
    }

    // Dish strip colour index: 4 + position in this list, 0 = not one of these
    private static final List<String> STRIP_ORDER = List.of("grainy_capacitor", "vivid_capacitor", "crystalline_capacitor",
        "melodic_capacitor", "stellar_capacitor", TOTEMIC);
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
