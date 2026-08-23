package net.jenkimods.bioforge.mutation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.PathogenType;
import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.jenkimods.bioforge.BioForge;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;









public final class MutationDefinition {
    public enum Trigger {
        APPLY,
        CONTINUOUS,
        REMOVE;

        public static Trigger fromName(@Nullable String name, String effectType) {
            if (name != null && !name.isBlank()) {
                return switch (name.trim().toLowerCase(Locale.ROOT)) {
                    case "apply", "once", "on_apply" -> APPLY;
                    case "tick", "continuous", "while_infected" -> CONTINUOUS;
                    case "remove", "on_remove" -> REMOVE;
                    default -> throw new IllegalArgumentException("Unknown mutation effect trigger: " + name);
                };
            }
            return switch (effectType) {
                case "potion_effect", "spawn_particle", "attribute_modifier", "damage",
                        "heal", "exhaustion", "ignite", "play_sound" -> CONTINUOUS;
                default -> APPLY;
            };
        }
    }





    public static final class Effect {
        private final String type;
        private final String target;
        private final String operation;
        private final Trigger trigger;
        private final JsonObject parameters;

        public Effect(String type, String target, String operation, Trigger trigger, JsonObject parameters) {
            this.type = normalize(type);
            this.target = target == null ? "" : target.trim();
            this.operation = normalize(operation == null || operation.isBlank()
                    ? defaultOperation(this.type)
                    : operation);
            this.trigger = Objects.requireNonNull(trigger, "Mutation effect trigger cannot be null");
            this.parameters = parameters == null ? new JsonObject() : parameters.deepCopy();
        }

        public String type() {
            return type;
        }

        public String target() {
            return target;
        }

        public String operation() {
            return operation;
        }

        public Trigger trigger() {
            return trigger;
        }

        public JsonObject parameters() {
            return parameters.deepCopy();
        }

        public boolean has(String key) {
            return find(key) != null;
        }

