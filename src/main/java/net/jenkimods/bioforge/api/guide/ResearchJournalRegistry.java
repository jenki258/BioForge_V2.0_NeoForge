package net.jenkimods.bioforge.api.guide;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.mutation.MutationDefinition;
import net.jenkimods.bioforge.mutation.MutationLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@EventBusSubscriber(modid = BioForge.MODID)
public final class ResearchJournalRegistry {
    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final Map<ResourceLocation, ResearchJournalPageDefinition> JAVA_PAGES =
            new LinkedHashMap<>();
    private static final Set<String> HIDDEN_COMPONENT_ITEM_PAGES = Set.of(
            "activated_carbon", "activated_filter", "agar_powder", "airtight_seal",
            "biomedical_processor", "black_steel_bars", "black_steel_blend",
            "black_steel_block", "black_steel_door", "black_steel_grate",
            "black_steel_ingot", "black_steel_mesh", "black_steel_nugget",
            "black_steel_plate", "black_steel_trapdoor", "chemical_resistant_coating",
            "electronic_control_unit", "insulated_lining", "laboratory_frame",
            "laboratory_glassware", "neutralizing_agent", "optical_lens", "polymer_resin",
            "precision_mechanism", "reinforced_glass", "respirator_valve",
            "sealed_biofabric", "sterile_filter", "sterile_polymer_sheet",
            "sterile_rubber", "sterilizing_solution", "sulfuric_acid",
            "surfactant_concentrate", "thermal_gel", "wine_must",
            "ceiling_viral_scanner", "open_left_viral_scanner", "open_right_viral_scanner");
    private static volatile List<ResearchJournalPageDefinition> pages = List.of();
    private static volatile int contentHash;
    private static boolean frozen;

