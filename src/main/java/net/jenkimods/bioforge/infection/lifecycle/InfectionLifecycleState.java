package net.jenkimods.bioforge.infection.lifecycle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class InfectionLifecycleState {
    public static final ResourceLocation DEFAULT_PROFILE = ResourceLocation.tryBuild("bioforge", "default");

    private ResourceLocation profileId = DEFAULT_PROFILE;
    private long infectionAgeTicks;
    private float incubationProgress;
    private float hotAdaptationPoints;
    private float coldAdaptationPoints;
    private boolean selfDestructRequested;

    public ResourceLocation profileId() { return profileId; }
    public long infectionAgeTicks() { return infectionAgeTicks; }
    public float incubationProgress() { return incubationProgress; }
    public float hotAdaptationPoints() { return hotAdaptationPoints; }
    public float coldAdaptationPoints() { return coldAdaptationPoints; }
    public boolean selfDestructRequested() { return selfDestructRequested; }

    public void reset(ResourceLocation profileId) {
        this.profileId = profileId == null ? DEFAULT_PROFILE : profileId;
        infectionAgeTicks = 0L;
        incubationProgress = 0.0F;
        hotAdaptationPoints = 0.0F;
        coldAdaptationPoints = 0.0F;
        selfDestructRequested = false;
    }

    public void setProfileId(ResourceLocation profileId) {
        this.profileId = profileId == null ? DEFAULT_PROFILE : profileId;
    }

    public void advanceAge(long ticks) { infectionAgeTicks = Math.max(0L, infectionAgeTicks + ticks); }
    public void advanceIncubation(float ticks) { incubationProgress = Math.max(0.0F, incubationProgress + ticks); }
    public void addHotPoints(float points) { hotAdaptationPoints = Math.max(0.0F, hotAdaptationPoints + points); }
    public void addColdPoints(float points) { coldAdaptationPoints = Math.max(0.0F, coldAdaptationPoints + points); }
    public void requestSelfDestruct() { selfDestructRequested = true; }
    public void clearSelfDestructRequest() { selfDestructRequested = false; }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Profile", profileId.toString());
        tag.putLong("Age", infectionAgeTicks);
        tag.putFloat("Incubation", incubationProgress);
        tag.putFloat("HotAdaptation", hotAdaptationPoints);
        tag.putFloat("ColdAdaptation", coldAdaptationPoints);
        tag.putBoolean("SelfDestruct", selfDestructRequested);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("Profile"));
        profileId = parsed == null ? DEFAULT_PROFILE : parsed;
        infectionAgeTicks = Math.max(0L, tag.getLong("Age"));
        incubationProgress = Math.max(0.0F, tag.getFloat("Incubation"));
        hotAdaptationPoints = Math.max(0.0F, tag.getFloat("HotAdaptation"));
        coldAdaptationPoints = Math.max(0.0F, tag.getFloat("ColdAdaptation"));
        selfDestructRequested = tag.getBoolean("SelfDestruct");
    }
}
