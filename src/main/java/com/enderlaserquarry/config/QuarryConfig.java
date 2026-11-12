package com.enderlaserquarry.config;

import com.enderlaserquarry.content.QuarryTier;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

public final class QuarryConfig {
    public static final ModConfigSpec SPEC;

    public record TierValues(ModConfigSpec.IntValue maxChunks, ModConfigSpec.IntValue maxDishes,
                             ModConfigSpec.IntValue maxBlocksPerTick, ModConfigSpec.IntValue chunkRadius,
                             ModConfigSpec.DoubleValue dishSpeed, ModConfigSpec.IntValue dishRange,
                             ModConfigSpec.IntValue coolantTank, ModConfigSpec.IntValue xpTank,
                             ModConfigSpec.IntValue linkRange, ModConfigSpec.IntValue transferRange,
                             ModConfigSpec.IntValue launchFuel, ModConfigSpec.IntValue returnFuel) {
    }

    private static final Map<QuarryTier, TierValues> TIERS = new EnumMap<>(QuarryTier.class);

    public static TierValues tier(QuarryTier tier) {
        return TIERS.get(tier);
    }

    // Satellite dish (capacitor-scaled, Ender IO style: value * capacitorLevel^2)
    public static final ModConfigSpec.IntValue DISH_CAPACITY;
    public static final ModConfigSpec.IntValue DISH_USAGE;

    // Mining cost
    public static final ModConfigSpec.IntValue ENERGY_PER_BLOCK;
    public static final ModConfigSpec.IntValue ENERGY_PER_HARDNESS;
    public static final ModConfigSpec.DoubleValue ENCHANT_COST_MULTIPLIER;
    public static final ModConfigSpec.IntValue PULSATING_BLOCKS_PER_POINT;
    public static final ModConfigSpec.IntValue LASER_BLOCKS_PER_POINT;
    public static final ModConfigSpec.IntValue VOID_BLOCKS_PER_POINT;
    public static final ModConfigSpec.IntValue CRYSTAL_WORN_PERCENT;
    public static final ModConfigSpec.IntValue MENDING_XP_PER_SECOND;
    public static final ModConfigSpec.BooleanValue MINE_BLOCK_ENTITIES;
    public static final ModConfigSpec.IntValue BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue DEPTH_FLOOR_Y;
    public static final ModConfigSpec.IntValue VOID_ASTEROIDS;
    public static final ModConfigSpec.DoubleValue VOID_LARGE_CHANCE;
    public static final ModConfigSpec.DoubleValue VOID_MEDIUM_CHANCE;
    public static final ModConfigSpec.IntValue VOID_SMALL_CHUNKS;
    public static final ModConfigSpec.IntValue VOID_MEDIUM_CHUNKS;
    public static final ModConfigSpec.IntValue VOID_LARGE_CHUNKS;
    public static final ModConfigSpec.IntValue VOID_BLOCKS_PER_CHUNK;
    public static final ModConfigSpec.IntValue VOID_SCAN_TICKS;
    public static final ModConfigSpec.IntValue VOID_SCAN_ENERGY;
    public static final ModConfigSpec.IntValue VOID_MAX_QUEUED;
    public static final ModConfigSpec.DoubleValue VOID_NETHER_CHANCE;
    public static final ModConfigSpec.DoubleValue VOID_END_CHANCE;
    public static final ModConfigSpec.IntValue VOID_ANALYZE_TICKS;
    public static final ModConfigSpec.IntValue VOID_ANALYZE_ENERGY;
    public static final ModConfigSpec.IntValue VOID_SATELLITE_LAUNCH_FUEL;
    public static final ModConfigSpec.IntValue VOID_SATELLITE_RETURN_FUEL;
    public static final ModConfigSpec.IntValue VOID_SATELLITE_DEW_TANK;
    public static final ModConfigSpec.IntValue VOID_SCAN_DEW;
    public static final ModConfigSpec.IntValue PAD_DEW_TANK;
    public static final ModConfigSpec.IntValue SAMPLING_QUICK_SPOTS;
    public static final ModConfigSpec.IntValue SAMPLING_FULL_SPOTS;
    public static final ModConfigSpec.BooleanValue SAMPLING_REFINE;
    public static final ModConfigSpec.IntValue SAMPLING_MAX_HELD;
    public static final ModConfigSpec.IntValue SAMPLING_TICK_BUDGET;
    public static final ModConfigSpec.DoubleValue SAMPLING_PAUSE_MSPT;
    public static final ModConfigSpec.IntValue SAMPLING_MAX_PROBES;
    private static final int QUICK_SPOTS_DEFAULT = 16;
    private static final int FULL_SPOTS_DEFAULT = 128;