    private ResearchJournalRegistry() {}

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ReloadListener(event.getRegistryAccess()));
    }

    public static synchronized void register(ResearchJournalPageDefinition page) {
        Objects.requireNonNull(page, "page");
        if (frozen) throw new IllegalStateException("Research Journal registry is frozen");
        if (JAVA_PAGES.putIfAbsent(page.id(), page) != null) {
            throw new IllegalArgumentException("Duplicate Java Research Journal page " + page.id());
        }
    }

    public static synchronized void freeze() {
        frozen = true;
    }

    public static List<ResearchJournalPageDefinition> pages() {
        return pages;
    }

    public static int contentHash() {
        return contentHash;
    }

    public static List<Component> createBookPages() {
        List<ResearchJournalPageDefinition> definitions = pages;
        if (definitions.isEmpty()) {
            return List.of(Component.translatable("research.bioforge.page.contents.title")
                    .append("\n\n")
                    .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD)
                    .append(Component.translatable(
                                    "gui.bioforge.research_journal.not_loaded")
                            .withStyle(ChatFormatting.BLACK)));
        }

        Map<ResourceLocation, Integer> pageNumbers = new LinkedHashMap<>();
        for (int index = 0; index < definitions.size(); index++) {
            pageNumbers.put(definitions.get(index).id(), index + 1);
        }

        List<Component> result = new ArrayList<>(definitions.size());
        for (ResearchJournalPageDefinition definition : definitions) {
            MutableComponent page = definition.title().copy().append("\n\n");
            for (ResearchJournalPageDefinition.Element element : definition.elements()) {
                MutableComponent part = element.component().copy();
                if (element.linkTarget() != null) {
                    Integer target = pageNumbers.get(element.linkTarget());
                    if (target != null) {
                        part.withStyle(style -> style.withClickEvent(new ClickEvent(
                                ClickEvent.Action.CHANGE_PAGE, Integer.toString(target))));
                    }
                }
                page.append(part);
                if (element.lineBreaks() > 0) {
                    page.append("\n".repeat(element.lineBreaks()));
                }
            }
            result.add(page);
        }
        return List.copyOf(result);
    }

    public static List<ResearchJournalPageView> createViews(
            Set<ResourceLocation> unlockedPages) {
        return createViews(null, unlockedPages, Set.of());
    }

    public static List<ResearchJournalPageView> createViews(
            ServerPlayer player, Set<ResourceLocation> unlockedPages,
            Set<ResourceLocation> lockedPages) {
        List<ResearchJournalPageView> result = new ArrayList<>();
        Map<ResourceLocation, List<ItemStack>> usageIndex = player == null
                ? Map.of() : net.jenkimods.bioforge.item.guide
                .ResearchJournalRecipeResolver.buildUsageIndex(player);
        boolean unlockRecipeAssigned = false;
        for (ResearchJournalPageDefinition definition : pages) {
            boolean unlocked = !lockedPages.contains(definition.id())
                    && (definition.unlockRequirements().isEmpty()
                    || unlockedPages.contains(definition.id()));
            MutableComponent body = Component.empty();
            if (unlocked) {
                for (ResearchJournalPageDefinition.Element element : definition.elements()) {
                    if (element.linkTarget() == null) {
                        body.append(element.component().copy());
                        if (element.lineBreaks() > 0) {
                            body.append("\n".repeat(element.lineBreaks()));
                        }
                    }
                }
            } else {
                body.append(Component.translatable("gui.bioforge.research_journal.locked_body")
                        .withStyle(ChatFormatting.GRAY));
            }
            List<ResearchJournalRecipeView> recipeViews = List.of();
            if (player != null) {
                if (unlocked) {
                    recipeViews = net.jenkimods.bioforge.item.guide
                            .ResearchJournalRecipeResolver.resolve(
                                    player, definition.recipes());
                } else if (!unlockRecipeAssigned
                        && !definition.unlockRequirements().isEmpty()) {
                    recipeViews = net.jenkimods.bioforge.item.guide
                            .ResearchJournalRecipeResolver.resolveUnlocks(
                                    player, definition.unlockRequirements());
                    unlockRecipeAssigned = !recipeViews.isEmpty();
                }
            }
            if (player != null && unlocked && definition.id().getPath().startsWith("items/")) {
                body = createItemBody(definition, player, recipeViews, usageIndex);
            }
            result.add(new ResearchJournalPageView(definition.id(),
                    unlocked ? definition.title().copy()
                            : Component.translatable("gui.bioforge.research_journal.locked_title"),
                    body, unlocked, recipeViews));
        }
        boolean mutationCatalogueUnlocked = result.stream().anyMatch(page ->
                page.id().equals(ResourceLocation.tryBuild(BioForge.MODID, "mutations"))
                        && page.unlocked());
        if (mutationCatalogueUnlocked) {
            MutationLoader.INSTANCE.getAllMutations().stream()
                    .filter(MutationDefinition::enabled)
                    .filter(definition -> !definition.hidden())
                    .sorted(Comparator.comparing(MutationDefinition::id))
                    .forEach(definition -> result.add(createMutationView(
                            definition, true)));
        }
        return List.copyOf(result);
    }

    private static MutableComponent createItemBody(
            ResearchJournalPageDefinition definition, ServerPlayer player,
            List<ResearchJournalRecipeView> recipes,
            Map<ResourceLocation, List<ItemStack>> usageIndex) {
        String itemPath = definition.id().getPath().substring("items/".length());
        ResourceLocation itemId = ResourceLocation.tryBuild(
                definition.id().getNamespace(), itemPath);
        Item item = itemId == null ? null
                : net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
        if (item == null) return Component.empty();
        ItemStack stack = new ItemStack(item);
        MutableComponent body = Component.translatable(
                "research.bioforge.item_summary.function").append("\n");
        List<Component> tooltips;
        try {
            tooltips = stack.getTooltipLines(
                    Item.TooltipContext.of(player.level()), player, TooltipFlag.NORMAL);
        } catch (RuntimeException exception) {
            tooltips = List.of();
        }
        int added = 0;
        for (int index = 1; index < tooltips.size() && added < 6; index++) {
            Component line = tooltips.get(index);
            if (line.getString().isBlank()) continue;
            if (added > 0) body.append("\n");
            body.append(line.copy());
            added++;
        }
        if (added == 0) {
            body.append(Component.translatable(
                    "research.bioforge.item_summary.component", stack.getHoverName()));
        }

        body.append("\n\n").append(Component.translatable(
                "research.bioforge.item_summary.acquisition")).append("\n");
        String specialSource = specialAcquisitionKey(itemPath);
        if (specialSource != null) {
            body.append(Component.translatable(specialSource));
        } else if (!recipes.isEmpty()) {
            MutableComponent stations = Component.empty();
            boolean first = true;
            for (ResearchJournalRecipeView recipe : recipes) {
                if (!first) stations.append(", ");
                stations.append(recipe.station().copy());
                first = false;
            }
            body.append(Component.translatable(
                    "research.bioforge.item_summary.produced_at", stations));
        } else {
            body.append(Component.translatable(
                    "research.bioforge.item_summary.no_direct_recipe"));
        }

        List<ItemStack> uses = usageIndex.getOrDefault(itemId, List.of());
        if (!uses.isEmpty()) {
            body.append("\n\n").append(Component.translatable(
                    "research.bioforge.item_summary.used_for")).append("\n");
            int limit = Math.min(6, uses.size());
            for (int index = 0; index < limit; index++) {
                if (index > 0) body.append(", ");
                body.append(uses.get(index).getHoverName());
            }
            if (uses.size() > limit) {
                body.append(Component.translatable(
                        "research.bioforge.item_summary.more_uses", uses.size() - limit));
            }
        }
        return body;
    }

    private static String specialAcquisitionKey(String itemPath) {
        return switch (itemPath) {
            case "split_bone" -> "research.bioforge.item_summary.source.split_bone";
            case "withered_split_bone" ->
                    "research.bioforge.item_summary.source.withered_split_bone";
            case "bone_marrow" -> "research.bioforge.item_summary.source.bone_marrow";
            case "withered_bone_marrow" ->
                    "research.bioforge.item_summary.source.withered_bone_marrow";
            case "plasma_sample", "cell_pellet" ->
                    "research.bioforge.item_summary.source.centrifuged_tube";
            default -> null;
        };
    }

    private static ResearchJournalPageView createMutationView(
            MutationDefinition definition, boolean unlocked) {
        ResourceLocation id = ResourceLocation.tryParse(definition.id());
        if (id == null) id = ResourceLocation.tryBuild(BioForge.MODID, definition.id());
        ResourceLocation pageId = ResourceLocation.tryBuild(BioForge.MODID,
                "mutation/" + id.getNamespace() + "/" + id.getPath());
        if (!unlocked) {
            return new ResearchJournalPageView(pageId,
                    Component.translatable("gui.bioforge.research_journal.locked_title"),
                    Component.translatable("gui.bioforge.research_journal.locked_body")
                            .withStyle(ChatFormatting.GRAY), false, List.of());
        }

        MutableComponent body = Component.translatable(definition.descriptionKey())
                .append("\n\n")
                .append(Component.translatable("research.bioforge.mutation_catalog.rarity",
                        Component.translatable("mutation.rarity."
                                + definition.rarity())));
        if (!definition.upgradeTo().isEmpty()) {
            body.append("\n").append(Component.translatable(
                    "research.bioforge.mutation_catalog.upgrade",
                    mutationName(definition.upgradeTo())));
        }
        if (!definition.requiredMutations().isEmpty()) {
            MutableComponent requirements = Component.empty();
            boolean first = true;
            for (String required : definition.requiredMutations()) {
                if (!first) requirements.append(", ");
                requirements.append(mutationName(required));
                first = false;
            }
            body.append("\n").append(Component.translatable(
                    "research.bioforge.mutation_catalog.requires", requirements));
        }
        return new ResearchJournalPageView(pageId,
                Component.translatable(definition.nameKey()), body, true, List.of());
    }

    private static Component mutationName(String mutationId) {
        return MutationLoader.INSTANCE.getMutation(mutationId)
                .<Component>map(definition -> Component.translatable(definition.nameKey()))
                .orElseGet(() -> Component.literal(mutationId));
    }

    private static void replacePages(Map<ResourceLocation, ResearchJournalPageDefinition> loaded,
                                     HolderLookup.Provider registries) {
        JAVA_PAGES.forEach(loaded::putIfAbsent);
        List<ResearchJournalPageDefinition> sorted = loaded.values().stream()
                .filter(page -> !isHiddenComponentItemPage(page.id()))
                .sorted(Comparator.comparingInt(ResearchJournalPageDefinition::order)
                        .thenComparing(page -> page.id().toString()))
                .toList();
        pages = sorted;
        contentHash = calculateHash(sorted, registries);
        validateLinks(sorted);
    }

    private static boolean isHiddenComponentItemPage(ResourceLocation pageId) {
        String path = pageId.getPath();
        return path.startsWith("items/")
                && HIDDEN_COMPONENT_ITEM_PAGES.contains(path.substring("items/".length()));
    }

   private static int calculateHash(List<ResearchJournalPageDefinition> definitions,
                                     HolderLookup.Provider registries) {
        int hash = 1;
        for (ResearchJournalPageDefinition page : definitions) {
            hash = 31 * hash + page.id().hashCode();
            hash = 31 * hash + page.order();
            hash = 31 * hash + Component.Serializer.toJson(page.title(), registries).hashCode();
            hash = 31 * hash + Boolean.hashCode(page.requireAllUnlocks());
            for (ResearchJournalRecipeReference recipe : page.recipes()) {
                hash = 31 * hash + recipe.type().hashCode();
                hash = 31 * hash + recipe.id().hashCode();
            }
            for (ResearchJournalPageDefinition.UnlockRequirement requirement
                    : page.unlockRequirements()) {
                hash = 31 * hash + requirement.value().hashCode();
            }
            for (ResearchJournalPageDefinition.Element element : page.elements()) {
                hash = 31 * hash
                        + Component.Serializer.toJson(element.component(), registries).hashCode();
                hash = 31 * hash + Objects.hashCode(element.linkTarget());
                hash = 31 * hash + element.lineBreaks();
            }
        }
        return hash;
    }

    private static void validateLinks(List<ResearchJournalPageDefinition> definitions) {
        java.util.Set<ResourceLocation> ids = definitions.stream()
                .map(ResearchJournalPageDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        for (ResearchJournalPageDefinition page : definitions) {
            for (ResearchJournalPageDefinition.Element element : page.elements()) {
                if (element.linkTarget() != null && !ids.contains(element.linkTarget())) {
                    BioForge.LOGGER.warn("Research Journal page {} links to missing page {}",
                            page.id(), element.linkTarget());
                }
            }
        }
    }

    private static final class ReloadListener extends SimpleJsonResourceReloadListener {
        private final HolderLookup.Provider registries;

        private ReloadListener(HolderLookup.Provider registries) {
            super(GSON, "research_journal");
            this.registries = registries;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries,
                             ResourceManager resourceManager, ProfilerFiller profiler) {
            Map<ResourceLocation, ResearchJournalPageDefinition> loaded = new LinkedHashMap<>();
            entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                try {
                    if (!entry.getValue().isJsonObject()) {
                        throw new IllegalArgumentException("Research Journal page must be an object");
                    }
                    loaded.put(entry.getKey(), ResearchJournalPageDefinition.fromJson(
                            entry.getKey(), entry.getValue().getAsJsonObject(), registries));
                } catch (RuntimeException exception) {
                    BioForge.LOGGER.error("Could not load Research Journal page {}",
                            entry.getKey(), exception);
                }
            });
            replacePages(loaded, registries);
            BioForge.LOGGER.info("Loaded {} Research Journal pages", pages.size());
        }
    }
}
