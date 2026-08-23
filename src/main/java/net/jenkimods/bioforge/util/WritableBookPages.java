package net.jenkimods.bioforge.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;

import java.util.ArrayList;
import java.util.List;

public final class WritableBookPages {
    private WritableBookPages() {}

    public static void append(ItemStack book, String page) {
        appendAll(book, List.of(page));
    }

    public static void appendAll(ItemStack book, List<String> additions) {
        if (book == null || !book.is(Items.WRITABLE_BOOK)
                || additions == null || additions.isEmpty()) return;

        WritableBookContent content = book.getOrDefault(
                DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY);
        List<Filterable<String>> pages = new ArrayList<>(content.pages());
        for (String addition : additions) {
            if (pages.size() >= WritableBookContent.MAX_PAGES) break;
            String page = addition == null ? "" : addition;
            if (page.length() > WritableBookContent.PAGE_EDIT_LENGTH) {
                page = page.substring(0, WritableBookContent.PAGE_EDIT_LENGTH);
            }
            pages.add(Filterable.passThrough(page));
        }
        book.set(DataComponents.WRITABLE_BOOK_CONTENT,
                content.withReplacedPages(List.copyOf(pages)));
    }
}
