package net.jenkimods.bioforge.mutation;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.registry.BioForgeSounds;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

@EventBusSubscriber(modid = BioForge.MODID, value = Dist.CLIENT)
public class SlotMachineOverlay {
    private static boolean animating = false;
    private static String selectedId = "";
    private static List<String> reelNames;
    private static Map<String, MutationVisual> visualCatalog = Map.of();
    private static float[] offsets = new float[3];
    private static float[] startOffsets = new float[3];
    private static float[] travelRows = new float[3];
    private static float[] lastUnwrappedOffsets = new float[3];
    private static float[] reelVelocity = new float[3];
    private static boolean[] stopped = new boolean[3];
    private static long startTime;
    private static long spinStartTime;
    private static long lastFrameTime;
    private static boolean revealed = false;
    private static long revealTime = 0;
    private static boolean jackpotCelebrated = false;
    private static boolean crankReleaseSoundPlayed = false;


    private static float crankPull = 0f;


    private static float screenShakeX = 0f;
    private static float screenShakeY = 0f;


    private static final List<GuiParticle> guiParticles = new ArrayList<>();
    private static long lastParticleSpawn = 0;

    private static final int REEL_HEIGHT = 32;
    private static final int REEL_WIDTH = 82;
    private static final int REEL_GAP = 5;
    private static final int PANEL_PADDING = 10;
    private static final int CRANK_AREA_WIDTH = 38;
    private static final int VISIBLE_ROWS = 3;
    private static final int TOTAL_ROWS = 20;
    private static final int MIDDLE_ROW = TOTAL_ROWS / 2;
    private static final Random RAND = new Random();
    private static final int ICON_SIZE = 16;
    private static final int NAME_GAP = 2;
    private static final long CRANK_PULL_MS = 420L;
    private static final long CRANK_RELEASE_MS = 280L;
    private static final long SPIN_DELAY_MS = CRANK_PULL_MS + CRANK_RELEASE_MS;
    private static final long BASE_REEL_DURATION_MS = 2450L;
    private static final long REEL_STOP_GAP_MS = 430L;

