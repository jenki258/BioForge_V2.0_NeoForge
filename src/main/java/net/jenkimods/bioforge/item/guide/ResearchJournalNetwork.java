package net.jenkimods.bioforge.item.guide;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.jenkimods.bioforge.network.compat.NetworkDirection;
import net.jenkimods.bioforge.network.compat.NetworkRegistry;
import net.jenkimods.bioforge.network.compat.PacketDistributor;
import net.jenkimods.bioforge.network.compat.SimpleChannel;

import java.util.Optional;

public final class ResearchJournalNetwork {
    private static final String PROTOCOL = "4";
    private static SimpleChannel channel;

    private ResearchJournalNetwork() {}

    public static void register() {
        channel = NetworkRegistry.newSimpleChannel(
                ResourceLocation.tryBuild(BioForge.MODID, "research_journal"),
                () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
        channel.registerMessage(0, ResearchJournalOpenPacket.class,
                ResearchJournalOpenPacket::encode,
                ResearchJournalOpenPacket::decode,
                ResearchJournalOpenPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(1, ResearchJournalUnlockPacket.class,
                ResearchJournalUnlockPacket::encode,
                ResearchJournalUnlockPacket::decode,
                ResearchJournalUnlockPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void open(ServerPlayer player) {
        channel.send(PacketDistributor.PLAYER.with(() -> player),
                new ResearchJournalOpenPacket(
                        net.jenkimods.bioforge.api.guide.ResearchJournalRegistry.createViews(
                                player, ResearchJournalProgress.unlockedPages(player),
                                ResearchJournalProgress.lockedPages(player))));
    }

    public static void unlocked(ServerPlayer player, ResourceLocation pageId,
                                Component title) {
        channel.send(PacketDistributor.PLAYER.with(() -> player),
                new ResearchJournalUnlockPacket(pageId, title, false));
    }

    public static void activated(ServerPlayer player) {
        channel.send(PacketDistributor.PLAYER.with(() -> player),
                new ResearchJournalUnlockPacket(
                        ResourceLocation.tryBuild(BioForge.MODID, "contents"),
                        Component.translatable("toast.bioforge.research_journal.ready"), true));
    }
}
