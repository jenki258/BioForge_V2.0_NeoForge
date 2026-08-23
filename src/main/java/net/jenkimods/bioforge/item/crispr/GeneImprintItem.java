package net.jenkimods.bioforge.item.crispr;

import net.jenkimods.bioforge.crispr.VaccineTargetCategory;
import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.jenkimods.bioforge.crispr.CrisprDisplayNames;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;








public class GeneImprintItem extends Item {
    private static final String ROOT_TAG = "BioForgeGeneImprint";
    private static final String CHANNEL = "gene_imprint";

    public record Data(String strainPayload, VaccineTargetCategory category, String target,
                       boolean identified, String fingerprint) {}

    private record Candidate(VaccineTargetCategory category, String target) {}

    public GeneImprintItem() {
        super(new Properties().stacksTo(1));
    }

    @Nullable
    public static Data read(ItemStack stack) {
        if (!net.jenkimods.bioforge.util.StackData.has(stack)) return null;
        CompoundTag root = net.jenkimods.bioforge.util.StackData.copy(stack);
        CompoundTag tag = NbtObfuscator.readCompound(root, CHANNEL);
        if (tag == null && root.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            tag = root.getCompound(ROOT_TAG);
        }
        if (tag == null) return null;
        VaccineTargetCategory category =
                VaccineTargetCategory.fromName(tag.getString("Category"));
        String payload = tag.getString("Strain");
        String target = tag.getString("Target");
        if (category == null || payload.isBlank() || target.isBlank()) return null;

        boolean identified = !tag.contains("Identified") || tag.getBoolean("Identified");
        String fingerprint = tag.getString("Fingerprint");
        if (fingerprint.isBlank()) fingerprint = fingerprint(payload, category, target);
        return new Data(payload, category, target, identified, fingerprint);
    }

    public static boolean isBlank(ItemStack stack) {
        return stack.getItem() instanceof GeneImprintItem && read(stack) == null;
    }

    public static boolean isIdentified(ItemStack stack) {
        Data data = read(stack);
        return data != null && data.identified();
    }




    public static boolean captureUnknown(ItemStack stack, StrainData strain, RandomSource random) {
        if (!(stack.getItem() instanceof GeneImprintItem)
                || strain == null || strain.getPathogenId() == null) {
            return false;
        }
        List<Candidate> candidates = allCandidates(strain);
        if (candidates.isEmpty()) return false;
        Candidate selected = candidates.get(random.nextInt(candidates.size()));
        write(stack, new Data(strain.toPayload(), selected.category(), selected.target(), false,
                fingerprint(strain.toPayload(), selected.category(), selected.target())));
        return true;
    }

    public static boolean identify(ItemStack stack) {
        Data data = read(stack);
        if (data == null || data.identified()) return false;
        write(stack, new Data(data.strainPayload(), data.category(), data.target(), true,
                data.fingerprint()));
        return true;
    }

    private static void write(ItemStack stack, Data data) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Strain", data.strainPayload());
        tag.putString("Category", data.category().serializedName());
        tag.putString("Target", data.target());
        tag.putBoolean("Identified", data.identified());
        tag.putString("Fingerprint", data.fingerprint());
        net.jenkimods.bioforge.util.StackData.update(stack,
                root -> root.remove(ROOT_TAG));
        NbtObfuscator.writeCompound(stack, CHANNEL, tag);
    }

    public static boolean copy(ItemStack source, ItemStack destination) {
        Data data = read(source);
        if (data == null || !isBlank(destination)) return false;
        write(destination, data);
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack other = player.getItemInHand(otherHand);
        if (other.getItem() instanceof GeneImprintItem) {
            ItemStack source = read(stack) != null ? stack : other;
            ItemStack destination = source == stack ? other : stack;
            if (copy(source, destination)) {
                if (!level.isClientSide()) {
                    player.displayClientMessage(Component.translatable(
                            "message.bioforge.gene_imprint.copied")
                            .withStyle(ChatFormatting.AQUA), true);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
            }
        }
        if (!level.isClientSide()) {
            player.displayClientMessage(Component.translatable(
                    read(stack) == null
                            ? "message.bioforge.gene_imprint.use_vaccine_maker"
                            : "message.bioforge.gene_imprint.use_microscope")
                    .withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                   LivingEntity target, InteractionHand hand) {
        if (!player.level().isClientSide()) {
            player.displayClientMessage(Component.translatable(
                    "message.bioforge.gene_imprint.use_vaccine_maker")
                    .withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide());
    }

    private static List<Candidate> allCandidates(StrainData strain) {
        List<Candidate> values = new ArrayList<>();
        for (VaccineTargetCategory category : VaccineTargetCategory.values()) {
            for (String target : candidates(strain, category)) {
                values.add(new Candidate(category, target));
            }
        }
        values.sort(Comparator.comparing((Candidate candidate) ->
                        candidate.category().serializedName())
                .thenComparing(Candidate::target));
        return values;
    }

    private static List<String> candidates(StrainData strain, VaccineTargetCategory category) {
        List<String> values = new ArrayList<>();
        switch (category) {
            case MUTATION -> values.addAll(strain.getMutationIds());
            case TRANSMISSION -> strain.getTransmissionIds()
                    .forEach(type -> values.add(BioForgeIds.legacyCompatible(type)));
            case SYMPTOM -> strain.getSymptoms().forEach((id, value) -> {
                var key = BioForgeSymptoms.getAllSymptomKeys().get(id);
                if (key == null || !value.equals(serialize(key.getDefaultValue()))) {
                    values.add(id);
                }
            });
        }
        values.sort(Comparator.naturalOrder());
        return values;
    }

    private static String fingerprint(String payload, VaccineTargetCategory category,
                                      String target) {
        String canonicalPayload = StrainData.canonicalGeneticPayload(payload);
        String source = canonicalPayload + "|" + category.serializedName() + "|" + target;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8))
                .toString().substring(0, 8).toUpperCase();
    }

    private static String serialize(Object value) {
        return value instanceof Enum<?> enumeration
                ? enumeration.name() : String.valueOf(value);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        Data data = read(stack);
        if (data == null) {
            tooltip.add(Component.translatable("item.bioforge.gene_imprint.empty")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.bioforge.gene_imprint.extract")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("item.bioforge.gene_imprint.copy_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.bioforge.gene_imprint.fingerprint",
                data.fingerprint()).withStyle(ChatFormatting.DARK_AQUA));
        if (!data.identified()) {
            tooltip.add(Component.translatable("item.bioforge.gene_imprint.unidentified")
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("item.bioforge.gene_imprint.microscope")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.bioforge.gene_imprint.category",
                Component.translatable("vaccine.category." + data.category().serializedName()))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.bioforge.gene_imprint.target",
                        CrisprDisplayNames.target(data.category(), data.target()))
                .withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable("item.bioforge.gene_imprint.copy_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
