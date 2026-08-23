package net.jenkimods.bioforge.api;

import net.jenkimods.bioforge.api.behavior.BioForgeBehaviorRegistry;
import net.jenkimods.bioforge.api.behavior.SymptomBehavior;
import net.jenkimods.bioforge.api.behavior.TransmissionBehavior;
import net.jenkimods.bioforge.api.behavior.MutationEffectHandler;
import net.jenkimods.bioforge.api.behavior.VaccineMakerOperationHandler;
import net.jenkimods.bioforge.api.guide.ResearchJournalPageDefinition;
import net.jenkimods.bioforge.api.guide.ResearchJournalRegistry;
import net.jenkimods.bioforge.api.definition.DefinitionSource;
import net.jenkimods.bioforge.api.definition.PathogenDefinition;
import net.jenkimods.bioforge.api.definition.SymptomDefinition;
import net.jenkimods.bioforge.api.definition.TransmissionDefinition;
import net.jenkimods.bioforge.api.vaccine.VaccineMakerPageDefinition;
import net.jenkimods.bioforge.api.vaccine.VaccineMakerPageRegistry;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.crispr.BioForgeResearchData;
import net.jenkimods.bioforge.crispr.CrisprAssayDefinition;
import net.jenkimods.bioforge.crispr.CrisprCasModuleDefinition;
import net.jenkimods.bioforge.crispr.CrisprGuideProfile;
import net.jenkimods.bioforge.mutation.MutationDefinition;
import net.jenkimods.bioforge.api.infection.InfectionLifecycleDefinition;
import net.jenkimods.bioforge.api.infection.NaturalInfectionRule;
import net.jenkimods.bioforge.api.infection.NaturalStrainDefinition;
import net.jenkimods.bioforge.infection.lifecycle.InfectionLifecycleRegistry;
import net.jenkimods.bioforge.infection.natural.NaturalInfectionManager;
import net.jenkimods.bioforge.mutation.MutationLoader;
import net.jenkimods.bioforge.vaccine.DirectedVaccineAction;
import net.jenkimods.bioforge.vaccine.VaccineCorrectionProfile;
import net.jenkimods.bioforge.infection.PathogenType;
import net.jenkimods.bioforge.world.centrifuge.CentrifugeRecipe;
import net.jenkimods.bioforge.world.centrifuge.CentrifugeRecipeManager;
import net.jenkimods.bioforge.world.decalcification.DecalcificationRecipe;
import net.jenkimods.bioforge.world.decalcification.DecalcificationRecipeManager;
import net.jenkimods.bioforge.world.incubator.CatalystMappingManager;
import net.jenkimods.bioforge.world.laboratory.LaboratoryProcessRecipe;
import net.jenkimods.bioforge.world.laboratory.LaboratoryProcessRecipeManager;
import net.jenkimods.bioforge.world.microscope.CalibrationSlider;
import net.jenkimods.bioforge.world.microscope.MicroscopeSymptomConfig;
import net.jenkimods.bioforge.world.microscope.MicroscopeSymptomEntry;
import net.jenkimods.bioforge.world.vaccine.VaccineMakerRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.List;

public final class BioForgeAddonApi {
    public static final int API_VERSION = 3;

    private BioForgeAddonApi() {}

    public static void registerPathogen(PathogenDefinition definition) {
        registerPathogen(definition, 0, false);
    }

    public static void registerPathogen(PathogenDefinition definition, int priority, boolean replace) {
        BioForgeDefinitionManager.PATHOGENS.registerJava(definition.id(), definition,
                priority, replace, DefinitionSource.JAVA_ADDON);
    }

    public static void registerTransmission(TransmissionDefinition definition) {
        registerTransmission(definition, 0, false);
    }

    public static void registerTransmission(TransmissionDefinition definition, int priority, boolean replace) {
        BioForgeDefinitionManager.TRANSMISSIONS.registerJava(definition.id(), definition,
                priority, replace, DefinitionSource.JAVA_ADDON);
    }

    public static void registerSymptom(SymptomDefinition definition) {
        registerSymptom(definition, 0, false);
    }

    public static void registerSymptom(SymptomDefinition definition, int priority, boolean replace) {
        BioForgeDefinitionManager.SYMPTOMS.registerJava(definition.id(), definition,
                priority, replace, DefinitionSource.JAVA_ADDON);
    }

    public static void registerPathogenAlias(ResourceLocation alias, ResourceLocation target) {
        BioForgeDefinitionManager.PATHOGENS.registerAlias(alias, target);
    }

