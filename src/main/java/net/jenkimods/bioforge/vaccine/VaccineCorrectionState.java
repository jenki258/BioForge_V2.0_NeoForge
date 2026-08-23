package net.jenkimods.bioforge.vaccine;

import net.jenkimods.bioforge.infection.InfectionType;
import net.jenkimods.bioforge.infection.HeartRate;
import net.jenkimods.bioforge.infection.LungSound;
import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.infection.symptoms.SymptomKey;
import net.jenkimods.bioforge.api.definition.SymptomDefinition;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.mutation.MutationDefinition;
import net.jenkimods.bioforge.mutation.MutationLoader;
import net.jenkimods.bioforge.infection.lifecycle.InfectionLifecycleRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VaccineCorrectionState {
    private String strainFingerprint = "";
    private ResourceLocation profileId;
    private final Map<VaccineCorrectionProfile.TargetKey, Integer> selections =
            new LinkedHashMap<>();

    public boolean ensure(StrainData strain, VaccineCorrectionProfile profile) {
        if (strain == null || strain.getPathogenId() == null || profile == null) {
            return clear();
        }
        String fingerprint = fingerprint(strain);
        boolean changed = !fingerprint.equals(strainFingerprint)
                || !profile.id().equals(profileId);
        if (changed) {
            strainFingerprint = fingerprint;
            profileId = profile.id();
            selections.clear();
        }

        List<ResolvedTarget> targets = resolveTargets(strain, profile);
        Set<VaccineCorrectionProfile.TargetKey> valid = new HashSet<>();
        for (ResolvedTarget target : targets) {
            valid.add(target.key());
            Integer selected = selections.get(target.key());
            if (selected == null || selected < 0 || selected >= target.states()) {
                selections.put(target.key(), initialState(target));
                changed = true;
            }
        }
        if (selections.keySet().removeIf(key -> !valid.contains(key))) {
            changed = true;
        }
        return changed;
    }

    public List<Target> targets(StrainData strain, VaccineCorrectionProfile profile) {
        ensure(strain, profile);
        if (strain == null || profile == null) return List.of();
        return resolveTargets(strain, profile).stream()
                .map(target -> new Target(
                        target.key().family(),
                        target.key().target(),
                        target.states(),
                        selections.getOrDefault(target.key(), 0),
                        target.valueKind(),
                        target.displayMinimum(),
                        target.displayMaximum()))
                .toList();
    }

    public boolean setSelection(StrainData strain, VaccineCorrectionProfile profile,
                                int targetIndex, int state) {
        ensure(strain, profile);
        if (strain == null || profile == null) return false;
        List<ResolvedTarget> targets = resolveTargets(strain, profile);
        if (targetIndex < 0 || targetIndex >= targets.size()) return false;
        ResolvedTarget target = targets.get(targetIndex);
        int clamped = Math.max(0, Math.min(target.states() - 1, state));
        Integer previous = selections.put(target.key(), clamped);
        return previous == null || previous != clamped;
    }

    public boolean cycleSelection(StrainData strain, VaccineCorrectionProfile profile,
                                  int targetIndex, int direction) {
        ensure(strain, profile);
        if (strain == null || profile == null) return false;
        List<ResolvedTarget> targets = resolveTargets(strain, profile);
        if (targetIndex < 0 || targetIndex >= targets.size()) return false;
        ResolvedTarget target = targets.get(targetIndex);
        int current = selections.getOrDefault(target.key(), 0);
        int next = Math.floorMod(current + Integer.signum(direction), target.states());
        if (next == current) return false;
        selections.put(target.key(), next);
        return true;
    }

    public boolean reset(StrainData strain, VaccineCorrectionProfile profile) {
        ensure(strain, profile);
        if (strain == null || profile == null) return false;
        Map<VaccineCorrectionProfile.TargetKey, Integer> previous =
                new LinkedHashMap<>(selections);
        selections.clear();
        ensure(strain, profile);
        return !previous.equals(selections);
    }

    public int applyTemplate(StrainData strain, VaccineCorrectionProfile profile,
                             VaccineCorrectionNotes.Data data) {
        ensure(strain, profile);
        if (strain == null || profile == null || data == null) return 0;
        Map<String, VaccineCorrectionNotes.Entry> stored = new LinkedHashMap<>();
        for (VaccineCorrectionNotes.Entry entry : data.entries()) {
            stored.put(entry.family().toLowerCase(Locale.ROOT) + '|'
                    + entry.target(), entry);
        }
        int applied = 0;
        for (ResolvedTarget target : resolveTargets(strain, profile)) {
            VaccineCorrectionNotes.Entry entry = stored.get(
                    target.key().family().serializedName() + '|'
                            + target.key().target());
            if (entry == null) continue;
            float normalized = entry.states() <= 1 ? 0.0F
                    : entry.state() / (float) (entry.states() - 1);
            int state = Math.round(normalized * (target.states() - 1));
            selections.put(target.key(), Math.max(0,
                    Math.min(target.states() - 1, state)));
            applied++;
        }
        return applied;
    }

    public int importMedicalReport(StrainData strain,
                                   VaccineCorrectionProfile profile,
                                   CompoundTag report) {
        ensure(strain, profile);
        if (strain == null || profile == null || report == null) return 0;
        int imported = 0;
        for (ResolvedTarget target : resolveTargets(strain, profile)) {
            Integer state = medicalState(target, report);
            if (state == null) continue;
            selections.put(target.key(), Math.max(0,
                    Math.min(target.states() - 1, state)));
            imported++;
        }
        return imported;
    }

    @Nullable
    private static Integer medicalState(ResolvedTarget target, CompoundTag report) {
        String id = target.key().target();
        return switch (id) {
            case "temperature_plus" -> report.contains("TemperatureC")
                    ? booleanState(report.getFloat("TemperatureC") >= 38.0F,
                    target.states()) : null;
            case "temperature_minus" -> report.contains("TemperatureC")
                    ? booleanState(report.getFloat("TemperatureC") <= 35.5F,
                    target.states()) : null;
            case "heart_rate" -> report.contains("HeartRate")
                    ? quantizeOrdinal(HeartRate.fromName(
                    report.getString("HeartRate")).ordinal(),
                    HeartRate.values().length, target.states()) : null;
            case "lung_sound" -> report.contains("LungSound")
                    ? quantizeOrdinal(LungSound.fromName(
                    report.getString("LungSound")).ordinal(),
                    LungSound.values().length, target.states()) : null;
            case "oxygen_saturation" -> report.contains("OxygenSaturation")
                    ? valueState(report.getFloat("OxygenSaturation"), target) : null;
            case "perfusion_index" -> report.contains("PerfusionIndex")
                    ? valueState(report.getFloat("PerfusionIndex"), target) : null;
            case "otoscope_redness" -> report.contains("Redness")
                    ? valueState(report.getFloat("Redness"), target) : null;
            case "otoscope_lesions" -> report.contains("Lesions")
                    ? valueState(report.getFloat("Lesions"), target) : null;
            case "otoscope_secretion" -> report.contains("Secretion")
                    ? valueState(report.getFloat("Secretion"), target) : null;
            case "otoscope_swelling" -> report.contains("Swelling")
                    ? valueState(report.getFloat("Swelling"), target) : null;
            case "reflex_delay" -> report.contains("ReflexDelay")
                    ? valueState(categoryValue(report.getString("ReflexDelay"),
                    0.08F, 0.22F, 0.40F), target) : null;
            case "reflex_strength" -> report.contains("ReflexStrength")
                    ? valueState(categoryValue(report.getString("ReflexStrength"),
                    0.15F, 0.50F, 0.85F), target) : null;
            default -> null;
        };
    }

    private static int booleanState(boolean value, int states) {
        return value ? Math.max(0, states - 1) : 0;
    }

    private static int valueState(float value, ResolvedTarget target) {
        float span = target.displayMaximum() - target.displayMinimum();
        float normalized = span <= 0.0F ? 0.0F
                : (value - target.displayMinimum()) / span;
        normalized = Math.max(0.0F, Math.min(1.0F, normalized));
        return Math.round(normalized * (target.states() - 1));
    }

    private static float categoryValue(String value, float low,
                                       float moderate, float high) {
        if ("high".equalsIgnoreCase(value)) return high;
        if ("moderate".equalsIgnoreCase(value)) return moderate;
        return low;
    }

    public float quality(StrainData strain, VaccineCorrectionProfile profile) {
        ensure(strain, profile);
        if (strain == null || profile == null) return 0.0F;
        List<ResolvedTarget> targets = resolveTargets(strain, profile);
        if (targets.isEmpty()) return 0.0F;

        Map<VaccineCorrectionProfile.TargetFamily, Float> earned =
                new EnumMap<>(VaccineCorrectionProfile.TargetFamily.class);
        Map<VaccineCorrectionProfile.TargetFamily, Float> possible =
                new EnumMap<>(VaccineCorrectionProfile.TargetFamily.class);
        for (ResolvedTarget target : targets) {
            int selected = selections.getOrDefault(target.key(), 0);
            float distance = Math.abs(selected - target.expectedState());
            float match = 1.0F - distance / Math.max(1, target.states() - 1);
            earned.merge(target.key().family(), match * target.targetWeight(), Float::sum);
            possible.merge(target.key().family(), target.targetWeight(), Float::sum);
        }

        float weightedQuality = 0.0F;
        float usedFamilyWeight = 0.0F;
        for (VaccineCorrectionProfile.TargetFamily family
                : VaccineCorrectionProfile.TargetFamily.values()) {
            float possibleInFamily = possible.getOrDefault(family, 0.0F);
            if (possibleInFamily <= 0.0F) continue;
            float familyWeight = profile.familyWeight(family);
            weightedQuality += familyWeight
                    * earned.getOrDefault(family, 0.0F) / possibleInFamily;
            usedFamilyWeight += familyWeight;
        }
        return usedFamilyWeight <= 0.0F
                ? 0.0F : weightedQuality / usedFamilyWeight;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("StrainFingerprint", strainFingerprint);
        if (profileId != null) tag.putString("Profile", profileId.toString());
        ListTag storedSelections = new ListTag();
        selections.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator
                        .comparing(VaccineCorrectionProfile.TargetKey::serialized)))
                .forEach(entry -> {
                    CompoundTag selection = new CompoundTag();
                    selection.putString(
                            "Family", entry.getKey().family().serializedName());
                    selection.putString("Target", entry.getKey().target());
                    selection.putInt("State", entry.getValue());
                    storedSelections.add(selection);
                });
        tag.put("Selections", storedSelections);
        return tag;
    }

    public void load(CompoundTag tag) {
        strainFingerprint = tag.getString("StrainFingerprint");
        profileId = ResourceLocation.tryParse(tag.getString("Profile"));
        selections.clear();
        ListTag storedSelections = tag.getList("Selections", Tag.TAG_COMPOUND);
        for (int index = 0; index < storedSelections.size(); index++) {
            CompoundTag selection = storedSelections.getCompound(index);
            try {
                VaccineCorrectionProfile.TargetFamily family =
                        VaccineCorrectionProfile.TargetFamily.fromName(
                                selection.getString("Family"));
                VaccineCorrectionProfile.TargetKey key =
                        new VaccineCorrectionProfile.TargetKey(
                                family, selection.getString("Target"));
                selections.put(key, Math.max(0, selection.getInt("State")));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private boolean clear() {
        if (strainFingerprint.isEmpty() && profileId == null && selections.isEmpty()) {
            return false;
        }
        strainFingerprint = "";
        profileId = null;
        selections.clear();
        return true;
    }

    private List<ResolvedTarget> resolveTargets(
            StrainData strain, VaccineCorrectionProfile profile) {
        List<ResolvedTarget> targets = new ArrayList<>();
        if (profile.includes(VaccineCorrectionProfile.TargetFamily.SYMPTOM)) {
            appendSymptoms(strain, profile, targets);
        }
        if (profile.includes(VaccineCorrectionProfile.TargetFamily.MUTATION)) {
            appendMutations(strain, profile, targets);
        }
        if (profile.includes(VaccineCorrectionProfile.TargetFamily.TRANSMISSION)) {
            appendTransmissions(strain, profile, targets);
        }
        if (profile.includes(VaccineCorrectionProfile.TargetFamily.PATHOGEN)) {
            appendPathogen(strain, profile, targets);
        }
        if (profile.includes(VaccineCorrectionProfile.TargetFamily.LIFECYCLE)) {
            appendLifecycle(strain, profile, targets);
        }
        targets.sort(Comparator
                .comparing((ResolvedTarget target) -> target.key().family().ordinal())
                .thenComparing(target -> target.key().target()));
        return targets;
    }

    private void appendSymptoms(StrainData strain, VaccineCorrectionProfile profile,
                                List<ResolvedTarget> targets) {
        Map<SymptomKey<?>, float[]> ranges =
                BioForgeSymptoms.getDefaultRanges(strain.getPathogenId());
        BioForgeSymptoms.getEnabledSymptomKeys().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    SymptomKey<?> key = entry.getValue();
                    SymptomDefinition definition = BioForgeDefinitionManager
                            .symptom(BioForgeDefinitionManager.symptomId(entry.getKey()))
                            .orElse(null);
                    boolean booleanValue = key.getType() == Boolean.class
                            || definition != null && definition.valueType()
                            == SymptomDefinition.ValueType.BOOLEAN;
                    boolean enumValue = key.getType().isEnum()
                            || definition != null && definition.valueType()
                            == SymptomDefinition.ValueType.ENUM;
                    int enumValues = key.getType().isEnum()
                            ? key.getType().getEnumConstants().length
                            : definition == null ? 0 : definition.allowedValues().size();
                    VaccineCorrectionProfile.TargetKey targetKey = targetKey(
                            VaccineCorrectionProfile.TargetFamily.SYMPTOM, entry.getKey());
                    VaccineCorrectionProfile.TargetOverride override =
                            profile.targetOverride(targetKey.family(), targetKey.target());
                    if (!override.enabled()) return;
                    int defaultStates = booleanValue ? 2
                            : enumValue
                            ? Math.max(2, enumValues)
                            : profile.numericStates();
                    String raw = strain.getSymptom(entry.getKey())
                            .orElse(String.valueOf(key.getDefaultValue()));
                    ValueKind valueKind;
                    float displayMinimum;
                    float displayMaximum;
                    if (booleanValue) {
                        valueKind = ValueKind.BOOLEAN;
                        displayMinimum = 0.0F;
                        displayMaximum = 1.0F;
                    } else if (enumValue) {
                        valueKind = ValueKind.ENUM;
                        displayMinimum = 0.0F;
                        displayMaximum = Math.max(
                                1, enumValues - 1);
                    } else {
                        float rangeMinimum = ranges.get(key) == null
                                ? 0.0F : ranges.get(key)[0];
                        float rangeMaximum = ranges.get(key) == null
                                ? 1.0F : ranges.get(key)[1];
                        boolean extendedNumber = entry.getKey().equals("colony_radius")
                                || entry.getKey().equals("max_infested_blocks");
                        if (extendedNumber) {
                            rangeMinimum = 0.0F;
                            rangeMaximum = 10000.0F;
                        }
                        if (rangeMinimum >= 0.0F
                                && rangeMaximum <= 1.0F) {
                            valueKind = ValueKind.PERCENTAGE;
                            displayMinimum = 0.0F;
                            displayMaximum = 1.0F;
                        } else {
                            valueKind = ValueKind.NUMBER;
                            displayMinimum = rangeMinimum;
                            displayMaximum = rangeMaximum;
                        }
                    }
                    int states = override.resolveStates(
                            valueKind == ValueKind.PERCENTAGE
                                    ? profile.percentageStates()
                                    : entry.getKey().equals("colony_radius")
                                    ? 100001
                                    : entry.getKey().equals("max_infested_blocks")
                                    ? 10001
                                    : defaultStates);
                    int expected = expectedSymptomState(
                            key, definition, raw,
                            new float[]{displayMinimum, displayMaximum}, states);
                    targets.add(new ResolvedTarget(
                            targetKey, states, expected,
                            override.resolveWeight(1.0F), valueKind,
                            displayMinimum, displayMaximum));
                });
    }

    private void appendMutations(StrainData strain, VaccineCorrectionProfile profile,
                                 List<ResolvedTarget> targets) {
        MutationLoader.INSTANCE.getMutationsForPathogen(strain.getPathogenId()).stream()
                .filter(MutationDefinition::enabled)
                .filter(definition -> definition.isCompatible(strain.getPathogenId()))
                .sorted(Comparator.comparing(MutationDefinition::id))
                .forEach(definition -> {
                    VaccineCorrectionProfile.TargetKey key = targetKey(
                            VaccineCorrectionProfile.TargetFamily.MUTATION,
                            definition.id());
                    VaccineCorrectionProfile.TargetOverride override =
                            profile.targetOverride(key.family(), key.target());
                    if (!override.enabled()) return;
                    int states = override.resolveStates(2);
                    int expected = strain.getMutationIds().contains(definition.id()) ? 1 : 0;
                    targets.add(new ResolvedTarget(
                            key, states, expected, override.resolveWeight(1.0F),
                            ValueKind.BOOLEAN, 0.0F, 1.0F));
                });
    }

    private void appendTransmissions(StrainData strain,
                                     VaccineCorrectionProfile profile,
                                     List<ResolvedTarget> targets) {
        for (ResourceLocation transmission : BioForgeDefinitionManager.TRANSMISSIONS.ids().stream()
                .sorted().toList()) {
            InfectionType legacy = BioForgeIds.legacyTransmission(transmission);
            if (legacy != null && !BioForgeServerConfig.isTransmissionEnabled(legacy)) continue;
            VaccineCorrectionProfile.TargetKey key = targetKey(
                    VaccineCorrectionProfile.TargetFamily.TRANSMISSION,
                    BioForgeDefinitionManager.storageId(transmission));
            VaccineCorrectionProfile.TargetOverride override =
                    profile.targetOverride(key.family(), key.target());
            if (!override.enabled()) continue;
            int states = override.resolveStates(2);
            int expected = strain.getTransmissionIds().contains(transmission) ? 1 : 0;
            targets.add(new ResolvedTarget(
                    key, states, expected, override.resolveWeight(1.0F),
                    ValueKind.BOOLEAN, 0.0F, 1.0F));
        }
    }

    private void appendPathogen(StrainData strain, VaccineCorrectionProfile profile,
                                List<ResolvedTarget> targets) {
        VaccineCorrectionProfile.TargetKey key = targetKey(
                VaccineCorrectionProfile.TargetFamily.PATHOGEN, "pathogen");
        VaccineCorrectionProfile.TargetOverride override =
                profile.targetOverride(key.family(), key.target());
        if (!override.enabled()) return;
        List<ResourceLocation> pathogens = BioForgeDefinitionManager.PATHOGENS.ids().stream()
                .sorted().toList();
        int possiblePathogens = pathogens.size();
        if (possiblePathogens == 0) return;
        int states = override.resolveStates(possiblePathogens);
        int pathogenIndex = Math.max(0, pathogens.indexOf(strain.getPathogenId()));
        int expected = quantizeOrdinal(
                pathogenIndex, possiblePathogens, states);
        targets.add(new ResolvedTarget(
                key, states, expected, override.resolveWeight(1.0F),
                ValueKind.ENUM, 0.0F, possiblePathogens - 1.0F));
    }

    private void appendLifecycle(StrainData strain, VaccineCorrectionProfile profile,
                                 List<ResolvedTarget> targets) {
        VaccineCorrectionProfile.TargetKey key = targetKey(
                VaccineCorrectionProfile.TargetFamily.LIFECYCLE,
                "incubation_period");
        VaccineCorrectionProfile.TargetOverride override =
                profile.targetOverride(key.family(), key.target());
        if (!override.enabled()) return;
        int maximum = Math.max(0, profile.maximumIncubationTicks());
        int states = override.resolveStates(maximum + 1);
        int incubation = InfectionLifecycleRegistry.INSTANCE
                .resolve(strain.getLifecycleProfileId()).incubationTicks();
        float normalized = maximum <= 0 ? 0.0F
                : Math.max(0.0F, Math.min(1.0F, incubation / (float) maximum));
        int expected = Math.round(normalized * Math.max(0, states - 1));
        targets.add(new ResolvedTarget(key, states, expected,
                override.resolveWeight(1.0F), ValueKind.NUMBER,
                0.0F, maximum));
    }

    private static int expectedSymptomState(SymptomKey<?> key,
                                            SymptomDefinition definition, String raw,
                                            float[] range, int states) {
        if (key.getType() == Boolean.class) {
            return Boolean.parseBoolean(raw) ? Math.min(1, states - 1) : 0;
        }
        if (key.getType().isEnum()) {
            Object[] constants = key.getType().getEnumConstants();
            int ordinal = 0;
            for (int index = 0; index < constants.length; index++) {
                if (((Enum<?>) constants[index]).name().equalsIgnoreCase(raw)) {
                    ordinal = index;
                    break;
                }
            }
            return quantizeOrdinal(ordinal, constants.length, states);
        }
        if (definition != null
                && definition.valueType() == SymptomDefinition.ValueType.ENUM) {
            List<String> values = definition.allowedValues();
            int ordinal = 0;
            for (int index = 0; index < values.size(); index++) {
                if (values.get(index).equalsIgnoreCase(raw)) {
                    ordinal = index;
                    break;
                }
            }
            return quantizeOrdinal(ordinal, values.size(), states);
        }
        try {
            float value = Float.parseFloat(raw);
            float minimum = range == null ? 0.0F : range[0];
            float maximum = range == null ? 1.0F : range[1];
            float normalized;
            if (maximum <= minimum) {
                normalized = minimum >= 0.0F && maximum <= 1.0F
                        ? Math.max(0.0F, Math.min(1.0F, value))
                        : 0.0F;
            } else {
                normalized = Math.max(0.0F, Math.min(1.0F,
                        (value - minimum) / (maximum - minimum)));
            }
            return Math.round(normalized * (states - 1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static int quantizeOrdinal(int ordinal, int count, int states) {
        if (count <= 1 || states <= 1) return 0;
        float normalized = (float) ordinal / (count - 1);
        return Math.round(normalized * (states - 1));
    }

    private int initialState(ResolvedTarget target) {
        if (target.states() <= 1) return 0;
        int selected = Math.floorMod((strainFingerprint + '|'
                + target.key().serialized() + "|initial").hashCode(), target.states());
        if (selected == target.expectedState()) {
            selected = (selected + 1) % target.states();
        }
        return selected;
    }

    private static String fingerprint(StrainData strain) {
        return UUID.nameUUIDFromBytes(strain.toCanonicalGeneticPayload()
                        .getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private static VaccineCorrectionProfile.TargetKey targetKey(
            VaccineCorrectionProfile.TargetFamily family, String target) {
        return new VaccineCorrectionProfile.TargetKey(
                family, target.toLowerCase(Locale.ROOT));
    }

    public enum ValueKind {
        BOOLEAN,
        PERCENTAGE,
        NUMBER,
        ENUM
    }

    public record Target(
            VaccineCorrectionProfile.TargetFamily family,
            String id,
            int states,
            int selectedState,
            ValueKind valueKind,
            float displayMinimum,
            float displayMaximum
    ) {}

    private record ResolvedTarget(
            VaccineCorrectionProfile.TargetKey key,
            int states,
            int expectedState,
            float targetWeight,
            ValueKind valueKind,
            float displayMinimum,
            float displayMaximum
    ) {}
}
