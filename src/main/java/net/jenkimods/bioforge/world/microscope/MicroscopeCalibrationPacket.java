package net.jenkimods.bioforge.world.microscope;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.jenkimods.bioforge.network.compat.NetworkEvent;

import java.util.function.Supplier;

public record MicroscopeCalibrationPacket(
        BlockPos blockPos,
        int sliderIndex,
        float normalizedValue
) {
    public static void encode(MicroscopeCalibrationPacket packet,
                              FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.blockPos);
        buffer.writeVarInt(packet.sliderIndex);
        buffer.writeFloat(packet.normalizedValue);
    }

    public static MicroscopeCalibrationPacket decode(FriendlyByteBuf buffer) {
        return new MicroscopeCalibrationPacket(
                buffer.readBlockPos(),
                buffer.readVarInt(),
                buffer.readFloat()
        );
    }

    public static void handle(MicroscopeCalibrationPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null
                    || packet.sliderIndex < 0
                    || packet.sliderIndex > 63
                    || !Float.isFinite(packet.normalizedValue)
                    || !(player.containerMenu instanceof MicroscopeMenu menu)
                    || menu.getBlockEntity() == null
                    || !menu.getBlockEntity().getBlockPos().equals(packet.blockPos)
                    || player.distanceToSqr(
                            packet.blockPos.getX() + 0.5D,
                            packet.blockPos.getY() + 0.5D,
                            packet.blockPos.getZ() + 0.5D) > 64.0D) {
                return;
            }
            menu.getBlockEntity().updateVisualCalibration(
                    packet.sliderIndex, packet.normalizedValue);
        });
        context.setPacketHandled(true);
    }
}
