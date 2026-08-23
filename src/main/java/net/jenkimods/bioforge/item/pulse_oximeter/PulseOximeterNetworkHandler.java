package net.jenkimods.bioforge.item.pulse_oximeter;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.item.clipboard.ClipboardHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.jenkimods.bioforge.network.compat.NetworkDirection;
import net.jenkimods.bioforge.network.compat.NetworkEvent;
import net.jenkimods.bioforge.network.compat.NetworkRegistry;
import net.jenkimods.bioforge.network.compat.PacketDistributor;
import net.jenkimods.bioforge.network.compat.SimpleChannel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class PulseOximeterNetworkHandler {
    private static final String PROTOCOL = "1";
    private static SimpleChannel CHANNEL;
    private static int id = 0;

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                ResourceLocation.tryBuild(BioForge.MODID, "pulse_oximeter"),
                () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
        CHANNEL.registerMessage(id++, PulseOximeterRequestPacket.class,
                PulseOximeterRequestPacket::encode,
                PulseOximeterRequestPacket::decode,
                PulseOximeterRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, PulseOximeterDataPacket.class,
                PulseOximeterDataPacket::encode,
                PulseOximeterDataPacket::decode,
                PulseOximeterDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendRequest(int entityId) {
        CHANNEL.sendToServer(new PulseOximeterRequestPacket(entityId));
    }

    public static void sendData(ServerPlayer player, float o2, float perf, boolean self, int entityId, String name) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PulseOximeterDataPacket(o2, perf, self, entityId, name));
    }

    public static class PulseOximeterRequestPacket {
        int entityId;
        public PulseOximeterRequestPacket(int entityId) { this.entityId = entityId; }
        public static void encode(PulseOximeterRequestPacket msg, FriendlyByteBuf buf) { buf.writeInt(msg.entityId); }
        public static PulseOximeterRequestPacket decode(FriendlyByteBuf buf) { return new PulseOximeterRequestPacket(buf.readInt()); }
        public static void handle(PulseOximeterRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ctx.get().getSender();
                if (sender == null) return;
                LivingEntity subject = null;
                if (msg.entityId == -1) {
                    subject = sender;
                } else {
                    Entity e = sender.level().getEntity(msg.entityId);
                    if (e instanceof LivingEntity le) subject = le;
                }
                if (subject == null) {
                    return;
                }

                InfectionData data = InfectionCapability.get(subject);
                float o2 = data != null ? data.getSymptom(BioForgeSymptoms.OXYGEN_SATURATION) : 0.95f;
                float perf = data != null ? data.getSymptom(BioForgeSymptoms.PERFUSION_INDEX) : 0.7f;
                String name = (msg.entityId != -1) ? subject.getDisplayName().getString() : "";
                sendData(sender, o2, perf, msg.entityId == -1, msg.entityId, name);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class PulseOximeterDataPacket {
        float oxygen, perfusion;
        boolean self;
        int entityId;
        String targetName;

        public PulseOximeterDataPacket(float o2, float perf, boolean self, int entityId, String targetName) {
            oxygen = o2; perfusion = perf; this.self = self; this.entityId = entityId; this.targetName = targetName;
        }
        public static void encode(PulseOximeterDataPacket msg, FriendlyByteBuf buf) {
            buf.writeFloat(msg.oxygen);
            buf.writeFloat(msg.perfusion);
            buf.writeBoolean(msg.self);
            buf.writeInt(msg.entityId);
            buf.writeUtf(msg.targetName);
        }
        public static PulseOximeterDataPacket decode(FriendlyByteBuf buf) {
            return new PulseOximeterDataPacket(buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readInt(), buf.readUtf());
        }
        public static void handle(PulseOximeterDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientHandler.handle(msg));
            ctx.get().setPacketHandled(true);
        }

        @OnlyIn(Dist.CLIENT)
        private static final class ClientHandler {
            private static void handle(PulseOximeterDataPacket msg) {
                PulseOximeterClientHandler.startInspection(msg.oxygen, msg.perfusion,
                        msg.self, msg.entityId, msg.targetName);
            }
        }
    }
}
