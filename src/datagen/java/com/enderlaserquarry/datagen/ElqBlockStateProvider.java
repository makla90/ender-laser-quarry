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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.MultiPartBlockStateBuilder;
import net.neoforged.neoforge.client.model.generators.loaders.CompositeModelBuilder;
import net.neoforged.neoforge.client.model.generators.loaders.ObjModelBuilder;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

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

    // Dish and quarry plating and their ports: one model per side-join combination with the fully joined top (the quarry
    // sides show where the grooves end), plus the ConnectedTop pieces of the open sides. The unconnected models stay for
    // the items.

    private void quarryPlating(Block block, String t) {
        String base = t + "laser_quarry_plating";
        quarryCube(base, base + "_top", base + "_side", base + "_bottom", null);
        MultiPartBlockStateBuilder parts = getMultipartBuilder(block);
        for (BlockState state : joinStates(block)) {
            joins(state, parts.part().modelFile(quarryCube(base + "_frame" + connection(state), base + "_top_nesw", base + "_side",
                base + "_bottom", state)).addModel()).end();
        }
        for (CrystalGlow glow : CrystalGlow.values()) {
            withPieces(parts, base, true, glowSuffix(glow), base + "_side", p -> p.condition(CrystalGlow.PROPERTY, glow));
        }
    }

    private void quarryPort() {
        Block block = ModBlocks.QUARRY_PORT.get();
        quarryPortModel("laser_quarry_port", "laser_quarry_plating_top", "", null);
        MultiPartBlockStateBuilder parts = getMultipartBuilder(block);
        // pieces first: Ender IO's IO overlay of the base goes on top of them
        for (QuarryTier tier : QuarryTier.values()) {
            String t = TIER_PREFIX[tier.ordinal()];
            for (CrystalGlow glow : CrystalGlow.values()) {
                withPieces(parts, t + "laser_quarry_plating", true, glowSuffix(glow), t + "laser_quarry_plating_side",
                    p -> p.condition(TieredPortBlock.TIER, tier).condition(CrystalGlow.PROPERTY, glow));
            }
        }
        for (QuarryTier tier : QuarryTier.values()) {
            String t = TIER_PREFIX[tier.ordinal()];
            for (BlockState state : joinStates(block)) {
                joins(state, parts.part().modelFile(quarryPortModel(t + "laser_quarry_port_frame" + connection(state),
                    t + "laser_quarry_plating_top_nesw", t, state)).addModel().condition(TieredPortBlock.TIER, tier)).end();
            }
        }
    }

    private ModelFile quarryPortModel(String name, String top, String t, BlockState state) {
        return model(name, () -> port(name, t + "laser_quarry_port_side",
            cube(state == null ? models().nested().parent(models().getExistingFile(mcLoc("block/cube"))) : cellCube(models().nested().parent(BLOCK)),
                t + "laser_quarry_port_side", top, t + "laser_quarry_port_bottom", t + "laser_quarry_port_side", state)));
    }

    private void dishPlating(Block block, String t) {
        String base = t + "satellite_dish_plating";
        cubeBottomTop(base, base + "_top", base + "_side", t + "satellite_dish_bottom");
        MultiPartBlockStateBuilder parts = getMultipartBuilder(block);
        String joined = base + "_joined";
        parts.part().modelFile(model(joined, () -> bottomTop(cellBottomTop(models().getBuilder(joined).parent(BLOCK)),
            base + "_top_nesw", base + "_side", t + "satellite_dish_bottom").texture("particle", tex(base + "_side")))).addModel().end();
        withPieces(parts, base, false, "", base + "_side", UnaryOperator.identity());
    }

    private void dishPort() {
        dishPortModel("satellite_dish_port", "", "satellite_dish_plating_top");
        MultiPartBlockStateBuilder parts = getMultipartBuilder(ModBlocks.SATELLITE_DISH_PORT.get());
        for (QuarryTier tier : QuarryTier.values()) {
            String t = TIER_PREFIX[tier.ordinal()];
            withPieces(parts, t + "satellite_dish_plating", false, "", t + "satellite_dish_plating_side", p -> p.condition(TieredPortBlock.TIER, tier));
        }
        for (QuarryTier tier : QuarryTier.values()) {
            String t = TIER_PREFIX[tier.ordinal()];
            parts.part().modelFile(dishPortModel(t + "satellite_dish_port_joined", t, t + "satellite_dish_plating_top_nesw"))
                .addModel().condition(TieredPortBlock.TIER, tier).end();
        }
    }

    private ModelFile dishPortModel(String name, String t, String top) {
        // the unconnected one (item) keeps a plain cube
        boolean joined = name.endsWith("_joined");
        return model(name, () -> port(name, t + "satellite_dish_port_side",
            bottomTop(joined ? cellBottomTop(models().nested().parent(BLOCK)) : models().nested().parent(models().getExistingFile(mcLoc("block/cube_bottom_top"))),
                top, t + "satellite_dish_port_side", t + "satellite_dish_port_bottom")));
    }

    // every ConnectedTop piece of <family>_edge_<side><glow>, shown while its side is open (and its neighbours fit)
    private void withPieces(MultiPartBlockStateBuilder parts, String family, boolean strip, String glow, String particle,
                            UnaryOperator<MultiPartBlockStateBuilder.PartBuilder> extra) {
        List<ConnectedTop.Piece> pieces = ConnectedTop.pieces(strip);
        List<ConnectedTop.Cell> cells = ConnectedTop.cells(strip);
        for (int p = 0; p < pieces.size(); p++) {
            ConnectedTop.Piece piece = pieces.get(p);
            int bit = 1 << p;
            MultiPartBlockStateBuilder.PartBuilder part = extra.apply(parts.part().modelFile(pieceModel(family + "_piece_" + piece.name() + glow,
                family + "_edge_" + ConnectedTop.letter(piece.side()) + glow, particle,
                cells.stream().filter(c -> (c.pieces() & bit) != 0).map(ConnectedTop.Cell::rect).toList())).addModel())
                .condition(joinProperty(piece.side()), false);
            piece.neighbours().forEach((side, joined) -> part.condition(joinProperty(side), joined));
            part.end();
        }
    }

    // faces lying on the block's top, each showing the same spot of the texture
    private ModelFile pieceModel(String name, String texture, String particle, List<ConnectedTop.Rect> rects) {
        return model(name, () -> topCells(models().getBuilder(name).renderType("cutout")
            .texture("overlay", tex(texture)).texture("particle", tex(particle)), rects, "#overlay"));
    }

    private static BlockModelBuilder topCells(BlockModelBuilder builder, List<ConnectedTop.Rect> rects, String texture) {
        for (ConnectedTop.Rect r : rects) {
            builder.element().from(r.x0(), 16, r.y0()).to(r.x1(), 16, r.y1())
                .face(Direction.UP).uvs(r.x0(), r.y0(), r.x1(), r.y1()).texture(texture).cullface(Direction.UP).end()
                .end();
        }
        return builder;
    }

    // a cube whose top is cut into the ConnectedTop cells, so the pieces drawn over it don't flicker; faces = texture refs
    private static BlockModelBuilder cellTopCube(BlockModelBuilder builder, boolean strip, Function<Direction, String> faces) {
        var cube = builder.element().from(0, 0, 0).to(16, 16, 16);
        for (Direction side : List.of(Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
            cube.face(side).texture(faces.apply(side)).cullface(side).end();
        }
        cube.end();
        return topCells(builder, ConnectedTop.cells(strip).stream().map(ConnectedTop.Cell::rect).toList(), faces.apply(Direction.UP));
    }

    private static BlockModelBuilder cellCube(BlockModelBuilder builder) {
        return cellTopCube(builder, true, side -> "#" + side.getSerializedName());
    }

    private static BlockModelBuilder cellBottomTop(BlockModelBuilder builder) {
        return cellTopCube(builder, false, side -> switch (side) {
            case UP -> "#top";
            case DOWN -> "#bottom";
            default -> "#side";
        });
    }

    // quarry plating cube; the side faces show where the grooves end next to joined neighbours (none for the item, which
    // keeps a plain cube)
    private ModelFile quarryCube(String name, String top, String side, String bottom, BlockState state) {
        return model(name, () -> state == null
            ? cube(models().withExistingParent(name, mcLoc("block/cube")), side, top, bottom, side, null)
            : cube(cellCube(models().getBuilder(name).parent(BLOCK)), side, top, bottom, side, state));
    }

    // one state per combination of joined sides, everything else at its default
    private static List<BlockState> joinStates(Block block) {
        List<BlockState> states = new ArrayList<>();
        for (int mask = 0; mask < 16; mask++) {
            BlockState state = block.defaultBlockState();
            int bit = 0;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                state = state.setValue(joinProperty(side), (mask & 1 << bit++) != 0);
            }
            states.add(state);
        }
        return states;
    }

    private static MultiPartBlockStateBuilder.PartBuilder joins(BlockState state, MultiPartBlockStateBuilder.PartBuilder part) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            part.condition(joinProperty(side), state.getValue(joinProperty(side)));
        }
        return part;
    }

    private static String glowSuffix(CrystalGlow glow) {
        return glow == CrystalGlow.NONE ? "" : "_" + glow.getSerializedName();
    }

    // Launch pad blocks: the fully joined model plus a hazard edge overlay per open side (see withPadEdges). The
    // unconnected models stay for the items.

    private void launchPadPlating() {
        cubeBottomTop("launch_pad_plating", "launch_pad_plating_top", "launch_pad_side", "quarry_machine_bottom");
        MultiPartBlockStateBuilder parts = getMultipartBuilder(ModBlocks.LAUNCH_PAD_PLATING.get());
        parts.part().modelFile(cubeBottomTop("launch_pad_plating_center", "launch_pad_top_connected", "launch_pad_side", "quarry_machine_bottom"))
            .addModel().condition(LaunchPadPlatingBlock.CENTER, true).end();
        parts.part().modelFile(cubeBottomTop("launch_pad_plating_joined", "launch_pad_plating_top_nesw", "launch_pad_side", "quarry_machine_bottom"))
            .addModel().condition(LaunchPadPlatingBlock.CENTER, false).end();
        withPadEdges(parts);
    }

    private void launchPadPort() {
        padPort("launch_pad_port", "launch_pad_plating_top");
        // base last, so Ender IO's IO overlay is drawn over the edges
        MultiPartBlockStateBuilder parts = withPadEdges(getMultipartBuilder(ModBlocks.LAUNCH_PAD_PORT.get()));
        parts.part().modelFile(padPort("launch_pad_port_joined", "launch_pad_plating_top_nesw")).addModel().end();
    }

    private ModelFile padPort(String name, String top) {
        return model(name, () -> port(name, "launch_pad_port_side",
            bottomTop(models().nested().parent(models().getExistingFile(mcLoc("block/cube_bottom_top"))),
                top, "launch_pad_port_side", "launch_pad_port_bottom")));
    }

    // the top keeps its world alignment (uvlock) when the front turns, so the edges line up
    private void launchPadController() {
        ModelFile joined = model("launch_pad_controller_joined", () -> models().withExistingParent("launch_pad_controller_joined",
                mcLoc("block/orientable_with_bottom"))
            .texture("front", tex("launch_pad_front"))
            .texture("side", tex("launch_pad_side"))
            .texture("top", tex("launch_pad_controller_top_nesw"))
            .texture("bottom", tex("quarry_machine_bottom")));
        MultiPartBlockStateBuilder parts = getMultipartBuilder(ModBlocks.LAUNCH_PAD.get());
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            parts.part().modelFile(joined).rotationY(rotation(facing)).uvLock(facing != Direction.NORTH)
                .addModel().condition(BlockStateProperties.HORIZONTAL_FACING, facing).end();
        }
        withPadEdges(parts);
    }

    private void groundRadar() {
        cubeBottomTop("ground_radar", "ground_radar_top", "ground_radar_side", "quarry_machine_bottom");
        MultiPartBlockStateBuilder parts = getMultipartBuilder(ModBlocks.GROUND_RADAR.get());
        parts.part().modelFile(cubeBottomTop("ground_radar_joined", "launch_pad_plating_top_nesw", "ground_radar_side", "quarry_machine_bottom"))
            .addModel().end();
        withPadEdges(parts);
        // the ring goes last: it covers the edge bolts that sit under it
        parts.part().modelFile(topOverlay("ground_radar_ring", "ground_radar_side")).addModel().end();
    }

    // the hazard strip of each side that isn't joined to the next pad block; the same four on every pad block
    private MultiPartBlockStateBuilder withPadEdges(MultiPartBlockStateBuilder parts) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            parts.part().modelFile(topOverlay("launch_pad_edge_" + side.getSerializedName().charAt(0), "launch_pad_side"))
                .addModel().condition(joinProperty(side), false).end();
        }
        return parts;
    }

    // one face lying on the top of the block; a multipart takes its break particles from the first part, which can be one
    private ModelFile topOverlay(String texture, String particle) {
        return model(texture, () -> models().withExistingParent(texture, modLoc("block/top_overlay"))
            .renderType("cutout")
            .texture("overlay", tex(texture))
            .texture("particle", tex(particle)));
    }

    private ModelFile cubeBottomTop(String name, String top, String side, String bottom) {
        return model(name, () -> bottomTop(models().withExistingParent(name, mcLoc("block/cube_bottom_top")), top, side, bottom));
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

    /** Side texture variant: whether the neighbours left / right of this face (seen from outside) are joined; none without a state. */
    private static String sideJoin(BlockState state, Direction face) {
        if (state == null) {
            return "";
        }
        boolean left = joined(state, face.getClockWise());
        boolean right = joined(state, face.getCounterClockWise());
        return left && right ? "_lr" : left ? "_l" : right ? "_r" : "";
    }

    private static boolean joined(BlockState state, Direction side) {
        return state.getValue(joinProperty(side));
    }

    private static BooleanProperty joinProperty(Direction side) {
        return switch (side) {
            case NORTH -> ConnectedFrameBlock.NORTH;
            case EAST -> ConnectedFrameBlock.EAST;
            case SOUTH -> ConnectedFrameBlock.SOUTH;
            default -> ConnectedFrameBlock.WEST;
        };
    }

    private ResourceLocation tex(String block) {
        return modLoc("block/" + block);
    }

    // endregion
}
