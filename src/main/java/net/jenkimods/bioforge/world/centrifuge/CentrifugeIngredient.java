package net.jenkimods.bioforge.world.centrifuge;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

public final class CentrifugeIngredient {

    private final Item item;
    private final TagKey<Item> tag;

    private CentrifugeIngredient(Item item, TagKey<Item> tag) {
        this.item = item;
        this.tag = tag;
    }

    public static CentrifugeIngredient ofItem(Item item) {
        return new CentrifugeIngredient(Objects.requireNonNull(item), null);
    }

    public static CentrifugeIngredient ofTag(TagKey<Item> tag) {
        return new CentrifugeIngredient(null, Objects.requireNonNull(tag));
    }

    public boolean isTag() {
        return tag != null;
    }

    public boolean test(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (tag != null) return stack.is(tag);
        return stack.is(item);
    }

    public Item resolveItem(RandomSource random) {
        if (item != null) return item;
        List<Item> items = StreamSupport.stream(
                        BuiltInRegistries.ITEM.getTagOrEmpty(tag).spliterator(), false)
                .map(Holder::value)
                .toList();
        if (items.isEmpty()) return null;
        return items.get(random.nextInt(items.size()));
    }

    public static CentrifugeIngredient parse(String value) {
        if (value.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(value.substring(1));
            if (tagId == null) throw new IllegalArgumentException("Invalid tag id: " + value);
            return ofTag(ItemTags.create(tagId));
        }
        ResourceLocation itemId = ResourceLocation.tryParse(value);
        if (itemId == null) throw new IllegalArgumentException("Invalid item id: " + value);
        Item resolved = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (resolved == null) throw new IllegalArgumentException("Unknown item: " + value);
        return ofItem(resolved);
    }

    @Override
    public String toString() {
        return tag != null ? "#" + tag.location()
                : BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
