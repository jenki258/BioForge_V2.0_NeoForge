package net.jenkimods.bioforge.infection;

import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.jenkimods.bioforge.infection.symptoms.SymptomKey;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class InfectionClientCache {
    private InfectionClientCache() {}

    private static final AtomicBoolean infected = new AtomicBoolean(false);
    private static final AtomicReference<PathogenType> pathogenType = new AtomicReference<>(null);
    private static final AtomicReference<ResourceLocation> pathogenId = new AtomicReference<>(null);
    private static final AtomicReference<List<InfectionType>> infectionTypes = new AtomicReference<>(List.of());
    private static final AtomicReference<List<ResourceLocation>> transmissionIds = new AtomicReference<>(List.of());
    private static final AtomicReference<Map<String, Object>> symptomMap = new AtomicReference<>(Map.of());
    private static final AtomicReference<Set<String>> mutations = new AtomicReference<>(Set.of());
    private static final AtomicReference<List<StrainImmunity>> immunities =
            new AtomicReference<>(List.of());

    public static void set(boolean isInfected, PathogenType pathogen, List<InfectionType> types,
                           Map<String, Object> symptoms, Collection<String> mutationIds,
                           Collection<StrainImmunity> strainImmunities) {
        infected.set(isInfected);
        pathogenType.set(pathogen);
        infectionTypes.set(Collections.unmodifiableList(new ArrayList<>(types)));
        symptomMap.set(Collections.unmodifiableMap(new LinkedHashMap<>(symptoms)));
        mutations.set(Collections.unmodifiableSet(new LinkedHashSet<>(mutationIds)));
        immunities.set(List.copyOf(strainImmunities));
        pathogenId.set(pathogen == null ? null : BioForgeIds.pathogen(pathogen));
        transmissionIds.set(types.stream().map(BioForgeIds::transmission).toList());
    }

    public static void set(boolean isInfected, ResourceLocation pathogen,
                           List<ResourceLocation> types, Map<String, Object> symptoms,
                           Collection<String> mutationIds,
                           Collection<StrainImmunity> strainImmunities) {
        infected.set(isInfected);
        pathogenId.set(pathogen);
        PathogenType legacy = BioForgeIds.legacyPathogen(pathogen);
        pathogenType.set(pathogen == null ? null : legacy == null ? PathogenType.UNIVERSAL : legacy);
        transmissionIds.set(List.copyOf(types));
        List<InfectionType> legacyTypes = types.stream().map(BioForgeIds::legacyTransmission)
                .filter(Objects::nonNull).toList();
        infectionTypes.set(List.copyOf(legacyTypes));
        symptomMap.set(Collections.unmodifiableMap(new LinkedHashMap<>(symptoms)));
        mutations.set(Collections.unmodifiableSet(new LinkedHashSet<>(mutationIds)));
        immunities.set(List.copyOf(strainImmunities));
    }

    public static boolean isInfected() { return infected.get(); }
    public static PathogenType getPathogenType() { return pathogenType.get(); }
    public static ResourceLocation getPathogenId() { return pathogenId.get(); }
    public static List<InfectionType> getInfectionTypes() { return infectionTypes.get(); }
    public static List<ResourceLocation> getTransmissionIds() { return transmissionIds.get(); }
    public static Set<String> getMutations() { return mutations.get(); }
    public static boolean hasMutation(String mutationId) { return mutations.get().contains(mutationId); }
    public static List<StrainImmunity> getStrainImmunities() { return immunities.get(); }

    @SuppressWarnings("unchecked")
    public static <T> T getSymptom(SymptomKey<T> key) {
        Object value = symptomMap.get().get(key.getId());
        if (value != null && key.getType().isInstance(value)) {
            return (T) value;
        }
        return key.getDefaultValue();
    }
}
