package net.jenkimods.bioforge.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.block.MicroscopeBlock;
import net.jenkimods.bioforge.world.microscope.MicroscopeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.Map;
import java.util.WeakHashMap;

public final class MicroscopeBlockEntityRenderer
        implements BlockEntityRenderer<MicroscopeBlockEntity> {
    public static final ModelResourceLocation KNOB_MODEL = model("microscope_knob");
    public static final ModelResourceLocation LENS_WHEEL_MODEL = model("microscope_lens_wheel");
    public static final ModelResourceLocation BULB_MODEL = model("microscope_bulb");

    private static final double KNOB_X = 6.0D / 16.0D;
    private static final double KNOB_Y = 5.0D / 16.0D;
    private static final double KNOB_Z = 13.65D / 16.0D;
    private static final double LENS_X = 8.1D / 16.0D;
    private static final double LENS_Y = 14.25D / 16.0D;
    private static final double LENS_Z = 6.75D / 16.0D;
    private static final double BULB_X = 8.0D / 16.0D;
    private static final double BULB_Y = 2.9D / 16.0D;
    private static final double BULB_Z = 7.95D / 16.0D;

    private final BlockRenderDispatcher blockRenderer;
    private final ItemRenderer itemRenderer;
    private final Map<MicroscopeBlockEntity, VisualState> visualStates = new WeakHashMap<>();

    public MicroscopeBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        blockRenderer = context.getBlockRenderDispatcher();
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(MicroscopeBlockEntity blockEntity, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        Direction facing = blockEntity.getBlockState().getValue(MicroscopeBlock.FACING);
        ItemStack stack = blockEntity.getItemHandler().getStackInSlot(0);
        VisualState visual = visualStates.computeIfAbsent(
                blockEntity,
                ignored -> new VisualState(
                        blockEntity.getVisualKnobAngle(),
                        blockEntity.getVisualLensAngle())
        );
        visual.knobAngle = Mth.lerp(
                0.2F, visual.knobAngle, blockEntity.getVisualKnobAngle());
        visual.lensAngle = Mth.rotLerp(
                0.16F, visual.lensAngle, blockEntity.getVisualLensAngle());

        poseStack.pushPose();
        MachineRenderTransforms.applyFacing(poseStack, facing);

        poseStack.pushPose();
        rotateAround(poseStack, KNOB_X, KNOB_Y, KNOB_Z,
                Axis.XP.rotationDegrees(visual.knobAngle));
        renderPart(blockEntity, KNOB_MODEL, poseStack, bufferSource,
                packedLight, packedOverlay);
        poseStack.popPose();

        poseStack.pushPose();
        rotateAround(poseStack, LENS_X, LENS_Y, LENS_Z,
                Axis.YP.rotationDegrees(visual.lensAngle));
        renderPart(blockEntity, LENS_WHEEL_MODEL, poseStack, bufferSource,
                packedLight, packedOverlay);
        poseStack.popPose();

        if (!stack.isEmpty()) {
            renderItem(blockEntity, stack, poseStack, bufferSource, packedLight);
        }

        poseStack.pushPose();
        if (!stack.isEmpty()) {
            poseStack.translate(BULB_X, BULB_Y, BULB_Z);
            poseStack.scale(1.04F, 1.04F, 1.04F);
            poseStack.translate(-BULB_X, -BULB_Y, -BULB_Z);
        }
        renderPart(blockEntity, BULB_MODEL, poseStack, bufferSource,
                stack.isEmpty() ? packedLight : LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        poseStack.popPose();
    }

    private void renderItem(MicroscopeBlockEntity blockEntity, ItemStack stack,
                            PoseStack poseStack, MultiBufferSource bufferSource,
                            int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 8.4D / 16.0D, 0.5D);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.scale(0.34F, 0.34F, 0.34F);
        itemRenderer.renderStatic(
                stack,
                ItemDisplayContext.FIXED,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                blockEntity.getLevel(),
                (int) blockEntity.getBlockPos().asLong()
        );
        poseStack.popPose();
    }

    private void renderPart(MicroscopeBlockEntity blockEntity,
                            ModelResourceLocation modelLocation,
                            PoseStack poseStack, MultiBufferSource bufferSource,
                            int packedLight, int packedOverlay) {
        BakedModel model = Minecraft.getInstance()
                .getModelManager().getModel(modelLocation);
        VertexConsumer vertices = bufferSource.getBuffer(RenderType.translucent());
        blockRenderer.getModelRenderer().renderModel(
                poseStack.last(),
                vertices,
                blockEntity.getBlockState(),
                model,
                1.0F,
                1.0F,
                1.0F,
                packedLight,
                packedOverlay,
                ModelData.EMPTY,
                RenderType.translucent()
        );
    }

    private static void rotateAround(PoseStack poseStack,
                                     double x, double y, double z,
                                     org.joml.Quaternionf rotation) {
        poseStack.translate(x, y, z);
        poseStack.mulPose(rotation);
        poseStack.translate(-x, -y, -z);
    }

    private static ModelResourceLocation model(String name) {
        return new ModelResourceLocation(
                ResourceLocation.fromNamespaceAndPath(BioForge.MODID, "block/" + name),
                "standalone");
    }

    private static final class VisualState {
        private float knobAngle;
        private float lensAngle;

        private VisualState(float knobAngle, float lensAngle) {
            this.knobAngle = knobAngle;
            this.lensAngle = lensAngle;
        }
    }
}
