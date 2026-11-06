package com.enderlaserquarry.content.controller;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.config.QuarryConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the laser may mine and what it costs.
 */
public final class MiningRules {
    // values() copies the array every call
    private static final Direction[] DIRECTIONS = Direction.values();

    // UPDATE_CLIENTS alone still sends shape updates and water floods the hole
    public static final int SILENT = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final Map<Block, Boolean> OUR_BLOCKS = new ConcurrentHashMap<>();

    // one per enchantment set
    private static final Map<ItemEnchantments, ItemStack> MINING_TOOLS = new ConcurrentHashMap<>();

    public static boolean canMine(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || isLiquid(state)) {
            return false;
        }

        // don't eat our own blocks
        if (isOurBlock(state.getBlock())) {
            return false;
        }

        // bedrock etc.
        if (state.getDestroySpeed(level, pos) < 0) {
            return false;
        }

        return !state.hasBlockEntity() || QuarryConfig.MINE_BLOCK_ENTITIES.get();
    }

    // cached per block
    private static boolean isOurBlock(Block block) {
        return OUR_BLOCKS.computeIfAbsent(block, b -> BuiltInRegistries.BLOCK.getKey(b).getNamespace().equals(EnderLaserQuarry.MOD_ID));
    }

    public static boolean isLiquid(BlockState state) {
        return state.getBlock() instanceof LiquidBlock || state.is(Blocks.BUBBLE_COLUMN);
    }

    // also waterlogged blocks, kelp, seagrass
    public static boolean hasLiquid(BlockState state) {
        return isLiquid(state) || !state.getFluidState().isEmpty();
    }

    public static boolean holdsBackLiquid(ServerLevel level, BlockPos pos) {
        var neighbour = new BlockPos.MutableBlockPos();
        for (Direction direction : DIRECTIONS) {
            // doesn't flow up
            if (direction != Direction.DOWN && hasLiquid(level.getBlockState(neighbour.setWithOffset(pos, direction)))) {
                return true;
            }
        }
        return false;
    }

    public static int energyCost(ServerLevel level, BlockPos pos, BlockState state, double scale) {
        // flat cost, liquids report hardness 100
        if (state.isAir() || isLiquid(state)) {
            return QuarryConfig.ENERGY_PER_BLOCK.get();
        }
        float hardness = Math.max(0, state.getDestroySpeed(level, pos));
        return (int) Math.min(Integer.MAX_VALUE,
            Math.round((QuarryConfig.ENERGY_PER_BLOCK.get() + hardness * QuarryConfig.ENERGY_PER_HARDNESS.get()) * scale));
    }

    // Silk Touch / Fortune make every mined block pricier
    // only Silk Touch and Fortune cost more, Mending and Unbreaking don't
    public static double enchantCostScale(ItemEnchantments enchantments) {
        boolean costly = enchantments.keySet().stream().anyMatch(e -> e.is(Enchantments.SILK_TOUCH) || e.is(Enchantments.FORTUNE));
        return costly ? QuarryConfig.ENCHANT_COST_MULTIPLIER.get() : 1.0;
    }

    public static int highestBlock(LevelChunk chunk) {
        int top = chunk.getMinBuildHeight();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                top = Math.max(top, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
            }
        }
        return top;
    }

    // Netherite pickaxe with the crystal's enchantments, so loot tables work normally
    public static ItemStack miningTool(ItemEnchantments enchantments) {
        if (MINING_TOOLS.size() > 32) {
            // only with weird /enchant combos
            MINING_TOOLS.clear();
        }
        return MINING_TOOLS.computeIfAbsent(enchantments, key -> {
            ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
            tool.set(DataComponents.ENCHANTMENTS, key);
            return tool;
        });
    }

    private MiningRules() {
    }
}
