package net.jenkimods.bioforge.item.reagents;

import net.jenkimods.bioforge.infection.MicroscopeVisibility;
import net.jenkimods.bioforge.infection.PathogenType;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.definition.BioForgeClientDefinitionCache;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.item.BloodSampleUtil;
import net.jenkimods.bioforge.item.bones.BoneMarrowItem;
import net.jenkimods.bioforge.item.bones.WitheredBoneMarrowItem;
import net.jenkimods.bioforge.item.needle.NeedleItem;
import net.jenkimods.bioforge.item.needle.SyringeItem;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public class DiagnosticReagentItem extends Item {
    public enum Kind {
        PATHOGEN,
        VISIBILITY
    }

    private static final String DATA_CHANNEL = "diagnostic_reagent";
    private static final String KEY_REACTED = "Reacted";
    private static final String KEY_RESULT = "Result";

    private final Kind kind;

    public DiagnosticReagentItem(Kind kind) {
        super(new Properties().stacksTo(64));
        this.kind = kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                   InteractionHand hand) {
        ItemStack reagent = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.pass(reagent);
        if (readResult(reagent) != null) {
            player.sendSystemMessage(Component.translatable(
                    "item.bioforge.diagnostic_reagent.already_used"));
            return InteractionResultHolder.fail(reagent);
        }

        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack sample = player.getItemInHand(otherHand);
        if (!BloodSampleUtil.hasBlood(sample)) {
            player.sendSystemMessage(Component.translatable(
                    "item.bioforge.diagnostic_reagent.no_sample")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return InteractionResultHolder.fail(reagent);
        }

        StrainData strain = readStrain(sample);
        TestResult test = kind == Kind.PATHOGEN
                ? pathogenTest(strain) : visibilityTest(strain, level);
        ItemStack usedReagent = takeOne(reagent);
        CompoundTag result = new CompoundTag();
        result.putBoolean(KEY_REACTED, test.reacted());
        result.putString(KEY_RESULT, test.result());
        NbtObfuscator.writeCompound(usedReagent, DATA_CHANNEL, result);

        consumeSample(sample);
        if (reagent.isEmpty()) {
            player.setItemInHand(hand, usedReagent);
        } else if (!player.getInventory().add(usedReagent)) {
            level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(),
                    player.getZ(), usedReagent));
        }

        level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY,
                SoundSource.PLAYERS, 1.0f, 1.2f);
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    private static StrainData readStrain(ItemStack sample) {
        String payload = NbtObfuscator.readInfection(sample);
        return payload == null || payload.isBlank() ? null : StrainData.parse(payload);
    }

    private static TestResult pathogenTest(@Nullable StrainData strain) {
        ResourceLocation pathogenId = strain == null ? null : strain.getPathogenId();
        return pathogenId == null
                ? new TestResult(false, "none")
                : new TestResult(true, BioForgeIds.legacyCompatible(pathogenId));
    }

    private static TestResult visibilityTest(@Nullable StrainData strain, Level level) {
        if (strain == null || strain.getPathogenId() == null) {
            return new TestResult(false, MicroscopeVisibility.NONE.name());
        }
        MicroscopeVisibility visibility = strain
                .getSymptom(BioForgeSymptoms.MICROSCOPE_VISIBILITY.getId())
                .map(MicroscopeVisibility::fromName)
                .orElse(MicroscopeVisibility.NONE);
        float chance = detectionChance(visibility);
        boolean reacted = chance >= 1.0f
                || chance > 0.0f && level.random.nextFloat() < chance;
        return new TestResult(reacted, visibility.name());
    }

    public static float detectionChance(MicroscopeVisibility visibility) {
        return switch (visibility) {
            case NONE -> 0.0f;
            case VERY_LOW -> 0.10f;
            case LOW -> 0.25f;
            case MEDIUM -> 0.50f;
            case HIGH -> 0.75f;
            case EXTREME -> 1.0f;
        };
    }

    private static ItemStack takeOne(ItemStack reagent) {
        ItemStack used = reagent.copy();
        used.setCount(1);
        reagent.shrink(1);
        return used;
    }

    private static void consumeSample(ItemStack sample) {
        if (sample.getItem() instanceof SyringeItem) {
            SyringeItem.consumeUse(sample);
        } else if (sample.getItem() instanceof NeedleItem) {
            NeedleItem.clearBlood(sample);
        } else if (sample.getItem() instanceof BoneMarrowItem
                || sample.getItem() instanceof WitheredBoneMarrowItem) {
            sample.shrink(1);
        } else {
            BloodSampleUtil.clear(sample);
        }
        if (!BloodSampleUtil.hasBlood(sample)) {
            NbtObfuscator.clearInfection(sample);
        }
    }

    @Nullable
    private static TestResult readResult(ItemStack stack) {
        CompoundTag result = NbtObfuscator.readCompound(stack, DATA_CHANNEL);
        if (result == null || !result.contains(KEY_RESULT)) return null;
        return new TestResult(result.getBoolean(KEY_REACTED),
                result.getString(KEY_RESULT));
    }

    public static int getLiquidColor(ItemStack stack) {
        if (!(stack.getItem() instanceof DiagnosticReagentItem reagent)) {
            return 0xFFFFFF;
        }
        TestResult result = readResult(stack);
        if (result == null) {
            return reagent.kind == Kind.PATHOGEN ? 0x72C8D4 : 0xA6D8E7;
        }
        if (!result.reacted()) return 0xA7ADB2;
        return reagent.kind == Kind.PATHOGEN
                ? pathogenColor(result.result()) : visibilityColor(result.result());
    }

    private static int pathogenColor(String result) {
        ResourceLocation id;
        try {
            id = BioForgeIds.parse(result);
        } catch (IllegalArgumentException exception) {
            return 0xFFFFFF;
        }
        PathogenType pathogen = BioForgeIds.legacyPathogen(id);
        if (pathogen != null) {
            return switch (pathogen) {
                case VIRUS -> 0xB14DE0;
                case BACTERIA -> 0x65C85A;
                case FUNGI -> 0xD19A3E;
                case PARASITE -> 0xD94B4B;
                case PRION -> 0x62D7D0;
                case UNIVERSAL -> 0xF0E4FF;
            };
        }
        BioForgeClientDefinitionCache.PathogenView client =
                BioForgeClientDefinitionCache.snapshot().pathogens().get(id);
        if (client != null) return client.color();
        return BioForgeDefinitionManager.pathogen(id)
                .map(definition -> definition.color()).orElse(0xFFFFFF);
    }

    private static int visibilityColor(String result) {
        return switch (MicroscopeVisibility.fromName(result)) {
            case NONE -> 0xA7ADB2;
            case VERY_LOW -> 0x587BD8;
            case LOW -> 0x42B9D1;
            case MEDIUM -> 0xE4D34A;
            case HIGH -> 0xE88A38;
            case EXTREME -> 0xE33F72;
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        TestResult result = readResult(stack);
        if (result == null) {
            tooltip.add(Component.translatable(kind == Kind.PATHOGEN
                            ? "item.bioforge.pathogen_reagent.description"
                            : "item.bioforge.visibility_reagent.description")
                    .withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable(
                            "item.bioforge.diagnostic_reagent.instructions")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        if (kind == Kind.PATHOGEN) {
            if (result.reacted()) {
                ResourceLocation id = BioForgeIds.parse(result.result());
                BioForgeClientDefinitionCache.PathogenView client =
                        BioForgeClientDefinitionCache.snapshot().pathogens().get(id);
                String key = client != null ? client.translationKey()
                        : BioForgeDefinitionManager.pathogen(id)
                        .map(definition -> definition.translationKey())
                        .orElse("pathogen." + id.getNamespace() + "." + id.getPath());
                tooltip.add(Component.translatable(
                                "item.bioforge.pathogen_reagent.result",
                                Component.translatable(key))
                        .withStyle(ChatFormatting.AQUA));
            } else {
                tooltip.add(Component.translatable(
                                "item.bioforge.pathogen_reagent.negative")
                        .withStyle(ChatFormatting.GRAY));
            }
        } else if (result.reacted()) {
            String key = "microscope.symptom.microscope_visibility."
                    + result.result().toLowerCase(Locale.ROOT);
            tooltip.add(Component.translatable(
                            "item.bioforge.visibility_reagent.result",
                            Component.translatable(key))
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable(
                            "item.bioforge.visibility_reagent.negative")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(
                            "item.bioforge.visibility_reagent.inconclusive")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable(
                        "item.bioforge.diagnostic_reagent.spent")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private record TestResult(boolean reacted, String result) {}
}
