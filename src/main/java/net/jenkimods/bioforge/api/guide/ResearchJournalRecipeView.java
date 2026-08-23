package net.jenkimods.bioforge.api.guide;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

public record ResearchJournalRecipeView(
        ResourceLocation id,
        Component station,
        int width,
        int height,
        List<List<ItemStack>> ingredients,
        List<ItemStack> results
) {
    public ResearchJournalRecipeView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(station, "station");
        width = Math.max(1, Math.min(3, width));
        height = Math.max(1, Math.min(3, height));
        ingredients = ingredients.stream()
                .map(choices -> choices.stream().map(ItemStack::copy).toList())
                .toList();
        results = results.stream().filter(stack -> !stack.isEmpty())
                .map(ItemStack::copy).toList();
    }
}
