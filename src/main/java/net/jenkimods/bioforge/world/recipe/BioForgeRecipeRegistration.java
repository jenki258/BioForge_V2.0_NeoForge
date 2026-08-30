package net.jenkimods.bioforge.world.recipe;

import net.jenkimods.bioforge.BioForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Objects;

@EventBusSubscriber(modid = BioForge.MODID)
public final class BioForgeRecipeRegistration {
    public static final ResourceLocation CENTRIFUGE_ID = id("centrifuge");
    public static final ResourceLocation DECALCIFICATION_ID = id("decalcification");
    public static final ResourceLocation LABORATORY_ID = id("laboratory_processing");
    public static final ResourceLocation VACCINE_MAKER_ID = id("vaccine_maker");

    public static final RecipeType<CentrifugeDataRecipe> CENTRIFUGE_TYPE = type(CENTRIFUGE_ID);
    public static final RecipeType<DecalcificationDataRecipe> DECALCIFICATION_TYPE =
            type(DECALCIFICATION_ID);
    public static final RecipeType<LaboratoryDataRecipe> LABORATORY_TYPE = type(LABORATORY_ID);
    public static final RecipeType<VaccineMakerDataRecipe> VACCINE_MAKER_TYPE =
            type(VACCINE_MAKER_ID);

    public static final RecipeSerializer<CentrifugeDataRecipe> CENTRIFUGE_SERIALIZER =
            new CentrifugeDataRecipe.Serializer();
    public static final RecipeSerializer<DecalcificationDataRecipe> DECALCIFICATION_SERIALIZER =
            new DecalcificationDataRecipe.Serializer();
    public static final RecipeSerializer<LaboratoryDataRecipe> LABORATORY_SERIALIZER =
            new LaboratoryDataRecipe.Serializer();
    public static final RecipeSerializer<VaccineMakerDataRecipe> VACCINE_MAKER_SERIALIZER =
            new VaccineMakerDataRecipe.Serializer();

    private BioForgeRecipeRegistration() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.RECIPE_TYPE, helper -> {
            helper.register(CENTRIFUGE_ID, CENTRIFUGE_TYPE);
            helper.register(DECALCIFICATION_ID, DECALCIFICATION_TYPE);
            helper.register(LABORATORY_ID, LABORATORY_TYPE);
            helper.register(VACCINE_MAKER_ID, VACCINE_MAKER_TYPE);
        });
        event.register(Registries.RECIPE_SERIALIZER, helper -> {
            helper.register(CENTRIFUGE_ID, CENTRIFUGE_SERIALIZER);
            helper.register(DECALCIFICATION_ID, DECALCIFICATION_SERIALIZER);
            helper.register(LABORATORY_ID, LABORATORY_SERIALIZER);
            helper.register(VACCINE_MAKER_ID, VACCINE_MAKER_SERIALIZER);
        });
    }

    private static ResourceLocation id(String path) {
        return Objects.requireNonNull(ResourceLocation.tryBuild(BioForge.MODID, path));
    }

    private static <T extends Recipe<?>> RecipeType<T> type(ResourceLocation id) {
        return new RecipeType<>() {
            @Override
            public String toString() {
                return id.toString();
            }
        };
    }
}
