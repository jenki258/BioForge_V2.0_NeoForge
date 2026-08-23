package net.jenkimods.bioforge.network.compat;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public record BioForgeNetworkPayload(Route route, Object message) implements CustomPacketPayload {
    public static final Type<BioForgeNetworkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BioForge.MODID, "routed_payload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BioForgeNetworkPayload> STREAM_CODEC =
            StreamCodec.ofMember(BioForgeNetworkPayload::encode, BioForgeNetworkPayload::decode);

    private static final Map<Route, Registration<?>> BY_ROUTE = new ConcurrentHashMap<>();
    private static final Map<MessageKey, Registration<?>> BY_MESSAGE = new ConcurrentHashMap<>();

    public static void registerPayload(RegisterPayloadHandlersEvent event) {
        event.registrar("2.0").playBidirectional(
                TYPE,
                STREAM_CODEC,
                BioForgeNetworkPayload::handle);
    }

    static <T> void register(ResourceLocation channel,
                             int id,
                             Class<T> messageType,
                             BiConsumer<T, RegistryFriendlyByteBuf> encoder,
                             Function<RegistryFriendlyByteBuf, T> decoder,
                             BiConsumer<T, Supplier<NetworkEvent.Context>> handler,
                             NetworkDirection direction) {
        Route route = new Route(channel, id);
        Registration<T> registration = new Registration<>(route, messageType, encoder,
                decoder, handler, direction);
        if (BY_ROUTE.putIfAbsent(route, registration) != null) {
            throw new IllegalStateException("Duplicate BioForge packet route " + route);
        }

        MessageKey key = new MessageKey(channel, messageType);
        if (BY_MESSAGE.putIfAbsent(key, registration) != null) {
            BY_ROUTE.remove(route, registration);
            throw new IllegalStateException("Duplicate BioForge packet class " + key);
        }
    }

    static BioForgeNetworkPayload create(ResourceLocation channel, Object message) {
        Objects.requireNonNull(message, "message");
        Registration<?> registration = BY_MESSAGE.get(new MessageKey(channel, message.getClass()));
        if (registration == null) {
            throw new IllegalStateException("Unregistered BioForge packet "
                    + channel + " / " + message.getClass().getName());
        }
        return new BioForgeNetworkPayload(registration.route(), message);
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        ResourceLocation.STREAM_CODEC.encode(buffer, route.channel());
        buffer.writeVarInt(route.id());
        registration(route).encode(message, buffer);
    }

    private static BioForgeNetworkPayload decode(RegistryFriendlyByteBuf buffer) {
        Route route = new Route(ResourceLocation.STREAM_CODEC.decode(buffer), buffer.readVarInt());
        Registration<?> registration = registration(route);
        return new BioForgeNetworkPayload(route, registration.decode(buffer));
    }

    private static void handle(BioForgeNetworkPayload payload, IPayloadContext context) {
        Registration<?> registration = registration(payload.route());
        NetworkDirection actual = context.flow() == PacketFlow.CLIENTBOUND
                ? NetworkDirection.PLAY_TO_CLIENT
                : NetworkDirection.PLAY_TO_SERVER;
        if (registration.direction() != null && registration.direction() != actual) {
            throw new IllegalStateException("BioForge packet " + payload.route()
                    + " received in the wrong direction");
        }
        NetworkEvent.Context legacyContext = new NetworkEvent.Context(context);
        registration.handle(payload.message(), () -> legacyContext);
    }

    private static Registration<?> registration(Route route) {
        Registration<?> registration = BY_ROUTE.get(route);
        if (registration == null) {
            throw new IllegalStateException("Unknown BioForge packet route " + route);
        }
        return registration;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Route(ResourceLocation channel, int id) {
        public Route {
            Objects.requireNonNull(channel, "channel");
            if (id < 0) throw new IllegalArgumentException("Packet id must be non-negative");
        }
    }

    private record MessageKey(ResourceLocation channel, Class<?> messageType) {
    }

    private static final class Registration<T> {
        private final Route route;
        private final Class<T> messageType;
        private final BiConsumer<T, RegistryFriendlyByteBuf> encoder;
        private final Function<RegistryFriendlyByteBuf, T> decoder;
        private final BiConsumer<T, Supplier<NetworkEvent.Context>> handler;
        private final NetworkDirection direction;

        private Registration(Route route,
                             Class<T> messageType,
                             BiConsumer<T, RegistryFriendlyByteBuf> encoder,
                             Function<RegistryFriendlyByteBuf, T> decoder,
                             BiConsumer<T, Supplier<NetworkEvent.Context>> handler,
                             NetworkDirection direction) {
            this.route = route;
            this.messageType = Objects.requireNonNull(messageType, "messageType");
            this.encoder = Objects.requireNonNull(encoder, "encoder");
            this.decoder = Objects.requireNonNull(decoder, "decoder");
            this.handler = Objects.requireNonNull(handler, "handler");
            this.direction = direction;
        }

        private Route route() {
            return route;
        }

        private NetworkDirection direction() {
            return direction;
        }

        private void encode(Object message, RegistryFriendlyByteBuf buffer) {
            encoder.accept(messageType.cast(message), buffer);
        }

        private T decode(RegistryFriendlyByteBuf buffer) {
            return decoder.apply(buffer);
        }

        private void handle(Object message, Supplier<NetworkEvent.Context> context) {
            handler.accept(messageType.cast(message), context);
        }
    }
}
