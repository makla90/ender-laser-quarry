package com.enderlaserquarry.init;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.TierUpgradeRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, EnderLaserQuarry.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, TierUpgradeRecipe.Serializer> TIER_UPGRADE = SERIALIZERS
        .register("tier_upgrade", TierUpgradeRecipe.Serializer::new);

    private ModRecipes() {
    }
}
