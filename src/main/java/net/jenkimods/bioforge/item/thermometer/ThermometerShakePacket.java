package net.jenkimods.bioforge.item.thermometer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.jenkimods.bioforge.network.compat.NetworkEvent;

import java.util.function.Supplier;

public class ThermometerShakePacket {

    private final boolean mainHand;

    public ThermometerShakePacket(boolean mainHand) {
        this.mainHand = mainHand;
    }

    public static void encode(ThermometerShakePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.mainHand);
    }

    public static ThermometerShakePacket decode(FriendlyByteBuf buf) {
        return new ThermometerShakePacket(buf.readBoolean());
    }

    public static void handle(ThermometerShakePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ItemStack stack = msg.mainHand
                    ? player.getItemInHand(InteractionHand.MAIN_HAND)
                    : player.getItemInHand(InteractionHand.OFF_HAND);

            if (stack.getItem() instanceof ThermometerItem item) {
                item.onShake(player, stack);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
