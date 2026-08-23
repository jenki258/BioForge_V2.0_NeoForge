package net.jenkimods.bioforge.infection;

import net.jenkimods.bioforge.infection.symptoms.EntitySymptoms;
import net.jenkimods.bioforge.infection.symptoms.SymptomKey;
import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.jenkimods.bioforge.infection.lifecycle.InfectionLifecycleRegistry;
import net.jenkimods.bioforge.infection.lifecycle.InfectionLifecycleState;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.Collection;
import java.util.Set;

public interface InfectionData {
    boolean isInfected();
    @Nullable PathogenType getPathogenType();
    Set<InfectionType> getInfectionTypes();

    default @Nullable ResourceLocation getPathogenId() {
        PathogenType legacy = getPathogenType();
        return legacy == null ? null : BioForgeIds.pathogen(legacy);
    }

    default Set<ResourceLocation> getTransmissionIds() {
        Set<ResourceLocation> ids = new java.util.LinkedHashSet<>();
        for (InfectionType type : getInfectionTypes()) ids.add(BioForgeIds.transmission(type));
        return java.util.Collections.unmodifiableSet(ids);
    }

    void setInfected(boolean infected);
    void setPathogenType(@Nullable PathogenType pathogenType);
    void addInfectionType(InfectionType type);
    void removeInfectionType(InfectionType type);

    default void setPathogenId(@Nullable ResourceLocation pathogenId) {
        if (pathogenId == null) {
            setPathogenType(null);
            return;
        }
        PathogenType legacy = BioForgeIds.legacyPathogen(pathogenId);
        setPathogenType(legacy == null ? PathogenType.UNIVERSAL : legacy);
    }

    default void addTransmissionId(ResourceLocation transmissionId) {
        InfectionType legacy = BioForgeIds.legacyTransmission(transmissionId);
        if (legacy != null) addInfectionType(legacy);
    }

    default void removeTransmissionId(ResourceLocation transmissionId) {
        InfectionType legacy = BioForgeIds.legacyTransmission(transmissionId);
        if (legacy != null) removeInfectionType(legacy);
    }
    void clearInfection();


    Collection<StrainImmunity> getStrainImmunities();
    boolean hasStrainImmunity(String fingerprint);
    float getStrainProtection(String fingerprint);
    void grantStrainProtection(String fingerprint, String displayName,
                               int durationTicks, float strength);
    default void grantStrainImmunity(String fingerprint, String displayName,
                                     int durationTicks) {
        grantStrainProtection(fingerprint, displayName, durationTicks, 1.0F);
    }
    boolean tickStrainImmunities();
    void copyStrainImmunitiesFrom(InfectionData source);

    EntitySymptoms getSymptoms();
    InfectionLifecycleState getLifecycle();

    default boolean isIncubating() {
        if (!isInfected()) return false;
        var profile = InfectionLifecycleRegistry.INSTANCE.resolve(getLifecycle().profileId());
        return getLifecycle().incubationProgress() < profile.incubationTicks();
    }

    default boolean isInfectionActive() {
        return isInfected() && !isIncubating();
    }

    default boolean isContagious() {
        if (!isInfected()) return false;
        var profile = InfectionLifecycleRegistry.INSTANCE.resolve(getLifecycle().profileId());
        return !isIncubating() || profile.contagiousDuringIncubation();
    }

    default <T> T getSymptom(SymptomKey<T> key) {
        return getSymptoms().get(key);
    }

    default <T> void setSymptom(SymptomKey<T> key, T value) {
        getSymptoms().set(key, value);
    }
}
