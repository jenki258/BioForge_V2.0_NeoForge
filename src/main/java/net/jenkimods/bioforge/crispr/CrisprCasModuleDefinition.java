package net.jenkimods.bioforge.crispr;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.jenkimods.bioforge.infection.PathogenType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;

import java.util.LinkedHashSet;
import java.util.Set;

public record CrisprCasModuleDefinition(
        ResourceLocation id,
        String displayName,
        String translationKey,
        String pam,
        float efficiency,
        Set<ResourceLocation> compatibleGuideProfiles,
        Set<PathogenType> compatiblePathogens,
        Set<ResourceLocation> compatiblePathogenIds
) {
    public CrisprCasModuleDefinition {
        if (displayName == null || displayName.isBlank()) {
            displayName = CrisprDisplayNames.humanize(id.getPath());
        }
        if (translationKey == null || translationKey.isBlank()) {
            translationKey = "crispr.cas_module." + id.getNamespace() + "." + id.getPath();
        }
        if (pam == null || pam.isBlank()) {
            throw new IllegalArgumentException("Cas module PAM cannot be empty");
        }
        efficiency = Mth.clamp(efficiency, 0.0f, 1.0f);
        compatibleGuideProfiles = Set.copyOf(compatibleGuideProfiles);
        compatiblePathogens = Set.copyOf(compatiblePathogens);
        compatiblePathogenIds = Set.copyOf(compatiblePathogenIds);
    }

    public CrisprCasModuleDefinition(ResourceLocation id, String displayName, String pam,
                                     float efficiency,
                                     Set<ResourceLocation> compatibleGuideProfiles,
                                     Set<PathogenType> compatiblePathogens) {
        this(id, displayName, "", pam, efficiency, compatibleGuideProfiles, compatiblePathogens,
                compatiblePathogens.stream().map(BioForgeIds::pathogen)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    public CrisprCasModuleDefinition(ResourceLocation id, String displayName, String pam,
                                     float efficiency,
                                     Set<ResourceLocation> compatibleGuideProfiles,
                                     Set<PathogenType> compatiblePathogens,
                                     Set<ResourceLocation> compatiblePathogenIds) {
        this(id, displayName, "", pam, efficiency, compatibleGuideProfiles,
                compatiblePathogens, compatiblePathogenIds);
    }

    public static CrisprCasModuleDefinition fromJson(ResourceLocation id, JsonObject json) {
        Set<ResourceLocation> profiles = new LinkedHashSet<>();
        if (json.has("compatible_guide_profiles")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(
                    json, "compatible_guide_profiles")) {
                ResourceLocation profile = ResourceLocation.tryParse(element.getAsString());
                if (profile == null) {
                    throw new IllegalArgumentException(
                            "Invalid compatible guide profile: " + element.getAsString());
                }
                profiles.add(profile);
            }
        }
        Set<PathogenType> pathogens = new LinkedHashSet<>();
        Set<ResourceLocation> pathogenIds = new LinkedHashSet<>();
        if (json.has("compatible_pathogens")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(
                    json, "compatible_pathogens")) {
                try {
                    ResourceLocation pathogenId = BioForgeIds.parse(element.getAsString());
                    pathogenIds.add(pathogenId);
                    PathogenType legacy = BioForgeIds.legacyPathogen(pathogenId);
                    if (legacy != null) pathogens.add(legacy);
                } catch (IllegalArgumentException invalid) {
                    throw new IllegalArgumentException(
                            "Invalid compatible pathogen: " + element.getAsString());
                }
            }
        }
        return new CrisprCasModuleDefinition(
                id,
                GsonHelper.getAsString(json, "display_name",
                        CrisprDisplayNames.humanize(id.getPath())),
                GsonHelper.getAsString(json, "translation_key", ""),
                GsonHelper.getAsString(json, "pam", "NGG"),
                GsonHelper.getAsFloat(json, "efficiency", 1.0f),
                profiles,
                pathogens,
                pathogenIds
        );
    }

    public boolean isCompatible(ResourceLocation profile) {
        return compatibleGuideProfiles.isEmpty()
                || compatibleGuideProfiles.contains(profile);
    }

    public boolean isCompatible(ResourceLocation profile, PathogenType pathogen) {
        return isCompatible(profile, pathogen == null ? null : BioForgeIds.pathogen(pathogen));
    }

    public boolean isCompatible(ResourceLocation profile, ResourceLocation pathogenId) {
        return isCompatible(profile) && pathogenId != null
                && (BioForgeIds.pathogen(PathogenType.UNIVERSAL).equals(pathogenId)
                || compatiblePathogenIds.isEmpty()
                || compatiblePathogenIds.contains(pathogenId));
    }
}