    // Launch pad, controller, radar & satellite
    public static final ModConfigSpec.IntValue PAD_TANK_CAPACITY;
    public static final ModConfigSpec.IntValue FUEL_PER_TRANSFER_CHUNK;
    public static final ModConfigSpec.IntValue ORBIT_ALTITUDE;
    public static final ModConfigSpec.IntValue CONTROLLER_OUTPUT_PER_PORT;
    public static final ModConfigSpec.IntValue CONTROLLER_FLUID_PER_PORT;
    public static final ModConfigSpec.IntValue CONTROLLER_ENERGY_INTAKE;
    public static final ModConfigSpec.IntValue CONTROLLER_BUFFER_TICKS;
    public static final ModConfigSpec.IntValue CONTROLLER_USAGE;
    public static final ModConfigSpec.IntValue CONTROLLER_ANTENNA_POWER;
    public static final ModConfigSpec.BooleanValue LOAD_BASE;
    public static final ModConfigSpec.IntValue PROBLEM_TIMEOUT;
    public static final ModConfigSpec.ConfigValue<String> CLAIM_CHECK_MODE;
    public static final ModConfigSpec.IntValue RADAR_CAPACITY;
    public static final ModConfigSpec.IntValue RADAR_MAX_INPUT;
    public static final ModConfigSpec.IntValue RADAR_UPLINK_USAGE;
    public static final ModConfigSpec.IntValue SCAN_TICKS_PER_CHUNK;
    public static final ModConfigSpec.IntValue COOLANT_ENERGY_PER_MB;
    public static final ModConfigSpec.IntValue UNCOOLED_WEAR_MULTIPLIER;

