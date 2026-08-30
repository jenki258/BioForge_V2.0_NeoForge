package net.jenkimods.bioforge.item.guide;

import net.jenkimods.bioforge.api.guide.ResearchJournalRecipeReference;
import net.jenkimods.bioforge.api.guide.ResearchJournalRecipeView;
import net.jenkimods.bioforge.api.guide.ResearchJournalPageDefinition;
import net.jenkimods.bioforge.crispr.BioForgeResearchData;
import net.jenkimods.bioforge.world.laboratory.LaboratoryProcessRecipe;
import net.jenkimods.bioforge.world.laboratory.LaboratoryProcessRecipeManager;
import net.jenkimods.bioforge.world.vaccine.VaccineMakerRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;

public final class ResearchJournalRecipeResolver {
    private static final int MAX_UNLOCK_RECIPES = 3;

    private ResearchJournalRecipeResolver() {}

    public static List<ResearchJournalRecipeView> resolve(
            ServerPlayer player, List<ResearchJournalRecipeReference> references) {
        Map<String, ResearchJournalRecipeView> resolved = new LinkedHashMap<>();
        for (ResearchJournalRecipeReference reference : references) {
            resolve(player, reference).ifPresent(view -> resolved.putIfAbsent(
                    reference.type().serializedName() + '|' + reference.id(), view));
        }
        return List.copyOf(resolved.values());
    }

