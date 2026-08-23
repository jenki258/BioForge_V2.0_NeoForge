package net.jenkimods.bioforge.registry;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.blood.BloodDataImpl;
import net.jenkimods.bioforge.infection.InfectionDataImpl;
import net.jenkimods.bioforge.infection.capability.CropInfectionStorage;
import net.jenkimods.bioforge.item.clipboard.Session;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class BioForgeAttachments {
    private static final String INFECTION_CHANNEL = "entity_infection";
    private static final String CROP_CHANNEL = "crop_infection";

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BioForge.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<BloodDataImpl>> BLOOD = ATTACHMENT_TYPES.register(
            "blood", () -> AttachmentType.builder(BloodDataImpl::new)
                    .serialize(new IAttachmentSerializer<CompoundTag, BloodDataImpl>() {
                        @Override
                        public BloodDataImpl read(IAttachmentHolder holder, CompoundTag tag,
                                                  HolderLookup.Provider provider) {
                            BloodDataImpl data = new BloodDataImpl();
                            data.deserializeNBT(tag);
                            return data;
                        }

                        @Override
                        public CompoundTag write(BloodDataImpl data, HolderLookup.Provider provider) {
                            return data.serializeNBT();
                        }
                    }).build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<InfectionDataImpl>> INFECTION =
            ATTACHMENT_TYPES.register("infection", () -> AttachmentType
                    .builder(InfectionDataImpl::new)
                    .serialize(new IAttachmentSerializer<CompoundTag, InfectionDataImpl>() {
                        @Override
                        public InfectionDataImpl read(IAttachmentHolder holder, CompoundTag tag,
                                                      HolderLookup.Provider provider) {
                            InfectionDataImpl data = new InfectionDataImpl();
                            CompoundTag decoded = NbtObfuscator.readCompound(tag, INFECTION_CHANNEL);
                            data.deserializeNBT(decoded == null ? tag : decoded);
                            return data;
                        }

                        @Override
                        public CompoundTag write(InfectionDataImpl data,
                                                 HolderLookup.Provider provider) {
                            CompoundTag encoded = new CompoundTag();
                            NbtObfuscator.writeCompoundDeterministic(
                                    encoded, INFECTION_CHANNEL, data.serializeNBT());
                            return encoded;
                        }
                    }).build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Session.SessionCapabilityImpl>> CLIPBOARD_SESSION =
            ATTACHMENT_TYPES.register("clipboard_session", () -> AttachmentType
                    .builder(Session.SessionCapabilityImpl::new)
                    .serialize(new IAttachmentSerializer<CompoundTag, Session.SessionCapabilityImpl>() {
                        @Override
                        public Session.SessionCapabilityImpl read(IAttachmentHolder holder,
                                                                  CompoundTag tag,
                                                                  HolderLookup.Provider provider) {
                            Session.SessionCapabilityImpl session =
                                    new Session.SessionCapabilityImpl();
                            if (tag.hasUUID("SessionId")) {
                                session.setId(tag.getUUID("SessionId"));
                            }
                            return session;
                        }

                        @Override
                        public CompoundTag write(Session.SessionCapabilityImpl session,
                                                 HolderLookup.Provider provider) {
                            CompoundTag tag = new CompoundTag();
                            if (session.getId() != null) {
                                tag.putUUID("SessionId", session.getId());
                            }
                            return tag;
                        }
                    }).build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CropInfectionStorage>> CROP_INFECTION =
            ATTACHMENT_TYPES.register("crop_infection", () -> AttachmentType
                    .builder(holder -> new CropInfectionStorage((LevelChunk) holder))
                    .serialize(new IAttachmentSerializer<CompoundTag, CropInfectionStorage>() {
                        @Override
                        public CropInfectionStorage read(IAttachmentHolder holder, CompoundTag tag,
                                                         HolderLookup.Provider provider) {
                            CropInfectionStorage storage =
                                    new CropInfectionStorage((LevelChunk) holder);
                            CompoundTag decoded = NbtObfuscator.readCompound(tag, CROP_CHANNEL);
                            storage.deserializeNBT(decoded == null ? tag : decoded);
                            return storage;
                        }

                        @Override
                        public CompoundTag write(CropInfectionStorage storage,
                                                 HolderLookup.Provider provider) {
                            CompoundTag encoded = new CompoundTag();
                            NbtObfuscator.writeCompoundDeterministic(
                                    encoded, CROP_CHANNEL, storage.serializeNBT());
                            return encoded;
                        }
                    }).build());

    private BioForgeAttachments() {
    }
}
