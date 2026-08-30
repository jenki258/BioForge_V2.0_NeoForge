package net.jenkimods.bioforge.infection.lifecycle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class InfectionLifecycleState {
    public static final ResourceLocation DEFAULT_PROFILE = ResourceLocation.tryBuild("bioforge", "default");
    public static final int NO_LIFESPAN_OVERRIDE = Integer.MIN_VALUE;

    private ResourceLocation profileId = DEFAULT_PROFILE;
    private long infectionAgeTicks;
    private long activeAgeTicks;
    private float incubationProgress;
    private float hotAdaptationPoints;
    private float coldAdaptationPoints;
    private boolean selfDestructRequested;
    private int incubationTicksOverride = -1;
    private int lifespanTicksOverride = NO_LIFESPAN_OVERRIDE;

    public ResourceLocation profileId() { return profileId; }
    public long infectionAgeTicks() { return infectionAgeTicks; }
    public long activeAgeTicks() { return activeAgeTicks; }
    public float incubationProgress() { return incubationProgress; }
    public float hotAdaptationPoints() { return hotAdaptationPoints; }
    public float coldAdaptationPoints() { return coldAdaptationPoints; }
    public boolean selfDestructRequested() { return selfDestructRequested; }
    public int incubationTicksOverride() { return incubationTicksOverride; }
    public int lifespanTicksOverride() { return lifespanTicksOverride; }

    public int effectiveIncubationTicks(int profileTicks) {
        return incubationTicksOverride >= 0 ? incubationTicksOverride : Math.max(0, profileTicks);
    }

    public int effectiveLifespanTicks(int profileTicks) {
        return lifespanTicksOverride != NO_LIFESPAN_OVERRIDE
                ? lifespanTicksOverride : profileTicks;
    }

    public void reset(ResourceLocation profileId) {
        this.profileId = profileId == null ? DEFAULT_PROFILE : profileId;
        infectionAgeTicks = 0L;
        activeAgeTicks = 0L;
        incubationProgress = 0.0F;
        hotAdaptationPoints = 0.0F;
        coldAdaptationPoints = 0.0F;
        selfDestructRequested = false;
        incubationTicksOverride = -1;
        lifespanTicksOverride = NO_LIFESPAN_OVERRIDE;
    }

    public void setProfileId(ResourceLocation profileId) {
        this.profileId = profileId == null ? DEFAULT_PROFILE : profileId;
    }

    public void setTimingOverrides(int incubationTicks, int lifespanTicks) {
        incubationTicksOverride = Math.max(0, incubationTicks);
        lifespanTicksOverride = Math.max(-1, lifespanTicks);
    }

    public void setTimingOverridesRaw(int incubationTicks, int lifespanTicks) {
        incubationTicksOverride = incubationTicks >= 0 ? incubationTicks : -1;
        lifespanTicksOverride = lifespanTicks >= -1
                ? lifespanTicks : NO_LIFESPAN_OVERRIDE;
    }

    public void advanceAge(long ticks) { infectionAgeTicks = Math.max(0L, infectionAgeTicks + ticks); }
    public void advanceActiveAge(long ticks) { activeAgeTicks = Math.max(0L, activeAgeTicks + ticks); }
    public void advanceIncubation(float ticks) { incubationProgress = Math.max(0.0F, incubationProgress + ticks); }
    public void addHotPoints(float points) { hotAdaptationPoints = Math.max(0.0F, hotAdaptationPoints + points); }
    public void addColdPoints(float points) { coldAdaptationPoints = Math.max(0.0F, coldAdaptationPoints + points); }
    public void requestSelfDestruct() { selfDestructRequested = true; }
    public void clearSelfDestructRequest() { selfDestructRequested = false; }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Profile", profileId.toString());
        tag.putLong("Age", infectionAgeTicks);
        tag.putLong("ActiveAge", activeAgeTicks);
        tag.putFloat("Incubation", incubationProgress);
        tag.putFloat("HotAdaptation", hotAdaptationPoints);
        tag.putFloat("ColdAdaptation", coldAdaptationPoints);
        tag.putBoolean("SelfDestruct", selfDestructRequested);
        if (incubationTicksOverride >= 0) {
            tag.putInt("IncubationTicksOverride", incubationTicksOverride);
        }
        if (lifespanTicksOverride != NO_LIFESPAN_OVERRIDE) {
            tag.putInt("LifespanTicksOverride", lifespanTicksOverride);
        }
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("Profile"));
        profileId = parsed == null ? DEFAULT_PROFILE : parsed;
        infectionAgeTicks = Math.max(0L, tag.getLong("Age"));
        activeAgeTicks = Math.max(0L, tag.getLong("ActiveAge"));
        incubationProgress = Math.max(0.0F, tag.getFloat("Incubation"));
        hotAdaptationPoints = Math.max(0.0F, tag.getFloat("HotAdaptation"));
        coldAdaptationPoints = Math.max(0.0F, tag.getFloat("ColdAdaptation"));
        selfDestructRequested = tag.getBoolean("SelfDestruct");
        incubationTicksOverride = tag.contains("IncubationTicksOverride")
                ? Math.max(0, tag.getInt("IncubationTicksOverride")) : -1;
        lifespanTicksOverride = tag.contains("LifespanTicksOverride")
                ? Math.max(-1, tag.getInt("LifespanTicksOverride"))
                : NO_LIFESPAN_OVERRIDE;
    }
}
