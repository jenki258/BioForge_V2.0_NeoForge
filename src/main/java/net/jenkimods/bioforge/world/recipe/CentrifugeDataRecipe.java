package net.jenkimods.bioforge.world.recipe;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.MapCodec;
import net.jenkimods.bioforge.world.centrifuge.CentrifugeIngredient;
import net.jenkimods.bioforge.world.centrifuge.CentrifugeOutput;
import net.jenkimods.bioforge.world.centrifuge.CentrifugeRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public record CentrifugeDataRecipe(JsonObject source, CentrifugeRecipe recipe)
        implements Recipe<RecipeInput> {
    private static final Gson GSON = new Gson();

    public static CentrifugeDataRecipe fromJson(JsonObject json) {
        CentrifugeIngredient input = CentrifugeIngredient.parse(
                GsonHelper.getAsString(json, "input"));
        List<CentrifugeOutput> outputs = new ArrayList<>();
        CentrifugeIngredient output = null;
        if (json.has("outputs")) {
            JsonArray values = GsonHelper.getAsJsonArray(json, "outputs");
            for (JsonElement element : values) {
                JsonObject value = GsonHelper.convertToJsonObject(element, "centrifuge output");
                outputs.add(new CentrifugeOutput(CentrifugeIngredient.parse(
                        GsonHelper.getAsString(value, "item")),
                        GsonHelper.getAsInt(value, "weight", 1)));
            }
        } else if (json.has("output")) {
            output = CentrifugeIngredient.parse(GsonHelper.getAsString(json, "output"));
        } else {
            throw new JsonParseException("Recipe must have 'output' or 'outputs'");
        }
        JsonArray keysJson = GsonHelper.getAsJsonArray(json, "copy_nbt_keys", new JsonArray());
        List<String> keys = new ArrayList<>();
        keysJson.forEach(element -> keys.add(element.getAsString()));
        return new CentrifugeDataRecipe(json.deepCopy(), new CentrifugeRecipe(
                input, output, outputs,
                GsonHelper.getAsBoolean(json, "copy_blood_data", false),
                GsonHelper.getAsBoolean(json, "copy_nbt", false), keys,
                GsonHelper.getAsBoolean(json, "copy_infection", false),
                Math.max(1, GsonHelper.getAsInt(json, "processing_time", 100))));
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return input.size() > 0 && recipe.input().test(input.getItem(0));
    }

    @Override
    public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
        return getResultItem(registries);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        CentrifugeIngredient output = recipe.output();
        if (output == null && !recipe.outputs().isEmpty()) {
            output = recipe.outputs().get(0).ingredient();
        }
        Item item = output == null ? null : output.resolveItem(RandomSource.create(0L));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.create();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BioForgeRecipeRegistration.CENTRIFUGE_SERIALIZER;
    }

    @Override
    public RecipeType<?> getType() {
        return BioForgeRecipeRegistration.CENTRIFUGE_TYPE;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public static final class Serializer implements RecipeSerializer<CentrifugeDataRecipe> {
        private static final MapCodec<CentrifugeDataRecipe> CODEC =
                RecipeJsonCodec.create(CentrifugeDataRecipe::fromJson,
                        CentrifugeDataRecipe::source);
        private static final StreamCodec<RegistryFriendlyByteBuf, CentrifugeDataRecipe> STREAM =
                StreamCodec.of((buffer, recipe) -> buffer.writeUtf(GSON.toJson(recipe.source())),
                        buffer -> fromJson(GSON.fromJson(buffer.readUtf(), JsonObject.class)));

        @Override
        public MapCodec<CentrifugeDataRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CentrifugeDataRecipe> streamCodec() {
            return STREAM;
        }
    }
}
