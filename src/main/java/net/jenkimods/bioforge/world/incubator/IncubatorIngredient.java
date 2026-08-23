package net.jenkimods.bioforge.world.incubator;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;




public final class IncubatorIngredient {

    private final Item item;
    private final TagKey<Item> tag;
    private final boolean any;

    private IncubatorIngredient(Item item, TagKey<Item> tag, boolean any) {
        this.item = item;
        this.tag = tag;
        this.any = any;
    }

    public static IncubatorIngredient ofItem(Item item) {
        return new IncubatorIngredient(Objects.requireNonNull(item), null, false);
    }

    public static IncubatorIngredient ofTag(TagKey<Item> tag) {
        return new IncubatorIngredient(null, Objects.requireNonNull(tag), false);
    }

    public static IncubatorIngredient any() {
        return new IncubatorIngredient(null, null, true);
    }

    public static IncubatorIngredient parse(String value) {
        if ("*".equals(value)) {
            return any();
        }
        if (value.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(value.substring(1));
            if (tagId == null) {
                throw new IllegalArgumentException("Invalid item tag id: " + value);
            }
            return ofTag(ItemTags.create(tagId));
        }

        ResourceLocation itemId = ResourceLocation.tryParse(value);
        if (itemId == null) {
            throw new IllegalArgumentException("Invalid item id: " + value);
        }
        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            throw new IllegalArgumentException("Unknown item: " + value);
        }
        Item resolved = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (resolved == null) {
            throw new IllegalArgumentException("Unknown item: " + value);
        }
        return ofItem(resolved);
    }

    public boolean test(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (any) {
            return true;
        }
        if (tag != null) {
            return stack.is(tag);
        }
        return stack.is(item);
    }

    public boolean isAny() {
        return any;
    }

    public boolean isTag() {
        return tag != null;
    }

    public boolean isDirectAir() {
        return item == Items.AIR;
    }

    public int specificity() {
        if (item != null) {
            return 2;
        }
        if (tag != null) {
            return 1;
        }
        return 0;
    }

    public List<Item> resolveItems() {
        if (item != null) {
            return item == Items.AIR ? List.of() : List.of(item);
        }
        if (tag != null) {
            return StreamSupport.stream(
                            BuiltInRegistries.ITEM.getTagOrEmpty(tag).spliterator(), false)
                    .map(Holder::value)
                    .filter(candidate -> candidate != Items.AIR)
                    .toList();
        }
        return List.of();
    }

    public Item resolveItem(RandomSource random) {
        List<Item> resolved = resolveItems();
        if (resolved.isEmpty()) {
            return null;
        }
        return resolved.get(random.nextInt(resolved.size()));
    }

    @Override
    public String toString() {
        if (any) {
            return "*";
        }
        if (tag != null) {
            return "#" + tag.location();
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return key == null ? "<unregistered>" : key.toString();
    }
}
