package net.jenkimods.bioforge.client;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.world.laboratory.LaboratoryProcessorMenu;
import net.jenkimods.bioforge.world.laboratory.LaboratoryStation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class LaboratoryProcessorScreen
        extends AbstractContainerScreen<LaboratoryProcessorMenu> {
    private static final int TEXTURE_SIZE = 256;

    public LaboratoryProcessorScreen(LaboratoryProcessorMenu menu,
                                     Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 72;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick,
                            int mouseX, int mouseY) {
        ResourceLocation texture = texture();
        GuiRenderCompat.prepare(texture);
        graphics.blit(texture, leftPos, topPos, 0, 0,
                imageWidth, imageHeight, TEXTURE_SIZE, TEXTURE_SIZE);
        switch (menu.station()) {
            case BARREL_PRESS -> renderVerticalProgress(graphics, texture,
                    104, 31, 18, 22);
            case CHEMICAL_SYNTHESIZER -> renderHorizontalProgress(graphics, texture,
                    93, 38, 24, 11);
            case PHARMA_MIXER -> renderVerticalProgress(graphics, texture,
                    109, 25, 4, 39);
            case STERILIZATION_CHAMBER -> renderVerticalProgress(graphics, texture,
                    120, 25, 5, 34);
        }
    }

    private ResourceLocation texture() {
        return ResourceLocation.fromNamespaceAndPath(BioForge.MODID,
                "textures/gui/laboratory/" + menu.station().getSerializedName() + ".png");
    }

    private void renderHorizontalProgress(GuiGraphics graphics, ResourceLocation texture,
                                          int x, int y, int width, int height) {
        graphics.blit(texture, leftPos + x, topPos + y,
                176, 0, width, height, TEXTURE_SIZE, TEXTURE_SIZE);
        int progress = menu.scaledProgress(width);
        if (progress > 0) {
            graphics.blit(texture, leftPos + x, topPos + y,
                    176 + width, 0, progress, height,
                    TEXTURE_SIZE, TEXTURE_SIZE);
        }
    }

    private void renderVerticalProgress(GuiGraphics graphics, ResourceLocation texture,
                                        int x, int y, int width, int height) {
        graphics.blit(texture, leftPos + x, topPos + y,
                176, 0, width, height, TEXTURE_SIZE, TEXTURE_SIZE);
        int progress = menu.scaledProgress(height);
        if (progress > 0) {
            int offset = height - progress;
            graphics.blit(texture, leftPos + x, topPos + y + offset,
                    176 + width, offset, width, progress,
                    TEXTURE_SIZE, TEXTURE_SIZE);
        }
    }

    private int accentColor() {
        return switch (menu.station()) {
            case BARREL_PRESS -> 0xFFC98A3B;
            case CHEMICAL_SYNTHESIZER -> 0xFF4FD8E8;
            case PHARMA_MIXER -> 0xFFD67BEA;
            case STERILIZATION_CHAMBER -> 0xFF70D9A7;
        };
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int color = accentColor() & 0xFFFFFF;
        graphics.drawString(font, title, titleLabelX, titleLabelY, color, false);
        graphics.drawString(font, playerInventoryTitle,
                inventoryLabelX, inventoryLabelY, color, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY,
                       float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
