package net.jenkimods.bioforge.world.decoration;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.network.compat.NetworkEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

public record BlackSteelTilesCyclePacket(BlockPos pos, Direction face, boolean copy) {
    public static void encode(BlackSteelTilesCyclePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeEnum(packet.face);
        buffer.writeBoolean(packet.copy);
    }

    public static BlackSteelTilesCyclePacket decode(FriendlyByteBuf buffer) {
        return new BlackSteelTilesCyclePacket(buffer.readBlockPos(),
                buffer.readEnum(Direction.class), buffer.readBoolean());
    }

    public static void handle(BlackSteelTilesCyclePacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.getMainHandItem().is(BioForge.BLACK_STEEL_TILES_ITEM.get())) return;
            if (player.distanceToSqr(Vec3.atCenterOf(packet.pos)) > 64.0D) return;
            if (!player.level().getBlockState(packet.pos).is(BioForge.BLACK_STEEL_TILES.get())) return;
            if (player.level().getBlockEntity(packet.pos) instanceof BlackSteelTilesBlockEntity tiles) {
                if (packet.copy) {
                    tiles.copyVariantsTo(player.getMainHandItem());
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "block.bioforge.black_steel_tiles.copied"), true);
                } else {
                    tiles.cycleVariant(packet.face);
                }
                player.level().playSound(null, packet.pos, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
                        SoundSource.BLOCKS, 0.35F, 1.25F);
            }
        });
        context.setPacketHandled(true);
    }
}
