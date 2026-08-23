package net.jenkimods.bioforge.mutation;

import net.jenkimods.bioforge.config.BioForgeServerConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.api.behavior.BioForgeBehaviorRegistry;
import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.infection.InfectionType;
import net.jenkimods.bioforge.infection.PathogenType;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.infection.symptoms.SymptomKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public final class MutationLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final Set<String> SUPPORTED_EFFECT_TYPES = Set.of(
            "modify_symptom",
            "set_symptom",
            "add_infection_type",
            "remove_infection_type",
            "potion_effect",
            "spawn_particle",
            "attribute_modifier",
            "damage",
            "heal",
            "exhaustion",
            "ignite",
            "play_sound"
    );
    private static final Set<String> MODIFIABLE_EFFECT_PARAMETERS = Set.of(
            "value",
            "amount",
            "chance",
            "interval",
            "duration",
            "amplifier",
            "count",
            "spread",
            "spread_x",
            "spread_y",
            "spread_z",
            "speed",
            "offset_y",
            "forward_offset",
            "forward_speed",
            "volume",
            "pitch",
            "ticks"
    );

    public static final MutationLoader INSTANCE = new MutationLoader();

    private final Map<String, MutationDefinition> mutations = new LinkedHashMap<>();
    private final Map<PathogenType, List<MutationDefinition>> byPathogen = new EnumMap<>(PathogenType.class);
    private final Map<ResourceLocation, List<MutationDefinition>> byPathogenId = new LinkedHashMap<>();
    private final Map<String, List<MutationDefinition>> byTag = new LinkedHashMap<>();
    private final List<MutationDefinition> allMutations = new ArrayList<>();
    private final Map<String, MutationDefinition> javaMutations = new LinkedHashMap<>();
    private volatile long generation;
    private boolean javaRegistrationsFrozen;

    private MutationLoader() {
        super(GSON, "mutations");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> elements, ResourceManager manager, ProfilerFiller profiler) {
        mutations.clear();
        byPathogen.clear();
        byPathogenId.clear();
        byTag.clear();
        allMutations.clear();

        elements.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> loadDefinition(entry.getKey(), entry.getValue()));

        javaMutations.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (mutations.containsKey(entry.getKey())) {
                BioForge.LOGGER.warn("Datapack mutation {} overrides the Java addon definition", entry.getKey());
            } else {
                indexDefinition(entry.getValue());
            }
        });

        validateInteractionReferences();
        generation++;
        BioForge.LOGGER.info("Loaded {} mutation definitions ({} enabled)",
                mutations.size(), allMutations.stream().filter(MutationDefinition::enabled).count());
    }

    private void validateInteractionReferences() {
        for (MutationDefinition definition : allMutations) {
            for (MutationDefinition.Interaction interaction : definition.interactions()) {
                for (String partner : interaction.withMutations()) {
                    if (getLoadedMutation(partner).isEmpty()) {
                        BioForge.LOGGER.warn(
                                "Mutation {} interaction {} references unknown partner mutation {}",
                                definition.id(), interaction.id(), partner);
                    }
                }
                for (String grant : interaction.grantMutations()) {
                    if (getLoadedMutation(grant).isEmpty()) {
                        BioForge.LOGGER.warn(
                                "Mutation {} interaction {} cannot grant unknown mutation {}",
                                definition.id(), interaction.id(), grant);
                    }
                }
            }
        }
    }

    private void loadDefinition(ResourceLocation sourceId, JsonElement element) {
        try {
            JsonObject json = GsonHelper.convertToJsonObject(element, "mutation");
            String collectionKey = json.has("definitions") ? "definitions"
                    : (json.has("mutations") ? "mutations" : "");
            if (!collectionKey.isEmpty()) {
                JsonArray definitions = GsonHelper.getAsJsonArray(json, collectionKey);
                for (int index = 0; index < definitions.size(); index++) {
                    ResourceLocation nestedId = ResourceLocation.tryBuild(sourceId.getNamespace(),
                            sourceId.getPath() + "/" + index);
                    loadDefinition(nestedId, definitions.get(index));
                }
                return;
            }
            String id = normalizeId(GsonHelper.getAsString(json, "id", sourceId.toString()));
            if (mutations.containsKey(id)) {
                throw new IllegalArgumentException("Duplicate mutation ID '" + id + "'");
            }

            String name = GsonHelper.getAsString(json, "name", id);
            String description = GsonHelper.getAsString(json, "description", "");
            String nameKey = GsonHelper.getAsString(json, "name_key", "");
            String descriptionKey = GsonHelper.getAsString(json, "description_key", "");
            Set<ResourceLocation> pathogenIds = parsePathogenIds(json);
            String rarity = GsonHelper.getAsString(json, "rarity", "common");
            int weight = GsonHelper.getAsInt(json, "weight", defaultWeight(rarity));
            if (weight < 0) throw new IllegalArgumentException("weight cannot be negative");
            boolean enabled = GsonHelper.getAsBoolean(json, "enabled", true);
            boolean hidden = GsonHelper.getAsBoolean(json, "hidden", false);

            ResourceLocation icon = null;
            String iconPath = GsonHelper.getAsString(json, "icon", "");
            if (!iconPath.isBlank()) {
                icon = ResourceLocation.tryParse(iconPath);
                if (icon == null) throw new IllegalArgumentException("Invalid icon resource location: " + iconPath);
            }

            List<MutationDefinition.Effect> effects = parseEffects(json);
            MutationDefinition definition = new MutationDefinition.Builder()
                    .id(id)
                    .name(name)
                    .description(description)
                    .nameKey(nameKey)
                    .descriptionKey(descriptionKey)
                    .pathogenIds(pathogenIds)
                    .effects(effects)
                    .rarity(rarity)
                    .weight(weight)
                    .enabled(enabled)
                    .hidden(hidden)
                    .icon(icon)
                    .requiredMutations(readStringSet(json, "requires", "required_mutations"))
                    .conflictingMutations(readStringSet(json, "conflicts", "incompatible_mutations"))
                    .tags(readStringSet(json, "tags"))
                    .interactions(parseInteractions(json))
                    .build();

            indexDefinition(definition);
        } catch (Exception ex) {
            BioForge.LOGGER.error("Failed to load mutation {}: {}", sourceId, ex.getMessage());
        }
    }

    private void indexDefinition(MutationDefinition definition) {
        mutations.put(definition.id(), definition);
        allMutations.add(definition);
        for (PathogenType pathogen : definition.pathogens()) {
            byPathogen.computeIfAbsent(pathogen, ignored -> new ArrayList<>()).add(definition);
        }
        for (ResourceLocation pathogenId : definition.pathogenIds()) {
            byPathogenId.computeIfAbsent(pathogenId, ignored -> new ArrayList<>()).add(definition);
        }
        for (String tag : definition.tags()) {
            byTag.computeIfAbsent(tag, ignored -> new ArrayList<>()).add(definition);
        }
    }

    public synchronized void registerJava(MutationDefinition definition) {
        if (javaRegistrationsFrozen) {
            throw new IllegalStateException("BioForge mutation registry is frozen");
        }
        if (definition == null) throw new IllegalArgumentException("Mutation definition cannot be null");
        String id = normalizeId(definition.id());
        validateJavaDefinition(definition);
        if (javaMutations.putIfAbsent(id, definition) != null) {
            throw new IllegalArgumentException("Duplicate Java mutation definition '" + id + "'");
        }
        if (generation > 0 && !mutations.containsKey(id)) {
            indexDefinition(definition);
            generation++;
        }
    }

    public synchronized void freezeJavaRegistrations() {
        javaRegistrationsFrozen = true;
    }

    private static void validateJavaDefinition(MutationDefinition definition) {
        for (ResourceLocation pathogenId : definition.pathogenIds()) {
            if (BioForgeDefinitionManager.PATHOGENS.get(pathogenId).isEmpty()) {
                throw new IllegalArgumentException("Unknown pathogen for mutation "
                        + definition.id() + ": " + pathogenId);
            }
        }
        List<MutationDefinition.Effect> effects = new ArrayList<>(definition.effects());
        for (MutationDefinition.Interaction interaction : definition.interactions()) {
            effects.addAll(interaction.effects());
        }
        for (MutationDefinition.Effect effect : effects) {
            if (!isSupportedEffectType(effect.type())) {
                throw new IllegalArgumentException("Unsupported mutation effect type: " + effect.type());
            }
            validateEffect(effect.parameters(), effect.type(), effect.target(),
                    effect.operation(), effect.trigger());
        }
    }

    private static Set<ResourceLocation> parsePathogenIds(JsonObject json) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        if (json.has("pathogens")) {
            JsonElement element = json.get("pathogens");
            if (element.isJsonArray()) {
                for (JsonElement value : element.getAsJsonArray()) {
                    result.add(parsePathogenId(value.getAsString()));
                }
            } else {
                result.add(parsePathogenId(element.getAsString()));
            }
        } else {
            result.add(parsePathogenId(GsonHelper.getAsString(json, "pathogen", "UNIVERSAL")));
        }
        if (result.isEmpty()) result.add(BioForgeIds.pathogen(PathogenType.UNIVERSAL));
        return result;
    }

    private static ResourceLocation parsePathogenId(String name) {
        try {
            ResourceLocation id = BioForgeIds.parse(name);
            if (BioForgeDefinitionManager.PATHOGENS.get(id).isEmpty()) {
                throw new IllegalArgumentException("Unknown pathogen type: " + name);
            }
            return BioForgeDefinitionManager.PATHOGENS.canonicalId(id);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown pathogen type: " + name);
        }
    }

    private static List<MutationDefinition.Effect> parseEffects(JsonObject json) {
        List<MutationDefinition.Effect> effects = new ArrayList<>();
        appendEffectArray(json, "effects", null, effects);
        appendEffectArray(json, "continuous_effects", MutationDefinition.Trigger.CONTINUOUS, effects);
        appendEffectArray(json, "on_remove", MutationDefinition.Trigger.REMOVE, effects);


        if (effects.isEmpty() && json.has("effect_type")) {
            JsonObject legacy = new JsonObject();
            legacy.addProperty("type", GsonHelper.getAsString(json, "effect_type"));
            legacy.addProperty("target", GsonHelper.getAsString(json, "effect_target", ""));
            if (json.has("effect_value")) {
                legacy.add("value", json.get("effect_value").deepCopy());
            } else {
                legacy.addProperty("value", 1.0f);
            }
            effects.add(parseEffect(legacy, null));
        }
        return effects;
    }

    private static List<MutationDefinition.Interaction> parseInteractions(JsonObject json) {
        if (!json.has("interactions")) return List.of();
        JsonElement element = json.get("interactions");
        JsonArray array;
        if (element.isJsonObject()) {
            array = new JsonArray();
            array.add(element);
        } else {
            array = GsonHelper.convertToJsonArray(element, "interactions");
        }
        if (array.size() > 32) {
            throw new IllegalArgumentException("interactions cannot contain more than 32 entries");
        }

        List<MutationDefinition.Interaction> interactions = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (int index = 0; index < array.size(); index++) {
            JsonObject interaction = GsonHelper.convertToJsonObject(
                    array.get(index), "interactions entry");
            String id = normalizeInteractionId(GsonHelper.getAsString(
                    interaction, "id", "interaction_" + index));
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate interaction ID '" + id + "'");
            }

            Set<String> partners = readRequiredStringSet(interaction, "with");
            if (partners.size() > 16) {
                throw new IllegalArgumentException(
                        "interaction " + id + " cannot reference more than 16 partner mutations");
            }
            String mode = GsonHelper.getAsString(interaction, "mode", "all")
                    .trim().toLowerCase(Locale.ROOT);
            if (!Set.of("all", "any").contains(mode)) {
                throw new IllegalArgumentException(
                        "interaction " + id + " mode must be 'all' or 'any'");
            }

            List<MutationDefinition.EffectModifier> modifiers =
                    parseEffectModifiers(interaction, id);
            List<MutationDefinition.Effect> effects = parseEffects(interaction);
            Set<String> grants = readStringSet(
                    interaction, "grant_mutations", "grant", "apply_mutations");
            Set<String> removals = readStringSet(
                    interaction, "remove_mutations", "remove");
            if (grants.size() > 16 || removals.size() > 16) {
                throw new IllegalArgumentException(
                        "interaction " + id + " cannot grant or remove more than 16 mutations");
            }
            Set<String> overlap = new LinkedHashSet<>(grants);
            overlap.retainAll(removals);
            if (!overlap.isEmpty()) {
                throw new IllegalArgumentException(
                        "interaction " + id + " both grants and removes " + overlap);
            }
            if (modifiers.isEmpty() && effects.isEmpty() && grants.isEmpty() && removals.isEmpty()) {
                throw new IllegalArgumentException(
                        "interaction " + id + " does not define an action");
            }

            interactions.add(new MutationDefinition.Interaction(
                    id,
                    partners,
                    "all".equals(mode),
                    modifiers,
                    effects,
                    grants,
                    removals,
                    GsonHelper.getAsBoolean(interaction, "force_grants", false)
            ));
        }
        return List.copyOf(interactions);
    }

    private static List<MutationDefinition.EffectModifier> parseEffectModifiers(
            JsonObject interaction, String interactionId) {
        if (!interaction.has("effect_modifiers")) return List.of();
        JsonElement element = interaction.get("effect_modifiers");
        JsonArray array;
        if (element.isJsonObject()) {
            array = new JsonArray();
            array.add(element);
        } else {
            array = GsonHelper.convertToJsonArray(element, "effect_modifiers");
        }
        if (array.size() > 64) {
            throw new IllegalArgumentException(
                    "interaction " + interactionId + " cannot contain more than 64 effect modifiers");
        }

        List<MutationDefinition.EffectModifier> modifiers = new ArrayList<>();
        for (JsonElement entry : array) {
            JsonObject modifier = GsonHelper.convertToJsonObject(entry, "effect modifier");
            JsonObject match = modifier.has("match")
                    ? GsonHelper.convertToJsonObject(modifier.get("match"), "effect modifier match")
                    : modifier;
            String type = GsonHelper.getAsString(match, "type", "").trim().toLowerCase(Locale.ROOT);
            if (!type.isEmpty() && !isSupportedEffectType(type)) {
                throw new IllegalArgumentException(
                        "Unknown effect modifier match type: " + type);
            }
            String target = GsonHelper.getAsString(match, "target", "");
            int effectIndex = GsonHelper.getAsInt(match, "effect_index", -1);
            if (effectIndex < -1 || effectIndex >= 64) {
                throw new IllegalArgumentException(
                        "effect_index must be between 0 and 63, or omitted");
            }

            MutationDefinition.Trigger trigger = null;
            String triggerName = GsonHelper.getAsString(match, "trigger", "");
            if (!triggerName.isBlank()) {
                trigger = MutationDefinition.Trigger.fromName(triggerName, type);
                if (trigger == MutationDefinition.Trigger.APPLY) {
                    throw new IllegalArgumentException(
                            "effect modifiers cannot target one-shot apply effects; "
                                    + "use interaction effects instead");
                }
            }

            boolean suppress = GsonHelper.getAsBoolean(modifier, "suppress", false);
            String parameter = GsonHelper.getAsString(modifier, "parameter", "")
                    .trim().toLowerCase(Locale.ROOT);
            if (!suppress && parameter.isEmpty()) {
                throw new IllegalArgumentException(
                        "Non-suppressing effect modifier requires 'parameter'");
            }
            if (!parameter.isEmpty() && !MODIFIABLE_EFFECT_PARAMETERS.contains(parameter)) {
                throw new IllegalArgumentException(
                        "Effect parameter cannot be modified safely: " + parameter);
            }

            double multiplier = getFiniteDouble(
                    modifier, modifier.has("multiply") ? "multiply" : "scale", 1.0);
            double offset = getFiniteDouble(
                    modifier, modifier.has("add") ? "add" : "offset", 0.0);
            double minimum = getFiniteDouble(modifier, "min", -Double.MAX_VALUE);
            double maximum = getFiniteDouble(modifier, "max", Double.MAX_VALUE);
            if (minimum > maximum) {
                throw new IllegalArgumentException(
                        "effect modifier min cannot be greater than max");
            }

            modifiers.add(new MutationDefinition.EffectModifier(
                    type, target, trigger, effectIndex, parameter,
                    multiplier, offset, minimum, maximum, suppress));
        }
        return List.copyOf(modifiers);
    }

    private static void appendEffectArray(JsonObject json, String key,
                                          @Nullable MutationDefinition.Trigger forcedTrigger,
                                          List<MutationDefinition.Effect> output) {
        if (!json.has(key)) return;
        JsonElement element = json.get(key);
        if (element.isJsonObject()) {
            output.add(parseEffect(element.getAsJsonObject(), forcedTrigger));
            return;
        }
        JsonArray array = GsonHelper.convertToJsonArray(element, key);
        if (array.size() > 64) {
            throw new IllegalArgumentException(key + " cannot contain more than 64 effects");
        }
        for (JsonElement effect : array) {
            output.add(parseEffect(GsonHelper.convertToJsonObject(effect, key + " entry"), forcedTrigger));
        }
    }

    private static MutationDefinition.Effect parseEffect(
            JsonObject json, @Nullable MutationDefinition.Trigger forcedTrigger) {
        String type = GsonHelper.getAsString(json, "type",
                GsonHelper.getAsString(json, "effect_type", "")).trim().toLowerCase(Locale.ROOT);
        if (type.isEmpty()) throw new IllegalArgumentException("Mutation effect is missing 'type'");
        if (!isSupportedEffectType(type)) {
            throw new IllegalArgumentException("Unsupported mutation effect type: " + type);
        }

        String target = GsonHelper.getAsString(json, "target",
                GsonHelper.getAsString(json, "effect_target", ""));
        String operation = GsonHelper.getAsString(json, "operation", "");
        MutationDefinition.Trigger trigger = forcedTrigger != null
                ? forcedTrigger
                : MutationDefinition.Trigger.fromName(GsonHelper.getAsString(json, "trigger", ""), type);

        validateEffect(json, type, target, operation, trigger);
        return new MutationDefinition.Effect(type, target, operation, trigger, json);
    }

    private static void validateEffect(JsonObject json, String type, String target, String operation,
                                       MutationDefinition.Trigger trigger) {
        validateFiniteParameters(json);
        if (json.has("conditions") && json.get("conditions").isJsonObject()) {
            validateFiniteParameters(json.getAsJsonObject("conditions"));
        }

        if (json.has("chance")) {
            double chance = GsonHelper.getAsDouble(json, "chance");
            if (chance < 0.0 || chance > 1.0) {
                throw new IllegalArgumentException("effect chance must be between 0 and 1");
            }
        }
        if (json.has("interval") && GsonHelper.getAsInt(json, "interval") < 1) {
            throw new IllegalArgumentException("effect interval must be at least 1 tick");
        }
        if (json.has("duration") && GsonHelper.getAsInt(json, "duration") < 1) {
            throw new IllegalArgumentException("potion duration must be at least 1 tick");
        }
        if (json.has("amplifier") && GsonHelper.getAsInt(json, "amplifier") < 0) {
            throw new IllegalArgumentException("potion amplifier cannot be negative");
        }

        if (!SUPPORTED_EFFECT_TYPES.contains(type)) {
            BioForgeBehaviorRegistry.mutationEffect(BioForgeIds.parse(type))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Missing mutation effect handler: " + type))
                    .validate(json.deepCopy());
            return;
        }

        String normalizedOperation = effectiveOperation(type, operation);
        validateOperationAndValue(json, type, target, operation, normalizedOperation);
        if (trigger == MutationDefinition.Trigger.CONTINUOUS
                && ("modify_symptom".equals(type) || "set_symptom".equals(type))
                && ("add".equals(normalizedOperation) || "multiply".equals(normalizedOperation))
                && !GsonHelper.getAsBoolean(json, "allow_accumulation", false)) {
            throw new IllegalArgumentException(
                    "continuous add/multiply symptom effects require allow_accumulation=true");
        }
    }

    private static void validateFiniteParameters(JsonObject json) {
        for (String numericKey : List.of(
                "amount", "min", "max", "chance", "spread", "spread_x",
                "spread_y", "spread_z", "speed", "offset_y", "forward_offset",
                "forward_speed", "volume", "pitch",
                "min_health", "max_health", "min_health_ratio", "max_health_ratio")) {
            if (json.has(numericKey)) {
                JsonElement element = json.get(numericKey);
                if (!element.isJsonPrimitive() || element.getAsJsonPrimitive().isBoolean()) {
                    throw new IllegalArgumentException(numericKey + " must be a number");
                }
                double value;
                try {
                    value = element.getAsDouble();
                } catch (RuntimeException exception) {
                    throw new IllegalArgumentException(numericKey + " must be a number");
                }
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException(numericKey + " must be a finite number");
                }
            }
        }
    }

    private static String effectiveOperation(String type, String operation) {
        if (operation != null && !operation.isBlank()) {
            return operation.trim().toLowerCase(Locale.ROOT);
        }
        return switch (type) {
            case "modify_symptom" -> "multiply";
            case "attribute_modifier" -> "add";
            default -> "set";
        };
    }

    private static void validateOperationAndValue(JsonObject json, String type, String target,
                                                  String declaredOperation, String operation) {
        if ("modify_symptom".equals(type) || "set_symptom".equals(type)) {
            SymptomKey<?> key = BioForgeSymptoms.getAllSymptomKeys().get(target);
            if (key == null) {
                throw new IllegalArgumentException("Unknown symptom target: " + target);
            }

            Set<String> allowedOperations;
            if (key.getType() == Boolean.class) {
                allowedOperations = Set.of("set", "toggle", "and", "or");
                if (json.has("value")) {
                    JsonElement value = json.get("value");
                    if (!value.isJsonPrimitive()) {
                        throw new IllegalArgumentException("Boolean symptom value must be true or false");
                    }
                    JsonPrimitive primitive = value.getAsJsonPrimitive();
                    if (!primitive.isBoolean()
                            && !"true".equalsIgnoreCase(primitive.getAsString())
                            && !"false".equalsIgnoreCase(primitive.getAsString())) {
                        throw new IllegalArgumentException("Boolean symptom value must be true or false");
                    }
                }
            } else if (key.getType().isEnum()) {
                allowedOperations = Set.of("set");
                if (!json.has("value") || !json.get("value").isJsonPrimitive()) {
                    throw new IllegalArgumentException("Enum symptom " + target + " requires a string value");
                }
                String value = json.get("value").getAsString();
                boolean valid = false;
                for (Object constant : key.getType().getEnumConstants()) {
                    if (((Enum<?>) constant).name().equalsIgnoreCase(value)) {
                        valid = true;
                        break;
                    }
                }
                if (!valid) {
                    throw new IllegalArgumentException(
                            "Unknown value '" + value + "' for enum symptom " + target);
                }
            } else {
                allowedOperations = Set.of("set", "add", "multiply", "min", "max", "clamp");
                validateOptionalFiniteValue(json, "value");
            }
            if (!allowedOperations.contains(operation)) {
                throw new IllegalArgumentException(
                        "Operation '" + operation + "' is not valid for symptom " + target);
            }
            return;
        }

        if ("attribute_modifier".equals(type)) {
            if (!Set.of("add", "addition", "multiply", "multiply_base", "multiply_total")
                    .contains(operation)) {
                throw new IllegalArgumentException(
                        "Unknown attribute modifier operation: " + operation);
            }
            validateOptionalFiniteValue(json, json.has("amount") ? "amount" : "value");
            return;
        }

        if (("add_infection_type".equals(type) || "remove_infection_type".equals(type))
                && !isKnownTransmission(target)) {
            throw new IllegalArgumentException("Unknown infection type: " + target);
        }

        if (declaredOperation != null && !declaredOperation.isBlank()
                && !"set".equals(operation)) {
            throw new IllegalArgumentException(
                    "Effect type " + type + " does not support operation '" + operation + "'");
        }
        if (Set.of("potion_effect", "spawn_particle", "damage", "heal", "exhaustion", "ignite")
                .contains(type)) {
            validateOptionalFiniteValue(json, "value");
        }
    }

    private static void validateOptionalFiniteValue(JsonObject json, String key) {
        if (!json.has(key)) return;
        JsonElement element = json.get(key);
        if (!element.isJsonPrimitive() || element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        double value;
        try {
            value = element.getAsDouble();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(key + " must be a finite number");
        }
    }

    @Nullable
    private static InfectionType parseInfectionType(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return InfectionType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean isSupportedEffectType(String type) {
        if (SUPPORTED_EFFECT_TYPES.contains(type)) return true;
        try {
            return BioForgeBehaviorRegistry.mutationEffect(BioForgeIds.parse(type)).isPresent();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isKnownTransmission(String value) {
        try {
            return BioForgeDefinitionManager.TRANSMISSIONS.get(BioForgeIds.parse(value)).isPresent();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static Set<String> readStringSet(JsonObject json, String... keys) {
        Set<String> result = new LinkedHashSet<>();
        for (String key : keys) {
            if (!json.has(key)) continue;
            JsonElement element = json.get(key);
            if (element.isJsonArray()) {
                for (JsonElement value : element.getAsJsonArray()) {
                    addString(result, value.getAsString());
                }
            } else {
                String raw = element.getAsString();
                for (String value : raw.split(",")) addString(result, value);
            }
            break;
        }
        return result;
    }

    private static Set<String> readRequiredStringSet(JsonObject json, String key) {
        Set<String> result = readStringSet(json, key);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Interaction requires a non-empty '" + key + "'");
        }
        return result;
    }

    private static void addString(Collection<String> values, String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.isEmpty()) values.add(normalized);
    }

    private static String normalizeId(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("Mutation ID cannot be empty");
        if (normalized.length() > 256) throw new IllegalArgumentException("Mutation ID is longer than 256 characters");
        ResourceLocation parsed = ResourceLocation.tryParse(normalized);
        if (parsed == null || normalized.indexOf(':') != normalized.lastIndexOf(':')) {
            throw new IllegalArgumentException("Mutation ID contains unsupported characters: " + id);
        }
        return normalized;
    }

    private static String normalizeInteractionId(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Interaction ID cannot be empty");
        }
        if (normalized.length() > 128
                || !normalized.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException(
                    "Interaction ID contains unsupported characters: " + id);
        }
        return normalized;
    }

    private static double getFiniteDouble(JsonObject json, String key, double fallback) {
        if (!json.has(key)) return fallback;
        JsonElement element = json.get(key);
        if (!element.isJsonPrimitive() || element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        final double value;
        try {
            value = element.getAsDouble();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(key + " must be finite");
        }
        return value;
    }

    private static int defaultWeight(String rarity) {
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "uncommon" -> 50;
            case "rare" -> 20;
            case "epic" -> 8;
            case "legendary" -> 2;
            default -> 100;
        };
    }

    public Optional<MutationDefinition> getMutation(String id) {
        return getLoadedMutation(id).filter(definition ->
                BioForgeServerConfig.isMutationEnabled(definition.id()));
    }

    private Optional<MutationDefinition> getLoadedMutation(String id) {
        if (id == null) return Optional.empty();
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        MutationDefinition exact = mutations.get(normalized);
        if (exact != null) return Optional.of(exact);


        ResourceLocation parsed = ResourceLocation.tryParse(normalized);
        if (parsed != null && "minecraft".equals(parsed.getNamespace())) {
            MutationDefinition legacy = mutations.get(parsed.getPath());
            return Optional.ofNullable(legacy);
        }
        return Optional.empty();
    }

    public List<MutationDefinition> getMutationsForPathogen(@Nullable PathogenType pathogen) {
        if (pathogen == null) return List.of();
        if (pathogen == PathogenType.UNIVERSAL) return getAllMutations();
        LinkedHashSet<MutationDefinition> result = new LinkedHashSet<>();
        result.addAll(byPathogen.getOrDefault(PathogenType.UNIVERSAL, List.of()));
        result.addAll(byPathogen.getOrDefault(pathogen, List.of()));
        return result.stream()
                .filter(definition -> BioForgeServerConfig.isMutationEnabled(definition.id()))
                .toList();
    }

    public List<MutationDefinition> getMutationsForPathogen(@Nullable ResourceLocation pathogen) {
        if (pathogen == null) return List.of();
        if (BioForgeIds.pathogen(PathogenType.UNIVERSAL).equals(pathogen)) {
            return getAllMutations();
        }
        LinkedHashSet<MutationDefinition> result = new LinkedHashSet<>();
        result.addAll(byPathogenId.getOrDefault(BioForgeIds.pathogen(PathogenType.UNIVERSAL), List.of()));
        result.addAll(byPathogenId.getOrDefault(pathogen, List.of()));
        return result.stream().filter(definition -> BioForgeServerConfig.isMutationEnabled(definition.id())).toList();
    }

    public List<MutationDefinition> getMutationsWithTag(String tag) {
        if (tag == null) return List.of();
        return byTag.getOrDefault(tag.trim().toLowerCase(Locale.ROOT), List.of()).stream()
                .filter(definition -> BioForgeServerConfig.isMutationEnabled(definition.id()))
                .toList();
    }

    public List<MutationDefinition> getAllMutations() {
        return allMutations.stream()
                .filter(definition -> BioForgeServerConfig.isMutationEnabled(definition.id()))
                .toList();
    }

    public long generation() {
        return generation;
    }




    @Nullable
    public MutationDefinition getRandomMutationForPathogen(PathogenType pathogen, Random random) {
        List<MutationDefinition> available = getMutationsForPathogen(pathogen).stream()
                .filter(MutationDefinition::enabled)
                .filter(definition -> definition.weight() > 0)
                .toList();
        return chooseWeighted(available, random);
    }

    @Nullable
    public MutationDefinition chooseWeighted(List<MutationDefinition> definitions, Random random) {
        long totalWeight = 0;
        for (MutationDefinition definition : definitions) {
            if (definition.enabled() && BioForgeServerConfig.isMutationEnabled(definition.id())
                    && definition.weight() > 0) {
                totalWeight += definition.weight();
            }
        }
        if (totalWeight <= 0) return null;

        long roll = Math.floorMod(random.nextLong(), totalWeight);
        for (MutationDefinition definition : definitions) {
            if (!definition.enabled() || !BioForgeServerConfig.isMutationEnabled(definition.id())
                    || definition.weight() <= 0) continue;
            roll -= definition.weight();
            if (roll < 0) return definition;
        }
        return null;
    }
}
