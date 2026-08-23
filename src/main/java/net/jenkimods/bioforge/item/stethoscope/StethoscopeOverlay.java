package net.jenkimods.bioforge.item.stethoscope;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.infection.HeartRate;
import net.jenkimods.bioforge.infection.LungSound;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = BioForge.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public class StethoscopeOverlay {

    private static long listenStartTime = 0;
    private static long readingAnimationStart = 0;
    private static boolean wasListening = false;
    private static boolean wasShowingReading = false;

    private static final float MIN_LISTEN_TIME = 5.0f;
    private static final float LISTENING_PHASE_DURATION = 2.5f;
    private static final float WAVE_REVEAL_DURATION = 0.8f;
    private static final float WAVE_STABILIZE_DURATION = 0.5f;

    private static final int BG_COLOR = 0x060C14;
    private static final int BORDER_COLOR = 0x1A5070;
    private static final int DIVIDER_COLOR = 0x1A5070;
    private static final int GRID_COLOR = 0x0D2030;
    private static final int WAITING_COLOR = 0x4899BB;
    private static final int LINE_COLOR = 0x44DDFF;

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        if (!isHoldingStethoscope(player)) {
            if (StethoscopeClientHandler.isListening()) {
                StethoscopeClientHandler.stopListening();
            }
            if (wasListening) {
                listenStartTime = 0;
                readingAnimationStart = 0;
                wasListening = false;
                wasShowingReading = false;
            }
            return;
        }

        if (!StethoscopeClientHandler.isListening()) {
            if (wasListening) {
                listenStartTime = 0;
                readingAnimationStart = 0;
                wasListening = false;
                wasShowingReading = false;
            }
            return;
        }

        if (!wasListening) {
            listenStartTime = System.currentTimeMillis();
            readingAnimationStart = 0;
            wasListening = true;
            wasShowingReading = false;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        float elapsed = (System.currentTimeMillis() - listenStartTime) / 1000f;
        boolean hasReading = StethoscopeClientHandler.hasReading();
        boolean showReading = hasReading && elapsed > MIN_LISTEN_TIME;

        if (showReading && !wasShowingReading) {
            readingAnimationStart = System.currentTimeMillis();
            wasShowingReading = true;
        }
        if (!showReading) {
            readingAnimationStart = 0;
            wasShowingReading = false;
        }

        float fadeIn = Math.min(1f, elapsed / 0.6f);

        int panelW = 160;
        int panelH = 64;
        int panelX = (screenW - panelW) / 2;
        int panelY = screenH - 56 - panelH - 4;

        renderPanel(graphics, panelX, panelY, panelW, panelH, fadeIn);

        if (showReading) {
            float readingElapsed = (System.currentTimeMillis() - readingAnimationStart) / 1000f;
            renderReadingPhase(graphics, panelX, panelY, panelW, panelH, elapsed, readingElapsed, mc);
        } else {
            renderListeningPhase(graphics, panelX, panelY, panelW, panelH, elapsed, fadeIn, mc);
        }
    }

    private static boolean isHoldingStethoscope(Player player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        return main.getItem() instanceof StethoscopeItem || off.getItem() instanceof StethoscopeItem;
    }

    private static void renderListeningPhase(GuiGraphics graphics, int x, int y, int w, int h, float elapsed, float fadeIn, Minecraft mc) {
        float phaseProgress = Math.min(1f, elapsed / LISTENING_PHASE_DURATION);
        renderScanLines(graphics, x, y, w, h, phaseProgress, fadeIn);

        float textFadeIn = Math.max(0f, Math.min(1f, (phaseProgress - 0.7f) / 0.3f));
        if (textFadeIn > 0f) {
            int dotCount = ((int) (elapsed * 2)) % 4;
            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < dotCount; i++) dots.append(".");
            Component waiting = Component.translatable("overlay.bioforge.stethoscope.listening").withStyle(ChatFormatting.AQUA);
            String waitStr = waiting.getString() + dots;
            int textX = x + (w - mc.font.width(waitStr)) / 2;
            int textY = y + (h - 8) / 2;
            int textAlpha = (int) (fadeIn * textFadeIn * 255);
            graphics.drawString(mc.font, waitStr, textX, textY, applyAlpha(WAITING_COLOR, textAlpha), false);
        }
    }

    private static void renderReadingPhase(GuiGraphics graphics, int x, int y, int w, int h, float totalElapsed, float readingElapsed, Minecraft mc) {
        int heartWaveX = x + 8;
        int heartWaveY = y + 8;
        int heartWaveW = 68;
        int heartWaveH = 22;

        int lungWaveX = x + 84;
        int lungWaveY = y + 8;
        int lungWaveW = 68;
        int lungWaveH = 22;

        float revealProgress = Math.min(1f, readingElapsed / WAVE_REVEAL_DURATION);
        float stabilizeProgress = Math.max(0f, Math.min(1f, (readingElapsed - WAVE_REVEAL_DURATION) / WAVE_STABILIZE_DURATION));

        renderHeartWaveReveal(graphics, heartWaveX, heartWaveY, heartWaveW, heartWaveH, totalElapsed, revealProgress, stabilizeProgress);
        renderLungWaveReveal(graphics, lungWaveX, lungWaveY, lungWaveW, lungWaveH, totalElapsed, revealProgress, stabilizeProgress);

        float labelFadeIn = Math.max(0f, Math.min(1f, (readingElapsed - WAVE_REVEAL_DURATION - 0.2f) / 0.3f));

        if (labelFadeIn > 0f) {
            renderLabels(graphics, x, y, w, h, labelFadeIn, mc);
        }

        renderWaveLabels(graphics, x, y, w, mc, labelFadeIn);
    }

    private static void renderWaveLabels(GuiGraphics graphics, int x, int y, int w, Minecraft mc, float labelFadeIn) {
        if (labelFadeIn <= 0f) return;

        int alpha = (int)(labelFadeIn * 180);

        Component heartLabel = Component.translatable("overlay.bioforge.stethoscope.heart").withStyle(ChatFormatting.DARK_AQUA);
        Component lungLabel = Component.translatable("overlay.bioforge.stethoscope.lungs").withStyle(ChatFormatting.DARK_AQUA);

        int heartTextX = x + 8 + (68 - mc.font.width(heartLabel.getString())) / 2;
        int lungTextX = x + 84 + (68 - mc.font.width(lungLabel.getString())) / 2;
        int textY = y + 3;

        int color = applyAlpha(0x44DDFF, alpha);

        graphics.drawString(mc.font, heartLabel, heartTextX, textY, color, false);
        graphics.drawString(mc.font, lungLabel, lungTextX, textY, color, false);
    }

    private static void renderHeartWaveReveal(GuiGraphics graphics, int x, int y, int w, int h, float totalElapsed, float revealProgress, float stabilizeProgress) {
        HeartRate state = StethoscopeClientHandler.getHeartState();
        float bpm = switch (state) {
            case TACHY -> 2.4f;
            case BRADY -> 0.55f;
            default -> 1.1f;
        };
        int rgb = switch (state) {
            case TACHY -> 0xFF4466;
            case BRADY -> 0x33AAEE;
            default -> 0x44DDFF;
        };
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        float revealEased = easeOutCubic(revealProgress);
        int revealWidth = (int) (w * revealEased);
        int revealStartX = x + w - revealWidth;

        float noiseAmount = (1f - stabilizeProgress) * 0.3f;
        float amplitudeMultiplier = 0.5f + stabilizeProgress * 0.5f;

        int alpha = (int) (Math.min(revealProgress * 2f, 1f) * 230);
        float phase = (totalElapsed * bpm) % 1f;

        for (int px = 0; px < w - 1; px++) {
            int drawX = x + px;
            if (drawX < revealStartX) continue;

            float scrollT = (float) px / w;
            float scrollTNext = (float) (px + 1) / w;
            float amp = getHeartbeatAmplitude((scrollT - phase + 2f) % 1f, state) * amplitudeMultiplier;
            float ampNext = getHeartbeatAmplitude((scrollTNext - phase + 2f) % 1f, state) * amplitudeMultiplier;

            if (noiseAmount > 0.01f) {
                amp += (Math.sin(px * 0.7f + totalElapsed * 8f) * 0.5f + Math.cos(px * 1.3f + totalElapsed * 6f) * 0.5f) * noiseAmount;
                ampNext += (Math.sin((px + 1) * 0.7f + totalElapsed * 8f) * 0.5f + Math.cos((px + 1) * 1.3f + totalElapsed * 6f) * 0.5f) * noiseAmount;
            }

            int py = y + h / 2 - (int) (amp * h * 0.42f);
            int pyNext = y + h / 2 - (int) (ampNext * h * 0.42f);

            int edgeAlpha = alpha;
            if (drawX < revealStartX + 4) {
                edgeAlpha = (int) (alpha * (drawX - revealStartX) / 4f);
            }

            graphics.fill(drawX, Math.min(py, pyNext), drawX + 1, Math.max(py, pyNext) + 1, (edgeAlpha << 24) | (r << 16) | (g << 8) | b);
        }
    }

    private static void renderLungWaveReveal(GuiGraphics graphics, int x, int y, int w, int h, float totalElapsed, float revealProgress, float stabilizeProgress) {
        LungSound state = StethoscopeClientHandler.getLungState();
        int baseColor = state == LungSound.CRACKLE ? 0xFFAA33 : 0x2299CC;
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        float revealEased = easeOutCubic(revealProgress);
        int revealWidth = (int) (w * revealEased);
        int revealStartX = x + w - revealWidth;

        float noiseAmount = (1f - stabilizeProgress) * 0.35f;
        float amplitudeMultiplier = 0.4f + stabilizeProgress * 0.6f;

        int alpha = (int) (Math.min(revealProgress * 2f, 1f) * 200);
        float breathRate = state == LungSound.CRACKLE ? 0.28f : 0.22f;

        for (int px = 0; px < w - 1; px++) {
            int drawX = x + px;
            if (drawX < revealStartX) continue;

            float t = (float) px / w;
            float phase = (t - totalElapsed * breathRate + 4f) % 1f;
            float wave = (float) Math.sin(phase * Math.PI * 2) * 0.5f * amplitudeMultiplier;

            if (noiseAmount > 0.01f) {
                wave += (Math.sin(px * 0.9f + totalElapsed * 5f) * 0.5f + Math.cos(px * 1.1f + totalElapsed * 7f) * 0.5f) * noiseAmount;
            }

            if (state == LungSound.CRACKLE && (px % 7) < 2) {
                wave += ((float) Math.random() - 0.5f) * 0.35f * (0.6f + stabilizeProgress * 0.4f);
            }

            float tNext = (float) (px + 1) / w;
            float phaseNext = (tNext - totalElapsed * breathRate + 4f) % 1f;
            float waveNext = (float) Math.sin(phaseNext * Math.PI * 2) * 0.5f * amplitudeMultiplier;

            if (noiseAmount > 0.01f) {
                waveNext += (Math.sin((px + 1) * 0.9f + totalElapsed * 5f) * 0.5f + Math.cos((px + 1) * 1.1f + totalElapsed * 7f) * 0.5f) * noiseAmount;
            }

            if (state == LungSound.CRACKLE && ((px + 1) % 7) < 2) {
                waveNext += ((float) Math.random() - 0.5f) * 0.35f * (0.6f + stabilizeProgress * 0.4f);
            }

            int py = y + h / 2 - (int) (wave * h * 0.44f);
            int pyNext = y + h / 2 - (int) (waveNext * h * 0.44f);

            int edgeAlpha = alpha;
            if (drawX < revealStartX + 4) {
                edgeAlpha = (int) (alpha * (drawX - revealStartX) / 4f);
            }

            graphics.fill(drawX, Math.min(py, pyNext), drawX + 1, Math.max(py, pyNext) + 1, (edgeAlpha << 24) | (r << 16) | (g << 8) | b);
        }
    }

    private static void renderScanLines(GuiGraphics graphics, int x, int y, int w, int h, float progress, float fadeIn) {
        int innerX = x + 12;
        int innerY = y + 18;
        int innerW = w - 24;
        int innerH = h - 36;

        int lineSpacing = 6;
        int numLines = (innerH / lineSpacing) + 1;

        for (int i = 0; i < numLines; i++) {
            float lineDelay = (float) i / numLines * 0.6f;
            float lineProgress = Math.max(0f, Math.min(1f, (progress - lineDelay) / 0.4f));

            if (lineProgress <= 0f) continue;

            int lineY = innerY + i * lineSpacing;
            if (lineY > innerY + innerH) break;

            float drawWidth = innerW * easeOutCubic(lineProgress);
            int rightX = innerX + (int) drawWidth;

            int lineAlpha = (int) (fadeIn * lineProgress * 180);
            if (lineProgress < 0.1f) {
                lineAlpha = (int) (fadeIn * (lineProgress / 0.1f) * 255);
            } else if (lineProgress > 0.9f) {
                lineAlpha = (int) (fadeIn * ((1f - lineProgress) / 0.1f) * 180);
            }

            int tipAlpha = (int) (fadeIn * Math.min(1f, lineProgress * 2f) * 255);

            graphics.fill(innerX, lineY, rightX, lineY + 1, (lineAlpha << 24) | LINE_COLOR);

            if (drawWidth > 2 && lineProgress < 0.95f) {
                int glowWidth = 2;
                int glowStart = rightX - glowWidth;
                if (glowStart < innerX) glowStart = innerX;
                graphics.fill(glowStart, lineY - 1, rightX, lineY + 2, (tipAlpha << 24) | 0x88EEFF);
            }

            if (drawWidth > 1 && lineProgress < 0.9f) {
                graphics.fill(rightX, lineY - 1, rightX + 1, lineY + 2, (tipAlpha << 24) | 0xFFFFFF);
            }
        }
    }

    private static void renderPanel(GuiGraphics graphics, int x, int y, int w, int h, float fadeIn) {
        int alpha = (int) (fadeIn * 185);
        int bg = (alpha << 24) | BG_COLOR;
        int border = (alpha << 24) | BORDER_COLOR;
        int grid = ((int) (fadeIn * 28) << 24) | GRID_COLOR;
        graphics.fill(x + 1, y, x + w - 1, y + h, bg);
        graphics.fill(x, y + 1, x + 1, y + h - 1, bg);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, bg);
        for (int gx = x + 8; gx < x + w - 8; gx += 8) {
            graphics.fill(gx, y + 1, gx + 1, y + h - 1, grid);
        }
        for (int gy = y + 8; gy < y + h - 8; gy += 8) {
            graphics.fill(x + 1, gy, x + w - 1, gy + 1, grid);
        }
        graphics.fill(x + 1, y, x + w - 1, y + 1, border);
        graphics.fill(x + 1, y + h - 1, x + w - 1, y + h, border);
        graphics.fill(x, y + 1, x + 1, y + h - 1, border);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
        int divAlpha = (int) (fadeIn * 120);
        graphics.fill(x + 78, y + 6, x + 79, y + h - 6, (divAlpha << 24) | DIVIDER_COLOR);
    }

    private static float getHeartbeatAmplitude(float t, HeartRate state) {
        if (state == HeartRate.TACHY) {
            float p1 = 0.08f, p2 = 0.18f, p3 = 0.26f, p4 = 0.38f;
            if (t < p1) return t / p1 * 0.3f;
            if (t < p2) return 0.3f + (t - p1) / (p2 - p1) * 0.7f;
            if (t < p3) return 1.0f - (t - p2) / (p3 - p2) * 1.6f;
            if (t < p4) return -0.6f + (t - p3) / (p4 - p3) * 0.65f;
            return 0.05f * (float) Math.sin(t * (float) Math.PI * 6);
        } else if (state == HeartRate.BRADY) {
            float p1 = 0.12f, p2 = 0.22f, p3 = 0.32f, p4 = 0.44f;
            if (t < p1) return t / p1 * 0.25f;
            if (t < p2) return 0.25f + (t - p1) / (p2 - p1) * 0.75f;
            if (t < p3) return 1.0f - (t - p2) / (p3 - p2) * 1.5f;
            if (t < p4) return -0.5f + (t - p3) / (p4 - p3) * 0.55f;
            return 0.02f * (float) Math.sin(t * (float) Math.PI * 3);
        } else {
            float p1 = 0.1f, p2 = 0.2f, p3 = 0.3f, p4 = 0.42f;
            if (t < p1) return t / p1 * 0.3f;
            if (t < p2) return 0.3f + (t - p1) / (p2 - p1) * 0.7f;
            if (t < p3) return 1.0f - (t - p2) / (p3 - p2) * 1.55f;
            if (t < p4) return -0.55f + (t - p3) / (p4 - p3) * 0.6f;
            return 0.03f * (float) Math.sin(t * (float) Math.PI * 4);
        }
    }

    private static void renderLabels(GuiGraphics graphics, int x, int y, int w, int h, float readingFadeIn, Minecraft mc) {
        int textAlpha = (int) (readingFadeIn * 255);
        HeartRate heartState = StethoscopeClientHandler.getHeartState();
        LungSound lungState = StethoscopeClientHandler.getLungState();
        Component heartLabel = switch (heartState) {
            case TACHY -> Component.translatable("overlay.bioforge.stethoscope.heart.tachy").withStyle(ChatFormatting.RED);
            case BRADY -> Component.translatable("overlay.bioforge.stethoscope.heart.brady").withStyle(ChatFormatting.AQUA);
            default -> Component.translatable("overlay.bioforge.stethoscope.heart.normal").withStyle(ChatFormatting.AQUA);
        };
        Component lungLabel = switch (lungState) {
            case CRACKLE -> Component.translatable("overlay.bioforge.stethoscope.lungs.crackle").withStyle(ChatFormatting.GOLD);
            default -> Component.translatable("overlay.bioforge.stethoscope.lungs.normal").withStyle(ChatFormatting.AQUA);
        };
        int labelY = y + h - 14;
        int heartColor = applyAlpha(switch (heartState) {
            case TACHY -> 0xFF4466;
            case BRADY -> 0x33AAEE;
            default -> 0x44DDFF;
        }, textAlpha);
        int lungColor = applyAlpha(lungState == LungSound.CRACKLE ? 0xFFAA33 : 0x2299CC, textAlpha);
        String heartStr = heartLabel.getString();
        String lungStr = lungLabel.getString();
        int heartTextX = x + 8 + (68 - mc.font.width(heartStr)) / 2;
        int lungTextX = x + 84 + (68 - mc.font.width(lungStr)) / 2;
        graphics.drawString(mc.font, heartStr, heartTextX, labelY, heartColor, false);
        graphics.drawString(mc.font, lungStr, lungTextX, labelY, lungColor, false);
        String nameStr = StethoscopeClientHandler.getTargetName();
        if (!nameStr.isEmpty()) {
            Component nameComp = Component.translatable("overlay.bioforge.stethoscope.listening_to", nameStr).withStyle(ChatFormatting.GRAY);
            int nameTextX = x + (w - mc.font.width(nameComp.getString())) / 2;
            graphics.drawString(mc.font, nameComp, nameTextX, y + 40, applyAlpha(0x7AB8CC, textAlpha), false);
        }
    }

    private static float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1 - t, 3);
    }

    private static int applyAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
