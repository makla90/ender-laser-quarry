package com.enderlaserquarry.datagen;

import com.enderio.enderio.client.content.machines.IOOverlayBlockStateModel;
import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.client.render.MachineItemRenderer;
import com.enderlaserquarry.content.ConnectedFrameBlock;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.launchpad.LaunchPadPlatingBlock;
import com.enderlaserquarry.content.obelisk.CrystalGlow;
import com.enderlaserquarry.content.port.TieredPortBlock;
import com.enderlaserquarry.init.ModBlocks;
import com.enderlaserquarry.init.ModItems;
import com.mojang.math.Quadrant;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.BlockModelDefinitionGenerator;
import net.minecraft.client.data.models.blockstates.ConditionBuilder;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelInstance;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.client.model.generators.blockstate.CustomBlockStateModelBuilder;
import net.neoforged.neoforge.client.model.generators.loaders.ObjModelBuilder;
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplateBuilder;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Blockstates, block and item models and item definitions. The hand-made geometry (controller antenna templates, the
 * OBJ obelisk) stays in src/main/resources; everything derived from it is made here.
 */
public class ElqModelProvider extends ModelProvider {
    private static final TextureSlot ANTENNA = TextureSlot.create("antenna");
    private static final Identifier CONTROLLER_TEMPLATE = rl("block/quarry_controller_template");
    private static final Identifier MAST_TEMPLATE = rl("block/quarry_controller_mast_template");
    // one face lying on the top of the block, for the connected-top overlays
    private static final Identifier TOP_OVERLAY = rl("block/top_overlay");
    private static final TextureSlot OVERLAY = TextureSlot.create("overlay");
    private static final String[] TIER_PREFIX = {"", "enhanced_", "ender_"};

    private BiConsumer<Identifier, ModelInstance> models;
    private final Map<Identifier, Identifier> made = new HashMap<>();

    public ElqModelProvider(PackOutput output) {
        super(output, EnderLaserQuarry.MOD_ID);
    }

