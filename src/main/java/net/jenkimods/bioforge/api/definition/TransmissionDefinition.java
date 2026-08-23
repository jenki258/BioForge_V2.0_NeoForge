package net.jenkimods.bioforge.api.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record TransmissionDefinition(ResourceLocation id, String translationKey,
                                     Set<ResourceLocation> behaviors) {
    public TransmissionDefinition {
        Objects.requireNonNull(id, "Transmission id");
        translationKey = translationKey == null || translationKey.isBlank()
                ? "infection_type." + id.getNamespace() + "." + id.getPath() : translationKey;
        behaviors = Set.copyOf(behaviors == null ? Set.of() : behaviors);
    }

    public static Builder builder(ResourceLocation id) { return new Builder(id); }

    public static final class Builder {
        private final ResourceLocation id;
        private String translationKey;
        private final Set<ResourceLocation> behaviors = new LinkedHashSet<>();
        private Builder(ResourceLocation id) { this.id = Objects.requireNonNull(id); }
        public Builder translationKey(String value) { translationKey = value; return this; }
        public Builder behavior(ResourceLocation value) { behaviors.add(value); return this; }
        public Builder behaviors(Collection<ResourceLocation> values) { behaviors.addAll(values); return this; }
        public TransmissionDefinition build() { return new TransmissionDefinition(id, translationKey, behaviors); }
    }
}
