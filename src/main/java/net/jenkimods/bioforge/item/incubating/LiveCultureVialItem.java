package net.jenkimods.bioforge.item.incubating;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.block.PetriDishBlock;
import net.jenkimods.bioforge.infection.*;
import net.jenkimods.bioforge.infection.naming.StrainNamingManager;
import net.jenkimods.bioforge.item.needle.SyringeItem;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.jenkimods.bioforge.vaccine.StrainFingerprint;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LiveCultureVialItem extends Item {

    public LiveCultureVialItem() {
        super(new Properties().stacksTo(16));
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return net.jenkimods.bioforge.util.StackData.has(stack) && hasStrain(stack) ? 1 : 16;
    }

    public static boolean hasStrain(ItemStack stack) {
        return NbtObfuscator.readString(stack) != null;
    }

    @Nullable
    public static StrainData getStrain(ItemStack stack) {
        String raw = NbtObfuscator.readString(stack);
        if (raw != null && !raw.isEmpty()) {
            return StrainData.parse(raw);
        }
        return null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack vial = player.getItemInHand(hand);
        if (!hasStrain(vial)) return InteractionResultHolder.pass(vial);

        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(other);

        if (otherStack.getItem() instanceof SyringeItem && SyringeItem.hasBlood(otherStack)) {
            return transferToSyringe(player, vial, otherStack, hand);
        }

        if (otherStack.getFoodProperties(player) != null) {
            return infectItem(player, vial, otherStack, hand);
        }

        if (otherStack.getItem() == Items.POTION || otherStack.getItem() == Items.HONEY_BOTTLE) {
            return infectItem(player, vial, otherStack, hand);
        }

        return InteractionResultHolder.pass(vial);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        ItemStack vial = context.getItemInHand();
        if (!hasStrain(vial)) return InteractionResult.PASS;

        if (state.getBlock() instanceof PetriDishBlock) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof net.jenkimods.bioforge.item.infection.PetriDishBlockEntity dish && !dish.isInoculated()) {
                if (!level.isClientSide()) {
                    String strainRaw = NbtObfuscator.readString(vial);
                    dish.setStrainData(strainRaw);
                    level.setBlock(pos, state.setValue(PetriDishBlock.GROWTH, 0), 3);
                    level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8f, 1.2f);
                    Player player = context.getPlayer();
                    if (player != null) {
                        player.sendSystemMessage(Component.translatable("item.bioforge.petri_dish.inoculated"));
                        if (!player.getAbilities().instabuild) {
                            vial.shrink(1);
                            ItemStack dirty = new ItemStack(BioForge.DIRTY_CULTURE_VIAL.get());
                            if (!player.getInventory().add(dirty)) {
                                player.drop(dirty, false);
                            }
                        }
                    }
                }
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    private InteractionResultHolder<ItemStack> transferToSyringe(Player player, ItemStack vial, ItemStack syringe, InteractionHand hand) {
        String existingRaw = NbtObfuscator.readInfection(syringe);
        StrainData existingStrain = existingRaw != null && !existingRaw.isEmpty()
                ? StrainData.parse(existingRaw) : null;
        StrainData vialStrain = getStrain(vial);
        if (vialStrain == null) return InteractionResultHolder.fail(vial);

        StrainData finalStrain = StrainData.compete(existingStrain, vialStrain);
        NbtObfuscator.writeInfection(syringe, finalStrain.toPayload());

        vial.shrink(1);
        ItemStack dirty = new ItemStack(BioForge.DIRTY_CULTURE_VIAL.get());
        if (!player.getInventory().add(dirty)) {
            player.drop(dirty, false);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8f, 1.2f);
        player.sendSystemMessage(Component.translatable("item.bioforge.live_culture_vial.transferred"));
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    private InteractionResultHolder<ItemStack> infectItem(Player player, ItemStack vial, ItemStack target, InteractionHand hand) {
        String strainRaw = NbtObfuscator.readString(vial);
        if (strainRaw == null || strainRaw.isEmpty()) return InteractionResultHolder.fail(vial);
        NbtObfuscator.writeString(target, strainRaw);
        vial.shrink(1);
        ItemStack dirty = new ItemStack(BioForge.DIRTY_CULTURE_VIAL.get());
        if (!player.getInventory().add(dirty)) {
            player.drop(dirty, false);
        }
        player.sendSystemMessage(Component.translatable("item.bioforge.live_culture_vial.infect_item", target.getDisplayName()));
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        StrainData strain = getStrain(stack);
        if (strain != null) {
            tooltip.add(Component.translatable("item.bioforge.live_culture_vial.filled").withStyle(ChatFormatting.DARK_RED));
            String fingerprint = StrainFingerprint.ofPayload(strain.toPayload());
            StrainNamingManager.getClientName(fingerprint).ifPresent(name ->
                    tooltip.add(Component.translatable("item.bioforge.strain_name", name)
                            .withStyle(ChatFormatting.AQUA)));
            tooltip.add(Component.translatable("item.bioforge.live_culture_vial.sealed_profile")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal(" "));
            tooltip.add(Component.translatable("item.bioforge.live_culture_vial.use_syringe").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("item.bioforge.live_culture_vial.use_infect").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("item.bioforge.live_culture_vial.empty").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.bioforge.live_culture_vial.tooltip").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal(" "));
            tooltip.add(Component.translatable("item.bioforge.live_culture_vial.grow_in_incubator").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
