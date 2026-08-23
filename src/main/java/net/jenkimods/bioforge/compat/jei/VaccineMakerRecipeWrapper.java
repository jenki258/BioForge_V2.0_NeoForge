package net.jenkimods.bioforge.compat.jei;

import net.jenkimods.bioforge.crispr.VaccineTargetCategory;
import net.jenkimods.bioforge.world.vaccine.VaccineMakerOperation;
import net.jenkimods.bioforge.world.vaccine.VaccineMakerRecipe;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class VaccineMakerRecipeWrapper {
    private final VaccineMakerRecipe recipe;

    public VaccineMakerRecipeWrapper(VaccineMakerRecipe recipe) {
        this.recipe = recipe;
    }

    public VaccineMakerRecipe recipe() {
        return recipe;
    }

    public List<ItemStack> sampleInputs() {
        return copies(recipe.sample().getItems());
    }

    public List<ItemStack> carrierInputs() {
        return copies(recipe.carrier().getItems());
    }

    public List<ItemStack> reagentInputs() {
        return copies(recipe.reagent().getItems());
    }

    public List<ItemStack> reportInputs() {
        return copies(recipe.report().getItems());
    }

    public List<ItemStack> cartridgeInputs() {
        return copies(recipe.cartridge().getItems());
    }

    public List<ItemStack> casInputs() {
        return copies(recipe.casModule().getItems());
    }

    public List<ItemStack> outputs() {
        List<ItemStack> result = new ArrayList<>();
        if (recipe.fullResult() != null) {
            result.add(new ItemStack(recipe.fullResult()));
        } else if (recipe.operation() == VaccineMakerOperation.DIRECTED) {
            if (recipe.fixedDirectedCategory() != null
                    && recipe.directedResult(recipe.fixedDirectedCategory()) != null) {
                result.add(new ItemStack(
                        recipe.directedResult(recipe.fixedDirectedCategory())));
                return result;
            }
            for (VaccineTargetCategory category : VaccineTargetCategory.values()) {
                if (recipe.directedResult(category) != null) {
                    result.add(new ItemStack(recipe.directedResult(category)));
                }
            }
        } else if (recipe.operation() == VaccineMakerOperation.CLONE) {
            result.addAll(sampleInputs());
        }
        return result;
    }

    private static List<ItemStack> copies(ItemStack[] stacks) {
        return Arrays.stream(stacks).map(ItemStack::copy).toList();
    }
}
