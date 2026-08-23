package net.jenkimods.bioforge.vaccine;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.jenkimods.bioforge.util.WritableBookPages;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;





public final class VaccineResearchNotes {
    public static final String ROOT_TAG = "BioForgeVaccineResearch";
    private static final String CHANNEL = "crispr_notes";

    public record Data(float quality, String guideOne, String guideTwo, String guideThree,
                       String sampleFingerprint, String recipeId, long recordedAt) {
        public String sequence() {
            return guideOne + guideTwo + guideThree;
        }
    }

    private VaccineResearchNotes() {}

    public static ItemStack record(ItemStack medium, float quality, String sequence,
                                   String samplePayload, ResourceLocation recipeId,
                                   long gameTime) {
        if (medium.is(Items.PAPER)) {
            ItemStack report = new ItemStack(BioForge.CRISPR_NOTES.get());
            write(report, quality, sequence, samplePayload, recipeId, gameTime);
            return report;
        }
        if (medium.is(Items.WRITABLE_BOOK)) {
            appendBookPage(medium, quality, sequence, samplePayload, recipeId);
            return medium;
        }
        if (medium.is(BioForge.CRISPR_NOTES.get())) {
            write(medium, quality, sequence, samplePayload, recipeId, gameTime);
            return medium;
        }
        return ItemStack.EMPTY;
    }

    public static boolean canRecord(ItemStack stack) {
        return stack.is(Items.PAPER) || stack.is(Items.WRITABLE_BOOK)
                || (stack.is(BioForge.CRISPR_NOTES.get()) && read(stack) == null);
    }

    public static boolean isTemplate(ItemStack stack) {
        return stack.is(BioForge.CRISPR_NOTES.get()) && read(stack) != null;
    }

    public static boolean matchesSample(ItemStack stack, String samplePayload) {
        Data data = read(stack);
        return data != null && data.sampleFingerprint().equals(
                sampleFingerprint(samplePayload));
    }

    @Nullable
    public static Data read(ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || !stack.is(BioForge.CRISPR_NOTES.get()) || !net.jenkimods.bioforge.util.StackData.has(stack)) return null;
        CompoundTag root = net.jenkimods.bioforge.util.StackData.copy(stack);
        CompoundTag tag = NbtObfuscator.readCompound(root, CHANNEL);

        if (tag == null && root.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            tag = root.getCompound(ROOT_TAG);
        }
        if (tag == null) return null;
        return new Data(tag.getFloat("Quality"), tag.getString("Guide1"),
                tag.getString("Guide2"), tag.getString("Guide3"),
                tag.getString("SampleFingerprint"), tag.getString("Recipe"),
                tag.getLong("RecordedAt"));
    }

    private static void write(ItemStack stack, float quality, String sequence,
                              String samplePayload, ResourceLocation recipeId,
                              long gameTime) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Quality", quality);
        tag.putString("Guide1", guide(sequence, 0));
        tag.putString("Guide2", guide(sequence, 1));
        tag.putString("Guide3", guide(sequence, 2));
        tag.putString("SampleFingerprint", sampleFingerprint(samplePayload));
        tag.putString("Recipe", recipeId.toString());
        tag.putLong("RecordedAt", Math.max(0L, gameTime));
        net.jenkimods.bioforge.util.StackData.update(stack,
                root -> root.remove(ROOT_TAG));
        NbtObfuscator.writeCompound(stack, CHANNEL, tag);
    }

    private static void appendBookPage(ItemStack book, float quality, String sequence,
                                       String samplePayload, ResourceLocation recipeId) {
        String page = line("book.bioforge.crispr.header")
                + "\n" + line("book.bioforge.crispr.batch", sampleFingerprint(samplePayload))
                + "\n" + line("book.bioforge.crispr.assay_required")
                + "\n" + line("book.bioforge.crispr.guide", 1, guide(sequence, 0))
                + "\n" + line("book.bioforge.crispr.guide", 2, guide(sequence, 1))
                + "\n" + line("book.bioforge.crispr.guide", 3, guide(sequence, 2))
                + "\n" + line("book.bioforge.crispr.recipe", recipeId);
        WritableBookPages.append(book, page);
    }

    public static void appendToBook(ItemStack book, Data data) {
        if (!book.is(Items.WRITABLE_BOOK) || data == null) return;
        String page = line("book.bioforge.crispr.header")
                + "\n" + line("book.bioforge.crispr.batch", data.sampleFingerprint())
                + "\n" + line("book.bioforge.crispr.assay_required")
                + "\n" + line("book.bioforge.crispr.guide", 1, data.guideOne())
                + "\n" + line("book.bioforge.crispr.guide", 2, data.guideTwo())
                + "\n" + line("book.bioforge.crispr.guide", 3, data.guideThree())
                + "\n" + line("book.bioforge.crispr.recipe", data.recipeId());
        WritableBookPages.append(book, page);
    }

    private static String guide(String sequence, int index) {
        int start = index * 20;
        if (sequence == null || sequence.length() <= start) return "";
        return sequence.substring(start, Math.min(sequence.length(), start + 20));
    }

    public static String sampleFingerprint(String payload) {
        return StrainFingerprint.ofPayload(payload);
    }

    private static String line(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
