package net.jenkimods.bioforge.client.render;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public final class ProtectiveGearModel extends HumanoidModel<LivingEntity> {
    public static final ModelLayerLocation THERMAL_BAG_LAYER = layer("thermal_bag");
    public static final ModelLayerLocation MEDICAL_MASK_LAYER = layer("medical_mask");
    public static final ModelLayerLocation PROTECTIVE_GLOVES_LAYER = layer("protective_gloves");

    public ProtectiveGearModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createThermalBagLayer() {
        MeshDefinition mesh = emptyHumanoidMesh();
        PartDefinition head = mesh.getRoot().getChild("head");
        PartDefinition bag = head.addOrReplaceChild("thermal_bag",
                CubeListBuilder.create(), PartPose.offset(0.0F, 0.25F, -0.5F));
        bag.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(0, 14)
                        .addBox(-2.0F, 1.25F, -1.0F, 3.0F, 3.0F, 3.0F,
                                new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(1.0F, -16.0F, -1.0F,
                        0.0F, 0.0F, -0.1745F));
        bag.addOrReplaceChild("bag", CubeListBuilder.create().texOffs(2, 2)
                        .addBox(-8.0F, -36.75F, -4.0F, 7.0F, 5.0F, 7.0F,
                                new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(3.0F, 24.0F, 0.0F,
                        0.0F, 0.0F, 0.0436F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    public static LayerDefinition createMedicalMaskLayer() {
        MeshDefinition mesh = emptyHumanoidMesh();
        PartDefinition head = mesh.getRoot().getChild("head");
        head.addOrReplaceChild("mask", CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-5.0F, -3.25F, -5.0F, 10.0F, 4.0F, 2.0F,
                                new CubeDeformation(0.0F))
                        .texOffs(0, 6)
                        .addBox(-4.5F, -5.0F, -4.0F, 0.0F, 5.0F, 8.0F,
                                new CubeDeformation(0.0F))
                        .texOffs(0, 6)
                        .addBox(4.5F, -5.0F, -4.0F, 0.0F, 5.0F, 8.0F,
                                new CubeDeformation(0.0F)),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    public static LayerDefinition createProtectiveGlovesLayer() {
        MeshDefinition mesh = emptyHumanoidMesh();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F,
                                new CubeDeformation(0.25F)),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F,
                                new CubeDeformation(0.25F)),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    private static MeshDefinition emptyHumanoidMesh() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(),
                PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(),
                PartPose.offset(1.9F, 12.0F, 0.0F));
        return mesh;
    }

    private static ModelLayerLocation layer(String path) {
        return new ModelLayerLocation(
                ResourceLocation.tryBuild(BioForge.MODID, path), "main");
    }
}
