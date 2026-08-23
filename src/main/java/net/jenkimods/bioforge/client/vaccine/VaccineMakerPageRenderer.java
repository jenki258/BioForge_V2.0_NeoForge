package net.jenkimods.bioforge.client.vaccine;

import net.jenkimods.bioforge.client.VaccineMakerScreen;
import net.minecraft.client.gui.GuiGraphics;


public interface VaccineMakerPageRenderer {
    default void renderBackground(VaccineMakerScreen screen, GuiGraphics graphics,
                                  int left, int top, int mouseX, int mouseY,
                                  float partialTick) {}

    default void renderLabels(VaccineMakerScreen screen, GuiGraphics graphics,
                              int mouseX, int mouseY) {}

    default void containerTick(VaccineMakerScreen screen) {}

    default boolean mouseClicked(VaccineMakerScreen screen, double mouseX,
                                 double mouseY, int button) {
        return false;
    }

    default boolean mouseScrolled(VaccineMakerScreen screen, double mouseX,
                                  double mouseY, double delta) {
        return false;
    }
}
