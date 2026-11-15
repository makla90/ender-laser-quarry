package com.enderlaserquarry.content.demo;

import com.enderio.enderio.api.io.IOConfigurable;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.init.EIOConduits;
import com.enderlaserquarry.EnderLaserQuarry;
import com.enderlaserquarry.content.QuarryTier;
import com.enderlaserquarry.content.asteroid.Asteroid;
import com.enderlaserquarry.content.controller.QuarryControllerBlockEntity;
import com.enderlaserquarry.content.dish.SatelliteDishBlockEntity;
import com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity;
import com.enderlaserquarry.content.linker.LaserLinkerItem;
import com.enderlaserquarry.content.obelisk.CrystalObeliskBlockEntity;
import com.enderlaserquarry.content.port.StructurePortBlockEntity;
import com.enderlaserquarry.init.ModBlocks;
import com.enderlaserquarry.init.ModItems;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /elq demo [basic|enhanced|ender|asteroid]} (ops only): builds a ready base at Y 200 around the player, a sign at every part.
 */
@EventBusSubscriber(modid = EnderLaserQuarry.MOD_ID)
public final class DemoCommand {
    private static final int FLOOR_Y = 199;
    public static final int SIZE = 41;
    public static final int WATER_TANKS = 4;
    private static final int[] DISH_ROWS = {24, 18, 12, 6};

    private DemoCommand() {
    }

    public static BlockPos floor(BlockPos o) {
        return o.offset(20, 0, 22);
    }

    public static BlockPos padCentre(BlockPos o) {
        return o.offset(20, 0, 7);
    }

    public static BlockPos padController(BlockPos o) {
        return padCentre(o).south(LaunchPadBlockEntity.RADIUS);
    }

    public static BlockPos station(BlockPos o) {
        return o.offset(20, 0, 31);
    }

    public static BlockPos waterTank(BlockPos o, int y) {
        return o.offset(18, y, 27);
    }

    public static BlockPos xpTank(BlockPos o) {
        return o.offset(22, 0, 27);
    }

