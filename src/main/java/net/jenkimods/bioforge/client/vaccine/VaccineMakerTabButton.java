package net.jenkimods.bioforge.client.vaccine;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.api.vaccine.VaccineMakerPageDefinition;
import net.jenkimods.bioforge.client.VaccineMakerScreen;
import net.jenkimods.bioforge.registry.BioForgeSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class VaccineMakerTabButton extends AbstractButton {
    private static final ResourceLocation TEXTURE = ResourceLocation.tryBuild(
            BioForge.MODID, "textures/gui/vaccine_maker/tab.png");
    private final VaccineMakerScreen screen;
    private final VaccineMakerPageDefinition page;
    private final int pageIndex;

    public VaccineMakerTabButton(VaccineMakerScreen screen,
                                 VaccineMakerPageDefinition page,
                                 int pageIndex, int x, int y) {
        super(x, y, 28, 22, page.title().get());
        this.screen = screen;
        this.page = page;
        this.pageIndex = pageIndex;
        setTooltip(Tooltip.create(page.title().get()));
    }

    @Override
    public void onPress() {
        screen.selectPage(pageIndex);
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        soundManager.play(SimpleSoundInstance.forUI(
                BioForgeSounds.UI_BUTTON.get(), 1.0F, 0.6F));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY,
                                float partialTick) {
        boolean selected = screen.getMenuView().getActivePageIndex() == pageIndex;
        if (Minecraft.getInstance().getResourceManager().getResource(TEXTURE).isPresent()) {
            int frame = selected ? 2 : !active ? 3 : isHovered() || isFocused() ? 1 : 0;
            graphics.blit(TEXTURE, getX(), getY(), frame * width, 0,
                    width, height, width * 4, height);
        } else {
            int border = selected ? 0xFF67F5D0 : isHovered() ? 0xFF75BFCB : 0xFF2F6570;
            int background = selected ? 0xFF123C43 : 0xFF0A222A;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF02090D);
            graphics.fill(getX() + 1, getY() + 1,
                    getX() + width - 1, getY() + height - 1, background);
            graphics.fill(getX(), getY() + height - 2,
                    getX() + width, getY() + height, border);
        }
        ItemStack icon = page.icon().get();
        if (!icon.isEmpty()) graphics.renderItem(icon, getX() + 6, getY() + 2);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
