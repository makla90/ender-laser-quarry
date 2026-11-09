package com.enderlaserquarry.datagen;

import com.enderio.enderio.content.machines.alloy.AlloySmeltingRecipe;
import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.TierUpgradeRecipe;
import com.enderlaserquarry.init.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Every recipe (design decisions D2). Material ladder = Ender IO's own: Basic dark steel / basic capacitor, Enhanced
 * energetic + soularium / double-layer, Ender vibrant + end steel / octadic. No recipe book unlocks, like before.
 */
public class ElqRecipeProvider extends RecipeProvider {
    private final RecipeOutput out;

    protected ElqRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
        this.out = output;
    }

    public static class Runner extends RecipeProvider.Runner {
        public Runner(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
            return new ElqRecipeProvider(registries, output);
        }

        @Override
        public String getName() {
            return "Ender Laser Quarry recipes";
        }
    }

    @Override
    protected void buildRecipes() {
        Ingredient darkSteel = c("ingots/dark_steel"), endSteel = c("ingots/end_steel"), soularium = c("ingots/soularium");
        Ingredient energetic = c("ingots/energetic_alloy"), conductive = c("ingots/conductive_alloy"), redstoneAlloy = c("ingots/redstone_alloy");
        Ingredient bimetalGear = c("gears/dark_steel"), ironGear = c("gears/iron"), energizedGear = c("gears/energized"), vibrantGear = c("gears/vibrant");
        Ingredient chassis = eio("void_chassis"), binder = eio("conduit_binder");
        Ingredient enlightened = c("glass_blocks/enlighted_fused_quartz"), fusedQuartz = eio("fused_quartz");
        Ingredient transceiver = of(ModItems.SIGNAL_TRANSCEIVER.get()), parabolic = of(ModItems.PARABOLIC_DISH.get());
        Ingredient basicCapacitor = eio("basic_capacitor"), resonator = eio("ender_resonator");

        // crystals
        shaped(ModItems.PULSATING_LASER_CRYSTAL.get(), 1, keys('A', of(Items.AMETHYST_SHARD), 'P', c("gems/pulsating_crystal"), 'D', c("gems/diamond")),
            "APA", "PDP", "APA");
        upgrade(ModItems.LASER_CRYSTAL.get(), keys('Q', enlightened, 'N', of(Items.NETHER_STAR), 'E', c("gems/enticing_crystal"),
            'P', of(ModItems.PULSATING_LASER_CRYSTAL.get()), 'S', soularium), "QNQ", "EPE", "QSQ");
        upgrade(ModItems.VOID_LASER_CRYSTAL.get(), keys('P', c("gems/prescient_crystal"), 'B', of(Items.DRAGON_BREATH), 'E', endSteel,
            'L', of(ModItems.LASER_CRYSTAL.get())), "PBP", "ELE", "PBP");

        // quarry core
        shaped(ModItems.CRYSTAL_OBELISK.get(), 1, keys('L', of(ModItems.FOCUSING_LENS.get()), 'D', darkSteel, 'G', bimetalGear,
            'O', of(Items.OBSIDIAN), 'C', chassis), " L ", "DGD", "OCO");
        shaped(ModItems.QUARRY_CONTROLLER.get(), 1, keys('G', bimetalGear, 'M', of(Items.MAP), 'C', basicCapacitor, 'V', chassis,
            'D', darkSteel, 'T', transceiver), "GMG", "CVC", "DTD");
        upgrade(ModItems.ENHANCED_QUARRY_CONTROLLER.get(), keys('S', soularium, 'Z', eio("z_logic_controller"), 'D', eio("double_layer_capacitor"),
            'B', of(ModItems.QUARRY_CONTROLLER.get()), 'G', energizedGear), "SZS", "DBD", "SGS");
        upgrade(ModItems.ENDER_QUARRY_CONTROLLER.get(), keys('E', endSteel, 'R', resonator, 'O', eio("octadic_capacitor"),
            'B', of(ModItems.ENHANCED_QUARRY_CONTROLLER.get()), 'G', vibrantGear), "ERE", "OBO", "EGE");
        shaped(ModItems.GROUND_RADAR.get(), 1, keys('G', bimetalGear, 'P', parabolic, 'T', transceiver, 'V', chassis, 'D', darkSteel,
            'O', of(Items.OBSERVER)), "GPG", "TVT", "DOD");

        // plating
        shaped(ModItems.QUARRY_PLATING.get(), 8, keys('O', of(Items.OBSIDIAN), 'D', darkSteel, 'E', c("ender_pearls")), "ODO", "DED", "ODO");
        shaped(ModItems.ENHANCED_QUARRY_PLATING.get(), 4, keys('P', of(ModItems.QUARRY_PLATING.get()), 'S', soularium, 'Q', enlightened),
            "PSP", "SQS", "PSP");
        shaped(ModItems.ENDER_QUARRY_PLATING.get(), 4, keys('P', of(ModItems.ENHANCED_QUARRY_PLATING.get()), 'E', endSteel,
            'C', c("gems/ender_crystal")), "PEP", "ECE", "PEP");
        shaped(ModItems.SATELLITE_DISH_PLATING.get(), 8, keys('I', c("ingots/iron"), 'D', darkSteel, 'G', ironGear), "IDI", "DGD", "IDI");
        shaped(ModItems.ENHANCED_SATELLITE_DISH_PLATING.get(), 4, keys('P', of(ModItems.SATELLITE_DISH_PLATING.get()), 'E', energetic,
            'G', energizedGear), "PEP", "EGE", "PEP");
        shaped(ModItems.ENDER_SATELLITE_DISH_PLATING.get(), 4, keys('P', of(ModItems.ENHANCED_SATELLITE_DISH_PLATING.get()), 'E', endSteel,
            'G', vibrantGear), "PEP", "EGE", "PEP");
        shaped(ModItems.LAUNCH_PAD_PLATING.get(), 8, keys('S', of(Items.SMOOTH_STONE), 'Y', c("dyes/yellow"), 'B', c("dyes/black"),
            'D', darkSteel), "SYS", "BDB", "SYS");

        // I/O ports: plating of any tier
        shaped(ModItems.QUARRY_PORT.get(), 1, keys('B', binder, 'H', of(Items.HOPPER), 'G', ironGear, 'U', of(Items.BUCKET),
            'P', of(ModItems.QUARRY_PLATING.get(), ModItems.ENHANCED_QUARRY_PLATING.get(), ModItems.ENDER_QUARRY_PLATING.get())), "BHB", "GPG", "BUB");
        shaped(ModItems.SATELLITE_DISH_PORT.get(), 1, keys('B', binder, 'C', conductive, 'G', ironGear,
            'P', of(ModItems.SATELLITE_DISH_PLATING.get(), ModItems.ENHANCED_SATELLITE_DISH_PLATING.get(), ModItems.ENDER_SATELLITE_DISH_PLATING.get())),
            "BCB", "GPG", "BCB");
        shaped(ModItems.LAUNCH_PAD_PORT.get(), 1, keys('B', binder, 'H', of(Items.HOPPER), 'G', ironGear, 'U', of(Items.BUCKET),
            'P', of(ModItems.LAUNCH_PAD_PLATING.get())), "BHB", "GPG", "BUB");

        // dishes
        shaped(ModItems.SATELLITE_DISH.get(), 1, keys('G', ironGear, 'P', parabolic, 'C', conductive, 'V', chassis, 'D', darkSteel,
            'L', of(ModItems.LASER_EMITTER.get())), "GPG", "CVC", "DLD");
        upgrade(ModItems.ENHANCED_SATELLITE_DISH.get(), keys('E', energetic, 'G', energizedGear, 'S', soularium,
            'B', of(ModItems.SATELLITE_DISH.get()), 'Q', enlightened), "EGE", "SBS", "EQE");
        upgrade(ModItems.ENDER_SATELLITE_DISH.get(), keys('E', endSteel, 'G', vibrantGear, 'C', c("gems/vibrant_crystal"),
            'B', of(ModItems.ENHANCED_SATELLITE_DISH.get()), 'R', resonator), "EGE", "CBC", "ERE");

        // launch pad and orbit
        shaped(ModItems.LAUNCH_PAD.get(), 1, keys('G', bimetalGear, 'P', parabolic, 'I', c("storage_blocks/iron"), 'V', chassis,
            'D', darkSteel, 'T', transceiver), "GPG", "IVI", "DTD");
        shaped(ModItems.ROCKET.get(), 1, keys('B', eio("dark_steel_bars"), 'C', of(Items.COMPASS), 'D', darkSteel,
            'T', eio("pressurized_fluid_tank"), 'E', of(ModItems.ROCKET_ENGINE.get())), "BCB", "DTD", "DED");
        shaped(ModItems.ROCKET_ENGINE.get(), 1, keys('D', darkSteel, 'F', of(Items.FIRE_CHARGE), 'R', redstoneAlloy,
            'B', of(Items.BLAST_FURNACE), 'I', eio("industrial_insulation")), "DFD", "RBR", "DID");
        shaped(ModItems.SATELLITE.get(), 1, keys('D', darkSteel, 'T', transceiver, 'P', eio("energetic_photovoltaic_module"),
            'M', of(ModItems.REFLECTOR_MIRROR.get()), 'C', basicCapacitor), "DTD", "PMP", "DCD");
        upgrade(ModItems.ENHANCED_SATELLITE.get(), keys('E', energetic, 'Z', eio("z_logic_controller"), 'P', eio("pulsating_photovoltaic_module"),
            'B', of(ModItems.SATELLITE.get()), 'D', of(Items.DAYLIGHT_DETECTOR)), "EZE", "PBP", "EDE");
        upgrade(ModItems.ENDER_SATELLITE.get(), keys('E', endSteel, 'R', resonator, 'P', eio("vibrant_photovoltaic_module"),
            'B', of(ModItems.ENHANCED_SATELLITE.get()), 'C', c("gems/prescient_crystal")), "ERE", "PBP", "ECE");

        // parts
        shaped(ModItems.LASER_EMITTER.get(), 1, keys('Q', fusedQuartz, 'R', redstoneAlloy, 'P', c("gems/pulsating_crystal"), 'C', conductive,
            'B', basicCapacitor), " Q ", "RPR", "CBC");
        shaped(ModItems.SIGNAL_TRANSCEIVER.get(), 1, keys('L', of(Items.LIGHTNING_ROD), 'C', conductive, 'B', basicCapacitor,
            'R', redstoneAlloy, 'P', c("ingots/pulsating_alloy")), " L ", "CBC", "RPR");
        shaped(ModItems.PARABOLIC_DISH.get(), 1, keys('C', conductive, 'G', ironGear), "C C", "CGC", " C ");
        save(ModItems.LASER_LINKER.get(), new ShapedRecipe(new Recipe.CommonInfo(true), new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.EQUIPMENT, ""),
            ShapedRecipePattern.of(keys('N', c("nuggets/conductive_alloy"), 'R', c("dusts/redstone"), 'C', conductive, 'I', c("ingots/iron")),
                " NR", "NCN", "IN "), new ItemStackTemplate(ModItems.LASER_LINKER.get())));
        alloy(ModItems.FOCUSING_LENS.get(), List.of(new SizedIngredient(fusedQuartz, 4), new SizedIngredient(of(Items.AMETHYST_SHARD), 2),
            new SizedIngredient(c("gems/diamond"), 1)));
        alloy(ModItems.REFLECTOR_MIRROR.get(), List.of(new SizedIngredient(fusedQuartz, 1), new SizedIngredient(c("ingots/gold"), 3),
            new SizedIngredient(darkSteel, 1)));
    }

    private void shaped(Item result, int count, Map<Character, Ingredient> key, String... pattern) {
        save(result, new ShapedRecipe(new Recipe.CommonInfo(true), new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
            ShapedRecipePattern.of(key, pattern), new ItemStackTemplate(result, count)));
    }

    // copies the components of the lower tier part (TierUpgradeRecipe)
    private void upgrade(Item result, Map<Character, Ingredient> key, String... pattern) {
        save(result, new TierUpgradeRecipe(new Recipe.CommonInfo(true), new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
            ShapedRecipePattern.of(key, pattern), new ItemStackTemplate(result)));
    }

    private void alloy(Item result, List<SizedIngredient> inputs) {
        save(result, new AlloySmeltingRecipe(inputs, new ItemStackTemplate(result), 6400, 0.3f));
    }

    private void save(Item result, Recipe<?> recipe) {
        Identifier id = EnderLaserQuarry.rl(result.builtInRegistryHolder().key().identifier().getPath());
        out.accept(ResourceKey.create(Registries.RECIPE, id), recipe, null);
    }

    private Ingredient c(String tag) {
        return Ingredient.of(items.getOrThrow(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", tag))));
    }

    private Ingredient eio(String item) {
        return Ingredient.of(items.getOrThrow(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("enderio", item))).value());
    }

    private static Ingredient of(ItemLike... items) {
        return Ingredient.of(items);
    }

    private static Map<Character, Ingredient> keys(Object... pairs) {
        Map<Character, Ingredient> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Character) pairs[i], (Ingredient) pairs[i + 1]);
        }
        return map;
    }
}
