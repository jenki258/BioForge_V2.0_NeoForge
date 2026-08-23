package net.jenkimods.bioforge.item.pulse_oximeter;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = BioForge.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public class PulseOximeterClientHandler {
    private static boolean inspecting = false;
    private static float oxygenSaturation = 0.95f;
    private static float perfusionIndex = 0.7f;
    private static float quality = 1.0f;
    private static long startTime;
    private static boolean selfMode;
    private static int targetEntityId = -1;
    private static String targetName = "";

    private static double lastPlayerX, lastPlayerY, lastPlayerZ;
    private static double lastTargetX, lastTargetY, lastTargetZ;
    private static long lastMoveCheck = 0;


    public static void startInspection(float o2, float perf, boolean self, int entityId, String name) {
        if (!inspecting) {
            inspecting = true;
            startTime = System.currentTimeMillis();
            quality = 1.0f;

            Player player = Minecraft.getInstance().player;
            if (player != null) {
                lastPlayerX = player.getX();
                lastPlayerY = player.getY();
                lastPlayerZ = player.getZ();
            }
            if (!self && entityId >= 0) {
                Entity e = Minecraft.getInstance().level.getEntity(entityId);
                if (e != null) {
                    lastTargetX = e.getX();
                    lastTargetY = e.getY();
                    lastTargetZ = e.getZ();
                }
            }
            lastMoveCheck = System.currentTimeMillis();
        }

        oxygenSaturation = o2;
        perfusionIndex = perf;
        selfMode = self;
        targetEntityId = entityId;
        targetName = name;
    }

    public static void stopInspection() {
        inspecting = false;
        PulseOximeterOverlay.resetWaveform();
    }
    public static boolean isInspecting() { return inspecting; }
    public static float getOxygenSaturation() { return oxygenSaturation; }
    public static float getPerfusionIndex() { return perfusionIndex; }
    public static float getQuality() { return quality; }
    public static boolean isSelfMode() { return selfMode; }
    public static String getTargetName() { return targetName; }
    public static long getStartTime() { return startTime; }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!inspecting) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        long elapsed = System.currentTimeMillis() - startTime;
        float stabilization = Math.min(1.0f, elapsed / 8000f);
        quality = 0.3f + stabilization * 0.7f;

        if (System.currentTimeMillis() - lastMoveCheck > 500) {
            double playerMoved = distance(player.getX(), player.getY(), player.getZ(),
                    lastPlayerX, lastPlayerY, lastPlayerZ);
            double targetMoved = 0;
            if (!selfMode && targetEntityId >= 0) {
                Entity e = mc.level.getEntity(targetEntityId);
                if (e != null) {
                    targetMoved = distance(e.getX(), e.getY(), e.getZ(),
                            lastTargetX, lastTargetY, lastTargetZ);
                }
            }
            double totalMove = playerMoved + targetMoved;
            float movePenalty = (float)Math.min(1.0, totalMove * 2.0);
            quality -= movePenalty * 0.5f;
            quality = Math.max(0.1f, Math.min(1.0f, quality));

            lastPlayerX = player.getX();
            lastPlayerY = player.getY();
            lastPlayerZ = player.getZ();
            if (!selfMode && targetEntityId >= 0) {
                Entity e = mc.level.getEntity(targetEntityId);
                if (e != null) {
                    lastTargetX = e.getX();
                    lastTargetY = e.getY();
                    lastTargetZ = e.getZ();
                }
            }
            lastMoveCheck = System.currentTimeMillis();
        }
    }

    private static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Math.sqrt((x1-x2)*(x1-x2) + (y1-y2)*(y1-y2) + (z1-z2)*(z1-z2));
    }
}
