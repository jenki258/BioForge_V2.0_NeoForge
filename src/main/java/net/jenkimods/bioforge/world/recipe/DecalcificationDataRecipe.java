package net.jenkimods.bioforge.world.recipe;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.MapCodec;
import net.jenkimods.bioforge.world.decalcification.DecalcificationRecipe;
import net.jenkimods.bioforge.world.decalcification.DecalcificationRecipeManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.stream.StreamSupport;

public record DecalcificationDataRecipe(JsonObject source, DecalcificationRecipe recipe)
        implements Recipe<RecipeInput> {
    private static final Gson GSON = new Gson();

    public static DecalcificationDataRecipe fromJson(JsonObject json) {
        JsonArray keysJson = GsonHelper.getAsJsonArray(json, "copy_nbt_keys", new JsonArray());
        List<String> keys = StreamSupport.stream(keysJson.spliterator(), false)
                .map(JsonElement::getAsString).toList();
        return new DecalcificationDataRecipe(json.deepCopy(), new DecalcificationRecipe(
                GsonHelper.getAsString(json, "input"),
                GsonHelper.getAsString(json, "output"),
                GsonHelper.getAsBoolean(json, "copy_blood_data", true),
                GsonHelper.getAsBoolean(json, "copy_nbt", false), keys));
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return input.size() > 0 && matches(recipe.input(), input.getItem(0));
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
        Item item = DecalcificationRecipeManager.INSTANCE.resolveOutput(
                recipe, RandomSource.create(0L));
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.create();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BioForgeRecipeRegistration.DECALCIFICATION_SERIALIZER;
    }

    @Override
    public RecipeType<?> getType() {
        return BioForgeRecipeRegistration.DECALCIFICATION_TYPE;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    private static boolean matches(String value, ItemStack stack) {
        ResourceLocation id = ResourceLocation.tryParse(value.startsWith("#")
                ? value.substring(1) : value);
        if (id == null) return false;
        return value.startsWith("#")
                ? stack.is(ItemTags.create(id))
                : stack.is(BuiltInRegistries.ITEM.get(id));
    }

    public static final class Serializer implements RecipeSerializer<DecalcificationDataRecipe> {
        private static final MapCodec<DecalcificationDataRecipe> CODEC =
                RecipeJsonCodec.create(DecalcificationDataRecipe::fromJson,
                        DecalcificationDataRecipe::source);
        private static final StreamCodec<RegistryFriendlyByteBuf, DecalcificationDataRecipe> STREAM =
                StreamCodec.of((buffer, recipe) -> buffer.writeUtf(GSON.toJson(recipe.source())),
                        buffer -> fromJson(GSON.fromJson(buffer.readUtf(), JsonObject.class)));

        @Override
        public MapCodec<DecalcificationDataRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, DecalcificationDataRecipe> streamCodec() {
            return STREAM;
        }
    }
}
