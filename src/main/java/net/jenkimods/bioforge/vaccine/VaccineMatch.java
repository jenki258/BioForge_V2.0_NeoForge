package net.jenkimods.bioforge.vaccine;

import net.jenkimods.bioforge.infection.InfectionType;
import net.jenkimods.bioforge.infection.StrainData;
import net.minecraft.util.Mth;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;




public record VaccineMatch(
        boolean pathogenCompatible,
        float totalSimilarity,
        float pathogenSimilarity,
        float transmissionSimilarity,
        float symptomSimilarity,
        float mutationSimilarity
) {
    private static final float PATHOGEN_WEIGHT = 0.35f;
    private static final float TRANSMISSION_WEIGHT = 0.15f;
    private static final float SYMPTOM_WEIGHT = 0.30f;
    private static final float MUTATION_WEIGHT = 0.20f;

    public static VaccineMatch compare(StrainData template, StrainData infection) {
        if (template == null || infection == null) {
            return new VaccineMatch(false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        }

        boolean samePathogen = template.getPathogenId() != null
                && template.getPathogenId().equals(infection.getPathogenId());
        float pathogen = samePathogen ? 1.0f : 0.0f;
        float transmission = jaccard(template.getTransmissionIds(), infection.getTransmissionIds());
        float symptoms = compareSymptoms(template.getSymptoms(), infection.getSymptoms());
        float mutations = jaccard(template.getMutationIds(), infection.getMutationIds());
        float total = pathogen * PATHOGEN_WEIGHT
                + transmission * TRANSMISSION_WEIGHT
                + symptoms * SYMPTOM_WEIGHT
                + mutations * MUTATION_WEIGHT;
        return new VaccineMatch(samePathogen, Mth.clamp(total, 0.0f, 1.0f),
                pathogen, transmission, symptoms, mutations);
    }

    public boolean exact() {
        return pathogenCompatible
                && transmissionSimilarity >= 0.999f
                && symptomSimilarity >= 0.999f
                && mutationSimilarity >= 0.999f;
    }

    private static float compareSymptoms(Map<String, String> template, Map<String, String> infection) {
        Set<String> keys = new LinkedHashSet<>(template.keySet());
        keys.addAll(infection.keySet());
        if (keys.isEmpty()) return 1.0f;

        float total = 0.0f;
        for (String key : keys) {
            String left = template.get(key);
            String right = infection.get(key);
            if (left == null || right == null) continue;
            total += compareSymptomValue(key, left, right);
        }
        return Mth.clamp(total / keys.size(), 0.0f, 1.0f);
    }

    private static float compareSymptomValue(String key, String left, String right) {
        if (left.equalsIgnoreCase(right)) return 1.0f;
        try {
            float a = Float.parseFloat(left);
            float b = Float.parseFloat(right);
            if (!Float.isFinite(a) || !Float.isFinite(b)) return 0.0f;
            float scale = switch (key) {
                case "colony_radius" -> 40.0f;
                case "max_infested_blocks" -> 200.0f;
                default -> Math.max(1.0f, Math.max(Math.abs(a), Math.abs(b)));
            };
            return Mth.clamp(1.0f - Math.abs(a - b) / scale, 0.0f, 1.0f);
        } catch (NumberFormatException ignored) {
            return 0.0f;
        }
    }

    private static float jaccard(Set<?> first, Set<?> second) {
        if (first.isEmpty() && second.isEmpty()) return 1.0f;
        Set<Object> union = new HashSet<>(first);
        union.addAll(second);
        Set<Object> intersection = new HashSet<>(first);
        intersection.retainAll(second);
        return union.isEmpty() ? 1.0f : (float) intersection.size() / (float) union.size();
    }
}
