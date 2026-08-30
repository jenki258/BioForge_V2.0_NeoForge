package net.jenkimods.bioforge.world.decoration;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.network.compat.NetworkDirection;
import net.jenkimods.bioforge.network.compat.NetworkRegistry;
import net.jenkimods.bioforge.network.compat.SimpleChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public final class BlackSteelTilesNetworkHandler {
    private static final String PROTOCOL = "1";
    private static SimpleChannel channel;

    private BlackSteelTilesNetworkHandler() {
    }

    public static void register() {
        channel = NetworkRegistry.newSimpleChannel(
                ResourceLocation.fromNamespaceAndPath(BioForge.MODID, "black_steel_tiles"),
                () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
        channel.registerMessage(0, BlackSteelTilesCyclePacket.class,
                BlackSteelTilesCyclePacket::encode,
                BlackSteelTilesCyclePacket::decode,
                BlackSteelTilesCyclePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendCycle(BlockPos pos, Direction face) {
        if (channel != null) channel.sendToServer(
                new BlackSteelTilesCyclePacket(pos, face, false));
    }

    public static void sendCopy(BlockPos pos, Direction face) {
        if (channel != null) channel.sendToServer(
                new BlackSteelTilesCyclePacket(pos, face, true));
    }
}
