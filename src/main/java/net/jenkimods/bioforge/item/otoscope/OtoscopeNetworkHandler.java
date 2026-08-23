package net.jenkimods.bioforge.item.otoscope;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.jenkimods.bioforge.network.compat.NetworkDirection;
import net.jenkimods.bioforge.network.compat.NetworkEvent;
import net.jenkimods.bioforge.network.compat.NetworkRegistry;
import net.jenkimods.bioforge.network.compat.PacketDistributor;
import net.jenkimods.bioforge.network.compat.SimpleChannel;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Optional;
import java.util.function.Supplier;

public class OtoscopeNetworkHandler {

    private static final String PROTOCOL = "1";
    private static SimpleChannel CHANNEL;
    private static int id = 0;

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                ResourceLocation.tryBuild(BioForge.MODID, "otoscope"),
                () -> PROTOCOL,
                PROTOCOL::equals,
                PROTOCOL::equals
        );
        CHANNEL.registerMessage(id++, OtoscopeRequestPacket.class,
                OtoscopeRequestPacket::encode,
                OtoscopeRequestPacket::decode,
                OtoscopeRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, OtoscopeDataPacket.class,
                OtoscopeDataPacket::encode,
                OtoscopeDataPacket::decode,
                OtoscopeDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendRequest(int entityId) {
        CHANNEL.sendToServer(new OtoscopeRequestPacket(entityId));
    }

    public static void sendData(ServerPlayer player, float redness, float lesions, float secretion, float swelling, boolean self, int entityId) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OtoscopeDataPacket(redness, lesions, secretion, swelling, self, entityId));
    }

    public static class OtoscopeRequestPacket {
        private final int entityId;

        public OtoscopeRequestPacket(int entityId) { this.entityId = entityId; }

        public static void encode(OtoscopeRequestPacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.entityId);
        }
        public static OtoscopeRequestPacket decode(FriendlyByteBuf buf) {
            return new OtoscopeRequestPacket(buf.readInt());
        }
        public static void handle(OtoscopeRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ctx.get().getSender();
                if (sender == null) return;
                Level level = sender.level();
                float redness = 0, lesions = 0, secretion = 0, swelling = 0;
                LivingEntity subject;
                if (msg.entityId == -1) {
                    subject = sender;
                } else {
                    Entity entity = level.getEntity(msg.entityId);
                    if (entity instanceof LivingEntity living && !living.getType().is(OtoscopeItem.NO_OTOSCOPE_TAG)) {
                        subject = living;
                    } else {
                        subject = null;
                    }
                }
                if (subject == null) {
                    return;
                }
                InfectionData data = InfectionCapability.get(subject);
                if (data != null) {
                    redness = data.getSymptom(BioForgeSymptoms.OTOSCOPE_REDNESS);
                    lesions = data.getSymptom(BioForgeSymptoms.OTOSCOPE_LESIONS);
                    secretion = data.getSymptom(BioForgeSymptoms.OTOSCOPE_SECRETION);
                    swelling = data.getSymptom(BioForgeSymptoms.OTOSCOPE_SWELLING);
                }

                sendData(sender, redness, lesions, secretion, swelling, subject == sender, msg.entityId);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class OtoscopeDataPacket {
        private final float redness, lesions, secretion, swelling;
        private final boolean self;
        private final int entityId;

        public OtoscopeDataPacket(float r, float l, float s, float w, boolean self, int entityId) {
            redness = r; lesions = l; secretion = s; swelling = w; this.self = self; this.entityId = entityId;
        }

        public static void encode(OtoscopeDataPacket msg, FriendlyByteBuf buf) {
            buf.writeFloat(msg.redness);
            buf.writeFloat(msg.lesions);
            buf.writeFloat(msg.secretion);
            buf.writeFloat(msg.swelling);
            buf.writeBoolean(msg.self);
            buf.writeInt(msg.entityId);
        }

        public static OtoscopeDataPacket decode(FriendlyByteBuf buf) {
            return new OtoscopeDataPacket(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readInt());
        }

        public static void handle(OtoscopeDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientHandler.handle(msg));
            ctx.get().setPacketHandled(true);
        }

        @OnlyIn(Dist.CLIENT)
        private static final class ClientHandler {
            private static void handle(OtoscopeDataPacket msg) {
                OtoscopeClientHandler.startInspection(msg.redness, msg.lesions,
                        msg.secretion, msg.swelling, msg.self, msg.entityId);
            }
        }
    }
}
