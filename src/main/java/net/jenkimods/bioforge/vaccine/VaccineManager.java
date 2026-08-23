package net.jenkimods.bioforge.vaccine;

import net.jenkimods.bioforge.blood.BloodCapability;
import net.jenkimods.bioforge.blood.BloodData;
import net.jenkimods.bioforge.blood.BloodType;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.InfectionEventHandler;
import net.jenkimods.bioforge.infection.InfectionStore;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.infection.lifecycle.InfectionLifecycleRegistry;
import net.jenkimods.bioforge.mutation.MutationDefinition;
import net.jenkimods.bioforge.mutation.MutationLoader;
import net.jenkimods.bioforge.mutation.MutationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;




public final class VaccineManager {
    public static final String VACCINE_DEFENSE_MUTATION_TAG = "vaccine_defense";
    public static final String IMMUNE_ESCAPE_MUTATION_TAG = "immune_escape";
    public static final String VACCINE_VULNERABILITY_MUTATION_TAG = "vaccine_vulnerability";
    public static final String REQUIRES_RH_POSITIVE_TAG = "vaccine_requires_rh_positive";
    public static final String REQUIRES_RH_NEGATIVE_TAG = "vaccine_requires_rh_negative";
    private static final List<String> DEFENSE_TAGS =
            List.of(VACCINE_DEFENSE_MUTATION_TAG, IMMUNE_ESCAPE_MUTATION_TAG);

    public enum Outcome {
        CURED,
        IMMUNIZED,
        RESISTED,
        MISMATCH,
        NO_INFECTION,
        INVALID_VACCINE
    }

    public record AttemptResult(
            Outcome outcome,
            VaccineMatch match,
            float cureChance,
            float infectionStrength,
            boolean defenseMutationApplied
    ) {
        public boolean consumesDose() {
            return outcome == Outcome.CURED || outcome == Outcome.IMMUNIZED
                    || outcome == Outcome.RESISTED || outcome == Outcome.MISMATCH;
        }
    }

    private static volatile VaccineRules overrideRules;

    private VaccineManager() {}

    public static VaccineRules getRules() {
        VaccineRules override = overrideRules;
        return override == null ? BioForgeServerConfig.vaccineRules() : override;
    }

    public static void setRules(VaccineRules replacement) {
        overrideRules = Objects.requireNonNull(replacement, "replacement");
    }

    public static void clearRulesOverride() {
        overrideRules = null;
    }

    public static AttemptResult attemptVaccination(LivingEntity target, @Nullable VaccineProfile profile) {
        return attemptVaccination(target, profile, null);
    }

    public static AttemptResult attemptVaccination(LivingEntity target,
                                                   @Nullable VaccineProfile profile,
                                                   @Nullable VaccineHostProfile hostProfile) {
        if (profile == null || !profile.isValid()) {
            return emptyResult(Outcome.INVALID_VACCINE);
        }
        InfectionData infection = InfectionCapability.get(target);
        if (infection == null) {
            return emptyResult(Outcome.NO_INFECTION);
        }

        StrainData vaccineStrain = profile.strain();
        if (!infection.isInfected() || infection.getPathogenId() == null) {
            StrainImmunityManager.grant(target, infection, vaccineStrain, profile.quality());
            persistAndSync(target, infection);
            return emptyResult(Outcome.IMMUNIZED);
        }
        StrainData liveStrain = StrainData.buildFrom(infection);
        VaccineMatch match = VaccineMatch.compare(vaccineStrain, liveStrain);
        float strength = sanitizeStrength(infection.getSymptom(BioForgeSymptoms.INFECTION_STRENGTH));
        VaccineRules activeRules = getRules();
        float chance = calculateCureChance(profile, infection, match, strength, activeRules);
        if (!meetsRhRequirements(infection, hostProfile)) {
            chance = 0.0f;
        } else {
            chance = Mth.clamp(chance * hostCompatibilityMultiplier(
                    target, hostProfile, activeRules), 0.0f,
                    activeRules.maximumCureChance());
        }

        if (chance > 0.0f && target.getRandom().nextFloat() < chance) {
            cure(target, infection);
            StrainImmunityManager.grant(target, infection, vaccineStrain, profile.quality());
            persistAndSync(target, infection);
            return new AttemptResult(Outcome.CURED, match, chance, strength, false);
        }

        boolean defenseApplied = maybeApplyDefenseMutation(
                target, infection, profile.defenseMutationChance(), match, strength, activeRules);
        if (defenseApplied) persistAndSync(target, infection);
        Outcome outcome = !match.pathogenCompatible()
                || match.totalSimilarity() < activeRules.minimumSimilarity()
                ? Outcome.MISMATCH : Outcome.RESISTED;
        return new AttemptResult(outcome, match, chance, strength, defenseApplied);
    }

