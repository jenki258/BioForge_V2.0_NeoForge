package net.jenkimods.bioforge.item.reagents;

import net.jenkimods.bioforge.item.BloodSampleUtil;
import net.jenkimods.bioforge.world.decalcification.DecalcificationRecipe;
import net.jenkimods.bioforge.world.decalcification.DecalcificationRecipeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class DecalcificationFluidItem extends Item {

    public DecalcificationFluidItem() {
        super(new Properties().stacksTo(64));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack fluidStack = player.getItemInHand(hand);

        if (level.isClientSide()) return InteractionResultHolder.pass(fluidStack);

        if (hand != InteractionHand.MAIN_HAND) {
            player.sendSystemMessage(Component.translatable(
                    "item.bioforge.decalcification_fluid.wrong_hand_fluid").withStyle(ChatFormatting.RED));
            return InteractionResultHolder.fail(fluidStack);
        }

        ItemStack offhandStack = player.getItemInHand(InteractionHand.OFF_HAND);
        Optional<DecalcificationRecipe> recipeOpt = DecalcificationRecipeManager.INSTANCE.getRecipe(level, offhandStack);

        if (recipeOpt.isEmpty()) {
            ItemStack mainOther = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (DecalcificationRecipeManager.INSTANCE.getRecipe(level, mainOther).isPresent()) {
                player.sendSystemMessage(Component.translatable(
                        "item.bioforge.decalcification_fluid.put_target_in_offhand").withStyle(ChatFormatting.RED));
                return InteractionResultHolder.fail(fluidStack);
            }
            player.sendSystemMessage(Component.translatable(
                    "item.bioforge.decalcification_fluid.invalid_target").withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.fail(fluidStack);
        }

        DecalcificationRecipe recipe = recipeOpt.get();

        if (recipe.copyBloodData() && !BloodSampleUtil.hasBlood(offhandStack)) {
            player.sendSystemMessage(Component.translatable(
                    "item.bioforge.decalcification_fluid.no_sample").withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.fail(fluidStack);
        }

        ResourceLocation outputId = ResourceLocation.tryParse(recipe.output());
        if (outputId == null) return InteractionResultHolder.fail(fluidStack);
        net.minecraft.world.item.Item outputItem =
                BuiltInRegistries.ITEM.getOptional(outputId).orElse(null);
        if (outputItem == null) return InteractionResultHolder.fail(fluidStack);

        ItemStack result = new ItemStack(outputItem, offhandStack.getCount());

        if (recipe.copyBloodData()) {
            BloodSampleUtil.copy(offhandStack, result);
        }

        if (recipe.copyNbt() && net.jenkimods.bioforge.util.StackData.has(offhandStack)) {
            net.jenkimods.bioforge.util.StackData.set(result, net.jenkimods.bioforge.util.StackData.copy(offhandStack).copy());
        } else if (!recipe.copyNbtKeys().isEmpty() && net.jenkimods.bioforge.util.StackData.has(offhandStack)) {
            CompoundTag outTag = net.jenkimods.bioforge.util.StackData.copy(result);
            CompoundTag inTag = net.jenkimods.bioforge.util.StackData.copy(offhandStack);
            for (String key : recipe.copyNbtKeys()) {
                if (inTag.contains(key)) {
                    outTag.put(key, inTag.get(key).copy());
                }
            }
            net.jenkimods.bioforge.util.StackData.set(result, outTag);
        }

        offhandStack.shrink(1);
        fluidStack.shrink(1);

        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1.0f, 1.2f);

        return InteractionResultHolder.success(fluidStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bioforge.decalcification_fluid.tooltip.desc")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
