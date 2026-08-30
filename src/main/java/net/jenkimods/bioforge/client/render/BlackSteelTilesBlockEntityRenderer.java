package net.jenkimods.bioforge.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.world.decoration.BlackSteelTilesBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.LightLayer;

public final class BlackSteelTilesBlockEntityRenderer
        implements BlockEntityRenderer<BlackSteelTilesBlockEntity> {
    private static final float OFFSET = 0.001F;

    public BlackSteelTilesBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BlackSteelTilesBlockEntity blockEntity, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        VertexConsumer vertices = bufferSource.getBuffer(
                RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose pose = poseStack.last();
        for (Direction face : Direction.values()) {
            int variant = blockEntity.getVariant(face);
            if (variant == 0) continue;
            TextureAtlasSprite sprite = Minecraft.getInstance()
                    .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(texture(variant));
            int faceLight = packedLight;
            if (blockEntity.getLevel() != null) {
                var lightPos = blockEntity.getBlockPos().relative(face);
                faceLight = LightTexture.pack(
                        blockEntity.getLevel().getBrightness(LightLayer.BLOCK, lightPos),
                        blockEntity.getLevel().getBrightness(LightLayer.SKY, lightPos));
            }
            renderFace(vertices, pose, face, sprite, faceLight);
        }
    }

    private static ResourceLocation texture(int variant) {
        return ResourceLocation.fromNamespaceAndPath(BioForge.MODID,
                "block/black_steel_tiles_" + variant);
    }

    private static void renderFace(VertexConsumer vertices, PoseStack.Pose pose,
                                   Direction face, TextureAtlasSprite sprite, int light) {
        float min = -OFFSET;
        float max = 1.0F + OFFSET;
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        switch (face) {
            case DOWN -> quad(vertices, pose, light, face,
                    min, min, min, u0, v0, max, min, min, u1, v0,
                    max, min, max, u1, v1, min, min, max, u0, v1);
            case UP -> quad(vertices, pose, light, face,
                    min, max, max, u0, v1, max, max, max, u1, v1,
                    max, max, min, u1, v0, min, max, min, u0, v0);
            case NORTH -> quad(vertices, pose, light, face,
                    max, min, min, u0, v1, min, min, min, u1, v1,
                    min, max, min, u1, v0, max, max, min, u0, v0);
            case SOUTH -> quad(vertices, pose, light, face,
                    min, min, max, u0, v1, max, min, max, u1, v1,
                    max, max, max, u1, v0, min, max, max, u0, v0);
            case WEST -> quad(vertices, pose, light, face,
                    min, min, min, u0, v1, min, min, max, u1, v1,
                    min, max, max, u1, v0, min, max, min, u0, v0);
            case EAST -> quad(vertices, pose, light, face,
                    max, min, max, u0, v1, max, min, min, u1, v1,
                    max, max, min, u1, v0, max, max, max, u0, v0);
        }
    }

    private static void quad(VertexConsumer vertices, PoseStack.Pose pose,
                             int light, Direction face,
                             float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3,
                             float x4, float y4, float z4, float u4, float v4) {
        vertex(vertices, pose, light, face, x1, y1, z1, u1, v1);
        vertex(vertices, pose, light, face, x2, y2, z2, u2, v2);
        vertex(vertices, pose, light, face, x3, y3, z3, u3, v3);
        vertex(vertices, pose, light, face, x4, y4, z4, u4, v4);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose,
                               int light, Direction face, float x, float y, float z,
                               float u, float v) {
        vertices.addVertex(pose.pose(), x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, face.getStepX(), face.getStepY(), face.getStepZ());
    }
}
