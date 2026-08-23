package net.jenkimods.bioforge.world.incubator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class IncubatorRecipeSerializer implements RecipeSerializer<IncubatorRecipe> {
    private static final MapCodec<IncubatorRecipe> CODEC = Serialized.CODEC.xmap(
            Serialized::toRecipe, Serialized::fromRecipe);
    private static final StreamCodec<RegistryFriendlyByteBuf, IncubatorRecipe> STREAM_CODEC =
            StreamCodec.of(IncubatorRecipeSerializer::encode,
                    IncubatorRecipeSerializer::decode);

    @Override
    public MapCodec<IncubatorRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, IncubatorRecipe> streamCodec() {
        return STREAM_CODEC;
    }

    private static IncubatorRecipe decode(RegistryFriendlyByteBuf buffer) {
        IncubatorIngredient primary = IncubatorIngredient.parse(buffer.readUtf());
        IncubatorIngredient secondary = IncubatorIngredient.parse(buffer.readUtf());
        IncubatorIngredient output = IncubatorIngredient.parse(buffer.readUtf());
        int outputCount = buffer.readVarInt();
        IncubatorOperation operation = IncubatorOperation.parse(buffer.readUtf());
        int processingTime = buffer.readVarInt();
        int primaryItemCost = buffer.readVarInt();
        boolean primaryCostPerOutput = buffer.readBoolean();
        int catalystChargeCost = buffer.readVarInt();
        IncubatorIngredient jeiInput = buffer.readBoolean()
                ? IncubatorIngredient.parse(buffer.readUtf()) : null;
        return validated(primary, secondary, output, outputCount, operation,
                processingTime, primaryItemCost, primaryCostPerOutput,
                catalystChargeCost, jeiInput);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, IncubatorRecipe recipe) {
        buffer.writeUtf(recipe.primaryInput().toString());
        buffer.writeUtf(recipe.secondaryInput().toString());
        buffer.writeUtf(recipe.output().toString());
        buffer.writeVarInt(recipe.outputCount());
        buffer.writeUtf(recipe.operation().serializedName());
        buffer.writeVarInt(recipe.processingTime());
        buffer.writeVarInt(recipe.primaryItemCost());
        buffer.writeBoolean(recipe.primaryCostPerOutput());
        buffer.writeVarInt(recipe.catalystChargeCost());
        buffer.writeBoolean(recipe.jeiInput() != null);
        if (recipe.jeiInput() != null) buffer.writeUtf(recipe.jeiInput().toString());
    }

    private static IncubatorRecipe validated(IncubatorIngredient primary,
                                             IncubatorIngredient secondary,
                                             IncubatorIngredient output,
                                             int outputCount,
                                             IncubatorOperation operation,
                                             int processingTime,
                                             int primaryItemCost,
                                             boolean primaryCostPerOutput,
                                             int catalystChargeCost,
                                             @Nullable IncubatorIngredient jeiInput) {
        if (output.isAny() || output.isDirectAir()) {
            throw new IllegalArgumentException(
                    "'output' must be an item or tag containing non-air items");
        }
        if (outputCount < 1 || outputCount > 64) {
            throw new IllegalArgumentException("'output_count' must be between 1 and 64");
        }
        if (processingTime < 1) {
            throw new IllegalArgumentException("'processing_time' must be at least 1");
        }
        if (primaryItemCost < 0 || catalystChargeCost < 0) {
            throw new IllegalArgumentException("Incubator costs cannot be negative");
        }
        if (operation == IncubatorOperation.GENERATE_STRAIN && catalystChargeCost < 1) {
            throw new IllegalArgumentException(
                    "'generate_strain' requires at least one catalyst charge");
        }
        if (operation != IncubatorOperation.GENERATE_STRAIN && catalystChargeCost != 0) {
            throw new IllegalArgumentException(
                    "'catalyst_charge_cost' can only be used by 'generate_strain'");
        }
        if (jeiInput != null && jeiInput.isAny()) {
            throw new IllegalArgumentException("'jei_input' cannot be the wildcard '*'");
        }
        if (jeiInput == null && primary.isAny()) {
            throw new IllegalArgumentException(
                    "Wildcard 'primary_input' requires a concrete 'jei_input'");
        }
        return new IncubatorRecipe(primary, secondary, output, outputCount,
                operation, processingTime, primaryItemCost, primaryCostPerOutput,
                catalystChargeCost, jeiInput);
    }

    private record Serialized(String primaryInput, String secondaryInput,
                              String output, int outputCount, String operation,
                              int processingTime, Optional<Integer> primaryItemCost,
                              Optional<String> primaryCostMode,
                              Optional<Integer> catalystChargeCost,
                              Optional<String> jeiInput) {
        private static final MapCodec<Serialized> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Codec.STRING.fieldOf("primary_input").forGetter(Serialized::primaryInput),
                        Codec.STRING.fieldOf("secondary_input").forGetter(Serialized::secondaryInput),
                        Codec.STRING.fieldOf("output").forGetter(Serialized::output),
                        Codec.INT.optionalFieldOf("output_count", 1).forGetter(Serialized::outputCount),
                        Codec.STRING.fieldOf("operation").forGetter(Serialized::operation),
                        Codec.INT.optionalFieldOf("processing_time", 200).forGetter(Serialized::processingTime),
                        Codec.INT.optionalFieldOf("primary_item_cost").forGetter(Serialized::primaryItemCost),
                        Codec.STRING.optionalFieldOf("primary_cost_mode").forGetter(Serialized::primaryCostMode),
                        Codec.INT.optionalFieldOf("catalyst_charge_cost").forGetter(Serialized::catalystChargeCost),
                        Codec.STRING.optionalFieldOf("jei_input").forGetter(Serialized::jeiInput)
                ).apply(instance, Serialized::new));

        private IncubatorRecipe toRecipe() {
            IncubatorOperation parsedOperation = IncubatorOperation.parse(operation);
            int primaryCost = primaryItemCost.orElse(
                    parsedOperation == IncubatorOperation.GENERATE_STRAIN ? 0 : 1);
            String mode = primaryCostMode.orElse(
                    parsedOperation == IncubatorOperation.CRAFT ? "per_output" : "per_batch");
            boolean perOutput = switch (mode) {
                case "per_output" -> true;
                case "per_batch" -> false;
                default -> throw new IllegalArgumentException(
                        "'primary_cost_mode' must be 'per_output' or 'per_batch'");
            };
            int chargeCost = catalystChargeCost.orElse(
                    parsedOperation == IncubatorOperation.GENERATE_STRAIN ? 1 : 0);
            return validated(IncubatorIngredient.parse(primaryInput),
                    IncubatorIngredient.parse(secondaryInput),
                    IncubatorIngredient.parse(output), outputCount, parsedOperation,
                    processingTime, primaryCost, perOutput, chargeCost,
                    jeiInput.map(IncubatorIngredient::parse).orElse(null));
        }

        private static Serialized fromRecipe(IncubatorRecipe recipe) {
            return new Serialized(recipe.primaryInput().toString(),
                    recipe.secondaryInput().toString(), recipe.output().toString(),
                    recipe.outputCount(), recipe.operation().serializedName(),
                    recipe.processingTime(), Optional.of(recipe.primaryItemCost()),
                    Optional.of(recipe.primaryCostPerOutput()
                            ? "per_output" : "per_batch"),
                    Optional.of(recipe.catalystChargeCost()),
                    Optional.ofNullable(recipe.jeiInput()).map(Object::toString));
        }
    }
}
