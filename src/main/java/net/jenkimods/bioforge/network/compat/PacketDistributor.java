package net.jenkimods.bioforge.network.compat;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.function.Supplier;

public final class PacketDistributor {
    public static final PlayerTarget PLAYER = new PlayerTarget();
    public static final AllTarget ALL = new AllTarget();

    private PacketDistributor() {
    }

    public interface PacketTarget {
        void send(CustomPacketPayload payload);
    }

    public static final class PlayerTarget {
        public PacketTarget with(Supplier<ServerPlayer> playerSupplier) {
            return payload -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    Objects.requireNonNull(playerSupplier.get(), "player"), payload);
        }
    }

    public static final class AllTarget {
        public PacketTarget noArg() {
            return net.neoforged.neoforge.network.PacketDistributor::sendToAllPlayers;
        }
    }
}
