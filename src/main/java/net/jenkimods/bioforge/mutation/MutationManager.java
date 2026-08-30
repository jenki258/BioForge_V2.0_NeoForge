package net.jenkimods.bioforge.mutation;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.api.behavior.BioForgeBehaviorRegistry;
import net.jenkimods.bioforge.api.behavior.MutationEffectContext;
import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.InfectionEventHandler;
import net.jenkimods.bioforge.infection.InfectionInvulnerability;
import net.jenkimods.bioforge.infection.InfectionStore;
import net.jenkimods.bioforge.infection.InfectionType;
import net.jenkimods.bioforge.infection.PathogenType;
import net.jenkimods.bioforge.infection.spread.ProtectiveEquipment;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.infection.symptoms.SymptomKey;
import net.jenkimods.bioforge.infection.symptoms.SymptomSuppression;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

public final class MutationManager {
    private static final int MAX_INTERACTION_MUTATION_CHANGES = 64;
    private static final int INTERACTION_EFFECT_INDEX_BASE = 1024;

    private static final int INTERACTION_EFFECT_INDEX_STRIDE = 256;
    private static final String ATTRIBUTE_MODIFIER_PATH_PREFIX = "mutation/";
    private static final Map<LivingEntity, Long> RUNTIME_DEFINITION_GENERATIONS =
            new WeakHashMap<>();

    public enum ApplyResult {
        APPLIED,
        UNKNOWN,
        NOT_INFECTED,
        DISABLED,
        ALREADY_PRESENT,
        INCOMPATIBLE,
        MISSING_REQUIREMENT,
        CONFLICT,
        INVALID_EFFECT,
        IMMUNE
    }

    private MutationManager() {}

    public static ApplyResult applyMutation(InfectionData data, LivingEntity entity, String mutationId) {
        return MutationLoader.INSTANCE.getMutation(mutationId)
                .map(definition -> applyMutation(definition, data, entity))
                .orElse(ApplyResult.UNKNOWN);
    }

    public static ApplyResult applyMutation(MutationDefinition definition, InfectionData data, LivingEntity entity) {
        return applyMutation(definition, data, entity, false);
    }

    public static ApplyResult applyMutation(MutationDefinition definition, InfectionData data,
                                            LivingEntity entity, boolean force) {
        if (InfectionInvulnerability.isEnabled(entity)) {
            if (entity instanceof ServerPlayer player) InfectionInvulnerability.ensureCured(player);
            return ApplyResult.IMMUNE;
        }
        if (data != null && data.isInfected() && resetRuntimeEffectsAfterReload(entity)) {
            refreshContinuousEffects(data, entity);
        }
        ApplyResult validation = validateApplication(definition, data, force);
        if (validation != ApplyResult.APPLIED) {
            if (validation == ApplyResult.ALREADY_PRESENT) {
                refreshMutation(definition, data, entity);
            }
            return validation;
        }
        if (!validateEffects(definition)) return ApplyResult.INVALID_EFFECT;

        Set<String> before = mutationSnapshot(data);

        data.getSymptoms().addMutation(definition.id());
        if (data.isInfectionActive()) {
            runEffects(definition, MutationDefinition.Trigger.APPLY, data, entity, true);
            runEffects(definition, MutationDefinition.Trigger.CONTINUOUS, data, entity, true);
        }
        reconcileInteractions(before, data, entity, true, false);
        mutationDataChanged(data, entity);
        return ApplyResult.APPLIED;
    }

    public static ApplyResult validateApplication(MutationDefinition definition, InfectionData data, boolean force) {
        if (definition == null) return ApplyResult.UNKNOWN;
        if (!BioForgeServerConfig.isMutationEnabled(definition.id())) return ApplyResult.DISABLED;
        if (data == null || !data.isInfected()) return ApplyResult.NOT_INFECTED;
        if (data.getSymptoms().hasMutation(definition.id())) return ApplyResult.ALREADY_PRESENT;
        if (force) return ApplyResult.APPLIED;
        if (!definition.enabled()) return ApplyResult.DISABLED;
        if (!definition.isCompatible(data.getPathogenId())) return ApplyResult.INCOMPATIBLE;
        if (!definition.requirementsMet(data)) return ApplyResult.MISSING_REQUIREMENT;
        if (definition.conflictsWith(data) || conflictsFromOwnedMutation(definition, data)) {
            return ApplyResult.CONFLICT;
        }
        return ApplyResult.APPLIED;
    }

    private static boolean conflictsFromOwnedMutation(MutationDefinition incoming, InfectionData data) {
        for (String ownedId : data.getSymptoms().getMutations()) {
            MutationDefinition owned = MutationLoader.INSTANCE.getMutation(ownedId).orElse(null);
            if (owned != null && owned.conflictingMutations().contains(incoming.id())) return true;
        }
        return false;
    }

