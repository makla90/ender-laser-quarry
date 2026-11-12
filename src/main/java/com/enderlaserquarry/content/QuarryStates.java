package com.enderlaserquarry.content;

import com.enderio.enderio.foundation.state.MachineState;
import com.enderio.enderio.foundation.state.MachineStateType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * Our machine states for Ender IO's status widget. Equality is by identity, so keep them singletons.
 */
public final class QuarryStates {
    public static final MachineState NOT_IN_QUARRY = error("not_in_quarry");
    public static final MachineState DISH_INCOMPLETE = error("dish_incomplete");
    public static final MachineState ENDERGY_NEEDS_ENDER_DISH = error("endergy_needs_ender_dish");
    public static final MachineState CAPACITOR_UNSUPPORTED = error("capacitor_unsupported");
    public static final MachineState NO_LAUNCH_PAD = error("no_launch_pad");
    public static final MachineState PAD_OUT_OF_RANGE = error("pad_out_of_range");
    public static final MachineState TARGET_OUT_OF_RANGE = error("target_out_of_range");
    public static final MachineState NO_TARGET = new MachineState(MachineStateType.IDLE,
        Component.translatable("gui.ender_laser_quarry.status.no_target"));
    public static final MachineState POWERING_UP = new MachineState(MachineStateType.ACTIVE,
        Component.translatable("gui.ender_laser_quarry.status.powering_up"));
    public static final MachineState FINISHED = new MachineState(MachineStateType.IDLE,
        Component.translatable("gui.ender_laser_quarry.status.finished"));

    private static final Map<QuarryTier, MachineState> NEEDS_SATELLITE = new EnumMap<>(QuarryTier.class);

    static {
        for (QuarryTier tier : QuarryTier.values()) {
            NEEDS_SATELLITE.put(tier, new MachineState(MachineStateType.ERROR,
                Component.translatable("gui.ender_laser_quarry.status.needs_satellite", tier.displayName())));
        }
    }

    public static MachineState needsSatellite(QuarryTier tier) {
        return NEEDS_SATELLITE.get(tier);
    }

    public static Collection<MachineState> allNeedsSatellite() {
        return NEEDS_SATELLITE.values();
    }

    private static final Map<QuarryTier, MachineState> DISH_NEEDS_PLATING = new EnumMap<>(QuarryTier.class);
    private static final Map<QuarryTier, MachineState> QUARRY_NEEDS_PLATING = new EnumMap<>(QuarryTier.class);

    static {
        for (QuarryTier tier : QuarryTier.values()) {
            DISH_NEEDS_PLATING.put(tier, new MachineState(MachineStateType.ERROR,
                Component.translatable("gui.ender_laser_quarry.status.dish_needs_plating", tier.displayName())));
            QUARRY_NEEDS_PLATING.put(tier, new MachineState(MachineStateType.ERROR,
                Component.translatable("gui.ender_laser_quarry.status.quarry_needs_plating", tier.displayName())));
        }
    }

    public static MachineState dishNeedsPlating(QuarryTier tier) {
        return DISH_NEEDS_PLATING.get(tier);
    }

    public static MachineState quarryNeedsPlating(QuarryTier tier) {
        return QUARRY_NEEDS_PLATING.get(tier);
    }

    public static Collection<MachineState> allQuarryNeedsPlating() {
        return QUARRY_NEEDS_PLATING.values();
    }

    // Launch pad
    public static final MachineState PAD_INCOMPLETE = error("pad_incomplete");
    public static final MachineState NO_SKY = error("no_sky");
    public static final MachineState RADAR_NO_POWER = error("radar_no_power");

    // Satellite
    public static final MachineState NO_SATELLITE = error("no_satellite");
    public static final MachineState SATELLITE_OFFLINE = error("satellite_offline");
    // orbit transfer under way
    public static final MachineState SATELLITE_MOVING = new MachineState(MachineStateType.ACTIVE,
        Component.translatable("gui.ender_laser_quarry.status.satellite_moving"));
    public static final MachineState OBELISK_NO_SKY = error("obelisk_no_sky");

    // Crystal obelisk
    public static final MachineState OBELISK_INCOMPLETE = error("obelisk_incomplete");
    public static final MachineState NO_CRYSTAL = error("no_crystal");
    public static final MachineState CRYSTAL_BROKEN = error("crystal_broken");
    // warning only, mining goes on (Ender IO has no warning type, IDLE shows above ACTIVE)
    public static final MachineState CRYSTAL_WORN = new MachineState(MachineStateType.IDLE,
        Component.translatable("gui.ender_laser_quarry.status.crystal_worn").withStyle(ChatFormatting.YELLOW));
    public static final MachineState CRYSTAL_TIER_TOO_LOW = error("crystal_tier_too_low");
    public static final MachineState NO_DISHES = error("no_dishes");
    // warning only: no coolant, the crystal wears faster
    public static final MachineState RUNNING_HOT = new MachineState(MachineStateType.IDLE,
        Component.translatable("gui.ender_laser_quarry.status.running_hot").withStyle(ChatFormatting.YELLOW));
    // the controller capacitor caps port output / energy intake
    public static final MachineState OUTPUT_LIMITED = error("output_limited");
    public static final MachineState INTAKE_LIMITED = error("intake_limited");
    public static final MachineState NO_RADAR = error("no_radar");
    public static final MachineState NO_CONTROLLER = error("no_controller");

    // Void Laser Crystal
    public static final MachineState VOID_NEEDS_ENDER = error("void_needs_ender");
    public static final MachineState ASTEROID_UNMEASURED = error("asteroid_unmeasured");
    // the satellite in orbit decides between ground and asteroids
    public static final MachineState NEEDS_VOID_SATELLITE = error("needs_void_satellite");
    public static final MachineState NEEDS_VOID_CRYSTAL = error("needs_void_crystal");
    public static final MachineState VOID_SATELLITE_UP = error("void_satellite_up");
    // warning only: no space scans until a resupply, asteroid mining goes on
    public static final MachineState SATELLITE_NO_DEW = new MachineState(MachineStateType.IDLE,
        Component.translatable("gui.ender_laser_quarry.status.satellite_no_dew").withStyle(ChatFormatting.YELLOW));

    private static MachineState error(String key) {
        return new MachineState(MachineStateType.ERROR, Component.translatable("gui.ender_laser_quarry.status." + key));
    }

    private QuarryStates() {
    }
}
