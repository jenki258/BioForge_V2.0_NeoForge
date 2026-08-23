package net.jenkimods.bioforge.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class BioForgeClientDefinitionCache {
    private static final AtomicReference<Snapshot> SNAPSHOT =
            new AtomicReference<>(new Snapshot(0, Map.of(), Map.of(), Map.of()));

    private BioForgeClientDefinitionCache() {}

    public static Snapshot snapshot() { return SNAPSHOT.get(); }
    public static void set(Snapshot snapshot) { SNAPSHOT.set(snapshot); }

    public record PathogenView(String translationKey, int color, boolean environmental,
                               List<ResourceLocation> allowedTransmissions) {}
    public record TransmissionView(String translationKey, List<ResourceLocation> behaviors) {}
    public record SymptomView(String translationKey, String valueType, String defaultValue,
                              double minimum, double maximum,
                              List<String> allowedValues, List<ResourceLocation> behaviors) {}
    public record Snapshot(long generation,
                           Map<ResourceLocation, PathogenView> pathogens,
                           Map<ResourceLocation, TransmissionView> transmissions,
                           Map<ResourceLocation, SymptomView> symptoms) {
        public Snapshot {
            pathogens = Map.copyOf(pathogens);
            transmissions = Map.copyOf(transmissions);
            symptoms = Map.copyOf(symptoms);
        }
    }
}
