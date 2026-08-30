package net.jenkimods.bioforge;

import com.mojang.logging.LogUtils;
import net.jenkimods.bioforge.block.*;
import net.jenkimods.bioforge.client.CentrifugeScreen;
import net.jenkimods.bioforge.blood.network.NetworkHandler;
import net.jenkimods.bioforge.client.IncubatorScreen;
import net.jenkimods.bioforge.client.LaboratoryProcessorScreen;
import net.jenkimods.bioforge.client.MicroscopeScreen;
import net.jenkimods.bioforge.client.VaccineMakerScreen;
import net.jenkimods.bioforge.client.render.CentrifugeBlockEntityRenderer;
import net.jenkimods.bioforge.client.render.BlackSteelTilesBlockEntityRenderer;
import net.jenkimods.bioforge.client.render.MicroscopeBlockEntityRenderer;
import net.jenkimods.bioforge.api.vaccine.VaccineMakerPageRegistry;
import net.jenkimods.bioforge.api.guide.ResearchJournalRegistry;
import net.jenkimods.bioforge.api.behavior.BioForgeBehaviorRegistry;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.command.BioForgeTestCommand;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.definition.BioForgeValidateCommand;
import net.jenkimods.bioforge.definition.BioForgeDefinitionCommand;
import net.jenkimods.bioforge.crispr.command.CrisprCommand;
import net.jenkimods.bioforge.infection.command.InfectCommand;
import net.jenkimods.bioforge.infection.command.InfectionInvulnerabilityCommand;
import net.jenkimods.bioforge.infection.command.DecontaminationCommand;
import net.jenkimods.bioforge.infection.command.StrainCommand;
import net.jenkimods.bioforge.infection.network.InfectionNetworkHandler;
import net.jenkimods.bioforge.infection.naming.StrainNameNetworkHandler;
import net.jenkimods.bioforge.infection.spread.AirborneReservoirManager;
import net.jenkimods.bioforge.infection.spread.TransmissionEngine;
import net.jenkimods.bioforge.item.bone_saw.BoneSawItem;
import net.jenkimods.bioforge.item.AreaContaminationScannerItem;
import net.jenkimods.bioforge.item.DescribedBlockItem;
import net.jenkimods.bioforge.item.bones.BoneMarrowItem;
import net.jenkimods.bioforge.item.bones.SplitBoneItem;
import net.jenkimods.bioforge.item.bones.WitheredBoneMarrowItem;
import net.jenkimods.bioforge.item.bones.WitheredSplitBoneItem;
import net.jenkimods.bioforge.item.clipboard.ClipboardItem;
import net.jenkimods.bioforge.item.clipboard.MedicalReportItem;
import net.jenkimods.bioforge.item.incubating.DirtyCultureVialItem;
import net.jenkimods.bioforge.item.incubating.LiveCultureVialItem;
import net.jenkimods.bioforge.item.incubating.NutrientMediumItem;
import net.jenkimods.bioforge.item.incubating.VirusSampleItem;
import net.jenkimods.bioforge.item.crispr.CasModuleItem;
import net.jenkimods.bioforge.item.crispr.CrisprCartridgeItem;
import net.jenkimods.bioforge.item.crispr.CrisprNotesItem;
import net.jenkimods.bioforge.item.crispr.GeneImprintItem;
import net.jenkimods.bioforge.item.reagents.EthanolItem;
import net.jenkimods.bioforge.item.reagents.WipeItem;
import net.jenkimods.bioforge.item.infection.*;
import net.jenkimods.bioforge.item.guide.ResearchJournalItem;
import net.jenkimods.bioforge.item.guide.ResearchJournalNetwork;
import net.jenkimods.bioforge.item.guide.ResearchJournalCommand;
import net.jenkimods.bioforge.item.needle.NeedleItem;
import net.jenkimods.bioforge.item.needle.SyringeItem;
import net.jenkimods.bioforge.item.otoscope.OtoscopeItem;
import net.jenkimods.bioforge.item.otoscope.OtoscopeNetworkHandler;
import net.jenkimods.bioforge.item.pulse_oximeter.PulseOximeterItem;
import net.jenkimods.bioforge.item.pulse_oximeter.PulseOximeterNetworkHandler;
import net.jenkimods.bioforge.item.protective.BioForgeArmorMaterial;
import net.jenkimods.bioforge.item.protective.ProtectiveGearItem;
import net.jenkimods.bioforge.item.reagents.CatalystVialItem;
import net.jenkimods.bioforge.item.reagents.DecalcificationFluidItem;
import net.jenkimods.bioforge.item.reagents.DiagnosticReagentItem;
import net.jenkimods.bioforge.item.reagents.DecontaminationFlaskItem;
import net.jenkimods.bioforge.item.reagents.ReagentVialItem;
import net.jenkimods.bioforge.item.reflex_hammer.ReflexHammerItem;
import net.jenkimods.bioforge.item.reflex_hammer.ReflexHammerNetworkHandler;
import net.jenkimods.bioforge.item.otoscope.MirrorItem;
import net.jenkimods.bioforge.item.samples.BloodSlideItem;
import net.jenkimods.bioforge.item.samples.CellPelletItem;
import net.jenkimods.bioforge.item.samples.PlasmaSampleItem;
import net.jenkimods.bioforge.item.samples.TubeItem;
import net.jenkimods.bioforge.item.stethoscope.StethoscopeItem;
import net.jenkimods.bioforge.item.stethoscope.StethoscopeNetworkHandler;
import net.jenkimods.bioforge.item.stethoscope.StethoscopeSounds;
import net.jenkimods.bioforge.item.thermometer.ThermometerItem;
import net.jenkimods.bioforge.item.thermometer.ThermometerNetworkHandler;
import net.jenkimods.bioforge.item.vaccine.VaccineItem;
import net.jenkimods.bioforge.item.vaccine.ResistancePillItem;
import net.jenkimods.bioforge.item.vaccine.SymptomTabletItem;
import net.jenkimods.bioforge.mutation.command.MutateCommand;
import net.jenkimods.bioforge.mutation.LegacyMutationBehaviors;
import net.jenkimods.bioforge.mutation.network.MutationNetworkHandler;
import net.jenkimods.bioforge.network.compat.BioForgeNetworkPayload;
import net.jenkimods.bioforge.registry.BFCreativeTabs;
import net.jenkimods.bioforge.registry.BioForgeAttachments;
import net.jenkimods.bioforge.registry.BioForgeCapabilities;
import net.jenkimods.bioforge.registry.BioForgeSounds;
import net.jenkimods.bioforge.registry.BioForgeEffects;
import net.jenkimods.bioforge.vaccine.command.VaccineMakeCommand;
import net.jenkimods.bioforge.world.centrifuge.CentrifugeBlockEntity;
import net.jenkimods.bioforge.world.centrifuge.CentrifugeMenu;
import net.jenkimods.bioforge.world.incubator.IncubatorBlockEntity;
import net.jenkimods.bioforge.world.incubator.IncubatorMenu;
import net.jenkimods.bioforge.world.laboratory.LaboratoryProcessRecipeManager;
import net.jenkimods.bioforge.world.laboratory.LaboratoryProcessorBlockEntity;
import net.jenkimods.bioforge.world.laboratory.LaboratoryProcessorMenu;
import net.jenkimods.bioforge.world.laboratory.LaboratoryStation;
import net.jenkimods.bioforge.world.microscope.MicroscopeBlockEntity;
import net.jenkimods.bioforge.world.microscope.MicroscopeMenu;
import net.jenkimods.bioforge.world.microscope.MicroscopeNetwork;
import net.jenkimods.bioforge.world.vaccine.VaccineMakerBlockEntity;
import net.jenkimods.bioforge.world.vaccine.VaccineMakerCorrectionNetwork;
import net.jenkimods.bioforge.world.vaccine.VaccineMakerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.function.Supplier;

