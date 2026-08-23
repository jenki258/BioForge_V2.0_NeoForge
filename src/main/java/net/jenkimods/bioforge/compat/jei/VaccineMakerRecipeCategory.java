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
import net.minecraft.world.item.ItemStack;

public final class VaccineMakerRecipeCategory
        implements IRecipeCategory<VaccineMakerRecipeWrapper> {
    public static final RecipeType<VaccineMakerRecipeWrapper> RECIPE_TYPE =
            RecipeType.create(BioForge.MODID, "vaccine_maker",
                    VaccineMakerRecipeWrapper.class);

    private final IDrawable background;
    private final IDrawable icon;

    public VaccineMakerRecipeCategory(IGuiHelper guiHelper) {
        background = guiHelper.createBlankDrawable(200, 92);
        icon = guiHelper.createDrawableItemStack(
                new ItemStack(BioForge.VACCINE_MAKER_ITEM.get()));
    }

    @Override
    public RecipeType<VaccineMakerRecipeWrapper> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.bioforge.category.vaccine_maker");
    }

    @Override
    public int getWidth() {
        return 200;
    }

    @Override
    public int getHeight() {
        return 92;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, VaccineMakerRecipeWrapper recipe,
                          IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 7, 39)
                .addItemStacks(recipe.sampleInputs());
        builder.addSlot(RecipeIngredientRole.INPUT, 31, 39)
                .addItemStacks(recipe.carrierInputs());
        builder.addSlot(RecipeIngredientRole.INPUT, 55, 39)
                .addItemStacks(recipe.reagentInputs());
        if (recipe.recipe().requiresReport()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 79, 39)
                    .addItemStacks(recipe.reportInputs());
        }
        if (recipe.recipe().requiresProgram()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 107, 16)
                    .addItemStacks(recipe.cartridgeInputs());
            builder.addSlot(RecipeIngredientRole.INPUT, 107, 62)
                    .addItemStacks(recipe.casInputs());
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 174, 39)
                .addItemStacks(recipe.outputs());
    }

    @Override
    public void draw(VaccineMakerRecipeWrapper recipe, IRecipeSlotsView slots,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        graphics.fill(0, 0, 200, 92, 0xFF0C222B);
        graphics.fill(3, 3, 197, 89, 0xFF102F38);
        for (int x : new int[]{6, 30, 54, 106, 173}) {
            int y = x == 106 ? 15 : 38;
            graphics.fill(x, y, x + 18, y + 18, 0xFF06151B);
            graphics.fill(x, y, x + 18, y + 1, 0xFF4EC9DA);
        }
        if (recipe.recipe().requiresReport()) {
            graphics.fill(78, 38, 96, 56, 0xFF06151B);
            graphics.fill(78, 38, 96, 39, 0xFFFFC95C);
        }
        if (recipe.recipe().requiresProgram()) {
            graphics.fill(106, 61, 124, 79, 0xFF06151B);
            graphics.fill(106, 61, 124, 62, 0xFF4EC9DA);
            graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
                    "15x", 128, 21, 0xFF7CEBF5, false);
        }
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
                Component.translatable("jei.bioforge.vaccine_maker.operation."
                        + recipe.recipe().operationId().getPath()),
                5, 5, 0xFF77EAF3, false);
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
                "→", 145, 43, 0xFF77EAF3, false);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, VaccineMakerRecipeWrapper recipe,
                           IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (mouseX >= 127 && mouseX <= 166 && mouseY >= 12 && mouseY <= 78) {
            tooltip.add(Component.translatable("jei.bioforge.vaccine_maker.time",
                    recipe.recipe().processingTime()));
            tooltip.add(Component.translatable("jei.bioforge.vaccine_maker.minimum_quality",
                    Math.round(recipe.recipe().minimumQuality() * 100.0f)));
            if (recipe.recipe().requiresProgram()) {
                tooltip.add(Component.translatable("jei.bioforge.vaccine_maker.program"));
            }
        }
    }
}
