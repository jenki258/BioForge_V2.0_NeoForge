package net.jenkimods.bioforge.item.reagents;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.BioForgeTags;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.infection.spread.AirborneReservoirManager;
import net.jenkimods.bioforge.infection.spread.SurfaceContaminationData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.List;

public class DecontaminationFlaskItem extends ThrowablePotionItem {
    public DecontaminationFlaskItem() {
        super(new Properties().stacksTo(16));
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return "item.bioforge.decontamination_flask";
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bioforge.decontamination_flask.tooltip",
                BioForgeServerConfig.decontaminationRadius() * 2 + 1)
                .withStyle(ChatFormatting.AQUA));
    }

    @EventBusSubscriber(modid = BioForge.MODID)
    public static final class ImpactHandler {
        private ImpactHandler() {}

        @SubscribeEvent
        public static void onImpact(ProjectileImpactEvent event) {
            if (!(event.getProjectile() instanceof ThrownPotion potion)
                    || !(potion.level() instanceof ServerLevel level)
                    || !potion.getItem().is(BioForge.DECONTAMINATION_FLASK.get())) return;
            HitResult hit = event.getRayTraceResult();
            BlockPos center = BlockPos.containing(hit.getLocation());
            int radius = BioForgeServerConfig.decontaminationRadius();
            SurfaceContaminationData.get(level).clean(center, radius, 1.0F,
                    level.getGameTime());
            AirborneReservoirManager.reduce(level, center, radius, 0.85F);
            for (BlockPos pos : BlockPos.betweenClosed(
                    center.offset(-radius, -radius, -radius),
                    center.offset(radius, radius, radius))) {
                if (level.getBlockState(pos).is(BioForgeTags.DECONTAMINATION_TARGETS)
                        && level.getRandom().nextFloat() < 0.65F) {
                    level.destroyBlock(pos, false);
                }
            }
            level.sendParticles(ParticleTypes.CLOUD,
                    center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D,
                    32, radius * 0.45D, radius * 0.35D, radius * 0.45D, 0.03D);
            level.playSound(null, center, SoundEvents.SPLASH_POTION_BREAK,
                    SoundSource.BLOCKS, 1.0F, 1.15F);
        }
    }
}
