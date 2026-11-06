package com.enderlaserquarry.init;

import com.enderlaserquarry.EnderLaserQuarry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.TicketType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTickets {
    public static final DeferredRegister<TicketType> TICKETS = DeferredRegister.create(Registries.TICKET_TYPE, EnderLaserQuarry.MOD_ID);

    // brief loads while the satellite maps new ground
    public static final DeferredHolder<TicketType, TicketType> SCAN = TICKETS.register("scan",
        () -> new TicketType(100L, TicketType.FLAG_LOADING));

    // sampling patches, held until counted
    public static final DeferredHolder<TicketType, TicketType> SAMPLING = TICKETS.register("sampling",
        () -> new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING));

    private ModTickets() {
    }
}