    private static class GuiParticle {
        float x, y, vx, vy, life, maxLife;
        int color;
        GuiParticle(float x, float y, float vx, float vy, int color, float life) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.color = color; this.maxLife = life; this.life = life;
        }
        boolean update(float deltaTicks) {
            x += vx * deltaTicks;
            y += vy * deltaTicks;
            life -= deltaTicks;
            return life > 0;
        }
    }

    public static void startAnimation(String selected) {
        List<MutationVisual> localCatalog = MutationLoader.INSTANCE.getAllMutations().stream()
                .filter(MutationDefinition::enabled)
                .map(MutationVisual::fromDefinition)
                .toList();
        startAnimation(selected, localCatalog);
    }

    public static void startAnimation(String selected, List<MutationVisual> catalog) {
        Map<String, MutationVisual> visuals = new LinkedHashMap<>();
        for (MutationVisual visual : catalog) {
            if (visual != null && visual.id() != null && !visual.id().isBlank()) {
                visuals.putIfAbsent(visual.id(), visual);
            }
        }
        MutationVisual selectedVisual = visuals.get(selected);
        if (selectedVisual == null) return;

        List<MutationVisual> decoys = new ArrayList<>();
        for (MutationVisual visual : visuals.values()) {
            if (!visual.id().equals(selected)) decoys.add(visual);
        }
        if (decoys.isEmpty()) {
            decoys.add(selectedVisual);
        }

        reelNames = new ArrayList<>();
        for (int i = 0; i < TOTAL_ROWS; i++) {
            if (i == MIDDLE_ROW) reelNames.add(selected);
            else {
                MutationVisual visual = decoys.get(RAND.nextInt(decoys.size()));
                reelNames.add(visual.id());
            }
        }

        selectedId = selected;
        visualCatalog = Map.copyOf(visuals);
        for (int i = 0; i < 3; i++) {
            startOffsets[i] = RAND.nextInt(TOTAL_ROWS);
            offsets[i] = startOffsets[i];
            lastUnwrappedOffsets[i] = startOffsets[i];
            reelVelocity[i] = 0f;
            int rowsToTarget = Math.floorMod(MIDDLE_ROW - (int) startOffsets[i], TOTAL_ROWS);
            travelRows[i] = (3 + i) * TOTAL_ROWS + rowsToTarget;
            stopped[i] = false;
        }
        startTime = Util.getMillis();
        spinStartTime = startTime + SPIN_DELAY_MS;
        lastFrameTime = startTime;
        revealed = false;
        jackpotCelebrated = false;
        crankReleaseSoundPlayed = false;
        crankPull = 0f;
        screenShakeX = 0f; screenShakeY = 0f;
        guiParticles.clear();
        lastParticleSpawn = 0;
        animating = true;

        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.0f)
        );
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;
        if (!animating) return;

        GuiGraphics gfx = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || reelNames == null || reelNames.isEmpty()) return;

        long now = Util.getMillis();
        float deltaTicks = Math.min(3f, Math.max(0f, (now - lastFrameTime) / (1000f / 60f)));
        lastFrameTime = now;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int reelAreaWidth = REEL_WIDTH * 3 + REEL_GAP * 2;
        int panelW = PANEL_PADDING * 2 + reelAreaWidth + CRANK_AREA_WIDTH;
        int panelH = REEL_HEIGHT * VISIBLE_ROWS + 62;
        int panelX = Math.max(0, (screenW - panelW) / 2);
        int panelY = Math.max(0, (screenH - panelH) / 2);


        long animationElapsed = now - startTime;
        if (animationElapsed < CRANK_PULL_MS) {
            crankPull = smoothStep(animationElapsed / (float) CRANK_PULL_MS);
        } else if (animationElapsed < SPIN_DELAY_MS) {
            float releaseProgress = (animationElapsed - CRANK_PULL_MS) / (float) CRANK_RELEASE_MS;
            crankPull = 1f - smoothStep(releaseProgress);
            if (!crankReleaseSoundPlayed) {
                crankReleaseSoundPlayed = true;
                mc.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 0.8f, 1.0f)
                );
            }
        } else {
            crankPull = 0f;
        }


        if (now >= spinStartTime && !revealed) {
            int stoppedCount = 0;
            for (int i = 0; i < 3; i++) {
                if (stopped[i]) {
                    stoppedCount++;
                    continue;
                }

                long duration = BASE_REEL_DURATION_MS + i * REEL_STOP_GAP_MS;
                float progress = Math.min(1f, (now - spinStartTime) / (float) duration);
                float unwrappedOffset = startOffsets[i] + travelRows[i] * reelProgress(progress);
                if (deltaTicks > 0f) {
                    reelVelocity[i] = (unwrappedOffset - lastUnwrappedOffsets[i]) / deltaTicks;
                }
                lastUnwrappedOffsets[i] = unwrappedOffset;
                offsets[i] = wrapOffset(unwrappedOffset);

                if (progress >= 1f) {
                    stopped[i] = true;
                    offsets[i] = MIDDLE_ROW;
                    reelVelocity[i] = 0f;
                    stoppedCount++;
                    mc.getSoundManager().play(
                            SimpleSoundInstance.forUI(BioForgeSounds.UI_SATISFYING.get(),
                                    1.05F - i * 0.08F, 0.7F)
                    );
                    screenShakeX = (RAND.nextFloat() - 0.5f) * 2f;
                    screenShakeY = (RAND.nextFloat() - 0.5f) * 2f;
                }
            }

            if (stoppedCount == 3) {
                revealed = true; revealTime = now;
                screenShakeX = (RAND.nextFloat() - 0.5f) * 5f;
                screenShakeY = (RAND.nextFloat() - 0.5f) * 5f;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(
                        BioForgeSounds.GENES_COMPLETE.get(), 1.0F, 0.85F));
                for (int i = 0; i < 24; i++) {
                    float x = panelX + RAND.nextFloat() * panelW;
                    float y = panelY + RAND.nextFloat() * panelH;
                    float vx = (RAND.nextFloat() - 0.5f) * 2f;
                    float vy = (RAND.nextFloat() - 0.5f) * 2f;
                    int color = randomBioColor();
                    guiParticles.add(new GuiParticle(x, y, vx, vy, color, 60 + RAND.nextInt(40)));
                }
            }
        }

        float shakeDecay = (float) Math.pow(0.9f, deltaTicks);
        screenShakeX *= shakeDecay; screenShakeY *= shakeDecay;
        if (Math.abs(screenShakeX) < 0.1f) screenShakeX = 0f;
        if (Math.abs(screenShakeY) < 0.1f) screenShakeY = 0f;
        panelX = Math.max(0, Math.min(Math.max(0, screenW - panelW), panelX + (int) screenShakeX));
        panelY = Math.max(0, Math.min(Math.max(0, screenH - panelH), panelY + (int) screenShakeY));

        if (!revealed && now - lastParticleSpawn > 280) {
            lastParticleSpawn = now;
            float x = panelX + 10 + RAND.nextFloat() * (panelW - 20);
            float y = panelY + 10 + RAND.nextFloat() * (panelH - 20);
            float vx = (RAND.nextFloat() - 0.5f) * 0.45f;
            float vy = -0.15f - RAND.nextFloat() * 0.35f;
            guiParticles.add(new GuiParticle(x, y, vx, vy, randomBioColor(), 35 + RAND.nextInt(25)));
        }
        guiParticles.removeIf(p -> !p.update(deltaTicks));

        long time = now;

        MutationVisual selectedDefinition = visualCatalog.get(selectedId);
        int accentColor = 0xFF2DD4C8;


        gfx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF123D45);
        gfx.fill(panelX + 2, panelY + 2, panelX + panelW - 2, panelY + panelH - 2, 0xF0051116);
        gfx.fill(panelX + 4, panelY + 4, panelX + panelW - 4, panelY + panelH - 4, 0xEE0A1D23);
        gfx.fill(panelX + 4, panelY + 4, panelX + panelW - 4, panelY + 22, 0xEE0D2A30);

        int pulseAlpha = 90 + (int) (55 * (0.5f + 0.5f * Math.sin(time / 260f)));
        int pulseColor = (pulseAlpha << 24) | (accentColor & 0x00FFFFFF);
        gfx.fill(panelX, panelY, panelX + panelW, panelY + 1, accentColor);
        gfx.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, accentColor);
        gfx.fill(panelX, panelY, panelX + 1, panelY + panelH, accentColor);
        gfx.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, accentColor);

        int cornerLength = 13;
        gfx.fill(panelX + 3, panelY + 3, panelX + 3 + cornerLength, panelY + 5, pulseColor);
        gfx.fill(panelX + 3, panelY + 3, panelX + 5, panelY + 3 + cornerLength, pulseColor);
        gfx.fill(panelX + panelW - 3 - cornerLength, panelY + 3, panelX + panelW - 3, panelY + 5, pulseColor);
        gfx.fill(panelX + panelW - 5, panelY + 3, panelX + panelW - 3, panelY + 3 + cornerLength, pulseColor);
        gfx.fill(panelX + 3, panelY + panelH - 5, panelX + 3 + cornerLength, panelY + panelH - 3, pulseColor);
        gfx.fill(panelX + 3, panelY + panelH - 3 - cornerLength, panelX + 5, panelY + panelH - 3, pulseColor);
        gfx.fill(panelX + panelW - 3 - cornerLength, panelY + panelH - 5, panelX + panelW - 3, panelY + panelH - 3, pulseColor);
        gfx.fill(panelX + panelW - 5, panelY + panelH - 3 - cornerLength, panelX + panelW - 3, panelY + panelH - 3, pulseColor);

        if (!revealed) {
            int scanRange = panelW - 30;
            int scanX = panelX + 15 + (int) ((time / 9L) % Math.max(1, scanRange));
            gfx.fill(scanX, panelY, Math.min(scanX + 18, panelX + panelW - 1), panelY + 1, 0xFF8CFFF2);
        }

        int reelStartX = panelX + PANEL_PADDING;
        int reelStartY = panelY + 26;


        int crankX = reelStartX + reelAreaWidth + 8;
        int crankY = panelY + 28;
        int crankW = CRANK_AREA_WIDTH - 12;
        int crankH = REEL_HEIGHT * VISIBLE_ROWS - 4;
        gfx.fill(crankX, crankY, crankX + crankW, crankY + crankH, 0xFF071318);
        gfx.fill(crankX + 1, crankY + 1, crankX + crankW - 1, crankY + crankH - 1, 0xFF102B31);
        gfx.fill(crankX + crankW / 2 - 2, crankY + 12,
                crankX + crankW / 2 + 2, crankY + 58, 0xFF183F46);

        int pullOff = (int) (crankPull * 30f);
        int leverY = crankY + 13 + pullOff;
        gfx.fill(crankX + crankW / 2 - 1, crankY + 12,
                crankX + crankW / 2 + 1, leverY + 3, 0xFF91B5B8);
        gfx.fill(crankX + 4, leverY, crankX + crankW - 4, leverY + 5, 0xFF1B5961);
        gfx.fill(crankX + 6, leverY + 1, crankX + crankW - 6, leverY + 4, 0xFF63F7E7);

        for (int i = 0; i < 3; i++) {
            int lightX = crankX + 4 + i * 7;
            int lightColor = stopped[i] ? 0xFF63F7E7 : 0xFF17383D;
            gfx.fill(lightX, crankY + crankH - 9, lightX + 4, crankY + crankH - 5, lightColor);
        }


        String title = revealed ? Component.translatable("mutation.slot.jackpot").getString()
                : Component.translatable("mutation.slot.title").getString();
        title = fitText(mc.font, title, reelAreaWidth - 8);
        int titleX = reelStartX + (reelAreaWidth - mc.font.width(title)) / 2;
        gfx.drawString(mc.font, title, titleX, panelY + 8, revealed ? 0xFF8CFFF2 : 0xFFD5FFFA, false);


        int totalReelWidth = REEL_WIDTH + REEL_GAP;

        for (int reel = 0; reel < 3; reel++) {
            int x = reelStartX + reel * totalReelWidth;
            int y = reelStartY;
            int reelBottom = y + REEL_HEIGHT * VISIBLE_ROWS;
            int selectorY = y + REEL_HEIGHT;

            int reelFrameColor = stopped[reel] ? 0xFF2DD4C8 : 0xFF294C4F;
            gfx.fill(x - 2, y - 2, x + REEL_WIDTH + 2, reelBottom + 2, reelFrameColor);
            gfx.fill(x, y, x + REEL_WIDTH, reelBottom, 0xFF071315);

            gfx.enableScissor(x, y, x + REEL_WIDTH, reelBottom);
            gfx.fill(x, selectorY, x + REEL_WIDTH, selectorY + REEL_HEIGHT, 0x182DD4C8);

            float motionStrength = Math.min(1f, Math.abs(reelVelocity[reel]) / 0.45f);
            if (!stopped[reel] && motionStrength > 0.08f) {
                int trailAlpha = 10 + (int) (motionStrength * 34);
                int trailColor = (trailAlpha << 24) | 0x002DD4C8;
                for (int trail = 0; trail < 4; trail++) {
                    int trailY = y + Math.floorMod((int) (time / 13L) + reel * 19 + trail * 23,
                            REEL_HEIGHT * VISIBLE_ROWS);
                    gfx.fill(x + 8, trailY, x + REEL_WIDTH - 8, trailY + 1, trailColor);
                }
            }

            int base = (int)Math.floor(offsets[reel]);
            int startRow = base - 1;
            int endRow = base + VISIBLE_ROWS + 1;

            for (int row = startRow; row < endRow; row++) {
                int idx = Math.floorMod(row, TOTAL_ROWS);
                String mutationId = reelNames.get(idx);
                MutationVisual def = visualCatalog.get(mutationId);

                int relRow = row - base;
                if (relRow < -1 || relRow > 3) continue;

                float yPos = y + (row - offsets[reel] + 1) * REEL_HEIGHT;
                int drawY = (int) yPos;


                boolean isWinner = (relRow == 0 && stopped[reel]);

                float distanceFromCenter = Math.abs(row - offsets[reel]);
                float scaleY = Math.max(0.68f, 1f - distanceFromCenter * 0.16f);
                float offsetY = 0f;


                if (def != null && def.icon() != null) {
                    int iconX = x + (REEL_WIDTH - ICON_SIZE) / 2;
                    int iconY = drawY + 2;

                    String name = fitText(mc.font, displayName(def), REEL_WIDTH - 6);
                    int nameX = x + (REEL_WIDTH - mc.font.width(name)) / 2;
                    int nameY = drawY + ICON_SIZE + NAME_GAP + 2;

                    if (isWinner && revealed) {
                        float bobOffset = (float) Math.sin(time / 320f + reel * 1.7f) * 2f;
                        float scale = 1.0f + 0.03f * (float) Math.sin(time / 380f + reel * 1.3f);
                        int animatedIconY = iconY + Math.round(bobOffset);

                        gfx.pose().pushPose();
                        gfx.pose().translate(iconX + ICON_SIZE / 2f, animatedIconY + ICON_SIZE / 2f, 0);
                        gfx.pose().scale(scale, scale, 1f);
                        gfx.pose().translate(-iconX - ICON_SIZE / 2f, -animatedIconY - ICON_SIZE / 2f, 0);
                        gfx.fill(iconX - 2, animatedIconY - 2,
                                iconX + ICON_SIZE + 2, animatedIconY + ICON_SIZE + 2, 0x552DD4C8);
                        gfx.blit(def.icon(), iconX, animatedIconY,
                                0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

                        gfx.pose().popPose();

                        int bobNameY = (int)(nameY + bobOffset);
                        gfx.drawString(mc.font, name, nameX + 1, bobNameY + 1, 0x552DD4C8, false);
                        gfx.drawString(mc.font, name, nameX, bobNameY, 0xFF8CFFF2, false);
                    } else {
                        gfx.pose().pushPose();
                        gfx.pose().translate(x + REEL_WIDTH / 2f, drawY + REEL_HEIGHT / 2f + offsetY, 0);
                        gfx.pose().scale(1f, scaleY, 1f);
                        gfx.pose().translate(-x - REEL_WIDTH / 2f, -drawY - REEL_HEIGHT / 2f - offsetY, 0);

                        gfx.blit(def.icon(), iconX, iconY, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

                        gfx.pose().popPose();

                        gfx.drawString(mc.font, name, nameX, nameY, 0xFFE8FFF9, false);
                    }
                } else {
                    String rawName = (def != null) ? displayName(def) : mutationId;
                    String name = fitText(mc.font, rawName, REEL_WIDTH - 6);
                    int color = isWinner ? 0xFF8CFFF2 : 0xFFE8FFF9;
                    int textX = x + (REEL_WIDTH - mc.font.width(name)) / 2;
                    int textY = drawY + (REEL_HEIGHT - mc.font.lineHeight) / 2;

                    if (isWinner && revealed) {
                        float bobOffset = (float) Math.sin(time / 320f + reel * 1.7f) * 2f;
                        int bobY = (int)(textY + bobOffset);
                        gfx.drawString(mc.font, name, textX + 1, bobY + 1, 0x552DD4C8, false);
                        gfx.drawString(mc.font, name, textX, bobY, color, false);
                    } else {
                        gfx.pose().pushPose();
                        gfx.pose().translate(x + REEL_WIDTH / 2f, drawY + REEL_HEIGHT / 2f + offsetY, 0);
                        gfx.pose().scale(1f, scaleY, 1f);
                        gfx.pose().translate(-x - REEL_WIDTH / 2f, -drawY - REEL_HEIGHT / 2f - offsetY, 0);
                        gfx.drawString(mc.font, name, textX, textY, color, false);
                        gfx.pose().popPose();
                    }
                }
            }

            gfx.fillGradient(x, y, x + REEL_WIDTH, y + 20, 0xE6071315, 0x00071315);
            gfx.fillGradient(x, reelBottom - 20, x + REEL_WIDTH, reelBottom, 0x00071315, 0xE6071315);
            gfx.disableScissor();

            float selectorPulse = 0.65f + 0.35f * (float) Math.sin(time / 320f + reel);
            int selectorAlpha = stopped[reel]
                    ? 190 + (int) (selectorPulse * 50)
                    : 105 + (int) (selectorPulse * 35);
            int selectorColor = (selectorAlpha << 24) | 0x002DD4C8;
            gfx.fill(x, selectorY, x + REEL_WIDTH, selectorY + 1, selectorColor);
            gfx.fill(x, selectorY + REEL_HEIGHT - 1,
                    x + REEL_WIDTH, selectorY + REEL_HEIGHT, selectorColor);
            gfx.fill(x, selectorY, x + 1, selectorY + REEL_HEIGHT, selectorColor);
            gfx.fill(x + REEL_WIDTH - 1, selectorY,
                    x + REEL_WIDTH, selectorY + REEL_HEIGHT, selectorColor);
            int markerY = selectorY + REEL_HEIGHT / 2;
            gfx.fill(x - 3, markerY - 2, x, markerY + 2, selectorColor);
            gfx.fill(x + REEL_WIDTH, markerY - 2, x + REEL_WIDTH + 3, markerY + 2, selectorColor);
        }


        for (GuiParticle p : guiParticles) {
            float alpha = p.life / p.maxLife;
            int a = (int)(alpha * 255);
            int color = (a << 24) | (p.color & 0x00FFFFFF);
            int size = (int)(3 + 2 * alpha);
            gfx.fill((int)p.x, (int)p.y, (int)p.x + size, (int)p.y + size, color);
        }


        if (revealed && selectedDefinition != null) {
                int finalColor = rarityColor(selectedDefinition.rarity());
                float resultPulse = 0.65f + 0.35f * (float) Math.sin(time / 260f);
                int resultGlowAlpha = 30 + (int) (resultPulse * 45);
                int resultGlow = (resultGlowAlpha << 24) | (finalColor & 0x00FFFFFF);

                String resultName = fitText(mc.font, displayName(selectedDefinition), reelAreaWidth - 14);
                String rarityText = Component.translatable(
                        "mutation.slot.rarity",
                        selectedDefinition.rarity().toUpperCase(Locale.ROOT)
                ).getString();
                rarityText = fitText(mc.font, rarityText, reelAreaWidth - 30);

                int resultNameX = reelStartX + (reelAreaWidth - mc.font.width(resultName)) / 2;
                int resultNameY = panelY + panelH - 28;
                int rarityX = reelStartX + (reelAreaWidth - mc.font.width(rarityText)) / 2;
                int rarityY = panelY + panelH - 16;
                int badgePad = 4;

                gfx.fill(resultNameX - 5, resultNameY - 2,
                        resultNameX + mc.font.width(resultName) + 5,
                        resultNameY + mc.font.lineHeight + 1, resultGlow);
                gfx.drawString(mc.font, resultName, resultNameX, resultNameY, 0xFFE8FFF9, false);
                gfx.fill(rarityX - badgePad, rarityY - 1,
                        rarityX + mc.font.width(rarityText) + badgePad,
                        rarityY + mc.font.lineHeight, (0x55 << 24) | (finalColor & 0x00FFFFFF));
                gfx.drawString(mc.font, rarityText, rarityX, rarityY, finalColor, false);

                if (!jackpotCelebrated) {
                    jackpotCelebrated = true;
                    for (int i = 0; i < 18; i++) {
                        float x = panelX + RAND.nextFloat() * panelW;
                        float y = panelY + RAND.nextFloat() * panelH;
                        float vx = (RAND.nextFloat() - 0.5f) * 1.4f;
                        float vy = -0.4f - RAND.nextFloat() * 1.1f;
                        int color = i % 3 == 0 ? finalColor : randomBioColor();
                        guiParticles.add(new GuiParticle(x, y, vx, vy, color, 45 + RAND.nextInt(30)));
                    }
                }
        }

        if (revealed && now - revealTime > 5000) {
            animating = false;
            reelNames = null;
            guiParticles.clear();
        }
    }

    private static String displayName(MutationVisual visual) {
        if (visual == null) return "";
        return I18n.exists(visual.nameKey())
                ? Component.translatable(visual.nameKey()).getString()
                : visual.fallbackName();
    }

    private static String fitText(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }

        String suffix = "...";
        int availableWidth = Math.max(0, maxWidth - font.width(suffix));
        return font.plainSubstrByWidth(text, availableWidth) + suffix;
    }

    private static int rarityColor(String rarity) {
        if (rarity == null) return 0xFF91AAA5;
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "uncommon" -> 0xFF8FEA62;
            case "rare" -> 0xFF58A6FF;
            case "epic" -> 0xFFB97AFF;
            case "legendary" -> 0xFFFFB84A;
            case "mythic" -> 0xFFFF5D73;
            default -> 0xFF91AAA5;
        };
    }

    private static int randomBioColor() {
        return switch (RAND.nextInt(4)) {
            case 0 -> 0xFF2DD4C8;
            case 1 -> 0xFF63F7E7;
            case 2 -> 0xFF8FEA62;
            default -> 0xFF5BA7B4;
        };
    }

    private static float smoothStep(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        return t * t * (3f - 2f * t);
    }

    private static float reelProgress(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        final float cruiseEnd = 0.55f;
        final float normalizedSpeed = 2f / (1f + cruiseEnd);

        if (t <= cruiseEnd) {
            return normalizedSpeed * t;
        }

        float decelerationProgress = (t - cruiseEnd) / (1f - cruiseEnd);
        return normalizedSpeed * cruiseEnd
                + normalizedSpeed * (1f - cruiseEnd)
                * (decelerationProgress - 0.5f * decelerationProgress * decelerationProgress);
    }

    private static float wrapOffset(float value) {
        float wrapped = value % TOTAL_ROWS;
        return wrapped < 0f ? wrapped + TOTAL_ROWS : wrapped;
    }

    public static boolean isAnimating() {
        return animating;
    }
}
