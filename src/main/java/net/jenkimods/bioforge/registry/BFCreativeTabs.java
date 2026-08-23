package net.jenkimods.bioforge.registry;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class BFCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BioForge.MODID);

    public static final Supplier<CreativeModeTab> MATERIALS_TAB = TABS.register(
            "materials_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.bioforge.materials_tab"))
                    .icon(() -> new ItemStack(BioForge.BLACK_STEEL_INGOT.get()))
                    .displayItems((params, output) -> {
                        output.accept(BioForge.ACTIVATED_CARBON.get());
                        output.accept(BioForge.BLACK_STEEL_BLEND.get());
                        output.accept(BioForge.BLACK_STEEL_INGOT.get());
                        output.accept(BioForge.BLACK_STEEL_NUGGET.get());
                        output.accept(BioForge.BLACK_STEEL_PLATE.get());
                        output.accept(BioForge.BLACK_STEEL_BLOCK_ITEM.get());
                        output.accept(BioForge.REINFORCED_GLASS.get());
                        output.accept(BioForge.AGAR_POWDER.get());
                        output.accept(BioForge.SULFURIC_ACID.get());
                        output.accept(BioForge.STERILIZING_SOLUTION.get());
                        output.accept(BioForge.POLYMER_RESIN.get());
                        output.accept(BioForge.STERILE_POLYMER_SHEET.get());
                        output.accept(BioForge.LABORATORY_GLASSWARE.get());
                        output.accept(BioForge.STERILE_FILTER.get());
                        output.accept(BioForge.OPTICAL_LENS.get());
                        output.accept(BioForge.PRECISION_MECHANISM.get());
                        output.accept(BioForge.ELECTRONIC_CONTROL_UNIT.get());
                        output.accept(BioForge.LABORATORY_FRAME.get());
                        output.accept(BioForge.BIOMEDICAL_PROCESSOR.get());
                        output.accept(BioForge.NEUTRALIZING_AGENT.get());
                        output.accept(BioForge.SURFACTANT_CONCENTRATE.get());
                        output.accept(BioForge.SEALED_BIOFABRIC.get());
                        output.accept(BioForge.STERILE_RUBBER.get());
                        output.accept(BioForge.ACTIVATED_FILTER.get());
                        output.accept(BioForge.RESPIRATOR_VALVE.get());
                        output.accept(BioForge.THERMAL_GEL.get());
                        output.accept(BioForge.INSULATED_LINING.get());
                        output.accept(BioForge.BLACK_STEEL_MESH.get());
                        output.accept(BioForge.CHEMICAL_RESISTANT_COATING.get());
                        output.accept(BioForge.AIRTIGHT_SEAL.get());
                    })
                    .build());

    public static final Supplier<CreativeModeTab> TOOLS_TAB = TABS.register("tools_tab", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.bioforge.tools_tab"))
                    .icon(() -> new ItemStack(BioForge.THERMOMETER_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(BioForge.RESEARCH_JOURNAL.get());
                        output.accept(BioForge.AREA_CONTAMINATION_SCANNER.get());
                        output.accept(BioForge.CLIPBOARD.get());
                        output.accept(BioForge.SWAB.get());
                        output.accept(BioForge.PETRI_DISH.get());
                        output.accept(BioForge.CONTAMINATED_SUBSTRATE_ITEM.get());
                        output.accept(BioForge.THERMOMETER_ITEM.get());
                        output.accept(BioForge.STETHOSCOPE.get());
                        output.accept(BioForge.OTOSCOPE.get());
                        output.accept(BioForge.MIRROR.get());
                        output.accept(BioForge.REFLEX_HAMMER.get());
                        output.accept(BioForge.PULSE_OXIMETER.get());
                        output.accept(BioForge.BONE_SAW.get());
                        output.accept(BioForge.WOODEN_NEEDLE.get());
                        output.accept(BioForge.IRON_NEEDLE.get());
                        output.accept(BioForge.HARDENED_NEEDLE.get());
                        output.accept(BioForge.SYRINGE.get());
                        output.accept(BioForge.BLOOD_SLIDE.get());
                        output.accept(BioForge.TUBE.get());
                    })
                    .build());

    public static final Supplier<CreativeModeTab> REAGENTS_TAB = TABS.register("reagents_tab", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.bioforge.reagents_tab"))
                    .icon(() -> new ItemStack(BioForge.ANTI_A_VIAL.get()))
                    .displayItems((params, output) -> {
                        output.accept(BioForge.ANTI_A_VIAL.get());
                        output.accept(BioForge.ANTI_B_VIAL.get());
                        output.accept(BioForge.ANTI_D_VIAL.get());
                        output.accept(BioForge.PATHOGEN_REAGENT.get());
                        output.accept(BioForge.VISIBILITY_REAGENT.get());
                        output.accept(BioForge.VACCINE.get());
                        output.accept(BioForge.MUTATION_VACCINE.get());
                        output.accept(BioForge.TRANSMISSION_VACCINE.get());
                        output.accept(BioForge.SYMPTOM_VACCINE.get());
                        output.accept(BioForge.RANDOM_MUTATION_VACCINE.get());
                        output.accept(BioForge.VIRAL_SUPPRESSOR_PILL.get());
                        output.accept(BioForge.VIRAL_INHIBITOR_PILL.get());
                        output.accept(BioForge.VIRAL_BLOCKER_PILL.get());
                        output.accept(BioForge.SYMPTOM_TABLET.get());
                        output.accept(BioForge.CRISPR_CARTRIDGE.get());
                        output.accept(BioForge.CAS_MODULE.get());
                        output.accept(BioForge.GENE_IMPRINT.get());
                        output.accept(BioForge.DECALCIFICATION_FLUID.get());
                        output.accept(BioForge.CATALYST_VIAL.get());
                        output.accept(BioForge.NUTRIENT_MEDIUM.get());
                        output.accept(BioForge.LIVE_CULTURE_VIAL.get());
                        output.accept(BioForge.DIRTY_CULTURE_VIAL.get());
                        output.accept(BioForge.WINE_MUST.get());
                        output.accept(BioForge.ETHANOL.get());
                        output.accept(BioForge.WIPES.get());
                        output.accept(BioForge.DECONTAMINATION_FLASK.get());
                        output.accept(BioForge.MEDICAL_MASK.get());
                        output.accept(BioForge.PROTECTIVE_GLOVES.get());
                        output.accept(BioForge.ICE_BAG.get());
                        output.accept(BioForge.MAGMA_BAG.get());
                        output.accept(BioForge.HAZCURE_HELMET.get());
                        output.accept(BioForge.HAZCURE_CHESTPLATE.get());
                        output.accept(BioForge.HAZCURE_LEGGINGS.get());
                        output.accept(BioForge.HAZCURE_BOOTS.get());
                    })
                    .build());

    public static final Supplier<CreativeModeTab> MACHINERY_TAB = TABS.register("machinery_tab", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.bioforge.machinery_tab"))
                    .icon(() -> new ItemStack(BioForge.CENTRIFUGE_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(BioForge.CENTRIFUGE_ITEM.get());
                        output.accept(BioForge.BARREL_PRESS_ITEM.get());
                        output.accept(BioForge.MICROSCOPE_ITEM.get());
                        output.accept(BioForge.INCUBATOR_ITEM.get());
                        output.accept(BioForge.VACCINE_MAKER_ITEM.get());
                        output.accept(BioForge.VIRAL_SCANNER_ITEM.get());
                        output.accept(BioForge.CEILING_VIRAL_SCANNER_ITEM.get());
                        output.accept(BioForge.OPEN_LEFT_VIRAL_SCANNER_ITEM.get());
                        output.accept(BioForge.OPEN_RIGHT_VIRAL_SCANNER_ITEM.get());
                        output.accept(BioForge.AIR_VENT_ITEM.get());
                        output.accept(BioForge.CHEMICAL_SYNTHESIZER_ITEM.get());
                        output.accept(BioForge.STERILIZATION_CHAMBER_ITEM.get());
                        output.accept(BioForge.PHARMA_MIXER_ITEM.get());
                    })
                    .build());

    public static final Supplier<CreativeModeTab> SAMPLES_TAB = TABS.register("samples_tab", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.bioforge.samples_tab"))
                    .icon(() -> new ItemStack(BioForge.BONE_MARROW.get()))
                    .displayItems((params, output) -> {
                        output.accept(BioForge.SPLIT_BONE.get());
                        output.accept(BioForge.BONE_MARROW.get());
                        output.accept(BioForge.WITHERED_SPLIT_BONE.get());
                        output.accept(BioForge.WITHERED_BONE_MARROW.get());
                        output.accept(BioForge.PLASMA_SAMPLE.get());
                        output.accept(BioForge.CELL_PELLET.get());
                        output.accept(BioForge.MEDICAL_REPORT.get());
                        output.accept(BioForge.VIRUS_SAMPLE.get());
                        output.accept(BioForge.CRISPR_NOTES.get());
                    })
                    .build());
}