@Mod(BioForge.MODID)
public class BioForge {
    public static final String MODID = "bioforge";
    public static final String MOD_NAME = "BioForge";
    public static final String VERSION = "2.0";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MODID);

    public static final Supplier<Item> ACTIVATED_CARBON = ITEMS.register(
            "activated_carbon", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> BLACK_STEEL_BLEND = ITEMS.register(
            "black_steel_blend", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> BLACK_STEEL_INGOT = ITEMS.register(
            "black_steel_ingot", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> BLACK_STEEL_NUGGET = ITEMS.register(
            "black_steel_nugget", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> BLACK_STEEL_PLATE = ITEMS.register(
            "black_steel_plate", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> REINFORCED_GLASS = ITEMS.register(
            "reinforced_glass", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> AGAR_POWDER = ITEMS.register(
            "agar_powder", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> SULFURIC_ACID = ITEMS.register(
            "sulfuric_acid", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final Supplier<Item> STERILIZING_SOLUTION = ITEMS.register(
            "sterilizing_solution", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final Supplier<Item> POLYMER_RESIN = ITEMS.register(
            "polymer_resin", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> STERILE_POLYMER_SHEET = ITEMS.register(
            "sterile_polymer_sheet", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> LABORATORY_GLASSWARE = ITEMS.register(
            "laboratory_glassware", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> STERILE_FILTER = ITEMS.register(
            "sterile_filter", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> OPTICAL_LENS = ITEMS.register(
            "optical_lens", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> PRECISION_MECHANISM = ITEMS.register(
            "precision_mechanism", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> ELECTRONIC_CONTROL_UNIT = ITEMS.register(
            "electronic_control_unit", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> LABORATORY_FRAME = ITEMS.register(
            "laboratory_frame", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final Supplier<Item> BIOMEDICAL_PROCESSOR = ITEMS.register(
            "biomedical_processor", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> NEUTRALIZING_AGENT = ITEMS.register(
            "neutralizing_agent", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> SURFACTANT_CONCENTRATE = ITEMS.register(
            "surfactant_concentrate", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final Supplier<Item> DECONTAMINATION_FLASK = ITEMS.register(
            "decontamination_flask", DecontaminationFlaskItem::new);
    public static final Supplier<Item> SEALED_BIOFABRIC = ITEMS.register(
            "sealed_biofabric", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> STERILE_RUBBER = ITEMS.register(
            "sterile_rubber", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> ACTIVATED_FILTER = ITEMS.register(
            "activated_filter", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> RESPIRATOR_VALVE = ITEMS.register(
            "respirator_valve", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> THERMAL_GEL = ITEMS.register(
            "thermal_gel", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> INSULATED_LINING = ITEMS.register(
            "insulated_lining", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> BLACK_STEEL_MESH = ITEMS.register(
            "black_steel_mesh", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> CHEMICAL_RESISTANT_COATING = ITEMS.register(
            "chemical_resistant_coating", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final Supplier<Item> AIRTIGHT_SEAL = ITEMS.register(
            "airtight_seal", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> WINE_MUST = ITEMS.register(
            "wine_must", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final Supplier<Item> MEDICAL_MASK = ITEMS.register(
            "medical_mask", () -> new ProtectiveGearItem(
                    BioForgeArmorMaterial.MEDICAL_MASK, ArmorItem.Type.HELMET,
                    new Item.Properties(), "item.bioforge.protective_gear.mask",
                    ProtectiveGearItem.WearableStyle.MEDICAL_MASK));
    public static final Supplier<Item> PROTECTIVE_GLOVES = ITEMS.register(
            "protective_gloves", () -> new ProtectiveGearItem(
                    BioForgeArmorMaterial.PROTECTIVE_GLOVES, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties(), "item.bioforge.protective_gear.gloves",
                    ProtectiveGearItem.WearableStyle.PROTECTIVE_GLOVES));
    public static final Supplier<Item> ICE_BAG = ITEMS.register(
            "ice_bag", () -> new ProtectiveGearItem(
                    BioForgeArmorMaterial.ICE_BAG, ArmorItem.Type.HELMET,
                    new Item.Properties(), "item.bioforge.protective_gear.ice_bag",
                    ProtectiveGearItem.WearableStyle.THERMAL_BAG));
    public static final Supplier<Item> MAGMA_BAG = ITEMS.register(
            "magma_bag", () -> new ProtectiveGearItem(
                    BioForgeArmorMaterial.MAGMA_BAG, ArmorItem.Type.HELMET,
                    new Item.Properties().fireResistant(),
                    "item.bioforge.protective_gear.magma_bag",
                    ProtectiveGearItem.WearableStyle.THERMAL_BAG));
    public static final Supplier<Item> HAZCURE_HELMET = ITEMS.register(
            "hazcure_helmet", () -> new ProtectiveGearItem(
                    BioForgeArmorMaterial.HAZCURE, ArmorItem.Type.HELMET,
                    new Item.Properties(), "item.bioforge.protective_gear.hazcure"));
    public static final Supplier<Item> HAZCURE_CHESTPLATE = ITEMS.register(
            "hazcure_chestplate", () -> new ProtectiveGearItem(
                    BioForgeArmorMaterial.HAZCURE, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties(), "item.bioforge.protective_gear.hazcure"));
    public static final Supplier<Item> HAZCURE_LEGGINGS = ITEMS.register(
            "hazcure_leggings", () -> new ProtectiveGearItem(
                    BioForgeArmorMaterial.HAZCURE, ArmorItem.Type.LEGGINGS,
                    new Item.Properties(), "item.bioforge.protective_gear.hazcure"));
    public static final Supplier<Item> HAZCURE_BOOTS = ITEMS.register(
            "hazcure_boots", () -> new ProtectiveGearItem(
                    BioForgeArmorMaterial.HAZCURE, ArmorItem.Type.BOOTS,
                    new Item.Properties(), "item.bioforge.protective_gear.hazcure"));
    public static final Supplier<Block> BLACK_STEEL_BLOCK = BLOCKS.register(
            "black_steel_block", BlackSteelBlock::new);
    public static final Supplier<Item> BLACK_STEEL_BLOCK_ITEM = ITEMS.register(
            "black_steel_block", () -> new DescribedBlockItem(
                    BLACK_STEEL_BLOCK.get(), new Item.Properties(),
                    "block.bioforge.black_steel_block.tooltip",
                    "block.bioforge.black_steel.coating_tooltip"));
    public static final Supplier<Block> BLACK_STEEL_TILES = BLOCKS.register(
            "black_steel_tiles", BlackSteelTilesBlock::new);
    public static final Supplier<Item> BLACK_STEEL_TILES_ITEM = ITEMS.register(
            "black_steel_tiles", () -> new DescribedBlockItem(
                    BLACK_STEEL_TILES.get(), new Item.Properties(),
                    "block.bioforge.black_steel_tiles.tooltip",
                    "block.bioforge.black_steel_tiles.controls_tooltip"));
    public static final Supplier<BlockEntityType<net.jenkimods.bioforge.world.decoration.BlackSteelTilesBlockEntity>>
            BLACK_STEEL_TILES_BE = BLOCK_ENTITIES.register(
                    "black_steel_tiles", () -> BlockEntityType.Builder.of(
                            net.jenkimods.bioforge.world.decoration.BlackSteelTilesBlockEntity::new,
                            BLACK_STEEL_TILES.get()).build(null));
    public static final Supplier<Block> BLACK_STEEL_GRATE = BLOCKS.register(
            "black_steel_grate", BlackSteelGrateBlock::new);
    public static final Supplier<Item> BLACK_STEEL_GRATE_ITEM = ITEMS.register(
            "black_steel_grate", () -> new DescribedBlockItem(
                    BLACK_STEEL_GRATE.get(), new Item.Properties(),
                    "block.bioforge.black_steel_grate.tooltip",
                    "block.bioforge.black_steel.coating_tooltip"));
    public static final Supplier<Block> BLACK_STEEL_DOOR = BLOCKS.register(
            "black_steel_door", () -> new DoorBlock(BlockSetType.IRON,
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                            .strength(7.0F, 9.0F).sound(SoundType.METAL)
                            .noOcclusion().requiresCorrectToolForDrops()));
    public static final Supplier<Item> BLACK_STEEL_DOOR_ITEM = ITEMS.register(
            "black_steel_door", () -> new DescribedBlockItem(
                    BLACK_STEEL_DOOR.get(), new Item.Properties(),
                    "block.bioforge.black_steel_door.tooltip",
                    "block.bioforge.black_steel.coating_tooltip"));
    public static final Supplier<Block> BLACK_STEEL_TRAPDOOR = BLOCKS.register(
            "black_steel_trapdoor", () -> new TrapDoorBlock(BlockSetType.IRON,
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                            .strength(7.0F, 9.0F).sound(SoundType.METAL)
                            .noOcclusion().requiresCorrectToolForDrops()));
    public static final Supplier<Item> BLACK_STEEL_TRAPDOOR_ITEM = ITEMS.register(
            "black_steel_trapdoor", () -> new DescribedBlockItem(
                    BLACK_STEEL_TRAPDOOR.get(), new Item.Properties(),
                    "block.bioforge.black_steel_trapdoor.tooltip",
                    "block.bioforge.black_steel.coating_tooltip"));
    public static final Supplier<Block> BLACK_STEEL_BARS = BLOCKS.register(
            "black_steel_bars", () -> new IronBarsBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(7.0F, 9.0F)
                    .sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));
    public static final Supplier<Item> BLACK_STEEL_BARS_ITEM = ITEMS.register(
            "black_steel_bars", () -> new DescribedBlockItem(
                    BLACK_STEEL_BARS.get(), new Item.Properties(),
                    "block.bioforge.black_steel_bars.tooltip",
                    "block.bioforge.black_steel.coating_tooltip"));

    public static final Supplier<Item> WOODEN_NEEDLE = ITEMS.register("wooden_needle", () -> new NeedleItem(NeedleItem.Tier.WOODEN));
    public static final Supplier<Item> IRON_NEEDLE = ITEMS.register("iron_needle", () -> new NeedleItem(NeedleItem.Tier.IRON));
    public static final Supplier<Item> HARDENED_NEEDLE = ITEMS.register("hardened_needle", () -> new NeedleItem(NeedleItem.Tier.HARDENED));
    public static final Supplier<Item> ANTI_A_VIAL = ITEMS.register("anti_a_vial", () -> new ReagentVialItem(ReagentVialItem.Type.ANTI_A));
    public static final Supplier<Item> ANTI_B_VIAL = ITEMS.register("anti_b_vial", () -> new ReagentVialItem(ReagentVialItem.Type.ANTI_B));
    public static final Supplier<Item> ANTI_D_VIAL = ITEMS.register("anti_d_vial", () -> new ReagentVialItem(ReagentVialItem.Type.ANTI_D));
    public static final Supplier<Item> PATHOGEN_REAGENT = ITEMS.register(
            "pathogen_reagent",
            () -> new DiagnosticReagentItem(DiagnosticReagentItem.Kind.PATHOGEN));
    public static final Supplier<Item> VISIBILITY_REAGENT = ITEMS.register(
            "visibility_reagent",
            () -> new DiagnosticReagentItem(DiagnosticReagentItem.Kind.VISIBILITY));
    public static final Supplier<Item> VACCINE = ITEMS.register("vaccine", () -> new VaccineItem());
    public static final Supplier<Item> MUTATION_VACCINE = ITEMS.register("mutation_vaccine",
            () -> new VaccineItem(VaccineItem.Kind.MUTATION));
    public static final Supplier<Item> TRANSMISSION_VACCINE = ITEMS.register("transmission_vaccine",
            () -> new VaccineItem(VaccineItem.Kind.TRANSMISSION));
    public static final Supplier<Item> SYMPTOM_VACCINE = ITEMS.register("symptom_vaccine",
            () -> new VaccineItem(VaccineItem.Kind.SYMPTOM));
    public static final Supplier<Item> RANDOM_MUTATION_VACCINE =
            ITEMS.register("random_mutation_vaccine",
                    () -> new VaccineItem(VaccineItem.Kind.RANDOM_MUTATION));
    public static final Supplier<Item> MUTATION_UPGRADE_VACCINE =
            ITEMS.register("mutation_upgrade_vaccine",
                    () -> new VaccineItem(VaccineItem.Kind.RANDOM_MUTATION_UPGRADE));
    public static final Supplier<Item> VIRAL_SUPPRESSOR_PILL =
            ITEMS.register("viral_suppressor_pill", ResistancePillItem::new);
    public static final Supplier<Item> VIRAL_INHIBITOR_PILL =
            ITEMS.register("viral_inhibitor_pill", ResistancePillItem::new);
    public static final Supplier<Item> VIRAL_BLOCKER_PILL =
            ITEMS.register("viral_blocker_pill", ResistancePillItem::new);
    public static final Supplier<Item> SYMPTOM_TABLET =
            ITEMS.register("symptom_tablet", SymptomTabletItem::new);
    public static final Supplier<Item> CRISPR_CARTRIDGE = ITEMS.register("crispr_cartridge",
            CrisprCartridgeItem::new);
    public static final Supplier<Item> CAS_MODULE = ITEMS.register("cas_module",
            CasModuleItem::new);
    public static final Supplier<Item> GENE_IMPRINT = ITEMS.register("gene_imprint",
            GeneImprintItem::new);
    public static final Supplier<Item> CRISPR_NOTES = ITEMS.register("crispr_notes",
            CrisprNotesItem::new);
    public static final Supplier<Item> DECALCIFICATION_FLUID = ITEMS.register("decalcification_fluid", DecalcificationFluidItem::new);
    public static final Supplier<Item> BONE_SAW = ITEMS.register("bone_saw", BoneSawItem::new);
    public static final Supplier<Item> WITHERED_SPLIT_BONE = ITEMS.register("withered_split_bone", WitheredSplitBoneItem::new);
    public static final Supplier<Item> WITHERED_BONE_MARROW = ITEMS.register("withered_bone_marrow", WitheredBoneMarrowItem::new);
    public static final Supplier<Item> SPLIT_BONE = ITEMS.register("split_bone", SplitBoneItem::new);
    public static final Supplier<Item> BONE_MARROW = ITEMS.register("bone_marrow", BoneMarrowItem::new);
    public static final Supplier<Item> THERMOMETER_ITEM = ITEMS.register("thermometer", ThermometerItem::new);
    public static final Supplier<Item> STETHOSCOPE = ITEMS.register("stethoscope", StethoscopeItem::new);
    public static final Supplier<Item> OTOSCOPE = ITEMS.register("otoscope", OtoscopeItem::new);
    public static final Supplier<Item> MIRROR = ITEMS.register("mirror", MirrorItem::new);
    public static final Supplier<Item> REFLEX_HAMMER = ITEMS.register("reflex_hammer", ReflexHammerItem::new);
    public static final Supplier<Item> PULSE_OXIMETER = ITEMS.register("pulse_oximeter", PulseOximeterItem::new);
    public static final Supplier<Item> CLIPBOARD = ITEMS.register("clipboard", ClipboardItem::new);
    public static final Supplier<Item> MEDICAL_REPORT = ITEMS.register("medical_report", MedicalReportItem::new);
    public static final Supplier<Item> RESEARCH_JOURNAL = ITEMS.register(
            "research_journal", ResearchJournalItem::new);
    public static final Supplier<Item> AREA_CONTAMINATION_SCANNER = ITEMS.register(
            "area_contamination_scanner", AreaContaminationScannerItem::new);

    public static final Supplier<Block> CENTRIFUGE = BLOCKS.register("centrifuge", CentrifugeBlock::new);
    public static final Supplier<Item> CENTRIFUGE_ITEM = ITEMS.register("centrifuge", () -> new BlockItem(CENTRIFUGE.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CentrifugeBlockEntity>> CENTRIFUGE_BE = BLOCK_ENTITIES.register("centrifuge", () -> BlockEntityType.Builder.of(CentrifugeBlockEntity::new, CENTRIFUGE.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<CentrifugeMenu>> CENTRIFUGE_MENU = MENUS.register(
            "centrifuge", () -> IMenuTypeExtension.create(CentrifugeMenu::new));

    public static final Supplier<Block> VIRAL_SCANNER = BLOCKS.register("viral_scanner",
            () -> new ViralScannerBlock(ViralScannerBlock.Variant.FULL));
    public static final Supplier<Block> CEILING_VIRAL_SCANNER = BLOCKS.register("ceiling_viral_scanner",
            () -> new ViralScannerBlock(ViralScannerBlock.Variant.CEILING));
    public static final Supplier<Block> OPEN_LEFT_VIRAL_SCANNER = BLOCKS.register("open_left_viral_scanner",
            () -> new ViralScannerBlock(ViralScannerBlock.Variant.OPEN_LEFT));
    public static final Supplier<Block> OPEN_RIGHT_VIRAL_SCANNER = BLOCKS.register("open_right_viral_scanner",
            () -> new ViralScannerBlock(ViralScannerBlock.Variant.OPEN_RIGHT));
    public static final Supplier<Item> VIRAL_SCANNER_ITEM = ITEMS.register("viral_scanner",
            () -> new BlockItem(VIRAL_SCANNER.get(), new Item.Properties()));
    public static final Supplier<Item> CEILING_VIRAL_SCANNER_ITEM = ITEMS.register("ceiling_viral_scanner",
            () -> new BlockItem(CEILING_VIRAL_SCANNER.get(), new Item.Properties()));
    public static final Supplier<Item> OPEN_LEFT_VIRAL_SCANNER_ITEM = ITEMS.register("open_left_viral_scanner",
            () -> new BlockItem(OPEN_LEFT_VIRAL_SCANNER.get(), new Item.Properties()));
    public static final Supplier<Item> OPEN_RIGHT_VIRAL_SCANNER_ITEM = ITEMS.register("open_right_viral_scanner",
            () -> new BlockItem(OPEN_RIGHT_VIRAL_SCANNER.get(), new Item.Properties()));
    public static final Supplier<Block> AIR_VENT = BLOCKS.register("air_vent", AirVentBlock::new);
    public static final Supplier<Item> AIR_VENT_ITEM = ITEMS.register("air_vent",
            () -> new DescribedBlockItem(AIR_VENT.get(), new Item.Properties(),
                    "block.bioforge.air_vent.tooltip",
                    "block.bioforge.air_vent.limits_tooltip"));

    public static final Supplier<Block> MICROBIAL_MAT = BLOCKS.register("microbial_mat", MicrobialMatBlock::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MicrobialMatBlockEntity>> MICROBIAL_MAT_BE =
            BLOCK_ENTITIES.register("microbial_mat",
                    () -> BlockEntityType.Builder.of(MicrobialMatBlockEntity::new, MICROBIAL_MAT.get()).build(null));

    public static final Supplier<Block> PETRI_DISH_BLOCK = BLOCKS.register("petri_dish", PetriDishBlock::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PetriDishBlockEntity>> PETRI_DISH_BE =
            BLOCK_ENTITIES.register("petri_dish",
                    () -> BlockEntityType.Builder.of(PetriDishBlockEntity::new, PETRI_DISH_BLOCK.get()).build(null));

    public static final Supplier<Block> SPOROCARP = BLOCKS.register("sporocarp", SporocarpBlock::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SporocarpBlockEntity>> SPOROCARP_BE =
            BLOCK_ENTITIES.register("sporocarp",
                    () -> BlockEntityType.Builder.of(SporocarpBlockEntity::new, SPOROCARP.get()).build(null));
    public static final Supplier<Block> NECROTIC_PATCH = BLOCKS.register("necrotic_patch", NecroticPatchBlock::new);

    public static final Supplier<Block> CONTAMINATED_SUBSTRATE = BLOCKS.register("contaminated_substrate", ContaminatedSubstrateBlock::new);
    public static final Supplier<Item> CONTAMINATED_SUBSTRATE_ITEM = ITEMS.register("contaminated_substrate", ContaminatedSubstrateItem::new);

    public static final Supplier<Block> COLONY_CORE = BLOCKS.register("colony_core", ColonyCoreBlock::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ColonyCoreBlockEntity>> COLONY_CORE_BE =
            BLOCK_ENTITIES.register("colony_core",
                    () -> BlockEntityType.Builder.of(ColonyCoreBlockEntity::new, COLONY_CORE.get()).build(null));

    public static final Supplier<Block> INFESTED_BLOCK = BLOCKS.register("infested_block", InfestedBlock::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InfestedBlockEntity>> INFESTED_BLOCK_BE =
            BLOCK_ENTITIES.register("infested_block",
                    () -> BlockEntityType.Builder.of(InfestedBlockEntity::new, INFESTED_BLOCK.get()).build(null));

    public static final Supplier<Item> SWAB = ITEMS.register("swab", SwabItem::new);
    public static final Supplier<Item> PETRI_DISH = ITEMS.register("petri_dish", PetriDishItem::new);
    public static final Supplier<Item> SYRINGE = ITEMS.register("syringe", SyringeItem::new);
    public static final Supplier<Item> BLOOD_SLIDE = ITEMS.register("blood_slide", BloodSlideItem::new);
    public static final Supplier<Item> TUBE = ITEMS.register("tube", TubeItem::new);
    public static final Supplier<Item> PLASMA_SAMPLE = ITEMS.register("plasma_sample", PlasmaSampleItem::new);
    public static final Supplier<Item> CELL_PELLET = ITEMS.register("cell_pellet", CellPelletItem::new);

    public static final Supplier<Block> MICROSCOPE = BLOCKS.register("microscope", MicroscopeBlock::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MicroscopeBlockEntity>> MICROSCOPE_BE =
            BLOCK_ENTITIES.register("microscope", () ->
                    BlockEntityType.Builder.of(MicroscopeBlockEntity::new, MICROSCOPE.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<MicroscopeMenu>> MICROSCOPE_MENU = MENUS.register(
            "microscope", () -> IMenuTypeExtension.create(MicroscopeMenu::new));
    public static final Supplier<Item> MICROSCOPE_ITEM = ITEMS.register("microscope",
            () -> new BlockItem(MICROSCOPE.get(), new Item.Properties()));

    public static final Supplier<Block> INCUBATOR = BLOCKS.register("incubator", IncubatorBlock::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IncubatorBlockEntity>> INCUBATOR_BE =
            BLOCK_ENTITIES.register("incubator", () -> BlockEntityType.Builder.of(IncubatorBlockEntity::new, INCUBATOR.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<IncubatorMenu>> INCUBATOR_MENU =
            MENUS.register("incubator", () -> IMenuTypeExtension.create(IncubatorMenu::new));
    public static final Supplier<Item> INCUBATOR_ITEM = ITEMS.register("incubator",
            () -> new BlockItem(INCUBATOR.get(), new Item.Properties()));

    public static final Supplier<Block> VACCINE_MAKER =
            BLOCKS.register("vaccine_maker", VaccineMakerBlock::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VaccineMakerBlockEntity>> VACCINE_MAKER_BE =
            BLOCK_ENTITIES.register("vaccine_maker", () ->
                    BlockEntityType.Builder.of(VaccineMakerBlockEntity::new,
                            VACCINE_MAKER.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<VaccineMakerMenu>> VACCINE_MAKER_MENU =
            MENUS.register("vaccine_maker", () -> IMenuTypeExtension.create(VaccineMakerMenu::new));
    public static final Supplier<Item> VACCINE_MAKER_ITEM =
            ITEMS.register("vaccine_maker",
                    () -> new BlockItem(VACCINE_MAKER.get(), new Item.Properties()));

    public static final Supplier<Block> BARREL_PRESS = BLOCKS.register(
            "barrel_press",
            () -> new LaboratoryProcessorBlock(LaboratoryStation.BARREL_PRESS));
    public static final Supplier<Block> CHEMICAL_SYNTHESIZER = BLOCKS.register(
            "chemical_synthesizer",
            () -> new LaboratoryProcessorBlock(LaboratoryStation.CHEMICAL_SYNTHESIZER));
    public static final Supplier<Block> STERILIZATION_CHAMBER = BLOCKS.register(
            "sterilization_chamber",
            () -> new LaboratoryProcessorBlock(LaboratoryStation.STERILIZATION_CHAMBER));
    public static final Supplier<Block> PHARMA_MIXER = BLOCKS.register(
            "pharma_mixer",
            () -> new LaboratoryProcessorBlock(LaboratoryStation.PHARMA_MIXER));
    public static final Supplier<Item> BARREL_PRESS_ITEM = ITEMS.register(
            "barrel_press", () -> new BlockItem(BARREL_PRESS.get(), new Item.Properties()));
    public static final Supplier<Item> CHEMICAL_SYNTHESIZER_ITEM = ITEMS.register(
            "chemical_synthesizer", () -> new BlockItem(CHEMICAL_SYNTHESIZER.get(), new Item.Properties()));
    public static final Supplier<Item> STERILIZATION_CHAMBER_ITEM = ITEMS.register(
            "sterilization_chamber", () -> new BlockItem(STERILIZATION_CHAMBER.get(), new Item.Properties()));
    public static final Supplier<Item> PHARMA_MIXER_ITEM = ITEMS.register(
            "pharma_mixer", () -> new BlockItem(PHARMA_MIXER.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaboratoryProcessorBlockEntity>> LABORATORY_PROCESSOR_BE =
            BLOCK_ENTITIES.register("laboratory_processor", () -> BlockEntityType.Builder.of(
                    LaboratoryProcessorBlockEntity::new, BARREL_PRESS.get(), CHEMICAL_SYNTHESIZER.get(),
                    STERILIZATION_CHAMBER.get(), PHARMA_MIXER.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<LaboratoryProcessorMenu>> LABORATORY_PROCESSOR_MENU =
            MENUS.register("laboratory_processor", () -> IMenuTypeExtension.create(LaboratoryProcessorMenu::new));

    public static final Supplier<Item> CATALYST_VIAL = ITEMS.register("catalyst_vial", CatalystVialItem::new);
    public static final Supplier<Item> NUTRIENT_MEDIUM = ITEMS.register("nutrient_medium", NutrientMediumItem::new);
    public static final Supplier<Item> VIRUS_SAMPLE = ITEMS.register("virus_sample", VirusSampleItem::new);
    public static final Supplier<Item> LIVE_CULTURE_VIAL = ITEMS.register("live_culture_vial", LiveCultureVialItem::new);
    public static final Supplier<Item> DIRTY_CULTURE_VIAL = ITEMS.register("dirty_culture_vial", DirtyCultureVialItem::new);
    public static final Supplier<Item> ETHANOL = ITEMS.register("ethanol", EthanolItem::new);
    public static final Supplier<Item> WIPES = ITEMS.register("wipes", WipeItem::new);

    public BioForge(IEventBus modEventBus, ModContainer modContainer) {
        net.jenkimods.bioforge.infection.BioForgeGameRules.register();
        modContainer.registerConfig(ModConfig.Type.SERVER, BioForgeServerConfig.SPEC,
                "bioforge-server.toml");
        LegacyMutationBehaviors.register();
        VaccineMakerPageRegistry.bootstrapBuiltIns();
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        BioForgeSounds.SOUNDS.register(modEventBus);
        StethoscopeSounds.SOUNDS.register(modEventBus);
        BioForgeEffects.register(modEventBus);
        BFCreativeTabs.TABS.register(modEventBus);
        BioForgeAttachments.ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(BioForgeNetworkPayload::registerPayload);
        modEventBus.addListener(BioForgeCapabilities::register);
        modEventBus.addListener(this::onCommonSetup);

        NeoForge.EVENT_BUS.register(this);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NetworkHandler.register();
            ThermometerNetworkHandler.register();
            StethoscopeNetworkHandler.register();
            OtoscopeNetworkHandler.register();
            ReflexHammerNetworkHandler.register();
            PulseOximeterNetworkHandler.register();
            InfectionNetworkHandler.register();
            StrainNameNetworkHandler.register();
            MicroscopeNetwork.register();
            VaccineMakerCorrectionNetwork.register();
            MutationNetworkHandler.register();
            ResearchJournalNetwork.register();
            net.jenkimods.bioforge.world.decoration.BlackSteelTilesNetworkHandler.register();
        });
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        InfectCommand.register(event.getDispatcher());
        InfectionInvulnerabilityCommand.register(event.getDispatcher());
        StrainCommand.register(event.getDispatcher());
        MutateCommand.register(event.getDispatcher());
        VaccineMakeCommand.register(event.getDispatcher(), VACCINE);
        CrisprCommand.register(event.getDispatcher());
        BioForgeValidateCommand.register(event.getDispatcher());
        BioForgeDefinitionCommand.register(event.getDispatcher());
        BioForgeTestCommand.register(event.getDispatcher());
        ResearchJournalCommand.register(event.getDispatcher());
        DecontaminationCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        BioForgeDefinitionManager.freezeJavaRegistrations();
        net.jenkimods.bioforge.mutation.MutationLoader.INSTANCE.freezeJavaRegistrations();
        net.jenkimods.bioforge.infection.lifecycle.InfectionLifecycleRegistry.INSTANCE.freezeJavaRegistrations();
        net.jenkimods.bioforge.infection.natural.NaturalInfectionManager.INSTANCE.freezeJavaRegistrations();
        net.jenkimods.bioforge.crispr.BioForgeResearchData.freezeJavaRegistrations();
        net.jenkimods.bioforge.world.centrifuge.CentrifugeRecipeManager.INSTANCE.freezeJavaRegistrations();
        net.jenkimods.bioforge.world.decalcification.DecalcificationRecipeManager.INSTANCE.freezeJavaRegistrations();
        net.jenkimods.bioforge.world.incubator.CatalystMappingManager.INSTANCE.freezeJavaRegistrations();
        net.jenkimods.bioforge.world.microscope.MicroscopeSymptomConfig.INSTANCE.freezeJavaRegistrations();
        LaboratoryProcessRecipeManager.INSTANCE.freezeJavaRegistrations();
        net.jenkimods.bioforge.api.vaccine.VaccineMakerPageRegistry.freeze();
        ResearchJournalRegistry.freeze();
        BioForgeBehaviorRegistry.freeze();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        event.getServer().getAllLevels().forEach(level -> {
            AirborneReservoirManager.clear(level);
            AirVentBlock.clear(level);
        });
        TransmissionEngine.clearCaches();
        InfectionNetworkHandler.clearDefinitionSyncState();
    }

    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                        BioForge.CENTRIFUGE_BE.get(),
                        CentrifugeBlockEntityRenderer::new);
                net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                        BioForge.MICROSCOPE_BE.get(),
                        MicroscopeBlockEntityRenderer::new);
                net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                        BioForge.BLACK_STEEL_TILES_BE.get(),
                        BlackSteelTilesBlockEntityRenderer::new);
                ResourceLocation filledRL = ResourceLocation.tryBuild(BioForge.MODID, "filled");
                ResourceLocation reactedRL = ResourceLocation.tryBuild(BioForge.MODID, "reacted");
                net.minecraft.client.renderer.item.ItemProperties.register(BioForge.SWAB.get(), filledRL, (stack, level, entity, seed) -> SwabItem.isContaminated(stack) ? 1.0f : 0.0f);
                net.minecraft.client.renderer.item.ItemProperties.register(BioForge.PETRI_DISH.get(), filledRL, (stack, level, entity, seed) -> {if (PetriDishItem.isInoculated(stack)) {return net.jenkimods.bioforge.util.StackData.copy(stack).getInt("Growth") >= 1 ? 1.0f : 0.0f;}return 0.0f;});
                net.minecraft.client.renderer.item.ItemProperties.register(BioForge.CLIPBOARD.get(), filledRL, (stack, level, entity, seed) -> ClipboardItem.getFilledModel(stack));
                net.minecraft.client.renderer.item.ItemProperties.register(BioForge.WOODEN_NEEDLE.get(), filledRL, (stack, level, entity, seed) -> NeedleItem.getFilledPredicate(stack));
                net.minecraft.client.renderer.item.ItemProperties.register(BioForge.IRON_NEEDLE.get(), filledRL, (stack, level, entity, seed) -> NeedleItem.getFilledPredicate(stack));
                net.minecraft.client.renderer.item.ItemProperties.register(BioForge.HARDENED_NEEDLE.get(), filledRL, (stack, level, entity, seed) -> NeedleItem.getFilledPredicate(stack));
                net.minecraft.client.renderer.item.ItemProperties.register(BioForge.ANTI_A_VIAL.get(), reactedRL, (stack, level, entity, seed) -> ReagentVialItem.getReactedPredicate(stack));
                net.minecraft.client.renderer.item.ItemProperties.register(BioForge.ANTI_B_VIAL.get(), reactedRL, (stack, level, entity, seed) -> ReagentVialItem.getReactedPredicate(stack));
                net.minecraft.client.renderer.item.ItemProperties.register(BioForge.ANTI_D_VIAL.get(), reactedRL, (stack, level, entity, seed) -> ReagentVialItem.getReactedPredicate(stack));
                net.minecraft.client.renderer.item.ItemProperties.register(BioForge.THERMOMETER_ITEM.get(), ResourceLocation.tryBuild(BioForge.MODID, "ready"), (stack, level, entity, seed) -> ThermometerItem.isReady(stack) ? 1.0f : 0.0f);
                net.minecraft.client.renderer.item.ItemProperties.register(BioForge.SYRINGE.get(), ResourceLocation.tryBuild(BioForge.MODID, "syringe_fill"), (stack, level, entity, seed) -> {int uses = SyringeItem.getUses(stack);return uses / 4.0f;});
                net.minecraft.client.renderer.item.ItemProperties.register(BioForge.BLOOD_SLIDE.get(), ResourceLocation.tryBuild(BioForge.MODID, "blood_slide_filled"), (stack, level, entity, seed) -> BloodSlideItem.hasBlood(stack) ? 1.0f : 0.0f);
                net.minecraft.client.renderer.item.ItemProperties.register(BioForge.TUBE.get(), ResourceLocation.tryBuild(BioForge.MODID, "tube_filled_blood"), (stack, level, entity, seed) -> TubeItem.hasBlood(stack) ? 1.0f : 0.0f);
                net.minecraft.client.renderer.item.ItemProperties.register(BioForge.LIVE_CULTURE_VIAL.get(),
                        ResourceLocation.tryBuild(BioForge.MODID, "filled"),
                        (stack, level, entity, seed) -> LiveCultureVialItem.hasStrain(stack) ? 1.0f : 0.0f);
            });
        }

        @SubscribeEvent
        public static void onRegisterMenuScreens(
                net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
            event.register(BioForge.CENTRIFUGE_MENU.get(), CentrifugeScreen::new);
            event.register(BioForge.MICROSCOPE_MENU.get(), MicroscopeScreen::new);
            event.register(BioForge.INCUBATOR_MENU.get(), IncubatorScreen::new);
            event.register(BioForge.VACCINE_MAKER_MENU.get(), VaccineMakerScreen::new);
            event.register(BioForge.LABORATORY_PROCESSOR_MENU.get(), LaboratoryProcessorScreen::new);
        }

        @SubscribeEvent
        public static void onRegisterClientExtensions(
                net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
            registerProtectiveGearModel(event, BioForge.MEDICAL_MASK.get());
            registerProtectiveGearModel(event, BioForge.PROTECTIVE_GLOVES.get());
            registerProtectiveGearModel(event, BioForge.ICE_BAG.get());
            registerProtectiveGearModel(event, BioForge.MAGMA_BAG.get());
        }

        private static void registerProtectiveGearModel(
                net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event,
                Item item) {
            if (item instanceof ProtectiveGearItem protectiveGear) {
                event.registerItem(
                        net.jenkimods.bioforge.client.render.ProtectiveGearClientExtensions
                                .create(protectiveGear::wearableStyle),
                        item);
            }
        }

        @SubscribeEvent
        public static void onRegisterAdditionalModels(
                ModelEvent.RegisterAdditional event) {
            event.register(MicroscopeBlockEntityRenderer.KNOB_MODEL);
            event.register(MicroscopeBlockEntityRenderer.LENS_WHEEL_MODEL);
            event.register(MicroscopeBlockEntityRenderer.BULB_MODEL);
        }

        @SubscribeEvent
        public static void onRegisterLayerDefinitions(
                net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(
                    net.jenkimods.bioforge.client.render.ProtectiveGearModel.THERMAL_BAG_LAYER,
                    net.jenkimods.bioforge.client.render.ProtectiveGearModel::createThermalBagLayer);
            event.registerLayerDefinition(
                    net.jenkimods.bioforge.client.render.ProtectiveGearModel.MEDICAL_MASK_LAYER,
                    net.jenkimods.bioforge.client.render.ProtectiveGearModel::createMedicalMaskLayer);
            event.registerLayerDefinition(
                    net.jenkimods.bioforge.client.render.ProtectiveGearModel.PROTECTIVE_GLOVES_LAYER,
                    net.jenkimods.bioforge.client.render.ProtectiveGearModel::createProtectiveGlovesLayer);
        }
    }
}
