package net.jenkimods.bioforge.item.reflex_hammer;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.client.BioForgeKeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Random;

@EventBusSubscriber(modid = BioForge.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public class ReflexHammerClientHandler {

    public static final float SLIDER_SPEED_PER_SEC = 0.5f;
    private static final int REQUIRED_SUCCESSES = 5;
    private static final double MAX_DISTANCE = 4.0;

    private static float hitZoneStart = 0.7f;
    private static float hitZoneEnd   = 0.85f;
    private static final Random RAND = new Random();

    private static boolean charging = false;
    private static float sliderPos = 0f;
    private static long startTime;
    private static boolean missedTiming = false;
    private static long missedTime = 0;

    private static boolean selfMode = false;
    private static int targetEntityId = -1;
    private static String targetName = "";

    private static int consecutiveSuccesses = 0;
    private static int lastStrikeTargetId = -1;

    private static boolean summaryVisible = false;
    private static float summaryDelay, summaryStrength, summaryNeural;
    private static long summaryShowTime = 0;
    private static final long SUMMARY_DISPLAY_MS = 6000;

    private static long shakeEndTime = 0;
    private static final long SHAKE_DURATION_MS = 400;

    public static boolean isCharging() { return charging; }
    public static float getSliderPos() { return sliderPos; }
    public static boolean hasMissed() { return missedTiming; }
    public static String getTargetName() { return targetName; }
    public static boolean isSelfMode() { return selfMode; }
    public static int getConsecutiveSuccesses() { return consecutiveSuccesses; }
    public static float getHitZoneStart() { return hitZoneStart; }
    public static float getHitZoneEnd()   { return hitZoneEnd; }

    public static boolean isSummaryVisible() { return summaryVisible; }
    public static float getSummaryDelay()    { return summaryDelay; }
    public static float getSummaryStrength() { return summaryStrength; }
    public static float getSummaryNeural()   { return summaryNeural; }

    public static int getShakeOffsetX() {
        return (System.currentTimeMillis() < shakeEndTime) ? (RAND.nextInt(5) - 2) : 0;
    }
    public static int getShakeOffsetY() {
        return (System.currentTimeMillis() < shakeEndTime) ? (RAND.nextInt(5) - 2) : 0;
    }

    public static void beginCharge(int entityId, boolean self) {
        charging = true;
        missedTiming = false;
        summaryVisible = false;
        resetSlider();
        selfMode = self;
        targetEntityId = entityId;
        if (entityId != lastStrikeTargetId) {
            consecutiveSuccesses = 0;
            lastStrikeTargetId = entityId;
        }
        updateTargetName();
    }

    public static void cancelCharge() {
        charging = false;
        consecutiveSuccesses = 0;
        summaryVisible = false;
    }

    private static void resetSlider() {
        sliderPos = 0f;
        startTime = System.currentTimeMillis();
        hitZoneStart = 0.3f + RAND.nextFloat() * 0.4f;
        hitZoneEnd   = hitZoneStart + 0.1f + RAND.nextFloat() * 0.15f;
        if (hitZoneEnd > 0.9f) hitZoneEnd = 0.9f;
    }

    public static float onStrike() {
        if (!charging) return -1f;

        if (missedTiming) {
            missedTiming = false;
            consecutiveSuccesses = 0;
            resetSlider();
            return -1f;
        }

        float accuracy;
        if (sliderPos >= hitZoneStart && sliderPos <= hitZoneEnd) {
            accuracy = 1.0f;
        } else if (sliderPos < hitZoneStart) {
            float dist = hitZoneStart - sliderPos;
            accuracy = 1.0f - Math.min(1.0f, dist / 0.3f);
        } else {
            float dist = sliderPos - hitZoneEnd;
            accuracy = 1.0f - Math.min(1.0f, dist / 0.2f);
        }
        accuracy = Math.max(0.0f, accuracy);

        if (accuracy < 0.9f) {
            consecutiveSuccesses = 0;
            startShake();
        } else {
            consecutiveSuccesses++;
        }

        if (consecutiveSuccesses >= REQUIRED_SUCCESSES) {
            charging = false;
            return accuracy;
        }

        resetSlider();
        return accuracy;
    }

    private static void startShake() {
        shakeEndTime = System.currentTimeMillis() + SHAKE_DURATION_MS;
    }

    public static void showSummary(float delay, float strength, float neural) {
        summaryDelay = delay;
        summaryStrength = strength;
        summaryNeural = neural;
        summaryVisible = true;
        summaryShowTime = System.currentTimeMillis();
        charging = false;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (summaryVisible && System.currentTimeMillis() - summaryShowTime > SUMMARY_DISPLAY_MS) {
            summaryVisible = false;
        }

        if (!charging) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            cancelCharge();
            return;
        }

        if (!selfMode && targetEntityId >= 0) {
            Entity target = mc.level.getEntity(targetEntityId);
            if (target == null || !target.isAlive() || player.distanceTo(target) > MAX_DISTANCE) {
                cancelCharge();
                return;
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        sliderPos = Math.min(1.0f, elapsed / 2000f);
        updateTargetName();

        if (sliderPos >= 1.0f && !missedTiming) {
            missedTiming = true;
            missedTime = System.currentTimeMillis();
            startShake();
            consecutiveSuccesses = 0;
        }

        if (BioForgeKeyBindings.REFLEX_STRIKE.consumeClick()) {
            if (!(player.getMainHandItem().getItem() instanceof ReflexHammerItem) &&
                    !(player.getOffhandItem().getItem() instanceof ReflexHammerItem)) {
                cancelCharge();
                return;
            }
            float accuracy = onStrike();
            if (accuracy >= 0) {
                ReflexHammerNetworkHandler.sendStrike(
                        selfMode ? -1 : targetEntityId,
                        accuracy,
                        consecutiveSuccesses
                );
            }
        }
    }

    private static void updateTargetName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { targetName = ""; return; }
        if (selfMode) {
            targetName = net.minecraft.network.chat.Component.translatable(
                    "gui.bioforge.reflex_hammer.self").getString();
            return;
        }
        if (targetEntityId >= 0) {
            Entity e = mc.level.getEntity(targetEntityId);
            if (e instanceof LivingEntity) {
                targetName = e.getDisplayName().getString();
                return;
            }
        }
        targetName = "";
    }
}
