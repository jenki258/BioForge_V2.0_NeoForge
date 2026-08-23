package net.jenkimods.bioforge.event;

import net.jenkimods.bioforge.block.InfestedBlock;
import net.jenkimods.bioforge.block.MicrobialMatBlock;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "bioforge")
public class MatSlownessHandler {

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity.level().isClientSide()) return;

        BlockState state = entity.getBlockStateOn();
        if (state.getBlock() instanceof MicrobialMatBlock || state.getBlock() instanceof InfestedBlock) {
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0, false, false, false));
        }
    }
}
