package net.jenkimods.bioforge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.world.incubator.IncubatorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class IncubatorScreen extends AbstractContainerScreen<IncubatorMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.tryBuild(
            BioForge.MODID, "textures/gui/laboratory/incubator.png");

    public IncubatorScreen(IncubatorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        GuiRenderCompat.prepare(TEXTURE);
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        int progress = menu.getProgress();
        int maxProgress = menu.getMaxProgress();
        if (maxProgress > 0 && progress > 0) {
            int barX = leftPos + 72;
            int barY = topPos + 27;
            int barWidth = 31;
            int barHeight = 22;
            int filledHeight = (int) (barHeight * ((float) progress / maxProgress));
            g.blit(TEXTURE, barX, barY, 176, 0, barWidth, filledHeight);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x6EFFFF, false);
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x6EFFFF, false);
    }
}
