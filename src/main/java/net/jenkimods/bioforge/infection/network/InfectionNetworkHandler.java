package net.jenkimods.bioforge.infection.network;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.jenkimods.bioforge.network.compat.NetworkDirection;
import net.jenkimods.bioforge.network.compat.NetworkRegistry;
import net.jenkimods.bioforge.network.compat.PacketDistributor;
import net.jenkimods.bioforge.network.compat.SimpleChannel;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InfectionNetworkHandler {

    private static final String PROTOCOL = "5";
    private static SimpleChannel CHANNEL;
    private static int id = 0;
    private static final Map<UUID, Long> DEFINITION_GENERATIONS = new HashMap<>();

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                ResourceLocation.tryBuild(BioForge.MODID, "infection"),
                () -> PROTOCOL,
                PROTOCOL::equals,
                PROTOCOL::equals
        );
        CHANNEL.registerMessage(id++,
                InfectionSyncPacket.class,
                InfectionSyncPacket::encode,
                InfectionSyncPacket::decode,
                InfectionSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        CHANNEL.registerMessage(id++,
                DefinitionSyncPacket.class,
                DefinitionSyncPacket::encode,
                DefinitionSyncPacket::decode,
                DefinitionSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static synchronized void sendDefinitionsIfChanged(ServerPlayer player) {
        long generation = BioForgeDefinitionManager.generation();
        if (DEFINITION_GENERATIONS.getOrDefault(player.getUUID(), -1L) == generation) return;
        sendToPlayer(DefinitionSyncPacket.current(), player);
        DEFINITION_GENERATIONS.put(player.getUUID(), generation);
    }

    public static synchronized void clearDefinitionSyncState() {
        DEFINITION_GENERATIONS.clear();
    }
}
