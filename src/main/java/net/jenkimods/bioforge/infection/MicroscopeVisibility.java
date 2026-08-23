package net.jenkimods.bioforge.infection;

public enum MicroscopeVisibility {
    NONE,
    VERY_LOW,
    LOW,
    MEDIUM,
    HIGH,
    EXTREME;

    public static MicroscopeVisibility fromName(String name) {
        for (MicroscopeVisibility v : values()) {
            if (v.name().equalsIgnoreCase(name)) return v;
        }
        return NONE;
    }
}