    public static void registerTransmissionAlias(ResourceLocation alias, ResourceLocation target) {
        BioForgeDefinitionManager.TRANSMISSIONS.registerAlias(alias, target);
    }

    public static void registerSymptomAlias(ResourceLocation alias, ResourceLocation target) {
        BioForgeDefinitionManager.SYMPTOMS.registerAlias(alias, target);
    }

    public static void registerTransmissionBehavior(ResourceLocation id, TransmissionBehavior behavior) {
        BioForgeBehaviorRegistry.registerTransmission(id, behavior);
    }

    public static void registerSymptomBehavior(ResourceLocation id, SymptomBehavior behavior) {
        BioForgeBehaviorRegistry.registerSymptom(id, behavior);
    }

    public static void registerMutationEffect(ResourceLocation id, MutationEffectHandler handler) {
        BioForgeBehaviorRegistry.registerMutationEffect(id, handler);
    }

    public static void registerMutation(MutationDefinition definition) {
        MutationLoader.INSTANCE.registerJava(definition);
    }

    public static void registerInfectionLifecycle(InfectionLifecycleDefinition definition) {
        InfectionLifecycleRegistry.INSTANCE.registerJava(definition);
    }

    public static void registerPathogenLifecycle(ResourceLocation pathogenId,
                                                 ResourceLocation lifecycleProfileId) {
        InfectionLifecycleRegistry.INSTANCE.registerPathogenDefault(pathogenId, lifecycleProfileId);
    }

    public static void registerNaturalStrain(NaturalStrainDefinition definition) {
        NaturalInfectionManager.INSTANCE.registerJava(definition);
    }

    public static void registerNaturalInfectionRule(NaturalInfectionRule rule) {
        NaturalInfectionManager.INSTANCE.registerJava(rule);
    }

    public static void registerVaccineMakerOperation(ResourceLocation id,
                                                     VaccineMakerOperationHandler handler) {
        BioForgeBehaviorRegistry.registerVaccineOperation(id, handler);
    }

    public static void registerCrisprGuideProfile(CrisprGuideProfile definition) {
        BioForgeResearchData.registerGuideProfile(definition);
    }

    public static void registerCrisprCasModule(CrisprCasModuleDefinition definition) {
        BioForgeResearchData.registerCasModule(definition);
    }

    public static void registerCrisprAssay(CrisprAssayDefinition definition) {
        BioForgeResearchData.registerAssay(definition);
    }

    public static void registerDirectedVaccineAction(DirectedVaccineAction definition) {
        BioForgeResearchData.registerAction(definition);
    }

    public static void registerVaccineCorrectionProfile(VaccineCorrectionProfile definition) {
        BioForgeResearchData.registerCorrectionProfile(definition);
    }

    public static void registerVaccineMakerRecipe(VaccineMakerRecipe recipe) {
        BioForgeResearchData.registerVaccineMakerRecipe(recipe);
    }

    public static void registerCentrifugeRecipe(ResourceLocation id, CentrifugeRecipe recipe) {
        CentrifugeRecipeManager.INSTANCE.registerJava(id, recipe);
    }

    public static void registerDecalcificationRecipe(ResourceLocation id,
                                                     DecalcificationRecipe recipe) {
        DecalcificationRecipeManager.INSTANCE.registerJava(id, recipe);
    }

    public static void registerMicroscopeItem(Item item, List<MicroscopeSymptomEntry> entries,
                                              List<CalibrationSlider> calibration) {
        MicroscopeSymptomConfig.INSTANCE.registerJava(item, entries, calibration);
    }

    public static void registerCatalystMapping(Item item, PathogenType pathogen) {
        CatalystMappingManager.INSTANCE.registerJava(item, pathogen);
    }

    public static void registerCatalystMapping(Item item, ResourceLocation pathogenId) {
        CatalystMappingManager.INSTANCE.registerJava(item, pathogenId);
    }

    public static void registerLaboratoryProcessRecipe(LaboratoryProcessRecipe recipe) {
        LaboratoryProcessRecipeManager.INSTANCE.registerJava(recipe);
    }

    public static void setUniversalCatalystChance(double chance) {
        CatalystMappingManager.INSTANCE.setJavaUniversalChance(chance);
    }

    public static void registerVaccineMakerPage(VaccineMakerPageDefinition page) {
        VaccineMakerPageRegistry.register(page);
    }

    public static void registerResearchJournalPage(ResearchJournalPageDefinition page) {
        ResearchJournalRegistry.register(page);
    }
}
