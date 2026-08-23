package net.jenkimods.bioforge.network.compat;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.concurrent.CompletableFuture;

public final class NetworkEvent {
    private NetworkEvent() {
    }

    public static final class Context {
        private final IPayloadContext delegate;

        Context(IPayloadContext delegate) {
            this.delegate = delegate;
        }

        public CompletableFuture<Void> enqueueWork(Runnable task) {
            return delegate.enqueueWork(task);
        }

        public ServerPlayer getSender() {
            return delegate.player() instanceof ServerPlayer player ? player : null;
        }

        public void setPacketHandled(boolean handled) {
        }
    }
}
