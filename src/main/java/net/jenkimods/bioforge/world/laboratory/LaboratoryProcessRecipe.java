package net.jenkimods.bioforge.world.laboratory;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record LaboratoryProcessRecipe(ResourceLocation id, LaboratoryStation station,
                                      List<Ingredient> ingredients, ItemStack result,
                                      ItemStack waste, boolean copyNbt,
                                      int processingTime) {
    public LaboratoryProcessRecipe {
        ingredients = List.copyOf(ingredients);
        result = result.copy();
        waste = waste.copy();
        processingTime = Math.max(1, processingTime);
    }

    public LaboratoryProcessRecipe(ResourceLocation id, LaboratoryStation station,
                                   List<Ingredient> ingredients, ItemStack result,
                                   int processingTime) {
        this(id, station, ingredients, result, ItemStack.EMPTY, false, processingTime);
    }

    public LaboratoryProcessRecipe withId(ResourceLocation newId) {
        return new LaboratoryProcessRecipe(newId, station, ingredients, result,
                waste, copyNbt, processingTime);
    }

    public boolean matches(ItemStackHandler inventory) {
        return findSlots(inventory) != null;
    }

    public void consume(ItemStackHandler inventory) {
        int[] slots = findSlots(inventory);
        if (slots == null) return;
        for (int slot : slots) inventory.extractItem(slot, 1, false);
    }

    private int[] findSlots(ItemStackHandler inventory) {
        List<Integer> occupied = new ArrayList<>();
        for (int slot = 0; slot < station.inputSlots(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) occupied.add(slot);
        }
        if (occupied.size() != ingredients.size()) return null;
        int[] assignment = new int[ingredients.size()];
        boolean[] used = new boolean[station.inputSlots()];
        return assign(inventory, 0, assignment, used) ? assignment : null;
    }

    private boolean assign(ItemStackHandler inventory, int ingredientIndex,
                           int[] assignment, boolean[] used) {
        if (ingredientIndex >= ingredients.size()) return true;
        Ingredient ingredient = ingredients.get(ingredientIndex);
        for (int slot = 0; slot < station.inputSlots(); slot++) {
            if (used[slot] || !ingredientMatches(ingredient, inventory.getStackInSlot(slot))) continue;
            used[slot] = true;
            assignment[ingredientIndex] = slot;
            if (assign(inventory, ingredientIndex + 1, assignment, used)) return true;
            used[slot] = false;
        }
        return false;
    }

    public boolean matchesSingle(ItemStack stack) {
        return ingredients.size() == 1 && ingredientMatches(ingredients.get(0), stack);
    }

    private static boolean ingredientMatches(Ingredient ingredient, ItemStack stack) {
        if (ingredient.test(stack)) return true;
        for (ItemStack expected : ingredient.getItems()) {
            if (!ItemStack.isSameItem(expected, stack)) continue;
            DataComponentPatch required = expected.getComponentsPatch();
            if (!required.isEmpty() && containsRequiredComponents(stack, required)) return true;
        }
        return false;
    }

    private static boolean containsRequiredComponents(ItemStack actual, DataComponentPatch required) {
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : required.entrySet()) {
            Optional<?> expectedValue = entry.getValue();
            if (expectedValue.isPresent()) {
                if (!componentEquals(actual, entry.getKey(), expectedValue.get())) return false;
            } else if (actual.has(entry.getKey())) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean componentEquals(ItemStack actual, DataComponentType<?> type,
                                           Object expectedValue) {
        return Objects.equals(actual.get((DataComponentType) type), expectedValue);
    }
}
