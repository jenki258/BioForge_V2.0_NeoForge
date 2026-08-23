package net.jenkimods.bioforge.item.incubating;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class DirtyCultureVialItem extends Item {
    public DirtyCultureVialItem() {
        super(new Properties().stacksTo(16));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bioforge.dirty_culture_vial.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.bioforge.dirty_culture_vial.tooltip.clean").withStyle(ChatFormatting.DARK_GRAY));
    }
}
