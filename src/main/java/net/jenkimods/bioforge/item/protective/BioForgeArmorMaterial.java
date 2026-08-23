package net.jenkimods.bioforge.item.protective;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;

public final class BioForgeArmorMaterial {
    public static final Holder<ArmorMaterial> MEDICAL_MASK = protective("medical_mask");
    public static final Holder<ArmorMaterial> PROTECTIVE_GLOVES =
            protective("protective_gloves");
    public static final Holder<ArmorMaterial> ICE_BAG = protective("ice_bag");
    public static final Holder<ArmorMaterial> MAGMA_BAG = protective("magma_bag");
    public static final Holder<ArmorMaterial> HAZCURE = material(
            "hazcure", true, 10, 1.5F, 0.0F,
            () -> Ingredient.of(BioForge.BLACK_STEEL_PLATE.get()));

    private BioForgeArmorMaterial() {}

    private static Holder<ArmorMaterial> protective(String texture) {
        return material(texture, false, 9, 1.0F, 0.0F,
                () -> Ingredient.of(BioForge.STERILE_RUBBER.get()));
    }

    private static Holder<ArmorMaterial> material(String texture,
                                                   boolean hazcure,
                                                   int enchantmentValue,
                                                   float toughness,
                                                   float knockbackResistance,
                                                   Supplier<Ingredient> repairIngredient) {
        EnumMap<ArmorItem.Type, Integer> defense =
                new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.BOOTS, hazcure ? 2 : 1);
        defense.put(ArmorItem.Type.LEGGINGS, hazcure ? 5 : 2);
        defense.put(ArmorItem.Type.CHESTPLATE, hazcure ? 6 : 2);
        defense.put(ArmorItem.Type.HELMET, hazcure ? 2 : 1);
        defense.put(ArmorItem.Type.BODY, hazcure ? 6 : 2);
        ArmorMaterial value = new ArmorMaterial(defense, enchantmentValue,
                SoundEvents.ARMOR_EQUIP_LEATHER, repairIngredient,
                List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(
                        BioForge.MODID, texture))), toughness, knockbackResistance);
        return Holder.direct(value);
    }

    public static int durabilityMultiplier(Holder<ArmorMaterial> material) {
        return material == HAZCURE ? 24 : 18;
    }
}
