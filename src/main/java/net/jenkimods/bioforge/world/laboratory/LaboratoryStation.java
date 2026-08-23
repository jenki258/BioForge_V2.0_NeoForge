package net.jenkimods.bioforge.world.laboratory;

import net.minecraft.util.StringRepresentable;

public enum LaboratoryStation implements StringRepresentable {
    BARREL_PRESS("barrel_press", 4, 1),
    CHEMICAL_SYNTHESIZER("chemical_synthesizer", 3, 1),
    STERILIZATION_CHAMBER("sterilization_chamber", 8, 0),
    PHARMA_MIXER("pharma_mixer", 5, 2);

    private final String id;
    private final int inputSlots;
    private final int outputSlots;

    LaboratoryStation(String id, int inputSlots, int outputSlots) {
        this.id = id;
        this.inputSlots = inputSlots;
        this.outputSlots = outputSlots;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public int inputSlots() {
        return inputSlots;
    }

    public int outputSlots() {
        return outputSlots;
    }

    public int machineSlots() {
        return inputSlots + outputSlots;
    }

    public int resultSlot() {
        return outputSlots == 0 ? -1 : inputSlots;
    }

    public int wasteSlot() {
        return outputSlots < 2 ? -1 : inputSlots + 1;
    }

    public boolean processesInPlace() {
        return outputSlots == 0;
    }

    public static LaboratoryStation byName(String value) {
        for (LaboratoryStation station : values()) {
            if (station.id.equals(value)) return station;
        }
        throw new IllegalArgumentException("Unknown laboratory station " + value);
    }
}
