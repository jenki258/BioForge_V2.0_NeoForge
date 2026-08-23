package net.jenkimods.bioforge.infection.naming;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.jenkimods.bioforge.network.compat.NetworkEvent;

import java.util.function.Supplier;

public record StrainNameSubmitPacket(String fingerprint, String name) {
    public static void encode(StrainNameSubmitPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.fingerprint(), 64);
        buffer.writeUtf(packet.name(), StrainNamingManager.MAX_NAME_LENGTH);
    }

    public static StrainNameSubmitPacket decode(FriendlyByteBuf buffer) {
        return new StrainNameSubmitPacket(buffer.readUtf(64),
                buffer.readUtf(StrainNamingManager.MAX_NAME_LENGTH));
    }

    public static void handle(StrainNameSubmitPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> {
                StrainNamingManager.NamingResult result =
                        StrainNamingManager.submitFirstName(
                                player, packet.fingerprint(), packet.name());
                String key = switch (result) {
                    case NAMED -> "message.bioforge.strain.gui_named";
                    case INVALID -> "message.bioforge.strain.gui_invalid";
                    case DUPLICATE -> "message.bioforge.strain.gui_duplicate";
                    case LOCKED -> "message.bioforge.strain.gui_locked";
                };
                player.displayClientMessage(Component.translatable(key)
                        .withStyle(result == StrainNamingManager.NamingResult.NAMED
                                ? ChatFormatting.AQUA : ChatFormatting.RED), true);
                if (result != StrainNamingManager.NamingResult.NAMED) {
                    StrainNameNetworkHandler.prompt(player, packet.fingerprint());
                }
            });
        }
        context.setPacketHandled(true);
    }
}
