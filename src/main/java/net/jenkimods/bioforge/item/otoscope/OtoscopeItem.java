package net.jenkimods.bioforge.item.otoscope;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.BioForgeTags;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.item.clipboard.ClipboardHelper;
import net.jenkimods.bioforge.util.HitResultUtil;
import net.minecraft.ChatFormatting;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class OtoscopeItem extends Item {

    public static final int USE_DURATION = 72000;
    public static final int HOLD_TICKS_REQUIRED = 60;

    public static final TagKey<EntityType<?>> NO_OTOSCOPE_TAG =
            TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    ResourceLocation.tryBuild(BioForge.MODID, "no_otoscope"));

    public OtoscopeItem() {
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
        if (usedTicks != HOLD_TICKS_REQUIRED) return;

        boolean holdsMirror = player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof MirrorItem;
        if (level.isClientSide()) {
            ClientInteraction.request(player, holdsMirror);
        } else {
            EntityHitResult hitResult = HitResultUtil.getHitResult(player);
            Entity target = hitResult == null ? null : hitResult.getEntity();
            if (target == null && !holdsMirror) {
                return;
            }
            LivingEntity subject = target instanceof LivingEntity living ? living : player;
            InfectionData data = InfectionCapability.get(subject);
            float redness;
            float lesions;
            float secretion;
            float swelling;
            if (data != null) {
                redness = data.getSymptom(BioForgeSymptoms.OTOSCOPE_REDNESS);
                lesions = data.getSymptom(BioForgeSymptoms.OTOSCOPE_LESIONS);
                secretion = data.getSymptom(BioForgeSymptoms.OTOSCOPE_SECRETION);
                swelling = data.getSymptom(BioForgeSymptoms.OTOSCOPE_SWELLING);
            } else {
                redness = 0.0f;
                lesions = 0.0f;
                secretion = 0.0f;
                swelling = 0.0f;
            }
            ClipboardHelper.recordVisual(redness, lesions, secretion, swelling, false, player, subject.getUUID());
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        if (level.isClientSide()) {
            ClientInteraction.stop();
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientInteraction {
        private static void request(Player player, boolean holdsMirror) {
            Entity target = pickTargetEntity();
            if (target == null) {
                if (holdsMirror) {
                    OtoscopeNetworkHandler.sendRequest(-1);
                } else {
                    player.sendSystemMessage(Component.translatable(
                            "item.bioforge.otoscope.tooltip.target").withStyle(ChatFormatting.RED));
                }
            } else if (target instanceof LivingEntity
                    && !target.getType().is(NO_OTOSCOPE_TAG)
                    && !target.getType().is(BioForgeTags.NO_DIAGNOSTICS)) {
                OtoscopeNetworkHandler.sendRequest(target.getId());
            }
        }

        private static void stop() {
            OtoscopeClientHandler.stopInspection();
        }

        private static Entity pickTargetEntity() {
            HitResult hit = Minecraft.getInstance().hitResult;
            if (hit instanceof EntityHitResult ehr) return ehr.getEntity();
            return null;
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bioforge.otoscope.tooltip.hold")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.otoscope.tooltip.target")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.otoscope.tooltip.move")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.otoscope.tooltip.exit")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
