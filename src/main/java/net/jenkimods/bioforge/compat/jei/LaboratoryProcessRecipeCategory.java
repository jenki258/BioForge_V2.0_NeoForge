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
import net.jenkimods.bioforge.world.laboratory.LaboratoryProcessRecipe;
import net.jenkimods.bioforge.world.laboratory.LaboratoryStation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class LaboratoryProcessRecipeCategory
        implements IRecipeCategory<LaboratoryProcessRecipe> {
    public static final RecipeType<LaboratoryProcessRecipe> BARREL_PRESS_TYPE =
            RecipeType.create(BioForge.MODID, "barrel_press",
                    LaboratoryProcessRecipe.class);
    public static final RecipeType<LaboratoryProcessRecipe> CHEMICAL_TYPE =
            RecipeType.create(BioForge.MODID, "chemical_synthesizer",
                    LaboratoryProcessRecipe.class);
    public static final RecipeType<LaboratoryProcessRecipe> PHARMA_TYPE =
            RecipeType.create(BioForge.MODID, "pharma_mixer",
                    LaboratoryProcessRecipe.class);
    public static final RecipeType<LaboratoryProcessRecipe> STERILIZATION_TYPE =
            RecipeType.create(BioForge.MODID, "sterilization_chamber",
                    LaboratoryProcessRecipe.class);

    private final LaboratoryStation station;
    private final IDrawable background;
    private final IDrawable icon;

    public LaboratoryProcessRecipeCategory(IGuiHelper guiHelper,
                                           LaboratoryStation station,
                                           ItemStack icon) {
        this.station = station;
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(BioForge.MODID,
                "textures/gui/laboratory/" + station.getSerializedName() + ".png");
        this.background = guiHelper.createDrawable(texture, 0, 0, 176, 72);
        this.icon = guiHelper.createDrawableItemStack(icon);
    }

    @Override
    public RecipeType<LaboratoryProcessRecipe> getRecipeType() {
        return type(station);
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.bioforge.category."
                + station.getSerializedName());
    }

    @Override
    public int getWidth() {
        return 176;
    }

    @Override
    public int getHeight() {
        return 72;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder,
                          LaboratoryProcessRecipe recipe, IFocusGroup focuses) {
        switch (station) {
            case BARREL_PRESS -> {
                for (int index = 0; index < recipe.ingredients().size(); index++) {
                    builder.addSlot(RecipeIngredientRole.INPUT, 26 + index * 18, 35)
                            .addIngredients(recipe.ingredients().get(index));
                }
                builder.addSlot(RecipeIngredientRole.OUTPUT, 125, 35)
                        .addItemStack(recipe.result());
            }
            case CHEMICAL_SYNTHESIZER -> {
                for (int index = 0; index < recipe.ingredients().size(); index++) {
                    builder.addSlot(RecipeIngredientRole.INPUT, 35 + index * 18, 35)
                            .addIngredients(recipe.ingredients().get(index));
                }
                builder.addSlot(RecipeIngredientRole.OUTPUT, 125, 35)
                        .addItemStack(recipe.result());
            }
            case PHARMA_MIXER -> {
                for (int index = 0; index < recipe.ingredients().size(); index++) {
                    builder.addSlot(RecipeIngredientRole.INPUT, 17 + index * 18, 35)
                            .addIngredients(recipe.ingredients().get(index));
                }
                builder.addSlot(RecipeIngredientRole.OUTPUT, 126, 26)
                        .addItemStack(recipe.result());
                if (!recipe.waste().isEmpty()) {
                    builder.addSlot(RecipeIngredientRole.OUTPUT, 126, 49)
                            .addItemStack(recipe.waste());
                }
            }
            case STERILIZATION_CHAMBER -> {
                builder.addSlot(RecipeIngredientRole.INPUT, 43, 25)
                        .addIngredients(recipe.ingredients().get(0));
                builder.addSlot(RecipeIngredientRole.OUTPUT, 97, 43)
                        .addItemStack(recipe.result());
            }
        }
    }

    @Override
    public void draw(LaboratoryProcessRecipe recipe, IRecipeSlotsView slots,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        background.draw(graphics, 0, 0);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, LaboratoryProcessRecipe recipe,
                           IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (mouseY < 56 || mouseX < 99 || mouseX > 132) return;
        if (station == LaboratoryStation.STERILIZATION_CHAMBER) {
            tooltip.add(Component.translatable(
                    "jei.bioforge.laboratory.sterilization.generic"));
        }
        tooltip.add(Component.translatable("jei.bioforge.laboratory.time",
                recipe.processingTime()));
    }

    public static RecipeType<LaboratoryProcessRecipe> type(LaboratoryStation station) {
        return switch (station) {
            case BARREL_PRESS -> BARREL_PRESS_TYPE;
            case CHEMICAL_SYNTHESIZER -> CHEMICAL_TYPE;
            case PHARMA_MIXER -> PHARMA_TYPE;
            case STERILIZATION_CHAMBER -> STERILIZATION_TYPE;
        };
    }
}
