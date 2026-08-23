package net.jenkimods.bioforge.compat.jei;

import net.jenkimods.bioforge.blood.BloodType;
import net.jenkimods.bioforge.infection.PathogenType;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.item.BloodSampleUtil;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.jenkimods.bioforge.world.incubator.IncubatorIngredient;
import net.jenkimods.bioforge.world.incubator.IncubatorOperation;
import net.jenkimods.bioforge.world.incubator.IncubatorRecipe;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class IncubatorRecipeWrapper {

    private final IncubatorRecipe recipe;
    private final List<ItemStack> primaryInputs;
    private final List<ItemStack> secondaryInputs;
    private final List<ItemStack> outputs;

    public IncubatorRecipeWrapper(IncubatorRecipe recipe) {
        this.recipe = recipe;
        IncubatorIngredient displayedPrimary =
                recipe.jeiInput() == null ? recipe.primaryInput() : recipe.jeiInput();
        this.primaryInputs = resolve(displayedPrimary, Math.max(1, recipe.primaryItemCost()));
        this.secondaryInputs = resolve(recipe.secondaryInput(), 1);
        this.outputs = resolve(recipe.output(), recipe.outputCount());
        decorateRepresentativeStacks();
    }

    private static List<ItemStack> resolve(IncubatorIngredient ingredient, int count) {
        List<ItemStack> stacks = new ArrayList<>();
        ingredient.resolveItems().stream()
                .map(item -> new ItemStack(item,
                        Math.min(count, item.getDefaultMaxStackSize())))
                .forEach(stacks::add);
        return stacks;
    }

    private void decorateRepresentativeStacks() {
        String strain = createRepresentativeStrain();

        switch (recipe.operation()) {
            case CRAFT -> {
                return;
            }
            case GENERATE_STRAIN -> primaryInputs.forEach(stack -> {
                net.jenkimods.bioforge.item.reagents.CatalystVialItem.setPathogen(
                        stack, net.jenkimods.bioforge.api.definition.BioForgeIds.pathogen(
                                PathogenType.VIRUS));
                net.jenkimods.bioforge.util.StackData.update(stack,
                        tag -> tag.putInt("Charges", Math.max(1, recipe.catalystChargeCost())));
            });
            case COPY_SAMPLE_STRAIN ->
                    primaryInputs.forEach(stack ->
                            NbtObfuscator.writeString(stack, strain));
            case COPY_BLOOD_STRAIN -> primaryInputs.forEach(stack -> {
                BloodSampleUtil.setData(stack, 1, BloodType.O_POSITIVE, "JEI", null);
                NbtObfuscator.writeInfection(stack, strain);
            });
        }

        outputs.forEach(stack -> NbtObfuscator.writeString(stack, strain));
    }

    private static String createRepresentativeStrain() {
        StrainData strain = StrainData.createEmpty();
        strain.setPathogen(PathogenType.VIRUS);
        strain.setColonyId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        return strain.toPayload();
    }

    public IncubatorRecipe getRecipe() {
        return recipe;
    }

    public List<ItemStack> getPrimaryInputs() {
        return primaryInputs;
    }

    public List<ItemStack> getSecondaryInputs() {
        return secondaryInputs;
    }

    public List<ItemStack> getOutputs() {
        return outputs;
    }

    public int getProcessingTime() {
        return recipe.processingTime();
    }

    public IncubatorOperation getOperation() {
        return recipe.operation();
    }

    public int getPrimaryItemCost() {
        return recipe.primaryItemCost();
    }

    public boolean isPrimaryCostPerOutput() {
        return recipe.primaryCostPerOutput();
    }

    public int getCatalystChargeCost() {
        return recipe.catalystChargeCost();
    }
}
