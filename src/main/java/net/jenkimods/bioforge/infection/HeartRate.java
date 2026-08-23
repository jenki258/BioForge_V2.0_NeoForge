package net.jenkimods.bioforge.infection;

public enum HeartRate {
    NORMAL,
    TACHY,
    BRADY;

    public static HeartRate fromName(String name) {
        for (HeartRate v : values()) {
            if (v.name().equalsIgnoreCase(name)) return v;
        }
        return NORMAL;
    }
}
