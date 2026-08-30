package net.jenkimods.bioforge.item.needle;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.item.BloodSampleUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = BioForge.MODID)
public class NeedleAttackHandler {

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer attacker)) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        if (target == attacker) return;

        ItemStack mainHand = attacker.getMainHandItem();
        if (mainHand.getItem() instanceof NeedleItem needle) {
            needle.tryExtractBlood(mainHand, target, attacker);
        } else if (mainHand.getItem() instanceof SyringeItem syringe) {
            syringe.tryExtractBlood(mainHand, target, attacker);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack held = event.getEntity().getItemInHand(event.getHand());
        if (!BloodSampleUtil.hasBlood(held)
                || (!(held.getItem() instanceof NeedleItem)
                && !(held.getItem() instanceof SyringeItem))) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;

        event.setCanceled(true);
        if (event.getLevel().isClientSide()) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        InteractionResult result = held.getItem().interactLivingEntity(
                held, event.getEntity(), target, event.getHand());
        event.setCancellationResult(result == InteractionResult.PASS
                ? InteractionResult.SUCCESS : result);
    }
}
