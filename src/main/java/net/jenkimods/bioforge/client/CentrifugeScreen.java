package net.jenkimods.bioforge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.jenkimods.bioforge.world.centrifuge.CentrifugeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.Objects;

public class CentrifugeScreen extends AbstractContainerScreen<CentrifugeMenu> {

    private static final ResourceLocation GUI_TEXTURE =
            Objects.requireNonNull(ResourceLocation.tryBuild(
                    "bioforge", "textures/gui/laboratory/centrifuge.png"));
    private static final int TEX_W = 256;
    private static final int TEX_H = 256;

    public CentrifugeScreen(CentrifugeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 190;
        this.inventoryLabelY = 96;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);
        guiGraphics.blit(GUI_TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, TEX_W, TEX_H);

        int progress = menu.getScaledProgress32();
        int ringX = leftPos + 73;
        int ringY = topPos + 44;
        guiGraphics.blit(GUI_TEXTURE, ringX, ringY, 176, 0, 32, 32, TEX_W, TEX_H);
        if (progress > 0) {
            int clippedHeight = Math.min(32, progress);
            int vOffset = 32 - clippedHeight;
            guiGraphics.blit(
                    GUI_TEXTURE,
                    ringX,
                    ringY + vOffset,
                    208,
                    vOffset,
                    32,
                    clippedHeight,
                    TEX_W,
                    TEX_H
            );
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x6EFFFF, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x6EFFFF, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
