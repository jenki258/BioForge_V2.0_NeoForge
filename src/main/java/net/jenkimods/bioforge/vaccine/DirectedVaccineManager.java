package net.jenkimods.bioforge.vaccine;

import net.jenkimods.bioforge.crispr.BioForgeResearchData;
import net.jenkimods.bioforge.crispr.VaccineTargetCategory;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.jenkimods.bioforge.api.definition.SymptomDefinition;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.infection.lifecycle.InfectionLifecycleRegistry;
import net.jenkimods.bioforge.infection.symptoms.SymptomKey;
import net.jenkimods.bioforge.mutation.MutationDefinition;
import net.jenkimods.bioforge.mutation.MutationLoader;
import net.jenkimods.bioforge.mutation.MutationManager;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;


public final class DirectedVaccineManager {
    public enum Outcome {
        APPLIED,
        RESISTED,
        MISMATCH,
        NO_TARGET,
        NO_INFECTION,
        INVALID_VACCINE
    }

    public record AttemptResult(
            Outcome outcome,
            VaccineMatch match,
            float successChance,
            boolean defenseMutationApplied
    ) {
        public boolean consumesDose() {
            return outcome == Outcome.APPLIED || outcome == Outcome.RESISTED
                    || outcome == Outcome.MISMATCH || outcome == Outcome.NO_TARGET;
        }
    }

    private DirectedVaccineManager() {}

    public static AttemptResult attempt(LivingEntity target, DirectedVaccineProfile profile) {
        return attempt(target, profile, null);
    }

    public static AttemptResult attempt(LivingEntity target, DirectedVaccineProfile profile,
                                        VaccineHostProfile hostProfile) {
        if (profile == null || !profile.isValid()) {
            return empty(Outcome.INVALID_VACCINE);
        }
        DirectedVaccineAction action =
                BioForgeResearchData.action(profile.actionId()).orElse(null);
        if (action == null || !action.supports(profile.category())) {
            return empty(Outcome.INVALID_VACCINE);
        }
        InfectionData infection = InfectionCapability.get(target);
        if (infection == null || !infection.isInfected() || infection.getPathogenId() == null) {
            return empty(Outcome.NO_INFECTION);
        }

        StrainData source = profile.strain();
        VaccineMatch match = VaccineMatch.compare(source, StrainData.buildFrom(infection));
        float strength = VaccineManager.sanitizeStrength(
                infection.getSymptom(BioForgeSymptoms.INFECTION_STRENGTH));
        boolean mismatch = (action.requireSamePathogen() && !match.pathogenCompatible())
                || match.totalSimilarity() < action.minimumSimilarity();
        float resistance = 1.0f / (1.0f
                + VaccineManager.getRules().strengthResistance() * Math.max(0.0f, strength));
        float chance = mismatch ? 0.0f : Mth.clamp(
                action.baseSuccessChance()
                        * (0.25f + 0.75f * profile.quality())
                        * (0.65f + 0.35f * match.totalSimilarity())
                        * resistance
                        * (1.0F - InfectionLifecycleRegistry.INSTANCE
                        .resolve(infection.getLifecycle().profileId()).cureResistance()),
                0.0f, 1.0f);
        if (!VaccineManager.meetsRhRequirements(infection, hostProfile)) {
            chance = 0.0f;
        } else {
            chance = Mth.clamp(chance * VaccineManager.hostCompatibilityMultiplier(
                    target, hostProfile, VaccineManager.getRules()), 0.0f, 1.0f);
        }

        if (chance > 0.0f && target.getRandom().nextFloat() < chance) {
            boolean changed = applyAction(target, infection, source, profile, action);
            if (changed) {
                VaccineManager.persistAndSync(target, infection);
                return new AttemptResult(Outcome.APPLIED, match, chance, false);
            }
            return new AttemptResult(Outcome.NO_TARGET, match, chance, false);
        }

        boolean defense = VaccineManager.maybeApplyDefenseMutation(
                target, infection, profile.defenseMutationChance(), match, strength,
                VaccineManager.getRules());
        if (defense) VaccineManager.persistAndSync(target, infection);
        return new AttemptResult(mismatch ? Outcome.MISMATCH : Outcome.RESISTED,
                match, chance, defense);
    }

    private static boolean applyAction(LivingEntity target, InfectionData infection,
                                       StrainData source, DirectedVaccineProfile profile,
                                       DirectedVaccineAction action) {
        String editTarget = action.targetOverride().isBlank()
                ? profile.target() : action.targetOverride();
        return switch (profile.category()) {
            case MUTATION -> applyMutation(target, infection, editTarget, action.operation());
            case TRANSMISSION ->
                    applyTransmission(infection, editTarget, action.operation());
            case SYMPTOM ->
                    applySymptom(infection, source, editTarget, action.operation(),
                            action.potency(), action.neutralEpsilon(),
                            action.valueOverride(), action.neutralValueOverride());
        };
    }

