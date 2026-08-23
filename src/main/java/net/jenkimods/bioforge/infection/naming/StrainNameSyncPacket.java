package net.jenkimods.bioforge.infection.naming;

import net.minecraft.network.FriendlyByteBuf;
import net.jenkimods.bioforge.network.compat.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public record StrainNameSyncPacket(Map<String, String> names) {
    private static final int MAX_NAMES = 4096;
    private static final int MAX_FINGERPRINT_LENGTH = 64;
    private static final int MAX_NAME_LENGTH = 64;

    public StrainNameSyncPacket {
        names = Map.copyOf(names);
    }

    public static void encode(StrainNameSyncPacket packet, FriendlyByteBuf buffer) {
        int count = Math.min(MAX_NAMES, packet.names().size());
        buffer.writeVarInt(count);
        int written = 0;
        for (Map.Entry<String, String> entry : packet.names().entrySet()) {
            if (written++ >= count) break;
            buffer.writeUtf(entry.getKey(), MAX_FINGERPRINT_LENGTH);
            buffer.writeUtf(entry.getValue(), MAX_NAME_LENGTH);
        }
    }

    public static StrainNameSyncPacket decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_NAMES) {
            throw new IllegalArgumentException("Invalid strain-name count: " + count);
        }
        Map<String, String> names = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            names.put(buffer.readUtf(MAX_FINGERPRINT_LENGTH),
                    buffer.readUtf(MAX_NAME_LENGTH));
        }
        return new StrainNameSyncPacket(names);
    }

    public static void handle(StrainNameSyncPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> StrainNameClientCache.replace(packet.names()));
        context.setPacketHandled(true);
    }
}
