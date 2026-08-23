package net.jenkimods.bioforge.compat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
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

import java.util.Objects;

public class DecalcificationRecipeCategory implements IRecipeCategory<DecalcificationRecipeWrapper> {

    public static final RecipeType<DecalcificationRecipeWrapper> RECIPE_TYPE =
            RecipeType.create(BioForge.MODID, "decalcification", DecalcificationRecipeWrapper.class);

    private static final ResourceLocation GUI_TEXTURE =
            Objects.requireNonNull(ResourceLocation.tryBuild(BioForge.MODID, "textures/gui/decalcification_jei.png"));

    private static final int WIDTH = 80;
    private static final int HEIGHT = 34;

    private static final int INPUT_X = 1;
    private static final int INPUT_Y = 9;
    private static final int OUTPUT_X = 60;
    private static final int OUTPUT_Y = 9;

    private static final int ARROW_X = 27;
    private static final int ARROW_Y = 12;
    private static final int ARROW_W = 22;
    private static final int ARROW_H = 10;

    private static final int SLOT_SIZE = 18;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable arrow;
    private final IDrawable inputSlot;
    private final IDrawable outputSlot;

    public DecalcificationRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(BioForge.DECALCIFICATION_FLUID.get()));
        this.arrow = guiHelper.drawableBuilder(GUI_TEXTURE, 0, 36, ARROW_W, ARROW_H).build();
        this.inputSlot = guiHelper.drawableBuilder(GUI_TEXTURE, 0, 0, SLOT_SIZE, SLOT_SIZE).build();
        this.outputSlot = guiHelper.drawableBuilder(GUI_TEXTURE, 18, 0, SLOT_SIZE, SLOT_SIZE).build();
    }

    @Override
    public RecipeType<DecalcificationRecipeWrapper> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.bioforge.category.decalcification");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, DecalcificationRecipeWrapper recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X + 1, INPUT_Y + 1)
                .addItemStacks(recipe.getInputs());
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X + 1, OUTPUT_Y + 1)
                .addItemStacks(recipe.getOutputs());
    }

    @Override
    public void draw(DecalcificationRecipeWrapper recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        inputSlot.draw(guiGraphics, INPUT_X, INPUT_Y);
        outputSlot.draw(guiGraphics, OUTPUT_X, OUTPUT_Y);
        arrow.draw(guiGraphics, ARROW_X, ARROW_Y);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, DecalcificationRecipeWrapper recipe,
                           IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        if (mouseX >= 20 && mouseX <= 55 && mouseY >= 12 && mouseY <= 23) {
            tooltip.add(Component.translatable("jei.bioforge.decalcification.hand_crafted"));
        }
    }
}
