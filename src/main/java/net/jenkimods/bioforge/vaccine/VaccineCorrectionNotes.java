package net.jenkimods.bioforge.vaccine;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.jenkimods.bioforge.util.WritableBookPages;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class VaccineCorrectionNotes {
    private static final String CHANNEL = "vaccine_correction_notes";
    private static final int VERSION = 1;

    public record Entry(String family, String target, int state, int states) {
        public Entry {
            family = family == null ? "" : family;
            target = target == null ? "" : target;
            states = Math.max(1, states);
            state = Math.max(0, Math.min(states - 1, state));
        }
    }

    public record Data(String sampleFingerprint, String profileId,
                       List<Entry> entries) {
        public Data {
            sampleFingerprint = sampleFingerprint == null ? "" : sampleFingerprint;
            profileId = profileId == null ? "" : profileId;
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }

    private VaccineCorrectionNotes() {}

    public static boolean canRecord(ItemStack stack) {
        return stack.is(Items.PAPER) || stack.is(Items.WRITABLE_BOOK)
                || stack.is(BioForge.CRISPR_NOTES.get());
    }

    public static boolean isTemplate(ItemStack stack) {
        return read(stack) != null;
    }

    public static boolean matchesSample(ItemStack stack, String samplePayload) {
        Data data = read(stack);
        return data != null && data.sampleFingerprint().equals(
                StrainFingerprint.ofPayload(samplePayload));
    }

    public static ItemStack record(ItemStack medium, String samplePayload,
                                   ResourceLocation profileId,
                                   List<VaccineCorrectionState.Target> targets) {
        Data data = capture(samplePayload, profileId, targets);
        if (medium.is(Items.PAPER)) {
            ItemStack notes = new ItemStack(BioForge.CRISPR_NOTES.get());
            write(notes, data);
            return notes;
        }
        if (medium.is(Items.WRITABLE_BOOK)) {
            appendToBook(medium, data);
            return medium;
        }
        if (medium.is(BioForge.CRISPR_NOTES.get())) {
            write(medium, data);
            return medium;
        }
        return ItemStack.EMPTY;
    }

    private static Data capture(String samplePayload, ResourceLocation profileId,
                                List<VaccineCorrectionState.Target> targets) {
        List<Entry> entries = targets.stream()
                .map(target -> new Entry(target.family().serializedName(), target.id(),
                        target.selectedState(), target.states()))
                .toList();
        return new Data(StrainFingerprint.ofPayload(samplePayload),
                profileId == null ? "" : profileId.toString(), entries);
    }

    @Nullable
    public static Data read(ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || !stack.is(BioForge.CRISPR_NOTES.get()) || !net.jenkimods.bioforge.util.StackData.has(stack)) return null;
        CompoundTag tag = NbtObfuscator.readCompound(stack, CHANNEL);
        if (tag == null || tag.getInt("Version") != VERSION) return null;
        List<Entry> entries = new ArrayList<>();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            String family = entry.getString("Family");
            String target = entry.getString("Target");
            if (family.isBlank() || target.isBlank()) continue;
            entries.add(new Entry(family, target, entry.getInt("State"),
                    entry.getInt("States")));
        }
        if (entries.isEmpty()) return null;
        return new Data(tag.getString("SampleFingerprint"),
                tag.getString("Profile"), entries);
    }

    private static void write(ItemStack stack, Data data) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        tag.putString("SampleFingerprint", data.sampleFingerprint());
        tag.putString("Profile", data.profileId());
        ListTag list = new ListTag();
        for (Entry source : data.entries()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Family", source.family());
            entry.putString("Target", source.target());
            entry.putInt("State", source.state());
            entry.putInt("States", source.states());
            list.add(entry);
        }
        tag.put("Entries", list);
        NbtObfuscator.writeCompound(stack, CHANNEL, tag);
    }

    public static void appendToBook(ItemStack book, Data data) {
        if (!book.is(Items.WRITABLE_BOOK) || data == null) return;
        List<String> pages = new ArrayList<>();
        int pageNumber = 1;
        for (int start = 0; start < data.entries().size(); start += 7) {
            StringBuilder page = new StringBuilder(Component.translatable(
                    "book.bioforge.correction.header", pageNumber++).getString())
                    .append('\n')
                    .append(Component.translatable("book.bioforge.crispr.batch",
                            data.sampleFingerprint()).getString()).append('\n');
            int end = Math.min(data.entries().size(), start + 7);
            for (int index = start; index < end; index++) {
                Entry entry = data.entries().get(index);
                page.append(Component.translatable("book.bioforge.correction.entry",
                        entry.family(), entry.target(), entry.state() + 1,
                        entry.states()).getString()).append('\n');
            }
            pages.add(page.toString());
        }
        WritableBookPages.appendAll(book, pages);
    }
}
