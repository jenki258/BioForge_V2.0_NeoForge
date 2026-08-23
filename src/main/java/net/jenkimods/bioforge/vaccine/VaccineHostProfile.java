package net.jenkimods.bioforge.vaccine;

import net.jenkimods.bioforge.blood.BloodType;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;




public record VaccineHostProfile(@Nullable BloodType bloodType, int findings,
                                 boolean bloodVerified) {
    public static final String ROOT_TAG = "BioForgeVaccineHost";
    private static final String CHANNEL = "vaccine_host";
    private static final int VERSION = 1;

    public VaccineHostProfile {
        findings = Math.max(0, Math.min(7, findings));
        if (bloodType == BloodType.ANIMAL_BLOOD) bloodVerified = true;
    }

    @Nullable
    public static VaccineHostProfile fromMedicalReport(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !net.jenkimods.bioforge.util.StackData.has(stack)) return null;
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(stack);
        if (tag == null || !tag.hasUUID("SessionId")) return null;
        int findings = countFindings(tag);
        boolean bloodVerified = hasCompleteBlood(tag);
        BloodType blood = bloodVerified
                ? BloodType.findByName(tag.getString("BloodType")) : null;
        return new VaccineHostProfile(blood, findings, bloodVerified && blood != null);
    }

    public void write(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        if (bloodType != null) tag.putString("BloodType", bloodType.name());
        tag.putInt("Findings", findings);
        tag.putBoolean("BloodVerified", bloodVerified);
        net.jenkimods.bioforge.util.StackData.update(stack,
                root -> root.remove(ROOT_TAG));
        NbtObfuscator.writeCompound(stack, CHANNEL, tag);
    }

    @Nullable
    public static VaccineHostProfile read(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !net.jenkimods.bioforge.util.StackData.has(stack)) return null;
        CompoundTag root = net.jenkimods.bioforge.util.StackData.copy(stack);
        CompoundTag tag = NbtObfuscator.readCompound(root, CHANNEL);
        if (tag == null && root.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            tag = root.getCompound(ROOT_TAG);
        }
        if (tag == null) return null;
        if (tag.getInt("Version") != VERSION) return null;
        BloodType blood = BloodType.findByName(tag.getString("BloodType"));
        return new VaccineHostProfile(blood, tag.getInt("Findings"),
                tag.getBoolean("BloodVerified") && blood != null);
    }

    public static int countFindings(CompoundTag tag) {
        int count = 0;
        if (tag.contains("TemperatureC")) count++;
        if (tag.contains("HeartRate")) count++;
        if (tag.contains("LungSound")) count++;
        if (tag.contains("OxygenSaturation")) count++;
        if (tag.contains("Redness")) count++;
        if (tag.contains("ReflexDelay")) count++;
        if (hasCompleteBlood(tag)) count++;
        return count;
    }

    public static boolean hasCompleteBlood(CompoundTag tag) {
        if (!tag.contains("BloodType") || tag.getString("BloodType").isBlank()) return false;
        boolean hasResults = tag.contains("AntiA") && tag.contains("AntiB")
                && tag.contains("AntiD");
        boolean ranReagents = tag.getBoolean("ReagentA")
                && tag.getBoolean("ReagentB") && tag.getBoolean("ReagentD");
        return hasResults || ranReagents;
    }
}
