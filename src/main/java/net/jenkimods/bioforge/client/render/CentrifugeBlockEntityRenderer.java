package net.jenkimods.bioforge.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.jenkimods.bioforge.block.CentrifugeBlock;
import net.jenkimods.bioforge.world.centrifuge.CentrifugeBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

public final class CentrifugeBlockEntityRenderer
        implements BlockEntityRenderer<CentrifugeBlockEntity> {
    private static final double[] SLOT_X = {
            0.5D, 0.276D, 0.724D, 0.796D,
            0.196D, 0.276D, 0.5D, 0.724D
    };
    private static final double[] SLOT_Z = {
            0.196D, 0.284D, 0.284D, 0.5D,
            0.5D, 0.716D, 0.796D, 0.716D
    };
    private static final float ROTATION_DEGREES_PER_TICK = 18.0F;
    private final ItemRenderer itemRenderer;
    private final Map<CentrifugeBlockEntity, RotorState> rotorStates = new WeakHashMap<>();

    public CentrifugeBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(CentrifugeBlockEntity blockEntity, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        Direction facing = blockEntity.getBlockState().getValue(CentrifugeBlock.FACING);
        RotorState rotor = rotorStates.computeIfAbsent(
                blockEntity, ignored -> new RotorState());
        double renderTime = blockEntity.getLevel() == null
                ? 0.0D
                : blockEntity.getLevel().getGameTime() + partialTick;
        if (!rotor.initialized) {
            rotor.lastRenderTime = renderTime;
            rotor.initialized = true;
        }
        double elapsed = Math.max(0.0D, Math.min(1.0D,
                renderTime - rotor.lastRenderTime));
        rotor.lastRenderTime = renderTime;
        if (blockEntity.isProcessing()) {
            rotor.angle = (float) ((rotor.angle
                    + ROTATION_DEGREES_PER_TICK * elapsed) % 360.0D);
        }

        poseStack.pushPose();
        MachineRenderTransforms.applyFacing(poseStack, facing);
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotor.angle));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        for (int slot = 0; slot < SLOT_X.length; slot++) {
            ItemStack stack = blockEntity.getItemHandler().getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            poseStack.pushPose();
            poseStack.translate(SLOT_X[slot], 0.585D, SLOT_Z[slot]);
            poseStack.mulPose(Axis.YP.rotationDegrees(slot * 45.0F));
            poseStack.scale(0.18F, 0.18F, 0.18F);
            itemRenderer.renderStatic(
                    stack,
                    ItemDisplayContext.FIXED,
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    poseStack,
                    bufferSource,
                    blockEntity.getLevel(),
                    (int) (blockEntity.getBlockPos().asLong() + slot)
            );
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static final class RotorState {
        private float angle;
        private double lastRenderTime;
        private boolean initialized;
    }
}
