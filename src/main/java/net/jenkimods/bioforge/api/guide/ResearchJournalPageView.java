package net.jenkimods.bioforge.api.guide;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.List;

public record ResearchJournalPageView(ResourceLocation id, Component title,
                                      Component body, boolean unlocked,
                                      List<ResearchJournalRecipeView> recipes) {
    public ResearchJournalPageView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        recipes = List.copyOf(recipes);
    }
}
