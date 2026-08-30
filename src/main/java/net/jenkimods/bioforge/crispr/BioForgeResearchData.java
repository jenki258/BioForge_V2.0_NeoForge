package net.jenkimods.bioforge.crispr;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.vaccine.DirectedVaccineAction;
import net.jenkimods.bioforge.vaccine.VaccineCorrectionProfile;
import net.jenkimods.bioforge.world.vaccine.VaccineMakerRecipe;
import net.jenkimods.bioforge.world.recipe.BioForgeRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@EventBusSubscriber(modid = BioForge.MODID)
public final class BioForgeResearchData {
    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static volatile Map<ResourceLocation, CrisprGuideProfile> guideProfiles = Map.of();
    private static volatile Map<ResourceLocation, CrisprCasModuleDefinition> casModules = Map.of();
    private static volatile Map<ResourceLocation, CrisprAssayDefinition> assays = Map.of();
    private static volatile Map<ResourceLocation, DirectedVaccineAction> actions = Map.of();
    private static volatile Map<ResourceLocation, VaccineCorrectionProfile>
            correctionProfiles = Map.of();
    private static volatile List<VaccineMakerRecipe> recipes = List.of();
    private static final Map<ResourceLocation, CrisprGuideProfile> JAVA_GUIDE_PROFILES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, CrisprCasModuleDefinition> JAVA_CAS_MODULES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, CrisprAssayDefinition> JAVA_ASSAYS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, DirectedVaccineAction> JAVA_ACTIONS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, VaccineCorrectionProfile> JAVA_CORRECTION_PROFILES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, VaccineMakerRecipe> JAVA_RECIPES = new LinkedHashMap<>();
    private static boolean javaRegistrationsFrozen;

