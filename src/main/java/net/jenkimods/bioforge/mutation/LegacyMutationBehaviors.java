package net.jenkimods.bioforge.mutation;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.api.behavior.BioForgeBehaviorRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.registries.BuiltInRegistries;

public final class LegacyMutationBehaviors {
    private static final TagKey<Block> GRASS_DEPENDENCY_SUBSTRATES = TagKey.create(
            Registries.BLOCK, id("grass_dependency_substrates"));

    private LegacyMutationBehaviors() {}

    public static void register() {
        BioForgeBehaviorRegistry.registerMutationEffect(id("wall_climb"), context -> {
            LivingEntity entity = context.entity();
            if (!touchesClimbableSurface(entity) || entity.isCrouching()
                    || entity.isInWaterOrBubble()) return;
            double vertical = entity.getDeltaMovement().y < 0.0D
                    ? 0.16D : Math.max(entity.getDeltaMovement().y, 0.20D);
            entity.setDeltaMovement(entity.getDeltaMovement().x * 0.92D, vertical,
                    entity.getDeltaMovement().z * 0.92D);
            entity.hasImpulse = true;
            entity.fallDistance = 0.0F;
            if (entity instanceof Player player && entity.tickCount % 20 == 0) {
                player.causeFoodExhaustion(0.04F);
            }
        });
        BioForgeBehaviorRegistry.registerMutationEffect(id("grass_dependency"), context -> {
            LivingEntity entity = context.entity();
            boolean supported = entity.getBlockStateOn().is(GRASS_DEPENDENCY_SUBSTRATES);
            if (supported) return;
            int duration = Math.max(20, context.effect().intValue("duration", 100));
            if (entity instanceof Player player) {
                player.causeFoodExhaustion(Math.max(0.0F,
                        context.effect().floatValue("exhaustion", 0.35F)));
                player.addEffect(new MobEffectInstance(
                        MobEffects.HUNGER, duration, 0, true, false, true));
            } else {
                entity.addEffect(new MobEffectInstance(
                        MobEffects.WEAKNESS, duration, 0, true, false, true));
            }
        });
        BioForgeBehaviorRegistry.registerMutationEffect(id("respiration"), context -> {
            LivingEntity entity = context.entity();
            if (entity.isUnderWater()) entity.setAirSupply(entity.getMaxAirSupply());
        });
        BioForgeBehaviorRegistry.registerMutationEffect(id("camouflage"), context -> {
            LivingEntity entity = context.entity();
            String mode = context.effect().stringValue("mode", "crouching");
            boolean active = switch (mode) {
                case "water" -> entity.isInWaterOrBubble();
                case "dark" -> entity.level().getMaxLocalRawBrightness(entity.blockPosition()) <= 7;
                case "still" -> entity.getDeltaMovement().horizontalDistanceSqr() < 0.0025D;
                default -> entity.isCrouching();
            };
            if (active) entity.addEffect(new MobEffectInstance(
                    MobEffects.INVISIBILITY, 30, 0, true, false, false));
        });
        BioForgeBehaviorRegistry.registerMutationEffect(id("clear_effect"), context -> {
            ResourceLocation effectId = ResourceLocation.tryParse(context.effect().target());
            Holder<MobEffect> effect = effectId == null ? null
                    : BuiltInRegistries.MOB_EFFECT.getHolder(effectId).orElse(null);
            if (effect != null) context.entity().removeEffect(effect);
        });
        BioForgeBehaviorRegistry.registerMutationEffect(id("light_reaction"), context -> {
            LivingEntity entity = context.entity();
            int brightness = entity.level().getMaxLocalRawBrightness(entity.blockPosition());
            String reaction = context.effect().stringValue("reaction", "bright");
            boolean active = "dark".equals(reaction) ? brightness <= 7 : brightness >= 12;
            if (!active) return;
            ResourceLocation effectId = ResourceLocation.tryParse(context.effect().target());
            Holder<MobEffect> effect = effectId == null ? null
                    : BuiltInRegistries.MOB_EFFECT.getHolder(effectId).orElse(null);
            if (effect == null) return;
            entity.addEffect(new MobEffectInstance(effect,
                    Math.max(1, context.effect().intValue("duration", 60)),
                    Math.max(0, context.effect().intValue("amplifier", 0)), true, false, true));
        });
        BioForgeBehaviorRegistry.registerMutationEffect(id("self_destruct"), context -> {
            LivingEntity entity = context.entity();
            String condition = context.effect().stringValue("condition", "always");
            boolean active = switch (condition) {
                case "on_fire" -> entity.isOnFire();
                case "in_water" -> entity.isInWaterOrBubble();
                case "freezing" -> entity.isFreezing();
                case "daylight" -> entity.level().isDay()
                        && entity.level().canSeeSky(entity.blockPosition());
                case "hot_biome" -> entity.level().getBiome(entity.blockPosition())
                        .value().getBaseTemperature() >= 1.2F;
                case "cold_biome" -> entity.level().getBiome(entity.blockPosition())
                        .value().getBaseTemperature() <= 0.25F;
                case "low_health" -> entity.getHealth() / entity.getMaxHealth()
                        <= context.effect().floatValue("health_fraction", 0.25F);
                default -> true;
            };
            float chance = Math.max(0.0F, Math.min(1.0F,
                    context.effect().floatValue("chance", 1.0F)));
            if (active && entity.getRandom().nextFloat() < chance) {
                context.infection().getLifecycle().requestSelfDestruct();
            }
        });
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.tryBuild(BioForge.MODID, path);
    }

    private static boolean touchesClimbableSurface(LivingEntity entity) {
        if (entity.horizontalCollision) return true;
        AABB probe = entity.getBoundingBox().inflate(0.10D, 0.0D, 0.10D)
                .deflate(0.0D, 0.06D, 0.0D);
        return !entity.level().noCollision(entity, probe);
    }
}
