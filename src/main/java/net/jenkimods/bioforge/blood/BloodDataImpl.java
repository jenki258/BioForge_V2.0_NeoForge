package net.jenkimods.bioforge.blood;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

public class BloodDataImpl implements BloodData {

    private int       blood       = MAX_BLOOD;
    private BloodType bloodType   = BloodType.O_POSITIVE;

    private boolean initialized = false;

    private boolean needsInit = true;

    @Override public int       getBlood()       { return blood; }
    @Override public BloodType getBloodType()   { return bloodType; }
    @Override public boolean   isInitialized()  { return initialized; }
    @Override public boolean   needsInit()      { return needsInit; }
    @Override public void      clearNeedsInit() { this.needsInit = false; }

    @Override
    public void setBlood(int amount) {
        this.blood = Mth.clamp(amount, 0, MAX_BLOOD);
    }

    @Override
    public void setBloodType(BloodType type) {
        this.bloodType   = type;
        this.initialized = true;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt    ("Blood",       blood);
        tag.putString ("BloodType",   bloodType.name());
        tag.putBoolean("Initialized", initialized);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        if (tag.contains("Blood"))
            this.blood = Mth.clamp(tag.getInt("Blood"), 0, MAX_BLOOD);
        if (tag.contains("BloodType"))
            this.bloodType = BloodType.fromName(tag.getString("BloodType"));
        if (tag.contains("Initialized"))
            this.initialized = tag.getBoolean("Initialized");
    }
}