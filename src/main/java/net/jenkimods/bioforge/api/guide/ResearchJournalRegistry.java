package net.jenkimods.bioforge.api.guide;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.jenkimods.bioforge.BioForge;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
            result.add(new ResearchJournalPageView(definition.id(),
                    unlocked ? definition.title().copy()
                            : Component.translatable("gui.bioforge.research_journal.locked_title"),
                    body, unlocked, recipeViews));
        }
        return List.copyOf(result);
    }

    private static void replacePages(Map<ResourceLocation, ResearchJournalPageDefinition> loaded,
                                     HolderLookup.Provider registries) {
        JAVA_PAGES.forEach(loaded::putIfAbsent);
        List<ResearchJournalPageDefinition> sorted = loaded.values().stream()
                .sorted(Comparator.comparingInt(ResearchJournalPageDefinition::order)
                        .thenComparing(page -> page.id().toString()))
                .toList();
        pages = sorted;
        contentHash = calculateHash(sorted, registries);
        validateLinks(sorted);
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
