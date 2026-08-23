package net.jenkimods.bioforge.infection.capability;

import net.jenkimods.bioforge.infection.CropInfection;
import net.minecraft.core.BlockPos;

public interface ICropInfectionStorage {
    void setInfection(BlockPos pos, CropInfection infection);
    void removeInfection(BlockPos pos);
    CropInfection getInfection(BlockPos pos);
    boolean isInfected(BlockPos pos);
    java.util.Map<BlockPos, CropInfection> getAllInfections();
}