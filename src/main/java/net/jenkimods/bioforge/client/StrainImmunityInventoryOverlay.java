package net.jenkimods.bioforge.client;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.infection.InfectionClientCache;
import net.jenkimods.bioforge.infection.StrainImmunity;
import net.jenkimods.bioforge.infection.naming.StrainNameClientCache;
import net.jenkimods.bioforge.registry.BioForgeEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;


@EventBusSubscriber(modid = BioForge.MODID, value = Dist.CLIENT)
public final class StrainImmunityInventoryOverlay {
    private static final int SIZE = 18;
    private static List<StrainImmunity> lastSnapshot = List.of();
    private static long snapshotGameTime;
    private static InventoryScreen effectScreen;
    private static int effectX;
    private static boolean compactEffects;

    private StrainImmunityInventoryOverlay() {}

    @SubscribeEvent
    public static void onScreenPre(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof InventoryScreen) effectScreen = null;
    }

    @SubscribeEvent
    public static void onEffectLayout(ScreenEvent.RenderInventoryMobEffects event) {
        if (event.getScreen() instanceof InventoryScreen inventoryScreen) {
            effectScreen = inventoryScreen;
            effectX = event.getHorizontalOffset();
            compactEffects = event.isCompact();
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen)) return;
        Minecraft minecraft = Minecraft.getInstance();
        List<StrainImmunity> source = InfectionClientCache.getStrainImmunities();
        if (source != lastSnapshot) {
            lastSnapshot = source;
            snapshotGameTime = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        }
        long elapsed = minecraft.level == null ? 0L
                : Math.max(0L, minecraft.level.getGameTime() - snapshotGameTime);
        List<StrainImmunity> immunities = source.stream()
                .map(immunity -> new StrainImmunity(immunity.fingerprint(),
                        immunity.displayName(),
                        (int) Math.max(0L, immunity.remainingTicks() - elapsed),
                        immunity.strength()))
                .filter(StrainImmunity::isActive)
                .sorted(Comparator.comparing(StrainImmunity::displayName))
                .toList();
        if (immunities.isEmpty()) return;

        renderEffectHover(event, immunities, minecraft);

        GuiGraphics graphics = event.getGuiGraphics();
        int x = graphics.guiWidth() - SIZE - 8;
        int y = 8;
        graphics.fill(x, y, x + SIZE, y + SIZE, 0xD0203B3B);
        graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, 0xD0377F76);
        graphics.drawCenteredString(minecraft.font, "I",
                x + SIZE / 2, y + 5, 0xFFFFFFFF);

        if (event.getMouseX() < x || event.getMouseX() >= x + SIZE
                || event.getMouseY() < y || event.getMouseY() >= y + SIZE) return;

        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("gui.bioforge.immunity.title", immunities.size())
                .withStyle(ChatFormatting.AQUA));
        for (StrainImmunity immunity : immunities) {
            tooltip.add(protectionEntry(immunity));
        }
        graphics.renderTooltip(minecraft.font, tooltip, Optional.empty(),
                event.getMouseX(), event.getMouseY());
    }

    private static void renderEffectHover(ScreenEvent.Render.Post event,
                                          List<StrainImmunity> immunities,
                                          Minecraft minecraft) {
        if (effectScreen != event.getScreen() || minecraft.player == null) return;
        List<MobEffectInstance> effects = minecraft.player.getActiveEffects().stream()
                .filter(MobEffectInstance::showIcon)
                .sorted()
                .toList();
        int immunityIndex = -1;
        for (int index = 0; index < effects.size(); index++) {
            if (effects.get(index).getEffect().is(BioForgeEffects.STRAIN_IMMUNITY.getKey())) {
                immunityIndex = index;
                break;
            }
        }
        if (immunityIndex < 0) return;
        int spacing = minecraft.player.getActiveEffects().size() > 5
                ? 132 / Math.max(1, minecraft.player.getActiveEffects().size() - 1) : 33;
        int y = effectScreen.getGuiTop() + immunityIndex * spacing;
        int width = compactEffects ? 33 : 120;
        if (event.getMouseX() < effectX || event.getMouseX() > effectX + width
                || event.getMouseY() < y || event.getMouseY() > y + 32) return;
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("gui.bioforge.immunity.title", immunities.size())
                .withStyle(ChatFormatting.AQUA));
        for (StrainImmunity immunity : immunities) {
            tooltip.add(protectionEntry(immunity));
        }
        event.getGuiGraphics().renderTooltip(minecraft.font, tooltip, Optional.empty(),
                event.getMouseX(), event.getMouseY());
    }

    private static Component immunityName(StrainImmunity immunity) {
        Optional<String> catalogued = StrainNameClientCache.find(immunity.fingerprint());
        if (catalogued.isPresent()) return Component.literal(catalogued.get());
        String stored = immunity.displayName();
        if (stored.isBlank() || stored.equals(immunity.fingerprint())
                || stored.equals("Strain " + immunity.fingerprint())) {
            return Component.translatable("gui.bioforge.immunity.uncatalogued");
        }
        return Component.literal(stored);
    }

    private static Component protectionEntry(StrainImmunity immunity) {
        if (immunity.strength() >= 0.999F) {
            return Component.translatable("gui.bioforge.immunity.entry",
                    immunityName(immunity),
                    StringUtil.formatTickDuration(immunity.remainingTicks(), 20.0F))
                    .withStyle(ChatFormatting.GRAY);
        }
        return Component.translatable("gui.bioforge.resistance.entry",
                immunityName(immunity),
                Math.round(immunity.strength() * 100.0F),
                StringUtil.formatTickDuration(immunity.remainingTicks(), 20.0F))
                .withStyle(ChatFormatting.GOLD);
    }
}
