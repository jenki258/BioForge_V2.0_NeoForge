package net.jenkimods.bioforge.vaccine;

import net.jenkimods.bioforge.crispr.VaccineTargetCategory;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record DirectedVaccineProfile(
        String strainPayload,
        VaccineTargetCategory category,
        String target,
        ResourceLocation actionId,
        float quality,
        int remainingUses,
        float defenseMutationChance,
        UUID batchId,
        long createdAt
) {
    public static final String ROOT_TAG = "BioForgeDirectedVaccine";
    private static final String CHANNEL = "directed_vaccine";
    private static final int VERSION = 1;

    public DirectedVaccineProfile {
        quality = Mth.clamp(Float.isFinite(quality) ? quality : 0.0f, 0.0f, 1.0f);
        remainingUses = Mth.clamp(remainingUses, 1, 64);
        defenseMutationChance = Mth.clamp(
                Float.isFinite(defenseMutationChance) ? defenseMutationChance : 0.18f,
                0.0f, 1.0f);
    }

    public StrainData strain() {
        return StrainData.parse(strainPayload);
    }

    public boolean isValid() {
        return !strainPayload.isBlank() && strain().getPathogenId() != null
                && category != null && target != null && !target.isBlank()
                && actionId != null && remainingUses > 0;
    }

    public void write(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        tag.putString("Strain", strainPayload);
        tag.putString("Category", category.serializedName());
        tag.putString("Target", target);
        tag.putString("Action", actionId.toString());
        tag.putFloat("Quality", quality);
        tag.putInt("Uses", remainingUses);
        tag.putFloat("DefenseMutationChance", defenseMutationChance);
        tag.putUUID("BatchId", batchId);
        tag.putLong("CreatedAt", createdAt);
        net.jenkimods.bioforge.util.StackData.update(stack,
                root -> root.remove(ROOT_TAG));
        NbtObfuscator.writeCompound(stack, CHANNEL, tag);
    }

    @Nullable
    public static DirectedVaccineProfile read(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !net.jenkimods.bioforge.util.StackData.has(stack)) return null;
        CompoundTag root = net.jenkimods.bioforge.util.StackData.copy(stack);
        CompoundTag tag = NbtObfuscator.readCompound(root, CHANNEL);
        if (tag == null && root.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            tag = root.getCompound(ROOT_TAG);
        }
        if (tag == null) return null;
        if (tag.getInt("Version") != VERSION) return null;
        VaccineTargetCategory category =
                VaccineTargetCategory.fromName(tag.getString("Category"));
        ResourceLocation action = ResourceLocation.tryParse(tag.getString("Action"));
        UUID batch = tag.hasUUID("BatchId") ? tag.getUUID("BatchId") : UUID.randomUUID();
        DirectedVaccineProfile profile = new DirectedVaccineProfile(
                tag.getString("Strain"), category, tag.getString("Target"), action,
                tag.getFloat("Quality"), tag.getInt("Uses"),
                tag.getFloat("DefenseMutationChance"), batch, tag.getLong("CreatedAt"));
        return profile.isValid() ? profile : null;
    }

    public static void consumeDose(ItemStack stack) {
        DirectedVaccineProfile profile = read(stack);
        if (profile == null) return;
        if (profile.remainingUses <= 1) {
            stack.shrink(1);
        } else {
            new DirectedVaccineProfile(profile.strainPayload, profile.category, profile.target,
                    profile.actionId, profile.quality, profile.remainingUses - 1,
                    profile.defenseMutationChance, profile.batchId, profile.createdAt).write(stack);
        }
    }
}
