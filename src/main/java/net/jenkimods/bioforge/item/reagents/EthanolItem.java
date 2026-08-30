package net.jenkimods.bioforge.item.reagents;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.BioForgeTags;
import net.jenkimods.bioforge.infection.spread.SurfaceContaminationData;
import net.jenkimods.bioforge.item.incubating.DirtyCultureVialItem;
import net.jenkimods.bioforge.item.needle.SyringeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import javax.annotation.Nullable;
import java.util.List;

public class EthanolItem extends Item {
    public EthanolItem() {
        super(new Properties().stacksTo(16));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockState targetState = level.getBlockState(context.getClickedPos());
        var targetId = BuiltInRegistries.BLOCK.getKey(targetState.getBlock());
        boolean bioForgeBlock = BioForge.MODID.equals(targetId.getNamespace());
        boolean excluded = targetState.is(BioForge.BARREL_PRESS.get());
        if (excluded || !(bioForgeBlock || targetState.is(BioForgeTags.ETHANOL_COATABLE))) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            BlockPos clickedPos = context.getClickedPos();
            BlockState clickedState = level.getBlockState(clickedPos);
            SurfaceContaminationData contamination = SurfaceContaminationData.get(serverLevel);
            boolean applied = contamination.coatWithEthanol(clickedPos);
            if (clickedState.getBlock() instanceof DoorBlock
                    && clickedState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                BlockPos pairedPos = clickedState.getValue(
                        BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                        ? clickedPos.above() : clickedPos.below();
                if (level.getBlockState(pairedPos).is(clickedState.getBlock())) {
                    applied |= contamination.coatWithEthanol(pairedPos);
                }
            }
            Player player = context.getPlayer();
            if (applied) {
                if (player == null || !player.getAbilities().instabuild) {
                    context.getItemInHand().shrink(1);
                }
                level.playSound(null, context.getClickedPos(), SoundEvents.BOTTLE_EMPTY,
                        SoundSource.BLOCKS, 0.8F, 1.15F);
                serverLevel.sendParticles(ParticleTypes.CLOUD,
                        context.getClickLocation().x,
                        context.getClickLocation().y,
                        context.getClickLocation().z,
                        8, 0.18D, 0.18D, 0.18D, 0.01D);
                if (player != null) {
                    player.displayClientMessage(Component.translatable(
                            "item.bioforge.ethanol.coating_applied"), true);
                }
            } else if (player != null) {
                player.displayClientMessage(Component.translatable(
                        "item.bioforge.ethanol.already_coated"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
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
        tooltip.add(Component.translatable("item.bioforge.ethanol.tooltip.coat_black_steel").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.bioforge.ethanol.tooltip.protection_radius").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(" "));
        tooltip.add(Component.translatable("item.bioforge.ethanol.tooltip.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
