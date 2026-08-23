package net.jenkimods.bioforge.infection;

public enum InfectionType {
    AIR_BORNE,
    FOOD_BORNE,
    WATER_BORNE,
    CONTACT_BASED,
    ATTACK_BASED,
    ANIMALS,
    BLOOD,
    ENVIRONMENTAL;

    public static InfectionType fromName(String name) {
        for (InfectionType t : values()) {
            if (t.name().equalsIgnoreCase(name)) return t;
        }
        return CONTACT_BASED;
    }
}