package net.jenkimods.bioforge.infection.symptoms;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

public final class SymptomSuppression {
    private static final String ROOT = "BioForgeSymptomSuppression";

    private SymptomSuppression() {}

    public static void suppress(LivingEntity entity, String symptomId, int durationTicks) {
        if (entity == null || symptomId == null || symptomId.isBlank()) return;
        CompoundTag persistent = entity.getPersistentData();
        CompoundTag active = persistent.contains(ROOT)
                ? persistent.getCompound(ROOT) : new CompoundTag();
        long expires = entity.level().getGameTime() + Math.max(20, durationTicks);
        active.putLong(symptomId, Math.max(active.getLong(symptomId), expires));
        persistent.put(ROOT, active);
    }

    public static boolean isSuppressed(LivingEntity entity, String symptomId) {
        CompoundTag persistent = entity.getPersistentData();
        if (!persistent.contains(ROOT)) return false;
        CompoundTag active = persistent.getCompound(ROOT);
        long expires = active.getLong(symptomId);
        if (expires <= entity.level().getGameTime()) {
            active.remove(symptomId);
            if (active.isEmpty()) persistent.remove(ROOT);
            else persistent.put(ROOT, active);
            return false;
        }
        return true;
    }
}
