package net.jenkimods.bioforge.blood;

import net.jenkimods.bioforge.registry.BioForgeAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public final class BloodCapability {
    private BloodCapability() {
    }

    @Nullable
    public static BloodData get(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return null;
        return living.getData(BioForgeAttachments.BLOOD.get());
    }
}
