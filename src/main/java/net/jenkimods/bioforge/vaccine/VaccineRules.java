package net.jenkimods.bioforge.vaccine;

import net.minecraft.util.Mth;






public record VaccineRules(
        float minimumSimilarity,
        float similarityCurveFloor,
        float basePotency,
        float maximumCureChance,
        float strengthResistance,
        float defenseMutationCureMultiplier,
        float defenseRiskStrengthScale,
        float defenseRiskMismatchScale,
        float exactBloodTypeMultiplier,
        float sameRhMultiplier,
        float rhMismatchMultiplier,
        float unknownHostMultiplier,
        String defenseMutationId
) {
    public static final String DEFAULT_DEFENSE_MUTATION_ID = "vaccine_defense";

    public VaccineRules {
        minimumSimilarity = probability(minimumSimilarity);
        similarityCurveFloor = probability(similarityCurveFloor);
        similarityCurveFloor = Math.min(similarityCurveFloor, minimumSimilarity);
        if (similarityCurveFloor >= 1.0f) similarityCurveFloor = 0.999f;
        basePotency = Math.max(0.0f, finite(basePotency, 1.00f));
        maximumCureChance = probability(maximumCureChance);
        strengthResistance = Math.max(0.0f, finite(strengthResistance, 1.80f));
        defenseMutationCureMultiplier = probability(defenseMutationCureMultiplier);
        defenseRiskStrengthScale = Math.max(0.0f, finite(defenseRiskStrengthScale, 0.75f));
        defenseRiskMismatchScale = Math.max(0.0f, finite(defenseRiskMismatchScale, 0.5f));
        exactBloodTypeMultiplier = Math.max(0.0f, finite(exactBloodTypeMultiplier, 1.10f));
        sameRhMultiplier = Math.max(0.0f, finite(sameRhMultiplier, 0.95f));
        rhMismatchMultiplier = Math.max(0.0f, finite(rhMismatchMultiplier, 0.65f));
        unknownHostMultiplier = Math.max(0.0f, finite(unknownHostMultiplier, 0.85f));
        if (defenseMutationId == null || defenseMutationId.isBlank()) {
            defenseMutationId = DEFAULT_DEFENSE_MUTATION_ID;
        }
    }

    public static VaccineRules defaults() {
        return new VaccineRules(
                0.55f,
                0.40f,
                1.00f,
                0.88f,
                1.80f,
                0.40f,
                0.65f,
                0.40f,
                1.10f,
                0.95f,
                0.65f,
                0.85f,
                DEFAULT_DEFENSE_MUTATION_ID
        );
    }

    private static float probability(float value) {
        return Mth.clamp(finite(value, 0.0f), 0.0f, 1.0f);
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }
}
