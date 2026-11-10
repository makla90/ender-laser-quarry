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
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.client.model.generators.blockstate.CustomBlockStateModelBuilder;
import net.neoforged.neoforge.client.model.generators.loaders.ObjModelBuilder;
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplateBuilder;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
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

    private BlockModelDefinitionGenerator quarryPlating(Block block, String t) {
        String base = t + "laser_quarry_plating";
        return states(block, state -> {
            String conn = connection(state);
            String glow = glow(state);
            return plain(model(base + conn + glow, ModelTemplates.CUBE, new TextureMapping()
                .put(TextureSlot.PARTICLE, mat(base + "_side"))
                .put(TextureSlot.UP, mat(base + "_top" + conn + glow))
                .put(TextureSlot.DOWN, mat(base + "_bottom"))
                .put(TextureSlot.NORTH, mat(base + "_side" + sideJoin(state, Direction.NORTH)))
                .put(TextureSlot.SOUTH, mat(base + "_side" + sideJoin(state, Direction.SOUTH)))
                .put(TextureSlot.EAST, mat(base + "_side" + sideJoin(state, Direction.EAST)))
                .put(TextureSlot.WEST, mat(base + "_side" + sideJoin(state, Direction.WEST)))));
        });
    }

    private BlockModelDefinitionGenerator quarryPort() {
        return states(ModBlocks.QUARRY_PORT.get(), state -> {
            String t = TIER_PREFIX[state.getValue(TieredPortBlock.TIER).ordinal()];
            String base = t + "laser_quarry_port";
            String conn = connection(state);
            String glow = glow(state);
            return ioOverlay(model(base + conn + glow, ModelTemplates.CUBE, new TextureMapping()
                .put(TextureSlot.PARTICLE, mat(base + "_side"))
                .put(TextureSlot.UP, mat(t + "laser_quarry_plating_top" + conn + glow))
                .put(TextureSlot.DOWN, mat(base + "_bottom"))
                .put(TextureSlot.NORTH, mat(base + "_side" + sideJoin(state, Direction.NORTH)))
                .put(TextureSlot.SOUTH, mat(base + "_side" + sideJoin(state, Direction.SOUTH)))
                .put(TextureSlot.EAST, mat(base + "_side" + sideJoin(state, Direction.EAST)))
                .put(TextureSlot.WEST, mat(base + "_side" + sideJoin(state, Direction.WEST)))));
        });
    }

    private BlockModelDefinitionGenerator dishPlating(Block block, String t) {
        String base = t + "satellite_dish_plating";
        return states(block, state -> plain(bottomTop(base + connection(state), base + "_top" + connection(state),
            base + "_side", t + "satellite_dish_bottom")));
    }

    private BlockModelDefinitionGenerator dishPort() {
        return states(ModBlocks.SATELLITE_DISH_PORT.get(), state -> {
            String t = TIER_PREFIX[state.getValue(TieredPortBlock.TIER).ordinal()];
            String conn = connection(state);
            return ioOverlay(bottomTop(t + "satellite_dish_port" + conn, t + "satellite_dish_plating_top" + conn,
                t + "satellite_dish_port_side", t + "satellite_dish_port_bottom"));
        });
    }

    private BlockModelDefinitionGenerator launchPadPlating() {
        return states(ModBlocks.LAUNCH_PAD_PLATING.get(), state -> state.getValue(LaunchPadPlatingBlock.CENTER)
            ? plain(bottomTop("launch_pad_plating_center", "launch_pad_top_connected", "launch_pad_side", "quarry_machine_bottom"))
            : plain(bottomTop("launch_pad_plating" + connection(state), "launch_pad_plating_top" + connection(state),
                "launch_pad_side", "quarry_machine_bottom")));
    }

    private BlockModelDefinitionGenerator launchPadPort() {
        return states(ModBlocks.LAUNCH_PAD_PORT.get(), state -> ioOverlay(bottomTop("launch_pad_port" + connection(state),
            "launch_pad_plating_top" + connection(state), "launch_pad_port_side", "launch_pad_port_bottom")));
    }

    // the top follows the plating around it, so it keeps its world alignment (uvlock) when the front turns
    private BlockModelDefinitionGenerator launchPadController() {
        return states(ModBlocks.LAUNCH_PAD.get(), state -> {
            String conn = connection(state);
            Identifier model = model("launch_pad_controller" + conn, ModelTemplates.CUBE_ORIENTABLE_TOP_BOTTOM, new TextureMapping()
                .put(TextureSlot.FRONT, mat("launch_pad_front"))
                .put(TextureSlot.SIDE, mat("launch_pad_side"))
                .put(TextureSlot.TOP, mat("launch_pad_controller_top" + conn))
                .put(TextureSlot.BOTTOM, mat("quarry_machine_bottom")));
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            MultiVariant variant = rotated(model, facing);
            return facing == Direction.NORTH ? variant : variant.with(BlockModelGenerators.UV_LOCK);
        });
    }

    private BlockModelDefinitionGenerator groundRadar() {
        return states(ModBlocks.GROUND_RADAR.get(), state -> plain(bottomTop("ground_radar", "ground_radar_top", "ground_radar_side", "quarry_machine_bottom")));
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
            ModItems.SATELLITE, ModItems.ENHANCED_SATELLITE, ModItems.ENDER_SATELLITE, ModItems.ROCKET, ModItems.ROCKET_ENGINE,
            ModItems.PARABOLIC_DISH, ModItems.FOCUSING_LENS, ModItems.LASER_EMITTER, ModItems.SIGNAL_TRANSCEIVER, ModItems.REFLECTOR_MIRROR)) {
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
        machineItem(items, ModItems.LAUNCH_PAD, "launch_pad_side", -0.45f, 0.55f, 0.825f);
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
        return state.getValue(switch (side) {
            case NORTH -> ConnectedFrameBlock.NORTH;
            case EAST -> ConnectedFrameBlock.EAST;
            case SOUTH -> ConnectedFrameBlock.SOUTH;
            default -> ConnectedFrameBlock.WEST;
        });
    }

    private static Material mat(String block) {
        return new Material(rl("block/" + block));
    }

    private static Identifier rl(String path) {
        return EnderLaserQuarry.rl(path);
    }

    // endregion
}
