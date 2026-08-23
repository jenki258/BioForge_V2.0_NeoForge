package net.jenkimods.bioforge.world.centrifuge;

import net.jenkimods.bioforge.BioForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = BioForge.MODID)
public class CentrifugeReloadListener {

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(CentrifugeRecipeManager.INSTANCE);
    }
}
