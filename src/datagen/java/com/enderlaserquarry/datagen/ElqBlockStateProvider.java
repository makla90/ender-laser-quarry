package com.enderlaserquarry.datagen;

import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.ConnectedFrameBlock;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.launchpad.LaunchPadPlatingBlock;
import com.enderlaserquarry.content.obelisk.CrystalGlow;
import com.enderlaserquarry.content.port.TieredPortBlock;
import com.enderlaserquarry.init.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.loaders.CompositeModelBuilder;
import net.neoforged.neoforge.client.model.generators.loaders.ObjModelBuilder;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Blockstates and block models. The hand-made geometry (controller antenna templates, the OBJ obelisk) stays in
 * src/main/resources; everything derived from it is made here.
 */
public class ElqBlockStateProvider extends BlockStateProvider {
    private static final String[] TIER_PREFIX = {"", "enhanced_", "ender_"};
    private static final ModelFile BLOCK = new ModelFile.UncheckedModelFile("minecraft:block/block");
    private static final ModelFile IO_OVERLAY = new ModelFile.UncheckedModelFile("enderio:block/io_overlay");

    private final Map<String, ModelFile> made = new HashMap<>();

    public ElqBlockStateProvider(PackOutput output, ExistingFileHelper files) {
        super(output, EnderLaserQuarry.MOD_ID, files);
    }

    @Override
    protected void registerStatesAndModels() {
        for (QuarryTier tier : QuarryTier.values()) {
            String t = TIER_PREFIX[tier.ordinal()];
            quarryPlating(ModBlocks.quarryPlating(tier).get(), t);
            dishPlating(ModBlocks.dishPlating(tier).get(), t);
            controllerModels(t);
        }
        quarryPort();
        dishPort();
        launchPadPlating();
        launchPadPort();
        launchPadController();
        padMast();
        groundRadar();
        obelisk();
        for (var block : List.of(ModBlocks.QUARRY_CONTROLLER, ModBlocks.ENHANCED_QUARRY_CONTROLLER, ModBlocks.ENDER_QUARRY_CONTROLLER)) {
            String name = block.getId().getPath();
            facing(block.get(), models().getBuilder(name));
        }
        for (var block : List.of(ModBlocks.SATELLITE_DISH, ModBlocks.ENHANCED_SATELLITE_DISH, ModBlocks.ENDER_SATELLITE_DISH)) {
            String name = block.getId().getPath();
            String t = name.replace("satellite_dish", "");
            facing(block.get(), models().withExistingParent(name, mcLoc("block/orientable_with_bottom"))
                .texture("front", tex(t + "satellite_dish_front"))
                .texture("side", tex(t + "satellite_dish_plating_side"))
                .texture("top", tex(t + "satellite_dish_plating_top_nesw"))
                .texture("bottom", tex(t + "satellite_dish_bottom")));
        }
    }

    // region blocks

    private void quarryPlating(Block block, String t) {
        String base = t + "laser_quarry_plating";
        states(block, state -> {
            String conn = connection(state);
            String glow = glow(state);
            return model(base + conn + glow, () -> cube(models().withExistingParent(base + conn + glow, mcLoc("block/cube")),
                base + "_side", base + "_top" + conn + glow, base + "_bottom", base + "_side", state));
        });
    }

    private void quarryPort() {
        states(ModBlocks.QUARRY_PORT.get(), state -> {
            String t = TIER_PREFIX[state.getValue(TieredPortBlock.TIER).ordinal()];
            String base = t + "laser_quarry_port";
            String conn = connection(state);
            String glow = glow(state);
            return model(base + conn + glow, () -> port(base + conn + glow, base + "_side",
                cube(models().nested().parent(models().getExistingFile(mcLoc("block/cube"))),
                    base + "_side", t + "laser_quarry_plating_top" + conn + glow, base + "_bottom", base + "_side", state)));
        });
    }

    private void dishPlating(Block block, String t) {
        String base = t + "satellite_dish_plating";
        states(block, state -> {
            String conn = connection(state);
            return model(base + conn, () -> bottomTop(models().withExistingParent(base + conn, mcLoc("block/cube_bottom_top")),
                base + "_top" + conn, base + "_side", t + "satellite_dish_bottom"));
        });
    }

