package net.jenkimods.bioforge.api.definition;

import net.jenkimods.bioforge.infection.InfectionType;
import net.jenkimods.bioforge.infection.PathogenType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class BioForgeIds {
    public static final String BIOFORGE_NAMESPACE = "bioforge";
    private BioForgeIds() {}

    public static ResourceLocation bioforge(String path) {
        return ResourceLocation.tryBuild(BIOFORGE_NAMESPACE, normalizePath(path));
    }

    public static ResourceLocation parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Definition id cannot be empty");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        ResourceLocation id = normalized.indexOf(':') >= 0
                ? ResourceLocation.tryParse(normalized)
                : bioforge(normalized);
        if (id == null) throw new IllegalArgumentException("Invalid definition id: " + value);
        return id;
    }

    public static ResourceLocation pathogen(PathogenType pathogen) {
        return bioforge(pathogen.name());
    }

    public static ResourceLocation transmission(InfectionType transmission) {
        return bioforge(transmission.name());
    }

    @Nullable
    public static PathogenType legacyPathogen(ResourceLocation id) {
        if (id == null || !BIOFORGE_NAMESPACE.equals(id.getNamespace())) return null;
        try {
            return PathogenType.valueOf(id.getPath().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    public static InfectionType legacyTransmission(ResourceLocation id) {
        if (id == null || !BIOFORGE_NAMESPACE.equals(id.getNamespace())) return null;
        try {
            return InfectionType.valueOf(id.getPath().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static String legacyCompatible(ResourceLocation id) {
        return BIOFORGE_NAMESPACE.equals(id.getNamespace())
                ? id.getPath().toUpperCase(Locale.ROOT)
                : id.toString();
    }

    private static String normalizePath(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
