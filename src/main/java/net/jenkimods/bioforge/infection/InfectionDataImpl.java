package net.jenkimods.bioforge.infection;

import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.infection.symptoms.EntitySymptoms;
import net.jenkimods.bioforge.infection.lifecycle.InfectionLifecycleState;
import net.jenkimods.bioforge.infection.lifecycle.InfectionLifecycleRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.*;

public class InfectionDataImpl implements InfectionData {
    private boolean infected = false;
    @Nullable private PathogenType pathogenType = null;
    @Nullable private ResourceLocation pathogenId = null;
    private final Set<InfectionType> infectionTypes = EnumSet.noneOf(InfectionType.class);
    private final Set<ResourceLocation> transmissionIds = new LinkedHashSet<>();
    private final EntitySymptoms symptoms = new EntitySymptoms();
    private final InfectionLifecycleState lifecycle = new InfectionLifecycleState();
    private final Map<String, StrainImmunity> strainImmunities = new LinkedHashMap<>();

    @Override public boolean isInfected() { return infected; }
    @Override public @Nullable PathogenType getPathogenType() { return pathogenType; }
    @Override public @Nullable ResourceLocation getPathogenId() { return pathogenId; }
    @Override public Set<InfectionType> getInfectionTypes() {
        infectionTypes.removeIf(type -> !BioForgeServerConfig.isTransmissionEnabled(type));
        return infectionTypes;
    }
    @Override public Set<ResourceLocation> getTransmissionIds() {
        LinkedHashSet<ResourceLocation> enabled = transmissionIds.stream()
                .filter(id -> BioForgeDefinitionManager.TRANSMISSIONS.get(id).isPresent())
                .filter(id -> {
                    InfectionType legacy = BioForgeIds.legacyTransmission(id);
                    return legacy == null || BioForgeServerConfig.isTransmissionEnabled(legacy);
                }).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(enabled);
    }
    @Override public EntitySymptoms getSymptoms() { return symptoms; }
    @Override public InfectionLifecycleState getLifecycle() { return lifecycle; }
    @Override public Collection<StrainImmunity> getStrainImmunities() {
        return Collections.unmodifiableCollection(strainImmunities.values());
    }

    @Override
    public void setInfected(boolean infected) {
        boolean newlyInfected = infected && !this.infected;
        this.infected = infected;
        if (newlyInfected) lifecycle.reset(lifecycle.profileId());
        if (!infected) {
            pathogenType = null;
            pathogenId = null;
            infectionTypes.clear();
            transmissionIds.clear();
            symptoms.clearAll();
            lifecycle.reset(InfectionLifecycleState.DEFAULT_PROFILE);
        }
    }

    @Override public void setPathogenType(@Nullable PathogenType pathogenType) {
        this.pathogenType = pathogenType;
        this.pathogenId = pathogenType == null ? null : BioForgeIds.pathogen(pathogenType);
    }
    @Override public void setPathogenId(@Nullable ResourceLocation pathogenId) {
        this.pathogenId = pathogenId == null ? null
                : BioForgeDefinitionManager.PATHOGENS.canonicalId(pathogenId);
        PathogenType legacy = BioForgeIds.legacyPathogen(this.pathogenId);
        this.pathogenType = this.pathogenId == null ? null
                : legacy == null ? PathogenType.UNIVERSAL : legacy;
        if (this.pathogenId != null
                && lifecycle.profileId().equals(InfectionLifecycleState.DEFAULT_PROFILE)) {
            lifecycle.setProfileId(InfectionLifecycleRegistry.INSTANCE
                    .profileForPathogen(this.pathogenId));
        }
    }
    @Override public void addInfectionType(InfectionType type) {
        if (BioForgeServerConfig.isTransmissionEnabled(type)) {
            infectionTypes.add(type);
            transmissionIds.add(BioForgeIds.transmission(type));
        }
    }
    @Override public void removeInfectionType(InfectionType type) {
        infectionTypes.remove(type);
        transmissionIds.remove(BioForgeIds.transmission(type));
    }
    @Override public void addTransmissionId(ResourceLocation transmissionId) {
        ResourceLocation canonical = BioForgeDefinitionManager.TRANSMISSIONS.canonicalId(transmissionId);
        InfectionType legacy = BioForgeIds.legacyTransmission(canonical);
        if (legacy != null && !BioForgeServerConfig.isTransmissionEnabled(legacy)) return;
        transmissionIds.add(canonical);
        if (legacy != null) infectionTypes.add(legacy);
    }
    @Override public void removeTransmissionId(ResourceLocation transmissionId) {
        ResourceLocation canonical = BioForgeDefinitionManager.TRANSMISSIONS.canonicalId(transmissionId);
        transmissionIds.remove(canonical);
        InfectionType legacy = BioForgeIds.legacyTransmission(canonical);
        if (legacy != null) infectionTypes.remove(legacy);
    }

    @Override public void clearInfection() { setInfected(false); }

    @Override
    public boolean hasStrainImmunity(String fingerprint) {
        return getStrainProtection(fingerprint) >= 0.999F;
    }

    @Override
    public float getStrainProtection(String fingerprint) {
        StrainImmunity protection = strainImmunities.get(
                StrainImmunity.normalizeFingerprint(fingerprint));
        return protection != null && protection.isActive()
                ? protection.strength() : 0.0F;
    }

