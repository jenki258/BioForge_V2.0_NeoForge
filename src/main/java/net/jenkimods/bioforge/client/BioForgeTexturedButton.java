package net.jenkimods.bioforge.client;

import net.jenkimods.bioforge.registry.BioForgeSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;


public final class BioForgeTexturedButton extends Button {
    private final ResourceLocation texture;

    public BioForgeTexturedButton(int x, int y, int width, int height,
                                  Component message, OnPress onPress,
                                  ResourceLocation texture) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.texture = texture;
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        soundManager.play(SimpleSoundInstance.forUI(
                BioForgeSounds.UI_BUTTON.get(), 1.0F, 0.65F));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY,
                                float partialTick) {
        if (Minecraft.getInstance().getResourceManager().getResource(texture).isEmpty()) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            return;
        }
        int frame = !active ? 2 : isHovered() ? 1 : 0;
        graphics.setColor(1.0f, 1.0f, 1.0f, alpha);
        graphics.blit(texture, getX(), getY(), frame * width, 0,
                width, height, width * 3, height);
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        var font = Minecraft.getInstance().font;
        String label = getMessage().getString();
        int labelWidth = font.width(label);
        float scale = labelWidth <= width - 6 || labelWidth <= 0
                ? 1.0F : (width - 6) / (float) labelWidth;
        graphics.pose().pushPose();
        graphics.pose().translate(getX() + width / 2.0F,
                getY() + (height - font.lineHeight * scale) / 2.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, label, -labelWidth / 2, 0,
                active ? 0xFFFFFF : 0xA0A0A0, false);
        graphics.pose().popPose();
    }
}
