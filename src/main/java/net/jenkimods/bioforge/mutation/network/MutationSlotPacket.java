package net.jenkimods.bioforge.mutation.network;

import net.jenkimods.bioforge.mutation.MutationDefinition;
import net.jenkimods.bioforge.mutation.MutationLoader;
import net.jenkimods.bioforge.mutation.MutationVisual;
import net.jenkimods.bioforge.mutation.SlotMachineOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.jenkimods.bioforge.network.compat.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class MutationSlotPacket {
    private static final int MAX_CATALOG_SIZE = 32;
    private static final int MAX_ID_LENGTH = 256;
    private static final int MAX_TRANSLATION_KEY_LENGTH = 256;
    private static final int MAX_NAME_LENGTH = 160;
    private static final int MAX_RARITY_LENGTH = 32;
    private static final int MAX_ICON_LENGTH = 256;

    private final String selectedId;
    private final List<MutationVisual> catalog;

    public MutationSlotPacket(String selectedId, List<MutationVisual> catalog) {
        this.selectedId = selectedId;
        this.catalog = List.copyOf(catalog);
    }





    public static MutationSlotPacket forMutation(String selectedId) {
        Map<String, MutationVisual> visuals = new LinkedHashMap<>();
        MutationDefinition selected = MutationLoader.INSTANCE.getMutation(selectedId).orElse(null);
        if (selected != null) visuals.put(selected.id(), MutationVisual.fromDefinition(selected));

        for (MutationDefinition definition : MutationLoader.INSTANCE.getAllMutations()) {
            if (visuals.size() >= MAX_CATALOG_SIZE) break;
            if (!definition.enabled() || definition.hidden()) continue;
            visuals.putIfAbsent(definition.id(), MutationVisual.fromDefinition(definition));
        }
        return new MutationSlotPacket(selectedId, List.copyOf(visuals.values()));
    }

    public static void encode(MutationSlotPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.selectedId, MAX_ID_LENGTH);
        int size = Math.min(MAX_CATALOG_SIZE, message.catalog.size());
        buffer.writeVarInt(size);
        for (int index = 0; index < size; index++) {
            MutationVisual visual = message.catalog.get(index);
            buffer.writeUtf(visual.id(), MAX_ID_LENGTH);
            buffer.writeUtf(visual.nameKey(), MAX_TRANSLATION_KEY_LENGTH);
            buffer.writeUtf(visual.fallbackName(), MAX_NAME_LENGTH);
            buffer.writeUtf(visual.rarity(), MAX_RARITY_LENGTH);
            buffer.writeBoolean(visual.icon() != null);
            if (visual.icon() != null) {
                buffer.writeUtf(visual.icon().toString(), MAX_ICON_LENGTH);
            }
        }
    }

    public static MutationSlotPacket decode(FriendlyByteBuf buffer) {
        String selectedId = buffer.readUtf(MAX_ID_LENGTH);
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_CATALOG_SIZE) {
            throw new IllegalArgumentException("Invalid mutation visual catalog size: " + size);
        }
        List<MutationVisual> catalog = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            String id = buffer.readUtf(MAX_ID_LENGTH);
            String nameKey = buffer.readUtf(MAX_TRANSLATION_KEY_LENGTH);
            String fallbackName = buffer.readUtf(MAX_NAME_LENGTH);
            String rarity = buffer.readUtf(MAX_RARITY_LENGTH);
            ResourceLocation icon = null;
            if (buffer.readBoolean()) {
                icon = ResourceLocation.tryParse(buffer.readUtf(MAX_ICON_LENGTH));
            }
            catalog.add(new MutationVisual(id, nameKey, fallbackName, rarity, icon));
        }
        return new MutationSlotPacket(selectedId, catalog);
    }

    public static void handle(MutationSlotPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientHandler.handle(message));
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientHandler {
        private static void handle(MutationSlotPacket message) {
            SlotMachineOverlay.startAnimation(message.selectedId, message.catalog);
        }
    }

    public String getSelectedId() { return selectedId; }
    public List<MutationVisual> getCatalog() { return catalog; }
}