    @Override
    protected Stream<? extends Holder<Block>> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream();
    }

    @Override
    protected Stream<? extends Holder<Item>> getKnownItems() {
        return ModItems.ITEMS.getEntries().stream();
    }

    @Override
    protected void registerModels(BlockModelGenerators blocks, ItemModelGenerators items) {
        models = blocks.modelOutput;
        var states = blocks.blockStateOutput;

        for (QuarryTier tier : QuarryTier.values()) {
            String t = TIER_PREFIX[tier.ordinal()];
            states.accept(quarryPlating(ModBlocks.quarryPlating(tier).get(), t));
            states.accept(dishPlating(ModBlocks.dishPlating(tier).get(), t));
            controllerModels(t);
        }
        // the Launch Pad Controller's antenna mast, drawn by its renderer like the quarry controller's
        model("launch_pad_controller_mast", new ModelTemplate(Optional.of(MAST_TEMPLATE), Optional.empty(), ANTENNA),
            new TextureMapping().put(ANTENNA, mat("launch_pad_controller_antenna")));
        states.accept(quarryPort());
        states.accept(dishPort());
        states.accept(launchPadPlating());
        states.accept(launchPadPort());
        states.accept(launchPadController());
        states.accept(groundRadar());
        states.accept(obelisk());
        for (var block : List.of(ModBlocks.QUARRY_CONTROLLER, ModBlocks.ENHANCED_QUARRY_CONTROLLER, ModBlocks.ENDER_QUARRY_CONTROLLER)) {
            states.accept(facing(block.get(), rl("block/" + block.getId().getPath())));
        }
        for (var block : List.of(ModBlocks.SATELLITE_DISH, ModBlocks.ENHANCED_SATELLITE_DISH, ModBlocks.ENDER_SATELLITE_DISH)) {
            String t = block.getId().getPath().replace("satellite_dish", "");
            Identifier model = ModelTemplates.CUBE_ORIENTABLE_TOP_BOTTOM.create(rl("block/" + block.getId().getPath()), new TextureMapping()
                .put(TextureSlot.FRONT, mat(t + "satellite_dish_front"))
                .put(TextureSlot.SIDE, mat(t + "satellite_dish_plating_side"))
                .put(TextureSlot.TOP, mat(t + "satellite_dish_plating_top_nesw"))
                .put(TextureSlot.BOTTOM, mat(t + "satellite_dish_bottom")), models);
            states.accept(facing(block.get(), model));
        }

        itemModels(items);
    }

    // region blocks

    // Dish and quarry plating and their ports: one model per side-join combination with the fully joined top (the quarry
    // sides show where the grooves end), plus the ConnectedTop pieces of the open sides. The unconnected models stay for
    // the items.

    private BlockModelDefinitionGenerator quarryPlating(Block block, String t) {
        String base = t + "laser_quarry_plating";
        quarryCube(base, base + "_top", base + "_side", base + "_bottom", null);
        MultiPartGenerator parts = MultiPartGenerator.multiPart(block);
        for (BlockState state : joinStates(block)) {
            parts.with(joins(state, BlockModelGenerators.condition()),
                plain(quarryCube(base + "_frame" + connection(state), base + "_top_nesw", base + "_side", base + "_bottom", state)));
        }
        for (CrystalGlow glow : CrystalGlow.values()) {
            withPieces(parts, base, true, glowSuffix(glow), base + "_side", c -> c.term(CrystalGlow.PROPERTY, glow));
        }
        return parts;
    }

    private BlockModelDefinitionGenerator quarryPort() {
        Block block = ModBlocks.QUARRY_PORT.get();
        quarryCube("laser_quarry_port", "laser_quarry_plating_top", "laser_quarry_port_side", "laser_quarry_port_bottom", null);
        MultiPartGenerator parts = MultiPartGenerator.multiPart(block);
        // base first, the pieces lie on the same cells and the later quad wins; Ender IO's IO overlay floats just above
        for (QuarryTier tier : QuarryTier.values()) {
            String t = TIER_PREFIX[tier.ordinal()];
            for (BlockState state : joinStates(block)) {
                parts.with(joins(state, BlockModelGenerators.condition().term(TieredPortBlock.TIER, tier)),
                    ioOverlay(quarryCube(t + "laser_quarry_port_frame" + connection(state), t + "laser_quarry_plating_top_nesw",
                        t + "laser_quarry_port_side", t + "laser_quarry_port_bottom", state)));
            }
        }
        for (QuarryTier tier : QuarryTier.values()) {
            String t = TIER_PREFIX[tier.ordinal()];
            for (CrystalGlow glow : CrystalGlow.values()) {
                withPieces(parts, t + "laser_quarry_plating", true, glowSuffix(glow), t + "laser_quarry_plating_side",
                    c -> c.term(TieredPortBlock.TIER, tier).term(CrystalGlow.PROPERTY, glow));
            }
        }
        return parts;
    }

    private BlockModelDefinitionGenerator dishPlating(Block block, String t) {
        String base = t + "satellite_dish_plating";
        bottomTop(base, base + "_top", base + "_side", t + "satellite_dish_bottom");
        MultiPartGenerator parts = MultiPartGenerator.multiPart(block)
            .with(plain(cellBottomTop(base + "_joined", base + "_top_nesw", base + "_side", t + "satellite_dish_bottom")));
        return withPieces(parts, base, false, "", base + "_side", UnaryOperator.identity());
    }

    private BlockModelDefinitionGenerator dishPort() {
        bottomTop("satellite_dish_port", "satellite_dish_plating_top", "satellite_dish_port_side", "satellite_dish_port_bottom");
        MultiPartGenerator parts = MultiPartGenerator.multiPart(ModBlocks.SATELLITE_DISH_PORT.get());
        // base first, see quarryPort
        for (QuarryTier tier : QuarryTier.values()) {
            String t = TIER_PREFIX[tier.ordinal()];
            parts.with(BlockModelGenerators.condition(TieredPortBlock.TIER, tier), ioOverlay(cellBottomTop(t + "satellite_dish_port_joined",
                t + "satellite_dish_plating_top_nesw", t + "satellite_dish_port_side", t + "satellite_dish_port_bottom")));
        }
        for (QuarryTier tier : QuarryTier.values()) {
            String t = TIER_PREFIX[tier.ordinal()];
            withPieces(parts, t + "satellite_dish_plating", false, "", t + "satellite_dish_plating_side", c -> c.term(TieredPortBlock.TIER, tier));
        }
        return parts;
    }

    // every ConnectedTop piece of <family>_edge_<side><glow>, shown while its side is open (and its neighbours fit)
    private MultiPartGenerator withPieces(MultiPartGenerator parts, String family, boolean strip, String glow, String particle,
                                          UnaryOperator<ConditionBuilder> extra) {
        List<ConnectedTop.Piece> pieces = ConnectedTop.pieces(strip);
        List<ConnectedTop.Cell> cells = ConnectedTop.cells(strip);
        for (int p = 0; p < pieces.size(); p++) {
            ConnectedTop.Piece piece = pieces.get(p);
            int bit = 1 << p;
            ConditionBuilder condition = extra.apply(BlockModelGenerators.condition()).term(joinProperty(piece.side()), false);
            piece.neighbours().forEach((side, joined) -> condition.term(joinProperty(side), joined));
            parts.with(condition, plain(pieceModel(family + "_piece_" + piece.name() + glow,
                family + "_edge_" + ConnectedTop.letter(piece.side()) + glow, particle,
                cells.stream().filter(c -> (c.pieces() & bit) != 0).map(ConnectedTop.Cell::rect).toList())));
        }
        return parts;
    }

    // faces lying on the block's top, each showing the same spot of the texture
    private Identifier pieceModel(String name, String texture, String particle, List<ConnectedTop.Rect> rects) {
        return made.computeIfAbsent(rl("block/" + name), id -> {
            ExtendedModelTemplateBuilder builder = ExtendedModelTemplateBuilder.builder()
                .requiredTextureSlot(OVERLAY).requiredTextureSlot(TextureSlot.PARTICLE);
            topCells(builder, rects, OVERLAY);
            return builder.build().create(id, new TextureMapping().put(OVERLAY, mat(texture)).put(TextureSlot.PARTICLE, mat(particle)), models);
        });
    }

    private static void topCells(ExtendedModelTemplateBuilder builder, List<ConnectedTop.Rect> rects, TextureSlot texture) {
        for (ConnectedTop.Rect r : rects) {
            builder.element(e -> e.from(r.x0(), 16, r.y0()).to(r.x1(), 16, r.y1())
                .face(Direction.UP, face -> face.uvs(r.x0(), r.y0(), r.x1(), r.y1()).texture(texture).cullface(Direction.UP)));
        }
    }

    // a cube whose top is cut into the ConnectedTop cells, so the pieces drawn over it don't flicker
    private Identifier cellTopCube(String name, boolean strip, TextureMapping textures) {
        return made.computeIfAbsent(rl("block/" + name), id -> {
            ExtendedModelTemplateBuilder builder = ExtendedModelTemplateBuilder.builder().requiredTextureSlot(TextureSlot.PARTICLE)
                .requiredTextureSlot(TextureSlot.UP).requiredTextureSlot(TextureSlot.DOWN)
                .requiredTextureSlot(TextureSlot.NORTH).requiredTextureSlot(TextureSlot.SOUTH)
                .requiredTextureSlot(TextureSlot.EAST).requiredTextureSlot(TextureSlot.WEST);
            builder.element(e -> {
                e.from(0, 0, 0).to(16, 16, 16);
                for (Direction side : List.of(Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
                    e.face(side, face -> face.texture(faceSlot(side)).cullface(side));
                }
            });
            topCells(builder, ConnectedTop.cells(strip).stream().map(ConnectedTop.Cell::rect).toList(), TextureSlot.UP);
            return builder.build().create(id, textures, models);
        });
    }

    private static TextureSlot faceSlot(Direction side) {
        return switch (side) {
            case DOWN -> TextureSlot.DOWN;
            case UP -> TextureSlot.UP;
            case NORTH -> TextureSlot.NORTH;
            case SOUTH -> TextureSlot.SOUTH;
            case EAST -> TextureSlot.EAST;
            case WEST -> TextureSlot.WEST;
        };
    }

    // joined dish plating / port: plain sides, cell top
    private Identifier cellBottomTop(String name, String top, String side, String bottom) {
        return cellTopCube(name, false, new TextureMapping()
            .put(TextureSlot.PARTICLE, mat(side))
            .put(TextureSlot.UP, mat(top))
            .put(TextureSlot.DOWN, mat(bottom))
            .put(TextureSlot.NORTH, mat(side))
            .put(TextureSlot.SOUTH, mat(side))
            .put(TextureSlot.EAST, mat(side))
            .put(TextureSlot.WEST, mat(side)));
    }

    // quarry plating / port cube; the side faces show where the grooves end next to joined neighbours (none for the
    // item, which keeps a plain cube)
    private Identifier quarryCube(String name, String top, String side, String bottom, BlockState state) {
        TextureMapping textures = new TextureMapping()
            .put(TextureSlot.PARTICLE, mat(side))
            .put(TextureSlot.UP, mat(top))
            .put(TextureSlot.DOWN, mat(bottom))
            .put(TextureSlot.NORTH, mat(side + (state == null ? "" : sideJoin(state, Direction.NORTH))))
            .put(TextureSlot.SOUTH, mat(side + (state == null ? "" : sideJoin(state, Direction.SOUTH))))
            .put(TextureSlot.EAST, mat(side + (state == null ? "" : sideJoin(state, Direction.EAST))))
            .put(TextureSlot.WEST, mat(side + (state == null ? "" : sideJoin(state, Direction.WEST))));
        return state == null ? model(name, ModelTemplates.CUBE, textures) : cellTopCube(name, true, textures);
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

    private static ConditionBuilder joins(BlockState state, ConditionBuilder condition) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            condition.term(joinProperty(side), state.getValue(joinProperty(side)));
        }
        return condition;
    }

    private static String glowSuffix(CrystalGlow glow) {
        return glow == CrystalGlow.NONE ? "" : "_" + glow.getSerializedName();
    }

    // Launch pad blocks: the fully joined model plus a hazard edge overlay per open side (see withPadEdges). The
    // unconnected models stay for the items.

    private BlockModelDefinitionGenerator launchPadPlating() {
        bottomTop("launch_pad_plating", "launch_pad_plating_top", "launch_pad_side", "quarry_machine_bottom");
        return withPadEdges(MultiPartGenerator.multiPart(ModBlocks.LAUNCH_PAD_PLATING.get())
            .with(BlockModelGenerators.condition(LaunchPadPlatingBlock.CENTER, true),
                plain(bottomTop("launch_pad_plating_center", "launch_pad_top_connected", "launch_pad_side", "quarry_machine_bottom")))
            .with(BlockModelGenerators.condition(LaunchPadPlatingBlock.CENTER, false),
                plain(bottomTop("launch_pad_plating_joined", "launch_pad_plating_top_nesw", "launch_pad_side", "quarry_machine_bottom"))));
    }

    private BlockModelDefinitionGenerator launchPadPort() {
        bottomTop("launch_pad_port", "launch_pad_plating_top", "launch_pad_port_side", "launch_pad_port_bottom");
        // base first, the edges lie on its top; Ender IO's IO overlay floats just above both
        return withPadEdges(MultiPartGenerator.multiPart(ModBlocks.LAUNCH_PAD_PORT.get())
            .with(ioOverlay(bottomTop("launch_pad_port_joined", "launch_pad_plating_top_nesw", "launch_pad_port_side", "launch_pad_port_bottom"))));
    }

    // the top keeps its world alignment (uvlock) when the front turns, so the edges line up
    private BlockModelDefinitionGenerator launchPadController() {
        Identifier joined = model("launch_pad_controller_joined", ModelTemplates.CUBE_ORIENTABLE_TOP_BOTTOM, new TextureMapping()
            .put(TextureSlot.FRONT, mat("launch_pad_front"))
            .put(TextureSlot.SIDE, mat("launch_pad_side"))
            .put(TextureSlot.TOP, mat("launch_pad_controller_top_nesw"))
            .put(TextureSlot.BOTTOM, mat("quarry_machine_bottom")));
        MultiPartGenerator parts = MultiPartGenerator.multiPart(ModBlocks.LAUNCH_PAD.get());
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            MultiVariant variant = rotated(joined, facing);
            parts.with(BlockModelGenerators.condition(BlockStateProperties.HORIZONTAL_FACING, facing),
                facing == Direction.NORTH ? variant : variant.with(BlockModelGenerators.UV_LOCK));
        }
        return withPadEdges(parts);
    }

    private BlockModelDefinitionGenerator groundRadar() {
        bottomTop("ground_radar", "ground_radar_top", "ground_radar_side", "quarry_machine_bottom");
        // the ring goes last: it covers the edge bolts that sit under it
        return withPadEdges(MultiPartGenerator.multiPart(ModBlocks.GROUND_RADAR.get())
            .with(plain(bottomTop("ground_radar_joined", "launch_pad_plating_top_nesw", "ground_radar_side", "quarry_machine_bottom"))))
            .with(plain(topOverlay("ground_radar_ring", "ground_radar_side")));
    }

    // the hazard strip of each side that isn't joined to the next pad block; the same four on every pad block
    private MultiPartGenerator withPadEdges(MultiPartGenerator parts) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            parts.with(BlockModelGenerators.condition(joinProperty(side), false),
                plain(topOverlay("launch_pad_edge_" + side.getSerializedName().charAt(0), "launch_pad_side")));
        }
        return parts;
    }

    // a multipart takes its break particles from the first part, which can be an overlay
    private Identifier topOverlay(String texture, String particle) {
        return model(texture, new ModelTemplate(Optional.of(TOP_OVERLAY), Optional.empty(), OVERLAY, TextureSlot.PARTICLE),
            new TextureMapping().put(OVERLAY, mat(texture)).put(TextureSlot.PARTICLE, mat(particle)));
    }

    private BlockModelDefinitionGenerator obelisk() {
        return states(ModBlocks.CRYSTAL_OBELISK.get(), state -> {
            String glow = glow(state);
            Identifier id = rl("block/crystal_obelisk" + glow);
            return plain(made.computeIfAbsent(id, key -> ExtendedModelTemplateBuilder.builder()
                .parent(Identifier.withDefaultNamespace("block/block"))
                .requiredTextureSlot(TextureSlot.PARTICLE).requiredTextureSlot(TextureSlot.TOP)
                .requiredTextureSlot(TextureSlot.SIDE).requiredTextureSlot(TextureSlot.BOTTOM)
                .customLoader(ObjModelBuilder::new, obj -> obj
                    .modelLocation(rl("models/block/crystal_obelisk.obj"))
                    .flipV(true)
                    .automaticCulling(false))
                .build()
                .create(key, new TextureMapping()
                    .put(TextureSlot.PARTICLE, mat("crystal_obelisk_side" + glow))
                    .put(TextureSlot.TOP, mat("crystal_obelisk_top" + glow))
                    .put(TextureSlot.SIDE, mat("crystal_obelisk_side" + glow))
                    .put(TextureSlot.BOTTOM, mat("quarry_machine_bottom")), models)));
        });
    }

    // block model + the standalone antenna mast the renderer draws on top
    private void controllerModels(String t) {
        String base = t + "quarry_controller";
        model(base, ModelTemplates.CUBE_ORIENTABLE_TOP_BOTTOM, controllerTextures(base));
        model(base + "_mast", new ModelTemplate(Optional.of(MAST_TEMPLATE), Optional.empty(), ANTENNA),
            new TextureMapping().put(ANTENNA, mat(base + "_antenna")));
    }

    private static TextureMapping controllerTextures(String base) {
        return new TextureMapping()
            .put(TextureSlot.FRONT, mat(base + "_front"))
            .put(TextureSlot.SIDE, mat(base + "_side"))
            .put(TextureSlot.TOP, mat(base + "_top"))
            .put(TextureSlot.BOTTOM, mat(base + "_bottom"));
    }

    // endregion

    // region items

    private void itemModels(ItemModelGenerators items) {
        for (DeferredItem<?> item : List.of(ModItems.LASER_CRYSTAL, ModItems.PULSATING_LASER_CRYSTAL, ModItems.VOID_LASER_CRYSTAL,
            ModItems.SATELLITE, ModItems.ENHANCED_SATELLITE, ModItems.ENDER_SATELLITE, ModItems.VOID_SATELLITE, ModItems.ROCKET, ModItems.ROCKET_ENGINE,
            ModItems.PARABOLIC_DISH, ModItems.FOCUSING_LENS, ModItems.LASER_EMITTER, ModItems.SIGNAL_TRANSCEIVER, ModItems.REFLECTOR_MIRROR,
            ModItems.CHORUS_GYROSCOPE, ModItems.VOID_REFLECTOR)) {
            items.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM);
        }
        items.generateFlatItem(ModItems.LASER_LINKER.get(), ModelTemplates.FLAT_HANDHELD_ITEM);

        // block items that show their block model
        for (DeferredItem<?> item : List.of(ModItems.QUARRY_PLATING, ModItems.ENHANCED_QUARRY_PLATING, ModItems.ENDER_QUARRY_PLATING,
            ModItems.QUARRY_PORT, ModItems.SATELLITE_DISH_PLATING, ModItems.ENHANCED_SATELLITE_DISH_PLATING,
            ModItems.ENDER_SATELLITE_DISH_PLATING, ModItems.SATELLITE_DISH_PORT, ModItems.LAUNCH_PAD_PLATING, ModItems.LAUNCH_PAD_PORT,
            ModItems.CRYSTAL_OBELISK)) {
            String name = item.getId().getPath();
            Identifier model = new ModelTemplate(Optional.of(rl("block/" + name)), Optional.empty())
                .create(rl("item/" + name), new TextureMapping(), models);
            items.itemModelOutput.accept(item.get(), ItemModelUtils.plainModel(model));
        }

        // controller with its antenna on top
        for (DeferredItem<?> item : List.of(ModItems.QUARRY_CONTROLLER, ModItems.ENHANCED_QUARRY_CONTROLLER, ModItems.ENDER_QUARRY_CONTROLLER)) {
            String name = item.getId().getPath();
            Identifier model = ExtendedModelTemplateBuilder.builder()
                .parent(CONTROLLER_TEMPLATE)
                .requiredTextureSlot(TextureSlot.FRONT).requiredTextureSlot(TextureSlot.SIDE).requiredTextureSlot(TextureSlot.TOP)
                .requiredTextureSlot(TextureSlot.BOTTOM).requiredTextureSlot(ANTENNA)
                .transform(ItemDisplayContext.GUI, d -> d.rotation(30, 225, 0).translation(0, -1.25f, 0).scale(0.55f))
                .build()
                .create(rl("item/" + name), controllerTextures(name).put(ANTENNA, mat(name + "_antenna")), models);
            items.itemModelOutput.accept(item.get(), ItemModelUtils.plainModel(model));
        }

        // drawn in 3D by MachineItemRenderer; the base model only carries the transforms and particle
        machineItem(items, ModItems.SATELLITE_DISH, "satellite_dish_plating_side", -0.45f, 0.2f, 0.875f);
        machineItem(items, ModItems.ENHANCED_SATELLITE_DISH, "enhanced_satellite_dish_plating_side", -0.45f, 0.2f, 0.875f);
        machineItem(items, ModItems.ENDER_SATELLITE_DISH, "ender_satellite_dish_plating_side", -0.45f, 0.2f, 0.875f);
        machineItem(items, ModItems.GROUND_RADAR, "ground_radar_side", -0.45f, 0.3f, 0.86f);
        // the Launch Pad Controller with its mast, like the quarry controllers
        Identifier padModel = ExtendedModelTemplateBuilder.builder()
            .parent(CONTROLLER_TEMPLATE)
            .requiredTextureSlot(TextureSlot.FRONT).requiredTextureSlot(TextureSlot.SIDE).requiredTextureSlot(TextureSlot.TOP)
            .requiredTextureSlot(TextureSlot.BOTTOM).requiredTextureSlot(ANTENNA)
            .transform(ItemDisplayContext.GUI, d -> d.rotation(30, 225, 0).translation(0, -1.25f, 0).scale(0.55f))
            .build()
            .create(rl("item/launch_pad_controller"), new TextureMapping()
                .put(TextureSlot.FRONT, mat("launch_pad_front"))
                .put(TextureSlot.SIDE, mat("launch_pad_side"))
                .put(TextureSlot.TOP, mat("launch_pad_controller_top"))
                .put(TextureSlot.BOTTOM, mat("quarry_machine_bottom"))
                .put(ANTENNA, mat("launch_pad_controller_antenna")), models);
        items.itemModelOutput.accept(ModItems.LAUNCH_PAD.get(), ItemModelUtils.plainModel(padModel));
    }

    private void machineItem(ItemModelGenerators items, DeferredItem<?> item, String particle, float guiX, float guiY, float guiScale) {
        String name = item.getId().getPath();
        Identifier base = ExtendedModelTemplateBuilder.builder()
            .requiredTextureSlot(TextureSlot.PARTICLE)
            .guiLight(UnbakedModel.GuiLight.SIDE)
            .transform(ItemDisplayContext.GUI, d -> d.rotation(30, 225, 0).translation(guiX, guiY, 0).scale(guiScale))
            .transform(ItemDisplayContext.GROUND, d -> d.rotation(0, 0, 0).translation(0, 3, 0).scale(0.5f))
            .transform(ItemDisplayContext.FIXED, d -> d.rotation(0, 180, 0).translation(0, -1, 0).scale(0.9f))
            .transform(ItemDisplayContext.HEAD, d -> d.rotation(0, 180, 0).translation(0, 0, 0).scale(1))
            .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, d -> d.rotation(75, 45, 0).translation(0, 2.5f, 0).scale(0.6f))
            .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND, d -> d.rotation(75, 45, 0).translation(0, 2.5f, 0).scale(0.6f))
            .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, d -> d.rotation(0, 135, 0).translation(0, 0, 0).scale(0.6f))
            .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, d -> d.rotation(0, 135, 0).translation(0, 0, 0).scale(0.6f))
            .build()
            .create(rl("item/" + name), new TextureMapping().put(TextureSlot.PARTICLE, mat(particle)), models);
        items.itemModelOutput.accept(item.get(), ItemModelUtils.specialModel(base, new MachineItemRenderer.Unbaked(rl(name))));
    }

    // endregion

    // region helpers

    /** One blockstate entry per possible state. */
    private static BlockModelDefinitionGenerator states(Block block, Function<BlockState, MultiVariant> variant) {
        return new BlockModelDefinitionGenerator() {
            @Override
            public Block block() {
                return block;
            }

            @Override
            public BlockStateModelDispatcher create() {
                Map<String, BlockStateModel.Unbaked> variants = new HashMap<>();
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    variants.put(key(state), variant.apply(state).toUnbaked());
                }
                return new BlockStateModelDispatcher(Optional.of(new BlockStateModelDispatcher.SimpleModelSelectors(variants)), Optional.empty());
            }
        };
    }

    private static String key(BlockState state) {
        return state.getProperties().stream()
            .map(property -> property.getName() + "=" + name(state, property))
            .collect(Collectors.joining(","));
    }

    private static <T extends Comparable<T>> String name(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static BlockModelDefinitionGenerator facing(Block block, Identifier model) {
        return states(block, state -> rotated(model, state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    private static MultiVariant rotated(Identifier model, Direction facing) {
        MultiVariant variant = plain(model);
        return switch (facing) {
            case EAST -> variant.with(VariantMutator.Y_ROT.withValue(Quadrant.R90));
            case SOUTH -> variant.with(VariantMutator.Y_ROT.withValue(Quadrant.R180));
            case WEST -> variant.with(VariantMutator.Y_ROT.withValue(Quadrant.R270));
            default -> variant;
        };
    }

    private static MultiVariant plain(Identifier model) {
        return BlockModelGenerators.plainVariant(model);
    }

    // Ender IO draws the IO config of the port on top of it
    private static MultiVariant ioOverlay(Identifier model) {
        return MultiVariant.of(new CustomBlockStateModelBuilder.Simple(new IOOverlayBlockStateModel.Unbaked(BlockModelGenerators.plainModel(model))));
    }

    private Identifier bottomTop(String name, String top, String side, String bottom) {
        return model(name, ModelTemplates.CUBE_BOTTOM_TOP, new TextureMapping()
            .put(TextureSlot.TOP, mat(top))
            .put(TextureSlot.SIDE, mat(side))
            .put(TextureSlot.BOTTOM, mat(bottom)));
    }

    // each model once, several states share one
    private Identifier model(String name, ModelTemplate template, TextureMapping textures) {
        return made.computeIfAbsent(rl("block/" + name), id -> template.create(id, textures, models));
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

    private static Material mat(String block) {
        return new Material(rl("block/" + block));
    }

    private static Identifier rl(String path) {
        return EnderLaserQuarry.rl(path);
    }

    // endregion
}
