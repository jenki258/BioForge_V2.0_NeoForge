package net.jenkimods.bioforge.api.definition;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record SymptomDefinition(ResourceLocation id, String translationKey, ValueType valueType,
                                JsonElement defaultValue, double minimum, double maximum,
                                List<String> allowedValues, Set<ResourceLocation> behaviors) {
    public SymptomDefinition {
        Objects.requireNonNull(id, "Symptom id");
        Objects.requireNonNull(valueType, "Symptom value type");
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) {
            throw new IllegalArgumentException("Invalid symptom range for " + id);
        }
        translationKey = translationKey == null || translationKey.isBlank()
                ? "microscope.symptom."
                + (BioForgeIds.BIOFORGE_NAMESPACE.equals(id.getNamespace())
                ? id.getPath() : id.getNamespace() + "." + id.getPath())
                : translationKey;
        defaultValue = defaultValue == null ? valueType.fallback() : defaultValue.deepCopy();
        allowedValues = List.copyOf(allowedValues == null ? List.of() : allowedValues);
        behaviors = Set.copyOf(behaviors == null ? Set.of() : behaviors);
    }

    public static Builder builder(ResourceLocation id, ValueType valueType) { return new Builder(id, valueType); }
    public Object javaDefaultValue() { return valueType.read(defaultValue, allowedValues); }
    public Class<?> javaType() { return valueType.javaType(); }

    public enum ValueType {
        BOOLEAN(Boolean.class), INTEGER(Integer.class), FLOAT(Float.class), STRING(String.class), ENUM(String.class);
        private final Class<?> javaType;
        ValueType(Class<?> javaType) { this.javaType = javaType; }
        public Class<?> javaType() { return javaType; }
        private JsonElement fallback() {
            return switch (this) {
                case BOOLEAN -> new JsonPrimitive(false);
                case INTEGER -> new JsonPrimitive(0);
                case FLOAT -> new JsonPrimitive(0.0F);
                case STRING, ENUM -> new JsonPrimitive("");
            };
        }
        private Object read(JsonElement value, List<String> allowed) {
            try {
                return switch (this) {
                    case BOOLEAN -> value.getAsBoolean();
                    case INTEGER -> value.getAsInt();
                    case FLOAT -> value.getAsFloat();
                    case STRING -> value.getAsString();
                    case ENUM -> {
                        String selected = value.getAsString();
                        yield allowed.isEmpty() || allowed.contains(selected) ? selected : allowed.get(0);
                    }
                };
            } catch (RuntimeException exception) {
                return switch (this) {
                    case BOOLEAN -> false;
                    case INTEGER -> 0;
                    case FLOAT -> 0.0F;
                    case STRING, ENUM -> "";
                };
            }
        }
    }

    public static final class Builder {
        private final ResourceLocation id;
        private final ValueType valueType;
        private String translationKey;
        private JsonElement defaultValue;
        private double minimum = -Double.MAX_VALUE;
        private double maximum = Double.MAX_VALUE;
        private List<String> allowedValues = List.of();
        private final Set<ResourceLocation> behaviors = new LinkedHashSet<>();
        private Builder(ResourceLocation id, ValueType valueType) {
            this.id = Objects.requireNonNull(id);
            this.valueType = Objects.requireNonNull(valueType);
        }
        public Builder translationKey(String value) { translationKey = value; return this; }
        public Builder defaultValue(JsonElement value) { defaultValue = value; return this; }
        public Builder range(double min, double max) { minimum = min; maximum = max; return this; }
        public Builder allowedValues(Collection<String> values) { allowedValues = List.copyOf(values); return this; }
        public Builder behavior(ResourceLocation value) { behaviors.add(value); return this; }
        public SymptomDefinition build() {
            return new SymptomDefinition(id, translationKey, valueType, defaultValue,
                    minimum, maximum, allowedValues, behaviors);
        }
    }
}
