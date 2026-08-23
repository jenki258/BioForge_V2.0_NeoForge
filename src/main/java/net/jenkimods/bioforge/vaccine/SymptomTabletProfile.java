package net.jenkimods.bioforge.vaccine;

import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public record SymptomTabletProfile(String symptomId, int durationTicks, float quality) {
    private static final String CHANNEL = "symptom_tablet";

    public SymptomTabletProfile {
        symptomId = symptomId == null ? "" : symptomId.trim().toLowerCase(java.util.Locale.ROOT);
        durationTicks = Math.max(20, durationTicks);
        quality = Math.max(0.0F, Math.min(1.0F, quality));
    }

    public void write(ItemStack stack) {
        CompoundTag data = new CompoundTag();
        data.putString("Symptom", symptomId);
        data.putInt("Duration", durationTicks);
        data.putFloat("Quality", quality);
        NbtObfuscator.writeCompound(stack, CHANNEL, data);
    }

    @Nullable
    public static SymptomTabletProfile read(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !net.jenkimods.bioforge.util.StackData.has(stack)) return null;
        CompoundTag data = NbtObfuscator.readCompound(stack, CHANNEL);
        if (data == null || data.getString("Symptom").isBlank()) return null;
        return new SymptomTabletProfile(data.getString("Symptom"),
                data.getInt("Duration"), data.getFloat("Quality"));
    }
}