    private BioForgeResearchData() {}

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new GuideProfileReloadListener());
        event.addListener(new CasModuleReloadListener());
        event.addListener(new AssayReloadListener());
        event.addListener(new ActionReloadListener());
        event.addListener(new CorrectionProfileReloadListener());
        event.addListener(new RecipeReloadListener());
    }

    public static Optional<CrisprGuideProfile> guideProfile(ResourceLocation id) {
        return Optional.ofNullable(guideProfiles.get(id));
    }

    public static Optional<DirectedVaccineAction> action(ResourceLocation id) {
        return Optional.ofNullable(actions.get(id));
    }

    public static Optional<CrisprCasModuleDefinition> casModule(ResourceLocation id) {
        return Optional.ofNullable(casModules.get(id));
    }

    public static Optional<CrisprAssayDefinition> assay(ResourceLocation id) {
        return Optional.ofNullable(assays.get(id));
    }

    public static Optional<VaccineCorrectionProfile> correctionProfile(
            ResourceLocation id) {
        return Optional.ofNullable(correctionProfiles.get(id));
    }

    public static Set<ResourceLocation> correctionProfileIds() {
        return Set.copyOf(correctionProfiles.keySet());
    }

    public static List<VaccineMakerRecipe> recipes() {
        return recipes;
    }

    public static List<VaccineMakerRecipe> recipes(Level level) {
        if (level == null) return recipes();
        List<VaccineMakerRecipe> combined = new ArrayList<>();
        level.getRecipeManager().getAllRecipesFor(BioForgeRecipeRegistration.VACCINE_MAKER_TYPE)
                .forEach(holder -> combined.add(holder.value().recipe()
                        .withId(holder.id())));
        combined.addAll(recipes);
        return List.copyOf(combined);
    }

    public static Set<ResourceLocation> guideProfileIds() {
        return Set.copyOf(guideProfiles.keySet());
    }

    public static Set<ResourceLocation> casModuleIds() {
        return Set.copyOf(casModules.keySet());
    }

    public static Set<ResourceLocation> actionIds() {
        return Set.copyOf(actions.keySet());
    }

    public static synchronized void registerGuideProfile(CrisprGuideProfile definition) {
        registerJava(JAVA_GUIDE_PROFILES, definition.id(), definition, "CRISPR guide profile");
    }

    public static synchronized void registerCasModule(CrisprCasModuleDefinition definition) {
        registerJava(JAVA_CAS_MODULES, definition.id(), definition, "CRISPR Cas module");
    }

    public static synchronized void registerAssay(CrisprAssayDefinition definition) {
        registerJava(JAVA_ASSAYS, definition.id(), definition, "CRISPR assay");
    }

    public static synchronized void registerAction(DirectedVaccineAction definition) {
        registerJava(JAVA_ACTIONS, definition.id(), definition, "directed vaccine action");
    }

    public static synchronized void registerCorrectionProfile(VaccineCorrectionProfile definition) {
        registerJava(JAVA_CORRECTION_PROFILES, definition.id(), definition, "vaccine correction profile");
    }

    public static synchronized void registerVaccineMakerRecipe(VaccineMakerRecipe recipe) {
        registerJava(JAVA_RECIPES, recipe.id(), recipe, "Vaccine Maker recipe");
    }

    public static synchronized void freezeJavaRegistrations() {
        javaRegistrationsFrozen = true;
    }

    private static final class GuideProfileReloadListener extends SimpleJsonResourceReloadListener {
        private GuideProfileReloadListener() {
            super(GSON, "crispr/guide_profiles");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries,
                             ResourceManager resourceManager, ProfilerFiller profiler) {
            Map<ResourceLocation, CrisprGuideProfile> loaded = new LinkedHashMap<>();
            entries.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> parse(entry.getKey(), entry.getValue(),
                            json -> loaded.put(entry.getKey(),
                                    CrisprGuideProfile.fromJson(entry.getKey(), json)),
                            "CRISPR guide profile"));
            mergeJava(loaded, JAVA_GUIDE_PROFILES);
            guideProfiles = Map.copyOf(loaded);
            BioForge.LOGGER.info("Loaded {} CRISPR guide profiles", loaded.size());
        }
    }

    private static final class ActionReloadListener extends SimpleJsonResourceReloadListener {
        private ActionReloadListener() {
            super(GSON, "vaccine_actions");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries,
                             ResourceManager resourceManager, ProfilerFiller profiler) {
            Map<ResourceLocation, DirectedVaccineAction> loaded = new LinkedHashMap<>();
            entries.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> parse(entry.getKey(), entry.getValue(),
                            json -> loaded.put(entry.getKey(),
                                    DirectedVaccineAction.fromJson(entry.getKey(), json)),
                            "directed vaccine action"));
            mergeJava(loaded, JAVA_ACTIONS);
            actions = Map.copyOf(loaded);
            BioForge.LOGGER.info("Loaded {} directed vaccine actions", loaded.size());
        }
    }

    private static final class CasModuleReloadListener extends SimpleJsonResourceReloadListener {
        private CasModuleReloadListener() {
            super(GSON, "crispr/cas_modules");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries,
                             ResourceManager resourceManager, ProfilerFiller profiler) {
            Map<ResourceLocation, CrisprCasModuleDefinition> loaded = new LinkedHashMap<>();
            entries.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> parse(entry.getKey(), entry.getValue(),
                            json -> loaded.put(entry.getKey(),
                                    CrisprCasModuleDefinition.fromJson(entry.getKey(), json)),
                            "CRISPR Cas module"));
            mergeJava(loaded, JAVA_CAS_MODULES);
            casModules = Map.copyOf(loaded);
            BioForge.LOGGER.info("Loaded {} CRISPR Cas modules", loaded.size());
        }
    }

    private static final class AssayReloadListener extends SimpleJsonResourceReloadListener {
        private AssayReloadListener() {
            super(GSON, "crispr/assays");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries,
                             ResourceManager resourceManager, ProfilerFiller profiler) {
            Map<ResourceLocation, CrisprAssayDefinition> loaded = new LinkedHashMap<>();
            entries.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> parse(entry.getKey(), entry.getValue(),
                            json -> loaded.put(entry.getKey(),
                                    CrisprAssayDefinition.fromJson(entry.getKey(), json)),
                            "CRISPR assay"));
            mergeJava(loaded, JAVA_ASSAYS);
            assays = Map.copyOf(loaded);
            BioForge.LOGGER.info("Loaded {} CRISPR assays", loaded.size());
        }
    }

    private static final class RecipeReloadListener extends SimpleJsonResourceReloadListener {
        private RecipeReloadListener() {
            super(GSON, "vaccine_maker");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries,
                             ResourceManager resourceManager, ProfilerFiller profiler) {
            Map<ResourceLocation, VaccineMakerRecipe> loaded = new LinkedHashMap<>();
            entries.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> parse(entry.getKey(), entry.getValue(),
                            json -> loaded.put(entry.getKey(), VaccineMakerRecipe.fromJson(entry.getKey(), json)),
                            "Vaccine Maker recipe"));
            mergeJava(loaded, JAVA_RECIPES);
            recipes = loaded.values().stream()
                    .sorted(Comparator.comparing(recipe -> recipe.id().toString()))
                    .toList();
            BioForge.LOGGER.info("Loaded {} Vaccine Maker recipes", recipes.size());
        }
    }

    private static final class CorrectionProfileReloadListener
            extends SimpleJsonResourceReloadListener {
        private CorrectionProfileReloadListener() {
            super(GSON, "vaccine_correction_profiles");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries,
                             ResourceManager resourceManager, ProfilerFiller profiler) {
            Map<ResourceLocation, VaccineCorrectionProfile> loaded =
                    new LinkedHashMap<>();
            entries.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> parse(entry.getKey(), entry.getValue(),
                            json -> loaded.put(entry.getKey(),
                                    VaccineCorrectionProfile.fromJson(
                                            entry.getKey(), json)),
                            "vaccine correction profile"));
            mergeJava(loaded, JAVA_CORRECTION_PROFILES);
            correctionProfiles = Map.copyOf(loaded);
            BioForge.LOGGER.info(
                    "Loaded {} vaccine correction profiles", loaded.size());
        }
    }

    private static void parse(ResourceLocation id, JsonElement element,
                              java.util.function.Consumer<JsonObject> parser, String type) {
        try {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException(type + " must be a JSON object");
            }
            parser.accept(element.getAsJsonObject());
        } catch (RuntimeException exception) {
            BioForge.LOGGER.error("Could not load {} {}", type, id, exception);
        }
    }

    private static <T> void registerJava(Map<ResourceLocation, T> registry, ResourceLocation id,
                                         T definition, String type) {
        if (javaRegistrationsFrozen) throw new IllegalStateException("BioForge research registries are frozen");
        if (id == null || definition == null) throw new IllegalArgumentException(type + " cannot be null");
        if (registry.putIfAbsent(id, definition) != null) {
            throw new IllegalArgumentException("Duplicate Java " + type + " " + id);
        }
    }

    private static <T> void mergeJava(Map<ResourceLocation, T> loaded,
                                      Map<ResourceLocation, T> javaDefinitions) {
        javaDefinitions.forEach(loaded::putIfAbsent);
    }
}
