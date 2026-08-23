package net.jenkimods.bioforge.item.guide;

import net.jenkimods.bioforge.client.ResearchJournalClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.jenkimods.bioforge.network.compat.NetworkEvent;

import java.util.function.Supplier;

public record ResearchJournalUnlockPacket(ResourceLocation pageId, Component title,
                                          boolean activated) {
    public static void encode(ResearchJournalUnlockPacket packet, RegistryFriendlyByteBuf buffer) {
        buffer.writeResourceLocation(packet.pageId());
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, packet.title());
        buffer.writeBoolean(packet.activated());
    }

    public static ResearchJournalUnlockPacket decode(RegistryFriendlyByteBuf buffer) {
        return new ResearchJournalUnlockPacket(buffer.readResourceLocation(),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer), buffer.readBoolean());
    }

    public static void handle(ResearchJournalUnlockPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientHandler.handle(packet));
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientHandler {
        private static void handle(ResearchJournalUnlockPacket packet) {
            ResearchJournalClient.showUnlock(packet.title(), packet.activated());
        }
    }
}