    private void dishPort() {
        states(ModBlocks.SATELLITE_DISH_PORT.get(), state -> {
            String t = TIER_PREFIX[state.getValue(TieredPortBlock.TIER).ordinal()];
            String conn = connection(state);
            String name = t + "satellite_dish_port" + conn;
            return model(name, () -> port(name, t + "satellite_dish_port_side",
                bottomTop(models().nested().parent(models().getExistingFile(mcLoc("block/cube_bottom_top"))),
                    t + "satellite_dish_plating_top" + conn, t + "satellite_dish_port_side", t + "satellite_dish_port_bottom")));
        });
    }

    private void launchPadPlating() {
        states(ModBlocks.LAUNCH_PAD_PLATING.get(), state -> {
            if (state.getValue(LaunchPadPlatingBlock.CENTER)) {
                return model("launch_pad_plating_center", () -> bottomTop(models().withExistingParent("launch_pad_plating_center",
                    mcLoc("block/cube_bottom_top")), "launch_pad_top_connected", "launch_pad_side", "quarry_machine_bottom"));
            }
            String name = "launch_pad_plating" + connection(state);
            return model(name, () -> bottomTop(models().withExistingParent(name, mcLoc("block/cube_bottom_top")),
                "launch_pad_plating_top" + connection(state), "launch_pad_side", "quarry_machine_bottom"));
        });
    }

    private void launchPadPort() {
        states(ModBlocks.LAUNCH_PAD_PORT.get(), state -> {
            String name = "launch_pad_port" + connection(state);
            return model(name, () -> port(name, "launch_pad_port_side",
                bottomTop(models().nested().parent(models().getExistingFile(mcLoc("block/cube_bottom_top"))),
                    "launch_pad_plating_top" + connection(state), "launch_pad_port_side", "launch_pad_port_bottom")));
        });
    }

    // the top follows the plating around it, so it keeps its world alignment (uvlock) when the front turns
    private void launchPadController() {
        getVariantBuilder(ModBlocks.LAUNCH_PAD.get()).forAllStates(state -> {
            String name = "launch_pad_controller" + connection(state);
            ModelFile model = model(name, () -> models().withExistingParent(name, mcLoc("block/orientable_with_bottom"))
                .texture("front", tex("launch_pad_front"))
                .texture("side", tex("launch_pad_side"))
                .texture("top", tex("launch_pad_controller_top" + connection(state)))
                .texture("bottom", tex("quarry_machine_bottom")));
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            return new ConfiguredModel[]{new ConfiguredModel(model, 0, rotation(facing), facing != Direction.NORTH)};
        });
    }

    private void groundRadar() {
        // the top follows the launch pad plating around it like the controller's; the unconnected one is the item's base
        states(ModBlocks.GROUND_RADAR.get(), state -> {
            String name = "ground_radar" + connection(state);
            return model(name, () -> bottomTop(models().withExistingParent(name, mcLoc("block/cube_bottom_top")),
                "ground_radar_top" + connection(state), "ground_radar_side", "quarry_machine_bottom"));
        });
    }

    private void obelisk() {
        states(ModBlocks.CRYSTAL_OBELISK.get(), state -> {
            String glow = glow(state);
            String name = "crystal_obelisk" + glow;
            return model(name, () -> models().getBuilder(name).parent(BLOCK)
                .customLoader(ObjModelBuilder::begin)
                .modelLocation(modLoc("models/block/crystal_obelisk.obj"))
                .flipV(true)
                .automaticCulling(false)
                .end()
                .texture("particle", tex("crystal_obelisk_side" + glow))
                .texture("top", tex("crystal_obelisk_top" + glow))
                .texture("side", tex("crystal_obelisk_side" + glow))
                .texture("bottom", tex("quarry_machine_bottom")));
        });
    }

