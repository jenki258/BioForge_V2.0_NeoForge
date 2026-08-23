package net.jenkimods.bioforge.vaccine;

import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public record ResistancePillProfile(
        String strainPayload,
        float resistance,
        int durationTicks
) {
    private static final String CHANNEL = "resistance_pill";
    private static final int VERSION = 1;

    public ResistancePillProfile {
        strainPayload = strainPayload == null ? "" : strainPayload;
        resistance = Mth.clamp(Float.isFinite(resistance)
                ? resistance : 0.0F, 0.0F, 1.0F);
        durationTicks = Mth.clamp(durationTicks, 1, 20 * 60 * 60 * 24);
    }

    public StrainData strain() {
        return StrainData.parse(strainPayload);
    }

    public boolean isValid() {
        return !strainPayload.isBlank() && strain().getPathogenId() != null
                && resistance > 0.0F && durationTicks > 0;
    }

    public void write(ItemStack stack) {
        CompoundTag data = new CompoundTag();
        data.putInt("Version", VERSION);
        data.putString("Strain", strainPayload);
        data.putFloat("Resistance", resistance);
        data.putInt("Duration", durationTicks);
        NbtObfuscator.writeCompoundDeterministic(stack, CHANNEL, data);
    }

    @Nullable
    public static ResistancePillProfile read(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !net.jenkimods.bioforge.util.StackData.has(stack)) return null;
        CompoundTag data = NbtObfuscator.readCompound(stack, CHANNEL);
        if (data == null || data.getInt("Version") != VERSION) return null;
        ResistancePillProfile profile = new ResistancePillProfile(
                data.getString("Strain"), data.getFloat("Resistance"),
                data.getInt("Duration"));
        return profile.isValid() ? profile : null;
    }
}
