package net.jenkimods.bioforge.world.decalcification;

import net.jenkimods.bioforge.BioForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = BioForge.MODID)
public class DecalcificationReloadListener {

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(DecalcificationRecipeManager.INSTANCE);
    }
}
