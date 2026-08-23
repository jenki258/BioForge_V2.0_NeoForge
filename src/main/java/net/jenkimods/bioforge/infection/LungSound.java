package net.jenkimods.bioforge.infection;

public enum LungSound {
    NORMAL,
    CRACKLE;

    public static LungSound fromName(String name) {
        for (LungSound v : values()) {
            if (v.name().equalsIgnoreCase(name)) return v;
        }
        return NORMAL;
    }
}
