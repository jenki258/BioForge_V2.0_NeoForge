package net.jenkimods.bioforge.infection.naming;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.jenkimods.bioforge.network.compat.NetworkDirection;
import net.jenkimods.bioforge.network.compat.NetworkRegistry;
import net.jenkimods.bioforge.network.compat.PacketDistributor;
import net.jenkimods.bioforge.network.compat.SimpleChannel;

import java.util.Optional;

public final class StrainNameNetworkHandler {
    private static final String PROTOCOL = "2";
    private static SimpleChannel channel;

    private StrainNameNetworkHandler() {}

    public static void register() {
        channel = NetworkRegistry.newSimpleChannel(
                ResourceLocation.tryBuild(BioForge.MODID, "strain_names"),
                () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
        channel.registerMessage(0, StrainNameSyncPacket.class,
                StrainNameSyncPacket::encode, StrainNameSyncPacket::decode,
                StrainNameSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(1, StrainNamePromptPacket.class,
                StrainNamePromptPacket::encode, StrainNamePromptPacket::decode,
                StrainNamePromptPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(2, StrainNameSubmitPacket.class,
                StrainNameSubmitPacket::encode, StrainNameSubmitPacket::decode,
                StrainNameSubmitPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sync(ServerPlayer player) {
        if (channel == null) return;
        channel.send(PacketDistributor.PLAYER.with(() -> player),
                new StrainNameSyncPacket(
                        StrainNameStore.get(player.serverLevel()).namedEntries()));
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sync(player);
    }

    public static void prompt(ServerPlayer player, String fingerprint) {
        if (channel == null) return;
        channel.send(PacketDistributor.PLAYER.with(() -> player),
                new StrainNamePromptPacket(fingerprint));
    }

    public static void submit(String fingerprint, String name) {
        if (channel != null) channel.sendToServer(new StrainNameSubmitPacket(fingerprint, name));
    }
}
