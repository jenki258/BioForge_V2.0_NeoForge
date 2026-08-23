package net.jenkimods.bioforge.world.vaccine;

import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.Locale;

public enum VaccineMakerOperation {
    FULL,
    DIRECTED,
    RANDOM_MUTATION,
    RESISTANCE_PILL,
    SYMPTOM_TABLET,
    CLONE;

    public static VaccineMakerOperation fromName(String name) {
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unknown Vaccine Maker operation: " + name);
        }
    }

    public ResourceLocation id() { return BioForgeIds.bioforge(name()); }

    @Nullable
    public static VaccineMakerOperation fromId(ResourceLocation id) {
        if (id == null || !BioForgeIds.BIOFORGE_NAMESPACE.equals(id.getNamespace())) return null;
        try {
            return valueOf(id.getPath().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
