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
 * Rocket model. Pixels, origin at the nozzle (bottom center). The fairing halves swing open in orbit.
 * <p>
 * Texture regions (128x128): white body top half, black stripe (0,64), orange fins/rings (64,64), dark steel (0,80).
 */
public final class RocketModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EnderLaserQuarry.rl("rocket"), "main");

    public static final float FAIRING_BASE = 61;

    public final ModelPart root;
    public final ModelPart fairingLeft;
    public final ModelPart fairingRight;

    public RocketModel(ModelPart root) {
        this.root = root;
        this.fairingLeft = root.getChild("fairing_left");
        this.fairingRight = root.getChild("fairing_right");
    }

    // 0 = closed, 1 = open
    public void setFairingOpen(float open) {
        float angle = open * 110 * Mth.DEG_TO_RAD;
        fairingLeft.zRot = angle;
        fairingRight.zRot = -angle;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("body", CubeListBuilder.create()
            .texOffs(0, 80).addBox(-4, 0, -4, 8, 4, 8)                 // engine bell
            .texOffs(32, 80).addBox(-3, 4, -3, 6, 2, 6)                // engine mount
            .texOffs(0, 0).addBox(-6, 6, -6, 12, 24, 12)               // lower tank
            .texOffs(0, 64).addBox(-6.2f, 30, -6.2f, 12.4f, 3, 12.4f)  // black stripe
            .texOffs(48, 0).addBox(-6, 33, -6, 12, 26, 12)             // upper tank
            .texOffs(64, 64).addBox(-6.2f, 59, -6.2f, 12.4f, 2, 12.4f), // interstage ring
            PartPose.ZERO);

        // fins
        for (int i = 0; i < 4; i++) {
            root.addOrReplaceChild("fin_" + i, CubeListBuilder.create()
                .texOffs(64, 80).addBox(-0.5f, 2, 6, 1, 18, 8)
                .texOffs(84, 80).addBox(-0.5f, 20, 6, 1, 6, 4), PartPose.rotation(0, i * Mth.HALF_PI, 0));
        }

        // fairing halves with the nose cone
        root.addOrReplaceChild("fairing_left", CubeListBuilder.create()
            .texOffs(0, 36).addBox(0, 0, -6.5f, 6.5f, 12, 13)
            .texOffs(0, 92).addBox(1.5f, 12, -5, 5, 5, 10)
            .texOffs(0, 108).addBox(3, 17, -3.5f, 3.5f, 4, 7)
            .texOffs(28, 108).addBox(4.5f, 21, -2, 2, 3, 4)
            .texOffs(44, 108).addBox(5.5f, 24, -1, 1, 3, 2), PartPose.offset(-6.5f, FAIRING_BASE, 0));
        root.addOrReplaceChild("fairing_right", CubeListBuilder.create()
            .texOffs(0, 36).addBox(-6.5f, 0, -6.5f, 6.5f, 12, 13)
            .texOffs(0, 92).addBox(-6.5f, 12, -5, 5, 5, 10)
            .texOffs(0, 108).addBox(-6.5f, 17, -3.5f, 3.5f, 4, 7)
            .texOffs(28, 108).addBox(-6.5f, 21, -2, 2, 3, 4)
            .texOffs(44, 108).addBox(-6.5f, 24, -1, 1, 3, 2), PartPose.offset(6.5f, FAIRING_BASE, 0));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
