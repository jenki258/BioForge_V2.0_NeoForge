package net.jenkimods.bioforge.blood.network;

import net.jenkimods.bioforge.blood.BloodClientCache;
import net.jenkimods.bioforge.blood.BloodType;
import net.minecraft.network.FriendlyByteBuf;

import net.jenkimods.bioforge.network.compat.NetworkEvent;

import java.util.function.Supplier;


public class BloodSyncPacket {

    private final int blood;
    private final BloodType bloodType;

    public BloodSyncPacket(int blood, BloodType bloodType) {
        this.blood     = blood;
        this.bloodType = bloodType;
    }

    public static void encode(BloodSyncPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.blood);
        buf.writeUtf(pkt.bloodType.name());
    }

    public static BloodSyncPacket decode(FriendlyByteBuf buf) {
        int       blood     = buf.readInt();
        BloodType bloodType = BloodType.fromName(buf.readUtf());
        return new BloodSyncPacket(blood, bloodType);
    }

    public static void handle(BloodSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            BloodClientCache.set(pkt.blood, pkt.bloodType);
        });
        ctx.setPacketHandled(true);
    }
}