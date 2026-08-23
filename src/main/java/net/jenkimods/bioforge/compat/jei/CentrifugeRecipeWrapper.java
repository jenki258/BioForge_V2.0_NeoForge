package net.jenkimods.bioforge.compat.jei;

import net.jenkimods.bioforge.blood.BloodType;
import net.jenkimods.bioforge.item.BloodSampleUtil;
import net.jenkimods.bioforge.item.samples.TubeItem;
import net.jenkimods.bioforge.world.centrifuge.CentrifugeIngredient;
import net.jenkimods.bioforge.world.centrifuge.CentrifugeOutput;
import net.jenkimods.bioforge.world.centrifuge.CentrifugeRecipe;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

public class CentrifugeRecipeWrapper {

    private final CentrifugeRecipe recipe;
    private final List<ItemStack> inputs;
    private final List<ItemStack> outputs;

    public CentrifugeRecipeWrapper(CentrifugeRecipe recipe) {
        this.recipe = recipe;
        this.inputs = resolveIngredient(recipe.input());
        if (recipe.copyBloodData()) {
            inputs.stream()
                    .filter(stack -> stack.getItem() instanceof TubeItem)
                    .forEach(CentrifugeRecipeWrapper::markAsBloodFilled);
        }
        this.outputs = new ArrayList<>();

        if (!recipe.outputs().isEmpty()) {
            for (CentrifugeOutput out : recipe.outputs()) {
                outputs.addAll(resolveIngredient(out.ingredient()));
            }
        } else if (recipe.output() != null) {
            outputs.addAll(resolveIngredient(recipe.output()));
        }
    }

    private static List<ItemStack> resolveIngredient(CentrifugeIngredient ingredient) {
        List<ItemStack> result = new ArrayList<>();
        if (ingredient.isTag()) {
            StreamSupport.stream(BuiltInRegistries.ITEM.getTagOrEmpty(
                            net.minecraft.tags.ItemTags.create(
                                    net.minecraft.resources.ResourceLocation.tryParse(
                                            ingredient.toString().substring(1))))
                            .spliterator(), false)
                    .map(Holder::value)
                    .map(ItemStack::new)
                    .forEach(result::add);
        } else {
            Item item = ingredient.resolveItem(net.minecraft.util.RandomSource.create());
            if (item != null) result.add(new ItemStack(item));
        }
        return result;
    }

    private static void markAsBloodFilled(ItemStack stack) {
        BloodSampleUtil.setData(stack, 1, BloodType.O_POSITIVE, "JEI", null);
    }

    public List<ItemStack> getInputs() { return inputs; }
    public List<ItemStack> getOutputs() { return outputs; }
    public int getProcessingTime() { return recipe.processingTime(); }
    public boolean isCopyBloodData() { return recipe.copyBloodData(); }
    public boolean isCopyInfection() { return recipe.copyInfection(); }
    public CentrifugeRecipe getRecipe() { return recipe; }
}
