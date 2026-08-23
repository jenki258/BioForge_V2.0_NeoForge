package net.jenkimods.bioforge.compat.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.jenkimods.bioforge.BioForge;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

public class CentrifugeRecipeCategory implements IRecipeCategory<CentrifugeRecipeWrapper> {

    public static final RecipeType<CentrifugeRecipeWrapper> RECIPE_TYPE =
            RecipeType.create(BioForge.MODID, "centrifuge", CentrifugeRecipeWrapper.class);

    private static final ResourceLocation GUI_TEXTURE =
            Objects.requireNonNull(ResourceLocation.tryBuild(
                    "bioforge", "textures/gui/laboratory/centrifuge.png"));

    private static final int GUI_W = 176;
    private static final int GUI_H = 110;

    private static final int[] SLOT_X = {79, 51, 107, 116, 41, 51, 79, 107};
    private static final int[] SLOT_Y = {11, 22, 22, 49, 49, 76, 86, 76};

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawableAnimated progressSpinner;

    public CentrifugeRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createDrawable(GUI_TEXTURE, 0, 0, GUI_W, GUI_H);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(BioForge.CENTRIFUGE.get()));
        this.progressSpinner = guiHelper.drawableBuilder(GUI_TEXTURE, 208, 0, 32, 32)
                .buildAnimated(100, IDrawableAnimated.StartDirection.LEFT, false);
    }

    @Override
    public RecipeType<CentrifugeRecipeWrapper> getRecipeType() { return RECIPE_TYPE; }

    @Override
    public Component getTitle() { return Component.translatable("jei.bioforge.category.centrifuge"); }

    @Override
    public int getWidth() { return GUI_W; }

    @Override
    public int getHeight() { return GUI_H; }

    @Override
    public IDrawable getIcon() { return icon; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CentrifugeRecipeWrapper recipe, IFocusGroup focuses) {
        List<ItemStack> inputs = recipe.getInputs();
        List<ItemStack> outputs = recipe.getOutputs();

        if (!inputs.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, SLOT_X[4] + 1, SLOT_Y[4] + 1)
                    .addItemStacks(inputs);
        }
        if (!outputs.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, SLOT_X[3] + 1, SLOT_Y[3] + 1)
                    .addItemStacks(outputs);
        }
    }

    @Override
    public void draw(CentrifugeRecipeWrapper recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);
        progressSpinner.draw(guiGraphics, 73, 44);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, CentrifugeRecipeWrapper recipe,
                           IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        if (mouseX >= 73 && mouseX <= 105 && mouseY >= 44 && mouseY <= 76) {
            tooltip.add(Component.translatable("jei.bioforge.centrifuge.processing_time",
                    recipe.getProcessingTime()));
            if (recipe.isCopyBloodData()) {
                tooltip.add(Component.translatable("jei.bioforge.centrifuge.transfers_blood"));
            }
            if (recipe.isCopyInfection()) {
                tooltip.add(Component.translatable("jei.bioforge.centrifuge.transfers_infection"));
            }
        }
    }
}
