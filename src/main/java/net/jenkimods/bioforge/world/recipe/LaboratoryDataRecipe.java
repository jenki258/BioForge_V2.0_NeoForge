package net.jenkimods.bioforge.world.recipe;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.serialization.MapCodec;
import net.jenkimods.bioforge.world.laboratory.LaboratoryProcessRecipe;
import net.jenkimods.bioforge.world.laboratory.LaboratoryProcessRecipeManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public record LaboratoryDataRecipe(JsonObject source, LaboratoryProcessRecipe recipe)
        implements Recipe<RecipeInput> {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation DYNAMIC_ID =
            ResourceLocation.tryBuild("kubejs", "runtime");

    public static LaboratoryDataRecipe fromJson(JsonObject json) {
        return new LaboratoryDataRecipe(json.deepCopy(),
                LaboratoryProcessRecipeManager.parseRecipe(DYNAMIC_ID, json));
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
        return recipe.result().copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= recipe.ingredients().size();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return recipe.result().copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.addAll(recipe.ingredients());
        return ingredients;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BioForgeRecipeRegistration.LABORATORY_SERIALIZER;
    }

    @Override
    public RecipeType<?> getType() {
        return BioForgeRecipeRegistration.LABORATORY_TYPE;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public static final class Serializer implements RecipeSerializer<LaboratoryDataRecipe> {
        private static final MapCodec<LaboratoryDataRecipe> CODEC =
                RecipeJsonCodec.create(LaboratoryDataRecipe::fromJson,
                        LaboratoryDataRecipe::source);
        private static final StreamCodec<RegistryFriendlyByteBuf, LaboratoryDataRecipe> STREAM =
                StreamCodec.of((buffer, recipe) -> buffer.writeUtf(GSON.toJson(recipe.source())),
                        buffer -> fromJson(GSON.fromJson(buffer.readUtf(), JsonObject.class)));

        @Override
        public MapCodec<LaboratoryDataRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, LaboratoryDataRecipe> streamCodec() {
            return STREAM;
        }
    }
}
