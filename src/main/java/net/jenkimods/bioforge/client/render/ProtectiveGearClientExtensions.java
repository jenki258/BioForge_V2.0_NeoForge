package net.jenkimods.bioforge.client.render;

import net.jenkimods.bioforge.item.protective.ProtectiveGearItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Supplier;

public final class ProtectiveGearClientExtensions {
    private ProtectiveGearClientExtensions() {}

    public static IClientItemExtensions create(
            Supplier<ProtectiveGearItem.WearableStyle> styleSupplier) {
        return new IClientItemExtensions() {
            private ProtectiveGearModel model;
            private ProtectiveGearItem.WearableStyle bakedStyle;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(
                    LivingEntity entity, ItemStack stack, EquipmentSlot slot,
                    HumanoidModel<?> original) {
                ProtectiveGearItem.WearableStyle style = styleSupplier.get();
                if (style == null || style == ProtectiveGearItem.WearableStyle.VANILLA) {
                    return original;
                }
                if (model == null || style != bakedStyle) {
                    ModelLayerLocation layer = switch (style) {
                        case THERMAL_BAG -> ProtectiveGearModel.THERMAL_BAG_LAYER;
                        case MEDICAL_MASK -> ProtectiveGearModel.MEDICAL_MASK_LAYER;
                        case PROTECTIVE_GLOVES -> ProtectiveGearModel.PROTECTIVE_GLOVES_LAYER;
                        case VANILLA -> throw new IllegalStateException(
                                "Vanilla armor needs no custom model");
                    };
                    model = new ProtectiveGearModel(Minecraft.getInstance()
                            .getEntityModels().bakeLayer(layer));
                    bakedStyle = style;
                }
                copyProperties(original, model);
                return model;
            }
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void copyProperties(HumanoidModel<?> source,
                                       HumanoidModel<?> destination) {
        ((HumanoidModel) source).copyPropertiesTo((HumanoidModel) destination);
    }
}
