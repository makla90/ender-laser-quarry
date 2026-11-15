package com.enderlaserquarry.content;

import com.enderlaserquarry.init.ModRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * Shaped recipe for tier upgrades, keeps the contents (capacitor, energy, items...) of the lower tier.
 */
public class TierUpgradeRecipe extends ShapedRecipe {
    private final ShapedRecipePattern pattern;
    private final ItemStack result;

    public TierUpgradeRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result,
                             boolean showNotification) {
        super(group, category, pattern, result, showNotification);
        this.pattern = pattern;
        this.result = result;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack upgraded = super.assemble(input, registries);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (Tiered.of(stack) != null) {
                upgraded.applyComponents(stack.getComponentsPatch());
                // an upgraded crystal comes out fully repaired
                upgraded.remove(DataComponents.DAMAGE);
                break;
            }
        }
        return upgraded;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.TIER_UPGRADE.get();
    }

    public static class Serializer implements RecipeSerializer<TierUpgradeRecipe> {
        private static final MapCodec<TierUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("group", "").forGetter(ShapedRecipe::getGroup),
            CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(ShapedRecipe::category),
            ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
            Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(ShapedRecipe::showNotification)
        ).apply(instance, TierUpgradeRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, TierUpgradeRecipe> STREAM_CODEC = StreamCodec.of(
            (buf, recipe) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, recipe.getGroup());
                CraftingBookCategory.STREAM_CODEC.encode(buf, recipe.category());
                ShapedRecipePattern.STREAM_CODEC.encode(buf, recipe.pattern);
                ItemStack.STREAM_CODEC.encode(buf, recipe.result);
                ByteBufCodecs.BOOL.encode(buf, recipe.showNotification());
            },
            buf -> new TierUpgradeRecipe(ByteBufCodecs.STRING_UTF8.decode(buf), CraftingBookCategory.STREAM_CODEC.decode(buf),
                ShapedRecipePattern.STREAM_CODEC.decode(buf), ItemStack.STREAM_CODEC.decode(buf), ByteBufCodecs.BOOL.decode(buf)));

        @Override
        public MapCodec<TierUpgradeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, TierUpgradeRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
