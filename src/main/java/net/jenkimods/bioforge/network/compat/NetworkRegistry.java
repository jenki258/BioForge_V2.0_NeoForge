package net.jenkimods.bioforge.network.compat;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class NetworkRegistry {
    private NetworkRegistry() {
    }

    public static SimpleChannel newSimpleChannel(ResourceLocation name,
                                                  Supplier<String> protocolVersion,
                                                  Predicate<String> clientAcceptedVersions,
                                                  Predicate<String> serverAcceptedVersions) {
        Objects.requireNonNull(protocolVersion.get(), "protocolVersion");
        Objects.requireNonNull(clientAcceptedVersions, "clientAcceptedVersions");
        Objects.requireNonNull(serverAcceptedVersions, "serverAcceptedVersions");
        return new SimpleChannel(Objects.requireNonNull(name, "name"));
    }
}
