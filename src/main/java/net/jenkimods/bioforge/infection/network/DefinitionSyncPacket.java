package net.jenkimods.bioforge.infection.network;

import net.jenkimods.bioforge.api.definition.PathogenDefinition;
import net.jenkimods.bioforge.api.definition.SymptomDefinition;
import net.jenkimods.bioforge.api.definition.TransmissionDefinition;
import net.jenkimods.bioforge.definition.BioForgeClientDefinitionCache;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.jenkimods.bioforge.network.compat.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public record DefinitionSyncPacket(
        long generation,
        Map<ResourceLocation, BioForgeClientDefinitionCache.PathogenView> pathogens,
        Map<ResourceLocation, BioForgeClientDefinitionCache.TransmissionView> transmissions,
        Map<ResourceLocation, BioForgeClientDefinitionCache.SymptomView> symptoms
) {
    private static final int MAX_DEFINITIONS = 4096;
    private static final int MAX_LIST = 512;

    public static DefinitionSyncPacket current() {
        Map<ResourceLocation, BioForgeClientDefinitionCache.PathogenView> pathogens = new LinkedHashMap<>();
        for (PathogenDefinition definition : BioForgeDefinitionManager.PATHOGENS.values()) {
            pathogens.put(definition.id(), new BioForgeClientDefinitionCache.PathogenView(
                    definition.translationKey(), definition.color(), definition.environmental(),
                    List.copyOf(definition.allowedTransmissions())));
        }
        Map<ResourceLocation, BioForgeClientDefinitionCache.TransmissionView> transmissions = new LinkedHashMap<>();
        for (TransmissionDefinition definition : BioForgeDefinitionManager.TRANSMISSIONS.values()) {
            transmissions.put(definition.id(), new BioForgeClientDefinitionCache.TransmissionView(
                    definition.translationKey(), List.copyOf(definition.behaviors())));
        }
        Map<ResourceLocation, BioForgeClientDefinitionCache.SymptomView> symptoms = new LinkedHashMap<>();
        for (SymptomDefinition definition : BioForgeDefinitionManager.SYMPTOMS.values()) {
            symptoms.put(definition.id(), new BioForgeClientDefinitionCache.SymptomView(
                    definition.translationKey(), definition.valueType().name().toLowerCase(),
                    definition.defaultValue().toString(), definition.minimum(), definition.maximum(),
                    definition.allowedValues(), List.copyOf(definition.behaviors())));
        }
        long generation = BioForgeDefinitionManager.generation();
        return new DefinitionSyncPacket(generation, pathogens, transmissions, symptoms);
    }

    public static void encode(DefinitionSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.generation);
        buffer.writeVarInt(packet.pathogens.size());
        packet.pathogens.forEach((id, view) -> {
            buffer.writeResourceLocation(id);
            buffer.writeUtf(view.translationKey(), 256);
            buffer.writeInt(view.color());
            buffer.writeBoolean(view.environmental());
            writeIds(buffer, view.allowedTransmissions());
        });
        buffer.writeVarInt(packet.transmissions.size());
        packet.transmissions.forEach((id, view) -> {
            buffer.writeResourceLocation(id);
            buffer.writeUtf(view.translationKey(), 256);
            writeIds(buffer, view.behaviors());
        });
        buffer.writeVarInt(packet.symptoms.size());
        packet.symptoms.forEach((id, view) -> {
            buffer.writeResourceLocation(id);
            buffer.writeUtf(view.translationKey(), 256);
            buffer.writeUtf(view.valueType(), 32);
            buffer.writeUtf(view.defaultValue(), 1024);
            buffer.writeDouble(view.minimum());
            buffer.writeDouble(view.maximum());
            buffer.writeCollection(view.allowedValues(), (target, value) -> target.writeUtf(value, 128));
            writeIds(buffer, view.behaviors());
        });
    }

    public static DefinitionSyncPacket decode(FriendlyByteBuf buffer) {
        long generation = buffer.readLong();
        int pathogenCount = count(buffer, "pathogens");
        Map<ResourceLocation, BioForgeClientDefinitionCache.PathogenView> pathogens = new LinkedHashMap<>();
        for (int i = 0; i < pathogenCount; i++) {
            pathogens.put(buffer.readResourceLocation(), new BioForgeClientDefinitionCache.PathogenView(
                    buffer.readUtf(256), buffer.readInt(), buffer.readBoolean(), readIds(buffer)));
        }
        int transmissionCount = count(buffer, "transmissions");
        Map<ResourceLocation, BioForgeClientDefinitionCache.TransmissionView> transmissions = new LinkedHashMap<>();
        for (int i = 0; i < transmissionCount; i++) {
            transmissions.put(buffer.readResourceLocation(), new BioForgeClientDefinitionCache.TransmissionView(
                    buffer.readUtf(256), readIds(buffer)));
        }
        int symptomCount = count(buffer, "symptoms");
        Map<ResourceLocation, BioForgeClientDefinitionCache.SymptomView> symptoms = new LinkedHashMap<>();
        for (int i = 0; i < symptomCount; i++) {
            ResourceLocation id = buffer.readResourceLocation();
            String translation = buffer.readUtf(256);
            String type = buffer.readUtf(32);
            String fallback = buffer.readUtf(1024);
            double minimum = buffer.readDouble();
            double maximum = buffer.readDouble();
            int allowedCount = listCount(buffer, "allowed values");
            List<String> allowed = new ArrayList<>(allowedCount);
            for (int j = 0; j < allowedCount; j++) allowed.add(buffer.readUtf(128));
            symptoms.put(id, new BioForgeClientDefinitionCache.SymptomView(
                    translation, type, fallback, minimum, maximum, allowed, readIds(buffer)));
        }
        return new DefinitionSyncPacket(generation, pathogens, transmissions, symptoms);
    }

    public static void handle(DefinitionSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> BioForgeClientDefinitionCache.set(
                new BioForgeClientDefinitionCache.Snapshot(packet.generation,
                        packet.pathogens, packet.transmissions, packet.symptoms)));
        context.setPacketHandled(true);
    }

    private static void writeIds(FriendlyByteBuf buffer, List<ResourceLocation> values) {
        buffer.writeVarInt(values.size());
        values.forEach(buffer::writeResourceLocation);
    }

    private static List<ResourceLocation> readIds(FriendlyByteBuf buffer) {
        int count = listCount(buffer, "resource ids");
        List<ResourceLocation> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(buffer.readResourceLocation());
        return List.copyOf(values);
    }

    private static int count(FriendlyByteBuf buffer, String kind) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_DEFINITIONS) throw new IllegalArgumentException("Invalid " + kind + " count");
        return count;
    }

    private static int listCount(FriendlyByteBuf buffer, String kind) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_LIST) throw new IllegalArgumentException("Invalid " + kind + " count");
        return count;
    }
}