    public static float hostCompatibilityMultiplier(LivingEntity target,
                                                    @Nullable VaccineHostProfile hostProfile,
                                                    VaccineRules activeRules) {
        BloodData targetBlood = BloodCapability.get(target);
        BloodType targetType = targetBlood == null || !targetBlood.isInitialized()
                ? null : targetBlood.getBloodType();
        BloodType vaccineType = hostProfile == null || !hostProfile.bloodVerified()
                ? null : hostProfile.bloodType();
        if (targetType == null || vaccineType == null) {
            return activeRules.unknownHostMultiplier();
        }
        if (targetType == vaccineType) return activeRules.exactBloodTypeMultiplier();
        if (targetType.getCategory() != vaccineType.getCategory()) {
            return activeRules.rhMismatchMultiplier();
        }
        if (targetType.getCategory() == BloodType.Category.HUMAN
                && targetType.isRhPositive() == vaccineType.isRhPositive()) {
            return activeRules.sameRhMultiplier();
        }
        return activeRules.rhMismatchMultiplier();
    }

    public static boolean meetsRhRequirements(InfectionData infection,
                                              @Nullable VaccineHostProfile hostProfile) {
        boolean requiresPositive = false;
        boolean requiresNegative = false;
        for (String mutationId : infection.getSymptoms().getMutations()) {
            MutationDefinition definition =
                    MutationLoader.INSTANCE.getMutation(mutationId).orElse(null);
            if (definition == null) continue;
            requiresPositive |= definition.tags().contains(REQUIRES_RH_POSITIVE_TAG);
            requiresNegative |= definition.tags().contains(REQUIRES_RH_NEGATIVE_TAG);
        }
        if (!requiresPositive && !requiresNegative) return true;
        BloodType vaccineType = hostProfile == null || !hostProfile.bloodVerified()
                ? null : hostProfile.bloodType();
        if (vaccineType == null || vaccineType.getCategory() != BloodType.Category.HUMAN) {
            return false;
        }
        if (requiresPositive && !vaccineType.isRhPositive()) return false;
        return !requiresNegative || vaccineType.isRhNegative();
    }

    public static float calculateCureChance(VaccineProfile profile, InfectionData infection,
                                            VaccineMatch match, float infectionStrength,
                                            VaccineRules activeRules) {
        if (!match.pathogenCompatible()
                || match.totalSimilarity() < activeRules.minimumSimilarity()) {
            return 0.0f;
        }

        float curveRange = 1.0f - activeRules.similarityCurveFloor();
        float normalizedSimilarity = Mth.clamp(
                (match.totalSimilarity() - activeRules.similarityCurveFloor()) / curveRange,
                0.0f, 1.0f);
        float similarityFactor = normalizedSimilarity * normalizedSimilarity;
        float qualityFactor = 0.25f + 0.75f * profile.quality();
        float resistanceFactor =
                1.0f / (1.0f + activeRules.strengthResistance() * Math.max(0.0f, infectionStrength));

        float chance = activeRules.basePotency()
                * similarityFactor
                * qualityFactor
                * resistanceFactor
                * (1.0F - InfectionLifecycleRegistry.INSTANCE
                .resolve(infection.getLifecycle().profileId()).cureResistance());
        if (hasDefenseMutation(infection, activeRules.defenseMutationId())) {
            chance *= activeRules.defenseMutationCureMultiplier();
        }
        if (hasMutationTag(infection, VACCINE_VULNERABILITY_MUTATION_TAG)) {
            chance *= 1.35F;
        }
        return Mth.clamp(chance, 0.0f, activeRules.maximumCureChance());
    }

