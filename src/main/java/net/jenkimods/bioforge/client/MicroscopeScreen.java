package net.jenkimods.bioforge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.infection.MicroscopeVisibility;
import net.jenkimods.bioforge.item.crispr.GeneImprintItem;
import net.jenkimods.bioforge.vaccine.VaccineBloodAssay;
import net.jenkimods.bioforge.world.microscope.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class MicroscopeScreen extends AbstractContainerScreen<MicroscopeMenu> {

    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.tryBuild(
                    BioForge.MODID, "textures/gui/laboratory/microscope.png");
    private static final ResourceLocation EMPTY_ICON =
            ResourceLocation.tryBuild(BioForge.MODID, "textures/gui/microscope/empty.png");
    private static final ResourceLocation IDENTIFY_BUTTON_TEXTURE =
            ResourceLocation.tryBuild(BioForge.MODID,
                    "textures/gui/microscope/button_identify.png");

    private static final int GRID_X = 8, GRID_Y = 17, CELL_W = 18, CELL_H = 28, COLS = 3, ICON_SIZE = 16;
    private static final int GRID_W = COLS * CELL_W, GRID_H = 2 * CELL_H;

    private static final int CALIB_X = 100;
    private static final int CALIB_Y = 10;
    private static final int TRACK_HEIGHT = 25;
    private static final int TRACK_WIDTH = 2;
    private static final int HANDLE_SIZE = 4;
    private static final int SLIDER_SPACING_H = 6;
    private static final int SLIDER_SPACING_V = 8;
    private static final int SLIDERS_PER_ROW = 6;

    private int scrollOffset = 0;
    private boolean isScrolling = false;
    private int draggingSlider = -1;
    private int lastSentCalibrationIndex = -1;
    private float lastSentCalibrationValue = Float.NaN;
    private Button identifyButton;

    public MicroscopeScreen(MicroscopeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        identifyButton = addRenderableWidget(new BioForgeTexturedButton(
                leftPos + 64, topPos + 57, 34, 16,
                        Component.translatable("gui.bioforge.microscope.identify"),
                        button -> {
                            if (minecraft != null && minecraft.gameMode != null) {
                                minecraft.gameMode.handleInventoryButtonClick(
                                        menu.containerId,
                                        MicroscopeBlockEntity.IDENTIFY_GENE_BUTTON);
                            }
                        }, IDENTIFY_BUTTON_TEXTURE));
        updateIdentifyButton();
    }

    @Override
    public void removed() {
        super.removed();
        MicroscopeClientData.clear();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateIdentifyButton();
    }

    private void updateIdentifyButton() {
        if (identifyButton == null) return;
        ItemStack stack = menu.slots.get(0).getItem();
        GeneImprintItem.Data imprint = GeneImprintItem.read(stack);
        boolean pendingAssay = VaccineBloodAssay.isAssay(stack)
                && !VaccineBloodAssay.isScanned(stack);
        identifyButton.visible = imprint != null && !imprint.identified()
                || pendingAssay;
        identifyButton.setMessage(Component.translatable(pendingAssay
                ? "gui.bioforge.microscope.scan"
                : "gui.bioforge.microscope.identify"));
        identifyButton.active = identifyButton.visible && MicroscopeClientData.isCalibrated();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isMouseOverScrollbar(mouseX, mouseY)) {
            isScrolling = true;
            updateScrollFromMouse(mouseY);
            return true;
        }
        if (button == 0 && MicroscopeClientData.getCalibrationSliders().size() > 0) {
            int idx = getSliderAt(mouseX, mouseY);
            if (idx != -1) {
                draggingSlider = idx;
                updateSliderFromMouse(mouseY, idx);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isScrolling) { isScrolling = false; return true; }
        if (button == 0 && draggingSlider != -1) { draggingSlider = -1; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double deltaX, double deltaY) {
        double delta = deltaY;
        if (isScrolling) return true;
        if (isMouseInGrid(mx, my) && MicroscopeClientData.isCalibrated()) {
            List<MicroscopeSymptomEntry> entries = MicroscopeClientData.getEntries();
            int totalRows = (int) Math.ceil((double) entries.size() / COLS);
            int visibleRows = GRID_H / CELL_H;
            int maxScroll = Math.max(0, totalRows - visibleRows);
            scrollOffset = (int) Math.min(Math.max(scrollOffset - delta, 0), maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, deltaX, deltaY);
    }

    private int getSliderAt(double mx, double my) {
        List<CalibrationSlider> sliders = MicroscopeClientData.getCalibrationSliders();
        int calibX = leftPos + CALIB_X;
        int calibY = topPos + CALIB_Y;

        for (int i = 0; i < sliders.size(); i++) {
            int col = i % SLIDERS_PER_ROW;
            int row = i / SLIDERS_PER_ROW;
            int sx = calibX + col * (TRACK_WIDTH + HANDLE_SIZE + SLIDER_SPACING_H);
            int sy = calibY + row * (TRACK_HEIGHT + SLIDER_SPACING_V);

            if (mx >= sx - 2 && mx <= sx + TRACK_WIDTH + HANDLE_SIZE + 2 &&
                    my >= sy - 2 && my <= sy + TRACK_HEIGHT + 2) {
                return i;
            }
        }
        return -1;
    }

    private void updateSliderFromMouse(double mouseY, int index) {
        List<CalibrationSlider> sliders = MicroscopeClientData.getCalibrationSliders();
        int row = index / SLIDERS_PER_ROW;
        int calibY = topPos + CALIB_Y + row * (TRACK_HEIGHT + SLIDER_SPACING_V);

        CalibrationSlider slider = sliders.get(index);
        float fraction = 1.0f - (float)(mouseY - calibY) / TRACK_HEIGHT;
        float value = slider.rangeMin() + fraction * (slider.rangeMax() - slider.rangeMin());
        MicroscopeClientData.setSliderValue(index, value);
        float range = slider.rangeMax() - slider.rangeMin();
        float normalized = range == 0.0F
                ? 0.5F
                : (value - slider.rangeMin()) / range;
        normalized = Math.max(0.0F, Math.min(1.0F, normalized));
        if (index != lastSentCalibrationIndex
                || !Float.isFinite(lastSentCalibrationValue)
                || Math.abs(normalized - lastSentCalibrationValue) >= 0.01F) {
            MicroscopeNetwork.sendCalibration(new MicroscopeCalibrationPacket(
                    menu.getBlockEntity().getBlockPos(), index, normalized));
            lastSentCalibrationIndex = index;
            lastSentCalibrationValue = normalized;
        }
    }

    private boolean isMouseOverScrollbar(double mx, double my) {
        if (!MicroscopeClientData.isCalibrated()) return false;
        int totalRows = (int) Math.ceil((double) MicroscopeClientData.getEntries().size() / COLS);
        int visibleRows = GRID_H / CELL_H;
        if (totalRows <= visibleRows) return false;
        int sx = leftPos + GRID_X;
        int sy = topPos + GRID_Y;
        int sbx = sx + GRID_W + 4;
        int sbw = 2;
        return mx >= sbx && mx <= sbx + sbw && my >= sy && my <= sy + GRID_H;
    }

    private void updateScrollFromMouse(double mouseY) {
        int totalRows = (int) Math.ceil((double) MicroscopeClientData.getEntries().size() / COLS);
        int visibleRows = GRID_H / CELL_H;
        int maxScroll = Math.max(0, totalRows - visibleRows);
        int sy = topPos + GRID_Y;
        int sbh = GRID_H;
        double fraction = (mouseY - sy) / (double) sbh;
        fraction = Math.min(1.0, Math.max(0.0, fraction));
        scrollOffset = (int) Math.round(fraction * maxScroll);
    }

    private boolean isMouseInGrid(double mx, double my) {
        return mx >= leftPos + GRID_X && mx <= leftPos + GRID_X + GRID_W
                && my >= topPos  + GRID_Y && my <= topPos  + GRID_Y + GRID_H;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        super.render(g, mx, my, pt);
        ItemStack stack = menu.slots.get(0).getItem();
        if (VaccineBloodAssay.isAssay(stack)) {
            renderAssayPanel(g, stack);
        } else if (MicroscopeClientData.isCalibrated()) {
            renderSymptomGrid(g, mx, my);
        }
        renderCalibrationPanel(g, mx, my);
        renderTooltip(g, mx, my);
        if (isScrolling) updateScrollFromMouse(my);
        if (draggingSlider != -1) updateSliderFromMouse(my, draggingSlider);
    }

    private void renderAssayPanel(GuiGraphics graphics, ItemStack stack) {
        int centerX = leftPos + GRID_X + GRID_W / 2;
        int labelY = topPos + GRID_Y + 9;
        if (!VaccineBloodAssay.isScanned(stack)) {
            graphics.drawCenteredString(font, Component.translatable(
                            "gui.bioforge.microscope.assay.pending"),
                    centerX, labelY, 0xFF78C7D3);
            return;
        }
        int result = MicroscopeClientData.getAssayResultPermille();
        graphics.drawCenteredString(font, Component.translatable(
                        "gui.bioforge.microscope.assay.result"),
                centerX, labelY, 0xFF6EFFFF);
        if (result >= 0) {
            String value = String.format(Locale.ROOT, "%.1f%%", result / 10.0F);
            graphics.drawCenteredString(font, value, centerX,
                    labelY + 15, assayColor(result));
        }
        VaccineBloodAssay.Data assay = VaccineBloodAssay.read(stack);
        if (assay != null && assay.crisprSequence().length() == 60
                && assay.crisprFeedback().length() == 60) {
            for (int guide = 0; guide < 3; guide++) {
                int rowStart = guide * 20;
                int startX = centerX - 58;
                int y = labelY + 29 + guide * 10;
                for (int base = 0; base < 20; base++) {
                    int index = rowStart + base;
                    int color = switch (assay.crisprFeedback().charAt(index)) {
                        case 'C' -> 0xFF55F59A;
                        case 'P' -> 0xFFFFCC66;
                        default -> 0xFFFF6B6B;
                    };
                    graphics.drawString(font,
                            String.valueOf(assay.crisprSequence().charAt(index)),
                            startX + base * 6, y, color, false);
                }
            }
        }
    }

    private static int assayColor(int resultPermille) {
        return resultPermille >= 800 ? 0xFF55F59A
                : resultPermille >= 350 ? 0xFFFFCC66 : 0xFFFF6B6B;
    }

    private void renderCalibrationPanel(GuiGraphics g, int mouseX, int mouseY) {
        List<CalibrationSlider> sliders = MicroscopeClientData.getCalibrationSliders();
        if (sliders.isEmpty()) return;

        float[] values = MicroscopeClientData.getSliderValues();
        int calibX = leftPos + CALIB_X;
        int calibY = topPos + CALIB_Y;

        for (int i = 0; i < sliders.size(); i++) {
            CalibrationSlider slider = sliders.get(i);
            float val = values[i];
            int col = i % SLIDERS_PER_ROW;
            int row = i / SLIDERS_PER_ROW;

            int sx = calibX + col * (TRACK_WIDTH + HANDLE_SIZE + SLIDER_SPACING_H);
            int sy = calibY + row * (TRACK_HEIGHT + SLIDER_SPACING_V);

            int trackLeft = sx + HANDLE_SIZE/2 - TRACK_WIDTH/2;
            g.fill(trackLeft, sy, trackLeft + TRACK_WIDTH, sy + TRACK_HEIGHT, 0xFFAAAAAA);

            float fraction = (val - slider.rangeMin()) / (slider.rangeMax() - slider.rangeMin());
            int handleY = sy + (int)((1.0f - fraction) * (TRACK_HEIGHT - HANDLE_SIZE));
            boolean inTolerance = slider.isWithinTolerance(val);
            int handleColor = inTolerance ? 0xFF00FF00 : 0xFFFF0000;
            g.fill(sx, handleY, sx + HANDLE_SIZE, handleY + HANDLE_SIZE, handleColor);

            if (inTolerance) {
                String plus = "+";
                int numWidth = font.width(plus);
                font.drawInBatch(plus, sx + HANDLE_SIZE/2 - numWidth/2, handleY + HANDLE_SIZE + 1,
                        0xFFFFFF, false, g.pose().last().pose(), g.bufferSource(),
                        net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 15728880);
            }

            if (mouseX >= sx - 2 && mouseX <= sx + HANDLE_SIZE + 2 &&
                    mouseY >= sy - 2 && mouseY <= sy + TRACK_HEIGHT + 2) {
                g.renderTooltip(font, Component.translatable(slider.nameKey()), mouseX, mouseY);
            }
        }
    }

    private void renderSymptomGrid(GuiGraphics g, int mouseX, int mouseY) {
        List<MicroscopeSymptomEntry> entries = MicroscopeClientData.getEntries();
        Map<String, Object> values = MicroscopeClientData.getSymptoms();
        MicroscopeVisibility slideVis = MicroscopeVisibility.fromName(MicroscopeClientData.getVisibility());

        int visibleRows = GRID_H / CELL_H;
        int sx = leftPos + GRID_X, sy = topPos + GRID_Y, ey = sy + GRID_H;

        g.enableScissor(sx, sy, sx + GRID_W, ey);

        for (int row = scrollOffset; row < scrollOffset + visibleRows; row++) {
            int y = sy + (row - scrollOffset) * CELL_H;
            for (int col = 0; col < COLS; col++) {
                int idx = row * COLS + col;
                if (idx >= entries.size()) break;

                MicroscopeSymptomEntry entry = entries.get(idx);
                int cx = sx + col * CELL_W;

                boolean visible = slideVis.ordinal() >= entry.minVisibility().ordinal();
                ResourceLocation icon = EMPTY_ICON;

                if (visible) {
                    Object value = values.get(entry.symptomKey());
                    if (value != null) {
                        if (entry.isEnum() && entry.stateIcons() != null) {
                            String stateName = value.toString().toLowerCase();
                            icon = entry.stateIcons().getOrDefault(stateName, entry.icon());
                        } else if (entry.isBoolean()) {
                            icon = (Boolean) value ? entry.icon() : EMPTY_ICON;
                        } else {
                            icon = entry.icon();
                        }
                    }
                }

                GuiRenderCompat.prepare(icon);
                g.blit(icon, cx + 3, y + 1, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            }
        }

        g.disableScissor();

        int totalRows = (int) Math.ceil((double) entries.size() / COLS);
        if (totalRows > visibleRows) {
            int sbx = sx + GRID_W + 4;
            int sbh = GRID_H;
            int hh = Math.max(6, sbh * visibleRows / totalRows);
            int hy = sy + (sbh - hh) * scrollOffset / (totalRows - visibleRows);
            g.fill(sbx, sy, sbx + 2, sy + sbh, 0x44FFFFFF);
            g.fill(sbx, hy, sbx + 2, hy + hh, 0xFFFFFFFF);
        }

        if (isMouseInGrid(mouseX, mouseY)) {
            int col = (mouseX - sx) / CELL_W;
            int row = scrollOffset + (mouseY - sy) / CELL_H;
            int idx = row * COLS + col;
            if (idx >= 0 && idx < entries.size()) {
                MicroscopeSymptomEntry entry = entries.get(idx);
                boolean visible = slideVis.ordinal() >= entry.minVisibility().ordinal();
                String tipText = null;

                if (!visible) {
                    tipText = "-";
                } else {
                    Object value = values.get(entry.symptomKey());
                    if (value == null) {
                        tipText = "-";
                    } else {
                        boolean mutation = entry.symptomKey().startsWith("mutation:");
                        String nameKey = mutation
                                ? mutationNameKey(entry.symptomKey().substring("mutation:".length()))
                                : "microscope.symptom." + entry.symptomKey();
                        String translatedName = Component.translatable(nameKey).getString();
                        if (translatedName.equals(nameKey)) {
                            translatedName = entry.symptomKey();
                        }
                        String stateText = "";
                        if (entry.isEnum()) {
                            String state = value.toString().toLowerCase();
                            String stateKey = "microscope.symptom." + entry.symptomKey() + "." + state;
                            stateText = Component.translatable(stateKey).getString();
                            if (stateText.equals(stateKey)) {
                                stateText = state;
                            }
                        } else if (value instanceof Boolean bool) {
                            stateText = Component.translatable(bool
                                    ? "microscope.value.present"
                                    : "microscope.value.absent").getString();
                        } else if (value instanceof Float f) {
                            if (mutation) {
                                stateText = Component.translatable(
                                        "microscope.value.mutation_tier", Math.max(1, Math.round(f)))
                                        .getString();
                            } else if ("active_lifespan".equals(entry.symptomKey()) && f < 0.0F) {
                                stateText = Component.translatable(
                                        "microscope.value.infinite").getString();
                            } else if ("incubation_time".equals(entry.symptomKey())
                                    || "active_lifespan".equals(entry.symptomKey())) {
                                stateText = Component.translatable(
                                        "microscope.value.seconds", Math.round(f)).getString();
                            } else if (entry.displayPercentage()) {
                                stateText = String.format("%.0f%%", f * 100);
                            } else {
                                stateText = String.format("%.0f", f);
                            }
                        }
                        tipText = translatedName + ": " + stateText;
                    }
                }

                if (tipText != null) {
                    g.renderTooltip(font, Component.literal(tipText), mouseX, mouseY);
                }
            }
        }
    }

    private static String mutationNameKey(String mutationId) {
        ResourceLocation id = mutationId.contains(":")
                ? ResourceLocation.tryParse(mutationId)
                : ResourceLocation.tryBuild(BioForge.MODID, mutationId);
        if (id == null) return "mutation." + BioForge.MODID + ".unknown.name";
        return "mutation." + id.getNamespace() + "."
                + id.getPath().replace('/', '.') + ".name";
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        GuiRenderCompat.prepare(GUI_TEXTURE);
        g.blit(GUI_TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x6EFFFF, false);
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x6EFFFF, false);
    }
}
