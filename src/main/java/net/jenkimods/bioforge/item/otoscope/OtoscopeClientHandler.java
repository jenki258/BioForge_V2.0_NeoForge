package net.jenkimods.bioforge.item.otoscope;

import com.mojang.blaze3d.platform.InputConstants;
import net.jenkimods.bioforge.BioForge;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = BioForge.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public class OtoscopeClientHandler {

    private static boolean inspecting = false;
    private static float redness, lesions, secretion, swelling;
    private static float quality = 1.0f;
    private static long startTime;
    private static boolean selfMode;
    private static int targetEntityId = -1;

    private static float lockedYRot = 0f;
    private static float lockedXRot = 0f;

    private static float selectorX = 0f;
    private static float selectorY = 0f;
    private static final float SELECTOR_SPEED = 2f;
    private static final float MAX_SELECTOR_DIST = 80f;

    public static void startInspection(float r, float l, float s, float w, boolean self, int entityId) {
        inspecting = true;
        redness = r;
        lesions = l;
        secretion = s;
        swelling = w;
        selfMode = self;
        targetEntityId = entityId;
        startTime = System.currentTimeMillis();
        quality = 1.0f;
        selectorX = 0f;
        selectorY = 0f;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player != null) {
            lockedYRot = player.getYRot();
            lockedXRot = player.getXRot();
        }
    }

    public static void stopInspection() {
        inspecting = false;
    }

    public static boolean isInspecting() { return inspecting; }
    public static float getRedness()   { return redness * quality; }
    public static float getLesions()   { return lesions * quality; }
    public static float getSecretion() { return secretion * quality; }
    public static float getSwelling()  { return swelling * quality; }
    public static float getQuality()   { return quality; }
    public static boolean isSelfMode() { return selfMode; }
    public static int getTargetEntityId() { return targetEntityId; }
    public static float getLockedYRot() { return lockedYRot; }
    public static float getLockedXRot() { return lockedXRot; }
    public static float getSelectorX() { return selectorX; }
    public static float getSelectorY() { return selectorY; }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!inspecting) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        player.setYRot(lockedYRot);
        player.setXRot(lockedXRot);
        player.yRotO = lockedYRot;
        player.xRotO = lockedXRot;
        player.yBodyRot = lockedYRot;
        player.yBodyRotO = lockedYRot;
        player.yHeadRot = lockedYRot;
        player.yHeadRotO = lockedYRot;

        long window = mc.getWindow().getWindow();
        if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_A))
            selectorX -= SELECTOR_SPEED;
        if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_D))
            selectorX += SELECTOR_SPEED;
        if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_UP) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_W))
            selectorY -= SELECTOR_SPEED;
        if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_DOWN) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_S))
            selectorY += SELECTOR_SPEED;

        float dist = (float)Math.sqrt(selectorX * selectorX + selectorY * selectorY);
        if (dist > MAX_SELECTOR_DIST) {
            float scale = MAX_SELECTOR_DIST / dist;
            selectorX *= scale;
            selectorY *= scale;
        }
    }

    public static void tick() {
        if (!inspecting) return;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        float timeBonus = Math.min(1.0f, (System.currentTimeMillis() - startTime) / 2000.0f);
        quality = 0.6f + timeBonus * 0.4f;
    }
}
