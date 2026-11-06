package com.enderlaserquarry.content;

import com.enderlaserquarry.init.ModRecipes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * Shaped recipe for tier upgrades, keeps the contents (capacitor, energy, items...) of the lower tier.
 */
public class TierUpgradeRecipe extends ShapedRecipe {
    public static final MapCodec<TierUpgradeRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Recipe.CommonInfo.MAP_CODEC.forGetter(o -> o.commonInfo),
        CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(o -> o.bookInfo),
        ShapedRecipePattern.MAP_CODEC.forGetter(o -> o.pattern),
        ItemStackTemplate.CODEC.fieldOf("result").forGetter(o -> o.result)
    ).apply(i, TierUpgradeRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TierUpgradeRecipe> STREAM_CODEC = StreamCodec.composite(
        Recipe.CommonInfo.STREAM_CODEC, o -> o.commonInfo,
        CraftingRecipe.CraftingBookInfo.STREAM_CODEC, o -> o.bookInfo,
        ShapedRecipePattern.STREAM_CODEC, o -> o.pattern,
        ItemStackTemplate.STREAM_CODEC, o -> o.result,
        TierUpgradeRecipe::new);

    public static final RecipeSerializer<TierUpgradeRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final ItemStackTemplate result;

    public TierUpgradeRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ShapedRecipePattern pattern,
                             ItemStackTemplate result) {
        super(commonInfo, bookInfo, pattern, result);
        this.result = result;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack upgraded = super.assemble(input);
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    public RecipeSerializer<ShapedRecipe> getSerializer() {
        return (RecipeSerializer) ModRecipes.TIER_UPGRADE.get();
    }
}
