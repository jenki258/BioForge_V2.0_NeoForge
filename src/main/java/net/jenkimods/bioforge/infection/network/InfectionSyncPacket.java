package net.jenkimods.bioforge.infection.network;

import net.jenkimods.bioforge.infection.*;
import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.infection.symptoms.SymptomKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.jenkimods.bioforge.network.compat.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class InfectionSyncPacket {
    private static final int MAX_MUTATIONS = 1024;
    private static final int MAX_IMMUNITIES = 256;
    private final boolean infected;
    private final String pathogenType;
    private final List<String> infectionTypes;
    private final Map<String, Object> symptoms;
    private final List<String> mutations;
    private final List<StrainImmunity> immunities;

    public InfectionSyncPacket(boolean infected, String pathogenType, List<String> infectionTypes,
                               Map<String, Object> symptoms, List<String> mutations,
                               List<StrainImmunity> immunities) {
        this.infected = infected;
        this.pathogenType = pathogenType;
        this.infectionTypes = infectionTypes;
        this.symptoms = symptoms;
        this.mutations = mutations;
        this.immunities = immunities;
    }

    public static InfectionSyncPacket fromData(InfectionData data) {
        List<String> types = data.getTransmissionIds().stream().map(ResourceLocation::toString).toList();

        Map<String, Object> symptomMap = new LinkedHashMap<>();
        for (Map.Entry<String, SymptomKey<?>> entry : BioForgeSymptoms.getEnabledSymptomKeys().entrySet()) {
            SymptomKey<?> key = entry.getValue();
            Object value = data.getSymptom(key);
            if (value != null) {
                symptomMap.put(entry.getKey(), value);
            }
        }

        return new InfectionSyncPacket(
                data.isInfected(),
                data.getPathogenId() != null ? data.getPathogenId().toString() : "",
                types,
                symptomMap,
                List.copyOf(data.getSymptoms().getMutations()),
                data.getStrainImmunities().stream().filter(StrainImmunity::isActive)
                        .limit(MAX_IMMUNITIES).toList()
        );
    }

    public static void encode(InfectionSyncPacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.infected);
        buf.writeUtf(pkt.pathogenType);
        buf.writeCollection(pkt.infectionTypes, FriendlyByteBuf::writeUtf);

        Set<Map.Entry<String, Object>> entries = pkt.symptoms.entrySet();
        buf.writeInt(entries.size());
        for (Map.Entry<String, Object> entry : entries) {
            buf.writeUtf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Enum<?> e) {
                buf.writeByte(0);
                buf.writeUtf(e.name());
            } else if (value instanceof Boolean b) {
                buf.writeByte(1);
                buf.writeBoolean(b);
            } else if (value instanceof Float f) {
                buf.writeByte(2);
                buf.writeFloat(f);
            } else {
                buf.writeByte(3);
            }
        }
        List<String> safeMutations = pkt.mutations.stream()
                .filter(mutation -> mutation != null && !mutation.isBlank() && mutation.length() <= 256)
                .limit(MAX_MUTATIONS)
                .toList();
        buf.writeVarInt(safeMutations.size());
        for (String mutation : safeMutations) {
            buf.writeUtf(mutation, 256);
        }
        List<StrainImmunity> safeImmunities = pkt.immunities.stream()
                .filter(StrainImmunity::isActive).limit(MAX_IMMUNITIES).toList();
        buf.writeVarInt(safeImmunities.size());
        for (StrainImmunity immunity : safeImmunities) {
            buf.writeUtf(immunity.fingerprint(), 64);
            buf.writeUtf(immunity.displayName(), StrainImmunity.MAX_NAME_LENGTH);
            buf.writeVarInt(immunity.remainingTicks());
            buf.writeFloat(immunity.strength());
        }
    }

    public static InfectionSyncPacket decode(FriendlyByteBuf buf) {
        boolean infected = buf.readBoolean();
        String pathogenType = buf.readUtf();
        List<String> types = buf.readList(FriendlyByteBuf::readUtf);

        int symptomCount = buf.readInt();
        Map<String, Object> symptoms = new LinkedHashMap<>();
        for (int i = 0; i < symptomCount; i++) {
            String keyId = buf.readUtf();
            byte type = buf.readByte();
            switch (type) {
                case 0 -> symptoms.put(keyId, buf.readUtf());
                case 1 -> symptoms.put(keyId, buf.readBoolean());
                case 2 -> symptoms.put(keyId, buf.readFloat());
            }
        }

        int mutationCount = buf.readVarInt();
        if (mutationCount < 0 || mutationCount > MAX_MUTATIONS) {
            throw new IllegalArgumentException("Invalid mutation count: " + mutationCount);
        }
        List<String> mutations = new ArrayList<>(mutationCount);
        for (int i = 0; i < mutationCount; i++) {
            mutations.add(buf.readUtf(256));
        }

        int immunityCount = buf.readVarInt();
        if (immunityCount < 0 || immunityCount > MAX_IMMUNITIES) {
            throw new IllegalArgumentException("Invalid immunity count: " + immunityCount);
        }
        List<StrainImmunity> immunities = new ArrayList<>(immunityCount);
        for (int i = 0; i < immunityCount; i++) {
            immunities.add(new StrainImmunity(buf.readUtf(64),
                    buf.readUtf(StrainImmunity.MAX_NAME_LENGTH), buf.readVarInt(),
                    buf.readFloat()));
        }

        return new InfectionSyncPacket(infected, pathogenType, types, symptoms, mutations,
                immunities);
    }

    public static void handle(InfectionSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ResourceLocation pt = pkt.pathogenType.isEmpty() ? null : parseId(pkt.pathogenType);
            List<ResourceLocation> types = new ArrayList<>();
            for (String s : pkt.infectionTypes) {
                ResourceLocation id = parseId(s);
                if (id != null) types.add(id);
            }

            Map<String, Object> symptoms = pkt.symptoms;
            InfectionClientCache.set(pkt.infected, pt, types, symptoms, pkt.mutations,
                    pkt.immunities);
        });
        ctx.setPacketHandled(true);
    }

    private static ResourceLocation parseId(String value) {
        try { return BioForgeIds.parse(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
