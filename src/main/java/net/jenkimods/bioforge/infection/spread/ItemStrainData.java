package net.jenkimods.bioforge.infection.spread;

import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ItemStrainData {
    private static final String CONTAMINATION_CHANNEL = "item_contamination";

    private ItemStrainData() {}

    @Nullable
    public static StrainData read(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !net.jenkimods.bioforge.util.StackData.has(stack)) return null;
        var tag = net.jenkimods.bioforge.util.StackData.copy(stack);
        StrainData named = parse(NbtObfuscator.readString(tag, CONTAMINATION_CHANNEL));
        if (named != null) return named;
        StrainData infection = parse(NbtObfuscator.readInfection(tag));
        if (infection != null) return infection;
        return parse(NbtObfuscator.readString(tag));
    }

    public static void write(ItemStack stack, StrainData strain) {
        if (stack == null || stack.isEmpty() || strain == null || strain.getPathogenId() == null) return;
        NbtObfuscator.writeStringDeterministic(stack,
                CONTAMINATION_CHANNEL, strain.toPayload());
    }

    public static boolean clear(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !net.jenkimods.bioforge.util.StackData.has(stack)) return false;
        var tag = net.jenkimods.bioforge.util.StackData.copy(stack);
        boolean contaminated = read(stack) != null;
        NbtObfuscator.clear(tag, CONTAMINATION_CHANNEL);
        NbtObfuscator.clearInfection(tag);
        if (parse(NbtObfuscator.readString(tag)) != null) {
            NbtObfuscator.clear(tag);
        }
        net.jenkimods.bioforge.util.StackData.set(stack, tag);
        return contaminated;
    }

    @Nullable
    private static StrainData parse(@Nullable String payload) {
        if (payload == null || payload.isBlank() || "CLEAN".equals(payload)) return null;
        StrainData strain = StrainData.parse(payload);
        return strain.getPathogenId() == null ? null : strain;
    }

    public static StrainData stronger(@Nullable StrainData first, @Nullable StrainData second) {
        if (first == null) return second;
        if (second == null) return first;
        float firstStrength = strength(first);
        float secondStrength = strength(second);
        if (secondStrength > firstStrength) return second;
        if (firstStrength > secondStrength) return first;
        return second.toCanonicalGeneticPayload().compareTo(first.toCanonicalGeneticPayload()) < 0
                ? second : first;
    }

    private static float strength(StrainData strain) {
        return strain.getSymptom("infection_strength")
                .map(value -> {
                    try {
                        return Float.parseFloat(value);
                    } catch (NumberFormatException ignored) {
                        return 0.5F;
                    }
                }).orElse(0.5F);
    }
}
