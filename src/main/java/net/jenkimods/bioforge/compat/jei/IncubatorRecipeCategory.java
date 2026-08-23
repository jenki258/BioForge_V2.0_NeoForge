package net.jenkimods.bioforge.compat.jei;

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

import java.util.Objects;

public final class IncubatorRecipeCategory implements IRecipeCategory<IncubatorRecipeWrapper> {

    public static final RecipeType<IncubatorRecipeWrapper> RECIPE_TYPE =
            RecipeType.create(BioForge.MODID, "incubator", IncubatorRecipeWrapper.class);

    private static final ResourceLocation GUI_TEXTURE =
            Objects.requireNonNull(ResourceLocation.tryBuild(
                    BioForge.MODID, "textures/gui/laboratory/incubator.png"));
    private static final int WIDTH = 176;
    private static final int HEIGHT = 106;
    private static final int PRIMARY_X = 80;
    private static final int PRIMARY_Y = 7;
    private static final int[] BATCH_X = {57, 80, 103};
    private static final int SECONDARY_Y = 52;
    private static final int OUTPUT_Y = 88;

    private final IDrawable background;
    private final IDrawable machine;
    private final IDrawable outputSlot;
    private final IDrawable icon;
    private final IDrawableAnimated progress;

    public IncubatorRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.machine = guiHelper.createDrawable(GUI_TEXTURE, 0, 0, WIDTH, 82);
        this.outputSlot = guiHelper.createDrawable(GUI_TEXTURE, 56, 51, 18, 18);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(BioForge.INCUBATOR.get()));
        this.progress = guiHelper.drawableBuilder(GUI_TEXTURE, 176, 0, 31, 22)
                .buildAnimated(100, IDrawableAnimated.StartDirection.TOP, false);
    }

    @Override
    public RecipeType<IncubatorRecipeWrapper> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.bioforge.category.incubator");
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
    public void setRecipe(IRecipeLayoutBuilder builder, IncubatorRecipeWrapper recipe,
                          IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, PRIMARY_X, PRIMARY_Y)
                .addItemStacks(recipe.getPrimaryInputs());

        for (int index = 0; index < BATCH_X.length; index++) {
            RecipeIngredientRole role = index == 0
                    ? RecipeIngredientRole.INPUT
                    : RecipeIngredientRole.RENDER_ONLY;
            builder.addSlot(role, BATCH_X[index], SECONDARY_Y)
                    .addItemStacks(recipe.getSecondaryInputs());
        }

        for (int index = 0; index < BATCH_X.length; index++) {
            RecipeIngredientRole role = index == 0
                    ? RecipeIngredientRole.OUTPUT
                    : RecipeIngredientRole.RENDER_ONLY;
            builder.addSlot(role, BATCH_X[index], OUTPUT_Y)
                    .addItemStacks(recipe.getOutputs());
        }
    }

    @Override
    public void draw(IncubatorRecipeWrapper recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        machine.draw(guiGraphics, 0, 0);
        progress.draw(guiGraphics, 72, 27);

        for (int x : BATCH_X) {
            outputSlot.draw(guiGraphics, x - 1, OUTPUT_Y - 1);
            int center = x + 8;
            guiGraphics.fill(center, 70, center + 1, 84, 0xFF55DDE5);
            guiGraphics.fill(center - 2, 81, center, 83, 0xFF55DDE5);
            guiGraphics.fill(center + 1, 81, center + 3, 83, 0xFF55DDE5);
        }
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, IncubatorRecipeWrapper recipe,
                           IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        if (mouseX >= 72 && mouseX < 103 && mouseY >= 27 && mouseY < 49) {
            tooltip.add(Component.translatable(
                    "jei.bioforge.incubator.processing_time", recipe.getProcessingTime()
            ));
            tooltip.add(Component.translatable(
                    "jei.bioforge.incubator.operation." + recipe.getOperation().serializedName()
            ));
            tooltip.add(Component.translatable("jei.bioforge.incubator.parallel_slots"));
            if (recipe.getPrimaryItemCost() > 0) {
                String costMode = recipe.isPrimaryCostPerOutput() ? "per_output" : "per_batch";
                tooltip.add(Component.translatable(
                        "jei.bioforge.incubator.primary_cost." + costMode,
                        recipe.getPrimaryItemCost()
                ));
            }
            if (recipe.getCatalystChargeCost() > 0) {
                tooltip.add(Component.translatable(
                        "jei.bioforge.incubator.catalyst_cost",
                        recipe.getCatalystChargeCost()
                ));
            }
        }
    }
}
