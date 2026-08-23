package net.jenkimods.bioforge.infection.capability;

import net.jenkimods.bioforge.registry.BioForgeAttachments;
import net.minecraft.world.level.chunk.LevelChunk;

public final class CropInfectionCapability {
    private CropInfectionCapability() {
    }

    public static ICropInfectionStorage get(LevelChunk chunk) {
        return chunk.getData(BioForgeAttachments.CROP_INFECTION.get());
    }
}