    private static boolean applyMutation(LivingEntity target, InfectionData infection,
                                         String mutationId, String operation) {
        boolean present = MutationManager.hasMutation(infection, mutationId);
        boolean shouldAdd = switch (operation) {
            case "add", "set", "increase", "replace" -> true;
            case "remove", "reduce", "move_toward_neutral" -> false;
            case "toggle", "auto_opposite" -> !present;
            default -> false;
        };
        if (shouldAdd == present) return false;
        if (!shouldAdd) return MutationManager.removeMutation(infection, target, mutationId);
        MutationDefinition definition = MutationLoader.INSTANCE.getMutation(mutationId).orElse(null);
        if (definition == null) return false;
        return MutationManager.applyMutation(definition, infection, target, true)
                == MutationManager.ApplyResult.APPLIED;
    }

    private static boolean applyTransmission(InfectionData infection, String target,
                                             String operation) {
        ResourceLocation type;
        try {
            type = BioForgeIds.parse(target);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        type = BioForgeDefinitionManager.TRANSMISSIONS.canonicalId(type);
        if (BioForgeDefinitionManager.transmission(type).isEmpty()) return false;
        boolean present = infection.getTransmissionIds().contains(type);
        boolean shouldAdd = switch (operation) {
            case "add", "set", "increase", "replace" -> true;
            case "remove", "reduce", "move_toward_neutral" -> false;
            case "toggle", "auto_opposite" -> !present;
            default -> false;
        };
        if (shouldAdd == present) return false;
        if (shouldAdd) infection.addTransmissionId(type);
        else infection.removeTransmissionId(type);
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean applySymptom(InfectionData infection, StrainData source, String target,
                                        String operation, float potency, float epsilon,
                                        String valueOverride, String neutralOverride) {
        SymptomKey key = BioForgeSymptoms.getAllSymptomKeys().get(target);
        if (key == null) return false;
        SymptomDefinition definition = BioForgeDefinitionManager
                .symptom(BioForgeDefinitionManager.symptomId(target)).orElse(null);
        Object current = infection.getSymptom(key);
        Object neutral = key.getDefaultValue();
        Object sourceValue = source.getSymptom(target)
                .map(value -> parseValue(value, key.getType())).orElse(null);
        if (!valueOverride.isBlank()) {
            Object parsed = parseValue(valueOverride, key.getType());
            if (parsed != null) sourceValue = parsed;
        }
        if (!neutralOverride.isBlank()) {
            Object parsed = parseValue(neutralOverride, key.getType());
            if (parsed != null) neutral = parsed;
        }

        Object result;
        if (key.getType() == Boolean.class) {
            boolean now = (Boolean) current;
            boolean normal = (Boolean) neutral;
            boolean sample = sourceValue instanceof Boolean b ? b : !normal;
            result = switch (operation) {
                case "add", "increase", "set", "replace" -> sample;
                case "remove", "reduce", "move_toward_neutral" -> normal;
                case "toggle" -> !now;
                case "auto_opposite" -> now != normal ? normal : sample;
                default -> now;
            };
        } else if (key.getType().isEnum() || key.getType() == String.class) {
            result = switch (operation) {
                case "remove", "reduce", "move_toward_neutral" -> neutral;
                case "add", "increase", "set", "replace" ->
                        sourceValue == null ? current : sourceValue;
                case "toggle", "auto_opposite" ->
                        !current.equals(neutral) ? neutral
                                : sourceValue == null ? current : sourceValue;
                default -> current;
            };
        } else if (current instanceof Number number && neutral instanceof Number neutralNumber) {
            float now = number.floatValue();
            float normal = neutralNumber.floatValue();
            float sample = sourceValue instanceof Number sourceNumber
                    ? sourceNumber.floatValue() : now + potency;
            float resultNumber = switch (operation) {
                case "add", "increase" -> now + potency;
                case "remove", "reduce" -> now - potency;
                case "set", "replace" -> sample;
                case "move_toward_neutral" -> moveToward(now, normal, potency);
                case "toggle", "auto_opposite" -> Math.abs(now - normal) > epsilon
                        ? moveToward(now, normal, potency)
                        : moveToward(now, sample, potency);
                default -> now;
            };
            result = key.getType() == Integer.class
                    ? Math.round(resultNumber) : resultNumber;
        } else {
            return false;
        }
        if (definition != null && definition.valueType() == SymptomDefinition.ValueType.ENUM
                && result instanceof String selected
                && definition.allowedValues().stream().noneMatch(selected::equalsIgnoreCase)) {
            return false;
        }
        if (result == null || result.equals(current)) return false;
        infection.setSymptom(key, result);
        return true;
    }

    private static float moveToward(float value, float target, float amount) {
        if (amount <= 0.0f) return target;
        if (value < target) return Math.min(target, value + amount);
        return Math.max(target, value - amount);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parseValue(String value, Class<?> type) {
        try {
            if (type == Boolean.class) return Boolean.valueOf(value);
            if (type == Float.class) return Float.valueOf(value);
            if (type == Integer.class) return Integer.valueOf(value);
            if (type == String.class) return value;
            if (type.isEnum()) return Enum.valueOf((Class<Enum>) type, value);
        } catch (RuntimeException ignored) {}
        return null;
    }

    private static AttemptResult empty(Outcome outcome) {
        return new AttemptResult(outcome,
                new VaccineMatch(false, 0, 0, 0, 0, 0), 0.0f, false);
    }
}
