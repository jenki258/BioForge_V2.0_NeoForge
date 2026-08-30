package net.jenkimods.bioforge.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public final class DescribedBlockItem extends BlockItem {
    private final String[] tooltipKeys;

    public DescribedBlockItem(Block block, Properties properties, String... tooltipKeys) {
        super(block, properties);
        this.tooltipKeys = tooltipKeys.clone();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        for (int index = 0; index < tooltipKeys.length; index++) {
            tooltip.add(Component.translatable(tooltipKeys[index]).withStyle(
                    index == 0 ? ChatFormatting.GRAY : ChatFormatting.DARK_AQUA));
        }
    }
}
