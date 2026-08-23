package net.jenkimods.bioforge.client.vaccine;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;


public final class VaccineMakerPageRenderRegistry {
    private static final Map<ResourceLocation, VaccineMakerPageRenderer> RENDERERS =
            new LinkedHashMap<>();

    private VaccineMakerPageRenderRegistry() {}

    public static synchronized void register(ResourceLocation pageId,
                                             VaccineMakerPageRenderer renderer) {
        Objects.requireNonNull(pageId, "pageId");
        Objects.requireNonNull(renderer, "renderer");
        if (RENDERERS.putIfAbsent(pageId, renderer) != null) {
            throw new IllegalArgumentException(
                    "Duplicate Vaccine Maker page renderer: " + pageId);
        }
    }

    @Nullable
    public static synchronized VaccineMakerPageRenderer get(ResourceLocation pageId) {
        return RENDERERS.get(pageId);
    }
}
