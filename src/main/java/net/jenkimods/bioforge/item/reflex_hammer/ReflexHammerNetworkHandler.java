package net.jenkimods.bioforge.item.reflex_hammer;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.item.clipboard.ClipboardHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.jenkimods.bioforge.network.compat.NetworkDirection;
import net.jenkimods.bioforge.network.compat.NetworkEvent;
import net.jenkimods.bioforge.network.compat.NetworkRegistry;
import net.jenkimods.bioforge.network.compat.PacketDistributor;
import net.jenkimods.bioforge.network.compat.SimpleChannel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.*;
import java.util.function.Supplier;

public class ReflexHammerNetworkHandler {

    private static final String PROTOCOL = "1";
    private static SimpleChannel CHANNEL;
    private static int id = 0;

    private static final Map<UUID, Map<Integer, List<Float>>> playerStrikes = new HashMap<>();

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                ResourceLocation.tryBuild(BioForge.MODID, "reflex_hammer"),
                () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals
        );
        CHANNEL.registerMessage(id++, ReflexStrikePacket.class,
                ReflexStrikePacket::encode,
                ReflexStrikePacket::decode,
                ReflexStrikePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ReflexSummaryPacket.class,
                ReflexSummaryPacket::encode,
                ReflexSummaryPacket::decode,
                ReflexSummaryPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendStrike(int entityId, float accuracy, int successes) {
        CHANNEL.sendToServer(new ReflexStrikePacket(entityId, accuracy, successes));
    }

    public static void sendSummary(ServerPlayer player, float delay, float strength, float neural) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ReflexSummaryPacket(delay, strength, neural));
    }

    public static class ReflexStrikePacket {
        int entityId;
        float accuracy;
        int successes;

        public ReflexStrikePacket(int entityId, float accuracy, int successes) {
            this.entityId = entityId;
            this.accuracy = accuracy;
            this.successes = successes;
        }

        public static void encode(ReflexStrikePacket msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.entityId);
            buf.writeFloat(msg.accuracy);
            buf.writeInt(msg.successes);
        }
        public static ReflexStrikePacket decode(FriendlyByteBuf buf) {
            return new ReflexStrikePacket(buf.readInt(), buf.readFloat(), buf.readInt());
        }
        public static void handle(ReflexStrikePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                Level level = player.level();
                LivingEntity target = null;
                if (msg.entityId == -1) {
                    target = player;
                } else {
                    Entity e = level.getEntity(msg.entityId);
                    if (e instanceof LivingEntity le) target = le;
                }
                if (target == null) return;

                if (target != player && player.distanceTo(target) > 4.0) {
                    return;
                }

                InfectionData data = InfectionCapability.get(target);
                float reflexDelay = 0.1f;
                float reflexStrength = 0.8f;
                float neuralDamage = 0.0f;
                if (data != null) {
                    reflexDelay = data.getSymptom(BioForgeSymptoms.REFLEX_DELAY);
                    reflexStrength = data.getSymptom(BioForgeSymptoms.REFLEX_STRENGTH);
                    neuralDamage = data.getSymptom(BioForgeSymptoms.NEURAL_DAMAGE);
                }

                float effectiveStrength = reflexStrength * msg.accuracy * (1.0f - neuralDamage * 0.5f);
                effectiveStrength = Math.max(0.0f, Math.min(1.0f, effectiveStrength));

                if (effectiveStrength > 0.6f) {
                    target.setDeltaMovement(target.getDeltaMovement().add(0, 0.35, 0));
                    level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8f, 1.2f);
                    spawnParticles(level, target, 10);
                } else if (effectiveStrength > 0.1f) {
                    target.setDeltaMovement(target.getDeltaMovement().add(0, 0.1, 0));
                    level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.4f, 1.5f);
                    spawnParticles(level, target, 3);
                }

                UUID playerId = player.getUUID();
                int tId = msg.entityId;
                Map<Integer, List<Float>> targetMap = playerStrikes.computeIfAbsent(playerId, k -> new HashMap<>());
                List<Float> strengths = targetMap.computeIfAbsent(tId, k -> new ArrayList<>());

                if (msg.accuracy >= 0.9f) {
                    strengths.add(effectiveStrength);
                    if (strengths.size() >= 5) {
                        sendSummary(player, reflexDelay, reflexStrength, neuralDamage);
                        String delayStr = reflexDelay > 0.3f ? "High" : (reflexDelay > 0.15f ? "Moderate" : "Low");
                        String strengthStr = reflexStrength > 0.7f ? "High" : (reflexStrength > 0.3f ? "Moderate" : "Low");
                        ClipboardHelper.recordReflex(delayStr, strengthStr, false, player, target.getUUID());
                        strengths.clear();
                    }
                } else {
                    strengths.clear();
                }
            });
            ctx.get().setPacketHandled(true);
        }

        private static void spawnParticles(Level level, LivingEntity target, int count) {
            if (!level.isClientSide()) return;
            for (int i = 0; i < count; i++) {
                level.addParticle(ParticleTypes.CLOUD, target.getX(), target.getY()+0.5, target.getZ(), 0, 0.1, 0);
            }
        }
    }

    public static class ReflexSummaryPacket {
        float delay, strength, neural;

        public ReflexSummaryPacket(float delay, float strength, float neural) {
            this.delay = delay;
            this.strength = strength;
            this.neural = neural;
        }

        public static void encode(ReflexSummaryPacket msg, FriendlyByteBuf buf) {
            buf.writeFloat(msg.delay);
            buf.writeFloat(msg.strength);
            buf.writeFloat(msg.neural);
        }
        public static ReflexSummaryPacket decode(FriendlyByteBuf buf) {
            return new ReflexSummaryPacket(buf.readFloat(), buf.readFloat(), buf.readFloat());
        }
        public static void handle(ReflexSummaryPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientHandler.handle(msg));
            ctx.get().setPacketHandled(true);
        }

        @OnlyIn(Dist.CLIENT)
        private static final class ClientHandler {
            private static void handle(ReflexSummaryPacket msg) {
                ReflexHammerClientHandler.showSummary(msg.delay, msg.strength, msg.neural);
            }
        }
    }
}
