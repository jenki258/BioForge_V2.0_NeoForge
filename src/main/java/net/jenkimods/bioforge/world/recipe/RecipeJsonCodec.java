package net.jenkimods.bioforge.world.recipe;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

final class RecipeJsonCodec {
    private RecipeJsonCodec() {}

    static <T> MapCodec<T> create(Function<JsonObject, T> decoder,
                                  Function<T, JsonObject> encoder) {
        Codec<T> codec = Codec.PASSTHROUGH.xmap(
                dynamic -> decoder.apply(dynamic.convert(JsonOps.INSTANCE)
                        .getValue().getAsJsonObject()),
                value -> new Dynamic<>(JsonOps.INSTANCE, encoder.apply(value)));
        return MapCodec.assumeMapUnsafe(codec);
    }
}
