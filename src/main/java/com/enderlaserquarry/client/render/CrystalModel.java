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
 * The floating crystal with three orbiting shards. Pixels, origin at the center. Texture 64x32: crystal (0,0),
 * shard (0,16).
 */
public final class CrystalModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EnderLaserQuarry.rl("laser_crystal"), "main");

    public final ModelPart root;
    public final ModelPart gem;
    public final ModelPart shards;

    public CrystalModel(ModelPart root) {
        this.root = root;
        this.gem = root.getChild("gem");
        this.shards = root.getChild("shards");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("gem", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-3, -1.5f, -3, 6, 3, 6)          // girdle
            .texOffs(0, 0).addBox(-2.5f, 1.5f, -2.5f, 5, 2, 5)     // crown, stepping in
            .texOffs(0, 0).addBox(-2, 3.5f, -2, 4, 2, 4)
            .texOffs(0, 0).addBox(-1.5f, 5.5f, -1.5f, 3, 1.5f, 3)
            .texOffs(0, 0).addBox(-1, 7, -1, 2, 1.5f, 2)
            .texOffs(0, 0).addBox(-0.5f, 8.5f, -0.5f, 1, 1, 1)     // tip
            .texOffs(0, 0).addBox(-2.5f, -3.5f, -2.5f, 5, 2, 5)    // pavilion, shorter
            .texOffs(0, 0).addBox(-1.5f, -5, -1.5f, 3, 1.5f, 3)
            .texOffs(0, 0).addBox(-0.5f, -6, -0.5f, 1, 1, 1),
            PartPose.rotation(0, Mth.PI / 4, 0));

        PartDefinition shards = root.addOrReplaceChild("shards", CubeListBuilder.create(), PartPose.ZERO);
        for (int i = 0; i < 3; i++) {
            float angle = i * Mth.TWO_PI / 3;
            shards.addOrReplaceChild("shard_" + i, CubeListBuilder.create()
                    .texOffs(0, 16).addBox(-0.5f, -1.5f, -0.5f, 1, 3, 1),
                PartPose.offsetAndRotation(Mth.cos(angle) * 7, (i - 1) * 1.5f, Mth.sin(angle) * 7, 0.3f, angle, 0.4f));
        }

        return LayerDefinition.create(mesh, 64, 32);
    }
}
