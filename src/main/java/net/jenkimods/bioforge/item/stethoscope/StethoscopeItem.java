package net.jenkimods.bioforge.item.stethoscope;

import net.jenkimods.bioforge.BioForgeTags;
import net.jenkimods.bioforge.infection.HeartRate;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.LungSound;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.item.clipboard.ClipboardHelper;
import net.jenkimods.bioforge.util.HitResultUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class StethoscopeItem extends Item {

    public static final int USE_DURATION = 52000;
    public static final int LISTEN_TICKS_REQUIRED = 20;
    public static final int RESEND_INTERVAL = 40;
    public static final TagKey<EntityType<?>> NO_STETHOSCOPE_TAG =
            TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    ResourceLocation.tryBuild("bioforge", "no_stethoscope"));

    public StethoscopeItem() {
        super(new Properties().stacksTo(1).durability(0));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bioforge.stethoscope.tooltip.hold")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.stethoscope.tooltip.look")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.stethoscope.tooltip.self")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
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
        if (usedTicks < LISTEN_TICKS_REQUIRED) return;
        if ((usedTicks - LISTEN_TICKS_REQUIRED) % RESEND_INTERVAL != 0) return;

        if (level.isClientSide()) {
            ClientInteraction.tick(player);
        } else if (usedTicks >= 100) {
            EntityHitResult hitResult = HitResultUtil.getHitResult(player);
            Entity target = hitResult == null ? null : hitResult.getEntity();
            LivingEntity subject = target instanceof LivingEntity living
                    && !living.getType().is(NO_STETHOSCOPE_TAG)
                    && !living.getType().is(BioForgeTags.NO_DIAGNOSTICS)
                    ? living : player;
            InfectionData data = InfectionCapability.get(subject);
            HeartRate heartRate = data != null
                    ? data.getSymptom(BioForgeSymptoms.HEART_RATE) : HeartRate.NORMAL;
            LungSound lungSound = data != null
                    ? data.getSymptom(BioForgeSymptoms.LUNG_SOUND) : LungSound.NORMAL;
            ClipboardHelper.recordHeart(heartRate.name(), false, player, subject.getUUID());
            ClipboardHelper.recordLungs(lungSound.name(), false, player, subject.getUUID());
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        if (level.isClientSide()) ClientInteraction.stop();
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientInteraction {
        private static void tick(Player player) {
            Entity target = pickTargetEntity();
            int targetId = -1;
            if (target instanceof LivingEntity
                    && !target.getType().is(StethoscopeItem.NO_STETHOSCOPE_TAG)
                    && !target.getType().is(BioForgeTags.NO_DIAGNOSTICS)) {
                targetId = target.getId();
            }
            if (!StethoscopeClientHandler.isListening()
                    || targetId != StethoscopeClientHandler.getCurrentTargetId()) {
                StethoscopeClientHandler.beginListening(targetId);
            }
            StethoscopeNetworkHandler.sendRequest(targetId,
                    player.getUsedItemHand() == InteractionHand.MAIN_HAND);
        }

        private static void stop() {
            StethoscopeClientHandler.stopListening();
        }

        private static Entity pickTargetEntity() {
            HitResult hit = Minecraft.getInstance().hitResult;
            if (hit instanceof EntityHitResult ehr) return ehr.getEntity();
            return null;
        }
    }
}
