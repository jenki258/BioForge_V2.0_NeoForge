package net.jenkimods.bioforge.item.bones;

import net.jenkimods.bioforge.item.BloodSampleUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WitheredBoneMarrowItem extends Item {

    public WitheredBoneMarrowItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BloodSampleUtil.appendSampleTooltip(
                stack,
                tooltip,
                "item.bioforge.withered_bone_marrow.tooltip.empty",
                "item.bioforge.withered_bone_marrow.tooltip.filled",
                "item.bioforge.needle.tooltip.source",
                "item.bioforge.withered_bone_marrow.tooltip.processed"
        );
    }
}
