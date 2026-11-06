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
 * The 3D dish. Pixels, Y up, origin at the top center of the block, bowl axis is +Z.
 * <pre>
 * root
 *  ├ mount               static obelisk on the block top
 *  └ yoke                turns (yaw)
 *     └ bowl             tilts (pitch), pivot 24px above the block top
 *        ├ panels, struts, feed horn
 *        └ emitter       spinning crystal at the focus, drawn separately so it can glow
 * </pre>
 * Texture regions (128x128): dark steel top band, gunmetal left, white dish panels right, white emitter glow bottom.
 */
public final class SatelliteDishModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EnderLaserQuarry.rl("satellite_dish"), "main");

    private static final int INNER_PANELS = 8;
    private static final int OUTER_PANELS = 16;
    private static final int STRUTS = 3;

    public final ModelPart root;
    public final ModelPart yoke;
    public final ModelPart bowl;
    public final ModelPart emitter;
    public final ModelPart[] outerPanels = new ModelPart[OUTER_PANELS];

    public SatelliteDishModel(ModelPart root) {
        this.root = root;
        this.yoke = root.getChild("yoke");
        this.bowl = yoke.getChild("bowl");
        this.emitter = bowl.getChild("emitter");
        for (int i = 0; i < OUTER_PANELS; i++) {
            outerPanels[i] = bowl.getChild("outer_" + i);
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("mount", CubeListBuilder.create()
            .texOffs(0, 32).addBox(-6, 0, -6, 12, 4, 12), PartPose.ZERO);

        // turntable and fork
        PartDefinition yoke = root.addOrReplaceChild("yoke", CubeListBuilder.create()
            .texOffs(0, 47).addBox(-6, 0, -6, 12, 2, 12)
            .texOffs(48, 32).addBox(-7, 2, -1.5f, 2, 19, 3)
            .texOffs(48, 32).addBox(5, 2, -1.5f, 2, 19, 3), PartPose.offset(0, 4, 0));

        PartDefinition bowl = yoke.addOrReplaceChild("bowl", CubeListBuilder.create()
            .texOffs(22, 61).addBox(-6, -1, -1, 12, 2, 2)
            .texOffs(0, 61).addBox(-3, -3, -1, 6, 6, 5)
            .texOffs(64, 32).addBox(-4, -4, 4, 8, 8, 1), PartPose.offset(0, 20, 0));

        // two rings of tilted panels
        for (int i = 0; i < INNER_PANELS; i++) {
            bowl.addOrReplaceChild("inner_" + i, CubeListBuilder.create(), PartPose.rotation(0, 0, i * Mth.TWO_PI / INNER_PANELS))
                .addOrReplaceChild("panel", CubeListBuilder.create().texOffs(64, 41).addBox(-3, 0, -0.5f, 6, 9, 1),
                    PartPose.offsetAndRotation(0, 3, 4.5f, 15 * Mth.DEG_TO_RAD, 0, 0));
        }
        for (int i = 0; i < OUTER_PANELS; i++) {
            bowl.addOrReplaceChild("outer_" + i, CubeListBuilder.create(), PartPose.rotation(0, 0, (i + 0.5f) * Mth.TWO_PI / OUTER_PANELS))
                .addOrReplaceChild("panel", CubeListBuilder.create().texOffs(80, 41).addBox(-4, 0, -0.5f, 8, 9, 1),
                    PartPose.offsetAndRotation(0, 11.5f, 6.8f, 32 * Mth.DEG_TO_RAD, 0, 0));
        }

        for (int i = 0; i < STRUTS; i++) {
            bowl.addOrReplaceChild("strut_" + i, CubeListBuilder.create(), PartPose.rotation(0, 0, i * Mth.TWO_PI / STRUTS))
                .addOrReplaceChild("beam", CubeListBuilder.create().texOffs(0, 72).addBox(-0.5f, -0.5f, 0, 1, 1, 20),
                    PartPose.offsetAndRotation(0, 18, 11, 63.4f * Mth.DEG_TO_RAD, 0, 0));
        }
        bowl.addOrReplaceChild("feed", CubeListBuilder.create().texOffs(42, 72).addBox(-2, -2, 19, 4, 4, 3), PartPose.ZERO);

        // tip at z = 24, see SatelliteDishBlockEntity.FOCUS_DISTANCE
        bowl.addOrReplaceChild("emitter", CubeListBuilder.create()
            .texOffs(0, 96).addBox(-1, -1, 22, 2, 2, 2)
            .texOffs(16, 96).addBox(-1.5f, -0.5f, 21.5f, 3, 1, 1)
            .texOffs(16, 96).addBox(-0.5f, -1.5f, 21.5f, 1, 3, 1), PartPose.ZERO);

        return LayerDefinition.create(mesh, 128, 128);
    }
}
