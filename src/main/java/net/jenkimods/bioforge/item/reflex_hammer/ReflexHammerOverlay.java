package net.jenkimods.bioforge.item.reflex_hammer;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.client.BioForgeKeyBindings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = BioForge.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public class ReflexHammerOverlay {

    private static final int PANEL_W = 200;
    private static final int PANEL_H = 76;

    private static final int BG_COLOR = 0x060C14;
    private static final int BORDER_COLOR = 0x1A5070;
    private static final int GRID_COLOR = 0x0D2030;

    private static final int BAR_WIDTH = 140;
    private static final int BAR_HEIGHT = 14;
    private static final int BAR_X_OFFSET = 30;
    private static final int BAR_Y_OFFSET = 28;

    private static final int ZONE_COLOR_NORMAL = 0xCC00E5FF;
    private static final int ZONE_COLOR_MISSED = 0xCCFF0000;
    private static final int SLIDER_COLOR = 0xCC44DDFF;

    private static final float FADE_DURATION_SEC = 0.6f;
    private static long fadeStartTime = 0;

    @SubscribeEvent
    public static void onGuiRender(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics gfx = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        if (ReflexHammerClientHandler.isSummaryVisible()) {
            fadeStartTime = 0;
            renderSummaryPanel(gfx, screenW, screenH, mc);
            return;
        }

        if (!ReflexHammerClientHandler.isCharging()) {
            fadeStartTime = 0;
            return;
        }

        if (fadeStartTime == 0) fadeStartTime = System.currentTimeMillis();
        long now = System.currentTimeMillis();
        float fadeIn = Math.min(1.0f, (now - fadeStartTime) / (FADE_DURATION_SEC * 1000f));

        int shakeX = ReflexHammerClientHandler.getShakeOffsetX();
        int shakeY = ReflexHammerClientHandler.getShakeOffsetY();

        int panelX = (screenW - PANEL_W) / 2 + shakeX;
        int panelY = screenH / 2 + 20 + shakeY;

        renderHammerPanel(gfx, panelX, panelY, PANEL_W, PANEL_H, fadeIn);

        int barX = panelX + BAR_X_OFFSET;
        int barY = panelY + BAR_Y_OFFSET;

        float sliderPos = ReflexHammerClientHandler.getSliderPos();
        float zoneStart = ReflexHammerClientHandler.getHitZoneStart();
        float zoneEnd   = ReflexHammerClientHandler.getHitZoneEnd();
        boolean missed = ReflexHammerClientHandler.hasMissed();

        int barAlpha = (int)(0xCC * fadeIn);
        gfx.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, (barAlpha << 24) | 0x333333);
        int zoneColor = missed ? ZONE_COLOR_MISSED : ZONE_COLOR_NORMAL;
        int zoneAlpha = (int)(0xCC * fadeIn);
        int hitStartX = barX + (int)(BAR_WIDTH * zoneStart);
        int hitEndX   = barX + (int)(BAR_WIDTH * zoneEnd);
        gfx.fill(hitStartX, barY, hitEndX, barY + BAR_HEIGHT, (zoneAlpha << 24) | (zoneColor & 0xFFFFFF));
        int sliderX = barX + (int)(BAR_WIDTH * sliderPos);
        gfx.fill(sliderX - 2, barY - 2, sliderX + 2, barY + BAR_HEIGHT + 2, (zoneAlpha << 24) | (SLIDER_COLOR & 0xFFFFFF));

        String target = ReflexHammerClientHandler.getTargetName();
        if (!target.isEmpty()) {
            Component targetComp = Component.translatable("overlay.bioforge.reflex.target", target)
                    .withStyle(ChatFormatting.GRAY);
            int tw = mc.font.width(targetComp);
            int tx = panelX + (PANEL_W - tw) / 2;
            int ty = panelY + 4;
            gfx.drawString(mc.font, targetComp, tx, ty, applyAlpha(0xFFFFFF, (int)(255 * fadeIn)), false);
        }

        String promptKey = missed ? "overlay.bioforge.reflex.prompt_missed" : "overlay.bioforge.reflex.prompt";
        Component prompt = Component.translatable(promptKey,
                        BioForgeKeyBindings.REFLEX_STRIKE.getTranslatedKeyMessage())
                .withStyle(missed ? ChatFormatting.RED : ChatFormatting.WHITE);
        renderWrappedText(gfx, mc, prompt, panelX + 10, barY + BAR_HEIGHT + 4, PANEL_W - 20, applyAlpha(0xFFFFFF, (int)(255 * fadeIn)));

        int successes = ReflexHammerClientHandler.getConsecutiveSuccesses();
        Component counterComp = Component.translatable("overlay.bioforge.reflex.counter", successes, 5).withStyle(ChatFormatting.AQUA);
        int counterY = barY + BAR_HEIGHT + 4 + getWrappedHeight(mc, prompt, PANEL_W - 20);
        renderWrappedText(gfx, mc, counterComp, panelX + 10, counterY, PANEL_W - 20, applyAlpha(0xFFFFFF, (int)(255 * fadeIn)));
    }

    private static void renderSummaryPanel(GuiGraphics gfx, int screenW, int screenH, Minecraft mc) {
        float delay = ReflexHammerClientHandler.getSummaryDelay();
        float strength = ReflexHammerClientHandler.getSummaryStrength();
        float neural = ReflexHammerClientHandler.getSummaryNeural();

        Component title = Component.translatable("overlay.bioforge.reflex.summary.title").withStyle(ChatFormatting.AQUA);
        Component delayLine = Component.translatable("overlay.bioforge.reflex.summary.delay", intensity(delay)).withStyle(ChatFormatting.WHITE);
        Component strengthLine = Component.translatable("overlay.bioforge.reflex.summary.strength", intensity(strength)).withStyle(ChatFormatting.WHITE);
        Component neuralLine = Component.translatable("overlay.bioforge.reflex.summary.neural", intensity(neural)).withStyle(ChatFormatting.WHITE);
        Component close = Component.translatable("overlay.bioforge.reflex.summary.close").withStyle(ChatFormatting.GRAY);

        int maxTextWidth = Math.max(mc.font.width(delayLine), Math.max(mc.font.width(strengthLine), mc.font.width(neuralLine)));
        int panelW = Math.max(180, maxTextWidth + 30);
        int panelH = 20 + 3 * mc.font.lineHeight + 10 + mc.font.lineHeight + 4;
        int panelX = (screenW - panelW) / 2;
        int panelY = screenH / 2 + 20;

        renderHammerPanel(gfx, panelX, panelY, panelW, panelH, 1.0f);

        int titleX = panelX + (panelW - mc.font.width(title)) / 2;
        int titleY = panelY + 6;
        gfx.drawString(mc.font, title, titleX, titleY, 0xFFFFFFFF, false);

        int y = titleY + 16;
        gfx.drawString(mc.font, delayLine, panelX + 10, y, 0xFFFFFFFF, false);
        y += mc.font.lineHeight;
        gfx.drawString(mc.font, strengthLine, panelX + 10, y, 0xFFFFFFFF, false);
        y += mc.font.lineHeight;
        gfx.drawString(mc.font, neuralLine, panelX + 10, y, 0xFFFFFFFF, false);

        int closeX = panelX + (panelW - mc.font.width(close)) / 2;
        int closeY = panelY + panelH - mc.font.lineHeight - 4;
        gfx.drawString(mc.font, close, closeX, closeY, 0xFFFFFFFF, false);
    }

    private static Component intensity(float value) {
        String key;
        if (value > 0.7f) key = "overlay.bioforge.reflex.intensity.high";
        else if (value > 0.3f) key = "overlay.bioforge.reflex.intensity.moderate";
        else if (value > 0.0f) key = "overlay.bioforge.reflex.intensity.low";
        else key = "overlay.bioforge.reflex.intensity.none";
        return Component.translatable(key);
    }

    private static void renderWrappedText(GuiGraphics gfx, Minecraft mc, Component text, int x, int y, int maxWidth, int color) {
        List<String> lines = wrapText(mc, text.getString(), maxWidth);
        for (String line : lines) {
            gfx.drawString(mc.font, line, x, y, color, false);
            y += mc.font.lineHeight;
        }
    }

    private static int getWrappedHeight(Minecraft mc, Component text, int maxWidth) {
        return wrapText(mc, text.getString(), maxWidth).size() * mc.font.lineHeight;
    }

    private static List<String> wrapText(Minecraft mc, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            if (mc.font.width(currentLine + word) > maxWidth) {
                lines.add(currentLine.toString().trim());
                currentLine = new StringBuilder(word + " ");
            } else {
                currentLine.append(word).append(" ");
            }
        }
        if (currentLine.length() > 0) lines.add(currentLine.toString().trim());
        return lines;
    }

    private static void renderHammerPanel(GuiGraphics gfx, int x, int y, int w, int h, float fadeIn) {
        int alpha = (int)(0.8f * 185 * fadeIn);
        int bg = (alpha << 24) | BG_COLOR;
        int border = (alpha << 24) | BORDER_COLOR;
        int grid = ((int)(0.8f * 28 * fadeIn) << 24) | GRID_COLOR;

        gfx.fill(x + 1, y, x + w - 1, y + h, bg);
        gfx.fill(x, y + 1, x + 1, y + h - 1, bg);
        gfx.fill(x + w - 1, y + 1, x + w, y + h - 1, bg);

        for (int gx = x + 8; gx < x + w - 8; gx += 8) {
            gfx.fill(gx, y + 1, gx + 1, y + h - 1, grid);
        }
        for (int gy = y + 8; gy < y + h - 8; gy += 8) {
            gfx.fill(x + 1, gy, x + w - 1, gy + 1, grid);
        }

        gfx.fill(x + 1, y, x + w - 1, y + 1, border);
        gfx.fill(x + 1, y + h - 1, x + w - 1, y + h, border);
        gfx.fill(x, y + 1, x + 1, y + h - 1, border);
        gfx.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
    }

    private static int applyAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
