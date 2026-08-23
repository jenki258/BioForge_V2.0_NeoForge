package net.jenkimods.bioforge.item.infection;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PetriDishItem extends BlockItem {

    public PetriDishItem() {
        super(BioForge.PETRI_DISH_BLOCK.get(), new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack dishStack = player.getItemInHand(hand);

        if (isInoculated(dishStack)) {
            if (net.jenkimods.bioforge.util.StackData.copy(dishStack).getInt("Growth") >= 3) {
                InteractionHand otherHand = (hand == InteractionHand.MAIN_HAND) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                ItemStack otherStack = player.getItemInHand(otherHand);
                if (otherStack.getItem() instanceof SwabItem && !SwabItem.isContaminated(otherStack)) {
                    if (!level.isClientSide()) {
                        String data = NbtObfuscator.readString(dishStack);
                        if (data != null) {
                            NbtObfuscator.writeString(otherStack, data);
                            if (net.jenkimods.bioforge.util.StackData.copy(dishStack).getInt("Growth") == 3) {
                                NbtObfuscator.clear(dishStack);
                                net.jenkimods.bioforge.util.StackData.update(dishStack,
                                        tag -> tag.putInt("Growth", 0));
                            }
                            player.setItemInHand(hand, dishStack);
                            if (player instanceof ServerPlayer sp) {
                                sp.inventoryMenu.broadcastChanges();
                            }
                            level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8f, 1.2f);
                            player.sendSystemMessage(Component.translatable("item.bioforge.petri_dish.harvested"));
                        }
                    }
                    return InteractionResultHolder.success(dishStack);
                }
            }
            return super.use(level, player, hand);
        }

        InteractionHand otherHand = (hand == InteractionHand.MAIN_HAND) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(otherHand);
        if (otherStack.getItem() instanceof SwabItem && SwabItem.isContaminated(otherStack)) {
            if (!level.isClientSide()) {
                String payload = NbtObfuscator.readString(otherStack);
                if (payload == null) return InteractionResultHolder.fail(dishStack);

                NbtObfuscator.writeString(dishStack, payload);
                net.jenkimods.bioforge.util.StackData.update(dishStack,
                        tag -> tag.putInt("Growth", 0));

                player.setItemInHand(hand, dishStack);
                if (player instanceof ServerPlayer sp) {
                    sp.inventoryMenu.broadcastChanges();
                }

                level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8f, 1.2f);
                player.sendSystemMessage(Component.translatable("item.bioforge.petri_dish.inoculated"));
            }
            return InteractionResultHolder.success(dishStack);
        }

        return super.use(level, player, hand);
    }

    public static boolean isInoculated(ItemStack stack) {
        return NbtObfuscator.readString(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        if (!isInoculated(stack)) {
            tooltip.add(Component.translatable("item.bioforge.petri_dish.tooltip.empty")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.bioforge.petri_dish.tooltip.usage")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("item.bioforge.petri_dish.tooltip.cultured")
                    .withStyle(ChatFormatting.DARK_RED));
            int growth = net.jenkimods.bioforge.util.StackData.copy(stack).getInt("Growth");
            tooltip.add(Component.translatable(
                    "item.bioforge.petri_dish.growth_stage", growth)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
