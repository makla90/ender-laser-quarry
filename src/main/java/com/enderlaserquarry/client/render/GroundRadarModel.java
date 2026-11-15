package com.enderlaserquarry.client.render;

import com.enderlaserquarry.EnderLaserQuarry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Radar antenna. Pixels, origin at the top center of the block.
 * <pre>
 * root
 *  ├ mast
 *  └ turret        turns (yaw), at y 5 (short mast)
 *     └ antenna    tilts (pitch), pivot 11px above the block (GroundRadarBlockEntity.ANTENNA_PIVOT); axis +Z
 *        └ horn    glowing feed horn, tip at z 11 (see GroundRadarBlockEntity.HORN_DISTANCE)
 * </pre>
 * The reflector is a curved array of slats, concave towards +Z.
 * Texture regions (64x64): gunmetal (0,0), antenna slats (0,32), glow (48,48).
 */
public final class GroundRadarModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EnderLaserQuarry.rl("ground_radar"), "main");

    private static final int SLATS = 5;
    private static final float SLAT_SPACING = 22;
    private static final float CURVE_RADIUS = 10;

    public final ModelPart root;
    public final ModelPart turret;
    public final ModelPart antenna;
    public final ModelPart horn;

    public GroundRadarModel(ModelPart root) {
        this.root = root;
        this.turret = root.getChild("turret");
        this.antenna = turret.getChild("antenna");
        this.horn = antenna.getChild("horn");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("mast", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-2, 0, -2, 4, 5, 4), PartPose.ZERO);

        PartDefinition turret = root.addOrReplaceChild("turret", CubeListBuilder.create()
            .texOffs(16, 0).addBox(-3, 0, -3, 6, 2, 6)
            .texOffs(40, 0).addBox(-4, 2, -1, 1, 5, 2)
            .texOffs(40, 0).addBox(3, 2, -1, 1, 5, 2), PartPose.offset(0, 5, 0));

        PartDefinition antenna = turret.addOrReplaceChild("antenna", CubeListBuilder.create()
            .texOffs(0, 12).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3)   // hub
            .texOffs(12, 12).addBox(-3, -0.5f, -0.5f, 6, 1, 1)      // elevation axle
            .texOffs(24, 12).addBox(-0.5f, -0.5f, 2, 1, 1, 7),      // feed arm
            PartPose.offset(0, 6, 0));

        for (int i = 0; i < SLATS; i++) {
            float angle = (i - (SLATS - 1) / 2f) * SLAT_SPACING * Mth.DEG_TO_RAD;
            antenna.addOrReplaceChild("slat_" + i, CubeListBuilder.create()
                    .texOffs(0, 32).addBox(-2.5f, -6, -CURVE_RADIUS, 5, 12, 1),
                PartPose.offsetAndRotation(0, 0, CURVE_RADIUS + 1, 0, angle, 0));
        }

        antenna.addOrReplaceChild("horn", CubeListBuilder.create()
            .texOffs(48, 48).addBox(-1, -1, 9, 2, 2, 2), PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }
}
