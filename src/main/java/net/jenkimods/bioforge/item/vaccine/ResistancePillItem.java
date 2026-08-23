package net.jenkimods.bioforge.item.vaccine;

import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.InfectionEventHandler;
import net.jenkimods.bioforge.vaccine.ResistancePillProfile;
import net.jenkimods.bioforge.vaccine.StrainFingerprint;
import net.jenkimods.bioforge.vaccine.StrainImmunityManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ResistancePillItem extends Item {
    public ResistancePillItem() {
        super(new Properties().stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                   InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (ResistancePillProfile.read(stack) == null) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable(
                        "message.bioforge.resistance_pill.blank")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level,
                                     LivingEntity entity) {
        ResistancePillProfile profile = ResistancePillProfile.read(stack);
        if (!level.isClientSide() && profile != null && entity instanceof Player player) {
            InfectionData data = InfectionCapability.get(player);
            if (data != null) {
                StrainImmunityManager.grantResistance(player, data,
                        profile.strain(), profile.resistance(),
                        profile.durationTicks());
                if (player instanceof ServerPlayer serverPlayer) {
                    InfectionEventHandler.syncToClient(serverPlayer, data);
                }
                player.displayClientMessage(Component.translatable(
                                "message.bioforge.resistance_pill.applied",
                                Math.round(profile.resistance() * 100.0F))
                        .withStyle(ChatFormatting.GOLD), true);
                level.playSound(null, player.blockPosition(),
                        SoundEvents.GENERIC_EAT, SoundSource.PLAYERS,
                        0.7F, 1.2F);
                player.getCooldowns().addCooldown(this, 20);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 24;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return ResistancePillProfile.read(stack) != null || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        ResistancePillProfile profile = ResistancePillProfile.read(stack);
        if (profile == null) {
            tooltip.add(Component.translatable(
                            "item.bioforge.resistance_pill.blank")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(
                            "item.bioforge.resistance_pill.blank_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable(
                        "item.bioforge.resistance_pill.target",
                        StrainFingerprint.ofPayload(profile.strainPayload()))
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable(
                        "item.bioforge.resistance_pill.strength",
                        Math.round(profile.resistance() * 100.0F))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(
                        "item.bioforge.resistance_pill.duration",
                        StringUtil.formatTickDuration(profile.durationTicks(), 20.0F))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                        "item.bioforge.resistance_pill.warning")
                .withStyle(ChatFormatting.DARK_RED));
    }
}
