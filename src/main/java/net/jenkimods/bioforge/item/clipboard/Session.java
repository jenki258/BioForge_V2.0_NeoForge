package net.jenkimods.bioforge.item.clipboard;

import net.jenkimods.bioforge.registry.BioForgeAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class Session {
    public interface SessionCapability {
        @Nullable UUID getId();

        void setId(@Nullable UUID id);
    }

    public static final class SessionCapabilityImpl implements SessionCapability {
        @Nullable private UUID id;

        @Override
        public @Nullable UUID getId() {
            return id;
        }

        @Override
        public void setId(@Nullable UUID id) {
            this.id = id;
        }
    }

    private Session() {
    }

    @Nullable
    public static UUID get(Entity entity) {
        if (!(entity instanceof Player player)) return null;
        return player.getData(BioForgeAttachments.CLIPBOARD_SESSION.get()).getId();
    }

    public static SessionCapability getData(Player player) {
        return player.getData(BioForgeAttachments.CLIPBOARD_SESSION.get());
    }

    public static void set(Player player, UUID id) {
        getData(player).setId(id);
    }
}
