package net.jenkimods.bioforge.item.guide;

import net.jenkimods.bioforge.api.guide.ResearchJournalPageView;
import net.jenkimods.bioforge.api.guide.ResearchJournalRecipeView;
import net.jenkimods.bioforge.client.ResearchJournalClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.jenkimods.bioforge.network.compat.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record ResearchJournalOpenPacket(List<ResearchJournalPageView> pages) {
    public ResearchJournalOpenPacket {
        pages = List.copyOf(pages);
    }

    public static void encode(ResearchJournalOpenPacket packet, RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.pages().size());
        for (ResearchJournalPageView page : packet.pages()) {
            buffer.writeResourceLocation(page.id());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, page.title());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, page.body());
            buffer.writeBoolean(page.unlocked());
            buffer.writeVarInt(page.recipes().size());
            for (ResearchJournalRecipeView recipe : page.recipes()) {
                buffer.writeResourceLocation(recipe.id());
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, recipe.station());
                buffer.writeVarInt(recipe.width());
                buffer.writeVarInt(recipe.height());
                buffer.writeVarInt(recipe.ingredients().size());
                for (List<net.minecraft.world.item.ItemStack> choices : recipe.ingredients()) {
                    buffer.writeVarInt(choices.size());
                    choices.forEach(stack -> ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack));
                }
                buffer.writeVarInt(recipe.results().size());
                recipe.results().forEach(stack -> ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack));
            }
        }
    }

    public static ResearchJournalOpenPacket decode(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<ResearchJournalPageView> pages = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            var id = buffer.readResourceLocation();
            var title = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
            var body = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
            boolean unlocked = buffer.readBoolean();
            int recipeCount = buffer.readVarInt();
            List<ResearchJournalRecipeView> recipes = new ArrayList<>(recipeCount);
            for (int recipeIndex = 0; recipeIndex < recipeCount; recipeIndex++) {
                var recipeId = buffer.readResourceLocation();
                var station = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
                int width = buffer.readVarInt();
                int height = buffer.readVarInt();
                int ingredientCount = buffer.readVarInt();
                List<List<net.minecraft.world.item.ItemStack>> ingredients =
                        new ArrayList<>(ingredientCount);
                for (int ingredientIndex = 0; ingredientIndex < ingredientCount;
                     ingredientIndex++) {
                    int choiceCount = buffer.readVarInt();
                    List<net.minecraft.world.item.ItemStack> choices =
                            new ArrayList<>(choiceCount);
                    for (int choice = 0; choice < choiceCount; choice++) {
                        choices.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
                    }
                    ingredients.add(choices);
                }
                int resultCount = buffer.readVarInt();
                List<net.minecraft.world.item.ItemStack> results =
                        new ArrayList<>(resultCount);
                for (int result = 0; result < resultCount; result++) {
                    results.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
                }
                recipes.add(new ResearchJournalRecipeView(recipeId, station,
                        width, height, ingredients, results));
            }
            pages.add(new ResearchJournalPageView(
                    id, title, body, unlocked, recipes));
        }
        return new ResearchJournalOpenPacket(pages);
    }

    public static void handle(ResearchJournalOpenPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientHandler.handle(packet));
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientHandler {
        private static void handle(ResearchJournalOpenPacket packet) {
            ResearchJournalClient.open(packet.pages());
        }
    }
}
