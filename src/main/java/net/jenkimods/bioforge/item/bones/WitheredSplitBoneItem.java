package net.jenkimods.bioforge.item.bones;

import net.jenkimods.bioforge.item.BloodSampleUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WitheredSplitBoneItem extends Item {

    public WitheredSplitBoneItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (BloodSampleUtil.hasBlood(stack)) {
            tooltip.add(Component.translatable("item.bioforge.withered_split_bone.tooltip.raw").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("item.bioforge.withered_split_bone.tooltip.needs_decalcification").withStyle(ChatFormatting.DARK_PURPLE));
            return;
        }
        tooltip.add(Component.translatable("item.bioforge.withered_split_bone.tooltip.empty").withStyle(ChatFormatting.DARK_GRAY));
    }
}
