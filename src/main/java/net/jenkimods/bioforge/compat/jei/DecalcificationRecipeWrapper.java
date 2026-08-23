package net.jenkimods.bioforge.compat.jei;

import net.jenkimods.bioforge.world.decalcification.DecalcificationRecipe;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

public class DecalcificationRecipeWrapper {

    private final DecalcificationRecipe recipe;
    private final List<ItemStack> input;
    private final List<ItemStack> output;

    public DecalcificationRecipeWrapper(DecalcificationRecipe recipe) {
        this.recipe = recipe;
        this.input = resolveStacks(recipe.input());
        this.output = resolveStacks(recipe.output());
    }

    private static List<ItemStack> resolveStacks(String str) {
        List<ItemStack> result = new ArrayList<>();
        if (str.startsWith("#")) {
            ResourceLocation loc = ResourceLocation.tryParse(str.substring(1));
            if (loc == null) return result;
            StreamSupport.stream(BuiltInRegistries.ITEM.getTagOrEmpty(
                            ItemTags.create(loc)).spliterator(), false)
                    .map(Holder::value)
                    .map(ItemStack::new)
                    .forEach(result::add);
        } else {
            ResourceLocation loc = ResourceLocation.tryParse(str);
            if (loc == null) return result;
            Item item = BuiltInRegistries.ITEM.getOptional(loc).orElse(null);
            if (item != null) result.add(new ItemStack(item));
        }
        return result;
    }

    public List<ItemStack> getInputs() {
        return input;
    }

    public List<ItemStack> getOutputs() {
        return output;
    }

    public boolean isCopyBloodData() {
        return recipe.copyBloodData();
    }

    public DecalcificationRecipe getRecipe() {
        return recipe;
    }
}
