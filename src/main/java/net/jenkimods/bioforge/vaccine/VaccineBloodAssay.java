package net.jenkimods.bioforge.vaccine;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.crispr.BioForgeResearchData;
import net.jenkimods.bioforge.crispr.CrisprGuideProfile;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.item.BloodSampleUtil;
import net.jenkimods.bioforge.item.samples.TubeItem;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class VaccineBloodAssay {
    private static final String CHANNEL = "vaccine_blood_assay";
    private static final int VERSION = 3;
    private static final ResourceLocation DEFAULT_PROFILE =
            ResourceLocation.tryBuild(BioForge.MODID, "default");

    private VaccineBloodAssay() {}

    public record Data(UUID assayId, int resultPermille, boolean scanned,
                       String sampleFingerprint, String crisprSequence,
                       String crisprFeedback) {
        public float result() {
            return Mth.clamp(resultPermille, 0, 1000) / 1000.0F;
        }
    }

    public static boolean canCreate(ItemStack tube, ItemStack vaccine) {
        return tube != null && !tube.isEmpty()
                && tube.getItem() instanceof TubeItem
                && BloodSampleUtil.hasBlood(tube)
                && !isAssay(tube) && vaccineData(vaccine) != null;
    }

    public static boolean createAndConsume(Player player, ItemStack tube,
                                           ItemStack vaccine) {
        VaccineData vaccineData = vaccineData(vaccine);
        if (player == null || vaccineData == null || !canCreate(tube, vaccine)) {
            return false;
        }

        String bloodPayload = NbtObfuscator.readInfection(tube);
        float compatibility = compatibility(
                vaccineData.strainPayload(), bloodPayload, assaySettings());
        int resultPermille = Mth.clamp(Math.round(
                vaccineData.quality() * compatibility * 1000.0F), 0, 1000);

        CompoundTag assay = new CompoundTag();
        assay.putInt("Version", VERSION);
        assay.putUUID("AssayId", UUID.randomUUID());
        assay.putInt("Result", resultPermille);
        assay.putBoolean("Scanned", false);
        assay.putString("SampleFingerprint",
                StrainFingerprint.ofPayload(bloodPayload));
        String feedback = crisprFeedback(
                vaccineData.crisprSequence(), bloodPayload);
        assay.putString("CrisprSequence", vaccineData.crisprSequence());
        assay.putString("CrisprFeedback", feedback);
        NbtObfuscator.writeCompound(tube, CHANNEL, assay);

        if (!player.getAbilities().instabuild) {
            if (vaccineData.directed()) DirectedVaccineProfile.consumeDose(vaccine);
            else VaccineProfile.consumeDose(vaccine);
        }
        return true;
    }

    public static boolean isAssay(ItemStack stack) {
        return read(stack) != null;
    }

    public static boolean isScanned(ItemStack stack) {
        Data data = read(stack);
        return data != null && data.scanned();
    }

    public static boolean matchesScannedSample(ItemStack stack,
                                               String strainPayload) {
        Data data = read(stack);
        return data != null && data.scanned()
                && !data.sampleFingerprint().isBlank()
                && data.sampleFingerprint().equals(
                StrainFingerprint.ofPayload(strainPayload));
    }

    public static float feedback(ItemStack stack, String strainPayload) {
        Data data = read(stack);
        return matchesScannedSample(stack, strainPayload) && data != null
                ? data.result() : 0.0F;
    }

    public static boolean markScanned(ItemStack stack) {
        Data data = read(stack);
        if (data == null || data.scanned()) return false;
        CompoundTag assay = new CompoundTag();
        assay.putInt("Version", VERSION);
        assay.putUUID("AssayId", data.assayId());
        assay.putInt("Result", data.resultPermille());
        assay.putBoolean("Scanned", true);
        assay.putString("SampleFingerprint", data.sampleFingerprint());
        assay.putString("CrisprSequence", data.crisprSequence());
        assay.putString("CrisprFeedback", data.crisprFeedback());
        NbtObfuscator.writeCompound(stack, CHANNEL, assay);
        return true;
    }

    public static int visibleResultPermille(ItemStack stack) {
        Data data = read(stack);
        return data != null && data.scanned() ? data.resultPermille() : -1;
    }

    @Nullable
    public static Data read(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !net.jenkimods.bioforge.util.StackData.has(stack)) return null;
        CompoundTag assay = NbtObfuscator.readCompound(stack, CHANNEL);
        int version = assay == null ? 0 : assay.getInt("Version");
        if (assay == null || version < 1 || version > VERSION
                || !assay.hasUUID("AssayId")) return null;
        return new Data(assay.getUUID("AssayId"),
                Mth.clamp(assay.getInt("Result"), 0, 1000),
                assay.getBoolean("Scanned"),
                version >= 2 ? assay.getString("SampleFingerprint") : "",
                version >= 3 ? assay.getString("CrisprSequence") : "",
                version >= 3 ? assay.getString("CrisprFeedback") : "");
    }

    private static float compatibility(String vaccinePayload,
                                       @Nullable String bloodPayload,
                                       VaccineCorrectionProfile.AssaySettings settings) {
        if (vaccinePayload == null || vaccinePayload.isBlank()
                || bloodPayload == null || bloodPayload.isBlank()) {
            return settings.mismatchMultiplier();
        }
        String vaccineCanonical = StrainData.canonicalGeneticPayload(vaccinePayload);
        String bloodCanonical = StrainData.canonicalGeneticPayload(bloodPayload);
        if (vaccineCanonical.equals(bloodCanonical)) {
            return settings.exactStrainMultiplier();
        }
        StrainData vaccineStrain = StrainData.parse(vaccinePayload);
        StrainData bloodStrain = StrainData.parse(bloodPayload);
        return vaccineStrain.getPathogenId() != null
                && vaccineStrain.getPathogenId().equals(bloodStrain.getPathogenId())
                ? settings.samePathogenMultiplier()
                : settings.mismatchMultiplier();
    }

    private static VaccineCorrectionProfile.AssaySettings assaySettings() {
        return BioForgeResearchData.correctionProfile(DEFAULT_PROFILE)
                .map(VaccineCorrectionProfile::assay)
                .orElse(VaccineCorrectionProfile.AssaySettings.DEFAULT);
    }

    @Nullable
    private static VaccineData vaccineData(ItemStack stack) {
        VaccineProfile full = VaccineProfile.read(stack);
        if (full != null) {
            return new VaccineData(full.strainPayload(), full.quality(), false,
                    full.crisprSequence());
        }
        DirectedVaccineProfile directed = DirectedVaccineProfile.read(stack);
        return directed == null ? null : new VaccineData(
                directed.strainPayload(), directed.quality(), true, "");
    }

    private record VaccineData(String strainPayload, float quality,
                               boolean directed, String crisprSequence) {}

    private static String crisprFeedback(String actual,
                                         @Nullable String samplePayload) {
        if (actual == null || !actual.matches("[ACGTN]{60}")
                || samplePayload == null || samplePayload.isBlank()) return "";
        CrisprGuideProfile profile = BioForgeResearchData.guideProfile(
                ResourceLocation.tryBuild(BioForge.MODID, "default")).orElse(null);
        if (profile == null) return "";
        String expected = profile.deriveSequence(StrainData.parse(samplePayload));
        if (expected.length() != actual.length()) return "";
        char[] result = new char[actual.length()];
        int[] remaining = new int[26];
        for (int index = 0; index < actual.length(); index++) {
            if (actual.charAt(index) == expected.charAt(index)) {
                result[index] = 'C';
            } else {
                char expectedBase = expected.charAt(index);
                if (expectedBase >= 'A' && expectedBase <= 'Z') {
                    remaining[expectedBase - 'A']++;
                }
            }
        }
        for (int index = 0; index < actual.length(); index++) {
            if (result[index] == 'C') continue;
            char actualBase = actual.charAt(index);
            int bucket = actualBase >= 'A' && actualBase <= 'Z'
                    ? actualBase - 'A' : -1;
            if (bucket >= 0 && remaining[bucket] > 0) {
                result[index] = 'P';
                remaining[bucket]--;
            } else {
                result[index] = 'X';
            }
        }
        return new String(result);
    }
}
