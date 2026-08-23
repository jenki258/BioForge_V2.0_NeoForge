package net.jenkimods.bioforge.blood;

public interface BloodData {

    int MAX_BLOOD = 100;

    int       getBlood();
    BloodType getBloodType();
    boolean   isInitialized();

    boolean needsInit();
    void    clearNeedsInit();

    void setBlood(int amount);
    void setBloodType(BloodType type);

    default void addBlood(int amount) {
        setBlood(Math.min(getBlood() + amount, MAX_BLOOD));
    }

    default void removeBlood(int amount) {
        setBlood(Math.max(getBlood() - amount, 0));
    }

    default BloodPhase getPhase() {
        int b = getBlood();
        if (b >= 70) return BloodPhase.NORMAL;
        if (b >= 40) return BloodPhase.WEAKNESS;
        if (b >= 20) return BloodPhase.SEVERE;
        return BloodPhase.CRITICAL;
    }

    enum BloodPhase {
        NORMAL,
        WEAKNESS,
        SEVERE,
        CRITICAL
    }
}