package net.jenkimods.bioforge.block;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.world.decoration.BlackSteelTilesNetworkHandler;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = BioForge.MODID)
public final class BlackSteelTilesInteractionHandler {
    private BlackSteelTilesInteractionHandler() {
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().getBlockState(event.getPos()).is(BioForge.BLACK_STEEL_TILES.get())) return;
        ItemStack held = event.getEntity().getMainHandItem();
        if (!held.is(BioForge.BLACK_STEEL_TILES_ITEM.get())) return;

        event.setCanceled(true);
        Direction face = event.getFace();
        if (face == null) return;
        if (event.getLevel().isClientSide()) {
            if (event.getEntity().isShiftKeyDown()) {
                BlackSteelTilesNetworkHandler.sendCopy(event.getPos(), face);
            } else {
                BlackSteelTilesNetworkHandler.sendCycle(event.getPos(), face);
            }
        }
    }
}