    public static List<ResearchJournalRecipeView> resolveUnlocks(
            ServerPlayer player,
            List<ResearchJournalPageDefinition.UnlockRequirement> requirements) {
        Map<String, ResearchJournalRecipeView> resolved = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : player.serverLevel().getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            ItemStack output = recipe.getResultItem(
                    player.serverLevel().registryAccess());
            if (matchesAny(List.of(output), requirements)) {
                resolved.putIfAbsent("crafting|" + holder.id(),
                        vanilla(player, holder));
                if (resolved.size() >= MAX_UNLOCK_RECIPES) {
                    return List.copyOf(resolved.values());
                }
            }
        }
        for (LaboratoryProcessRecipe recipe
                : LaboratoryProcessRecipeManager.INSTANCE.recipes(player.serverLevel())) {
            if (matchesAny(List.of(recipe.result(), recipe.waste()), requirements)) {
                resolved.putIfAbsent("laboratory|" + recipe.id(),
                        laboratory(recipe));
                if (resolved.size() >= MAX_UNLOCK_RECIPES) {
                    return List.copyOf(resolved.values());
                }
            }
        }
        for (VaccineMakerRecipe recipe : BioForgeResearchData.recipes(player.serverLevel())) {
            ResearchJournalRecipeView view = vaccineMaker(recipe);
            if (matchesAny(view.results(), requirements)) {
                resolved.putIfAbsent("vaccine_maker|" + recipe.id(), view);
                if (resolved.size() >= MAX_UNLOCK_RECIPES) {
                    return List.copyOf(resolved.values());
                }
            }
        }
        return List.copyOf(resolved.values());
    }

    public static Map<net.minecraft.resources.ResourceLocation, List<ItemStack>>
    buildUsageIndex(ServerPlayer player) {
        Map<net.minecraft.resources.ResourceLocation,
                LinkedHashMap<net.minecraft.resources.ResourceLocation, ItemStack>> index =
                new LinkedHashMap<>();
        for (RecipeHolder<?> holder : player.serverLevel().getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            ItemStack output = recipe.getResultItem(player.serverLevel().registryAccess()).copy();
            indexIngredients(index, recipe.getIngredients(), List.of(output));
        }
        for (LaboratoryProcessRecipe recipe
                : LaboratoryProcessRecipeManager.INSTANCE.recipes(player.serverLevel())) {
            List<ItemStack> outputs = new ArrayList<>();
            if (!recipe.result().isEmpty()) outputs.add(recipe.result().copy());
            if (!recipe.waste().isEmpty()) outputs.add(recipe.waste().copy());
            indexIngredients(index, recipe.ingredients(), outputs);
        }
        for (VaccineMakerRecipe recipe : BioForgeResearchData.recipes(player.serverLevel())) {
            List<Ingredient> ingredients = new ArrayList<>();
            add(ingredients, recipe.sample());
            add(ingredients, recipe.carrier());
            add(ingredients, recipe.reagent());
            add(ingredients, recipe.report());
            add(ingredients, recipe.cartridge());
            add(ingredients, recipe.casModule());
            indexIngredients(index, ingredients, vaccineMaker(recipe).results());
        }
        Map<net.minecraft.resources.ResourceLocation, List<ItemStack>> result =
                new LinkedHashMap<>();
        index.forEach((id, outputs) -> result.put(id, List.copyOf(outputs.values())));
        return Map.copyOf(result);
    }

    private static void indexIngredients(
            Map<net.minecraft.resources.ResourceLocation,
                    LinkedHashMap<net.minecraft.resources.ResourceLocation, ItemStack>> index,
            List<Ingredient> ingredients, List<ItemStack> outputs) {
        if (outputs.isEmpty()) return;
        Set<net.minecraft.resources.ResourceLocation> inputIds = new LinkedHashSet<>();
        for (Ingredient ingredient : ingredients) {
            for (ItemStack input : ingredient.getItems()) {
                inputIds.add(net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(input.getItem()));
            }
        }
        for (net.minecraft.resources.ResourceLocation inputId : inputIds) {
            LinkedHashMap<net.minecraft.resources.ResourceLocation, ItemStack> uses =
                    index.computeIfAbsent(inputId, ignored -> new LinkedHashMap<>());
            for (ItemStack output : outputs) {
                if (output.isEmpty()) continue;
                net.minecraft.resources.ResourceLocation outputId =
                        net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(output.getItem());
                if (outputId.equals(inputId)) continue;
                ItemStack display = output.copy();
                display.setCount(1);
                uses.putIfAbsent(outputId, display);
            }
        }
    }

    private static Optional<ResearchJournalRecipeView> resolve(
            ServerPlayer player, ResearchJournalRecipeReference reference) {
        return switch (reference.type()) {
            case CRAFTING -> player.serverLevel().getRecipeManager()
                    .byKey(reference.id()).map(holder -> vanilla(player, holder))
                    .or(() -> player.serverLevel().getRecipeManager().getRecipes().stream()
                            .filter(holder -> outputMatches(player, holder, reference.id()))
                            .findFirst().map(holder -> vanilla(player, holder)));
            case LABORATORY -> LaboratoryProcessRecipeManager.INSTANCE
                    .recipes(player.serverLevel()).stream()
                    .filter(recipe -> recipe.id().equals(reference.id())
                            || stackMatches(recipe.result(), reference.id())
                            || stackMatches(recipe.waste(), reference.id()))
                    .findFirst().map(ResearchJournalRecipeResolver::laboratory);
            case VACCINE_MAKER -> BioForgeResearchData.recipes(player.serverLevel()).stream()
                    .filter(recipe -> recipe.id().equals(reference.id())
                            || vaccineMaker(recipe).results().stream()
                            .anyMatch(stack -> stackMatches(stack, reference.id())))
                    .findFirst().map(ResearchJournalRecipeResolver::vaccineMaker);
        };
    }

    private static boolean outputMatches(ServerPlayer player, RecipeHolder<?> holder,
                                         net.minecraft.resources.ResourceLocation itemId) {
        return stackMatches(holder.value().getResultItem(
                player.serverLevel().registryAccess()), itemId);
    }

    private static boolean stackMatches(ItemStack stack,
                                        net.minecraft.resources.ResourceLocation itemId) {
        return !stack.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem()).equals(itemId);
    }

    private static ResearchJournalRecipeView vanilla(ServerPlayer player,
                                                      RecipeHolder<?> holder) {
        Recipe<?> recipe = holder.value();
        boolean cooking = recipe instanceof AbstractCookingRecipe;
        int width = cooking ? 1 : recipe instanceof ShapedRecipe shaped
                ? Math.max(1, shaped.getWidth()) : 3;
        int height = cooking ? 1 : recipe instanceof ShapedRecipe shaped
                ? Math.max(1, shaped.getHeight())
                : Math.max(1, (recipe.getIngredients().size() + 2) / 3);
        ItemStack result = recipe.getResultItem(
                player.serverLevel().registryAccess()).copy();
        return new ResearchJournalRecipeView(holder.id(), cookingStation(recipe), width, height,
                choices(recipe.getIngredients()), List.of(result));
    }

    private static Component cookingStation(Recipe<?> recipe) {
        String key = recipe instanceof BlastingRecipe ? "blasting"
                : recipe instanceof SmeltingRecipe ? "smelting"
                : recipe instanceof SmokingRecipe ? "smoking"
                : recipe instanceof CampfireCookingRecipe ? "campfire"
                : "crafting";
        return Component.translatable(
                "gui.bioforge.research_journal.station." + key);
    }

    private static ResearchJournalRecipeView laboratory(LaboratoryProcessRecipe recipe) {
        List<ItemStack> outputs = new ArrayList<>();
        outputs.add(recipe.result());
        if (!recipe.waste().isEmpty()) outputs.add(recipe.waste());
        int width = Math.min(3, Math.max(1, recipe.ingredients().size()));
        int height = Math.max(1, (recipe.ingredients().size() + width - 1) / width);
        return new ResearchJournalRecipeView(recipe.id(), Component.translatable(
                "jei.bioforge.category." + recipe.station().getSerializedName()),
                width, height, choices(recipe.ingredients()), outputs);
    }

    private static ResearchJournalRecipeView vaccineMaker(VaccineMakerRecipe recipe) {
        List<Ingredient> inputs = new ArrayList<>();
        add(inputs, recipe.sample());
        add(inputs, recipe.carrier());
        add(inputs, recipe.reagent());
        add(inputs, recipe.report());
        add(inputs, recipe.cartridge());
        add(inputs, recipe.casModule());

        List<ItemStack> outputs = new ArrayList<>();
        if (recipe.fullResult() != null) outputs.add(new ItemStack(recipe.fullResult()));
        recipe.directedResults().values().stream().distinct()
                .forEach(item -> outputs.add(new ItemStack(item)));
        if (outputs.isEmpty()) {
            ItemStack[] carrierChoices = recipe.carrier().getItems();
            if (carrierChoices.length > 0) outputs.add(carrierChoices[0].copy());
        }
        return new ResearchJournalRecipeView(recipe.id(), Component.translatable(
                "jei.bioforge.category.vaccine_maker"), 3,
                Math.max(1, (inputs.size() + 2) / 3), choices(inputs), outputs);
    }

    private static void add(List<Ingredient> ingredients, Ingredient ingredient) {
        if (ingredient != null && !ingredient.isEmpty()) ingredients.add(ingredient);
    }

    private static List<List<ItemStack>> choices(List<Ingredient> ingredients) {
        List<List<ItemStack>> result = new ArrayList<>(ingredients.size());
        for (Ingredient ingredient : ingredients) {
            List<ItemStack> alternatives = new ArrayList<>();
            for (ItemStack choice : ingredient.getItems()) {
                ItemStack display = choice.copy();
                display.setCount(1);
                alternatives.add(display);
            }
            result.add(List.copyOf(alternatives));
        }
        return List.copyOf(result);
    }

    private static boolean matchesAny(
            List<ItemStack> results,
            List<ResearchJournalPageDefinition.UnlockRequirement> requirements) {
        for (ItemStack result : results) {
            for (ResearchJournalPageDefinition.UnlockRequirement requirement
                    : requirements) {
                if (requirement.test(result)) return true;
            }
        }
        return false;
    }
}
