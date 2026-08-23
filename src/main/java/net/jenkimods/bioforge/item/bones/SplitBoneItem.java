package net.jenkimods.bioforge.item.bones;

import net.jenkimods.bioforge.item.BloodSampleUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SplitBoneItem extends Item {

    public SplitBoneItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (BloodSampleUtil.hasBlood(stack)) {
            tooltip.add(Component.translatable("item.bioforge.split_bone.tooltip.raw")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.bioforge.split_bone.tooltip.empty")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
