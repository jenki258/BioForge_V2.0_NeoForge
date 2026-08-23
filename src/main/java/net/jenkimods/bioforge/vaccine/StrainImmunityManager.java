package net.jenkimods.bioforge.vaccine;

import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.infection.StrainImmunity;
import net.jenkimods.bioforge.infection.naming.StrainNamingManager;
import net.jenkimods.bioforge.registry.BioForgeEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;


public final class StrainImmunityManager {
    private StrainImmunityManager() {}

    public static String fingerprint(StrainData strain) {
        return StrainFingerprint.ofPayload(strain == null ? "" : strain.toPayload());
    }

    public static boolean blocks(LivingEntity target, InfectionData targetData,
                                 StrainData incoming) {
        if (target == null || targetData == null || incoming == null) return false;
        float protection = targetData.getStrainProtection(fingerprint(incoming));
        return protection >= 1.0F
                || protection > 0.0F && target.getRandom().nextFloat() < protection;
    }

    public static void grant(LivingEntity target, InfectionData targetData, StrainData strain) {
        grant(target, targetData, strain, 0.5f);
    }

    public static void grant(LivingEntity target, InfectionData targetData, StrainData strain,
                             float quality) {
        if (targetData == null || strain == null || strain.getPathogenId() == null) return;
        String fingerprint = fingerprint(strain);
        String fallbackName = target.level() instanceof ServerLevel level
                ? StrainNamingManager.displayName(level, fingerprint)
                : "Strain " + fingerprint;
        float clampedQuality = Math.max(0.0f, Math.min(1.0f, quality));
        float durationMultiplier = 0.5f + clampedQuality;
        int duration = Math.max(1, Math.round(
                BioForgeServerConfig.strainImmunityDurationTicks() * durationMultiplier));
        targetData.grantStrainProtection(
                fingerprint, fallbackName, duration, 1.0F);
        refreshStatusEffect(target, targetData);
    }

    public static void grantResistance(LivingEntity target, InfectionData targetData,
                                       StrainData strain, float strength,
                                       int durationTicks) {
        if (targetData == null || strain == null || strain.getPathogenId() == null) return;
        String fingerprint = fingerprint(strain);
        String fallbackName = target.level() instanceof ServerLevel level
                ? StrainNamingManager.displayName(level, fingerprint)
                : "Strain " + fingerprint;
        targetData.grantStrainProtection(fingerprint, fallbackName,
                Math.max(1, durationTicks),
                Math.max(0.0F, Math.min(1.0F, strength)));
        refreshStatusEffect(target, targetData);
    }

    public static void refreshStatusEffect(LivingEntity target, InfectionData targetData) {
        int longest = targetData.getStrainImmunities().stream()
                .filter(StrainImmunity::isActive)
                .mapToInt(StrainImmunity::remainingTicks)
                .max().orElse(0);
        if (longest <= 0) {
            target.removeEffect(BioForgeEffects.STRAIN_IMMUNITY);
            return;
        }
        MobEffectInstance current = target.getEffect(BioForgeEffects.STRAIN_IMMUNITY);
        if (current == null || Math.abs(current.getDuration() - longest) > 25) {
            target.forceAddEffect(new MobEffectInstance(BioForgeEffects.STRAIN_IMMUNITY,
                    longest, 0, false, false, true), target);
        }
    }
}
