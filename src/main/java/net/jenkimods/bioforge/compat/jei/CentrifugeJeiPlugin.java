package net.jenkimods.bioforge.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.world.centrifuge.CentrifugeRecipeManager;
import net.jenkimods.bioforge.world.decalcification.DecalcificationRecipeManager;
import net.jenkimods.bioforge.world.incubator.IncubatorRecipeRegistration;
import net.jenkimods.bioforge.world.laboratory.LaboratoryProcessRecipe;
import net.jenkimods.bioforge.world.laboratory.LaboratoryProcessRecipeManager;
import net.jenkimods.bioforge.world.laboratory.LaboratoryStation;
import net.jenkimods.bioforge.crispr.BioForgeResearchData;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

@JeiPlugin
public class CentrifugeJeiPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_ID =
            Objects.requireNonNull(ResourceLocation.tryBuild(BioForge.MODID, "jei_plugin"));
    private List<VaccineMakerRecipeWrapper> registeredVaccineRecipes = List.of();
    private List<CentrifugeRecipeWrapper> registeredCentrifugeRecipes = List.of();
    private List<DecalcificationRecipeWrapper> registeredDecalcificationRecipes = List.of();
    private List<IncubatorRecipeWrapper> registeredIncubatorRecipes = List.of();
    private final Map<LaboratoryStation, List<LaboratoryProcessRecipe>>
            registeredLaboratoryRecipes = new EnumMap<>(LaboratoryStation.class);
    private IJeiRuntime runtime;
    private boolean listeningForRecipeReloads;

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new CentrifugeRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new DecalcificationRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new IncubatorRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new VaccineMakerRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new LaboratoryProcessRecipeCategory(
                        registration.getJeiHelpers().getGuiHelper(),
                        LaboratoryStation.BARREL_PRESS,
                        new net.minecraft.world.item.ItemStack(
                                BioForge.BARREL_PRESS_ITEM.get())),
                new LaboratoryProcessRecipeCategory(
                        registration.getJeiHelpers().getGuiHelper(),
                        LaboratoryStation.CHEMICAL_SYNTHESIZER,
                        new net.minecraft.world.item.ItemStack(
                                BioForge.CHEMICAL_SYNTHESIZER_ITEM.get())),
                new LaboratoryProcessRecipeCategory(
                        registration.getJeiHelpers().getGuiHelper(),
                        LaboratoryStation.PHARMA_MIXER,
                        new net.minecraft.world.item.ItemStack(
                                BioForge.PHARMA_MIXER_ITEM.get())),
                new LaboratoryProcessRecipeCategory(
                        registration.getJeiHelpers().getGuiHelper(),
                        LaboratoryStation.STERILIZATION_CHAMBER,
                        new net.minecraft.world.item.ItemStack(
                                BioForge.STERILIZATION_CHAMBER_ITEM.get()))
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registeredCentrifugeRecipes = centrifugeRecipes();
        registration.addRecipes(CentrifugeRecipeCategory.RECIPE_TYPE,
                registeredCentrifugeRecipes);

        registeredDecalcificationRecipes = decalcificationRecipes();
        registration.addRecipes(DecalcificationRecipeCategory.RECIPE_TYPE,
                registeredDecalcificationRecipes);

        registeredIncubatorRecipes = incubatorRecipes();
        registration.addRecipes(IncubatorRecipeCategory.RECIPE_TYPE,
                registeredIncubatorRecipes);

        registeredVaccineRecipes = vaccineMakerRecipes();
        registration.addRecipes(VaccineMakerRecipeCategory.RECIPE_TYPE,
                registeredVaccineRecipes);

        for (LaboratoryStation station : LaboratoryStation.values()) {
            List<LaboratoryProcessRecipe> recipes = laboratoryRecipes(station);
            registeredLaboratoryRecipes.put(station, recipes);
            registration.addRecipes(LaboratoryProcessRecipeCategory.type(station), recipes);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(
                        BioForge.VACCINE_MAKER_ITEM.get()),
                VaccineMakerRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(
                        BioForge.CENTRIFUGE_ITEM.get()),
                CentrifugeRecipeCategory.RECIPE_TYPE,
                DecalcificationRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(
                        BioForge.INCUBATOR_ITEM.get()),
                IncubatorRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(
                        BioForge.BARREL_PRESS_ITEM.get()),
                LaboratoryProcessRecipeCategory.BARREL_PRESS_TYPE);
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(
                        BioForge.CHEMICAL_SYNTHESIZER_ITEM.get()),
                LaboratoryProcessRecipeCategory.CHEMICAL_TYPE);
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(
                        BioForge.PHARMA_MIXER_ITEM.get()),
                LaboratoryProcessRecipeCategory.PHARMA_TYPE);
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(
                        BioForge.STERILIZATION_CHAMBER_ITEM.get()),
                LaboratoryProcessRecipeCategory.STERILIZATION_TYPE);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        this.runtime = runtime;
        if (registeredVaccineRecipes.isEmpty()) {
            List<VaccineMakerRecipeWrapper> recipes = vaccineMakerRecipes();
            if (!recipes.isEmpty()) {
                runtime.getRecipeManager().addRecipes(
                        VaccineMakerRecipeCategory.RECIPE_TYPE, recipes);
                registeredVaccineRecipes = recipes;
            }
        }
        for (LaboratoryStation station : LaboratoryStation.values()) {
            if (!registeredLaboratoryRecipes.getOrDefault(station, List.of()).isEmpty()) {
                continue;
            }
            List<LaboratoryProcessRecipe> recipes = laboratoryRecipes(station);
            if (!recipes.isEmpty()) {
                runtime.getRecipeManager().addRecipes(
                        LaboratoryProcessRecipeCategory.type(station), recipes);
                registeredLaboratoryRecipes.put(station, recipes);
            }
        }
        if (!listeningForRecipeReloads) {
            NeoForge.EVENT_BUS.register(this);
            listeningForRecipeReloads = true;
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        if (listeningForRecipeReloads) {
            NeoForge.EVENT_BUS.unregister(this);
            listeningForRecipeReloads = false;
        }
        runtime = null;
        registeredCentrifugeRecipes = List.of();
        registeredDecalcificationRecipes = List.of();
        registeredIncubatorRecipes = List.of();
        registeredVaccineRecipes = List.of();
        registeredLaboratoryRecipes.clear();
    }

    @SubscribeEvent
    public void onRecipesUpdated(RecipesUpdatedEvent event) {
        if (runtime == null) return;
        if (!registeredCentrifugeRecipes.isEmpty()) {
            runtime.getRecipeManager().hideRecipes(CentrifugeRecipeCategory.RECIPE_TYPE,
                    registeredCentrifugeRecipes);
        }
        if (!registeredDecalcificationRecipes.isEmpty()) {
            runtime.getRecipeManager().hideRecipes(DecalcificationRecipeCategory.RECIPE_TYPE,
                    registeredDecalcificationRecipes);
        }
        if (!registeredIncubatorRecipes.isEmpty()) {
            runtime.getRecipeManager().hideRecipes(IncubatorRecipeCategory.RECIPE_TYPE,
                    registeredIncubatorRecipes);
        }
        registeredCentrifugeRecipes = centrifugeRecipes();
        registeredDecalcificationRecipes = decalcificationRecipes();
        registeredIncubatorRecipes = incubatorRecipes();
        runtime.getRecipeManager().addRecipes(CentrifugeRecipeCategory.RECIPE_TYPE,
                registeredCentrifugeRecipes);
        runtime.getRecipeManager().addRecipes(DecalcificationRecipeCategory.RECIPE_TYPE,
                registeredDecalcificationRecipes);
        runtime.getRecipeManager().addRecipes(IncubatorRecipeCategory.RECIPE_TYPE,
                registeredIncubatorRecipes);
        if (!registeredVaccineRecipes.isEmpty()) {
            runtime.getRecipeManager().hideRecipes(
                    VaccineMakerRecipeCategory.RECIPE_TYPE,
                    registeredVaccineRecipes);
        }
        for (LaboratoryStation station : LaboratoryStation.values()) {
            List<LaboratoryProcessRecipe> oldRecipes =
                    registeredLaboratoryRecipes.getOrDefault(station, List.of());
            if (!oldRecipes.isEmpty()) {
                runtime.getRecipeManager().hideRecipes(
                        LaboratoryProcessRecipeCategory.type(station), oldRecipes);
            }
            List<LaboratoryProcessRecipe> recipes = laboratoryRecipes(station);
            registeredLaboratoryRecipes.put(station, recipes);
            if (!recipes.isEmpty()) {
                runtime.getRecipeManager().addRecipes(
                        LaboratoryProcessRecipeCategory.type(station), recipes);
            }
        }
        registeredVaccineRecipes = vaccineMakerRecipes();
        if (!registeredVaccineRecipes.isEmpty()) {
            runtime.getRecipeManager().addRecipes(
                    VaccineMakerRecipeCategory.RECIPE_TYPE,
                    registeredVaccineRecipes);
        }
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                net.jenkimods.bioforge.world.centrifuge.CentrifugeMenu.class,
                BioForge.CENTRIFUGE_MENU.get(),
                CentrifugeRecipeCategory.RECIPE_TYPE,
                0,
                8,
                8,
                36
        );
        registration.addRecipeTransferHandler(
                net.jenkimods.bioforge.world.incubator.IncubatorMenu.class,
                BioForge.INCUBATOR_MENU.get(),
                IncubatorRecipeCategory.RECIPE_TYPE,
                0,
                4,
                4,
                36
        );
    }

    private static List<VaccineMakerRecipeWrapper> vaccineMakerRecipes() {
        return BioForgeResearchData.recipes(Minecraft.getInstance().level).stream()
                .map(VaccineMakerRecipeWrapper::new)
                .toList();
    }

    private static List<CentrifugeRecipeWrapper> centrifugeRecipes() {
        return CentrifugeRecipeManager.INSTANCE.getRecipes(Minecraft.getInstance().level)
                .stream().map(CentrifugeRecipeWrapper::new).toList();
    }

    private static List<DecalcificationRecipeWrapper> decalcificationRecipes() {
        return DecalcificationRecipeManager.INSTANCE.getRecipes(Minecraft.getInstance().level)
                .stream().map(DecalcificationRecipeWrapper::new).toList();
    }

    private static List<IncubatorRecipeWrapper> incubatorRecipes() {
        if (Minecraft.getInstance().level == null) return List.of();
        return Minecraft.getInstance().level.getRecipeManager()
                .getAllRecipesFor(IncubatorRecipeRegistration.TYPE).stream()
                .map(net.minecraft.world.item.crafting.RecipeHolder::value)
                .map(IncubatorRecipeWrapper::new).toList();
    }

    private static List<LaboratoryProcessRecipe> laboratoryRecipes(
            LaboratoryStation station) {
        return LaboratoryProcessRecipeManager.INSTANCE.recipes(Minecraft.getInstance().level).stream()
                .filter(recipe -> recipe.station() == station)
                .toList();
    }
}
