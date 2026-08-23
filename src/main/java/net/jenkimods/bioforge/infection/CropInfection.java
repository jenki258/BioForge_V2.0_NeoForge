package net.jenkimods.bioforge.infection;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class CropInfection {
    private StrainData strain;

    public CropInfection(String strainData) {
        this.strain = StrainData.parse(strainData);
    }

    public String getStrainData() {
        return strain.toPayload();
    }

    public PathogenType getPathogen() {
        return strain.getPathogen();
    }

    public UUID getColonyId() {
        return strain.getColonyId().orElse(null);
    }

    public float getInfectionStrength() {
        return strain.getSymptom("infection_strength")
                .map(Float::parseFloat)
                .orElse(0.5f);
    }

    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("strainData", strain.toPayload());
        return tag;
    }

    public static CropInfection readFromNBT(CompoundTag tag) {
        return new CropInfection(tag.getString("strainData"));
    }
}
