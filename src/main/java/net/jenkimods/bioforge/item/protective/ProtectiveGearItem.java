package net.jenkimods.bioforge.item.protective;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class ProtectiveGearItem extends ArmorItem {
    public enum WearableStyle {
        VANILLA,
        THERMAL_BAG,
        MEDICAL_MASK,
        PROTECTIVE_GLOVES
    }

    private final String tooltipKey;
    private final WearableStyle wearableStyle;
    public ProtectiveGearItem(Holder<ArmorMaterial> material, Type type,
                              Properties properties, String tooltipKey) {
        this(material, type, properties, tooltipKey, WearableStyle.VANILLA);
    }

    public ProtectiveGearItem(Holder<ArmorMaterial> material, Type type,
                              Properties properties, String tooltipKey,
                              WearableStyle wearableStyle) {
        super(material, type, withDurability(properties, material, type));
        this.tooltipKey = tooltipKey;
        this.wearableStyle = wearableStyle;
    }

    private static Properties withDurability(Properties properties,
                                             Holder<ArmorMaterial> material,
                                             Type type) {
        return properties.durability(type.getDurability(
                BioForgeArmorMaterial.durabilityMultiplier(material)));
    }

    public WearableStyle wearableStyle() {
        return wearableStyle;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tooltipKey)
                .withStyle(ChatFormatting.AQUA));
        if (tooltipKey.endsWith(".hazcure")) {
            tooltip.add(Component.translatable(
                    "item.bioforge.protective_gear.hazcure_set")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