        public String stringValue(String key, String fallback) {
            JsonElement element = find(key);
            if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return fallback;
            try {
                return element.getAsString();
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }

        public boolean booleanValue(String key, boolean fallback) {
            JsonElement element = find(key);
            if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return fallback;
            try {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (primitive.isBoolean()) return primitive.getAsBoolean();
                String text = primitive.getAsString();
                if ("true".equalsIgnoreCase(text)) return true;
                if ("false".equalsIgnoreCase(text)) return false;
            } catch (RuntimeException ignored) {

            }
            return fallback;
        }

        public int intValue(String key, int fallback) {
            JsonElement element = find(key);
            if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return fallback;
            try {
                return element.getAsInt();
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }

        public float floatValue(String key, float fallback) {
            JsonElement element = find(key);
            if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return fallback;
            try {
                return element.getAsFloat();
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }

        public double doubleValue(String key, double fallback) {
            JsonElement element = find(key);
            if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return fallback;
            try {
                return element.getAsDouble();
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }

        @Nullable
        private JsonElement find(String key) {
            JsonElement direct = parameters.get(key);
            if (direct != null) return direct;
            JsonElement conditions = parameters.get("conditions");
            if (conditions != null && conditions.isJsonObject()) {
                return conditions.getAsJsonObject().get(key);
            }
            return null;
        }

        private static String defaultOperation(String type) {
            return switch (type) {
                case "modify_symptom" -> "multiply";
                case "set_symptom" -> "set";
                case "attribute_modifier" -> "add";
                default -> "set";
            };
        }
    }








    public static final class EffectModifier {
        @Nullable private final String type;
        @Nullable private final String target;
        @Nullable private final Trigger trigger;
        private final int effectIndex;
        private final String parameter;
        private final double multiplier;
        private final double offset;
        private final double minimum;
        private final double maximum;
        private final boolean suppress;

        public EffectModifier(@Nullable String type, @Nullable String target,
                              @Nullable Trigger trigger, int effectIndex, String parameter,
                              double multiplier, double offset, double minimum, double maximum,
                              boolean suppress) {
            this.type = type == null || type.isBlank() ? null : normalize(type);
            this.target = target == null || target.isBlank() ? null : target.trim();
            this.trigger = trigger;
            this.effectIndex = effectIndex;
            this.parameter = parameter == null ? "" : normalize(parameter);
            this.multiplier = multiplier;
            this.offset = offset;
            this.minimum = Math.min(minimum, maximum);
            this.maximum = Math.max(minimum, maximum);
            this.suppress = suppress;
        }

        @Nullable public String type() { return type; }
        @Nullable public String target() { return target; }
        @Nullable public Trigger trigger() { return trigger; }
        public int effectIndex() { return effectIndex; }
        public String parameter() { return parameter; }
        public double multiplier() { return multiplier; }
        public double offset() { return offset; }
        public double minimum() { return minimum; }
        public double maximum() { return maximum; }
        public boolean suppress() { return suppress; }

        public boolean matches(Effect effect, int index) {


            if (effect.trigger() == Trigger.APPLY) return false;
            if (effectIndex >= 0 && effectIndex != index) return false;
            if (type != null && !type.equals(effect.type())) return false;
            if (target != null && !target.equals(effect.target())) return false;
            return trigger == null || trigger == effect.trigger();
        }





        @Nullable
        public Effect transform(Effect effect) {
            if (suppress) return null;
            if (parameter.isEmpty()) return effect;

            JsonObject parameters = effect.parameters();
            JsonElement element = parameters.get(parameter);
            if (element == null || !element.isJsonPrimitive()
                    || element.getAsJsonPrimitive().isBoolean()) {
                return effect;
            }

            final double original;
            try {
                original = element.getAsDouble();
            } catch (RuntimeException ignored) {
                return effect;
            }
            if (!Double.isFinite(original)) return effect;

            double transformed = original * multiplier + offset;
            transformed = Math.max(minimum, Math.min(maximum, transformed));
            parameters.addProperty(parameter, transformed);
            return new Effect(effect.type(), effect.target(), effect.operation(),
                    effect.trigger(), parameters);
        }
    }








    public static final class Interaction {
        private final String id;
        private final Set<String> withMutations;
        private final boolean requireAll;
        private final List<EffectModifier> effectModifiers;
        private final List<Effect> effects;
        private final Set<String> grantMutations;
        private final Set<String> removeMutations;
        private final boolean forceGrants;

        public Interaction(String id, Collection<String> withMutations, boolean requireAll,
                           Collection<EffectModifier> effectModifiers, Collection<Effect> effects,
                           Collection<String> grantMutations, Collection<String> removeMutations,
                           boolean forceGrants) {
            this.id = normalize(id);
            LinkedHashSet<String> partners = new LinkedHashSet<>();
            addNormalized(partners, withMutations);
            this.withMutations = Collections.unmodifiableSet(partners);
            this.requireAll = requireAll;
            this.effectModifiers = List.copyOf(effectModifiers);
            this.effects = List.copyOf(effects);
            LinkedHashSet<String> grants = new LinkedHashSet<>();
            addNormalized(grants, grantMutations);
            this.grantMutations = Collections.unmodifiableSet(grants);
            LinkedHashSet<String> removals = new LinkedHashSet<>();
            addNormalized(removals, removeMutations);
            this.removeMutations = Collections.unmodifiableSet(removals);
            this.forceGrants = forceGrants;
        }

        public String id() { return id; }
        public Set<String> withMutations() { return withMutations; }
        public boolean requireAll() { return requireAll; }
        public List<EffectModifier> effectModifiers() { return effectModifiers; }
        public List<Effect> effects() { return effects; }
        public Set<String> grantMutations() { return grantMutations; }
        public Set<String> removeMutations() { return removeMutations; }
        public boolean forceGrants() { return forceGrants; }

        public boolean isActive(Set<String> ownedMutations) {
            if (withMutations.isEmpty()) return false;
            if (requireAll) return ownedMutations.containsAll(withMutations);
            for (String mutation : withMutations) {
                if (ownedMutations.contains(mutation)) return true;
            }
            return false;
        }

        private static void addNormalized(Set<String> target, @Nullable Collection<String> values) {
            if (values == null) return;
            for (String value : values) {
                String normalized = normalize(value);
                if (!normalized.isEmpty()) target.add(normalized);
            }
        }
    }

    private final String id;
    private final String name;
    private final String description;
    private final String nameKey;
    private final String descriptionKey;
    private final Set<PathogenType> pathogens;
    private final Set<ResourceLocation> pathogenIds;
    private final List<Effect> effects;
    private final String rarity;
    private final int weight;
    private final boolean enabled;
    private final boolean hidden;
    private final ResourceLocation icon;
    private final Set<String> requiredMutations;
    private final Set<String> conflictingMutations;
    private final Set<String> tags;
    private final List<Interaction> interactions;

    private MutationDefinition(Builder builder) {
        this.id = builder.id;
        this.name = builder.name == null || builder.name.isBlank() ? builder.id : builder.name;
        this.description = builder.description;
        String translationBase = translationBase(builder.id);
        this.nameKey = builder.nameKey == null || builder.nameKey.isBlank()
                ? translationBase + ".name" : builder.nameKey;
        this.descriptionKey = builder.descriptionKey == null || builder.descriptionKey.isBlank()
                ? translationBase + ".description" : builder.descriptionKey;
        this.pathogens = Collections.unmodifiableSet(EnumSet.copyOf(builder.pathogens));
        this.pathogenIds = Collections.unmodifiableSet(new LinkedHashSet<>(builder.pathogenIds));
        this.effects = List.copyOf(builder.effects);
        this.rarity = builder.rarity;
        this.weight = builder.weight;
        this.enabled = builder.enabled;
        this.hidden = builder.hidden;
        this.icon = builder.icon;
        this.requiredMutations = Collections.unmodifiableSet(new LinkedHashSet<>(builder.requiredMutations));
        this.conflictingMutations = Collections.unmodifiableSet(new LinkedHashSet<>(builder.conflictingMutations));
        this.tags = Collections.unmodifiableSet(new LinkedHashSet<>(builder.tags));
        this.interactions = List.copyOf(builder.interactions);
    }

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public String nameKey() { return nameKey; }
    public String descriptionKey() { return descriptionKey; }
    public Set<PathogenType> pathogens() { return pathogens; }
    public Set<ResourceLocation> pathogenIds() { return pathogenIds; }
    public List<Effect> effects() { return effects; }
    public String rarity() { return rarity; }
    public int weight() { return weight; }
    public boolean enabled() { return enabled; }
    public boolean hidden() { return hidden; }
    @Nullable public ResourceLocation icon() { return icon; }
    public Set<String> requiredMutations() { return requiredMutations; }
    public Set<String> conflictingMutations() { return conflictingMutations; }
    public Set<String> tags() { return tags; }
    public List<Interaction> interactions() { return interactions; }




    public PathogenType pathogen() {
        if (pathogens.contains(PathogenType.UNIVERSAL)) return PathogenType.UNIVERSAL;
        return pathogens.isEmpty() ? PathogenType.UNIVERSAL : pathogens.iterator().next();
    }




    public String effectType() {
        return effects.isEmpty() ? "" : effects.get(0).type();
    }




    public String effectTarget() {
        return effects.isEmpty() ? "" : effects.get(0).target();
    }




    public float effectValue() {
        return effects.isEmpty() ? 1.0f : effects.get(0).floatValue("value", 1.0f);
    }

    public boolean isCompatible(@Nullable PathogenType pathogen) {
        if (pathogen == null) return false;
        if (pathogen == PathogenType.UNIVERSAL) return true;
        return pathogens.contains(PathogenType.UNIVERSAL) || pathogens.contains(pathogen);
    }

    public boolean isCompatible(@Nullable ResourceLocation pathogenId) {
        if (pathogenId == null) return false;
        ResourceLocation universal = BioForgeIds.pathogen(PathogenType.UNIVERSAL);
        if (universal.equals(pathogenId)) return true;
        return pathogenIds.contains(universal)
                || pathogenIds.contains(pathogenId);
    }

    public boolean requirementsMet(InfectionData data) {
        if (data == null) return false;
        Set<String> owned = data.getSymptoms().getMutations();
        return owned.containsAll(requiredMutations);
    }

    public boolean conflictsWith(InfectionData data) {
        if (data == null) return false;
        for (String mutation : conflictingMutations) {
            if (data.getSymptoms().hasMutation(mutation)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String translationBase(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(':');
        String namespace = separator >= 0 ? normalized.substring(0, separator) : BioForge.MODID;
        String path = separator >= 0 ? normalized.substring(separator + 1) : normalized;
        return "mutation." + namespace + "." + path.replace('/', '.');
    }

    public static final class Builder {
        private String id;
        private String name;
        private String description = "";
        private String nameKey;
        private String descriptionKey;
        private final EnumSet<PathogenType> pathogens = EnumSet.noneOf(PathogenType.class);
        private final Set<ResourceLocation> pathogenIds = new LinkedHashSet<>();
        private final List<Effect> effects = new ArrayList<>();
        private String rarity = "common";
        private int weight = 100;
        private boolean enabled = true;
        private boolean hidden = false;
        private ResourceLocation icon;
        private final Set<String> requiredMutations = new LinkedHashSet<>();
        private final Set<String> conflictingMutations = new LinkedHashSet<>();
        private final Set<String> tags = new LinkedHashSet<>();
        private final List<Interaction> interactions = new ArrayList<>();

        public Builder id(String id) {
            this.id = id == null ? null : id.trim().toLowerCase(Locale.ROOT);
            return this;
        }

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description == null ? "" : description; return this; }
        public Builder nameKey(String nameKey) { this.nameKey = nameKey; return this; }
        public Builder descriptionKey(String descriptionKey) { this.descriptionKey = descriptionKey; return this; }

        public Builder pathogen(PathogenType pathogen) {
            if (pathogen != null) {
                this.pathogens.add(pathogen);
                this.pathogenIds.add(BioForgeIds.pathogen(pathogen));
            }
            return this;
        }

        public Builder pathogens(Collection<PathogenType> pathogens) {
            if (pathogens != null) pathogens.forEach(this::pathogen);
            return this;
        }

        public Builder pathogenId(ResourceLocation pathogenId) {
            if (pathogenId != null) {
                this.pathogenIds.add(pathogenId);
                PathogenType legacy = BioForgeIds.legacyPathogen(pathogenId);
                if (legacy != null) this.pathogens.add(legacy);
            }
            return this;
        }

        public Builder pathogenIds(Collection<ResourceLocation> pathogenIds) {
            if (pathogenIds != null) pathogenIds.forEach(this::pathogenId);
            return this;
        }

        public Builder effect(Effect effect) {
            if (effect != null) this.effects.add(effect);
            return this;
        }

        public Builder effects(Collection<Effect> effects) {
            if (effects != null) this.effects.addAll(effects);
            return this;
        }

        public Builder rarity(String rarity) {
            this.rarity = normalize(rarity == null || rarity.isBlank() ? "common" : rarity);
            return this;
        }

        public Builder weight(int weight) {
            this.weight = Math.max(0, weight);
            return this;
        }

        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder hidden(boolean hidden) { this.hidden = hidden; return this; }
        public Builder icon(@Nullable ResourceLocation icon) { this.icon = icon; return this; }

        public Builder requiredMutations(Collection<String> ids) {
            addNormalized(this.requiredMutations, ids);
            return this;
        }

        public Builder conflictingMutations(Collection<String> ids) {
            addNormalized(this.conflictingMutations, ids);
            return this;
        }

        public Builder tags(Collection<String> tags) {
            addNormalized(this.tags, tags);
            return this;
        }

        public Builder interaction(Interaction interaction) {
            if (interaction != null) this.interactions.add(interaction);
            return this;
        }

        public Builder interactions(Collection<Interaction> interactions) {
            if (interactions != null) this.interactions.addAll(interactions);
            return this;
        }

        public MutationDefinition build() {
            Objects.requireNonNull(id, "Mutation ID cannot be null");
            if (id.isBlank()) throw new IllegalArgumentException("Mutation ID cannot be empty");
            if (pathogenIds.isEmpty()) pathogen(PathogenType.UNIVERSAL);
            if (effects.isEmpty() && interactions.isEmpty() && tags.isEmpty()) {
                throw new IllegalArgumentException(
                        "Mutation must define at least one effect, interaction, or marker tag");
            }
            return new MutationDefinition(this);
        }

        private static void addNormalized(Set<String> target, @Nullable Collection<String> values) {
            if (values == null) return;
            for (String value : values) {
                String normalized = normalize(value);
                if (!normalized.isEmpty()) target.add(normalized);
            }
        }
    }
}
