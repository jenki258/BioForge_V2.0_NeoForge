package net.jenkimods.bioforge.item.incubating;

import net.jenkimods.bioforge.infection.*;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class VirusSampleItem extends Item {

    public VirusSampleItem() {
        super(new Properties().stacksTo(1));
    }

    public static StrainData getStrain(ItemStack stack) {
        String raw = NbtObfuscator.readString(stack);
        if (raw != null && !raw.isEmpty()) {
            return StrainData.parse(raw);
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        StrainData strain = getStrain(stack);
        if (strain != null) {
            tooltip.add(Component.translatable("item.bioforge.virus_sample.filled").withStyle(ChatFormatting.DARK_RED));
            tooltip.add(Component.translatable("item.bioforge.virus_sample.sealed_profile")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal(" "));
            tooltip.add(Component.translatable("item.bioforge.virus_sample.use_in_incubator").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("item.bioforge.virus_sample.use_with_live_culture").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("item.bioforge.virus_sample.empty").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.bioforge.virus_sample.tooltip").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
