package net.jenkimods.bioforge.infection.symptoms;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.api.behavior.BioForgeBehaviorRegistry;
import net.jenkimods.bioforge.api.definition.SymptomDefinition;
import net.jenkimods.bioforge.blood.BloodCapability;
import net.jenkimods.bioforge.blood.BloodData;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.infection.HeartRate;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.InfectionType;
import net.jenkimods.bioforge.infection.LungSound;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.infection.spread.ProtectiveEquipment;
import net.jenkimods.bioforge.infection.spread.SurfaceContaminationData;
import net.jenkimods.bioforge.mutation.MutationManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = BioForge.MODID)
public final class SymptomRuntime {
    private SymptomRuntime() {}

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!(entity.level() instanceof ServerLevel level)
                || !BioForgeServerConfig.symptomsEnabled()) return;
        InfectionData data = InfectionCapability.get(entity);
        if (data == null || !data.isInfectionActive()) return;
        int tick = entity.tickCount + entity.getId();
        float infectionStrength = Math.max(0.0F,
                data.getSymptom(BioForgeSymptoms.INFECTION_STRENGTH));

        if (tick % 20 == 0) runAddonBehaviors(level, entity, data);

        if (tick % 40 == 0) {
            applyCardiacEffects(entity, data);
            applyTissueAndNeuralEffects(entity, data);
            applyOxygenEffects(entity, data);
        }
        if (tick % 100 == 0) {
            applyRespiratoryEffects(level, entity, data);
            shedSecretions(level, entity, data, infectionStrength);
        }
        if (tick % 1200 == 0) {
            applyTemperatureDamage(level, entity, data, infectionStrength);
        }
        if (tick % 200 == 0) {
            applyLesionBleeding(level, entity, data);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void runAddonBehaviors(ServerLevel level, LivingEntity entity, InfectionData data) {
        java.util.Map<String, SymptomKey<?>> keys = BioForgeSymptoms.getAllSymptomKeys();
        for (SymptomDefinition definition : BioForgeDefinitionManager.SYMPTOMS.values()) {
            if (definition.behaviors().isEmpty()) continue;
            SymptomKey key = keys.get(BioForgeDefinitionManager.storageId(definition.id()));
            if (key == null) continue;
            Object value = data.getSymptoms().get(key);
            for (net.minecraft.resources.ResourceLocation behaviorId : definition.behaviors()) {
                BioForgeBehaviorRegistry.symptom(behaviorId).ifPresent(behavior -> {
                    try {
                        behavior.tick(level, entity, data, definition, value);
                    } catch (RuntimeException exception) {
                        BioForge.LOGGER.error("Symptom behavior {} failed for {}: {}",
                                behaviorId, definition.id(), exception.getMessage());
                    }
                });
            }
        }
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()
                || !active(entity, "perfusion_index")) return;
        InfectionData data = InfectionCapability.get(entity);
        if (data == null || !data.isInfectionActive()) return;
        float perfusion = Math.max(0.0F, Math.min(1.0F,
                data.getSymptom(BioForgeSymptoms.PERFUSION_INDEX)));
        event.setAmount(event.getAmount() * (0.25F + perfusion * 0.75F));
    }

    private static void applyCardiacEffects(LivingEntity entity, InfectionData data) {
        if (!active(entity, "heart_rate")) return;
        HeartRate rate = data.getSymptom(BioForgeSymptoms.HEART_RATE);
        if (rate == HeartRate.TACHY) {
            exhaust(entity, 0.12F);
            if (entity.isSprinting() && entity.getRandom().nextFloat() < 0.18F) {
                entity.setSprinting(false);
            }
        } else if (rate == HeartRate.BRADY) {
            apply(entity, MobEffects.MOVEMENT_SLOWDOWN, 60, 0);
            apply(entity, MobEffects.DIG_SLOWDOWN, 60, 0);
        }
    }

    private static void applyRespiratoryEffects(ServerLevel level, LivingEntity entity,
                                                InfectionData data) {
        if (!active(entity, "lung_sound")
                || data.getSymptom(BioForgeSymptoms.LUNG_SOUND) != LungSound.CRACKLE) return;
        if (entity.isSprinting() && entity.getRandom().nextFloat() < 0.35F) {
            entity.setSprinting(false);
        }
        exhaust(entity, 0.25F);
        level.sendParticles(ParticleTypes.POOF,
                entity.getX(), entity.getEyeY(), entity.getZ(),
                5, 0.2D, 0.06D, 0.2D, 0.015D);
        level.sendParticles(ParticleTypes.CLOUD,
                entity.getX(), entity.getEyeY() - 0.12D, entity.getZ(),
                2, 0.1D, 0.04D, 0.1D, 0.008D);
    }

    private static void applyTissueAndNeuralEffects(LivingEntity entity, InfectionData data) {
        if (active(entity, "otoscope_swelling")
                && data.getSymptom(BioForgeSymptoms.OTOSCOPE_SWELLING) > 0.35F) {
            apply(entity, MobEffects.MOVEMENT_SLOWDOWN, 60,
                    data.getSymptom(BioForgeSymptoms.OTOSCOPE_SWELLING) > 0.75F ? 1 : 0);
        }
        if (active(entity, "reflex_delay")
                && data.getSymptom(BioForgeSymptoms.REFLEX_DELAY) > 0.25F) {
            apply(entity, MobEffects.DIG_SLOWDOWN, 60,
                    data.getSymptom(BioForgeSymptoms.REFLEX_DELAY) > 0.6F ? 1 : 0);
        }
        if (active(entity, "reflex_strength")
                && data.getSymptom(BioForgeSymptoms.REFLEX_STRENGTH) < 0.4F) {
            apply(entity, MobEffects.WEAKNESS, 60,
                    data.getSymptom(BioForgeSymptoms.REFLEX_STRENGTH) < 0.2F ? 1 : 0);
        }
        if (active(entity, "neural_damage")) {
            float neural = data.getSymptom(BioForgeSymptoms.NEURAL_DAMAGE);
            if (neural > 0.35F) apply(entity, MobEffects.CONFUSION, 100, 0);
            if (neural > 0.7F) apply(entity, MobEffects.WEAKNESS, 80, 1);
        }
        if (active(entity, "otoscope_redness")
                && data.getSymptom(BioForgeSymptoms.OTOSCOPE_REDNESS) > 0.8F
                && entity.tickCount % 400 == 0) {
            entity.hurt(entity.level().damageSources().generic(), 0.5F);
        }
    }

    private static void applyOxygenEffects(LivingEntity entity, InfectionData data) {
        if (!active(entity, "oxygen_saturation") || !entity.isUnderWater()) return;
        float saturation = Math.max(0.0F, Math.min(1.0F,
                data.getSymptom(BioForgeSymptoms.OXYGEN_SATURATION)));
        if (saturation >= 0.94F) return;
        int extraLoss = 1 + Math.round((0.94F - saturation) * 12.0F);
        entity.setAirSupply(Math.max(-20, entity.getAirSupply() - extraLoss));
    }

    private static void applyTemperatureDamage(ServerLevel level, LivingEntity entity,
                                               InfectionData data, float strength) {
        float chance = Math.min(0.9F, 0.2F + strength * 0.45F);
        if (active(entity, "temperature_plus")
                && data.getSymptom(BioForgeSymptoms.TEMPERATURE_PLUS)
                && !MutationManager.hasMutationTag(data, "heat_immunity")
                && !ProtectiveEquipment.blocksHeatSymptoms(entity)
                && level.getRandom().nextFloat() < chance) {
            entity.hurt(level.damageSources().onFire(), 1.0F + strength);
        }
        if (active(entity, "temperature_minus")
                && data.getSymptom(BioForgeSymptoms.TEMPERATURE_MINUS)
                && !MutationManager.hasMutationTag(data, "cold_immunity")
                && !ProtectiveEquipment.blocksChillSymptoms(entity)
                && level.getRandom().nextFloat() < chance) {
            entity.hurt(level.damageSources().freeze(), 1.0F + strength);
        }
    }

    private static void applyLesionBleeding(ServerLevel level, LivingEntity entity, InfectionData data) {
        if (!active(entity, "otoscope_lesions")) return;
        float lesions = data.getSymptom(BioForgeSymptoms.OTOSCOPE_LESIONS);
        if (lesions < 0.55F || entity.getRandom().nextFloat() >= lesions * 0.35F) return;
        BloodData blood = BloodCapability.get(entity);
        if (blood != null) blood.setBlood(Math.max(0, blood.getBlood() - 1));
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.55D, entity.getZ(),
                2 + Math.round(lesions * 3.0F),
                entity.getBbWidth() * 0.25D, entity.getBbHeight() * 0.18D,
                entity.getBbWidth() * 0.25D, 0.02D);
    }

    private static void shedSecretions(ServerLevel level, LivingEntity entity,
                                       InfectionData data, float strength) {
        if (!active(entity, "otoscope_secretion")
                || data.getSymptom(BioForgeSymptoms.OTOSCOPE_SECRETION) < 0.45F) return;
        boolean surfaceRoute = BioForgeDefinitionManager.hasTransmissionBehavior(data, InfectionType.CONTACT_BASED)
                || BioForgeDefinitionManager.hasTransmissionBehavior(data, InfectionType.ENVIRONMENTAL);
        if (!surfaceRoute || ProtectiveEquipment.outgoingContactMultiplier(entity) <= 0.0F
                || level.getRandom().nextFloat() >=
                data.getSymptom(BioForgeSymptoms.OTOSCOPE_SECRETION) * 0.4F) return;
        SurfaceContaminationData.get(level).contaminate(entity.blockPosition().below(),
                StrainData.buildFrom(data), Math.min(1.0F, 0.4F + strength * 0.6F),
                BioForgeServerConfig.surfaceLifetimeTicks(), level.getGameTime());
        level.sendParticles(ParticleTypes.SPLASH,
                entity.getX(), entity.getY() + 0.08D, entity.getZ(),
                4, entity.getBbWidth() * 0.35D, 0.04D,
                entity.getBbWidth() * 0.35D, 0.06D);
    }

    private static boolean active(LivingEntity entity, String symptomId) {
        return BioForgeServerConfig.isSymptomEnabled(symptomId)
                && !SymptomSuppression.isSuppressed(entity, symptomId);
    }

    private static void apply(LivingEntity entity, Holder<MobEffect> effect,
                              int duration, int amplifier) {
        MobEffectInstance current = entity.getEffect(effect);
        if (current != null && current.getAmplifier() >= amplifier
                && current.getDuration() > 30) return;
        entity.addEffect(new MobEffectInstance(effect, duration, amplifier,
                true, false, false));
    }

    private static void exhaust(LivingEntity entity, float amount) {
        if (entity instanceof Player player) player.causeFoodExhaustion(amount);
    }
}
