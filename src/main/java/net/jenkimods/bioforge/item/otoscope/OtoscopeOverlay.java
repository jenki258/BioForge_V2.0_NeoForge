package net.jenkimods.bioforge.item.otoscope;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.jenkimods.bioforge.BioForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

@EventBusSubscriber(modid = BioForge.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public class OtoscopeOverlay {

    private static final ResourceLocation REDNESS_TEXTURE =
            ResourceLocation.tryBuild(BioForge.MODID, "textures/gui/otoscope/redness_particle.png");
    private static final ResourceLocation LESION_TEXTURE =
            ResourceLocation.tryBuild(BioForge.MODID, "textures/gui/otoscope/lesion_particle.png");
    private static final ResourceLocation SECRETION_TEXTURE =
            ResourceLocation.tryBuild(BioForge.MODID, "textures/gui/otoscope/secretion_particle.png");
    private static final ResourceLocation SWELLING_TEXTURE =
            ResourceLocation.tryBuild(BioForge.MODID, "textures/gui/otoscope/swelling_particle.png");

    private static final float SCOPE_RADIUS = 90f;
    private static final float SCOPE_CENTER_OFFSET_Y = -10f;
    private static final float MAX_DISTANCE = 4.0f;
    private static final float FADE_DURATION_SEC = 0.6f;

    private static final Random RANDOM = new Random();
    private static final List<OtoscopeParticle> particles = new ArrayList<>();

    private static long lastTickTime = 0;
    private static OtoscopeParticle hoveredParticle = null;
    private static long fadeStartTime = 0;

    @SubscribeEvent
    public static void onPreRenderOverlay(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR) && OtoscopeClientHandler.isInspecting()) {
            event.setCanceled(true);
        }
        if (OtoscopeClientHandler.isInspecting() && Minecraft.getInstance().player != null) {
            Player player = Minecraft.getInstance().player;
            player.setYRot(OtoscopeClientHandler.getLockedYRot());
            player.setXRot(OtoscopeClientHandler.getLockedXRot());
            player.yRotO = OtoscopeClientHandler.getLockedYRot();
            player.xRotO = OtoscopeClientHandler.getLockedXRot();
            player.yBodyRot = OtoscopeClientHandler.getLockedYRot();
            player.yBodyRotO = OtoscopeClientHandler.getLockedYRot();
            player.yHeadRot = OtoscopeClientHandler.getLockedYRot();
            player.yHeadRotO = OtoscopeClientHandler.getLockedYRot();
        }
    }

    @SubscribeEvent
    public static void onPostRenderOverlay(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        if (!isHoldingOtoscope(player)) {
            if (OtoscopeClientHandler.isInspecting()) {
                OtoscopeClientHandler.stopInspection();
            }
            fadeStartTime = 0;
            return;
        }

        if (!OtoscopeClientHandler.isInspecting()) {
            fadeStartTime = 0;
            return;
        }

        if (fadeStartTime == 0) fadeStartTime = System.currentTimeMillis();
        long now = System.currentTimeMillis();
        float fadeIn = Math.min(1.0f, (now - fadeStartTime) / (FADE_DURATION_SEC * 1000f));

        if (!OtoscopeClientHandler.isSelfMode()) {
            int entityId = OtoscopeClientHandler.getTargetEntityId();
            if (entityId >= 0 && player.level() != null) {
                Entity target = player.level().getEntity(entityId);
                if (target == null || !target.isAlive() || player.distanceTo(target) > MAX_DISTANCE) {
                    OtoscopeClientHandler.stopInspection();
                    fadeStartTime = 0;
                    return;
                }
            }
        }

        GuiGraphics gfx = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int cx = screenW / 2;
        int cy = screenH / 2 + (int)SCOPE_CENTER_OFFSET_Y;

        float selX = OtoscopeClientHandler.getSelectorX();
        float selY = OtoscopeClientHandler.getSelectorY();
        int selectorScreenX = cx + (int)selX;
        int selectorScreenY = cy + (int)selY;

        OtoscopeClientHandler.tick();

        float quality = OtoscopeClientHandler.getQuality();
        float redness = OtoscopeClientHandler.getRedness();
        float lesions = OtoscopeClientHandler.getLesions();
        float secretion = OtoscopeClientHandler.getSecretion();
        float swelling = OtoscopeClientHandler.getSwelling();

        updateParticles(cx, cy, redness, lesions, secretion, swelling, quality, fadeIn);
        hoveredParticle = findHoveredParticle(selectorScreenX, selectorScreenY);

        renderScopeVignette(gfx, screenW, screenH, cx, cy, fadeIn);
        renderTint(gfx, cx, cy, redness, quality, fadeIn);
        renderParticles(gfx);
        renderScopeRing(gfx, cx, cy, quality, fadeIn);

        renderSelectorCrosshair(gfx, selectorScreenX, selectorScreenY, fadeIn);
        renderNameLabel(gfx, cx, cy, player, mc, fadeIn);

        if (hoveredParticle != null) {
            renderTooltip(gfx, hoveredParticle, mc, fadeIn);
        }

        renderTutorial(gfx, screenW, screenH, mc, fadeIn);
    }

    private static boolean isHoldingOtoscope(Player player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off  = player.getOffhandItem();
        return main.getItem() instanceof OtoscopeItem || off.getItem() instanceof OtoscopeItem;
    }

    private static void renderScopeVignette(GuiGraphics gfx, int w, int h, int cx, int cy, float fadeIn) {
        int r = (int)SCOPE_RADIUS + 5;
        int maskAlpha = (int)(0xCC * fadeIn);
        int maskColor = (maskAlpha << 24) | 0x000000;
        gfx.fill(0, 0, w, h, maskColor);
        for (int y = cy - r; y < cy + r; y++) {
            int dx = (int) Math.sqrt(r * r - (y - cy) * (y - cy));
            int left = cx - dx;
            int right = cx + dx;
            if (left > 0) gfx.fill(left, y, right, y + 1, 0x00000000);
        }
    }

    private static void renderNameLabel(GuiGraphics gfx, int cx, int cy, Player player, Minecraft mc, float fadeIn) {
        String name;
        if (OtoscopeClientHandler.isSelfMode()) {
            name = player.getDisplayName().getString();
        } else {
            int entityId = OtoscopeClientHandler.getTargetEntityId();
            if (entityId >= 0 && player.level() != null) {
                Entity target = player.level().getEntity(entityId);
                name = target != null ? target.getDisplayName().getString() : "???";
            } else {
                name = "???";
            }
        }

        int textWidth = mc.font.width(name);
        int textX = cx - textWidth / 2;
        int textY = cy - (int) SCOPE_RADIUS - 20;
        int bgAlpha = (int)(0xCC * fadeIn);
        int borderAlpha = (int)(0xFF * fadeIn);
        int bgColor = (bgAlpha << 24) | 0x1A1A2E;
        int borderColor = (borderAlpha << 24) | 0x44DDFF;

        gfx.fill(textX - 4, textY - 2, textX + textWidth + 4, textY + mc.font.lineHeight + 2, bgColor);
        gfx.fill(textX - 4, textY - 2, textX + textWidth + 4, textY - 1, borderColor);
        gfx.fill(textX - 4, textY + mc.font.lineHeight + 1, textX + textWidth + 4, textY + mc.font.lineHeight + 2, borderColor);
        gfx.fill(textX - 4, textY - 1, textX - 3, textY + mc.font.lineHeight + 1, borderColor);
        gfx.fill(textX + textWidth + 3, textY - 1, textX + textWidth + 4, textY + mc.font.lineHeight + 1, borderColor);

        gfx.drawString(mc.font, name, textX, textY, applyAlpha(0xFFFFFF, (int)(255 * fadeIn)), false);
    }

    private static void renderTint(GuiGraphics gfx, int cx, int cy, float redness, float quality, float fadeIn) {
        float strength = redness * quality * fadeIn;
        if (strength <= 0) return;
        int alpha = (int)(strength * 70);
        int color = (alpha << 24) | 0xFFB6C1;
        drawFilledCircle(gfx, cx, cy, (int) SCOPE_RADIUS, color);
    }

    private static void renderScopeRing(GuiGraphics gfx, int cx, int cy, float quality, float fadeIn) {
        long time = System.currentTimeMillis();
        float pulse = 0.9f + 0.1f * (float) Math.sin(time * 0.004);
        int alpha = (int)(160 * quality * pulse * fadeIn);
        int glowColor = (alpha << 24) | 0x44DDFF;
        drawCircleOutline(gfx, cx, cy, (int)(SCOPE_RADIUS + 2), glowColor, 2);
        drawCircleOutline(gfx, cx, cy, (int)(SCOPE_RADIUS + 8), (int)(alpha * 0.3f) << 24 | 0x44DDFF, 1);
    }

    private static void renderSelectorCrosshair(GuiGraphics gfx, int x, int y, float fadeIn) {
        int color1 = applyAlpha(0x44DDFF, (int)(255 * fadeIn));
        int color2 = applyAlpha(0x44DDFF, (int)(0x88 * 255 * fadeIn));
        gfx.fill(x - 1, y - 1, x + 1, y + 1, color1);
        gfx.fill(x - 3, y, x + 3, y + 1, color2);
        gfx.fill(x, y - 3, x + 1, y + 3, color2);
    }

    private static void renderTutorial(GuiGraphics gfx, int screenW, int screenH, Minecraft mc, float fadeIn) {
        String text1 = Component.translatable("overlay.bioforge.otoscope.tutorial.move").getString();
        String text2 = Component.translatable("overlay.bioforge.otoscope.tutorial.select").getString();
        String text3 = Component.translatable("overlay.bioforge.otoscope.tutorial.exit").getString();

        int panelX = screenW - 160;
        int panelY = screenH - 80;
        int panelW = 150;
        int panelH = 60;
        int bgAlpha = (int)(0xBB * fadeIn);
        int textAlpha = (int)(255 * fadeIn);

        gfx.fill(panelX, panelY, panelX + panelW, panelY + panelH, (bgAlpha << 24) | 0x1A1A2E);
        gfx.drawString(mc.font, text1, panelX + 5, panelY + 5, applyAlpha(0x44DDFF, textAlpha), false);
        gfx.drawString(mc.font, text2, panelX + 5, panelY + 18, applyAlpha(0x44DDFF, textAlpha), false);
        gfx.drawString(mc.font, text3, panelX + 5, panelY + 31, applyAlpha(0x44DDFF, textAlpha), false);
    }

    private static void updateParticles(int cx, int cy, float redness, float lesions, float secretion, float swelling, float quality, float fadeIn) {
        long now = System.currentTimeMillis();
        float delta = (lastTickTime == 0) ? 0.05f : (now - lastTickTime) / 1000f;
        lastTickTime = now;

        int targetRedness   = (int)(redness   * quality * 30 * fadeIn);
        int targetLesions   = (int)(lesions   * quality * 25 * fadeIn);
        int targetSecretion = (int)(secretion * quality * 20 * fadeIn);
        int targetSwelling  = (int)(swelling  * quality * 15 * fadeIn);

        while (particles.size() > (targetRedness + targetLesions + targetSecretion + targetSwelling)) {
            particles.remove(0);
        }

        int curRedness = 0, curLesions = 0, curSecretion = 0, curSwelling = 0;
        for (OtoscopeParticle p : particles) {
            switch (p.symptomType) {
                case REDNESS   -> curRedness++;
                case LESION    -> curLesions++;
                case SECRETION -> curSecretion++;
                case SWELLING  -> curSwelling++;
            }
        }
        while (curRedness < targetRedness) {
            particles.add(new OtoscopeParticle(cx, cy, SymptomType.REDNESS));
            curRedness++;
        }
        while (curLesions < targetLesions) {
            particles.add(new OtoscopeParticle(cx, cy, SymptomType.LESION));
            curLesions++;
        }
        while (curSecretion < targetSecretion) {
            particles.add(new OtoscopeParticle(cx, cy, SymptomType.SECRETION));
            curSecretion++;
        }
        while (curSwelling < targetSwelling) {
            particles.add(new OtoscopeParticle(cx, cy, SymptomType.SWELLING));
            curSwelling++;
        }

        Iterator<OtoscopeParticle> iter = particles.iterator();
        while (iter.hasNext()) {
            OtoscopeParticle p = iter.next();
            p.update(delta, cx, cy);
        }
    }

    private static OtoscopeParticle findHoveredParticle(int selectorX, int selectorY) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            OtoscopeParticle p = particles.get(i);
            int dx = selectorX - (int)p.x;
            int dy = selectorY - (int)p.y;
            if (dx * dx + dy * dy < 25f * 25f) {
                return p;
            }
        }
        return null;
    }

    private static void renderParticles(GuiGraphics gfx) {
        RenderSystem.enableBlend();
        for (OtoscopeParticle p : particles) {
            int size = p.size;
            int px = (int)p.x;
            int py = (int)p.y;
            float alpha = p.alpha / 255f;

            ResourceLocation texture = switch (p.symptomType) {
                case REDNESS   -> REDNESS_TEXTURE;
                case LESION    -> LESION_TEXTURE;
                case SECRETION -> SECRETION_TEXTURE;
                case SWELLING  -> SWELLING_TEXTURE;
            };

            gfx.pose().pushPose();
            gfx.pose().translate(px, py, 0);
            gfx.pose().mulPose(Axis.ZP.rotationDegrees(p.rotation));
            gfx.pose().translate(-px, -py, 0);

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);

            gfx.blit(texture, px - size/2, py - size/2, 0, 0, size, size, size, size);

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            gfx.pose().popPose();
        }
        RenderSystem.disableBlend();
    }

    private static void renderTooltip(GuiGraphics gfx, OtoscopeParticle particle, Minecraft mc, float fadeIn) {
        String name = switch (particle.symptomType) {
            case REDNESS   -> Component.translatable("overlay.bioforge.otoscope.redness").getString();
            case LESION    -> Component.translatable("overlay.bioforge.otoscope.lesion").getString();
            case SECRETION -> Component.translatable("overlay.bioforge.otoscope.secretion").getString();
            case SWELLING  -> Component.translatable("overlay.bioforge.otoscope.swelling").getString();
        };
        int textX = (int)particle.x - mc.font.width(name) / 2;
        int textY = (int)particle.y - 18;
        int bgAlpha = (int)(0xCC * fadeIn);
        int borderAlpha = (int)(0xFF * fadeIn);
        int textAlpha = (int)(255 * fadeIn);
        int bgColor = (bgAlpha << 24) | 0x1A1A2E;
        int borderColor = (borderAlpha << 24) | 0x44DDFF;
        gfx.fill(textX - 3, textY - 2, textX + mc.font.width(name) + 3, textY + mc.font.lineHeight + 2, bgColor);
        gfx.fill(textX - 3, textY - 2, textX + mc.font.width(name) + 3, textY - 1, borderColor);
        gfx.fill(textX - 3, textY + mc.font.lineHeight + 1, textX + mc.font.width(name) + 3, textY + mc.font.lineHeight + 2, borderColor);
        gfx.fill(textX - 3, textY - 1, textX - 2, textY + mc.font.lineHeight + 1, borderColor);
        gfx.fill(textX + mc.font.width(name) + 2, textY - 1, textX + mc.font.width(name) + 3, textY + mc.font.lineHeight + 1, borderColor);
        gfx.drawString(mc.font, name, textX, textY, applyAlpha(0xFFFFFF, textAlpha), false);
    }

    private static void drawFilledCircle(GuiGraphics gfx, int cx, int cy, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int x = (int) Math.sqrt(radius * radius - y * y);
            gfx.fill(cx - x, cy + y, cx + x, cy + y + 1, color);
        }
    }

    private static void drawCircleOutline(GuiGraphics gfx, int cx, int cy, int radius, int color, int thickness) {
        for (int i = 0; i < 360; i += 3) {
            double angle = Math.toRadians(i);
            int x = cx + (int)(Math.cos(angle) * radius);
            int y = cy + (int)(Math.sin(angle) * radius);
            gfx.fill(x - thickness/2, y - thickness/2, x + thickness/2, y + thickness/2, color);
        }
    }

    private static int applyAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    private enum SymptomType { REDNESS, LESION, SECRETION, SWELLING }

    private static class OtoscopeParticle {
        float x, y, vx, vy, rotation, rotationSpeed;
        int size, alpha;
        SymptomType symptomType;
        int motionType;
        float orbitAngle, orbitRadius, orbitSpeed;

        OtoscopeParticle(int cx, int cy, SymptomType type) {
            symptomType = type;
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double dist = RANDOM.nextDouble() * (SCOPE_RADIUS - 20);
            x = cx + (float)(Math.cos(angle) * dist);
            y = cy + (float)(Math.sin(angle) * dist);
            motionType = RANDOM.nextInt(3);
            rotation = RANDOM.nextFloat() * 360f;
            rotationSpeed = (RANDOM.nextFloat() - 0.5f) * 250f;

            switch (type) {
                case REDNESS:   size = 4; alpha = 180; break;
                case LESION:    size = 4 + RANDOM.nextInt(6); alpha = 220; break;
                case SECRETION: size = 4 + RANDOM.nextInt(5); alpha = 200; break;
                case SWELLING:  size = 4 + RANDOM.nextInt(8); alpha = 180; break;
            }

            if (motionType == 0) {
                orbitRadius = (float) dist;
                orbitSpeed = (RANDOM.nextFloat() * 80f + 40f) * (RANDOM.nextBoolean() ? 1 : -1);
                orbitAngle = (float) angle;
            } else if (motionType == 1) {
                vx = (RANDOM.nextFloat() - 0.5f) * 70f;
                vy = (RANDOM.nextFloat() - 0.5f) * 70f;
            } else {
                vx = (RANDOM.nextFloat() - 0.5f) * 30f;
                vy = (RANDOM.nextFloat() - 0.5f) * 30f;
            }
        }

        void update(float delta, int cx, int cy) {
            switch (motionType) {
                case 0:
                    orbitAngle += orbitSpeed * delta * (Math.PI / 180f) * 0.08f;
                    orbitRadius += (RANDOM.nextFloat() - 0.5f) * 5f * delta;
                    orbitRadius = Math.max(15, Math.min(SCOPE_RADIUS - 10, orbitRadius));
                    x = cx + (float)(Math.cos(orbitAngle) * orbitRadius);
                    y = cy + (float)(Math.sin(orbitAngle) * orbitRadius);
                    break;
                case 1:
                    x += vx * delta; y += vy * delta;
                    float dx = x - cx, dy = y - cy;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    if (dist > SCOPE_RADIUS - 5) {
                        x = cx + dx / dist * (SCOPE_RADIUS - 5);
                        y = cy + dy / dist * (SCOPE_RADIUS - 5);
                        vx *= -0.85f; vy *= -0.85f;
                    }
                    break;
                case 2:
                    x += vx * delta; y += vy * delta;
                    dx = x - cx; dy = y - cy;
                    dist = (float) Math.sqrt(dx * dx + dy * dy);
                    if (dist > SCOPE_RADIUS - 5) {
                        x = cx + dx / dist * (SCOPE_RADIUS - 5);
                        y = cy + dy / dist * (SCOPE_RADIUS - 5);
                        vx *= -0.85f; vy *= -0.85f;
                    }
                    break;
            }
            rotation += rotationSpeed * delta;
        }
    }
}
