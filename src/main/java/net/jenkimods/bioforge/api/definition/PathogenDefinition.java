package net.jenkimods.bioforge.api.definition;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record PathogenDefinition(ResourceLocation id, String translationKey, int color,
                                 boolean environmental, Set<ResourceLocation> allowedTransmissions,
                                 Map<ResourceLocation, DefaultSymptomValue> defaultSymptoms) {
    public PathogenDefinition {
        Objects.requireNonNull(id, "Pathogen id");
        translationKey = translationKey == null || translationKey.isBlank()
                ? "pathogen." + id.getNamespace() + "." + id.getPath() : translationKey;
        allowedTransmissions = Set.copyOf(allowedTransmissions == null ? Set.of() : allowedTransmissions);
        defaultSymptoms = Map.copyOf(defaultSymptoms == null ? Map.of() : defaultSymptoms);
    }

    public static Builder builder(ResourceLocation id) { return new Builder(id); }

    public record DefaultSymptomValue(JsonElement minimum, JsonElement maximum) {
        public DefaultSymptomValue {
            Objects.requireNonNull(minimum);
            maximum = maximum == null ? minimum : maximum;
            minimum = minimum.deepCopy();
            maximum = maximum.deepCopy();
        }
        public static DefaultSymptomValue fixed(JsonElement value) { return new DefaultSymptomValue(value, value); }
    }

    public static final class Builder {
        private final ResourceLocation id;
        private String translationKey;
        private int color = 0xFFFFFF;
        private boolean environmental;
        private final Set<ResourceLocation> transmissions = new LinkedHashSet<>();
        private final Map<ResourceLocation, DefaultSymptomValue> defaults = new LinkedHashMap<>();
        private Builder(ResourceLocation id) { this.id = Objects.requireNonNull(id); }
        public Builder translationKey(String value) { translationKey = value; return this; }
        public Builder color(int value) { color = value & 0xFFFFFF; return this; }
        public Builder environmental(boolean value) { environmental = value; return this; }
        public Builder transmission(ResourceLocation value) { transmissions.add(value); return this; }
        public Builder transmissions(Collection<ResourceLocation> values) { transmissions.addAll(values); return this; }
        public Builder defaultSymptom(ResourceLocation id, DefaultSymptomValue value) { defaults.put(id, value); return this; }
        public PathogenDefinition build() {
            return new PathogenDefinition(id, translationKey, color, environmental, transmissions, defaults);
        }
    }
}
