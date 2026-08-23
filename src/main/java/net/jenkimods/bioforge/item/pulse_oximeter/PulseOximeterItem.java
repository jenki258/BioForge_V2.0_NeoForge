package net.jenkimods.bioforge.item.pulse_oximeter;

import net.jenkimods.bioforge.BioForgeTags;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.item.clipboard.ClipboardHelper;
import net.jenkimods.bioforge.util.HitResultUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.List;

public class PulseOximeterItem extends Item {
    public static final int USE_DURATION = 72000;
    public static final int HOLD_TICKS_REQUIRED = 40;

    public PulseOximeterItem() {
        super(new Properties().stacksTo(1).durability(0));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) { return USE_DURATION; }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!(entity instanceof Player player)) return;
        int usedTicks = USE_DURATION - remainingUseDuration;
        if (usedTicks < HOLD_TICKS_REQUIRED) return;
        if ((usedTicks - HOLD_TICKS_REQUIRED) % 20 != 0) return;

        if (level.isClientSide()) {
            ClientInteraction.tick();
        } else if (usedTicks >= 50) {
            EntityHitResult hitResult = HitResultUtil.getHitResult(player);
            Entity target = hitResult == null ? null : hitResult.getEntity();
            LivingEntity subject = target instanceof LivingEntity living ? living : player;
            InfectionData data = InfectionCapability.get(subject);
            float o2 = data != null ? data.getSymptom(BioForgeSymptoms.OXYGEN_SATURATION) : 0.95f;
            float perf = data != null ? data.getSymptom(BioForgeSymptoms.PERFUSION_INDEX) : 0.7f;
            ClipboardHelper.recordOxygenSat(o2, perf, false, player, subject.getUUID());
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        if (level.isClientSide()) ClientInteraction.stop();
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientInteraction {
        private static void tick() {
            Entity target = pickTargetEntity();
            int targetId = -1;
            if (target instanceof LivingEntity
                    && !target.getType().is(BioForgeTags.NO_DIAGNOSTICS)
                    && !target.getType().is(PulseOximeterItem.NO_PULSE_OXIMETER_TAG)) {
                targetId = target.getId();
            }
            PulseOximeterNetworkHandler.sendRequest(targetId);
        }

        private static void stop() {
            PulseOximeterClientHandler.stopInspection();
        }

        private static Entity pickTargetEntity() {
            HitResult hit = Minecraft.getInstance().hitResult;
            if (hit instanceof EntityHitResult ehr) return ehr.getEntity();
            return null;
        }
    }

    public static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> NO_PULSE_OXIMETER_TAG =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    net.minecraft.resources.ResourceLocation.tryBuild("bioforge", "no_pulse_oximeter"));

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bioforge.pulse_oximeter.tooltip.hold")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.pulse_oximeter.tooltip.target")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.pulse_oximeter.tooltip.stabilize")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
