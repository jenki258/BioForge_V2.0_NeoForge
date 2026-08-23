package net.jenkimods.bioforge.network.compat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class SimpleChannel {
    private final ResourceLocation name;

    SimpleChannel(ResourceLocation name) {
        this.name = name;
    }

    public <T> void registerMessage(int id,
                                    Class<T> messageType,
                                    BiConsumer<T, RegistryFriendlyByteBuf> encoder,
                                    Function<RegistryFriendlyByteBuf, T> decoder,
                                    BiConsumer<T, Supplier<NetworkEvent.Context>> handler,
                                    Optional<NetworkDirection> direction) {
        BioForgeNetworkPayload.register(name, id, messageType, encoder, decoder,
                handler, direction.orElse(null));
    }

    public void send(PacketDistributor.PacketTarget target, Object message) {
        Objects.requireNonNull(target, "target").send(BioForgeNetworkPayload.create(name, message));
    }

    public void sendToServer(Object message) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                BioForgeNetworkPayload.create(name, message));
    }
}
