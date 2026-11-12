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
 * Satellite model. Pixels, origin at the bus center, Y up, +Z forward. The mirror is drawn separately so it can turn
 * towards the target.
 * <p>
 * Texture regions (128x128): bus (0,0), gimbal (48,0), deck (0,20), gold band (0,30), nozzles (44,30), mast (56,30),
 * red beacon (60,30), star tracker (44,34), wing arms (44,40), antenna dish (0,44), solar panels (0,56), mirror back
 * (0,72), mirror rim (0,86) + (16,86), mirror strut (32,86), green lens (96,32), mirror face (64,72), panel backs (0,96),
 * radiators (64,96), white for tinted lights (96,96).
 */
public final class SatelliteModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EnderLaserQuarry.rl("satellite"), "main");

    // must match LaunchPadBlockEntity.REFLECTOR_MIRROR_DROP
    public static final float MIRROR_DROP = 9;
    public static final float LENS_Y = -4.5f;
    public static final float LENS_Z = 6;
    public static final int RING_SEGMENTS = 8;
    // thruster nozzle centres (x, z), bottom at y -5
    public static final float[][] NOZZLES = { {-4, -4}, {4, -4}, {-4, 4}, {4, 4} };

    private static final float PANEL = 12;
    // not quite 180 so the folded panels don't z-fight
    private static final float OUTER_FOLD = 175 * Mth.DEG_TO_RAD;
    private static final float DISH_TILT = 35 * Mth.DEG_TO_RAD;

    public final ModelPart root;
    public final ModelPart wingLeft;
    public final ModelPart wingRight;
    public final ModelPart panelLeft;
    public final ModelPart panelRight;
    public final ModelPart outerLeft;
    public final ModelPart outerRight;
    public final ModelPart antenna;
    // Enhanced and Ender
    public final ModelPart antenna2;
    public final ModelPart radiatorFront;
    public final ModelPart radiatorBack;
    // glowing parts, drawn separately
    public final ModelPart beacon;
    public final ModelPart scanner;
    public final ModelPart navLeft;
    public final ModelPart navRight;
    // Void only: two end rods on the top deck, drawn glowing
    public final ModelPart endRods;
    // Ender only
    public final ModelPart ring;
    public final ModelPart[] ringSegments = new ModelPart[RING_SEGMENTS];
    public final ModelPart mirror;
    public final ModelPart mirrorSurface;

    public SatelliteModel(ModelPart root) {
        this.root = root;
        this.wingLeft = root.getChild("wing_left");
        this.wingRight = root.getChild("wing_right");
        this.panelLeft = wingLeft.getChild("panel");
        this.panelRight = wingRight.getChild("panel");
        this.outerLeft = panelLeft.getChild("outer");
        this.outerRight = panelRight.getChild("outer");
        this.antenna = root.getChild("antenna");
        this.antenna2 = root.getChild("antenna2");
        this.radiatorFront = root.getChild("radiator_front");
        this.radiatorBack = root.getChild("radiator_back");
        this.beacon = root.getChild("beacon");
        this.scanner = root.getChild("scanner");
        this.navLeft = outerLeft.getChild("nav");
        this.navRight = outerRight.getChild("nav");
        this.endRods = root.getChild("end_rods");
        this.ring = root.getChild("ring");
        for (int i = 0; i < RING_SEGMENTS; i++) {
            ringSegments[i] = ring.getChild("segment" + i);
        }
        this.mirror = root.getChild("mirror");
        this.mirrorSurface = mirror.getChild("surface");
    }

    // deploy 0 = folded, 1 = spread
    public void setWings(float deploy, float sunTilt) {
        float fold = (1 - deploy) * 90 * Mth.DEG_TO_RAD;
        // outer panels unfold a bit later
        float outer = (1 - Mth.clamp(deploy * 1.4f - 0.4f, 0, 1)) * OUTER_FOLD;
        wingLeft.zRot = fold;
        wingRight.zRot = -fold;
        outerLeft.zRot = -outer;
        outerRight.zRot = outer;
        panelLeft.xRot = sunTilt * deploy;
        panelRight.xRot = sunTilt * deploy;
        // radiators fold up against the bus for the ride
        float radiator = (1 - deploy) * 90 * Mth.DEG_TO_RAD;
        radiatorFront.xRot = -radiator;
        radiatorBack.xRot = radiator;
        // the ring only opens out in orbit, it wouldn't fit the fairing
        float ringScale = Mth.clamp(deploy * 1.5f - 0.5f, 0, 1);
        ring.xScale = ringScale;
        ring.zScale = ringScale;
    }

    // dishes sweep back and forth, the ring turns (time in ticks)
    public void animate(float time) {
        antenna.yRot = 0.9f * Mth.sin(time * 0.02f);
        antenna2.yRot = -0.9f * Mth.sin(time * 0.017f + 1);
        ring.yRot = time * 0.012f;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("bus", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-5, -4, -5, 10, 8, 10)                     // bus
            .texOffs(0, 30).addBox(-5.25f, -1, -5.25f, 10.5f, 2, 10.5f)      // gold insulation band
            .texOffs(0, 20).addBox(-4, 4, -4, 8, 1, 8)                       // top deck
            .texOffs(56, 30).addBox(-0.5f, 5, -0.5f, 1, 5, 1)                // beacon mast
            .texOffs(44, 34).addBox(5, 1, -1, 2, 2, 2)                       // star tracker
            .texOffs(44, 40).addBox(-9, -0.5f, -0.5f, 4, 1, 1)               // wing arms
            .texOffs(44, 40).addBox(5, -0.5f, -0.5f, 4, 1, 1)
            .texOffs(44, 30).addBox(-5, -5, -5, 2, 1, 2)                     // thruster nozzles
            .texOffs(44, 30).addBox(3, -5, -5, 2, 1, 2)
            .texOffs(44, 30).addBox(-5, -5, 3, 2, 1, 2)
            .texOffs(44, 30).addBox(3, -5, 3, 2, 1, 2)
            .texOffs(48, 0).addBox(-3, -6, -3, 6, 2, 6),                     // gimbal ring the mirror hangs from
            PartPose.ZERO);

        root.addOrReplaceChild("antenna", CubeListBuilder.create()
            .texOffs(56, 30).addBox(-0.5f, 0, -0.5f, 1, 4, 1)               // boom
            .texOffs(0, 44).addBox(-4, 4, -4, 8, 1, 8)                       // dish
            .texOffs(56, 30).addBox(-0.5f, 5, -0.5f, 1, 3, 1),               // feed
            PartPose.offsetAndRotation(0, 5, -3, -DISH_TILT, 0, 0));
        root.addOrReplaceChild("antenna2", CubeListBuilder.create()
            .texOffs(56, 30).addBox(-0.5f, 0, -0.5f, 1, 3, 1)
            .texOffs(0, 44).addBox(-3, 3, -3, 6, 1, 6)
            .texOffs(56, 30).addBox(-0.5f, 4, -0.5f, 1, 2, 1),
            PartPose.offsetAndRotation(0, 5, 3, DISH_TILT, 0, 0));

        // radiators, hinged at the bus
        root.addOrReplaceChild("radiator_front", CubeListBuilder.create()
            .texOffs(64, 96).addBox(-4, -0.25f, 0, 8, 0.5f, 6), PartPose.offset(0, 2, 5));
        root.addOrReplaceChild("radiator_back", CubeListBuilder.create()
            .texOffs(64, 96).addBox(-4, -0.25f, -6, 8, 0.5f, 6), PartPose.offset(0, 2, -5));

        // solar wings: cells on top, a truss back below
        PartDefinition left = root.addOrReplaceChild("wing_left", CubeListBuilder.create(), PartPose.offset(-9, 0, 0));
        left.addOrReplaceChild("panel", CubeListBuilder.create()
            .texOffs(0, 56).addBox(-PANEL, -0.25f, -6, PANEL, 0.5f, 12)
            .texOffs(0, 96).addBox(-PANEL, -0.75f, -6, PANEL, 0.25f, 12), PartPose.ZERO)
            .addOrReplaceChild("outer", CubeListBuilder.create()
                .texOffs(0, 56).addBox(-PANEL, -0.25f, -6, PANEL, 0.5f, 12)
                .texOffs(0, 96).addBox(-PANEL, -0.75f, -6, PANEL, 0.25f, 12), PartPose.offset(-PANEL - 0.5f, 0, 0))
            .addOrReplaceChild("nav", CubeListBuilder.create()
                .texOffs(96, 96).addBox(-PANEL - 1.5f, -0.75f, -0.75f, 1.5f, 1.5f, 1.5f), PartPose.ZERO);

        PartDefinition right = root.addOrReplaceChild("wing_right", CubeListBuilder.create(), PartPose.offset(9, 0, 0));
        right.addOrReplaceChild("panel", CubeListBuilder.create()
            .texOffs(0, 56).addBox(0, -0.25f, -6, PANEL, 0.5f, 12)
            .texOffs(0, 96).addBox(0, -0.75f, -6, PANEL, 0.25f, 12), PartPose.ZERO)
            .addOrReplaceChild("outer", CubeListBuilder.create()
                .texOffs(0, 56).addBox(0, -0.25f, -6, PANEL, 0.5f, 12)
                .texOffs(0, 96).addBox(0, -0.75f, -6, PANEL, 0.25f, 12), PartPose.offset(PANEL + 0.5f, 0, 0))
            .addOrReplaceChild("nav", CubeListBuilder.create()
                .texOffs(96, 96).addBox(PANEL, -0.75f, -0.75f, 1.5f, 1.5f, 1.5f), PartPose.ZERO);

        root.addOrReplaceChild("beacon", CubeListBuilder.create()
            .texOffs(60, 30).addBox(-1, 10, -1, 2, 2, 2), PartPose.ZERO);
        root.addOrReplaceChild("scanner", CubeListBuilder.create()
            .texOffs(96, 32).addBox(-2, LENS_Y, 5, 4, 2, 2), PartPose.ZERO);

        // Ender: a ring of glowing segments around the bus, below the wing arms
        PartDefinition ring = root.addOrReplaceChild("ring", CubeListBuilder.create(), PartPose.offset(0, -2.5f, 0));
        for (int i = 0; i < RING_SEGMENTS; i++) {
            ring.addOrReplaceChild("segment" + i, CubeListBuilder.create()
                .texOffs(96, 100).addBox(-3.4f, -0.5f, 7.2f, 6.8f, 1, 1), PartPose.rotation(0, i * Mth.TWO_PI / RING_SEGMENTS, 0));
        }

        // Void: end rods on the deck, a little foot and a long glowing rod
        root.addOrReplaceChild("end_rods", CubeListBuilder.create()
            .texOffs(96, 96).addBox(-3.75f, 5, -0.75f, 1.5f, 0.5f, 1.5f)
            .texOffs(96, 96).addBox(-3.5f, 5.5f, -0.5f, 1, 7, 1)
            .texOffs(96, 96).addBox(2.25f, 5, -0.75f, 1.5f, 0.5f, 1.5f)
            .texOffs(96, 96).addBox(2.5f, 5.5f, -0.5f, 1, 7, 1), PartPose.ZERO);

        // mirror, face towards -Y
        PartDefinition mirror = root.addOrReplaceChild("mirror", CubeListBuilder.create()
            .texOffs(0, 72).addBox(-6, 0, -6, 12, 1, 12)         // back plate
            .texOffs(0, 86).addBox(-7, 0, -3, 1, 1, 6)           // rim, rounding off the corners
            .texOffs(0, 86).addBox(6, 0, -3, 1, 1, 6)
            .texOffs(16, 86).addBox(-3, 0, -7, 6, 1, 1)
            .texOffs(16, 86).addBox(-3, 0, 6, 6, 1, 1)
            .texOffs(32, 86).addBox(-1, 1, -1, 2, 3, 2),         // strut up to the gimbal
            PartPose.ZERO);
        mirror.addOrReplaceChild("surface", CubeListBuilder.create()
            .texOffs(64, 72).addBox(-5.5f, -0.4f, -5.5f, 11, 0.4f, 11), PartPose.ZERO);

        return LayerDefinition.create(mesh, 128, 128);
    }
}
