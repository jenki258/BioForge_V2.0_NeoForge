package net.jenkimods.bioforge.item.needle;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = BioForge.MODID)
public class NeedleAttackHandler {

    @SubscribeEvent
    public static void onLivingAttack(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;

        LivingEntity target = event.getEntity();
        if (target == attacker) return;
        if (event.getSource().getDirectEntity() != attacker) return;

        ItemStack mainHand = attacker.getMainHandItem();
        if (mainHand.getItem() instanceof NeedleItem needle) {
            needle.tryExtractBlood(mainHand, target, attacker);
        } else if (mainHand.getItem() instanceof SyringeItem syringe) {
            syringe.tryExtractBlood(mainHand, target, attacker);
        }
    }
}
