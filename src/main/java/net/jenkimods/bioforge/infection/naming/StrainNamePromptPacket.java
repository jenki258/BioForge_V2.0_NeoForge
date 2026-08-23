package net.jenkimods.bioforge.infection.naming;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.jenkimods.bioforge.network.compat.NetworkEvent;

import java.util.function.Supplier;

public record StrainNamePromptPacket(String fingerprint) {
    public static void encode(StrainNamePromptPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.fingerprint(), 64);
    }

    public static StrainNamePromptPacket decode(FriendlyByteBuf buffer) {
        return new StrainNamePromptPacket(buffer.readUtf(64));
    }

    public static void handle(StrainNamePromptPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientHandler.handle(packet));
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientHandler {
        private static void handle(StrainNamePromptPacket packet) {
            StrainNameClientHandler.open(packet.fingerprint());
        }
    }
}
