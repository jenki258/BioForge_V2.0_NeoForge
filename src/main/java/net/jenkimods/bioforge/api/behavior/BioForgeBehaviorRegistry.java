package net.jenkimods.bioforge.api.behavior;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class BioForgeBehaviorRegistry {
    private static final Map<ResourceLocation, TransmissionBehavior> TRANSMISSIONS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, SymptomBehavior> SYMPTOMS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, MutationEffectHandler> MUTATION_EFFECTS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, VaccineMakerOperationHandler> VACCINE_OPERATIONS = new LinkedHashMap<>();
    private static boolean frozen;

    private BioForgeBehaviorRegistry() {}

    public static synchronized void registerTransmission(ResourceLocation id, TransmissionBehavior behavior) {
        if (frozen) throw new IllegalStateException("BioForge behavior registry is frozen");
        if (TRANSMISSIONS.putIfAbsent(id, behavior) != null) {
            throw new IllegalArgumentException("Duplicate transmission behavior " + id);
        }
    }

    public static synchronized void registerSymptom(ResourceLocation id, SymptomBehavior behavior) {
        if (frozen) throw new IllegalStateException("BioForge behavior registry is frozen");
        if (SYMPTOMS.putIfAbsent(id, behavior) != null) {
            throw new IllegalArgumentException("Duplicate symptom behavior " + id);
        }
    }

    public static Optional<TransmissionBehavior> transmission(ResourceLocation id) {
        return Optional.ofNullable(TRANSMISSIONS.get(id));
    }

    public static Optional<SymptomBehavior> symptom(ResourceLocation id) {
        return Optional.ofNullable(SYMPTOMS.get(id));
    }

    public static synchronized void registerMutationEffect(ResourceLocation id, MutationEffectHandler handler) {
        if (frozen) throw new IllegalStateException("BioForge behavior registry is frozen");
        if (MUTATION_EFFECTS.putIfAbsent(id, handler) != null) {
            throw new IllegalArgumentException("Duplicate mutation effect handler " + id);
        }
    }

    public static Optional<MutationEffectHandler> mutationEffect(ResourceLocation id) {
        return Optional.ofNullable(MUTATION_EFFECTS.get(id));
    }

    public static synchronized void registerVaccineOperation(ResourceLocation id,
                                                             VaccineMakerOperationHandler handler) {
        if (frozen) throw new IllegalStateException("BioForge behavior registry is frozen");
        if (VACCINE_OPERATIONS.putIfAbsent(id, handler) != null) {
            throw new IllegalArgumentException("Duplicate Vaccine Maker operation " + id);
        }
    }

    public static Optional<VaccineMakerOperationHandler> vaccineOperation(ResourceLocation id) {
        return Optional.ofNullable(VACCINE_OPERATIONS.get(id));
    }

    public static Set<ResourceLocation> transmissionIds() { return Set.copyOf(TRANSMISSIONS.keySet()); }
    public static Set<ResourceLocation> symptomIds() { return Set.copyOf(SYMPTOMS.keySet()); }
    public static Set<ResourceLocation> mutationEffectIds() { return Set.copyOf(MUTATION_EFFECTS.keySet()); }
    public static Set<ResourceLocation> vaccineOperationIds() { return Set.copyOf(VACCINE_OPERATIONS.keySet()); }
    public static synchronized void freeze() { frozen = true; }
}
