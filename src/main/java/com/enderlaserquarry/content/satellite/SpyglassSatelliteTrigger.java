package com.enderlaserquarry.content.satellite;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Advancement trigger: a player looks at a satellite in orbit through a spyglass. */
public class SpyglassSatelliteTrigger extends SimpleCriterionTrigger<SpyglassSatelliteTrigger.Instance> {
    // satellite size in blocks for the aim check, and the smallest angle we accept
    private static final double TARGET_RADIUS = 5;
    private static final double MIN_ANGLE = 2 * Mth.DEG_TO_RAD;
    private static final double MAX_DISTANCE = 512;

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player) {
        trigger(player, instance -> true);
    }

    // every scoping player in the level whose view has the satellite near its centre
    public void checkPlayers(ServerLevel level, Vec3 satellite) {
        for (ServerPlayer player : level.players()) {
            if (player.isScoping() && isLookingAt(player.getEyePosition(), player.getLookAngle(), satellite)) {
                trigger(player);
            }
        }
    }

    public static boolean isLookingAt(Vec3 eye, Vec3 look, Vec3 target) {
        Vec3 toTarget = target.subtract(eye);
        double distance = toTarget.length();
        if (distance < 1 || distance > MAX_DISTANCE) {
            return false;
        }
        double angle = Math.acos(Mth.clamp(look.normalize().dot(toTarget.scale(1 / distance)), -1, 1));
        return angle <= Math.max(MIN_ANGLE, Math.atan(TARGET_RADIUS / distance));
    }

    public record Instance(Optional<ContextAwarePredicate> player) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player)
        ).apply(instance, Instance::new));
    }
}
