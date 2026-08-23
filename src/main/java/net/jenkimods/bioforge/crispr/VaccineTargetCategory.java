package net.jenkimods.bioforge.crispr;

import java.util.Locale;

public enum VaccineTargetCategory {
    MUTATION,
    TRANSMISSION,
    SYMPTOM;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static VaccineTargetCategory fromName(String name) {
        if (name == null) return null;
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
