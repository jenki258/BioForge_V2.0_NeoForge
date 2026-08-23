package net.jenkimods.bioforge.api.vaccine;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.world.vaccine.VaccineMakerBlockEntity;
import net.jenkimods.bioforge.world.vaccine.VaccineMakerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;





public final class VaccineMakerPageRegistry {
    public static final ResourceLocation CRISPR =
            ResourceLocation.tryBuild(BioForge.MODID, "crispr");
    public static final ResourceLocation JOURNAL =
            ResourceLocation.tryBuild(BioForge.MODID, "journal");
    public static final ResourceLocation CRAFT =
            ResourceLocation.tryBuild(BioForge.MODID, "craft");
    public static final ResourceLocation CORRECTION =
            ResourceLocation.tryBuild(BioForge.MODID, "correction");
    public static final int CORRECTION_SYNC_BUTTON =
            VaccineMakerMenu.EXTENSION_BUTTON_BASE;
    public static final int CORRECTION_RESET_BUTTON =
            CORRECTION_SYNC_BUTTON + 1;
    public static final int CORRECTION_READ_BUTTON =
            CORRECTION_RESET_BUTTON + 1;
    public static final int CORRECTION_WRITE_BUTTON =
            CORRECTION_READ_BUTTON + 1;
    public static final int CORRECTION_TARGET_BUTTON_BASE =
            CORRECTION_WRITE_BUTTON + 1;

    private static final Map<ResourceLocation, VaccineMakerPageDefinition> PAGES =
            new LinkedHashMap<>();
    private static boolean builtInsRegistered;
    private static boolean frozen;

    private VaccineMakerPageRegistry() {}

    public static synchronized void bootstrapBuiltIns() {
        if (builtInsRegistered) return;
        builtInsRegistered = true;

        LinkedHashMap<Integer, VaccineMakerPageDefinition.SlotPosition> crispr =
                new LinkedHashMap<>();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 5; column++) {
                crispr.put(row * 5 + column,
                        new VaccineMakerPageDefinition.SlotPosition(
                                10 + column * 24, 18 + row * 32));
            }
        }
        crispr.put(VaccineMakerBlockEntity.CAS_SLOT,
                new VaccineMakerPageDefinition.SlotPosition(142, 44));
        crispr.put(VaccineMakerBlockEntity.REAGENT_SLOT,
                new VaccineMakerPageDefinition.SlotPosition(166, 44));
        crispr.put(VaccineMakerBlockEntity.REPORT_SLOT,
                new VaccineMakerPageDefinition.SlotPosition(190, 44));

        register(new VaccineMakerPageDefinition(
                CRISPR, 0,
                () -> Component.translatable("gui.bioforge.vaccine_maker.page.crispr"),
                () -> new ItemStack(BioForge.CRISPR_CARTRIDGE.get()),
                crispr, null));

        register(new VaccineMakerPageDefinition(
                JOURNAL, 100,
                () -> Component.translatable("gui.bioforge.vaccine_maker.page.journal"),
                () -> new ItemStack(BioForge.CRISPR_NOTES.get()),
                Map.of(), null));

        LinkedHashMap<Integer, VaccineMakerPageDefinition.SlotPosition> craft =
                new LinkedHashMap<>();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 5; column++) {
                craft.put(row * 5 + column,
                        new VaccineMakerPageDefinition.SlotPosition(
                                10 + column * 24, 18 + row * 32));
            }
        }
        craft.put(VaccineMakerBlockEntity.CAS_SLOT,
                new VaccineMakerPageDefinition.SlotPosition(142, 18));
        craft.put(VaccineMakerBlockEntity.SAMPLE_SLOT,
                new VaccineMakerPageDefinition.SlotPosition(166, 18));
        craft.put(VaccineMakerBlockEntity.CARRIER_SLOT,
                new VaccineMakerPageDefinition.SlotPosition(190, 18));
        craft.put(VaccineMakerBlockEntity.REAGENT_SLOT,
                new VaccineMakerPageDefinition.SlotPosition(214, 18));
        craft.put(VaccineMakerBlockEntity.REPORT_SLOT,
                new VaccineMakerPageDefinition.SlotPosition(166, 56));
        craft.put(VaccineMakerBlockEntity.OUTPUT_SLOT,
                new VaccineMakerPageDefinition.SlotPosition(214, 56));

        register(new VaccineMakerPageDefinition(
                CRAFT, 200,
                () -> Component.translatable("gui.bioforge.vaccine_maker.page.craft"),
                () -> new ItemStack(BioForge.VACCINE.get()),
                craft, null));

        register(new VaccineMakerPageDefinition(
                CORRECTION, 300,
                () -> Component.translatable(
                        "gui.bioforge.vaccine_maker.page.correction"),
                () -> new ItemStack(BioForge.GENE_IMPRINT.get()),
                Map.of(VaccineMakerBlockEntity.REPORT_SLOT,
                        new VaccineMakerPageDefinition.SlotPosition(218, 94)),
                (menu, player, buttonId) -> {
                    if (!(player instanceof ServerPlayer serverPlayer)) return false;
                    if (buttonId == CORRECTION_SYNC_BUTTON) {
                        menu.getBlockEntity().sendCorrectionState(
                                serverPlayer, menu.containerId);
                        return true;
                    }
                    if (buttonId == CORRECTION_RESET_BUTTON) {
                        return menu.getBlockEntity().resetCorrectionState(serverPlayer);
                    }
                    if (buttonId == CORRECTION_READ_BUTTON) {
                        return menu.getBlockEntity().readCorrectionDocument(serverPlayer);
                    }
                    if (buttonId == CORRECTION_WRITE_BUTTON) {
                        return menu.getBlockEntity().writeCorrectionDocument(serverPlayer);
                    }
                    int encoded = buttonId - CORRECTION_TARGET_BUTTON_BASE;
                    if (encoded < 0) return false;
                    int targetIndex = encoded / 2;
                    int direction = (encoded & 1) == 0 ? 1 : -1;
                    return menu.getBlockEntity().cycleCorrectionSelection(
                            targetIndex, direction);
                }));
    }

    public static synchronized void register(VaccineMakerPageDefinition definition) {
        if (frozen) throw new IllegalStateException("Vaccine Maker page registry is frozen");
        if (PAGES.containsKey(definition.id())) {
            throw new IllegalArgumentException(
                    "Duplicate Vaccine Maker page: " + definition.id());
        }
        PAGES.put(definition.id(), definition);
    }

    public static synchronized void freeze() {
        frozen = true;
    }

    public static synchronized List<VaccineMakerPageDefinition> pages() {
        List<VaccineMakerPageDefinition> result = new ArrayList<>(PAGES.values());
        result.sort(Comparator.comparingInt(VaccineMakerPageDefinition::order)
                .thenComparing(page -> page.id().toString()));
        return List.copyOf(result);
    }

    public static int indexOf(ResourceLocation id) {
        List<VaccineMakerPageDefinition> pages = pages();
        for (int index = 0; index < pages.size(); index++) {
            if (pages.get(index).id().equals(id)) return index;
        }
        return -1;
    }
}
