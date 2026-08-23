package net.jenkimods.bioforge.item.stethoscope;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.infection.*;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.jenkimods.bioforge.network.compat.NetworkDirection;
import net.jenkimods.bioforge.network.compat.NetworkRegistry;
import net.jenkimods.bioforge.network.compat.PacketDistributor;
import net.jenkimods.bioforge.network.compat.SimpleChannel;
import net.jenkimods.bioforge.network.compat.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

public class StethoscopeNetworkHandler {

    private static final String PROTOCOL = "1";
    private static SimpleChannel CHANNEL;

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                ResourceLocation.tryBuild(BioForge.MODID, "stethoscope"),
                () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
        CHANNEL.registerMessage(0, StethoscopeRequestPacket.class,
                StethoscopeRequestPacket::encode,
                StethoscopeRequestPacket::decode,
                StethoscopeRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, StethoscopeReadingPacket.class,
                StethoscopeReadingPacket::encode,
                StethoscopeReadingPacket::decode,
                StethoscopeReadingPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendRequest(int entityId, boolean mainHand) {
        CHANNEL.sendToServer(new StethoscopeRequestPacket(entityId, mainHand));
    }

    public static void sendReading(ServerPlayer player, HeartRate heartRate,
                                   LungSound lungSound, String targetName, boolean mainHand) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new StethoscopeReadingPacket(heartRate, lungSound, targetName, mainHand));
    }

    public static class StethoscopeRequestPacket {
        int entityId;
        boolean mainHand;

        public StethoscopeRequestPacket(int entityId, boolean mainHand) {
            this.entityId = entityId;
            this.mainHand = mainHand;
        }

        public static void encode(StethoscopeRequestPacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.entityId);
            buf.writeBoolean(msg.mainHand);
        }
        public static StethoscopeRequestPacket decode(FriendlyByteBuf buf) {
            return new StethoscopeRequestPacket(buf.readInt(), buf.readBoolean());
        }

        public static void handle(StethoscopeRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                LivingEntity target = null;
                if (msg.entityId == -1) {
                    target = player;
                } else {
                    Entity e = player.level().getEntity(msg.entityId);
                    if (e instanceof LivingEntity le) target = le;
                }
                if (target == null) return;

                InfectionData data = InfectionCapability.get(target);
                HeartRate heartRate = data != null
                        ? data.getSymptom(BioForgeSymptoms.HEART_RATE) : HeartRate.NORMAL;
                LungSound lungSound = data != null
                        ? data.getSymptom(BioForgeSymptoms.LUNG_SOUND) : LungSound.NORMAL;

                String targetName = "";
                if (msg.entityId != -1) {
                    targetName = target.getDisplayName().getString();
                }

                sendReading(player, heartRate, lungSound, targetName, msg.mainHand);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}