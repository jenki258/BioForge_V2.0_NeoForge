package net.jenkimods.bioforge.api.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ReloadableDefinitionRegistry<T> {
    private final String name;
    private final Map<ResourceLocation, DefinitionEntry<T>> javaEntries = new LinkedHashMap<>();
    private final Map<ResourceLocation, ResourceLocation> javaAliases = new LinkedHashMap<>();
    private Map<ResourceLocation, DefinitionEntry<T>> datapackEntries = Map.of();
    private Map<ResourceLocation, ResourceLocation> datapackAliases = Map.of();
    private volatile Snapshot<T> snapshot = Snapshot.empty();
    private boolean frozen;

    public ReloadableDefinitionRegistry(String name) {
        this.name = name;
    }

    public synchronized void registerJava(ResourceLocation id, T definition, int priority,
                                          boolean replace, DefinitionSource source) {
        if (frozen) throw new IllegalStateException(name + " Java registry is frozen");
        DefinitionEntry<T> current = javaEntries.get(id);
        if (current != null && (priority < current.priority()
                || (priority == current.priority() && !replace))) {
            throw new IllegalArgumentException("Duplicate " + name + " definition " + id);
        }
        javaEntries.put(id, DefinitionEntry.enabled(definition, priority, replace, source));
        snapshot = resolve(javaEntries, datapackEntries, combinedAliases(),
                snapshot.generation() + 1, List.of());
    }

    public synchronized void registerAlias(ResourceLocation alias, ResourceLocation target) {
        if (frozen) throw new IllegalStateException(name + " Java registry is frozen");
        ResourceLocation previous = javaAliases.putIfAbsent(alias, target);
        if (previous != null && !previous.equals(target)) {
            throw new IllegalArgumentException("Duplicate " + name + " alias " + alias);
        }
        snapshot = resolve(javaEntries, datapackEntries, combinedAliases(), snapshot.generation() + 1, List.of());
    }

    public synchronized void freezeJava() {
        frozen = true;
    }

    public synchronized void commitDatapack(
            Map<ResourceLocation, DefinitionEntry<T>> datapackEntries,
            Map<ResourceLocation, ResourceLocation> datapackAliases,
            List<String> diagnostics) {
        this.datapackEntries = Map.copyOf(datapackEntries);
        this.datapackAliases = Map.copyOf(datapackAliases);
        snapshot = resolve(javaEntries, this.datapackEntries, combinedAliases(),
                snapshot.generation() + 1, diagnostics);
    }

    public Optional<T> get(ResourceLocation id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(snapshot.values().get(canonicalId(id)));
    }

    public ResourceLocation canonicalId(ResourceLocation id) {
        ResourceLocation current = id;
        Set<ResourceLocation> visited = new java.util.HashSet<>();
        while (visited.add(current)) {
            ResourceLocation next = snapshot.aliases().get(current);
            if (next == null) return current;
            current = next;
        }
        return id;
    }

    public Collection<T> values() { return snapshot.values().values(); }
    public Set<ResourceLocation> ids() { return snapshot.values().keySet(); }
    public Snapshot<T> snapshot() { return snapshot; }

    public synchronized Map<ResourceLocation, DefinitionEntry<T>> javaEntries() {
        return Map.copyOf(javaEntries);
    }

    public Map<ResourceLocation, T> preview(Map<ResourceLocation, DefinitionEntry<T>> datapackEntries) {
        return resolve(javaEntries, datapackEntries, Map.of(), 0, List.of()).values();
    }

    private Map<ResourceLocation, ResourceLocation> combinedAliases() {
        Map<ResourceLocation, ResourceLocation> aliases = new LinkedHashMap<>(javaAliases);
        aliases.putAll(datapackAliases);
        return aliases;
    }

    private Snapshot<T> resolve(
            Map<ResourceLocation, DefinitionEntry<T>> base,
            Map<ResourceLocation, DefinitionEntry<T>> datapack,
            Map<ResourceLocation, ResourceLocation> aliases,
            long generation,
            List<String> diagnostics) {
        List<Map.Entry<ResourceLocation, DefinitionEntry<T>>> ordered = new ArrayList<>();
        ordered.addAll(base.entrySet());
        ordered.addAll(datapack.entrySet());
        ordered.sort(Comparator
                .comparingInt((Map.Entry<ResourceLocation, DefinitionEntry<T>> entry) ->
                        entry.getValue().priority())
                .thenComparing(entry -> entry.getValue().replace())
                .thenComparing(entry -> entry.getValue().source().ordinal())
                .thenComparing(entry -> entry.getKey().toString()));
        Map<ResourceLocation, DefinitionEntry<T>> winners = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, DefinitionEntry<T>> candidate : ordered) {
            DefinitionEntry<T> current = winners.get(candidate.getKey());
            DefinitionEntry<T> incoming = candidate.getValue();
            if (current == null || incoming.replace() || incoming.priority() >= current.priority()) {
                winners.put(candidate.getKey(), incoming);
            }
        }
        Map<ResourceLocation, T> values = new LinkedHashMap<>();
        winners.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (entry.getValue().enabled()) values.put(entry.getKey(), entry.getValue().definition());
        });
        return new Snapshot<>(generation, Collections.unmodifiableMap(values),
                Collections.unmodifiableMap(new LinkedHashMap<>(aliases)), List.copyOf(diagnostics));
    }

    public record Snapshot<T>(long generation, Map<ResourceLocation, T> values,
                              Map<ResourceLocation, ResourceLocation> aliases,
                              List<String> diagnostics) {
        private static <T> Snapshot<T> empty() {
            return new Snapshot<>(0, Map.of(), Map.of(), List.of());
        }
    }
}
