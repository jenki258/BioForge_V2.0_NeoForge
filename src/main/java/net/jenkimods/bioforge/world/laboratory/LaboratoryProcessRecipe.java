package net.jenkimods.bioforge.world.laboratory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

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
            if (used[slot] || !ingredient.test(inventory.getStackInSlot(slot))) continue;
            used[slot] = true;
            assignment[ingredientIndex] = slot;
            if (assign(inventory, ingredientIndex + 1, assignment, used)) return true;
            used[slot] = false;
        }
        return false;
    }

    public boolean matchesSingle(ItemStack stack) {
        return ingredients.size() == 1 && ingredients.get(0).test(stack);
    }
}
