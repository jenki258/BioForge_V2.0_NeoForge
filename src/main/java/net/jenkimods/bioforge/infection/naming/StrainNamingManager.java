package net.jenkimods.bioforge.infection.naming;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Optional;

@EventBusSubscriber(modid = BioForge.MODID)
public final class StrainNamingManager {
    public static final int MAX_NAME_LENGTH = 48;

    public enum NamingResult {
        NAMED,
        INVALID,
        DUPLICATE,
        LOCKED
    }

    private StrainNamingManager() {}

    public static void discoverResearch(ServerPlayer researcher, String fingerprint) {
        if (researcher == null || fingerprint == null || fingerprint.isBlank()) return;
        ServerLevel level = researcher.serverLevel();
        StrainNameStore.Discovery discovery = StrainNameStore.get(level).discover(
                fingerprint, researcher.getUUID(),
                researcher.getGameProfile().getName(), level.getGameTime());
        if (discovery.newlyDiscovered() || discovery.researcherAssigned()) {
            StrainNameNetworkHandler.syncAll(level.getServer());
        }
        if (discovery.researcherAssigned() && !discovery.entry().isNamed()) {
            researcher.sendSystemMessage(Component.translatable(
                    "message.bioforge.strain.discovered", fingerprint)
                    .withStyle(ChatFormatting.AQUA));
            StrainNameNetworkHandler.prompt(researcher, fingerprint);
        }
    }

    public static Optional<String> getName(ServerLevel level, String fingerprint) {
        return StrainNameStore.get(level).find(fingerprint)
                .filter(StrainNameStore.Entry::isNamed)
                .map(StrainNameStore.Entry::name);
    }

    public static String displayName(ServerLevel level, String fingerprint) {
        return getName(level, fingerprint).orElse("Strain " + fingerprint);
    }

    public static Optional<String> getClientName(String fingerprint) {
        return StrainNameClientCache.find(fingerprint);
    }

    public static NamingResult submitFirstName(ServerPlayer player, String fingerprint,
                                                String input) {
        String name = sanitizeName(input);
        if (name == null) return NamingResult.INVALID;
        StrainNameStore store = StrainNameStore.get(player.serverLevel());
        StrainNameStore.Entry entry = store.find(fingerprint).orElse(null);
        if (entry == null || entry.isNamed() || entry.researcherId() == null
                || !player.getUUID().equals(entry.researcherId())) {
            return NamingResult.LOCKED;
        }
        if (store.isNameTaken(name, fingerprint)) return NamingResult.DUPLICATE;
        if (!store.nameFirstDiscovery(fingerprint, player.getUUID(), name)) {
            return NamingResult.LOCKED;
        }
        StrainNameNetworkHandler.syncAll(player.server);
        return NamingResult.NAMED;
    }

    public static String sanitizeName(String input) {
        if (input == null) return null;
        String stripped = ChatFormatting.stripFormatting(input.replace('\u00A7', ' '));
        if (stripped == null) return null;
        String cleaned = stripped.replaceAll("[\\p{Cntrl}]", " ")
                .trim().replaceAll("\\s+", " ");
        return cleaned.isBlank() || cleaned.length() > MAX_NAME_LENGTH
                ? null : cleaned;
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StrainNameNetworkHandler.sync(player);
            StrainNameStore.get(player.serverLevel()).entries().stream()
                    .filter(entry -> !entry.isNamed())
                    .filter(entry -> player.getUUID().equals(entry.researcherId()))
                    .findFirst().ifPresent(entry -> StrainNameNetworkHandler.prompt(
                            player, entry.fingerprint()));
        }
    }
}