    static {
        var builder = new ModConfigSpec.Builder();

        builder.push("satellite_dish");
        DISH_CAPACITY = builder.comment("Base energy buffer of the dish. Scaled by capacitor level squared.")
            .defineInRange("capacity", 250_000, 1, Integer.MAX_VALUE);
        DISH_USAGE = builder.comment("Base max µI/t (RF/t) the dish can beam to the laser. Scaled by capacitor level squared.")
            .defineInRange("usage", 2_500, 1, Integer.MAX_VALUE);
        builder.pop();

        builder.push("mining");
        ENERGY_PER_BLOCK = builder.comment("Flat RF cost to laser one block.")
            .defineInRange("energyPerBlock", 1_000, 0, Integer.MAX_VALUE);
        ENERGY_PER_HARDNESS = builder.comment("Extra RF per point of block hardness (stone = 1.5, obsidian = 50).")
            .defineInRange("energyPerHardness", 100, 0, Integer.MAX_VALUE);
        ENCHANT_COST_MULTIPLIER = builder.comment("Energy cost multiplier per mined block while the crystal has Silk Touch or Fortune.")
            .defineInRange("enchantCostMultiplier", 1.5, 1.0, 100.0);
        MINE_BLOCK_ENTITIES = builder.comment("Whether the laser may destroy blocks with block entities (chests, machines...).")
            .define("mineBlockEntities", false);
        BLOCKS_PER_TICK = builder.comment("Blocks a dish with a Basic Capacitor lasers per tick. Scales with capacitor level squared (Double-Layer = 4x, Octadic = 9x).",
                "A crystal obelisk adds up the speed of its dishes, up to the controller tier's cap. Each block still costs its full RF,",
                "so faster = hungrier.")
            .defineInRange("blocksPerTick", 1, 1, 1024);
        DEPTH_FLOOR_Y = builder.comment("Lowest Y the \"Down to Y\" depth mode of Enhanced and Ender controllers mines (Overworld only;",
                "elsewhere that mode mines to bedrock). 0 = where deepslate starts.")
            .defineInRange("depthFloorY", 0, -2048, 2048);
        builder.pop();

        builder.comment("Quarry tiers: what a quarry controller of each tier can do. A controller needs a satellite of at least its",
            "tier, and a satellite of a tier maps chunkRadius chunks around its pad.").push("tiers");
        for (QuarryTier tier : QuarryTier.values()) {
            builder.push(tier.id());
            TIERS.put(tier, new TierValues(
                builder.comment("How many chunks the controller can queue.")
                    .defineInRange("maxChunks", tier.defaultMaxChunks(), 1, 1024),
                builder.comment("How many satellite dishes the obelisk combines for this controller (more linked ones stay idle).")
                    .defineInRange("maxDishes", tier.defaultMaxDishes(), 1, 64),
                builder.comment("Speed cap in blocks per tick, however many dishes feed the quarry.")
                    .defineInRange("maxBlocksPerTick", tier.defaultMaxBlocksPerTick(), 1, 4096),
                builder.comment("Chunks around the launch pad (radius) the satellite of this tier maps, and the controller of this tier",
                        "sees on its minimap and can laser.")
                    .defineInRange("chunkRadius", tier.defaultChunkRadius(), 0, 32),
                builder.comment("Satellite dish of this tier: speed, max µI/t and buffer relative to a Basic dish (Basic dish speed by capacitor:",
                        "1 / 2 / 4 blocks/t, rounded). A controller takes dishes up to its own tier.")
                    .defineInRange("dishSpeed", tier.defaultDishSpeed(), 0.1, 64.0),
                builder.comment("Satellite dish of this tier: max distance in blocks to the crystal obelisk it feeds.")
                    .defineInRange("dishRange", tier.defaultDishRange(), 1, 256),
                builder.comment("Coolant tank of a controller of this tier, in mB (kept on a tier upgrade).")
                    .defineInRange("coolantTank", tier.defaultCoolantTank(), 1_000, 1_000_000),
                builder.comment("Liquid XP tank of a controller of this tier, in mB (fluid tag c:experience, filled through the quarry",
                        "I/O ports or by bucket; 20 mB = 1 XP point like Ender IO).")
                    .defineInRange("xpTank", tier.defaultXpTank(), 0, 1_000_000),
                builder.comment("Max horizontal distance in blocks between a controller of this tier and the launch pad it is linked to.")
                    .defineInRange("linkRange", tier.defaultLinkRange(), 1, 4096),
                builder.comment("Satellite of this tier: how many chunks away from its launch pad an orbit transfer may move it (0 = it stays",
                        "over its pad).")
                    .defineInRange("transferRange", tier.defaultTransferRange(), 0, 256),
                builder.comment("Satellite of this tier: rocket fuel in mB to launch it (heavier satellites need more).")
                    .defineInRange("launchFuel", tier.defaultLaunchFuel(), 0, 1_000_000),
                builder.comment("Satellite of this tier: rocket fuel in mB for a flight to it while it is up (recovery, orbit transfer:",
                        "the rocket goes up empty or lightly loaded). An orbit transfer adds fuelPerTransferChunk per chunk.")
                    .defineInRange("returnFuel", tier.defaultReturnFuel(), 0, 1_000_000)));
            builder.pop();
        }
        builder.pop();

        builder.push("launch_pad");
        PAD_TANK_CAPACITY = builder.comment("Rocket fuel tank size of the launch pad, in mB.")
            .defineInRange("tankCapacity", 16_000, 1_000, 1_000_000);
        PAD_DEW_TANK = builder.comment("Dew of the Void tank of the launch pad, in mB: loaded into the Void Satellite at launch and on resupply flights.")
            .defineInRange("dewTankCapacity", 8_000, 1, 1_000_000);
        FUEL_PER_TRANSFER_CHUNK = builder.comment("Orbit transfer: extra rocket fuel per chunk the satellite moves, in mB (on top of the",
                "satellite tier's returnFuel).")
            .defineInRange("fuelPerTransferChunk", 100, 0, 100_000);
        ORBIT_ALTITUDE = builder.comment("How many blocks above the pad satellites orbit.")
            .defineInRange("orbitAltitude", 150, 24, 512);
        builder.pop();

        builder.push("quarry_controller");
        builder.comment("The controller's capacitor (level L: Basic 1, Double-Layer 2, Octadic 3, Endergy up to 6) sets what the quarry",
            "moves per tick. The dish capacitors set how fast it mines.");
        CONTROLLER_OUTPUT_PER_PORT = builder.comment("Items per tick each Laser Quarry I/O Port can take out of the controller, times L².")
            .defineInRange("outputPerPort", 16, 1, 1_000_000);
        CONTROLLER_FLUID_PER_PORT = builder.comment("mB per tick each Laser Quarry I/O Port can fill into the controller tanks, times L².")
            .defineInRange("fluidPerPort", 100, 1, 1_000_000);
        CONTROLLER_ENERGY_INTAKE = builder.comment("µI per tick the controller takes in through all its ports together, times L². It powers the controller (the",
                "dishes have their own ports; the launch pad gets power from it over the antenna link, see antennaPowerRate).")
            .defineInRange("energyIntake", 60_000, 1, 10_000_000);
        CONTROLLER_BUFFER_TICKS = builder.comment("Energy buffer = this many ticks of intake.")
            .defineInRange("bufferTicks", 20, 1, 1_000);
        CONTROLLER_USAGE = builder.comment("µI/t the controller itself uses while it runs (powering up or mining), times L.")
            .defineInRange("usage", 40, 0, Integer.MAX_VALUE);
        CONTROLLER_ANTENNA_POWER = builder.comment("Max µI/t the controller sends over its antenna link to the linked launch pad, for the Ground Radar there",
                "(uplink, space scans, Analyze). Only what the pad has room for, and only what the controller doesn't need itself. 0 = off:",
                "then the launch pad is powered through its own I/O ports only.")
            .defineInRange("antennaPowerRate", 10_000, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.push("chunk_loading");
        LOAD_BASE = builder.comment("A running quarry keeps itself loaded, so it works on while nobody is near: the chunks of the controller and",
                "obelisk, the linked dishes and the launch pad (the chunk being mined is always loaded while the laser works).",
                "Off: the quarry only runs while a player keeps its base loaded.")
            .define("loadBase", true);
        PROBLEM_TIMEOUT = builder.comment("Seconds a running quarry may be stuck on a problem (no power, output full, no coolant...) before it lets its",
                "chunks go. It holds them again once it works (a player nearby loads it and the problem is fixed).")
            .defineInRange("problemTimeout", 300, 10, 86_400);
        builder.pop();

        builder.push("claims");
        CLAIM_CHECK_MODE = builder.comment("How often the laser asks claim / protection mods (as the controller's owner) before it breaks a block.",
                "per_section: once per 16x16x16 section, again every 5 seconds. A chunk claimed while it is mined stops within a few seconds.",
                "per_block: before every block. Strictest, but every mod listening for block breaks runs for each mined block.")
            .defineInList("claimCheckMode", "per_section", Arrays.asList("per_section", "per_block"));
        builder.pop();

        builder.push("ground_radar");
        RADAR_CAPACITY = builder.comment("Energy buffer of the launch pad, it powers the ground radar on its ring. Filled by the linked quarry controller over its antenna",
                "and through the pad's own I/O ports.")
            .defineInRange("capacity", 100_000, 1, Integer.MAX_VALUE);
        RADAR_MAX_INPUT = builder.comment("Max µI/t (RF/t) the launch pad takes in through its I/O ports.")
            .defineInRange("maxInput", 50_000, 1, Integer.MAX_VALUE);
        RADAR_UPLINK_USAGE = builder.comment("µI/t (RF/t) the radar uses while it keeps an uplink to a satellite in orbit.")
            .defineInRange("uplinkUsage", 20, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.push("satellite");
        SCAN_TICKS_PER_CHUNK = builder.comment("Ticks needed to scan one chunk while the uplink is online. Once the area is mapped",
                "the satellite keeps re-imaging it at the same pace, so the minimap stays current.")
            .defineInRange("ticksPerChunk", 5, 1, 1200);
        builder.pop();

        builder.comment("Void Satellite (an upgraded Ender Satellite): with a Void Laser Crystal on the obelisk the quarry mines",
            "asteroids. It counts as an Ender satellite for the tier rules, maps no ground and stays over its launch pad.")
            .push("void_satellite");
        VOID_SATELLITE_LAUNCH_FUEL = builder.comment("Rocket fuel in mB to launch it.")
            .defineInRange("launchFuel", 8_000, 0, 1_000_000);
        VOID_SATELLITE_RETURN_FUEL = builder.comment("Rocket fuel in mB for a flight to it while it is up (recovery).")
            .defineInRange("returnFuel", 4_000, 0, 1_000_000);
        VOID_SATELLITE_DEW_TANK = builder.comment("Dew of the Void the satellite carries for space scans, in mB (filled at launch and by resupply flights).")
            .defineInRange("dewTank", 8_000, 1, 1_000_000);
        VOID_SCAN_DEW = builder.comment("Dew of the Void one space scan uses from the satellite, in mB (fluid tag ender_laser_quarry:space_scan_fluid).")
            .defineInRange("scanDew", 1_000, 0, 1_000_000);
        builder.pop();

        builder.comment("Laser crystals wear while mining: one durability point per this many blocks (solid blocks, voided ones too,",
            "and deleted liquids; air is free). At 1 durability a crystal stops working but never breaks. Unbreaking works like on",
            "tools; Mending repairs it from the controller's XP tank (2 durability per XP point, 20 mB liquid XP each).").push("crystal");
        PULSATING_BLOCKS_PER_POINT = builder.comment("Pulsating Laser Crystal (3,000 durability).")
            .defineInRange("pulsatingBlocksPerPoint", 100, 1, 1_000_000);
        LASER_BLOCKS_PER_POINT = builder.comment("Laser Crystal (7,500 durability).")
            .defineInRange("laserBlocksPerPoint", 100, 1, 1_000_000);
        VOID_BLOCKS_PER_POINT = builder.comment("Void Laser Crystal (26,000 durability, asteroid blocks).")
            .defineInRange("voidBlocksPerPoint", 100, 1, 1_000_000);
        CRYSTAL_WORN_PERCENT = builder.comment("The controller warns (\"Crystal worn\") at this much durability left, in percent.")
            .defineInRange("wornWarningPercent", 10, 0, 100);
        MENDING_XP_PER_SECOND = builder.comment("Most XP points Mending takes from the controller's tank per second.")
            .defineInRange("mendingXpPerSecond", 100, 1, 100_000);
        builder.pop();

        builder.push("coolant");
        COOLANT_ENERGY_PER_MB = builder.comment("Laser energy (µI) one mB of coolant takes away. Lower = thirstier quarry.")
            .defineInRange("energyPerMb", 20_000, 1, Integer.MAX_VALUE);
        UNCOOLED_WEAR_MULTIPLIER = builder.comment("With an empty coolant tank the quarry keeps running, but the crystal wears this many times faster.",
            "The controller GUI warning says 3x, so change that text too if you change this.")
            .defineInRange("uncooledWearMultiplier", 3, 1, 100);
        builder.pop();

        builder.comment("Void Laser Crystal (Ender controllers): the satellite scans space for asteroids and the quarry mines those",
            "instead of the ground. An asteroid is made of its biome's ground, measured in hidden copies of the dimensions when it is analyzed.")
            .push("void_crystal");
        VOID_ASTEROIDS = builder.comment("Asteroids a space scan finds (the field is filled up to this many).")
            .defineInRange("asteroids", 30, 1, 60);
        VOID_LARGE_CHANCE = builder.comment("Chance that a found asteroid is a large one.")
            .defineInRange("largeChance", 0.25, 0.0, 1.0);
        VOID_MEDIUM_CHANCE = builder.comment("Chance that a found asteroid is a medium one. The rest are small.")
            .defineInRange("mediumChance", 0.35, 0.0, 1.0);
        VOID_SMALL_CHUNKS = builder.comment("Size of a small asteroid, in chunks of blocks (see blocksPerChunk).")
            .defineInRange("smallChunks", 32, 1, 100_000);
        VOID_MEDIUM_CHUNKS = builder.comment("Size of a medium asteroid, in chunks of blocks.")
            .defineInRange("mediumChunks", 80, 1, 100_000);
        VOID_LARGE_CHUNKS = builder.comment("Size of a large asteroid, in chunks of blocks.")
            .defineInRange("largeChunks", 192, 1, 100_000);
        VOID_BLOCKS_PER_CHUNK = builder.comment("Blocks in one \"chunk of blocks\": 16 x 16 x 128, about what a lasered Overworld chunk gives.")
            .defineInRange("blocksPerChunk", 32_768, 1, 1_000_000);
        VOID_SCAN_TICKS = builder.comment("Ticks a space scan takes (1200 = 1 minute). Without radar power it pauses and goes on later.")
            .defineInRange("scanTicks", 1_200, 1, 72_000);
        VOID_SCAN_ENERGY = builder.comment("µI a space scan takes from the ground radar, spread over the scan (the launch pad powers the radar).")
            .defineInRange("scanEnergy", 10_000_000, 0, Integer.MAX_VALUE);
        VOID_MAX_QUEUED = builder.comment("Asteroids a controller can queue.")
            .defineInRange("maxQueued", 8, 1, 40);
        VOID_NETHER_CHANCE = builder.comment("Share of found asteroids that come from Nether biomes.")
            .defineInRange("netherChance", 0.15, 0.0, 1.0);
        VOID_END_CHANCE = builder.comment("Share of found asteroids that come from End biomes. The rest is Overworld (and modded dimensions).")
            .defineInRange("endChance", 0.15, 0.0, 1.0);
        VOID_ANALYZE_TICKS = builder.comment("Ticks analyzing an asteroid takes at least (longer while its biome is still being measured).")
            .defineInRange("analyzeTicks", 100, 1, 72_000);
        VOID_ANALYZE_ENERGY = builder.comment("µI analyzing an asteroid takes from the ground radar, spread over analyzeTicks.")
            .defineInRange("analyzeEnergy", 50_000, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.comment("Measuring biomes for asteroids: one counted chunk per far-apart spot of the biome, in a hidden copy of its",
            "dimension that never touches the disk.").push("sampling");
        SAMPLING_QUICK_SPOTS = builder.comment("Spots measured before an analysis finishes (the first asteroid of a biome waits for these).")
            .defineInRange("quickSpots", QUICK_SPOTS_DEFAULT, 1, 512);
        SAMPLING_FULL_SPOTS = builder.comment("Spots measured in the background afterwards, for a closer estimate.")
            .defineInRange("fullSpots", FULL_SPOTS_DEFAULT, 1, 1024);
        SAMPLING_REFINE = builder.comment("Keep measuring analyzed biomes in the background up to fullSpots.")
            .define("refine", true);
        SAMPLING_MAX_HELD = builder.comment("Chunks held for measuring at once (each spot holds 9).")
            .defineInRange("maxHeldChunks", 100, 9, 4096);
        SAMPLING_TICK_BUDGET = builder.comment("Main thread time per tick for counting chunks, in microseconds.")
            .defineInRange("tickBudgetMicros", 1_000, 100, 50_000);
        SAMPLING_PAUSE_MSPT = builder.comment("No new measuring while the server tick takes longer than this (ms).")
            .defineInRange("pauseAboveMspt", 40.0, 1.0, 1000.0);
        SAMPLING_MAX_PROBES = builder.comment("Biome lookups when searching spots of one biome, before it counts as not found.")
            .defineInRange("maxProbes", 400_000, 1_000, 10_000_000);
        builder.pop();

        SPEC = builder.build();
    }

    private QuarryConfig() {
    }
}