    @Override
    public void grantStrainProtection(String fingerprint, String displayName,
                                      int durationTicks, float strength) {
        StrainImmunity incoming = new StrainImmunity(
                fingerprint, displayName, durationTicks, strength);
        if (!incoming.isActive()) return;
        StrainImmunity current = strainImmunities.get(incoming.fingerprint());
        if (current != null && current.isActive()) {
            if (incoming.strength() < current.strength()) return;
            if (incoming.strength() == current.strength()
                    && incoming.remainingTicks() <= current.remainingTicks()) return;
        }
        strainImmunities.put(incoming.fingerprint(), incoming);
    }

    @Override
    public boolean tickStrainImmunities() {
        boolean expired = false;
        Iterator<Map.Entry<String, StrainImmunity>> iterator = strainImmunities.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, StrainImmunity> entry = iterator.next();
            StrainImmunity next = entry.getValue().tick();
            if (!next.isActive()) {
                iterator.remove();
                expired = true;
            } else {
                entry.setValue(next);
            }
        }
        return expired;
    }

    @Override
    public void copyStrainImmunitiesFrom(InfectionData source) {
        strainImmunities.clear();
        if (source == null) return;
        for (StrainImmunity immunity : source.getStrainImmunities()) {
            grantStrainProtection(immunity.fingerprint(), immunity.displayName(),
                    immunity.remainingTicks(), immunity.strength());
        }
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("BioForgeDataVersion", 2);
        tag.putBoolean("Infected", infected);
        if (pathogenId != null) {
            tag.putString("PathogenId", pathogenId.toString());
            PathogenType legacy = BioForgeIds.legacyPathogen(pathogenId);
            if (legacy != null) tag.putString("PathogenType", legacy.name());
        }
        StringJoiner joiner = new StringJoiner(",");
        for (InfectionType t : infectionTypes) {
            if (BioForgeServerConfig.isTransmissionEnabled(t)) joiner.add(t.name());
        }
        tag.putString("InfectionTypes", joiner.toString());
        StringJoiner idJoiner = new StringJoiner(",");
        transmissionIds.forEach(id -> idJoiner.add(id.toString()));
        tag.putString("TransmissionIds", idJoiner.toString());
        tag.put("Symptoms", symptoms.serializeNBT());
        tag.put("Lifecycle", lifecycle.serializeNBT());
        ListTag immunities = new ListTag();
        for (StrainImmunity immunity : strainImmunities.values()) {
            if (!immunity.isActive()) continue;
            CompoundTag immunityTag = new CompoundTag();
            immunityTag.putString("Fingerprint", immunity.fingerprint());
            immunityTag.putString("Name", immunity.displayName());
            immunityTag.putInt("RemainingTicks", immunity.remainingTicks());
            immunityTag.putFloat("Strength", immunity.strength());
            immunities.add(immunityTag);
        }
        tag.put("StrainImmunities", immunities);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        infected = tag.getBoolean("Infected");
        if (tag.contains("PathogenId")) {
            setPathogenId(ResourceLocation.tryParse(tag.getString("PathogenId")));
        } else {
            setPathogenType(tag.contains("PathogenType")
                    ? PathogenType.fromName(tag.getString("PathogenType")) : null);
        }
        infectionTypes.clear();
        transmissionIds.clear();
        if (tag.contains("TransmissionIds")) {
            String raw = tag.getString("TransmissionIds");
            if (!raw.isEmpty()) {
                for (String part : raw.split(",")) {
                    ResourceLocation id = ResourceLocation.tryParse(part.trim());
                    if (id != null) {
                        transmissionIds.add(BioForgeDefinitionManager.TRANSMISSIONS.canonicalId(id));
                        InfectionType legacy = BioForgeIds.legacyTransmission(id);
                        if (legacy != null && BioForgeServerConfig.isTransmissionEnabled(legacy)) {
                            infectionTypes.add(legacy);
                        }
                    }
                }
            }
        } else if (tag.contains("InfectionTypes")) {
            String raw = tag.getString("InfectionTypes");
            if (!raw.isEmpty()) {
                for (String part : raw.split(",")) {
                    InfectionType it = InfectionType.fromName(part);
                    if (it != null && BioForgeServerConfig.isTransmissionEnabled(it)) {
                        infectionTypes.add(it);
                        transmissionIds.add(BioForgeIds.transmission(it));
                    }
                }
            }
        }
        if (tag.contains("Symptoms")) {
            symptoms.deserializeNBT(tag.getCompound("Symptoms"), BioForgeSymptoms.deserializer());
        }
        if (tag.contains("Lifecycle", Tag.TAG_COMPOUND)) {
            lifecycle.deserializeNBT(tag.getCompound("Lifecycle"));
        } else if (infected) {
            lifecycle.reset(InfectionLifecycleState.DEFAULT_PROFILE);
        }
        strainImmunities.clear();
        if (tag.contains("StrainImmunities", Tag.TAG_LIST)) {
            ListTag immunities = tag.getList("StrainImmunities", Tag.TAG_COMPOUND);
            for (int i = 0; i < immunities.size(); i++) {
                CompoundTag immunityTag = immunities.getCompound(i);
                grantStrainProtection(immunityTag.getString("Fingerprint"),
                        immunityTag.getString("Name"),
                        immunityTag.getInt("RemainingTicks"),
                        immunityTag.contains("Strength")
                                ? immunityTag.getFloat("Strength") : 1.0F);
            }
        }
    }
}
