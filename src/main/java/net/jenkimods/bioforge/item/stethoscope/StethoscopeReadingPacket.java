package net.jenkimods.bioforge.item.stethoscope;

import net.jenkimods.bioforge.infection.HeartRate;
import net.jenkimods.bioforge.infection.LungSound;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.jenkimods.bioforge.network.compat.NetworkEvent;

import java.util.function.Supplier;

public class StethoscopeReadingPacket {

    private final HeartRate heartRate;
    private final LungSound lungSound;
    private final String targetName;
    private final boolean mainHand;

    public StethoscopeReadingPacket(HeartRate heartRate, LungSound lungSound,
                                    String targetName, boolean mainHand) {
        this.heartRate = heartRate;
        this.lungSound = lungSound;
        this.targetName = targetName;
        this.mainHand = mainHand;
    }

    public static void encode(StethoscopeReadingPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.heartRate.name());
        buf.writeUtf(msg.lungSound.name());
        buf.writeUtf(msg.targetName);
        buf.writeBoolean(msg.mainHand);
    }

    public static StethoscopeReadingPacket decode(FriendlyByteBuf buf) {
        HeartRate heartRate = HeartRate.fromName(buf.readUtf());
        LungSound lungSound = LungSound.fromName(buf.readUtf());
        String targetName = buf.readUtf();
        boolean mainHand = buf.readBoolean();
        return new StethoscopeReadingPacket(heartRate, lungSound, targetName, mainHand);
    }

    public static void handle(StethoscopeReadingPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientHandler.handle(msg));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientHandler {

        public static void handle(StethoscopeReadingPacket msg) {
            Player player = Minecraft.getInstance().player;
            if (player == null) return;

            StethoscopeClientHandler.applyReading(
                    msg.heartRate,
                    msg.lungSound,
                    msg.targetName
            );
        }
    }
}
