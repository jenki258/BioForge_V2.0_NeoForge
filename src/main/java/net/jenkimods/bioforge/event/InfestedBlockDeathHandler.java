package net.jenkimods.bioforge.event;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.block.InfestedBlock;
import net.jenkimods.bioforge.block.MicrobialMatBlock;
import net.jenkimods.bioforge.item.infection.ColonyCoreBlockEntity;
import net.jenkimods.bioforge.item.infection.InfestedBlockEntity;
import net.jenkimods.bioforge.item.infection.MicrobialMatBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = BioForge.MODID)
public class InfestedBlockDeathHandler {

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide()) return;

        BlockPos pos = entity.blockPosition();
        BlockEntity be = level.getBlockEntity(pos);

        if (level.getBlockState(pos).getBlock() instanceof InfestedBlock) {
            if (be instanceof InfestedBlockEntity infested) {
                addResourcesToCore(level, infested.getCorePos());
            }
        }

        else if (level.getBlockState(pos).getBlock() instanceof MicrobialMatBlock) {
            if (be instanceof MicrobialMatBlockEntity mat) {
                addResourcesToCore(level, mat.getCorePos());
            }
        }
    }

    private static void addResourcesToCore(Level level, BlockPos corePos) {
        if (corePos == null) return;
        BlockEntity coreBe = level.getBlockEntity(corePos);
        if (coreBe instanceof ColonyCoreBlockEntity core) {
            core.addResources(10);
        }
    }
}
