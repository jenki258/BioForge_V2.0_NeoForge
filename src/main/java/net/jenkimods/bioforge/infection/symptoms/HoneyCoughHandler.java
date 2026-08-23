package net.jenkimods.bioforge.infection.symptoms;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = BioForge.MODID)
public final class HoneyCoughHandler {
    private HoneyCoughHandler() {}

    @SubscribeEvent
    public static void onUseItem(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity().level().isClientSide()
                || !event.getItem().is(Items.HONEY_BOTTLE)) return;
        SymptomSuppression.suppress(event.getEntity(), "coughing", 6000);
    }
}
