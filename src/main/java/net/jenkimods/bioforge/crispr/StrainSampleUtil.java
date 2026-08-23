package net.jenkimods.bioforge.crispr;

import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.item.incubating.LiveCultureVialItem;
import net.jenkimods.bioforge.item.incubating.VirusSampleItem;
import net.jenkimods.bioforge.item.infection.PetriDishItem;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class StrainSampleUtil {
    private StrainSampleUtil() {}

    @Nullable
    public static StrainData getStrain(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.getItem() instanceof VirusSampleItem) {
            return VirusSampleItem.getStrain(stack);
        }
        if (stack.getItem() instanceof LiveCultureVialItem) {
            return LiveCultureVialItem.getStrain(stack);
        }
        if (stack.getItem() instanceof PetriDishItem && PetriDishItem.isInoculated(stack)) {
            return readObfuscated(stack);
        }
        return readObfuscated(stack);
    }

    @Nullable
    public static String getPayload(ItemStack stack) {
        StrainData strain = getStrain(stack);
        return strain == null || strain.getPathogenId() == null ? null : strain.toPayload();
    }

    @Nullable
    private static StrainData readObfuscated(ItemStack stack) {
        String payload = NbtObfuscator.readString(stack);
        if (payload == null || payload.isBlank() || "CLEAN".equals(payload)) return null;
        StrainData strain = StrainData.parse(payload);
        return strain.getPathogenId() == null ? null : strain;
    }
}
