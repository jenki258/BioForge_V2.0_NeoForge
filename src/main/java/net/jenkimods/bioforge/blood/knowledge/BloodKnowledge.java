package net.jenkimods.bioforge.blood.knowledge;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public final class BloodKnowledge {

    private final UUID   subjectUUID;
    private final String subjectName;
    private final String subjectType;
    private final boolean isPlayer;

    private Boolean antiA;
    private Boolean antiB;
    private Boolean antiD;

    private long lastUpdated;

    public BloodKnowledge(UUID subjectUUID, String subjectName, String subjectType, boolean isPlayer) {
        this.subjectUUID = subjectUUID;
        this.subjectName = subjectName;
        this.subjectType = subjectType;
        this.isPlayer    = isPlayer;
        this.lastUpdated = System.currentTimeMillis();
    }

    public UUID    getSubjectUUID() { return subjectUUID; }
    public String  getSubjectName() { return subjectName; }
    public String  getSubjectType() { return subjectType; }
    public boolean isPlayer()       { return isPlayer; }
    public Boolean getAntiA()       { return antiA; }
    public Boolean getAntiB()       { return antiB; }
    public Boolean getAntiD()       { return antiD; }
    public long    getLastUpdated() { return lastUpdated; }

    public void setAntiA(boolean result) { this.antiA = result; touch(); }
    public void setAntiB(boolean result) { this.antiB = result; touch(); }
    public void setAntiD(boolean result) { this.antiD = result; touch(); }

    private void touch() { this.lastUpdated = System.currentTimeMillis(); }

    public boolean hasAnyKnowledge() {
        return antiA != null || antiB != null || antiD != null;
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID   ("UUID",        subjectUUID);
        tag.putString ("Name",        subjectName);
        tag.putString ("Type",        subjectType);
        tag.putBoolean("IsPlayer",    isPlayer);
        tag.putLong   ("LastUpdated", lastUpdated);
        if (antiA != null) tag.putBoolean("AntiA", antiA);
        if (antiB != null) tag.putBoolean("AntiB", antiB);
        if (antiD != null) tag.putBoolean("AntiD", antiD);
        return tag;
    }

    public static BloodKnowledge deserialize(CompoundTag tag) {
        UUID   uuid     = tag.getUUID("UUID");
        String name     = tag.getString("Name");
        String type     = tag.getString("Type");
        boolean isPlayer = tag.getBoolean("IsPlayer");

        BloodKnowledge k = new BloodKnowledge(uuid, name, type, isPlayer);
        k.lastUpdated = tag.getLong("LastUpdated");
        if (tag.contains("AntiA")) k.antiA = tag.getBoolean("AntiA");
        if (tag.contains("AntiB")) k.antiB = tag.getBoolean("AntiB");
        if (tag.contains("AntiD")) k.antiD = tag.getBoolean("AntiD");
        return k;
    }
}