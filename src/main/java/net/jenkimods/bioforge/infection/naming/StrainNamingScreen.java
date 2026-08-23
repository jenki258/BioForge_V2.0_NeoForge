package net.jenkimods.bioforge.infection.naming;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.concurrent.ThreadLocalRandom;

@OnlyIn(Dist.CLIENT)
public final class StrainNamingScreen extends Screen {
    private static final String[] RANDOM_PREFIXES = {
            "Amber", "Ashen", "Azure", "Cinder", "Crimson", "Echo",
            "Glass", "Helix", "Hollow", "Ivory", "Lumen", "Nova",
            "Pale", "Sable", "Scarlet", "Silent", "Umbral", "Vesper",
            "Verdant", "Violet"
    };
    private static final String[] RANDOM_SUFFIXES = {
            "Bloom", "Cascade", "Crown", "Drift", "Echo", "Fever",
            "Halo", "Mist", "Pulse", "Rot", "Signal", "Spore",
            "Spiral", "Tide", "Vector", "Veil", "Wound"
    };
    private final String fingerprint;
    private EditBox nameField;
    private boolean submitted;

    public StrainNamingScreen(String fingerprint) {
        super(Component.translatable("gui.bioforge.strain_naming.title"));
        this.fingerprint = fingerprint;
    }

    @Override
    protected void init() {
        int center = width / 2;
        nameField = new EditBox(font, center - 100, height / 2 - 2, 136, 20,
                Component.translatable("gui.bioforge.strain_naming.field"));
        nameField.setMaxLength(StrainNamingManager.MAX_NAME_LENGTH);
        nameField.setHint(Component.translatable("gui.bioforge.strain_naming.field"));
        addRenderableWidget(nameField);
        addRenderableWidget(Button.builder(
                Component.translatable("gui.bioforge.strain_naming.random"),
                button -> randomizeName())
                .bounds(center + 40, height / 2 - 2, 60, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.bioforge.strain_naming.save"), button -> submit())
                .bounds(center - 100, height / 2 + 28, 96, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.bioforge.strain_naming.later"),
                button -> submitRandom()).bounds(center + 4, height / 2 + 28, 96, 20).build());
        setInitialFocus(nameField);
    }

    private void randomizeName() {
        nameField.setValue(randomName());
        setFocused(nameField);
    }

    private static String randomName() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String value = RANDOM_PREFIXES[random.nextInt(RANDOM_PREFIXES.length)] + " "
                + RANDOM_SUFFIXES[random.nextInt(RANDOM_SUFFIXES.length)];
        if (random.nextInt(4) == 0) {
            value += "-" + random.nextInt(2, 100);
        }
        return value;
    }

    private void submitRandom() {
        nameField.setValue(randomName());
        submit();
    }

    private void submit() {
        String value = nameField.getValue();
        if (StrainNamingManager.sanitizeName(value) == null) return;
        submitted = true;
        StrainNameNetworkHandler.submit(fingerprint, value);
        super.onClose();
    }

    @Override
    public void onClose() {
        if (!submitted) {
            submitRandom();
            return;
        }
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && nameField.isFocused()) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int center = width / 2;

        graphics.drawCenteredString(
                font,
                title,
                center,
                height / 2 - 56,
                0xFF75F4FF
        );

        graphics.drawCenteredString(
                font,
                Component.translatable("gui.bioforge.strain_naming.description"),
                center,
                height / 2 - 36,
                0xFFC6DCE3
        );

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
