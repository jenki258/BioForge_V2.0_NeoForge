package net.jenkimods.bioforge.item.pulse_oximeter;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@EventBusSubscriber(modid = BioForge.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public class PulseOximeterOverlay {

    private static final int PANEL_W = 180;
    private static final int PANEL_H = 52;
    private static final int PANEL_Y_OFFSET = 56 + 4;

    private static final int BG_COLOR = 0x060C14;
    private static final int BORDER_COLOR = 0x1A5070;
    private static final int GRID_COLOR = 0x0D2030;

    private static final List<Float> waveformHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 140;
    private static long lastWaveformUpdate = 0;

    private static final long DOT_PULSE_PERIOD_MS = 4000;
    private static final float FADE_DURATION_SEC = 0.6f;

    private static long fadeStartTime = 0;

    @SubscribeEvent
    public static void onPreRenderOverlay(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR) && PulseOximeterClientHandler.isInspecting()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPostRenderOverlay(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;
        if (!PulseOximeterClientHandler.isInspecting()) {
            fadeStartTime = 0;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics gfx = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        Player player = mc.player;
        if (player == null) return;
        if (!isHoldingOximeter(player)) {
            PulseOximeterClientHandler.stopInspection();
            fadeStartTime = 0;
            return;
        }

        if (fadeStartTime == 0) fadeStartTime = System.currentTimeMillis();
        long now = System.currentTimeMillis();
        float fadeIn = Math.min(1.0f, (now - fadeStartTime) / (FADE_DURATION_SEC * 1000f));

        float quality = PulseOximeterClientHandler.getQuality();
        float o2 = PulseOximeterClientHandler.getOxygenSaturation();
        float perf = PulseOximeterClientHandler.getPerfusionIndex();
        long elapsed = now - PulseOximeterClientHandler.getStartTime();
        float stable = Math.min(1.0f, elapsed / 2500f);

        if (now - lastWaveformUpdate > 40) {
            lastWaveformUpdate = now;
            double phase = (now * 0.01) % 1.0;
            float baseWave = 0.0f;
            if (phase < 0.1f) {
                baseWave = (float) Math.sin(phase / 0.1f * Math.PI) * 0.8f;
            } else {
                baseWave = (float) Math.exp(-5.0f * (phase - 0.1f)) * 0.8f;
            }
            float noise = (1f - stable) * 0.4f * (new Random().nextFloat() - 0.5f);
            float sample = baseWave * (0.3f + perf * 0.7f) * stable + noise;
            waveformHistory.add(sample);
            while (waveformHistory.size() > MAX_HISTORY) waveformHistory.remove(0);
        }

        int panelX = (screenW - PANEL_W) / 2;
        int panelY = screenH - PANEL_Y_OFFSET - PANEL_H;

        renderStethoscopePanel(gfx, panelX, panelY, PANEL_W, PANEL_H, fadeIn);

        int innerX = panelX + 6;
        int innerY = panelY + 4;
        int innerW = PANEL_W - 12;
        int innerH = PANEL_H - 8;

        String name = PulseOximeterClientHandler.getTargetName();
        if (!name.isEmpty()) {
            int nameW = mc.font.width(name);
            gfx.drawString(mc.font, name, panelX + (PANEL_W - nameW) / 2, innerY, applyAlpha(0x44DDFF, (int)(255 * fadeIn)), false);
        }

        int spo2X = innerX;
        int spo2Y = innerY + mc.font.lineHeight + 2;
        Component spo2Label = Component.translatable("overlay.bioforge.pulse_oximeter.spo2");
        gfx.drawString(mc.font, spo2Label, spo2X, spo2Y, applyAlpha(0x44DDFF, (int)(255 * fadeIn)), false);

        if (stable >= 0.6f) {
            String spo2Text = String.format("%.0f%%", o2 * 100f);
            int spo2Color = o2 >= 0.95f ? 0x00FF00 : (o2 >= 0.90f ? 0xFFFF00 : 0xFF0000);
            gfx.drawString(mc.font, spo2Text, spo2X, spo2Y + 10, applyAlpha(spo2Color, (int)(255 * fadeIn)), false);
        } else {
            gfx.drawString(mc.font, "--", spo2X, spo2Y + 10, applyAlpha(0x888888, (int)(255 * fadeIn)), false);
        }

        int traceX = spo2X + 35;
        int traceEndX = innerX + innerW - 40;
        int traceY = innerY + innerH / 2 + 2;
        renderPlethWaveform(gfx, traceX, traceEndX, traceY, stable, fadeIn);

        int dotX = innerX + innerW - 20;
        int dotY = innerY + innerH / 2;
        renderPerfusionDot(gfx, dotX, dotY, perf, stable, fadeIn);

        Component piLabel = Component.translatable("overlay.bioforge.pulse_oximeter.pi");
        int piLabelW = mc.font.width(piLabel);
        gfx.drawString(mc.font, piLabel, dotX - piLabelW / 2, dotY + 10, applyAlpha(0x44DDFF, (int)(255 * fadeIn)), false);
    }

    private static boolean isHoldingOximeter(Player player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off  = player.getOffhandItem();
        return main.getItem() instanceof PulseOximeterItem || off.getItem() instanceof PulseOximeterItem;
    }

    private static void renderPlethWaveform(GuiGraphics gfx, int startX, int endX, int baseY, float stable, float fadeIn) {
        if (waveformHistory.size() < 2) return;
        int width = endX - startX;
        float yScale = 30f;
        int alpha = (int)(160 * stable * fadeIn);
        int color = (alpha << 24) | 0x44DDFF;

        for (int i = 1; i < waveformHistory.size(); i++) {
            int x1 = startX + (int)((i - 1) / (float)MAX_HISTORY * width);
            int x2 = startX + (int)(i / (float)MAX_HISTORY * width);
            float v1 = waveformHistory.get(i - 1);
            float v2 = waveformHistory.get(i);
            int y1 = baseY - (int)(v1 * yScale);
            int y2 = baseY - (int)(v2 * yScale);
            gfx.fill(x1, y1, x2, y2 + 1, color);
        }
    }

    private static void renderPerfusionDot(GuiGraphics gfx, int cx, int cy, float perf, float stable, float fadeIn) {
        long t = System.currentTimeMillis();
        float sizePulse = 0.7f + 0.3f * (float)Math.sin(t * (2.0 * Math.PI / DOT_PULSE_PERIOD_MS));
        int radius = (int)(4 + 5 * perf * sizePulse * stable);
        int alpha = (int)(190 * stable * fadeIn);
        int color = (alpha << 24) | 0xFFFFFF;

        drawFilledCircle(gfx, cx, cy, radius, color);
        drawCircleOutline(gfx, cx, cy, radius + 2, ((int)(50 * stable * fadeIn) << 24) | 0x44DDFF, 1);
    }

    private static void renderStethoscopePanel(GuiGraphics gfx, int x, int y, int w, int h, float fadeIn) {
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

    private static void drawFilledCircle(GuiGraphics gfx, int cx, int cy, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int x = (int) Math.sqrt(radius * radius - y * y);
            gfx.fill(cx - x, cy + y, cx + x, cy + y + 1, color);
        }
    }

    private static void drawCircleOutline(GuiGraphics gfx, int cx, int cy, int radius, int color, int thickness) {
        for (int i = 0; i < 360; i += 6) {
            double angle = Math.toRadians(i);
            int x = cx + (int)(Math.cos(angle) * radius);
            int y = cy + (int)(Math.sin(angle) * radius);
            gfx.fill(x - thickness/2, y - thickness/2, x + thickness/2, y + thickness/2, color);
        }
    }

    private static int applyAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    public static void resetWaveform() {
        waveformHistory.clear();
    }
}
