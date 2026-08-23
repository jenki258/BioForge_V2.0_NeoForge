package net.jenkimods.bioforge.vaccine;

import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;








public final class VaccineProfile {
    public static final String ROOT_TAG = "BioForgeVaccine";
    private static final String CHANNEL = "vaccine";
    public static final int CURRENT_VERSION = 2;
    public static final int MAX_PAYLOAD_LENGTH = 32_767;
    public static final int MAX_USES = 64;

    private static final String VERSION_TAG = "Version";
    private static final String STRAIN_TAG = "Strain";
    private static final String QUALITY_TAG = "Quality";
    private static final String USES_TAG = "Uses";
    private static final String DEFENSE_RISK_TAG = "DefenseMutationChance";
    private static final String BATCH_ID_TAG = "BatchId";
    private static final String CREATED_AT_TAG = "CreatedAt";
    private static final String CRISPR_SEQUENCE_TAG = "CrisprSequence";

    private final String strainPayload;
    private final float quality;
    private final int remainingUses;
    private final float defenseMutationChance;
    private final UUID batchId;
    private final long createdAt;
    private final String crisprSequence;

    public VaccineProfile(String strainPayload, float quality, int remainingUses,
                          float defenseMutationChance, UUID batchId, long createdAt) {
        this(strainPayload, quality, remainingUses, defenseMutationChance,
                batchId, createdAt, "");
    }

    public VaccineProfile(String strainPayload, float quality, int remainingUses,
                          float defenseMutationChance, UUID batchId, long createdAt,
                          String crisprSequence) {
        this.strainPayload = Objects.requireNonNull(strainPayload, "strainPayload");
        this.quality = sanitizeProbability(quality, 0.75f);
        this.remainingUses = Mth.clamp(remainingUses, 1, MAX_USES);
        this.defenseMutationChance = sanitizeProbability(defenseMutationChance, 0.18f);
        this.batchId = Objects.requireNonNull(batchId, "batchId");
        this.createdAt = Math.max(0L, createdAt);
        this.crisprSequence = crisprSequence != null
                && crisprSequence.matches("[ACGTN]{60}")
                ? crisprSequence : "";
    }

    public static VaccineProfile capture(InfectionData data, float quality, int uses,
                                         float defenseMutationChance, long gameTime) {
        if (data == null || !data.isInfected() || data.getPathogenId() == null) {
            throw new IllegalArgumentException("Cannot create a vaccine from a clean infection profile");
        }
        StrainData strain = StrainData.buildFrom(data);
        return new VaccineProfile(strain.toPayload(), quality, uses, defenseMutationChance,
                UUID.randomUUID(), gameTime);
    }

    public String strainPayload() {
        return strainPayload;
    }

    public StrainData strain() {
        return StrainData.parse(strainPayload);
    }

    public float quality() {
        return quality;
    }

    public int remainingUses() {
        return remainingUses;
    }

    public float defenseMutationChance() {
        return defenseMutationChance;
    }

    public UUID batchId() {
        return batchId;
    }

    public long createdAt() {
        return createdAt;
    }

    public String crisprSequence() {
        return crisprSequence;
    }

    public VaccineProfile withRemainingUses(int uses) {
        return new VaccineProfile(strainPayload, quality, uses, defenseMutationChance,
                batchId, createdAt, crisprSequence);
    }

    public boolean isValid() {
        if (strainPayload.isBlank() || strainPayload.length() > MAX_PAYLOAD_LENGTH) return false;
        StrainData parsed = strain();
        return parsed.getPathogenId() != null && remainingUses > 0;
    }

    public void write(ItemStack stack) {
        CompoundTag vaccineTag = new CompoundTag();
        vaccineTag.putInt(VERSION_TAG, CURRENT_VERSION);
        vaccineTag.putString(STRAIN_TAG, strainPayload);
        vaccineTag.putFloat(QUALITY_TAG, quality);
        vaccineTag.putInt(USES_TAG, remainingUses);
        vaccineTag.putFloat(DEFENSE_RISK_TAG, defenseMutationChance);
        vaccineTag.putUUID(BATCH_ID_TAG, batchId);
        vaccineTag.putLong(CREATED_AT_TAG, createdAt);
        if (!crisprSequence.isBlank()) {
            vaccineTag.putString(CRISPR_SEQUENCE_TAG, crisprSequence);
        }
        net.jenkimods.bioforge.util.StackData.update(stack,
                root -> root.remove(ROOT_TAG));
        NbtObfuscator.writeCompound(stack, CHANNEL, vaccineTag);
    }

    @Nullable
    public static VaccineProfile read(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !net.jenkimods.bioforge.util.StackData.has(stack)) return null;
        CompoundTag root = net.jenkimods.bioforge.util.StackData.copy(stack);
        if (root == null) return null;
        CompoundTag vaccineTag = NbtObfuscator.readCompound(root, CHANNEL);
        if (vaccineTag == null && root.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            vaccineTag = root.getCompound(ROOT_TAG);
        }
        if (vaccineTag == null) return null;
        int version = vaccineTag.getInt(VERSION_TAG);
        if (version <= 0 || version > CURRENT_VERSION) return null;

        String payload = vaccineTag.getString(STRAIN_TAG);
        if (payload.isBlank() || payload.length() > MAX_PAYLOAD_LENGTH) return null;
        int uses = vaccineTag.getInt(USES_TAG);
        if (uses <= 0) return null;

        float quality = vaccineTag.contains(QUALITY_TAG)
                ? vaccineTag.getFloat(QUALITY_TAG) : 0.75f;
        float defenseRisk = vaccineTag.contains(DEFENSE_RISK_TAG)
                ? vaccineTag.getFloat(DEFENSE_RISK_TAG) : 0.18f;
        UUID batch = vaccineTag.hasUUID(BATCH_ID_TAG)
                ? vaccineTag.getUUID(BATCH_ID_TAG)
                : UUID.nameUUIDFromBytes(payload.getBytes(StandardCharsets.UTF_8));
        long createdAt = vaccineTag.getLong(CREATED_AT_TAG);

        VaccineProfile result =
                new VaccineProfile(payload, quality, uses, defenseRisk, batch, createdAt,
                        version >= 2 ? vaccineTag.getString(CRISPR_SEQUENCE_TAG) : "");
        return result.isValid() ? result : null;
    }




    public static void consumeDose(ItemStack stack) {
        VaccineProfile profile = read(stack);
        if (profile == null) return;
        if (profile.remainingUses() <= 1) {
            stack.shrink(1);
        } else {
            profile.withRemainingUses(profile.remainingUses() - 1).write(stack);
        }
    }

    private static float sanitizeProbability(float value, float fallback) {
        if (!Float.isFinite(value)) return fallback;
        return Mth.clamp(value, 0.0f, 1.0f);
    }
}
