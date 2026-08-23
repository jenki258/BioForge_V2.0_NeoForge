package net.jenkimods.bioforge.api.infection;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record NaturalStrainDefinition(
        ResourceLocation id,
        ResourceLocation pathogen,
        Set<ResourceLocation> transmissions,
        Map<String, String> symptoms,
        Set<String> mutations,
        List<RareMutation> rareMutations,
        ResourceLocation lifecycleProfile) {

    public NaturalStrainDefinition {
        if (id == null || pathogen == null) throw new IllegalArgumentException("Natural strain ids cannot be null");
        transmissions = transmissions == null ? Set.of() : Set.copyOf(transmissions);
        symptoms = symptoms == null ? Map.of() : Map.copyOf(symptoms);
        mutations = mutations == null ? Set.of() : Set.copyOf(mutations);
        rareMutations = rareMutations == null ? List.of() : List.copyOf(rareMutations);
        lifecycleProfile = lifecycleProfile == null
                ? ResourceLocation.tryBuild("bioforge", "default") : lifecycleProfile;
    }

    public record RareMutation(String mutation, float chance) {
        public RareMutation {
            mutation = mutation == null ? "" : mutation.trim();
            chance = Math.max(0.0F, Math.min(1.0F, chance));
        }
    }
}