    public static BlockPos trashCan(BlockPos o) {
        return o.offset(23, 0, 22);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("elq")
            .requires(source -> source.hasPermission(2))
            .then(demoCommand()));
    }

    /** {@code /elq demo} (Enhanced), {@code /elq demo basic|enhanced|ender}, or {@code /elq demo asteroid} (Ender, Void Satellite up). */
    private static LiteralArgumentBuilder<CommandSourceStack> demoCommand() {
        var demo = Commands.literal("demo").executes(context -> {
            build(context.getSource().getPlayerOrException(), QuarryTier.ENHANCED, false);
            return 1;
        });
        for (QuarryTier tier : QuarryTier.values()) {
            demo.then(Commands.literal(tier.id()).executes(context -> {
                build(context.getSource().getPlayerOrException(), tier, false);
                return 1;
            }));
        }
        demo.then(Commands.literal("asteroid").executes(context -> {
            build(context.getSource().getPlayerOrException(), QuarryTier.ENDER, true);
            return 1;
        }));
        return demo;
    }

    private static void build(ServerPlayer player, QuarryTier tier, boolean asteroid) {
        ServerLevel level = player.serverLevel();
        BlockPos o = new BlockPos(player.getBlockX() - SIZE / 2, FLOOR_Y + 1, player.getBlockZ() - SIZE / 2);
        QuarryControllerBlockEntity controller = build(level, o, tier, asteroid);

        player.teleportTo(level, o.getX() + 20.5, o.getY(), o.getZ() + 16.5, 0, 15);
        player.sendSystemMessage(Component.literal("Ender Laser Quarry demo: " + (asteroid ? "asteroid mining" : tier.id()) + ". "
            + "In front of you: the Quarry Controller, it runs almost everything. Behind you: the launch pad with its Rocket Fuel and "
            + "Dew of the Void tanks. Past the quarry: the power station, the water and liquid XP tanks with their pipes into the "
            + "quarry's I/O ports, and " + tier.maxDishes() + " satellite dishes on the power bus. Every part has a sign.").withStyle(ChatFormatting.GREEN));
        if (asteroid) {
            player.sendSystemMessage(Component.literal("The Void Satellite is in orbit and space is scanned. One asteroid is being analyzed "
                + "now: open the Quarry Controller, wait for the analysis, click the asteroid, queue it and press Start.")
                .withStyle(ChatFormatting.AQUA));
        } else {
            player.sendSystemMessage(Component.literal("The satellite is launching (about 20 s). Then open the Quarry Controller, "
                + "click a mapped chunk next to the platform and press Start: the quarry powers up for 10 s, then mines.")
                .withStyle(ChatFormatting.AQUA));
        }
        player.sendSystemMessage(Component.literal("The supply chest next to the controller has a Laser Linker, spare parts, capacitors and the "
            + "other satellites. Use a Yeta Wrench on an I/O port to see and change its sides."));
    }

    /**
     * Builds the base with its north-west floor corner one block below {@code o}.
     * @return the controller
     */
    public static QuarryControllerBlockEntity build(ServerLevel level, BlockPos o) {
        return build(level, o, QuarryTier.ENHANCED, false);
    }

    public static QuarryControllerBlockEntity build(ServerLevel level, BlockPos o, QuarryTier tier) {
        return build(level, o, tier, false);
    }

    public static QuarryControllerBlockEntity build(ServerLevel level, BlockPos o, QuarryTier tier, boolean asteroid) {
        // Platform + clear air above it
        for (int x = -1; x <= SIZE; x++) {
            for (int z = -1; z <= SIZE; z++) {
                level.setBlock(o.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 8; y++) {
                    level.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        LaunchPadBlockEntity pad = buildPad(level, o, tier, asteroid);
        BlockPos floor = floor(o);
        CrystalObeliskBlockEntity obelisk = buildQuarry(level, floor, tier, asteroid);
        QuarryControllerBlockEntity controller = (QuarryControllerBlockEntity) level.getBlockEntity(floor.north(2));

        // Dishes, closest rows first, each with its I/O port on the outer side
        List<Map.Entry<BlockPos, Direction>> dishSpots = new ArrayList<>();
        for (int row : DISH_ROWS) {
            dishSpots.add(Map.entry(o.offset(7, 0, row), Direction.WEST));
            dishSpots.add(Map.entry(o.offset(33, 0, row), Direction.EAST));
        }
        int dishes = Math.min(dishSpots.size(), tier.maxDishes());
        for (var spot : dishSpots.subList(0, dishes)) {
            LaserLinkerItem.link(buildDish(level, spot.getKey(), spot.getValue(), tier), obelisk);
        }
        int northmostDish = dishSpots.get(dishes - 1).getKey().getZ() - o.getZ();

        // Power: the station feeds the bus, the bus runs to the quarry's power port and up both sides to the dishes
        level.setBlockAndUpdate(station(o), DemoWiring.creativePower().defaultBlockState());
        DemoWiring.conduitLine(level, o.offset(5, 0, 30), Direction.EAST, 31, EIOConduits.VIBRANT_ENERGY);
        DemoWiring.conduitLine(level, o.offset(20, 0, 25), Direction.SOUTH, 5, EIOConduits.VIBRANT_ENERGY);
        DemoWiring.conduitLine(level, o.offset(5, 0, northmostDish), Direction.SOUTH, 30 - northmostDish, EIOConduits.VIBRANT_ENERGY);
        DemoWiring.conduitLine(level, o.offset(35, 0, northmostDish), Direction.SOUTH, 30 - northmostDish, EIOConduits.VIBRANT_ENERGY);

        // Water and liquid XP, each through its own pipe into its own I/O port
        List<BlockPos> waterTanks = new ArrayList<>();
        for (int y = 0; y < WATER_TANKS; y++) {
            DemoWiring.tank(level, waterTank(o, y), new FluidStack(Fluids.WATER, Integer.MAX_VALUE));
            waterTanks.add(waterTank(o, y));
        }
        DemoWiring.tank(level, xpTank(o), new FluidStack(fluid("fluid_xp_juice_still"), Integer.MAX_VALUE));
        DemoWiring.conduitLine(level, o.offset(18, 0, 25), Direction.SOUTH, 2, EIOConduits.ENERGETIC_FLUID);
        DemoWiring.conduitLine(level, o.offset(22, 0, 25), Direction.SOUTH, 2, EIOConduits.ENERGETIC_FLUID);

        // The output port pushes what the quarry mines into a trash can
        level.setBlockAndUpdate(trashCan(o), ModBlocks.DEV_TRASH_CAN.get().defaultBlockState());
        if (level.getBlockEntity(floor.east(2)) instanceof StructurePortBlockEntity port) {
            port.setIOMode(Direction.EAST, IOMode.PUSH);
        }

        // Rocket Fuel and Dew of the Void share one pipe into the launch pad's I/O port
        BlockPos padPort = padCentre(o).east(2);
        DemoWiring.conduitLine(level, padPort.east(), Direction.EAST, 4, EIOConduits.VIBRANT_FLUID);
        DemoWiring.tank(level, padPort.offset(3, 0, 1), new FluidStack(fluid("fluid_rocket_fuel_still"), Integer.MAX_VALUE));
        DemoWiring.tank(level, padPort.offset(4, 0, 1), new FluidStack(fluid("fluid_dew_of_the_void_still"), Integer.MAX_VALUE));

        // Ender IO machines and conduits reset their config when they initialise, so set them up a moment later
        later(level, 2, () -> {
            // every water tank above the bottom one pushes down, so the pipe always finds the bottom one full
            for (BlockPos pos : waterTanks.subList(1, waterTanks.size())) {
                if (level.getBlockEntity(pos) instanceof IOConfigurable io) {
                    io.setIOMode(Direction.DOWN, IOMode.PUSH);
                }
            }
            for (int x : new int[] {18, 22}) {
                DemoWiring.fluidSide(level, o.offset(x, 0, 26), Direction.SOUTH, true, EIOConduits.ENERGETIC_FLUID);
                DemoWiring.fluidSide(level, o.offset(x, 0, 25), Direction.NORTH, false, EIOConduits.ENERGETIC_FLUID);
            }
            DemoWiring.fluidSide(level, padPort.east(), Direction.WEST, false, EIOConduits.VIBRANT_FLUID);
            DemoWiring.fluidSide(level, padPort.east(3), Direction.SOUTH, true, EIOConduits.VIBRANT_FLUID);
            DemoWiring.fluidSide(level, padPort.east(4), Direction.SOUTH, true, EIOConduits.VIBRANT_FLUID);
        });

        signs(level, o, floor, padPort, dishes);
        fillSupplyChest(level, o.offset(17, 0, 19), tier, asteroid);
        // on both sides of the controller, facing the player who starts in front of it
        DemoWiring.sign(level, o.offset(19, 0, 19), true, 8, "START HERE:", "open this Quarry", "Controller to", "try it all");
        DemoWiring.sign(level, o.offset(21, 0, 19), true, 8, "Pick chunks,", "Start / Stop,", "Orbit, tanks,", "asteroids");

        LaserLinkerItem.link(controller, pad);
        if (asteroid) {
            // Void Satellite already up, space scanned; the first asteroid is analyzed once the radar has power
            pad.debugPlaceInOrbit(voidSatellite());
            pad.getAsteroids().refresh(level, level.getRandom(), id -> false);
            later(level, 60, () -> {
                List<Asteroid> found = pad.getAsteroids().asteroids();
                if (!found.isEmpty()) {
                    controller.tryAnalyze(found.getFirst().id());
                }
            });
        } else {
            pad.tryLaunch();
        }
        return controller;
    }

    private static void signs(ServerLevel level, BlockPos o, BlockPos floor, BlockPos padPort, int dishes) {
        DemoWiring.sign(level, station(o).south(), "Power station", "(creative power)", "feeds the bus", "below it");
        DemoWiring.sign(level, waterTank(o, 0).south(), "Water tanks", "pipe -> I/O port", "cools the", "Laser Crystal");
        DemoWiring.sign(level, xpTank(o).south(), "Liquid XP", "pipe -> I/O port", "Mending repairs", "the crystal");
        DemoWiring.sign(level, o.offset(21, 0, 26), "Power line", "-> I/O port", "-> Quarry", "Controller");
        DemoWiring.sign(level, trashCan(o).south(), "Mined items go", "into the TRASH!", "Swap in a chest", "to keep them");
        DemoWiring.sign(level, o.offset(6, 0, 31), "Power bus", "every dish gets", "power through its", "own I/O port");
        DemoWiring.sign(level, floor.offset(-3, 0, 0), "Laser Quarry", "obelisk + crystal", "controller north", dishes + " dishes beam in");
        DemoWiring.sign(level, padPort.offset(3, 0, 2), "Rocket Fuel", "pipe -> Launch", "Pad I/O port", "(for launches)");
        DemoWiring.sign(level, padPort.offset(4, 0, 2), "Dew of the Void", "space scans of", "the Void", "Satellite");
        DemoWiring.sign(level, padCentre(o).offset(-3, 0, 1), "Ground Radar", "powered by the", "Quarry Controller", "via its antenna");
    }

    private static Fluid fluid(String id) {
        return BuiltInRegistries.FLUID.get(ResourceLocation.fromNamespaceAndPath("enderio", id));
    }

    public static ItemStack voidSatellite() {
        ItemStack satellite = new ItemStack(ModItems.VOID_SATELLITE.get());
        com.enderlaserquarry.content.satellite.SatelliteItem.setDew(satellite, com.enderlaserquarry.content.satellite.SatelliteItem.dewCapacity());
        return satellite;
    }

    /**
     * A ready launch pad: controller on the south edge (facing the quarry), Ground Radar on the west edge, I/O port on the
     * east edge. Fuel and Dew are pre-filled so the first launch goes right away; the pipe keeps them topped up.
     */
    private static LaunchPadBlockEntity buildPad(ServerLevel level, BlockPos o, QuarryTier tier, boolean asteroid) {
        BlockPos pad = padCentre(o);
        place(level, pad, ModBlocks.LAUNCH_PAD_PLATING.get());
        ring(level, pad, 1, ModBlocks.LAUNCH_PAD_PLATING.get());
        ring(level, pad, 2, ModBlocks.LAUNCH_PAD_PLATING.get());
        place(level, pad.east(2), ModBlocks.LAUNCH_PAD_PORT.get());
        place(level, pad.west(2), ModBlocks.GROUND_RADAR.get());
        BlockPos controllerPos = padController(o);
        BlockState controllerState = ModBlocks.LAUNCH_PAD.get().defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        level.setBlockAndUpdate(controllerPos, Block.updateFromNeighbourShapes(controllerState, level, controllerPos));

        LaunchPadBlockEntity be = (LaunchPadBlockEntity) level.getBlockEntity(controllerPos);
        be.updateStructure();
        LaunchPadBlockEntity.ROCKET.setStackInSlot(be, new ItemStack(ModItems.ROCKET.get()));
        if (!asteroid) {
            LaunchPadBlockEntity.SATELLITE.setStackInSlot(be, new ItemStack(ModItems.satellite(tier).get()));
        }
        LaunchPadBlockEntity.FUEL.fill(be, new FluidStack(fluid("fluid_rocket_fuel_still"), 16_000), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        LaunchPadBlockEntity.DEW.fill(be, new FluidStack(fluid("fluid_dew_of_the_void_still"), 8_000), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        return be;
    }

    /**
     * The laser quarry: a 5x5 plating floor centred on {@code pos}, the controller on the north edge, I/O ports for water,
     * power and liquid XP on the south edge and one for the output on the east edge, the obelisk + crystal on top.
     * @return the obelisk
     */
    public static CrystalObeliskBlockEntity buildQuarry(ServerLevel level, BlockPos pos, QuarryTier tier, boolean asteroid) {
        place(level, pos, ModBlocks.quarryPlating(tier).get());
        ring(level, pos, 1, ModBlocks.quarryPlating(tier).get());
        ring(level, pos, 2, ModBlocks.quarryPlating(tier).get());
        level.setBlockAndUpdate(pos.north(2), ModBlocks.controller(tier).get().defaultBlockState());
        // The controller capacitor sets how many items a port moves per tick: one per tier keeps up with the dishes
        String controllerCapacitor = tier == QuarryTier.ENDER ? "octadic_capacitor" : tier == QuarryTier.ENHANCED ? "double_layer_capacitor" : "basic_capacitor";
        equipController((QuarryControllerBlockEntity) level.getBlockEntity(pos.north(2)), controllerCapacitor);
        for (BlockPos port : List.of(pos.offset(-2, 0, 2), pos.south(2), pos.offset(2, 0, 2), pos.east(2))) {
            place(level, port, ModBlocks.QUARRY_PORT.get());
        }
        level.setBlockAndUpdate(pos.above(), ModBlocks.CRYSTAL_OBELISK.get().defaultBlockState());
        CrystalObeliskBlockEntity obelisk = (CrystalObeliskBlockEntity) level.getBlockEntity(pos.above());
        ItemStack crystal = new ItemStack(asteroid ? ModItems.VOID_LASER_CRYSTAL.get()
            : tier == QuarryTier.BASIC ? ModItems.PULSATING_LASER_CRYSTAL.get() : ModItems.LASER_CRYSTAL.get());
        obelisk.insertCrystal(enchanted(level, crystal));
        return obelisk;
    }

    // capacitor, full coolant tank and a full buffer
    private static void equipController(QuarryControllerBlockEntity controller, String capacitorId) {
        controller.getInventory().setStackInSlot(controller.getCapacitorSlotIndex(), new ItemStack(BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath("enderio", capacitorId))));
        QuarryControllerBlockEntity.COOLANT.fill(controller, new FluidStack(Fluids.WATER, controller.getCoolantTank().getCapacity()),
            net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        controller.getEnergyStorage().setEnergyStored(controller.getMaxEnergyStored());
    }

    /** A formed dish with a Satellite Dish I/O Port in its frame on the {@code portSide} and an octadic capacitor. */
    private static SatelliteDishBlockEntity buildDish(ServerLevel level, BlockPos pos, Direction portSide, QuarryTier tier) {
        level.setBlockAndUpdate(pos, ModBlocks.dish(tier).get().defaultBlockState());
        ring(level, pos, 1, ModBlocks.dishPlating(tier).get());
        place(level, pos.relative(portSide), ModBlocks.SATELLITE_DISH_PORT.get());
        SatelliteDishBlockEntity dish = (SatelliteDishBlockEntity) level.getBlockEntity(pos);
        dish.getInventory().setStackInSlot(dish.getCapacitorSlotIndex(), new ItemStack(BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath("enderio", "octadic_capacitor"))));
        return dish;
    }

    private static void fillSupplyChest(ServerLevel level, BlockPos pos, QuarryTier tier, boolean asteroid) {
        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        DemoWiring.sign(level, pos.north(), false, 8, "Supplies", "linker, parts,", "capacitors,", "satellites");
        if (!(level.getBlockEntity(pos) instanceof Container chest)) {
            return;
        }
        List<ItemStack> items = List.of(
            new ItemStack(ModItems.LASER_LINKER.get()),
            new ItemStack(ModItems.ROCKET.get()),
            new ItemStack(ModItems.satellite(tier).get()),
            asteroid ? new ItemStack(ModItems.ENDER_SATELLITE.get()) : voidSatellite(),
            new ItemStack(ModBlocks.GROUND_RADAR.get()),
            enchanted(level, new ItemStack(ModItems.PULSATING_LASER_CRYSTAL.get())),
            enchanted(level, new ItemStack(ModItems.LASER_CRYSTAL.get())),
            enchanted(level, new ItemStack(asteroid ? ModItems.LASER_CRYSTAL.get() : ModItems.VOID_LASER_CRYSTAL.get())),
            new ItemStack(ModBlocks.LAUNCH_PAD_PORT.get(), 2),
            new ItemStack(ModBlocks.QUARRY_PORT.get(), 2),
            new ItemStack(ModBlocks.SATELLITE_DISH_PORT.get(), 2),
            new ItemStack(ModBlocks.dish(tier).get(), 2),
            new ItemStack(ModBlocks.dishPlating(tier).get(), 16),
            new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("enderio", "basic_capacitor")), 4),
            new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("enderio", "double_layer_capacitor")), 4),
            new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("enderio", "octadic_capacitor")), 4),
            new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("enderio", "yeta_wrench"))),
            new ItemStack(ModBlocks.DEV_TRASH_CAN.get(), 2),
            new ItemStack(Items.HOPPER, 4),
            new ItemStack(Items.WATER_BUCKET),
            new ItemStack(Items.CHEST, 4),
            new ItemStack(Items.SPYGLASS));
        for (int i = 0; i < items.size(); i++) {
            chest.setItem(i, items.get(i));
        }
    }

    /** Mending + Unbreaking III, so players see Mending pull from the XP tank. */
    public static ItemStack enchanted(ServerLevel level, ItemStack crystal) {
        var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        crystal.enchant(enchantments.getOrThrow(Enchantments.MENDING), 1);
        crystal.enchant(enchantments.getOrThrow(Enchantments.UNBREAKING), 3);
        return crystal;
    }

    private record Later(ServerLevel level, long tick, Runnable task) {
    }

    private static final List<Later> LATER = new ArrayList<>();

    /** Runs the task that many ticks from now (Ender IO machines reset their config on their first tick). */
    public static void later(ServerLevel level, int ticks, Runnable task) {
        LATER.add(new Later(level, level.getGameTime() + ticks, task));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (LATER.isEmpty()) {
            return;
        }
        for (Later later : List.copyOf(LATER)) {
            if (later.level().getGameTime() >= later.tick()) {
                LATER.remove(later);
                later.task().run();
            }
        }
    }

    /** The same-layer square ring this far from the centre (1 = the 8 neighbours). */
    private static void ring(ServerLevel level, BlockPos center, int radius, Block block) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == radius) {
                    place(level, center.offset(dx, 0, dz), block);
                }
            }
        }
    }

    /** Places with the connected-texture state worked out from what is already there. */
    private static void place(ServerLevel level, BlockPos pos, Block block) {
        BlockState state = Block.updateFromNeighbourShapes(block.defaultBlockState(), level, pos);
        level.setBlockAndUpdate(pos, state);
    }
}
