package net.jenkimods.bioforge.api.infection;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record NaturalInfectionRule(ResourceLocation id, List<String> entities,
                                   float chance, List<WeightedStrain> strains) {
    public NaturalInfectionRule {
        if (id == null) throw new IllegalArgumentException("Natural infection rule id cannot be null");
        entities = entities == null ? List.of() : List.copyOf(entities);
        chance = Math.max(0.0F, Math.min(1.0F, chance));
        strains = strains == null ? List.of() : List.copyOf(strains);
    }

    public record WeightedStrain(ResourceLocation strain, int weight) {
        public WeightedStrain {
            if (strain == null) throw new IllegalArgumentException("Natural strain reference cannot be null");
            weight = Math.max(1, weight);
        }
    }
}
