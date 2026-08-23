package net.jenkimods.bioforge.item.reagents;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.item.incubating.DirtyCultureVialItem;
import net.jenkimods.bioforge.item.needle.SyringeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class EthanolItem extends Item {
    public EthanolItem() {
        super(new Properties().stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack ethanol = player.getItemInHand(hand);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack other = player.getItemInHand(otherHand);

        if (other.getItem() instanceof DirtyCultureVialItem) {
            if (!level.isClientSide()) {
                other.shrink(1);
                ItemStack clean = new ItemStack(BioForge.LIVE_CULTURE_VIAL.get());
                if (!player.getInventory().add(clean)) {
                    player.drop(clean, false);
                }
                ethanol.shrink(1);
                level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8f, 1.2f);
                player.sendSystemMessage(Component.translatable("item.bioforge.ethanol.cleaned"));
            }
            return InteractionResultHolder.success(ethanol);
        }

        if (other.getItem() instanceof SyringeItem) {
            if (SyringeItem.hasBlood(other)) {
                if (!level.isClientSide()) {
                    player.sendSystemMessage(Component.translatable("item.bioforge.ethanol.syringe_not_empty"));
                }
                return InteractionResultHolder.fail(ethanol);
            }
            if (!level.isClientSide()) {
                SyringeItem.clearInfection(other);
                ethanol.shrink(1);
                level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8f, 1.2f);
                player.sendSystemMessage(Component.translatable("item.bioforge.ethanol.syringe_cleaned"));
            }
            return InteractionResultHolder.success(ethanol);
        }

        return InteractionResultHolder.pass(ethanol);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bioforge.ethanol.tooltip.clean_vial").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.bioforge.ethanol.tooltip.clean_syringe").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(" "));
        tooltip.add(Component.translatable("item.bioforge.ethanol.tooltip.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}