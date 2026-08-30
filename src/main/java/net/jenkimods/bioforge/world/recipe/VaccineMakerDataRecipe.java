package net.jenkimods.bioforge.world.recipe;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.serialization.MapCodec;
import net.jenkimods.bioforge.crispr.VaccineTargetCategory;
import net.jenkimods.bioforge.world.vaccine.VaccineMakerRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public record VaccineMakerDataRecipe(JsonObject source, VaccineMakerRecipe recipe)
        implements Recipe<RecipeInput> {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation DYNAMIC_ID =
            ResourceLocation.tryBuild("kubejs", "runtime");

    public static VaccineMakerDataRecipe fromJson(JsonObject json) {
        return new VaccineMakerDataRecipe(json.deepCopy(),
                VaccineMakerRecipe.fromJson(DYNAMIC_ID, json));
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
        return getResultItem(registries);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 3;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        Item result = recipe.fullResult();
        if (result == null) {
            for (VaccineTargetCategory category : VaccineTargetCategory.values()) {
                result = recipe.directedResult(category);
                if (result != null) break;
            }
        }
        return result == null ? ItemStack.EMPTY : new ItemStack(result);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        add(ingredients, recipe.sample());
        add(ingredients, recipe.carrier());
        add(ingredients, recipe.reagent());
        add(ingredients, recipe.report());
        add(ingredients, recipe.cartridge());
        add(ingredients, recipe.casModule());
        return ingredients;
    }

    private static void add(NonNullList<Ingredient> target, Ingredient ingredient) {
        if (ingredient != null && !ingredient.isEmpty()) target.add(ingredient);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BioForgeRecipeRegistration.VACCINE_MAKER_SERIALIZER;
    }

    @Override
    public RecipeType<?> getType() {
        return BioForgeRecipeRegistration.VACCINE_MAKER_TYPE;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public static final class Serializer implements RecipeSerializer<VaccineMakerDataRecipe> {
        private static final MapCodec<VaccineMakerDataRecipe> CODEC =
                RecipeJsonCodec.create(VaccineMakerDataRecipe::fromJson,
                        VaccineMakerDataRecipe::source);
        private static final StreamCodec<RegistryFriendlyByteBuf, VaccineMakerDataRecipe> STREAM =
                StreamCodec.of((buffer, recipe) -> buffer.writeUtf(GSON.toJson(recipe.source())),
                        buffer -> fromJson(GSON.fromJson(buffer.readUtf(), JsonObject.class)));

        @Override
        public MapCodec<VaccineMakerDataRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, VaccineMakerDataRecipe> streamCodec() {
            return STREAM;
        }
    }
}
