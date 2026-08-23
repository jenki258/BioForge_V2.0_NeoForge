package net.jenkimods.bioforge.world.vaccine;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.vaccine.VaccineCorrectionProfile;
import net.jenkimods.bioforge.vaccine.VaccineCorrectionState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.jenkimods.bioforge.network.compat.NetworkDirection;
import net.jenkimods.bioforge.network.compat.NetworkEvent;
import net.jenkimods.bioforge.network.compat.NetworkRegistry;
import net.jenkimods.bioforge.network.compat.PacketDistributor;
import net.jenkimods.bioforge.network.compat.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class VaccineMakerCorrectionNetwork {
    private static final String PROTOCOL = "4";
    private static final int MAX_TARGETS = 4096;
    private static final int MAX_STATES = 100001;
    private static SimpleChannel channel;
    private static volatile Snapshot clientSnapshot = Snapshot.EMPTY;

    private VaccineMakerCorrectionNetwork() {}

    public static void register() {
        channel = NetworkRegistry.newSimpleChannel(
                ResourceLocation.tryBuild(BioForge.MODID, "vaccine_correction"),
                () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
        channel.registerMessage(
                0, SyncPacket.class, SyncPacket::encode, SyncPacket::decode,
                SyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(
                1, SetSelectionPacket.class,
                SetSelectionPacket::encode, SetSelectionPacket::decode,
                SetSelectionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void send(ServerPlayer player, int containerId,
                            int targetsPerPage,
                            List<VaccineCorrectionState.Target> targets) {
        channel.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncPacket(containerId, targetsPerPage, targets));
    }

    public static Snapshot snapshot(int containerId) {
        Snapshot snapshot = clientSnapshot;
        return snapshot.containerId() == containerId ? snapshot : Snapshot.EMPTY;
    }

    public static void clearClientSnapshot(int containerId) {
        if (clientSnapshot.containerId() == containerId) {
            clientSnapshot = Snapshot.EMPTY;
        }
    }

    public static void setSelection(int containerId, int targetIndex, int state) {
        if (channel != null && targetIndex >= 0 && state >= 0) {
            channel.sendToServer(new SetSelectionPacket(
                    containerId, targetIndex, state));
        }
    }

    public record Snapshot(
            int containerId,
            int targetsPerPage,
            List<VaccineCorrectionState.Target> targets
    ) {
        private static final Snapshot EMPTY = new Snapshot(-1, 6, List.of());

        public Snapshot {
            targetsPerPage = Math.max(1, Math.min(6, targetsPerPage));
            targets = List.copyOf(targets);
        }

        public boolean available() {
            return containerId >= 0;
        }
    }

    private record SyncPacket(
            int containerId,
            int targetsPerPage,
            List<VaccineCorrectionState.Target> targets
    ) {
        private SyncPacket {
            targets = List.copyOf(targets);
        }

        private static void encode(SyncPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            buffer.writeVarInt(packet.targetsPerPage);
            buffer.writeVarInt(packet.targets.size());
            for (VaccineCorrectionState.Target target : packet.targets) {
                buffer.writeEnum(target.family());
                buffer.writeUtf(target.id(), 256);
                buffer.writeVarInt(target.states());
                buffer.writeVarInt(target.selectedState());
                buffer.writeEnum(target.valueKind());
                buffer.writeFloat(target.displayMinimum());
                buffer.writeFloat(target.displayMaximum());
            }
        }

        private static SyncPacket decode(FriendlyByteBuf buffer) {
            int containerId = buffer.readVarInt();
            int targetsPerPage = buffer.readVarInt();
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_TARGETS) {
                throw new IllegalArgumentException(
                        "Invalid vaccine correction target count: " + count);
            }
            List<VaccineCorrectionState.Target> targets = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                VaccineCorrectionProfile.TargetFamily family =
                        buffer.readEnum(VaccineCorrectionProfile.TargetFamily.class);
                String id = buffer.readUtf(256);
                int states = Math.max(
                        2, Math.min(MAX_STATES, buffer.readVarInt()));
                int selected = Math.max(0, Math.min(
                        states - 1, buffer.readVarInt()));
                VaccineCorrectionState.ValueKind valueKind =
                        buffer.readEnum(VaccineCorrectionState.ValueKind.class);
                float displayMinimum = buffer.readFloat();
                float displayMaximum = buffer.readFloat();
                targets.add(new VaccineCorrectionState.Target(
                        family, id, states, selected, valueKind,
                        displayMinimum, displayMaximum));
            }
            return new SyncPacket(containerId, targetsPerPage, targets);
        }

        private static void handle(
                SyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> clientSnapshot = new Snapshot(
                    packet.containerId, packet.targetsPerPage, packet.targets));
            context.setPacketHandled(true);
        }
    }

    private record SetSelectionPacket(int containerId, int targetIndex, int state) {
        private static void encode(SetSelectionPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.containerId);
            buffer.writeVarInt(packet.targetIndex);
            buffer.writeVarInt(packet.state);
        }

        private static SetSelectionPacket decode(FriendlyByteBuf buffer) {
            return new SetSelectionPacket(
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        }

        private static void handle(SetSelectionPacket packet,
                                   Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayer sender = context.getSender();
            context.enqueueWork(() -> {
                if (sender == null || packet.targetIndex < 0
                        || packet.state < 0 || packet.state >= MAX_STATES
                        || !(sender.containerMenu instanceof VaccineMakerMenu menu)
                        || menu.containerId != packet.containerId) return;
                menu.getBlockEntity().setCorrectionSelection(
                        packet.targetIndex, packet.state);
            });
            context.setPacketHandled(true);
        }
    }
}
