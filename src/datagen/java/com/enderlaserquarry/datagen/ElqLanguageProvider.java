package com.enderlaserquarry.datagen;

import com.enderlaserquarry.EnderLaserQuarry;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/** en_us. */
public class ElqLanguageProvider extends LanguageProvider {
    public ElqLanguageProvider(PackOutput output) {
        super(output, EnderLaserQuarry.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("advancements.ender_laser_quarry.spyglass_at_satellite.title", "Is It a Satellite?");
        add("advancements.ender_laser_quarry.spyglass_at_satellite.description", "Look at a satellite in orbit through a Spyglass");

        add("itemGroup.ender_laser_quarry", "Ender Laser Quarry");

        add("block.ender_laser_quarry.satellite_dish", "Basic Satellite Dish");

        add("block.ender_laser_quarry.enhanced_satellite_dish", "Enhanced Satellite Dish");

        add("block.ender_laser_quarry.ender_satellite_dish", "Ender Satellite Dish");

        add("tooltip.ender_laser_quarry.dish.speed", "%s / %s / %s blocks/t with a Basic / Double-Layer / Octadic Capacitor");
        add("tooltip.ender_laser_quarry.dish.endergy", "Takes Endergy capacitors: %s blocks/t with a Stellar Capacitor");
        add("tooltip.ender_laser_quarry.dish.no_endergy", "Endergy capacitors need an Ender Satellite Dish");
        add("tooltip.ender_laser_quarry.dish.range", "Reaches a Crystal Obelisk up to %s blocks away");
        add("tooltip.ender_laser_quarry.dish.controller", "Works with a %s Quarry Controller or better");
        add("tooltip.ender_laser_quarry.dish.plating", "Needs %s Satellite Dish Plating or better around it");

        add("message.ender_laser_quarry.linker.dish_tier_too_high", "A %s controller can't use %s dishes");

        add("block.ender_laser_quarry.quarry_controller", "Basic Quarry Controller");

        add("block.ender_laser_quarry.enhanced_quarry_controller", "Enhanced Quarry Controller");

        add("block.ender_laser_quarry.ender_quarry_controller", "Ender Quarry Controller");

        add("tier.ender_laser_quarry.basic", "Basic");

        add("tier.ender_laser_quarry.enhanced", "Enhanced");

        add("tier.ender_laser_quarry.ender", "Ender");

        add("tooltip.ender_laser_quarry.controller.stats", "Up to %s chunks, %s dishes, %s blocks/t");
        add("tooltip.ender_laser_quarry.controller.area", "Sees and lasers %sx%s chunks around the Launch Pad");
        add("tooltip.ender_laser_quarry.controller.link_range", "Launch Pad up to %s blocks away");
        add("tooltip.ender_laser_quarry.controller.satellite", "Needs a %s Satellite or better");
        add("tooltip.ender_laser_quarry.controller.plating", "Needs %s Laser Quarry Plating or better in the floor");

        add("gui.ender_laser_quarry.status.needs_satellite", "Needs a %s Satellite or better in orbit");

        add("item.ender_laser_quarry.laser_linker", "Laser Linker");

        add("block.ender_laser_quarry.launch_pad_controller", "Launch Pad Controller");

        add("item.ender_laser_quarry.parabolic_dish", "Parabolic Dish");

        add("item.ender_laser_quarry.rocket_engine", "Rocket Engine");

        add("item.ender_laser_quarry.focusing_lens", "Focusing Lens");

        add("item.ender_laser_quarry.laser_emitter", "Laser Emitter");

        add("item.ender_laser_quarry.signal_transceiver", "Signal Transceiver");

        add("item.ender_laser_quarry.reflector_mirror", "Reflector Mirror");

        add("block.ender_laser_quarry.launch_pad_plating", "Launch Pad Plating");

        add("block.ender_laser_quarry.ground_radar", "Ground Radar");

        add("item.ender_laser_quarry.rocket", "Rocket");

        add("item.ender_laser_quarry.satellite", "Basic Satellite");

        add("item.ender_laser_quarry.enhanced_satellite", "Enhanced Satellite");

        add("item.ender_laser_quarry.ender_satellite", "Ender Satellite");

        add("tooltip.ender_laser_quarry.satellite.area", "Maps %sx%s chunks around its Launch Pad");

        add("block.ender_laser_quarry.crystal_obelisk", "Crystal Obelisk");

        add("block.ender_laser_quarry.laser_quarry_plating", "Laser Quarry Plating");

        add("block.ender_laser_quarry.launch_pad_port", "Launch Pad I/O Port");

        add("block.ender_laser_quarry.laser_quarry_port", "Laser Quarry I/O Port");

        add("block.ender_laser_quarry.enhanced_laser_quarry_plating", "Enhanced Laser Quarry Plating");

        add("block.ender_laser_quarry.ender_laser_quarry_plating", "Ender Laser Quarry Plating");

        add("block.ender_laser_quarry.satellite_dish_plating", "Satellite Dish Plating");

        add("block.ender_laser_quarry.satellite_dish_port", "Satellite Dish I/O Port");

        add("block.ender_laser_quarry.enhanced_satellite_dish_plating", "Enhanced Satellite Dish Plating");

        add("block.ender_laser_quarry.ender_satellite_dish_plating", "Ender Satellite Dish Plating");

        add("tooltip.ender_laser_quarry.port.launch_pad", "Replaces a Launch Pad Plating on the outer ring: pipe in Rocket Fuel, rockets and satellites. Yeta Wrench a side to set it: none / push / pull / both / disabled");
        add("tooltip.ender_laser_quarry.port.laser_quarry", "Replaces a Laser Quarry Plating of any tier (it takes that plating's look): power the quarry (the Controller, which feeds the Ground Radar; each Satellite Dish is powered through its own Satellite Dish I/O Port), get the mined items out (the only way: the controller has no IO). Yeta Wrench a side to set it: none / push / pull / both / disabled");
        add("tooltip.ender_laser_quarry.port.satellite_dish", "Replaces a Satellite Dish Plating of any tier (it takes that plating's look) around a dish: the only way to power the dish. Yeta Wrench a side to set it: none / push / pull / both / disabled");

        add("item.ender_laser_quarry.pulsating_laser_crystal", "Pulsating Laser Crystal");

        add("tooltip.ender_laser_quarry.pulsating_laser_crystal.tier", "Only powers a Basic Quarry Controller");

        add("item.ender_laser_quarry.laser_crystal", "Laser Crystal");

        add("tooltip.ender_laser_quarry.laser_crystal", "Combines the lasers of several Satellite Dishes on a Crystal Obelisk");
        add("tooltip.ender_laser_quarry.laser_crystal.enchantable", "Enchant it with Silk Touch or Fortune: the laser mines with it");
        add("tooltip.ender_laser_quarry.laser_crystal.enchant_cost", "Enchanted: the laser uses %sx energy per block");

        add("item.ender_laser_quarry.void_laser_crystal", "Void Laser Crystal");

        add("tooltip.ender_laser_quarry.void_laser_crystal", "On a Crystal Obelisk, the satellite scans space for asteroids and the quarry mines those instead of the ground");
        add("tooltip.ender_laser_quarry.void_laser_crystal.tier", "Needs an Ender Quarry Controller");

        add("tooltip.ender_laser_quarry.rocket", "Reusable. Flies satellites to orbit and back from a Launch Pad");

        add("tooltip.ender_laser_quarry.satellite", "In orbit it maps the chunks around its Launch Pad and bounces the quarry laser down onto them");
        add("tooltip.ender_laser_quarry.satellite.launch", "Launch it from a Launch Pad with a Rocket");

        add("message.ender_laser_quarry.launch.busy", "A flight is already underway");
        add("message.ender_laser_quarry.launch.incomplete", "Finish the 5x5 Launch Pad: Launch Pad Plating in the middle 3x3, the controller on the outer ring");
        add("message.ender_laser_quarry.launch.no_sky", "The middle of the Launch Pad needs open sky above it");
        add("message.ender_laser_quarry.launch.no_rocket", "Insert a Rocket");
        add("message.ender_laser_quarry.launch.no_satellite", "Insert a satellite to launch");
        add("message.ender_laser_quarry.launch.satellite_slot_full", "Empty the satellite slot to recover the satellite");
        add("message.ender_laser_quarry.launch.no_fuel", "Not enough Rocket Fuel (needs %s mB)");
        add("message.ender_laser_quarry.launch.transfer_no_satellite", "No satellite in orbit to move");
        add("message.ender_laser_quarry.launch.transfer_tier", "A %s Satellite stays over its Launch Pad");
        add("message.ender_laser_quarry.launch.transfer_too_far", "Too far: this satellite can go at most %s chunks from its Launch Pad");
        add("message.ender_laser_quarry.launch.transfer_same", "The satellite is already over that area");
        add("message.ender_laser_quarry.launch.transfer_stop_quarry", "Stop the quarry before moving its satellite");

        add("message.ender_laser_quarry.transfer.arrived", "Satellite in position over X %s, Z %s: mapping %sx%s chunks");

        add("message.ender_laser_quarry.radar.linked", "Part of the quarry at %s, %s, %s | %s / %s µI");
        add("message.ender_laser_quarry.radar.unlinked", "Not on a quarry ring | %s / %s µI");

        add("gui.ender_laser_quarry.status.pad_incomplete", "Needs a 5x5 Launch Pad: plating in the middle 3x3, plating or I/O ports on the outer ring with this controller");
        add("gui.ender_laser_quarry.status.no_radar", "Needs a Ground Radar on the outer ring");
        add("gui.ender_laser_quarry.status.no_sky", "No open sky above the pad");
        add("gui.ender_laser_quarry.status.radar_no_power", "Ground Radar has no power");
        add("gui.ender_laser_quarry.status.no_satellite", "No satellite in orbit");
        add("gui.ender_laser_quarry.status.satellite_offline", "Satellite has no radar uplink");
        add("gui.ender_laser_quarry.status.satellite_moving", "Satellite is changing orbit");
        add("gui.ender_laser_quarry.status.obelisk_no_sky", "Laser needs open sky to reach the satellite (nothing above the obelisk)");
        add("gui.ender_laser_quarry.status.obelisk_incomplete", "Needs a full 5x5 of Laser Quarry Plating under the obelisk, with one Controller and one Ground Radar on its outer ring");
        add("gui.ender_laser_quarry.status.quarry_needs_plating", "This controller needs %s Laser Quarry Plating or better in the whole floor (I/O Ports fit any tier)");
        add("gui.ender_laser_quarry.status.no_crystal", "No Laser Crystal on the obelisk");
        add("gui.ender_laser_quarry.status.crystal_tier_too_low", "Crystal too weak: a Pulsating crystal only runs a Basic quarry");
        add("gui.ender_laser_quarry.status.powering_up", "Powering up: dishes charging the crystal");

        add("gui.ender_laser_quarry.energy.stored", "%s / %s µI");
        add("gui.ender_laser_quarry.energy.usage", "Using %s µI/t");
        add("gui.ender_laser_quarry.energy.max_usage", "Max %s µI/t (capacitor)");

        add("gui.ender_laser_quarry.controller.usage.controller", "  Controller: %s µI/t");
        add("gui.ender_laser_quarry.controller.usage.radar", "  Ground Radar: %s µI/t");
        add("gui.ender_laser_quarry.controller.intake", "Capacitor: takes in up to %s µI/t");
        add("gui.ender_laser_quarry.controller.port_output", "Capacitor: %s items/t out of each I/O port");

        add("gui.ender_laser_quarry.status.no_dishes", "No working Satellite Dish feeds the crystal");
        add("gui.ender_laser_quarry.status.running_hot", "Running hot: no coolant, the crystal wears 3x faster. Pipe water into a Laser Quarry I/O Port");
        add("gui.ender_laser_quarry.status.no_controller", "Needs a Quarry Controller on the outer ring");
        add("gui.ender_laser_quarry.status.void_needs_ender", "A Void Laser Crystal needs an Ender Quarry Controller");
        add("gui.ender_laser_quarry.status.asteroid_unmeasured", "Analyze this asteroid first (no rock data)");

        add("gui.ender_laser_quarry.launch_pad.pad.ready", "Pad: Ready");
        add("gui.ender_laser_quarry.launch_pad.pad.incomplete", "Pad: Incomplete");
        add("gui.ender_laser_quarry.launch_pad.pad.no_sky", "Pad: No sky");
        add("gui.ender_laser_quarry.launch_pad.radar", "Radar: %s%%");
        add("gui.ender_laser_quarry.launch_pad.radar_missing", "Radar: no quarry");
        add("gui.ender_laser_quarry.launch_pad.uplink.no_satellite", "Uplink: Standby");
        add("gui.ender_laser_quarry.launch_pad.uplink.no_radar", "Uplink: No radar");
        add("gui.ender_laser_quarry.launch_pad.uplink.no_power", "Uplink: No power");
        add("gui.ender_laser_quarry.launch_pad.uplink.online", "Uplink: Online");
        add("gui.ender_laser_quarry.launch_pad.uplink.no_station", "Uplink: No quarry");
        add("gui.ender_laser_quarry.launch_pad.sat_ground", "Sat: On ground");
        add("gui.ender_laser_quarry.launch_pad.sat_orbit", "Sat: In orbit");
        add("gui.ender_laser_quarry.launch_pad.countdown", "T-%s");
        add("gui.ender_laser_quarry.launch_pad.ascent", "Ascent %s%%");
        add("gui.ender_laser_quarry.launch_pad.deploying", "Deploying sat");
        add("gui.ender_laser_quarry.launch_pad.docking", "Docking sat");
        add("gui.ender_laser_quarry.launch_pad.fuelling", "Fuelling sat");
        add("gui.ender_laser_quarry.launch_pad.orbit_offset", "%s, %s chunks from this pad");
        add("gui.ender_laser_quarry.launch_pad.orbit_hint", "Move it in the Quarry Controller's Orbit planner");
        add("gui.ender_laser_quarry.launch_pad.landing", "Landing %s%%");
        add("gui.ender_laser_quarry.launch_pad.scan", "Scan: %s/%s");
        add("gui.ender_laser_quarry.launch_pad.reflector.lasering", "Laser: Reflecting");
        add("gui.ender_laser_quarry.launch_pad.launch", "Launch");
        add("gui.ender_laser_quarry.launch_pad.recover", "Recover Satellite");
        add("gui.ender_laser_quarry.launch_pad.in_flight", "In flight...");
        add("gui.ender_laser_quarry.launch_pad.rocket_slot", "Rocket");
        add("gui.ender_laser_quarry.launch_pad.satellite_slot", "Satellite");
        add("gui.ender_laser_quarry.launch_pad.fuel_cost", "Burns %s mB Rocket Fuel");

        add("tooltip.ender_laser_quarry.linker.usage", "Use on a Satellite Dish, Crystal Obelisk, Quarry Controller or Launch Pad Controller, then on the block to link it to");
        add("tooltip.ender_laser_quarry.linker.source", "Linking from %s, %s, %s");

        add("message.ender_laser_quarry.linker.stored.dish", "%s selected. Now use the linker on the Crystal Obelisk it should beam into.");
        add("message.ender_laser_quarry.linker.stored.obelisk", "%s selected. Now use the linker on a Satellite Dish.");
        add("message.ender_laser_quarry.linker.stored.launch_pad", "%s selected. Now use the linker on the Quarry Controller of your quarry.");
        add("message.ender_laser_quarry.linker.stored.controller", "%s selected. Now use the linker on a Launch Pad Controller.");
        add("message.ender_laser_quarry.linker.cleared", "Linker cleared");
        add("message.ender_laser_quarry.linker.other_dimension", "Both blocks must be in the same dimension");
        add("message.ender_laser_quarry.linker.source_missing", "The selected block is gone or not loaded");
        add("message.ender_laser_quarry.linker.invalid_pair", "These two can't be linked");
        add("message.ender_laser_quarry.linker.too_far", "Out of range (max %s blocks)");
        add("message.ender_laser_quarry.linker.dish_obelisk", "Satellite Dish now beams into the crystal");
        add("message.ender_laser_quarry.linker.controller_pad", "Quarry Controller linked to the Launch Pad: its satellite reflects the laser and maps the ground");
        add("message.ender_laser_quarry.linker.obelisk_pad", "Link the Quarry Controller to the Launch Pad instead");
        add("message.ender_laser_quarry.linker.dish_controller", "Link the dish to the Crystal Obelisk, not the Controller");
        add("message.ender_laser_quarry.linker.dish_needs_obelisk", "A dish can't reach the satellite alone: link it to a Crystal Obelisk, and the Quarry Controller to the Launch Pad");
        add("message.ender_laser_quarry.linker.controller_on_ring", "No link needed: place the Controller on the obelisk's outer ring");
        add("message.ender_laser_quarry.linker.too_many_dishes", "A %2$s controller combines at most %1$s dishes");

        add("message.ender_laser_quarry.controller.stop_first", "Stop the quarry before changing the chunk queue");
        add("message.ender_laser_quarry.controller.depth_stop_first", "Stop the quarry before changing the depth mode");
        add("message.ender_laser_quarry.controller.depth_needs_tier", "Depth modes need an Enhanced or Ender controller");
        add("message.ender_laser_quarry.controller.floor_needs_ender", "Only Ender controllers can pick their own Y");
        add("message.ender_laser_quarry.controller.no_void_crystal", "Needs a Void Laser Crystal on the obelisk");
        add("message.ender_laser_quarry.controller.asteroid_queue_full", "Asteroid queue is full (max %s). Click a queued asteroid to remove it");
        add("message.ender_laser_quarry.controller.asteroid_gone", "That asteroid is gone");
        add("message.ender_laser_quarry.controller.analyze_first", "Analyze that asteroid before mining it");
        add("message.ender_laser_quarry.controller.analyze_again", "The mods changed since this asteroid was analyzed: analyze it again");
        add("message.ender_laser_quarry.controller.analyze_busy", "Already analyzing another asteroid");
        add("message.ender_laser_quarry.controller.analyze_scan_running", "Wait for the space scan to finish");
        add("message.ender_laser_quarry.controller.scan_analyze_running", "Wait for the analysis to finish");
        add("message.ender_laser_quarry.controller.queue_full", "Queue is full (max %s chunks). Click a queued chunk to remove it");
        add("message.ender_laser_quarry.controller.box_too_big", "That box is too big: the queue holds at most %s chunks");
        add("message.ender_laser_quarry.controller.not_scanned", "The satellite hasn't mapped that chunk yet");
        add("message.ender_laser_quarry.controller.chunk_claimed", "That chunk is claimed by another player");
        add("message.ender_laser_quarry.controller.box_claimed", "Left out %s chunks claimed by other players");
        add("message.ender_laser_quarry.controller.chunk_claimed_skipped", "Laser Quarry: chunk [%s, %s] is claimed by another player now, skipped it");
        add("message.ender_laser_quarry.controller.no_scanner", "Link this controller to a Launch Pad Controller (Laser Linker) and put its satellite in orbit");

        add("gui.ender_laser_quarry.obelisk.status", "Status: %s");
        add("gui.ender_laser_quarry.obelisk.dishes", "Dishes: %s in use, %s linked");
        add("gui.ender_laser_quarry.obelisk.satellite", "Satellite: %s");
        add("gui.ender_laser_quarry.obelisk.crystal_hint", "Put a Laser Crystal here");
        add("gui.ender_laser_quarry.obelisk.enchant_cost", "%sx µI/block");

        add("message.ender_laser_quarry.obelisk.ready", "Ready");
        add("message.ender_laser_quarry.obelisk.linked", "linked");
        add("message.ender_laser_quarry.obelisk.unlinked", "none");

        add("gui.ender_laser_quarry.status.not_in_quarry", "Not part of a Laser Quarry (place it on the outer ring of the floor)");
        add("gui.ender_laser_quarry.status.dish_incomplete", "Dish needs 8 Satellite Dish Plating (or Satellite Dish I/O Ports) around it");
        add("gui.ender_laser_quarry.status.endergy_needs_ender_dish", "Endergy capacitors only work in an Ender Satellite Dish");
        add("gui.ender_laser_quarry.status.dish_needs_plating", "Needs %s Satellite Dish Plating or better all around the dish (I/O Ports fit any tier)");
        add("gui.ender_laser_quarry.status.output_limited", "Output limited by capacitor: add an I/O port or a better capacitor");
        add("gui.ender_laser_quarry.status.intake_limited", "Power intake limited by capacitor");
        add("gui.ender_laser_quarry.status.crystal_broken", "Crystal broken: repair it (anvil or Mending)");
        add("gui.ender_laser_quarry.status.crystal_worn", "Crystal worn: repair it soon (Mending or an anvil)");

        add("tooltip.ender_laser_quarry.laser_crystal.broken", "Broken: stops working until repaired");
        add("tooltip.ender_laser_quarry.laser_crystal.worn", "Worn: repair it soon");
        add("tooltip.ender_laser_quarry.laser_crystal.wear", "Wears 1 durability per %s blocks mined. Repair: anvil, or Mending with liquid XP in the controller");

        add("gui.ender_laser_quarry.obelisk.durability", "Durability: %s / %s");

        add("gui.ender_laser_quarry.status.no_launch_pad", "Controller not linked to a Launch Pad");
        add("gui.ender_laser_quarry.status.pad_out_of_range", "Launch Pad out of the controller's range");
        add("gui.ender_laser_quarry.status.target_out_of_range", "Chunk out of the satellite's range");
        add("gui.ender_laser_quarry.status.no_target", "No chunk selected");
        add("gui.ender_laser_quarry.status.finished", "All queued chunks done");

        add("gui.ender_laser_quarry.dish.obelisk", "Beams into:");
        add("gui.ender_laser_quarry.dish.not_linked", "Not linked");
        add("gui.ender_laser_quarry.dish.formed", "Frame: complete");
        add("gui.ender_laser_quarry.dish.incomplete", "Frame: incomplete");
        add("gui.ender_laser_quarry.dish.beaming", "Laser: BEAMING");
        add("gui.ender_laser_quarry.dish.idle", "Laser: idle");
        add("gui.ender_laser_quarry.dish.wrong_capacitor", "Capacitor won't work!");
        add("gui.ender_laser_quarry.dish.wrong_capacitor_hint", "Needs an Ender dish");

        add("message.ender_laser_quarry.dish.wrong_capacitor", "This capacitor only works in an Ender Satellite Dish");

        add("gui.ender_laser_quarry.controller.start", "Start");
        add("gui.ender_laser_quarry.controller.stop", "Stop");
        add("gui.ender_laser_quarry.controller.refresh", "Refresh");
        add("gui.ender_laser_quarry.controller.clear", "Clear");

        add("gui.ender_laser_quarry.depth", "Depth: %s");
        add("gui.ender_laser_quarry.depth.bedrock", "Bedrock");
        add("gui.ender_laser_quarry.depth.bedrock.desc", "Everything down to bedrock. Liquids are drained and walled off");
        add("gui.ender_laser_quarry.depth.y_level", "Down to Y %s");
        add("gui.ender_laser_quarry.depth.y_level.desc", "Stops at Y %s, where deepslate starts (bedrock outside the Overworld)");
        add("gui.ender_laser_quarry.depth.y_level.desc.ender", "Stops at Y %s, in every dimension");
        add("gui.ender_laser_quarry.depth.surface", "Surface only");
        add("gui.ender_laser_quarry.depth.surface.desc", "Only what stands on the natural ground: trees, buildings. The ground and all liquids stay");
        add("gui.ender_laser_quarry.depth.click", "Click: %s");
        add("gui.ender_laser_quarry.depth.stop_first", "Stop the quarry to change it");
        add("gui.ender_laser_quarry.depth.needs_tier", "Enhanced and Ender controllers only");
        add("gui.ender_laser_quarry.depth.floor_hint", "Arrows or scroll: change the Y (Shift: 10 at a time)");

        add("gui.ender_laser_quarry.controller.scan_space", "Scan");
        add("gui.ender_laser_quarry.controller.asteroid_line", "%s asteroids, %s blocks/t");
        add("gui.ender_laser_quarry.controller.space_scan", "Scanning space... %s%%");
        add("gui.ender_laser_quarry.controller.space_scan_waiting", "Scan waits: radar / uplink");
        add("gui.ender_laser_quarry.controller.space_scan_hint", "No asteroids yet: Scan");
        add("gui.ender_laser_quarry.controller.pick_asteroid", "Click asteroids to queue");
        add("gui.ender_laser_quarry.controller.mining_asteroid", "Asteroid %s%% left | %s");
        add("gui.ender_laser_quarry.controller.asteroids_selected", "%s queued: press Start");
        add("gui.ender_laser_quarry.controller.asteroid_hint", "Click: queue / unqueue | Scroll: zoom | Right-drag: pan");
        add("gui.ender_laser_quarry.controller.analyze_hint", "Click to pick it, then press Analyze");
        add("gui.ender_laser_quarry.controller.analyze", "Analyze");

        add("gui.ender_laser_quarry.orbit.button", "Orbit");
        add("gui.ender_laser_quarry.orbit.back", "Map");
        add("gui.ender_laser_quarry.orbit.launch", "Launch");
        add("gui.ender_laser_quarry.orbit.coords", "X %s, Z %s");
        add("gui.ender_laser_quarry.orbit.over", "Orbit %s");
        add("gui.ender_laser_quarry.orbit.fuelling", "Fuelling satellite... %s%%");
        add("gui.ender_laser_quarry.orbit.moving", "Changing orbit... %s%%");
        add("gui.ender_laser_quarry.orbit.no_satellite", "No satellite in orbit");
        add("gui.ender_laser_quarry.orbit.target", "To %s (%s mB)");
        add("gui.ender_laser_quarry.orbit.hint", "Click a spot to move it");
        add("gui.ender_laser_quarry.orbit.title", "Orbit planner");
        add("gui.ender_laser_quarry.orbit.what", "Fly your satellite over another area to mine there");
        add("gui.ender_laser_quarry.orbit.back_tooltip", "Back to the mining map");
        add("gui.ender_laser_quarry.orbit.basic", "A Basic Satellite stays over its Launch Pad");
        add("gui.ender_laser_quarry.orbit.reach", "Can move up to %s chunks from its Launch Pad");
        add("gui.ender_laser_quarry.orbit.launch_tooltip", "Fly the satellite over to %s");
        add("gui.ender_laser_quarry.orbit.fuel", "Rocket Fuel: %s mB (pad has %s mB)");
        add("gui.ender_laser_quarry.orbit.needs_rocket", "Needs a Rocket on the Launch Pad");
        add("gui.ender_laser_quarry.orbit.spot", "Area centre %s");
        add("gui.ender_laser_quarry.orbit.chunk", "Chunk %s, %s");
        add("gui.ender_laser_quarry.orbit.here", "The satellite is over this spot");
        add("gui.ender_laser_quarry.orbit.too_far", "Out of reach: at most %s chunks from the Launch Pad");
        add("gui.ender_laser_quarry.orbit.distance", "%s chunks to fly");
        add("gui.ender_laser_quarry.orbit.click", "Click to pick this spot");

        add("gui.ender_laser_quarry.controller.analyzing", "Analyzing asteroid %s%%");
        add("gui.ender_laser_quarry.controller.analyze_waiting", "Analysis waits: radar power");

        add("gui.ender_laser_quarry.asteroid.small", "Small %s asteroid");
        add("gui.ender_laser_quarry.asteroid.medium", "Medium %s asteroid");
        add("gui.ender_laser_quarry.asteroid.large", "Large %s asteroid");
        add("gui.ender_laser_quarry.asteroid.remaining", "%s / %s blocks left (%s%%)");
        add("gui.ender_laser_quarry.asteroid.no_ores", "No ores");
        add("gui.ender_laser_quarry.asteroid.unknown", "Unknown composition: analyze it first");
        add("gui.ender_laser_quarry.asteroid.stale", "Mods changed since the analysis: analyze it again");
        add("gui.ender_laser_quarry.asteroid.survey", "Detailed analysis complete: %s sites measured");
        add("gui.ender_laser_quarry.asteroid.survey_running", "Detailed analysis: %s of %s sites measured (still refining)");
        add("gui.ender_laser_quarry.asteroid.more_ores", "+%s more ores (Shift)");
        add("gui.ender_laser_quarry.asteroid.more_rocks", "+%s more rock types (Ctrl)");
        add("gui.ender_laser_quarry.asteroid.ppm_note", "ppm = blocks per million");

        add("gui.ender_laser_quarry.controller.void_filter", "Void Filter");
        add("gui.ender_laser_quarry.controller.void_filter.hint", "Put an Ender IO item filter here. Deny list: those items are voided. Allow list: only those are kept");
        add("gui.ender_laser_quarry.controller.queue_line", "Chunk %s/%s | %s blocks/t");
        add("gui.ender_laser_quarry.controller.queued", "Queued #%s (click to remove)");
        add("gui.ender_laser_quarry.controller.queued_done", "Mined out (#%s)");
        add("gui.ender_laser_quarry.controller.loading_map", "Scanning...");
        add("gui.ender_laser_quarry.controller.link_hint", "Build it into a Laser Quarry");
        add("gui.ender_laser_quarry.controller.crystal_too_weak", "Crystal too weak for tier");
        add("gui.ender_laser_quarry.controller.pick_chunk", "Pick chunks: click or drag");
        add("gui.ender_laser_quarry.controller.map_hint", "Drag: box (de)select | Scroll: zoom | Right-drag: pan");
        add("gui.ender_laser_quarry.controller.selected", "%s chunks | %s mined");
        add("gui.ender_laser_quarry.controller.locking_on", "Locking on %s, %s...");
        add("gui.ender_laser_quarry.controller.mining", "[%s,%s] Y %s | %s");
        add("gui.ender_laser_quarry.controller.walling", "[%1$s,%2$s] Wall Y %3$s");
        add("gui.ender_laser_quarry.controller.chunk_tooltip", "Chunk %s, %s (from %s, %s)");
        add("gui.ender_laser_quarry.controller.chunk_unscanned", "Chunk %s, %s: not scanned yet");
        add("gui.ender_laser_quarry.controller.scan_hint", "No map: launch a satellite");
    }
}
