package net.jenkimods.bioforge.infection;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public enum PathogenType {
    VIRUS(
            InfectionType.AIR_BORNE, InfectionType.CONTACT_BASED,
            InfectionType.ATTACK_BASED, InfectionType.BLOOD,
            InfectionType.FOOD_BORNE, InfectionType.WATER_BORNE
    ),
    BACTERIA(
            InfectionType.FOOD_BORNE, InfectionType.WATER_BORNE,
            InfectionType.CONTACT_BASED, InfectionType.ATTACK_BASED,
            InfectionType.ENVIRONMENTAL, InfectionType.ANIMALS
    ),
    FUNGI(
            InfectionType.AIR_BORNE, InfectionType.CONTACT_BASED,
            InfectionType.FOOD_BORNE,
            InfectionType.ENVIRONMENTAL
    ),
    PARASITE(
            InfectionType.FOOD_BORNE, InfectionType.WATER_BORNE,
            InfectionType.ANIMALS, InfectionType.ATTACK_BASED,
            InfectionType.BLOOD
    ),
    PRION(
            InfectionType.FOOD_BORNE, InfectionType.BLOOD,
            InfectionType.CONTACT_BASED
    ),
    UNIVERSAL(InfectionType.values());

    private final Set<InfectionType> allowedTransmissions;

    PathogenType(InfectionType... allowed) {
        this.allowedTransmissions = EnumSet.copyOf(Arrays.asList(allowed));
    }

    public boolean allows(InfectionType type) {
        return allowedTransmissions.contains(type);
    }

    public Set<InfectionType> getAllowedTransmissions() {
        return allowedTransmissions;
    }

    public boolean isEnvironmental() {
        return this == FUNGI || this == BACTERIA || this == UNIVERSAL;
    }

    public static PathogenType fromName(String name) {
        for (PathogenType t : values()) {
            if (t.name().equalsIgnoreCase(name)) return t;
        }
        return UNIVERSAL;
    }
}