    private static boolean hasMutationTag(InfectionData infection, String tag) {
        for (MutationDefinition definition : MutationLoader.INSTANCE.getMutationsWithTag(tag)) {
            if (MutationManager.hasMutation(infection, definition.id())) return true;
        }
        return false;
    }

    public static boolean maybeApplyDefenseMutation(LivingEntity target, InfectionData infection,
                                                    float baseRisk, VaccineMatch match,
                                                    float strength, VaccineRules activeRules) {
        if (hasDefenseMutation(infection, activeRules.defenseMutationId())) return false;

        float strengthFactor = 0.5f
                + Math.min(1.0f, strength) * activeRules.defenseRiskStrengthScale();
        float mismatchFactor = 1.0f
                + (1.0f - match.totalSimilarity()) * activeRules.defenseRiskMismatchScale();
        float chance = Mth.clamp(
                baseRisk * strengthFactor * mismatchFactor,
                0.0f, 1.0f);
        if (chance <= 0.0f || target.getRandom().nextFloat() >= chance) return false;

        MutationDefinition defense = MutationLoader.INSTANCE
                .getMutation(activeRules.defenseMutationId()).orElse(null);
        if (defense != null) {
            MutationManager.ApplyResult result =
                    MutationManager.applyMutation(defense, infection, target, false);
            if (result == MutationManager.ApplyResult.APPLIED) return true;
        }




        LinkedHashSet<MutationDefinition> taggedDefinitions = new LinkedHashSet<>();
        for (String tag : DEFENSE_TAGS) {
            taggedDefinitions.addAll(MutationLoader.INSTANCE.getMutationsWithTag(tag));
        }
        for (MutationDefinition tagged : taggedDefinitions) {
            if (tagged == defense) continue;
            MutationManager.ApplyResult result =
                    MutationManager.applyMutation(tagged, infection, target, false);
            if (result == MutationManager.ApplyResult.APPLIED) return true;
        }
        return false;
    }

    private static boolean hasDefenseMutation(InfectionData infection, String configuredId) {
        if (MutationManager.hasMutation(infection, configuredId)) return true;
        for (String tag : DEFENSE_TAGS) {
            for (MutationDefinition tagged : MutationLoader.INSTANCE.getMutationsWithTag(tag)) {
                if (MutationManager.hasMutation(infection, tagged.id())) return true;
            }
        }
        int separator = configuredId.indexOf(':');
        return separator >= 0 && separator + 1 < configuredId.length()
                && infection.getSymptoms().hasMutation(configuredId.substring(separator + 1));
    }

    private static void cure(LivingEntity target, InfectionData infection) {
        MutationManager.clearMutations(infection, target);
        infection.clearInfection();
        if (target instanceof ServerPlayer player) {
            InfectionStore.get(player.serverLevel()).clearInfection(player.getUUID());
            InfectionEventHandler.syncToClient(player, infection);
        }
    }

    public static void persistAndSync(LivingEntity target, InfectionData infection) {
        if (!(target instanceof ServerPlayer player)) return;
        InfectionStore store = InfectionStore.get(player.serverLevel());
        InfectionStore.InfectionRecord existing = store.getInfection(player.getUUID());
        if (existing != null && existing.persistent() && infection.isInfected()) {
            Map<String, Object> symptoms = new LinkedHashMap<>();
            BioForgeSymptoms.getEnabledSymptomKeys().forEach((id, key) ->
                    symptoms.put(id, infection.getSymptoms().get(key)));
            store.setInfection(player.getUUID(), new InfectionStore.InfectionRecord(
                    true, true, infection.getPathogenType(),
                    new ArrayList<>(infection.getInfectionTypes()), symptoms,
                    new ArrayList<>(infection.getSymptoms().getMutations()),
                    infection.getPathogenId(), new ArrayList<>(infection.getTransmissionIds())));
        }
        InfectionEventHandler.syncToClient(player, infection);
    }

    public static float sanitizeStrength(@Nullable Float strength) {
        if (strength == null || !Float.isFinite(strength)) return 0.5f;
        return Math.max(0.0f, strength);
    }

    private static AttemptResult emptyResult(Outcome outcome) {
        return new AttemptResult(outcome,
                new VaccineMatch(false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
                0.0f, 0.0f, false);
    }
}
