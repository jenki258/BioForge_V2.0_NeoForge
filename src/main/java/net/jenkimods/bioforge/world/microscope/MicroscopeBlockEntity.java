package net.jenkimods.bioforge.world.microscope;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.registry.BioForgeSounds;
import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.jenkimods.bioforge.block.MicroscopeBlock;
import net.jenkimods.bioforge.crispr.BioForgeResearchData;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.infection.naming.StrainNamingManager;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.infection.symptoms.SymptomKey;
import net.jenkimods.bioforge.item.crispr.GeneImprintItem;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.jenkimods.bioforge.vaccine.VaccineBloodAssay;
import net.jenkimods.bioforge.vaccine.VaccineCorrectionProfile;
import net.jenkimods.bioforge.vaccine.StrainFingerprint;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MicroscopeBlockEntity extends BlockEntity implements MenuProvider {
    public static final int IDENTIFY_GENE_BUTTON = 0;
    private static final int ITEM_CALIBRATION_VERSION = 1;
    private static final String ITEM_CALIBRATION_CHANNEL =
            "microscope_calibration";
    private static final ResourceLocation DEFAULT_CORRECTION_PROFILE =
            ResourceLocation.tryBuild(BioForge.MODID, "default");

    private final ItemStackHandler itemHandler = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                resetCalibrationSession();
                getCalibrationFor(itemHandler.getStackInSlot(0));
                updateLitState();
                syncBlockEntity();
                syncToViewers();
            }
        }
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return stack.getItem() instanceof GeneImprintItem
                    || VaccineBloodAssay.isAssay(stack)
                    || !MicroscopeSymptomConfig.INSTANCE.getEntriesFor(stack).isEmpty();
        }
    };

    private float visualKnobAngle;
    private float visualLensAngle;
    private List<CalibrationSlider> activeCalibration = List.of();
    private float[] calibrationValues = new float[0];
    private final Map<String, Float> calibrationPositions =
            new LinkedHashMap<>();

    public MicroscopeBlockEntity(BlockPos pos, BlockState state) {
        super(BioForge.MICROSCOPE_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateLitState();
    }

    private void updateLitState() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        boolean shouldBeLit = !itemHandler.getStackInSlot(0).isEmpty();
        if (state.hasProperty(MicroscopeBlock.LIT)
                && state.getValue(MicroscopeBlock.LIT) != shouldBeLit) {
            level.setBlock(
                    worldPosition,
                    state.setValue(MicroscopeBlock.LIT, shouldBeLit),
                    Block.UPDATE_ALL
            );
        }
    }

    private void syncToViewers() {
        if (level == null) return;
        ItemStack stack = itemHandler.getStackInSlot(0);
        List<MicroscopeSymptomEntry> entries = MicroscopeSymptomConfig.INSTANCE.getEntriesFor(stack);
        List<CalibrationSlider> calibration = getCalibrationFor(stack);
        Map<String, Object> symptoms = getCurrentSymptoms(entries);
        String visibility = getCurrentVisibility();
        MicroscopeSyncPacket packet = new MicroscopeSyncPacket(
                symptoms, visibility, entries, calibration,
                getCalibrationValues(),
                VaccineBloodAssay.visibleResultPermille(stack));
        for (Player player : level.players()) {
            if (player.containerMenu instanceof MicroscopeMenu menu && menu.getBlockEntity() == this) {
                MicroscopeNetwork.sendToPlayer(packet, (ServerPlayer) player);
            }
        }
    }

    private List<CalibrationSlider> getCalibrationFor(ItemStack stack) {
        if (!activeCalibration.isEmpty()) return activeCalibration;
        List<CalibrationSlider> definitions = calibrationDefinitions(stack);
        if (definitions.isEmpty()) return List.of();
        List<CalibrationSlider> resolved =
                resolveItemCalibration(stack, definitions);
        activeCalibration = List.copyOf(resolved);
        calibrationValues = new float[resolved.size()];
        for (int index = 0; index < resolved.size(); index++) {
            CalibrationSlider slider = resolved.get(index);
            float midpoint =
                    (slider.rangeMin() + slider.rangeMax()) / 2.0F;
            float stored = calibrationPositions.getOrDefault(
                    slider.nameKey(), midpoint);
            if (!Float.isFinite(stored)) stored = midpoint;
            float value = Mth.clamp(
                    stored, slider.rangeMin(), slider.rangeMax());
            calibrationValues[index] = value;
            calibrationPositions.put(slider.nameKey(), value);
        }
        return activeCalibration;
    }

    private List<CalibrationSlider> resolveItemCalibration(
            ItemStack stack, List<CalibrationSlider> definitions) {
        List<CalibrationSlider> stored =
                readItemCalibration(stack, definitions);
        if (!stored.isEmpty()) return stored;

        List<CalibrationSlider> resolved =
                new ArrayList<>(definitions.size());
        for (CalibrationSlider slider : definitions) {
            float target = slider.target();
            if (slider.randomTarget()) {
                net.minecraft.util.RandomSource random = level == null
                        ? net.minecraft.util.RandomSource.create() : level.random;
                target = slider.rangeMin() + random.nextFloat()
                        * (slider.rangeMax() - slider.rangeMin());
            }
            resolved.add(new CalibrationSlider(
                    slider.nameKey(), target, slider.rangeMin(),
                    slider.rangeMax(), slider.randomTarget()));
        }
        if (!stack.isEmpty()
                && (level == null || !level.isClientSide())) {
            writeItemCalibration(stack, resolved);
            setChanged();
        }
        return resolved;
    }

    private static List<CalibrationSlider> readItemCalibration(
            ItemStack stack, List<CalibrationSlider> definitions) {
        if (stack.isEmpty()) return List.of();
        CompoundTag stored = NbtObfuscator.readCompound(stack, ITEM_CALIBRATION_CHANNEL);
        if (stored == null
                || stored.getInt("Version") != ITEM_CALIBRATION_VERSION) {
            return List.of();
        }
        ListTag entries = stored.getList("Sliders", Tag.TAG_COMPOUND);
        if (entries.size() != definitions.size()) return List.of();
        List<CalibrationSlider> resolved =
                new ArrayList<>(definitions.size());
        for (int index = 0; index < definitions.size(); index++) {
            CalibrationSlider definition = definitions.get(index);
            CompoundTag entry = entries.getCompound(index);
            if (!definition.nameKey().equals(entry.getString("Name"))
                    || !entry.contains("Target", Tag.TAG_ANY_NUMERIC)
                    || !entry.contains("Minimum", Tag.TAG_ANY_NUMERIC)
                    || !entry.contains("Maximum", Tag.TAG_ANY_NUMERIC)
                    || Math.abs(entry.getFloat("Minimum")
                    - definition.rangeMin()) > 0.0001F
                    || Math.abs(entry.getFloat("Maximum")
                    - definition.rangeMax()) > 0.0001F) {
                return List.of();
            }
            float target = entry.getFloat("Target");
            if (!Float.isFinite(target)
                    || target < definition.rangeMin()
                    || target > definition.rangeMax()) {
                return List.of();
            }
            resolved.add(new CalibrationSlider(
                    definition.nameKey(), target,
                    definition.rangeMin(), definition.rangeMax(),
                    definition.randomTarget()));
        }
        return resolved;
    }

    private static void writeItemCalibration(
            ItemStack stack, List<CalibrationSlider> sliders) {
        CompoundTag stored = new CompoundTag();
        stored.putInt("Version", ITEM_CALIBRATION_VERSION);
        ListTag entries = new ListTag();
        for (CalibrationSlider slider : sliders) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Name", slider.nameKey());
            entry.putFloat("Target", slider.target());
            entry.putFloat("Minimum", slider.rangeMin());
            entry.putFloat("Maximum", slider.rangeMax());
            entries.add(entry);
        }
        stored.put("Sliders", entries);
        NbtObfuscator.writeCompoundDeterministic(
                stack, ITEM_CALIBRATION_CHANNEL, stored);
    }

    private List<CalibrationSlider> calibrationDefinitions(ItemStack stack) {
        if (VaccineBloodAssay.isAssay(stack)) {
            VaccineCorrectionProfile.AssaySettings settings = BioForgeResearchData
                    .correctionProfile(DEFAULT_CORRECTION_PROFILE)
                    .map(VaccineCorrectionProfile::assay)
                    .orElse(VaccineCorrectionProfile.AssaySettings.DEFAULT);
            List<CalibrationSlider> sliders = new ArrayList<>();
            for (String name : settings.calibrationSliders()) {
                String nameKey = name.startsWith("microscope.calibration.")
                        ? name : "microscope.calibration." + name;
                sliders.add(new CalibrationSlider(nameKey, 0.5F,
                        0.0F, 1.0F, true));
            }
            return sliders;
        }
        GeneImprintItem.Data imprint = GeneImprintItem.read(stack);
        if (imprint != null && !imprint.identified()) {
            return List.of(
                    new CalibrationSlider("microscope.calibration.focus", 0.5f,
                            0.0f, 1.0f, true),
                    new CalibrationSlider("microscope.calibration.contrast", 0.5f,
                            0.0f, 1.0f, true),
                    new CalibrationSlider("microscope.calibration.spectrum", 0.5f,
                            0.0f, 1.0f, true)
            );
        }
        return MicroscopeSymptomConfig.INSTANCE.getCalibrationFor(stack);
    }

    public boolean handleButton(Player player, int buttonId) {
        if (buttonId != IDENTIFY_GENE_BUTTON) return false;
        ItemStack stack = itemHandler.getStackInSlot(0);
        if (VaccineBloodAssay.isAssay(stack)
                && !VaccineBloodAssay.isScanned(stack)) {
            if (!isCalibrationComplete()) {
                player.displayClientMessage(Component.translatable(
                        "message.bioforge.microscope.calibration_incomplete")
                        .withStyle(ChatFormatting.RED), true);
                return false;
            }
            if (!VaccineBloodAssay.markScanned(stack)) return false;
            VaccineBloodAssay.Data assay = VaccineBloodAssay.read(stack);
            if (player instanceof ServerPlayer researcher && assay != null) {
                StrainNamingManager.discoverResearch(
                        researcher, assay.sampleFingerprint());
            }
            setChanged();
            syncBlockEntity();
            syncToViewers();
            player.displayClientMessage(Component.translatable(
                    "message.bioforge.microscope.assay_complete")
                    .withStyle(ChatFormatting.AQUA), true);
            playAnalysisCompleteSound();
            return true;
        }
        if (!isCalibrationComplete()) {
            player.displayClientMessage(Component.translatable(
                    "message.bioforge.microscope.calibration_incomplete")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (GeneImprintItem.identify(stack)) {
            GeneImprintItem.Data imprint = GeneImprintItem.read(stack);
            if (player instanceof ServerPlayer researcher && imprint != null) {
                StrainNamingManager.discoverResearch(researcher,
                        StrainFingerprint.ofPayload(imprint.strainPayload()));
            }
            resetCalibrationSession();
            setChanged();
            syncBlockEntity();
            syncToViewers();
            if (imprint != null) {
                player.displayClientMessage(Component.translatable(
                        "message.bioforge.gene_imprint.identified",
                        Component.translatable("vaccine.category."
                                + imprint.category().serializedName()),
                        imprint.target()).withStyle(ChatFormatting.AQUA), true);
            }
            playAnalysisCompleteSound();
            return true;
        }
        String samplePayload = NbtObfuscator.readInfection(stack);
        if (samplePayload == null || samplePayload.isBlank()) {
            samplePayload = NbtObfuscator.readString(stack);
        }
        if (samplePayload != null && !samplePayload.isBlank()
                && StrainData.parse(samplePayload).getPathogenId() != null) {
            if (player instanceof ServerPlayer researcher) {
                StrainNamingManager.discoverResearch(researcher,
                        StrainFingerprint.ofPayload(samplePayload));
            }
            player.displayClientMessage(Component.translatable(
                    "message.bioforge.microscope.sample_researched")
                    .withStyle(ChatFormatting.AQUA), true);
            playAnalysisCompleteSound();
            return true;
        }
        return false;
    }

    private void playAnalysisCompleteSound() {
        if (level != null) {
            level.playSound(null, worldPosition, BioForgeSounds.TESTING_COMPLETE.get(),
                    SoundSource.BLOCKS, 0.8F, 1.0F);
        }
    }

    public void updateVisualCalibration(int sliderIndex, float normalizedValue) {
        float normalized = Mth.clamp(normalizedValue, 0.0F, 1.0F);
        List<CalibrationSlider> calibration = getCalibrationFor(
                itemHandler.getStackInSlot(0));
        if (sliderIndex < 0 || sliderIndex >= calibration.size()) return;
        CalibrationSlider slider = calibration.get(sliderIndex);
        calibrationValues[sliderIndex] = slider.rangeMin()
                + normalized * (slider.rangeMax() - slider.rangeMin());
        calibrationPositions.put(
                slider.nameKey(), calibrationValues[sliderIndex]);
        visualKnobAngle = Mth.lerp(normalized, -35.0F, 35.0F);
        visualLensAngle = Mth.positiveModulo(
                Math.floorMod(sliderIndex, 3) * 120.0F
                        + normalized * 24.0F,
                360.0F
        );
        setChanged();
        syncBlockEntity();
        syncToViewers();
    }

    private boolean isCalibrationComplete() {
        List<CalibrationSlider> calibration = getCalibrationFor(
                itemHandler.getStackInSlot(0));
        if (calibration.isEmpty() || calibrationValues.length != calibration.size()) {
            return calibration.isEmpty();
        }
        for (int index = 0; index < calibration.size(); index++) {
            if (!calibration.get(index).isWithinTolerance(calibrationValues[index])) {
                return false;
            }
        }
        return true;
    }

    private void resetCalibrationSession() {
        activeCalibration = List.of();
        calibrationValues = new float[0];
    }

    private void syncBlockEntity() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        level.sendBlockUpdated(
                worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    private Map<String, Object> getCurrentSymptoms(List<MicroscopeSymptomEntry> entries) {
        Map<String, Object> symptoms = new LinkedHashMap<>();
        ItemStack stack = itemHandler.getStackInSlot(0);
        if (!stack.isEmpty()) {
            CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(stack);
            String strainRaw = NbtObfuscator.readInfection(tag);
            if (strainRaw == null || strainRaw.isEmpty()) {
                strainRaw = NbtObfuscator.readString(tag);
            }
            StrainData strain = strainRaw == null || strainRaw.isEmpty()
                    ? null : StrainData.parse(strainRaw);
            for (MicroscopeSymptomEntry entry : entries) {
                if ("nbt".equals(entry.source())) {
                    if (entry.nbtKey() == null) continue;
                    if (tag.contains(entry.nbtKey())) {
                        int value = tag.getInt(entry.nbtKey());
                        if (entry.matchesCondition(value)) {
                            symptoms.put(entry.symptomKey(), (float) value);
                        }
                    }
                    continue;
                }
                if (strain == null) continue;
                if ("pathogen".equals(entry.source())) {
                    if (entry.isEnum()) {
                        if (strain.getPathogenId() != null) {
                            symptoms.put(entry.symptomKey(),
                                    BioForgeIds.legacyCompatible(strain.getPathogenId()));
                        }
                    } else {
                        String expected = entry.symptomKey().startsWith("pathogen_")
                                ? entry.symptomKey().substring("pathogen_".length())
                                : entry.symptomKey();
                        String actual = strain.getPathogenId() == null ? ""
                                : BioForgeIds.legacyCompatible(strain.getPathogenId());
                        symptoms.put(entry.symptomKey(), actual.equalsIgnoreCase(expected)
                                || strain.getPathogenId() != null
                                && (strain.getPathogenId().toString().equalsIgnoreCase(expected)
                                || strain.getPathogenId().getPath().equalsIgnoreCase(expected)));
                    }
                    continue;
                }
                SymptomKey<?> key = BioForgeSymptoms.getAllSymptomKeys().get(entry.symptomKey());
                if (key == null) continue;
                String raw = strain.getSymptom(entry.symptomKey()).orElse(null);
                if (raw == null) continue;
                if (key.getType().isEnum()) symptoms.put(entry.symptomKey(), raw.toUpperCase());
                else if (key.getType() == Boolean.class) symptoms.put(entry.symptomKey(), Boolean.valueOf(raw));
                else if (key.getType() == Float.class) {
                    try { symptoms.put(entry.symptomKey(), Float.valueOf(raw)); } catch (Exception ignored) {}
                }
            }
        }
        return symptoms;
    }

    private String getCurrentVisibility() {
        ItemStack stack = itemHandler.getStackInSlot(0);
        if (!stack.isEmpty()) {
            String strainRaw = NbtObfuscator.readInfection(stack);
            if (strainRaw == null || strainRaw.isEmpty()) {
                strainRaw = NbtObfuscator.readString(stack);
            }
            if (strainRaw != null && !strainRaw.isEmpty()) {
                StrainData strain = StrainData.parse(strainRaw);
                return strain.getSymptom("microscope_visibility").orElse("NONE");
            }
        }
        return "NONE";
    }

    @Override
    public Component getDisplayName() { return Component.translatable("block.bioforge.microscope"); }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        if (player instanceof ServerPlayer sp) {
            ItemStack stack = itemHandler.getStackInSlot(0);
            List<MicroscopeSymptomEntry> entries = MicroscopeSymptomConfig.INSTANCE.getEntriesFor(stack);
            List<CalibrationSlider> calib = getCalibrationFor(stack);
            MicroscopeNetwork.sendToPlayer(
                    new MicroscopeSyncPacket(getCurrentSymptoms(entries),
                            getCurrentVisibility(), entries, calib,
                            getCalibrationValues(),
                            VaccineBloodAssay.visibleResultPermille(stack)), sp);
        }
        return new MicroscopeMenu(containerId, playerInventory, this);
    }

    @Override
    public void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", itemHandler.serializeNBT(registries));
        tag.putFloat("VisualKnobAngle", visualKnobAngle);
        tag.putFloat("VisualLensAngle", visualLensAngle);
        ListTag storedPositions = new ListTag();
        calibrationPositions.forEach((name, value) -> {
            if (!Float.isFinite(value)) return;
            CompoundTag position = new CompoundTag();
            position.putString("Name", name);
            position.putFloat("Value", value);
            storedPositions.add(position);
        });
        tag.put("CalibrationPositions", storedPositions);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        resetCalibrationSession();
        visualKnobAngle = tag.getFloat("VisualKnobAngle");
        visualLensAngle = tag.getFloat("VisualLensAngle");
        calibrationPositions.clear();
        ListTag storedPositions = tag.getList(
                "CalibrationPositions", Tag.TAG_COMPOUND);
        for (int index = 0; index < storedPositions.size(); index++) {
            CompoundTag position = storedPositions.getCompound(index);
            float value = position.getFloat("Value");
            if (!position.getString("Name").isBlank()
                    && Float.isFinite(value)) {
                calibrationPositions.put(
                        position.getString("Name"), value);
            }
        }
        itemHandler.deserializeNBT(registries, tag.getCompound("Inventory"));
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    public void drops() {
        if (level == null) return;
        ItemStack stack = itemHandler.getStackInSlot(0);
        if (!stack.isEmpty()) Containers.dropItemStack(level, worldPosition.getX()+0.5, worldPosition.getY()+0.5, worldPosition.getZ()+0.5, stack);
    }

    public ItemStackHandler getItemHandler() { return itemHandler; }

    public float getVisualKnobAngle() { return visualKnobAngle; }

    public float getVisualLensAngle() { return visualLensAngle; }

    public float[] getCalibrationValues() {
        return calibrationValues.clone();
    }
}