    // block model + the standalone antenna mast the renderer draws on top
    private void controllerModels(String t) {
        String base = t + "quarry_controller";
        models().withExistingParent(base, mcLoc("block/orientable_with_bottom"))
            .texture("front", tex(base + "_front"))
            .texture("side", tex(base + "_side"))
            .texture("top", tex(base + "_top"))
            .texture("bottom", tex(base + "_bottom"));
        models().withExistingParent(base + "_mast", modLoc("block/quarry_controller_mast_template"))
            .texture("antenna", tex(base + "_antenna"));
    }

    // the Launch Pad Controller's antenna mast, drawn by its renderer like the quarry controller's
    private void padMast() {
        models().withExistingParent("launch_pad_controller_mast", modLoc("block/quarry_controller_mast_template"))
            .texture("antenna", tex("launch_pad_controller_antenna"));
    }

    // endregion

    // region helpers

    // Ender IO's overlay model draws the IO config of the port on top of it
    private BlockModelBuilder port(String name, String particle, BlockModelBuilder port) {
        return models().getBuilder(name).parent(BLOCK)
            .customLoader(CompositeModelBuilder::begin)
            .child("port", port)
            .child("overlay", models().nested().parent(IO_OVERLAY))
            .itemRenderOrder("port", "overlay")
            .end()
            .texture("particle", tex(particle));
    }

    private BlockModelBuilder cube(BlockModelBuilder builder, String particle, String up, String down, String side, BlockState state) {
        return builder
            .texture("particle", tex(particle))
            .texture("up", tex(up))
            .texture("down", tex(down))
            .texture("north", tex(side + sideJoin(state, Direction.NORTH)))
            .texture("south", tex(side + sideJoin(state, Direction.SOUTH)))
            .texture("east", tex(side + sideJoin(state, Direction.EAST)))
            .texture("west", tex(side + sideJoin(state, Direction.WEST)));
    }

    private BlockModelBuilder bottomTop(BlockModelBuilder builder, String top, String side, String bottom) {
        return builder.texture("top", tex(top)).texture("side", tex(side)).texture("bottom", tex(bottom));
    }

    private void states(Block block, Function<BlockState, ModelFile> model) {
        getVariantBuilder(block).forAllStates(state -> new ConfiguredModel[]{new ConfiguredModel(model.apply(state))});
    }

    private void facing(Block block, ModelFile model) {
        getVariantBuilder(block).forAllStates(state ->
            new ConfiguredModel[]{new ConfiguredModel(model, 0, rotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)), false)});
    }

    private static int rotation(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    // each model once, several states share one
    private ModelFile model(String name, Supplier<ModelFile> make) {
        return made.computeIfAbsent(name, n -> make.get());
    }

    /** "_nesw" letters of the joined sides, "" when none. */
    private static String connection(BlockState state) {
        StringBuilder s = new StringBuilder();
        if (state.getValue(ConnectedFrameBlock.NORTH)) s.append('n');
        if (state.getValue(ConnectedFrameBlock.EAST)) s.append('e');
        if (state.getValue(ConnectedFrameBlock.SOUTH)) s.append('s');
        if (state.getValue(ConnectedFrameBlock.WEST)) s.append('w');
        return s.isEmpty() ? "" : "_" + s;
    }

    private static String glow(BlockState state) {
        CrystalGlow glow = state.getValue(CrystalGlow.PROPERTY);
        return glow == CrystalGlow.NONE ? "" : "_" + glow.getSerializedName();
    }

    /** Side texture variant: whether the neighbours left / right of this face (seen from outside) are joined. */
    private static String sideJoin(BlockState state, Direction face) {
        boolean left = joined(state, face.getClockWise());
        boolean right = joined(state, face.getCounterClockWise());
        return left && right ? "_lr" : left ? "_l" : right ? "_r" : "";
    }

    private static boolean joined(BlockState state, Direction side) {
        return state.getValue(switch (side) {
            case NORTH -> ConnectedFrameBlock.NORTH;
            case EAST -> ConnectedFrameBlock.EAST;
            case SOUTH -> ConnectedFrameBlock.SOUTH;
            default -> ConnectedFrameBlock.WEST;
        });
    }

    private ResourceLocation tex(String block) {
        return modLoc("block/" + block);
    }

    // endregion
}
