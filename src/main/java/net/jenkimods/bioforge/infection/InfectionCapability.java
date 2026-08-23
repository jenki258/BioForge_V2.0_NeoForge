package net.jenkimods.bioforge.infection;

import net.jenkimods.bioforge.registry.BioForgeAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public final class InfectionCapability {
    private InfectionCapability() {
    }

    @Nullable
    public static InfectionData get(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return null;
        return living.getData(BioForgeAttachments.INFECTION.get());
    }
}
