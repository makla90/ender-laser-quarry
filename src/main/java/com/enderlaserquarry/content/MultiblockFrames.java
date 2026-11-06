package com.enderlaserquarry.content;

import com.enderlaserquarry.content.dish.SatelliteDishBlockEntity;
import com.enderlaserquarry.content.launchpad.LaunchPadBlockEntity;
import com.enderlaserquarry.content.obelisk.CrystalObeliskBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Finds the core (dish, launch pad controller or obelisk) of a frame block.
 */
public final class MultiblockFrames {

    @Nullable
    public static BlockEntity findCore(BlockGetter level, BlockPos framePos) {
        BlockState state = level.getBlockState(framePos);
        boolean dishFrame = state.is(SatelliteDishBlockEntity.FRAME_BLOCKS);
        boolean padFrame = state.is(LaunchPadBlockEntity.FRAME_BLOCKS);
        boolean obeliskFrame = state.is(CrystalObeliskBlockEntity.FRAME_BLOCKS);
        if (!dishFrame && !padFrame && !obeliskFrame) {
            return null;
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockEntity be = level.getBlockEntity(framePos.offset(dx, 0, dz));
                if (dishFrame && be instanceof SatelliteDishBlockEntity dish && dish.isFormed()) {
                    return dish;
                }
            }
        }

        if (padFrame) {
            // controller is on the ring, so up to 4 away
            int radius = 2 * LaunchPadBlockEntity.RADIUS;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (level.getBlockEntity(framePos.offset(dx, 0, dz)) instanceof LaunchPadBlockEntity pad) {
                        BlockPos center = pad.getPadCenter();
                        if (Math.max(Math.abs(center.getX() - framePos.getX()), Math.abs(center.getZ() - framePos.getZ()))
                            <= LaunchPadBlockEntity.RADIUS) {
                            return pad;
                        }
                    }
                }
            }
        }

        if (obeliskFrame) {
            int radius = CrystalObeliskBlockEntity.RADIUS;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // obelisk is one layer up
                    if (level.getBlockEntity(framePos.offset(dx, 1, dz)) instanceof CrystalObeliskBlockEntity obelisk) {
                        return obelisk;
                    }
                }
            }
        }
        return null;
    }

    private MultiblockFrames() {
    }
}
