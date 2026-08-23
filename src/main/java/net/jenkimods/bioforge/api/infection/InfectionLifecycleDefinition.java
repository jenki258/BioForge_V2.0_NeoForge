package net.jenkimods.bioforge.api.infection;

import net.minecraft.resources.ResourceLocation;

public record InfectionLifecycleDefinition(
        ResourceLocation id,
        int incubationTicks,
        float adaptationSpeed,
        float hostileClimateIncubationRate,
        float adaptationPointsPerSecond,
        float hotAdaptationThreshold,
        float coldAdaptationThreshold,
        String hotAdaptationMutation,
        String coldAdaptationMutation,
        String dualAdaptationMutation,
        int lifespanTicks,
        float infectivity,
        float cureResistance,
        boolean contagiousDuringIncubation) {

    public InfectionLifecycleDefinition {
        if (id == null) throw new IllegalArgumentException("Lifecycle id cannot be null");
        incubationTicks = Math.max(0, incubationTicks);
        adaptationSpeed = Math.max(0.0F, adaptationSpeed);
        hostileClimateIncubationRate = clamp(hostileClimateIncubationRate, 0.01F, 1.0F);
        adaptationPointsPerSecond = Math.max(0.0F, adaptationPointsPerSecond);
        hotAdaptationThreshold = Math.max(1.0F, hotAdaptationThreshold);
        coldAdaptationThreshold = Math.max(1.0F, coldAdaptationThreshold);
        lifespanTicks = Math.max(-1, lifespanTicks);
        infectivity = Math.max(0.0F, infectivity);
        cureResistance = clamp(cureResistance, 0.0F, 0.95F);
        hotAdaptationMutation = normalize(hotAdaptationMutation);
        coldAdaptationMutation = normalize(coldAdaptationMutation);
        dualAdaptationMutation = normalize(dualAdaptationMutation);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
