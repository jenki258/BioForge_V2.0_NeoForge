package net.jenkimods.bioforge.vaccine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record VaccineCorrectionProfile(
        ResourceLocation id,
        int schemaVersion,
        Set<TargetFamily> includedFamilies,
        int targetsPerPage,
        int numericStates,
        int percentageSteps,
        int maximumIncubationTicks,
        BlendMode blendMode,
        float crisprWeight,
        float correctionWeight,
        Map<TargetFamily, Float> familyWeights,
        Map<TargetKey, TargetOverride> overrides,
        AssaySettings assay
) {
    public VaccineCorrectionProfile {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException(
                    "Unsupported vaccine correction schema version: " + schemaVersion);
        }
        if (targetsPerPage < 1 || targetsPerPage > 6) {
            throw new IllegalArgumentException("targets_per_page must be between 1 and 6");
        }
        if (numericStates < 2 || numericStates > 64) {
            throw new IllegalArgumentException("numeric_states must be between 2 and 64");
        }
        if (percentageSteps < 1 || percentageSteps > 1000) {
            throw new IllegalArgumentException(
                    "percentage_steps must be between 1 and 1000");
        }
        if (maximumIncubationTicks < 0 || maximumIncubationTicks > 100000) {
            throw new IllegalArgumentException(
                    "maximum_incubation_ticks must be between 0 and 100000");
        }
        if (!Float.isFinite(crisprWeight) || crisprWeight < 0.0F
                || !Float.isFinite(correctionWeight) || correctionWeight < 0.0F
                || crisprWeight + correctionWeight <= 0.0F) {
            throw new IllegalArgumentException(
                    "CRISPR and correction quality weights must have a positive total");
        }
        includedFamilies = Set.copyOf(includedFamilies);
        familyWeights = Map.copyOf(familyWeights);
        overrides = Map.copyOf(overrides);
        if (assay == null) assay = AssaySettings.DEFAULT;
    }

    public boolean includes(TargetFamily family) {
        return includedFamilies.contains(family);
    }

    public float normalizedCrisprWeight() {
        return crisprWeight / (crisprWeight + correctionWeight);
    }

    public float normalizedCorrectionWeight() {
        return correctionWeight / (crisprWeight + correctionWeight);
    }

    public float familyWeight(TargetFamily family) {
        return familyWeights.getOrDefault(family, 1.0F);
    }

    public TargetOverride targetOverride(TargetFamily family, String target) {
        return overrides.getOrDefault(
                new TargetKey(family, target), TargetOverride.DEFAULT);
    }

    public static VaccineCorrectionProfile fromJson(ResourceLocation id, JsonObject json) {
        int schemaVersion = GsonHelper.getAsInt(json, "schema_version", 1);

        JsonObject include = objectOrEmpty(json, "include");
        EnumSet<TargetFamily> included = EnumSet.noneOf(TargetFamily.class);
        for (TargetFamily family : TargetFamily.values()) {
            if (GsonHelper.getAsBoolean(include, family.serializedName(), true)) {
                included.add(family);
            }
        }

        JsonObject controls = objectOrEmpty(json, "controls");
        int targetsPerPage = GsonHelper.getAsInt(controls, "targets_per_page", 6);
        int numericStates = GsonHelper.getAsInt(controls, "numeric_states", 16);
        int percentageSteps = GsonHelper.getAsInt(
                controls, "percentage_steps", 100);
        int maximumIncubationTicks = GsonHelper.getAsInt(
                controls, "maximum_incubation_ticks", 72000);

        JsonObject blend = objectOrEmpty(json, "quality_blend");
        BlendMode blendMode = BlendMode.fromName(
                GsonHelper.getAsString(blend, "mode", "geometric"));
        float crisprWeight = GsonHelper.getAsFloat(blend, "crispr_weight", 0.55F);
        float correctionWeight = GsonHelper.getAsFloat(
                blend, "correction_weight", 0.45F);

        JsonObject weights = objectOrEmpty(json, "family_weights");
        EnumMap<TargetFamily, Float> familyWeights =
                new EnumMap<>(TargetFamily.class);
        for (TargetFamily family : TargetFamily.values()) {
            float weight = GsonHelper.getAsFloat(
                    weights, family.serializedName(), family.defaultWeight());
            if (!Float.isFinite(weight) || weight < 0.0F) {
                throw new IllegalArgumentException(
                        "Invalid weight for target family " + family.serializedName());
            }
            familyWeights.put(family, weight);
        }

        Map<TargetKey, TargetOverride> overrides = new LinkedHashMap<>();
        JsonArray overrideArray = json.has("overrides")
                ? GsonHelper.getAsJsonArray(json, "overrides") : new JsonArray();
        for (JsonElement element : overrideArray) {
            JsonObject entry = GsonHelper.convertToJsonObject(
                    element, "vaccine correction override");
            TargetFamily family = TargetFamily.fromName(
                    GsonHelper.getAsString(entry, "family"));
            String target = normalizeTarget(GsonHelper.getAsString(entry, "target"));
            TargetKey key = new TargetKey(family, target);
            boolean enabled = GsonHelper.getAsBoolean(entry, "enabled", true);
            float weight = GsonHelper.getAsFloat(entry, "weight", -1.0F);
            int states = GsonHelper.getAsInt(entry, "states", 0);
            if (!Float.isFinite(weight) || weight < -1.0F) {
                throw new IllegalArgumentException("Override weight must be -1 or greater");
            }
            if (states != 0 && (states < 2 || states > 100001)) {
                throw new IllegalArgumentException(
                        "Override states must be 0 or between 2 and 100001");
            }
            if (overrides.putIfAbsent(
                    key, new TargetOverride(enabled, weight, states)) != null) {
                throw new IllegalArgumentException(
                        "Duplicate vaccine correction override: " + key.serialized());
            }
        }

        JsonObject assayJson = objectOrEmpty(json, "assay");
        float exactStrainMultiplier = GsonHelper.getAsFloat(
                assayJson, "exact_strain_multiplier", 1.0F);
        float samePathogenMultiplier = GsonHelper.getAsFloat(
                assayJson, "same_pathogen_multiplier", 0.15F);
        float mismatchMultiplier = GsonHelper.getAsFloat(
                assayJson, "mismatch_multiplier", 0.0F);
        List<String> calibrationSliders = new ArrayList<>();
        if (assayJson.has("calibration_sliders")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(
                    assayJson, "calibration_sliders")) {
                calibrationSliders.add(element.getAsString());
            }
        } else {
            calibrationSliders.addAll(AssaySettings.DEFAULT.calibrationSliders());
        }
        AssaySettings assay = new AssaySettings(exactStrainMultiplier,
                samePathogenMultiplier, mismatchMultiplier, calibrationSliders);

        return new VaccineCorrectionProfile(
                id, schemaVersion, included, targetsPerPage, numericStates,
                percentageSteps, maximumIncubationTicks,
                blendMode, crisprWeight, correctionWeight, familyWeights,
                overrides, assay);
    }

    public int percentageStates() {
        return percentageSteps + 1;
    }

    private static JsonObject objectOrEmpty(JsonObject json, String name) {
        return json.has(name) ? GsonHelper.getAsJsonObject(json, name) : new JsonObject();
    }

    private static String normalizeTarget(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Vaccine correction target cannot be empty");
        }
        return normalized;
    }

    public enum TargetFamily {
        SYMPTOM(0.45F),
        MUTATION(0.30F),
        TRANSMISSION(0.15F),
        PATHOGEN(0.10F),
        LIFECYCLE(0.10F);

        private final float defaultWeight;

        TargetFamily(float defaultWeight) {
            this.defaultWeight = defaultWeight;
        }

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public float defaultWeight() {
            return defaultWeight;
        }

        public static TargetFamily fromName(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Unknown vaccine correction target family: " + value);
            }
        }
    }

    public enum BlendMode {
        ARITHMETIC,
        GEOMETRIC,
        HARMONIC;

        public static BlendMode fromName(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Unknown vaccine quality blend mode: " + value);
            }
        }
    }

    public record TargetKey(TargetFamily family, String target) {
        public TargetKey {
            target = normalizeTarget(target);
        }

        public String serialized() {
            return family.serializedName() + ":" + target;
        }
    }

    public record TargetOverride(boolean enabled, float weight, int states) {
        public static final TargetOverride DEFAULT =
                new TargetOverride(true, -1.0F, 0);

        public float resolveWeight(float fallback) {
            return weight < 0.0F ? fallback : weight;
        }

        public int resolveStates(int fallback) {
            return states == 0 ? fallback : states;
        }
    }

    public record AssaySettings(
            float exactStrainMultiplier,
            float samePathogenMultiplier,
            float mismatchMultiplier,
            List<String> calibrationSliders
    ) {
        public static final AssaySettings DEFAULT = new AssaySettings(
                1.0F, 0.15F, 0.0F,
                List.of("focus", "contrast", "spectrum", "phase", "stability"));

        public AssaySettings {
            validateMultiplier("exact_strain_multiplier", exactStrainMultiplier);
            validateMultiplier("same_pathogen_multiplier", samePathogenMultiplier);
            validateMultiplier("mismatch_multiplier", mismatchMultiplier);
            if (calibrationSliders == null || calibrationSliders.isEmpty()
                    || calibrationSliders.size() > 6) {
                throw new IllegalArgumentException(
                        "assay calibration_sliders must contain between 1 and 6 entries");
            }
            List<String> normalized = new ArrayList<>(calibrationSliders.size());
            for (String slider : calibrationSliders) {
                String value = slider == null ? ""
                        : slider.trim().toLowerCase(Locale.ROOT);
                if (value.isEmpty() || !value.matches("[a-z0-9_.-]+")) {
                    throw new IllegalArgumentException(
                            "Invalid assay calibration slider: " + slider);
                }
                normalized.add(value);
            }
            calibrationSliders = List.copyOf(normalized);
        }

        private static void validateMultiplier(String name, float value) {
            if (!Float.isFinite(value) || value < 0.0F || value > 1.0F) {
                throw new IllegalArgumentException(
                        name + " must be between 0 and 1");
            }
        }
    }
}