    private static boolean validateEffects(MutationDefinition definition) {
        List<MutationDefinition.Effect> allEffects = new ArrayList<>(definition.effects());
        for (MutationDefinition.Interaction interaction : definition.interactions()) {
            allEffects.addAll(interaction.effects());
        }
        for (MutationDefinition.Effect effect : allEffects) {
            String target = effect.target();
            switch (effect.type()) {
                case "modify_symptom", "set_symptom" -> {
                    if (target.isEmpty() || !BioForgeSymptoms.getAllSymptomKeys().containsKey(target)) {
                        BioForge.LOGGER.warn("Mutation {} references unknown symptom {}", definition.id(), target);
                        return false;
                    }
                }
                case "add_infection_type", "remove_infection_type" -> {
                    if (parseTransmissionId(target) == null) {
                        BioForge.LOGGER.warn("Mutation {} references unknown infection type {}", definition.id(), target);
                        return false;
                    }
                }
                case "potion_effect" -> {
                    ResourceLocation id = ResourceLocation.tryParse(target);
                    if (id == null || !BuiltInRegistries.MOB_EFFECT.containsKey(id)) {
                        BioForge.LOGGER.warn("Mutation {} references unknown potion {}", definition.id(), target);
                        return false;
                    }
                }
                case "spawn_particle" -> {
                    ResourceLocation id = ResourceLocation.tryParse(target);
                    ParticleType<?> particle = id == null ? null : BuiltInRegistries.PARTICLE_TYPE.get(id);
                    if (!(particle instanceof ParticleOptions)) {
                        BioForge.LOGGER.warn("Mutation {} references unsupported particle {}", definition.id(), target);
                        return false;
                    }
                }
                case "attribute_modifier" -> {
                    ResourceLocation id = ResourceLocation.tryParse(target);
                    if (id == null || !BuiltInRegistries.ATTRIBUTE.containsKey(id)) {
                        BioForge.LOGGER.warn("Mutation {} references unknown attribute {}", definition.id(), target);
                        return false;
                    }
                }
                case "play_sound" -> {
                    ResourceLocation id = ResourceLocation.tryParse(target);
                    if (id == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
                        BioForge.LOGGER.warn("Mutation {} references unknown sound {}", definition.id(), target);
                        return false;
                    }
                    String gameEventName = effect.stringValue("game_event", "");
                    if (!gameEventName.isBlank()) {
                        ResourceLocation gameEventId = ResourceLocation.tryParse(gameEventName);
                        if (gameEventId == null
                                || !BuiltInRegistries.GAME_EVENT.containsKey(gameEventId)) {
                            BioForge.LOGGER.warn("Mutation {} references unknown game event {}",
                                    definition.id(), gameEventName);
                            return false;
                        }
                    }
                }
                case "damage" -> {
                    if (!target.isEmpty() && !Set.of("generic", "magic", "wither", "on_fire", "drown")
                            .contains(target.toLowerCase(Locale.ROOT))) {
                        BioForge.LOGGER.warn("Mutation {} references unsupported damage source {}", definition.id(), target);
                        return false;
                    }
                }
                case "heal", "exhaustion", "ignite" -> {
                }
                default -> {
                    try {
                        BioForgeBehaviorRegistry.mutationEffect(BioForgeIds.parse(effect.type()))
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "Missing mutation effect handler " + effect.type()))
                                .validate(effect.parameters());
                    } catch (RuntimeException exception) {
                        BioForge.LOGGER.warn("Mutation {} has invalid custom effect {}: {}",
                                definition.id(), effect.type(), exception.getMessage());
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static boolean removeMutation(InfectionData data, LivingEntity entity, String mutationId) {
        if (data == null || mutationId == null) return false;
        if (data.isInfected() && resetRuntimeEffectsAfterReload(entity)) {
            refreshContinuousEffects(data, entity);
        }
        MutationDefinition definition = MutationLoader.INSTANCE.getMutation(mutationId).orElse(null);
        String storedId = definition == null ? mutationId : definition.id();
        if (!data.getSymptoms().hasMutation(storedId)) return false;
        Set<String> before = mutationSnapshot(data);
        removeMutationInternal(data, entity, storedId);
        reconcileInteractions(before, data, entity, true, false);
        mutationDataChanged(data, entity);
        return true;
    }

    public static int clearMutations(InfectionData data, LivingEntity entity) {
        if (data == null) return 0;


        resetRuntimeEffectsAfterReload(entity);
        List<String> ids = new ArrayList<>(data.getSymptoms().getMutations());
        if (ids.isEmpty()) return 0;
        Set<String> before = mutationSnapshot(data);
        for (String id : ids) {
            removeMutationInternal(data, entity, id);
        }


        reconcileInteractions(before, data, entity, false, false);
        mutationDataChanged(data, entity);
        return ids.size();
    }





    public static void refreshContinuousEffects(InfectionData data, LivingEntity entity) {
        if (data == null || !data.isInfectionActive()) return;
        resetRuntimeEffectsAfterReload(entity);
        Set<String> before = mutationSnapshot(data);
        for (String id : List.copyOf(data.getSymptoms().getMutations())) {
            MutationLoader.INSTANCE.getMutation(id).ifPresent(definition ->
                    refreshMutation(definition, data, entity));
        }
        runActiveInteractionEffects(MutationDefinition.Trigger.CONTINUOUS, data, entity, true);
        reconcileInteractions(before, data, entity, true, true);
        if (!before.equals(mutationSnapshot(data))) {
            mutationDataChanged(data, entity);
        }
    }

    public static void activateIncubatedMutations(InfectionData data, LivingEntity entity) {
        if (data == null || !data.isInfectionActive()) return;
        resetRuntimeEffectsAfterReload(entity);
        Set<String> before = mutationSnapshot(data);
        boolean changed = false;
        for (String id : List.copyOf(data.getSymptoms().getMutations())) {
            MutationDefinition definition = MutationLoader.INSTANCE.getMutation(id).orElse(null);
            if (definition == null || !definition.enabled()
                    || !definition.isCompatible(data.getPathogenId())) continue;
            changed |= runEffects(definition, MutationDefinition.Trigger.APPLY, data, entity, true);
            changed |= runEffects(definition, MutationDefinition.Trigger.CONTINUOUS, data, entity, true);
        }
        changed |= runActiveInteractionEffects(MutationDefinition.Trigger.APPLY, data, entity, true);
        changed |= runActiveInteractionEffects(MutationDefinition.Trigger.CONTINUOUS, data, entity, true);
        changed |= reconcileInteractions(before, data, entity, true, true);
        if (changed || !before.equals(mutationSnapshot(data))) mutationDataChanged(data, entity);
    }

    private static void refreshMutation(MutationDefinition definition, InfectionData data, LivingEntity entity) {
        if (!definition.enabled() || !definition.isCompatible(data.getPathogenId())) return;
        runEffects(definition, MutationDefinition.Trigger.CONTINUOUS, data, entity, true);
    }

    public static void tickMutations(InfectionData data, LivingEntity entity) {
        if (InfectionInvulnerability.isEnabled(entity)) {
            if (entity instanceof ServerPlayer player) InfectionInvulnerability.ensureCured(player);
            return;
        }
        if (data == null || !data.isInfectionActive()) return;
        boolean definitionsReloaded = resetRuntimeEffectsAfterReload(entity);
        if (data.getSymptoms().getMutations().isEmpty()) return;
        if (definitionsReloaded) {
            refreshContinuousEffects(data, entity);
            return;
        }
        boolean infectionDataChanged = false;
        for (String id : List.copyOf(data.getSymptoms().getMutations())) {
            MutationDefinition definition = MutationLoader.INSTANCE.getMutation(id).orElse(null);
            if (definition == null) continue;
            if (!definition.enabled() || !definition.isCompatible(data.getPathogenId())) {
                cleanUpContinuousEffects(definition, data, entity);
                continue;
            }
            infectionDataChanged |= runEffects(
                    definition, MutationDefinition.Trigger.CONTINUOUS, data, entity, false);
        }
        infectionDataChanged |= runActiveInteractionEffects(
                MutationDefinition.Trigger.CONTINUOUS, data, entity, false);
        if (Math.floorMod(entity.tickCount + entity.getId(), 200) == 0) {
            Set<String> before = mutationSnapshot(data);
            infectionDataChanged |= reconcileInteractions(before, data, entity, true, true);
        }
        if (infectionDataChanged) {
            mutationDataChanged(data, entity);
        }
    }

    private static boolean runEffects(MutationDefinition definition, MutationDefinition.Trigger trigger,
                                      InfectionData data, LivingEntity entity, boolean immediate) {
        for (String tag : definition.tags()) {
            if (tag.startsWith("suppressible:")
                    && SymptomSuppression.isSuppressed(entity, tag.substring("suppressible:".length()))) {
                return false;
            }
        }
        boolean infectionDataChanged = false;
        List<MutationDefinition.Effect> effects = definition.effects();
        for (int index = 0; index < effects.size(); index++) {
            MutationDefinition.Effect originalEffect = effects.get(index);
            if (originalEffect.trigger() != trigger) continue;
            MutationDefinition.Effect effect = resolveEffect(
                    definition, originalEffect, index, data);
            if (effect == null) {
                cleanUpSuppressedEffect(originalEffect, definition, entity, index);
                continue;
            }
            try {
                if (!shouldRun(effect, definition, data, entity, index, immediate)) continue;
                Object before = infectionDataState(effect, definition, data, entity, index);
                executeEffect(effect, definition, data, entity, index);
                Object after = infectionDataState(effect, definition, data, entity, index);
                infectionDataChanged |= !Objects.equals(before, after);
            } catch (RuntimeException exception) {
                BioForge.LOGGER.error("Failed to execute effect {} of mutation {}: {}",
                        effect.type(), definition.id(), exception.getMessage());
            }
        }
        return infectionDataChanged;
    }

    @Nullable
    private static MutationDefinition.Effect resolveEffect(
            MutationDefinition definition, MutationDefinition.Effect original, int effectIndex,
            InfectionData data) {
        MutationDefinition.Effect transformed = original;
        Set<String> owned = data.getSymptoms().getMutations();
        if (!owned.contains(definition.id())) return transformed;
        for (MutationDefinition.Interaction interaction : definition.interactions()) {
            if (!interaction.isActive(owned)) continue;
            for (MutationDefinition.EffectModifier modifier : interaction.effectModifiers()) {
                if (!modifier.matches(original, effectIndex)) continue;
                transformed = modifier.transform(transformed);
                if (transformed == null) return null;
            }
        }
        return transformed;
    }

    private static boolean runActiveInteractionEffects(
            MutationDefinition.Trigger trigger, InfectionData data, LivingEntity entity,
            boolean immediate) {
        boolean infectionDataChanged = false;
        Set<String> owned = mutationSnapshot(data);
        for (String mutationId : owned) {
            MutationDefinition definition = MutationLoader.INSTANCE
                    .getMutation(mutationId).orElse(null);
            if (definition == null || !definition.enabled()
                    || !definition.isCompatible(data.getPathogenId())) {
                continue;
            }
            List<MutationDefinition.Interaction> interactions = definition.interactions();
            for (int interactionIndex = 0;
                 interactionIndex < interactions.size();
                 interactionIndex++) {
                MutationDefinition.Interaction interaction = interactions.get(interactionIndex);
                if (!interaction.isActive(owned)) continue;
                infectionDataChanged |= runInteractionEffects(
                        definition, interaction, interactionIndex, trigger, data, entity, immediate);
            }
        }
        return infectionDataChanged;
    }

    private static boolean runInteractionEffects(
            MutationDefinition definition, MutationDefinition.Interaction interaction,
            int interactionIndex, MutationDefinition.Trigger trigger, InfectionData data,
            LivingEntity entity, boolean immediate) {
        boolean infectionDataChanged = false;
        List<MutationDefinition.Effect> effects = interaction.effects();
        for (int effectIndex = 0; effectIndex < effects.size(); effectIndex++) {
            MutationDefinition.Effect effect = effects.get(effectIndex);
            if (effect.trigger() != trigger) continue;
            int runtimeIndex = interactionEffectIndex(interactionIndex, effectIndex);
            try {
                if (!shouldRun(effect, definition, data, entity, runtimeIndex, immediate)) continue;
                Object before = infectionDataState(effect, definition, data, entity, runtimeIndex);
                executeEffect(effect, definition, data, entity, runtimeIndex);
                Object after = infectionDataState(effect, definition, data, entity, runtimeIndex);
                infectionDataChanged |= !Objects.equals(before, after);
            } catch (RuntimeException exception) {
                BioForge.LOGGER.error(
                        "Failed to execute effect {} of mutation {} interaction {}: {}",
                        effect.type(), definition.id(), interaction.id(), exception.getMessage());
            }
        }
        return infectionDataChanged;
    }









    private static boolean reconcileInteractions(
            Set<String> previousOwned, InfectionData data, LivingEntity entity,
            boolean allowMutationActions, boolean includeSteadyActions) {
        Set<String> transitionBase = new LinkedHashSet<>(previousOwned);
        Set<String> changedByInteraction = new LinkedHashSet<>();
        boolean infectionDataChanged = false;
        boolean steadyActions = includeSteadyActions;
        int changes = 0;

        while (true) {
            Set<String> current = mutationSnapshot(data);
            LinkedHashSet<String> removals = new LinkedHashSet<>();
            LinkedHashMap<String, Boolean> grants = new LinkedHashMap<>();

            for (MutationDefinition definition : MutationLoader.INSTANCE.getAllMutations()) {
                List<MutationDefinition.Interaction> interactions = definition.interactions();
                for (int interactionIndex = 0;
                     interactionIndex < interactions.size();
                     interactionIndex++) {
                    MutationDefinition.Interaction interaction = interactions.get(interactionIndex);
                    boolean wasActive = interactionActive(definition, interaction, transitionBase);
                    boolean isActive = interactionActive(definition, interaction, current);

                    if (!wasActive && isActive) {
                        infectionDataChanged |= runInteractionEffects(
                                definition, interaction, interactionIndex,
                                MutationDefinition.Trigger.APPLY, data, entity, true);
                        infectionDataChanged |= runInteractionEffects(
                                definition, interaction, interactionIndex,
                                MutationDefinition.Trigger.CONTINUOUS, data, entity, true);
                    } else if (wasActive && !isActive) {
                        cleanUpInteractionContinuousEffects(
                                definition, interaction, interactionIndex, data, entity);
                        infectionDataChanged |= runInteractionEffects(
                                definition, interaction, interactionIndex,
                                MutationDefinition.Trigger.REMOVE, data, entity, true);
                    }

                    if (allowMutationActions
                            && isActive
                            && (!wasActive || steadyActions)) {
                        removals.addAll(interaction.removeMutations());
                        for (String grant : interaction.grantMutations()) {
                            grants.merge(grant, interaction.forceGrants(), Boolean::logicalOr);
                        }
                    }
                }
            }

            if (!allowMutationActions || (removals.isEmpty() && grants.isEmpty())) break;



            for (String mutationId : removals) {
                if (changes >= MAX_INTERACTION_MUTATION_CHANGES) break;
                if (!changedByInteraction.add(mutationId)) continue;
                grants.remove(mutationId);
                if (data.getSymptoms().hasMutation(mutationId)) {
                    removeMutationInternal(data, entity, mutationId);
                    infectionDataChanged = true;
                    changes++;
                }
            }

            for (Map.Entry<String, Boolean> grant : grants.entrySet()) {
                if (changes >= MAX_INTERACTION_MUTATION_CHANGES) break;
                String mutationId = grant.getKey();
                if (!changedByInteraction.add(mutationId)
                        || data.getSymptoms().hasMutation(mutationId)) {
                    continue;
                }
                MutationDefinition granted =
                        MutationLoader.INSTANCE.getMutation(mutationId).orElse(null);
                if (granted == null) continue;
                ApplyResult validation = validateApplication(granted, data, grant.getValue());
                if (validation != ApplyResult.APPLIED || !validateEffects(granted)) continue;
                data.getSymptoms().addMutation(granted.id());
                runEffects(granted, MutationDefinition.Trigger.APPLY, data, entity, true);
                runEffects(granted, MutationDefinition.Trigger.CONTINUOUS, data, entity, true);
                infectionDataChanged = true;
                changes++;
            }

            Set<String> afterActions = mutationSnapshot(data);
            if (current.equals(afterActions)) break;
            transitionBase = current;
            steadyActions = false;

            if (changes >= MAX_INTERACTION_MUTATION_CHANGES) {
                BioForge.LOGGER.warn(
                        "Stopped mutation interaction reconciliation after {} mutation changes on {}",
                        changes, entity.getScoreboardName());
                break;
            }
        }
        return infectionDataChanged;
    }

    private static boolean interactionActive(
            MutationDefinition definition, MutationDefinition.Interaction interaction,
            Set<String> ownedMutations) {
        return ownedMutations.contains(definition.id())
                && interaction.isActive(ownedMutations);
    }

    private static void removeMutationInternal(
            InfectionData data, LivingEntity entity, String mutationId) {
        MutationDefinition definition = MutationLoader.INSTANCE.getMutation(mutationId).orElse(null);
        String storedId = definition == null ? mutationId : definition.id();
        if (!data.getSymptoms().hasMutation(storedId)) return;
        if (definition != null) {
            cleanUpContinuousEffects(definition, data, entity);
            data.getSymptoms().removeMutation(definition.id());
            runEffects(definition, MutationDefinition.Trigger.REMOVE, data, entity, true);
        } else {

            data.getSymptoms().removeMutation(storedId);
        }
    }

    private static Set<String> mutationSnapshot(InfectionData data) {
        return new LinkedHashSet<>(data.getSymptoms().getMutations());
    }

    private static int interactionEffectIndex(int interactionIndex, int effectIndex) {
        return INTERACTION_EFFECT_INDEX_BASE
                + interactionIndex * INTERACTION_EFFECT_INDEX_STRIDE
                + effectIndex;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Nullable
    private static Object infectionDataState(MutationDefinition.Effect effect,
                                             MutationDefinition definition, InfectionData data,
                                             LivingEntity entity, int effectIndex) {
        return switch (effect.type()) {
            case "modify_symptom", "set_symptom" -> {
                SymptomKey<?> key = BioForgeSymptoms.getAllSymptomKeys().get(effect.target());
                yield key == null ? null : data.getSymptoms().get((SymptomKey) key);
            }
            case "add_infection_type", "remove_infection_type" -> {
                ResourceLocation type = parseTransmissionId(effect.target());
                yield type == null ? null : data.getTransmissionIds().contains(type);
            }
            default -> {
                try {
                    yield BioForgeBehaviorRegistry.mutationEffect(BioForgeIds.parse(effect.type()))
                            .map(handler -> handler.state(new MutationEffectContext(
                                    effect, definition, data, entity, effectIndex)))
                            .orElse(null);
                } catch (IllegalArgumentException ignored) {
                    yield null;
                }
            }
        };
    }

    private static boolean shouldRun(MutationDefinition.Effect effect, MutationDefinition definition,
                                     InfectionData data, LivingEntity entity, int index, boolean immediate) {
        if (!immediate && effect.trigger() == MutationDefinition.Trigger.CONTINUOUS) {
            int interval = Math.max(1, effect.intValue("interval", defaultInterval(effect.type())));
            int offset = Math.floorMod(31 * definition.id().hashCode() + index, interval);
            if (Math.floorMod(entity.tickCount, interval) != offset) return false;
        }
        float chance = Math.max(0.0f, Math.min(1.0f, effect.floatValue("chance", 1.0f)));
        if (chance < 1.0f && entity.getRandom().nextFloat() >= chance) return false;

        float healthRatio = entity.getMaxHealth() <= 0.0f ? 0.0f : entity.getHealth() / entity.getMaxHealth();
        if (healthRatio < effect.floatValue("min_health_ratio", 0.0f)) return false;
        if (healthRatio > effect.floatValue("max_health_ratio", 1.0f)) return false;
        if (entity.getHealth() < effect.floatValue("min_health", 0.0f)) return false;
        if (entity.getHealth() > effect.floatValue("max_health", Float.MAX_VALUE)) return false;

        if (effect.has("on_fire") && entity.isOnFire() != effect.booleanValue("on_fire", false)) return false;
        if (effect.has("in_water") && entity.isInWater() != effect.booleanValue("in_water", false)) return false;
        if (effect.has("is_baby") && entity.isBaby() != effect.booleanValue("is_baby", false)) return false;

        String dimension = effect.stringValue("dimension", "");
        if (!dimension.isEmpty() && !entity.level().dimension().location().toString().equals(dimension)) return false;
        String entityType = effect.stringValue("entity_type", "");
        ResourceLocation actualType = EntityType.getKey(entity.getType());
        if (!entityType.isEmpty() && !actualType.toString().equals(entityType)) return false;

        String requiredType = effect.stringValue("requires_infection_type", "");
        ResourceLocation parsedType = requiredType.isEmpty() ? null : parseTransmissionId(requiredType);
        if (!requiredType.isEmpty() && (parsedType == null || !data.getTransmissionIds().contains(parsedType))) {
            return false;
        }
        String requiredMutation = effect.stringValue("requires_mutation", "").toLowerCase(Locale.ROOT);
        if (!requiredMutation.isEmpty() && !data.getSymptoms().hasMutation(requiredMutation)) return false;
        String absentMutation = effect.stringValue("without_mutation", "").toLowerCase(Locale.ROOT);
        if (!absentMutation.isEmpty() && data.getSymptoms().hasMutation(absentMutation)) return false;

        String difficulty = effect.stringValue("difficulty", "");
        if (!difficulty.isEmpty()) {
            Difficulty current = entity.level().getDifficulty();
            if (!current.getKey().equalsIgnoreCase(difficulty)) return false;
        }
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void executeEffect(MutationDefinition.Effect effect, MutationDefinition definition,
                                      InfectionData data, LivingEntity entity, int effectIndex) {
        String target = effect.target();
        float value = effect.floatValue("value", 1.0f);
        Level level = entity.level();

        switch (effect.type()) {
            case "modify_symptom", "set_symptom" -> {
                SymptomKey<?> key = BioForgeSymptoms.getAllSymptomKeys().get(target);
                if (key == null) return;
                Class<?> type = key.getType();
                String operation = effect.operation();
                if (type == Float.class) {
                    float current = data.getSymptom((SymptomKey<Float>) key);
                    float result = applyNumberOperation(current, value, operation, effect);
                    data.setSymptom((SymptomKey<Float>) key, result);
                } else if (type == Integer.class) {
                    int current = data.getSymptom((SymptomKey<Integer>) key);
                    int result = Math.round(applyNumberOperation(current, value, operation, effect));
                    data.setSymptom((SymptomKey<Integer>) key, result);
                } else if (type == Boolean.class) {
                    boolean current = data.getSymptom((SymptomKey<Boolean>) key);
                    boolean configured = effect.booleanValue("value", value > 0.5f);
                    boolean result = switch (operation) {
                        case "toggle" -> !current;
                        case "and" -> current && configured;
                        case "or" -> current || configured;
                        default -> configured;
                    };
                    data.setSymptom((SymptomKey<Boolean>) key, result);
                } else if (type.isEnum()) {
                    String enumValue = effect.stringValue("value", "");
                    if (!enumValue.isEmpty()) {
                        try {
                            Object parsed = Enum.valueOf((Class<Enum>) type, enumValue.toUpperCase(Locale.ROOT));
                            data.getSymptoms().set((SymptomKey) key, parsed);
                        } catch (IllegalArgumentException ignored) {
                            BioForge.LOGGER.warn("Invalid enum value {} for symptom {}", enumValue, target);
                        }
                    }
                }
            }
            case "add_infection_type" -> {
                ResourceLocation type = parseTransmissionId(target);
                if (type != null) data.addTransmissionId(type);
            }
            case "remove_infection_type" -> {
                ResourceLocation type = parseTransmissionId(target);
                if (type != null) data.removeTransmissionId(type);
            }
            case "potion_effect" -> applyPotionEffect(effect, entity);
            case "spawn_particle" -> {
                if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                    ResourceLocation particleId = ResourceLocation.tryParse(target);
                    ParticleType<?> particle = particleId == null
                            ? null : BuiltInRegistries.PARTICLE_TYPE.get(particleId);
                    if (particle instanceof ParticleOptions particleOptions) {
                        int count = Math.max(0, Math.min(256,
                                effect.intValue("count", Math.max(1, Math.round(value)))));
                        double spread = Math.max(0.0, effect.doubleValue("spread", 0.5));
                        double spreadX = Math.max(0.0, effect.doubleValue("spread_x", spread));
                        double spreadY = Math.max(0.0, effect.doubleValue("spread_y", spread));
                        double spreadZ = Math.max(0.0, effect.doubleValue("spread_z", spread));
                        double speed = Math.max(0.0, effect.doubleValue("speed", 0.1));
                        double offsetY = effect.doubleValue("offset_y", 0.5);
                        double forwardOffset = effect.doubleValue("forward_offset", 0.0);
                        double forwardSpeed = Math.max(0.0,
                                effect.doubleValue("forward_speed", 0.0));
                        net.minecraft.world.phys.Vec3 look = entity.getLookAngle();
                        double originX = entity.getX() + look.x * forwardOffset;
                        double originY = entity.getY() + offsetY + look.y * forwardOffset;
                        double originZ = entity.getZ() + look.z * forwardOffset;
                        if (forwardSpeed > 0.0 && count > 0) {
                            for (int particleIndex = 0; particleIndex < count; particleIndex++) {
                                double particleX = originX
                                        + (entity.getRandom().nextDouble() - 0.5) * spreadX * 2.0;
                                double particleY = originY
                                        + (entity.getRandom().nextDouble() - 0.5) * spreadY * 2.0;
                                double particleZ = originZ
                                        + (entity.getRandom().nextDouble() - 0.5) * spreadZ * 2.0;
                                serverLevel.sendParticles(particleOptions,
                                        particleX, particleY, particleZ, 0,
                                        look.x, look.y, look.z, forwardSpeed);
                            }
                        } else {
                            serverLevel.sendParticles(particleOptions,
                                    originX, originY, originZ,
                                    count, spreadX, spreadY, spreadZ, speed);
                        }
                    }
                }
            }
            case "attribute_modifier" -> applyAttributeModifier(effect, definition, entity, effectIndex);
            case "damage" -> {
                float amount = Math.max(0.0f, Math.min(2048.0f, value));
                if (amount > 0.0f) {
                    switch (target.toLowerCase(Locale.ROOT)) {
                        case "magic" -> entity.hurt(entity.damageSources().magic(), amount);
                        case "wither" -> entity.hurt(entity.damageSources().wither(), amount);
                        case "on_fire" -> entity.hurt(entity.damageSources().onFire(), amount);
                        case "drown" -> entity.hurt(entity.damageSources().drown(), amount);
                        default -> entity.hurt(entity.damageSources().generic(), amount);
                    }
                }
            }
            case "heal" -> entity.heal(Math.max(0.0f, Math.min(2048.0f, value)));
            case "exhaustion" -> {
                if (entity instanceof Player player) {
                    player.causeFoodExhaustion(Math.max(0.0f, Math.min(40.0f, value)));
                }
            }
            case "ignite" -> {
                int ticks = Math.max(0, Math.min(72000,
                        effect.intValue("ticks", Math.round(value * 20.0f))));
                if (ticks > entity.getRemainingFireTicks()) entity.setRemainingFireTicks(ticks);
            }
            case "play_sound" -> {
                ResourceLocation soundId = ResourceLocation.tryParse(target);
                SoundEvent sound = soundId == null ? null
                        : BuiltInRegistries.SOUND_EVENT.getOptional(soundId).orElse(null);
                if (sound != null && !level.isClientSide()) {
                    float volume = Math.max(0.0f, Math.min(16.0f,
                            effect.floatValue("volume", 1.0f)));
                    volume *= symptomSoundMultiplier(entity, soundId);
                    float pitch = Math.max(0.01f, Math.min(2.0f,
                            effect.floatValue("pitch", 1.0f)));
                    level.playSound(null, entity.blockPosition(), sound,
                            parseSoundSource(effect.stringValue("sound_source", "hostile")), volume, pitch);
                    String gameEventName = effect.stringValue("game_event", "");
                    if (!gameEventName.isBlank()) {
                        ResourceLocation gameEventId = ResourceLocation.tryParse(gameEventName);
                        Holder<GameEvent> gameEvent = gameEventId == null ? null
                                : BuiltInRegistries.GAME_EVENT.getHolder(gameEventId).orElse(null);
                        if (gameEvent != null
                                && shouldEmitSymptomVibration(entity, soundId)) {
                            level.gameEvent(entity, gameEvent, entity.blockPosition());
                        }
                    }
                }
            }
            default -> BioForgeBehaviorRegistry.mutationEffect(BioForgeIds.parse(effect.type()))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown mutation effect type: " + effect.type()))
                    .execute(new MutationEffectContext(effect, definition, data, entity, effectIndex));
        }
    }

    private static float symptomSoundMultiplier(LivingEntity entity,
                                                @Nullable ResourceLocation soundId) {
        if (!isMuffledSymptomSound(soundId)) return 1.0F;
        if (ProtectiveEquipment.hasFullHazcureSuit(entity)) return 0.12F;
        return ProtectiveEquipment.hasEquipped(
                entity, net.jenkimods.bioforge.BioForgeTags.REDUCES_INCOMING_AIRBORNE)
                ? 0.35F : 1.0F;
    }

    private static boolean shouldEmitSymptomVibration(
            LivingEntity entity, @Nullable ResourceLocation soundId) {
        if (!isMuffledSymptomSound(soundId)) return true;
        if (ProtectiveEquipment.hasFullHazcureSuit(entity)) return false;
        return !ProtectiveEquipment.hasEquipped(
                entity, net.jenkimods.bioforge.BioForgeTags.REDUCES_INCOMING_AIRBORNE)
                || entity.getRandom().nextFloat() < 0.25F;
    }

    private static boolean isMuffledSymptomSound(@Nullable ResourceLocation soundId) {
        if (soundId == null) return false;
        String path = soundId.getPath();
        return path.equals("symptom.cough") || path.equals("symptom.sneeze")
                || path.equals("entity.player.breath");
    }

    private static float applyNumberOperation(float current, float value, String operation,
                                              MutationDefinition.Effect effect) {
        float result = switch (operation) {
            case "add" -> current + value;
            case "multiply" -> current * value;
            case "min" -> Math.min(current, value);
            case "max" -> Math.max(current, value);
            case "clamp" -> current;
            default -> value;
        };
        float minimum = effect.floatValue("min", -Float.MAX_VALUE);
        float maximum = effect.floatValue("max", Float.MAX_VALUE);
        if (minimum > maximum) {
            float swap = minimum;
            minimum = maximum;
            maximum = swap;
        }
        return Math.max(minimum, Math.min(maximum, result));
    }

    private static void applyPotionEffect(MutationDefinition.Effect effect, LivingEntity entity) {
        ResourceLocation potionId = ResourceLocation.tryParse(effect.target());
        Holder<MobEffect> potion = potionId == null ? null
                : BuiltInRegistries.MOB_EFFECT.getHolder(potionId).orElse(null);
        if (potion == null) return;
        int interval = Math.max(1, effect.intValue("interval", defaultInterval(effect.type())));
        int duration = Math.max(1, effect.intValue("duration", Math.max(220, interval + 40)));
        int legacyAmplifier = Math.max(0, Math.round(effect.floatValue("value", 1.0f)) - 1);
        int amplifier = Math.max(0, Math.min(255, effect.intValue("amplifier", legacyAmplifier)));
        boolean ambient = effect.booleanValue("ambient", false);
        boolean particles = effect.booleanValue("show_particles", true);
        boolean icon = effect.booleanValue("show_icon", true);

        MobEffectInstance existing = entity.getEffect(potion);
        if (existing == null || existing.getAmplifier() < amplifier
                || (existing.getAmplifier() == amplifier && existing.getDuration() <= interval + 20)) {
            entity.addEffect(new MobEffectInstance(potion, duration, amplifier, ambient, particles, icon));
        }
    }

    private static void applyAttributeModifier(MutationDefinition.Effect effect,
                                               MutationDefinition definition,
                                               LivingEntity entity, int effectIndex) {
        ResourceLocation attributeId = ResourceLocation.tryParse(effect.target());
        Holder<Attribute> attribute = attributeId == null ? null
                : BuiltInRegistries.ATTRIBUTE.getHolder(attributeId).orElse(null);
        AttributeInstance instance = attribute == null ? null : entity.getAttribute(attribute);
        if (instance == null) return;

        ResourceLocation modifierId = modifierId(definition.id(), effectIndex);
        AttributeModifier.Operation operation = switch (effect.operation()) {
            case "multiply_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "multiply_total", "multiply" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_VALUE;
        };
        double amount = effect.doubleValue("amount", effect.doubleValue("value", 0.0));
        AttributeModifier existing = instance.getModifier(modifierId);
        if (existing != null
                && (existing.amount() != amount || existing.operation() != operation)) {
            instance.removeModifier(modifierId);
            existing = null;
        }
        if (existing == null) {
            instance.addTransientModifier(new AttributeModifier(
                    modifierId, amount, operation));
        }
    }

    private static void cleanUpContinuousEffects(MutationDefinition definition,
                                                 InfectionData data, LivingEntity entity) {
        List<MutationDefinition.Effect> effects = definition.effects();
        for (int index = 0; index < effects.size(); index++) {
            MutationDefinition.Effect effect = effects.get(index);
            if (effect.trigger() != MutationDefinition.Trigger.CONTINUOUS) continue;
            cleanUpContinuousEffect(effect, definition, data, entity, index, false);
        }
        List<MutationDefinition.Interaction> interactions = definition.interactions();
        Set<String> owned = mutationSnapshot(data);
        for (int interactionIndex = 0;
             interactionIndex < interactions.size();
             interactionIndex++) {
            MutationDefinition.Interaction interaction = interactions.get(interactionIndex);
            if (interaction.isActive(owned)) {
                cleanUpInteractionContinuousEffects(
                        definition, interaction, interactionIndex, data, entity);
            }
        }
    }

    private static void cleanUpInteractionContinuousEffects(
            MutationDefinition definition, MutationDefinition.Interaction interaction,
            int interactionIndex, InfectionData data, LivingEntity entity) {
        List<MutationDefinition.Effect> effects = interaction.effects();
        for (int effectIndex = 0; effectIndex < effects.size(); effectIndex++) {
            MutationDefinition.Effect effect = effects.get(effectIndex);
            if (effect.trigger() != MutationDefinition.Trigger.CONTINUOUS) continue;
            cleanUpContinuousEffect(effect, definition, data, entity,
                    interactionEffectIndex(interactionIndex, effectIndex), false);
        }
    }

    private static void cleanUpSuppressedEffect(
            MutationDefinition.Effect effect, MutationDefinition definition,
            LivingEntity entity, int effectIndex) {
        if (effect.trigger() != MutationDefinition.Trigger.CONTINUOUS) return;



        cleanUpContinuousEffect(effect, definition, null, entity, effectIndex, true);
    }

    private static void cleanUpContinuousEffect(
            MutationDefinition.Effect effect, MutationDefinition definition,
            @Nullable InfectionData data, LivingEntity entity, int effectIndex,
            boolean suppressed) {
        if ("attribute_modifier".equals(effect.type())) {
            ResourceLocation attributeId = ResourceLocation.tryParse(effect.target());
            Holder<Attribute> attribute =
                    attributeId == null ? null
                            : BuiltInRegistries.ATTRIBUTE.getHolder(attributeId).orElse(null);
            AttributeInstance instance = attribute == null ? null : entity.getAttribute(attribute);
            if (instance != null) {
                instance.removeModifier(modifierId(definition.id(), effectIndex));
            }
        } else if (!suppressed
                && data != null
                && "potion_effect".equals(effect.type())
                && effect.booleanValue("remove_on_mutation_end", false)
                && !anotherMutationUsesPotion(data, definition.id(), effect.target())) {
            ResourceLocation potionId = ResourceLocation.tryParse(effect.target());
            Holder<MobEffect> potion =
                    potionId == null ? null
                            : BuiltInRegistries.MOB_EFFECT.getHolder(potionId).orElse(null);
            if (potion != null) entity.removeEffect(potion);
        }
    }

    private static boolean anotherMutationUsesPotion(InfectionData data, String removedId, String potionId) {
        for (String id : data.getSymptoms().getMutations()) {
            if (id.equals(removedId)) continue;
            MutationDefinition other = MutationLoader.INSTANCE.getMutation(id).orElse(null);
            if (other == null) continue;
            for (MutationDefinition.Effect effect : other.effects()) {
                if (effect.trigger() == MutationDefinition.Trigger.CONTINUOUS
                        && "potion_effect".equals(effect.type())
                        && effect.target().equals(potionId)) return true;
            }
        }
        return false;
    }

    private static ResourceLocation modifierId(String mutationId, int effectIndex) {
        String path = mutationId.toLowerCase(Locale.ROOT)
                .replace(':', '/')
                .replaceAll("[^a-z0-9/._-]", "_");
        return ResourceLocation.fromNamespaceAndPath(
                BioForge.MODID, ATTRIBUTE_MODIFIER_PATH_PREFIX + path + "/" + effectIndex);
    }







    private static boolean resetRuntimeEffectsAfterReload(LivingEntity entity) {
        long generation = MutationLoader.INSTANCE.generation();
        synchronized (RUNTIME_DEFINITION_GENERATIONS) {
            Long seen = RUNTIME_DEFINITION_GENERATIONS.get(entity);
            if (seen != null && seen == generation) return false;

            for (Attribute attribute : BuiltInRegistries.ATTRIBUTE) {
                AttributeInstance instance = entity.getAttribute(
                        BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute));
                if (instance == null) continue;
                for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
                    if (modifier.id().getNamespace().equals(BioForge.MODID)
                            && modifier.id().getPath().startsWith(ATTRIBUTE_MODIFIER_PATH_PREFIX)) {
                        instance.removeModifier(modifier);
                    }
                }
            }
            RUNTIME_DEFINITION_GENERATIONS.put(entity, generation);
            return true;
        }
    }

    private static int defaultInterval(String effectType) {
        int builtIn = switch (effectType) {
            case "spawn_particle" -> 20;
            case "potion_effect", "attribute_modifier" -> 100;
            default -> -1;
        };
        if (builtIn > 0) return builtIn;
        try {
            return Math.max(1, BioForgeBehaviorRegistry.mutationEffect(BioForgeIds.parse(effectType))
                    .map(net.jenkimods.bioforge.api.behavior.MutationEffectHandler::defaultInterval)
                    .orElse(20));
        } catch (IllegalArgumentException ignored) {
            return 20;
        }
    }

    @Nullable
    private static ResourceLocation parseTransmissionId(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            ResourceLocation id = BioForgeIds.parse(name);
            return BioForgeDefinitionManager.TRANSMISSIONS.get(id).isPresent()
                    ? BioForgeDefinitionManager.TRANSMISSIONS.canonicalId(id) : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static SoundSource parseSoundSource(String name) {
        try {
            return SoundSource.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return SoundSource.HOSTILE;
        }
    }

    public static List<MutationDefinition> getAvailableMutations(InfectionData data) {
        if (data == null || !data.isInfected() || data.getPathogenId() == null) return List.of();
        List<MutationDefinition> result = new ArrayList<>();
        for (MutationDefinition definition :
                MutationLoader.INSTANCE.getMutationsForPathogen(data.getPathogenId())) {
            if (definition.enabled() && definition.weight() > 0
                    && validateApplication(definition, data, false) == ApplyResult.APPLIED
                    && validateEffects(definition)) {
                result.add(definition);
            }
        }
        return List.copyOf(result);
    }

    @Nullable
    public static String getRandomMutationId(InfectionData data, Random random) {
        MutationDefinition definition = MutationLoader.INSTANCE.chooseWeighted(
                getAvailableMutations(data), random);
        return definition == null ? null : definition.id();
    }




    @Nullable
    public static String getRandomMutationId(PathogenType pathogen, Random random) {
        MutationDefinition definition =
                MutationLoader.INSTANCE.getRandomMutationForPathogen(pathogen, random);
        return definition != null ? definition.id() : null;
    }

    public static boolean hasMutation(InfectionData data, String mutationId) {
        if (data == null || mutationId == null) return false;
        String storedId = MutationLoader.INSTANCE.getMutation(mutationId)
                .map(MutationDefinition::id)
                .orElse(mutationId);
        return data.getSymptoms().hasMutation(storedId);
    }

    public static boolean hasMutationTag(InfectionData data, String tag) {
        if (data == null || tag == null || tag.isBlank()) return false;
        for (MutationDefinition definition : MutationLoader.INSTANCE.getMutationsWithTag(tag)) {
            if (data.getSymptoms().hasMutation(definition.id())) return true;
        }
        return false;
    }

    private static void mutationDataChanged(InfectionData data, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        InfectionEventHandler.syncToClient(player, data);

        InfectionStore store = InfectionStore.get(player.serverLevel());
        InfectionStore.InfectionRecord existing = store.getInfection(player.getUUID());
        if (existing == null || !existing.persistent()) return;

        Map<String, Object> symptoms = new LinkedHashMap<>();
        for (Map.Entry<String, SymptomKey<?>> entry : BioForgeSymptoms.getEnabledSymptomKeys().entrySet()) {
            symptoms.put(entry.getKey(), data.getSymptom(entry.getValue()));
        }
        store.setInfection(player.getUUID(), new InfectionStore.InfectionRecord(
                data.isInfected(),
                true,
                data.getPathogenType(),
                new ArrayList<>(data.getInfectionTypes()),
                symptoms,
                new ArrayList<>(data.getSymptoms().getMutations()),
                data.getPathogenId(), new ArrayList<>(data.getTransmissionIds()),
                data.getLifecycle().incubationTicksOverride(),
                data.getLifecycle().lifespanTicksOverride()
        ));
    }
}
