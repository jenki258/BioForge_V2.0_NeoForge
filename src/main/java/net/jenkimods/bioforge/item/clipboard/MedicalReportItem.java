package net.jenkimods.bioforge.item.clipboard;

import net.jenkimods.bioforge.vaccine.MedicalReportStrainBinding;
import net.jenkimods.bioforge.infection.naming.StrainNamingManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MedicalReportItem extends Item {

    public MedicalReportItem() {
        super(new Properties().stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack report = player.getItemInHand(hand);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(otherHand);
        CompoundTag reportTag = net.jenkimods.bioforge.util.StackData.copy(report);
        if (reportTag == null || !reportTag.hasUUID("SessionId")) {
            return InteractionResultHolder.pass(report);
        }

        if (otherStack.is(Items.WRITABLE_BOOK)) {
            if (!level.isClientSide()) {
                ClipboardAppendToBookHelper.appendToBook(reportTag, player, otherStack);
                player.setItemInHand(otherHand, otherStack);
            }
            return InteractionResultHolder.sidedSuccess(report, level.isClientSide());
        }

        if (!otherStack.is(Items.PAPER)) {
            return InteractionResultHolder.pass(report);
        }

        if (!level.isClientSide()) {
            ItemStack copy = report.copy();
            copy.setCount(1);

            if (!player.getAbilities().instabuild) {
                otherStack.shrink(1);
            }
            if (!player.getInventory().add(copy)) {
                player.drop(copy, false);
            }
        }

        return InteractionResultHolder.sidedSuccess(report, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(stack);
        if (tag == null || !tag.hasUUID("SessionId")) {
            tooltip.add(Component.translatable("item.bioforge.medical_report.blank").withStyle(ChatFormatting.GRAY));
            return;
        }

        String patient = tag.contains("SubjectName") ? tag.getString("SubjectName") : "???";
        tooltip.add(Component.translatable("item.bioforge.medical_report.patient", patient)
                .withStyle(ChatFormatting.AQUA));
        String strainFingerprint = MedicalReportStrainBinding.fingerprint(stack);
        tooltip.add(Component.translatable(
                        strainFingerprint == null
                                ? "item.bioforge.medical_report.strain_unbound"
                                : "item.bioforge.medical_report.strain_bound")
                .withStyle(strainFingerprint == null
                        ? ChatFormatting.DARK_GRAY : ChatFormatting.DARK_AQUA));
        if (strainFingerprint != null && hasCompleteRecord(tag)) {
            StrainNamingManager.getClientName(strainFingerprint).ifPresent(name ->
                    tooltip.add(Component.translatable("item.bioforge.strain_name", name)
                            .withStyle(ChatFormatting.AQUA)));
        }
        tooltip.add(Component.literal(""));

        tooltip.add(Component.translatable("clipboard.section.vital").withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA));

        if (tag.contains("TemperatureC")) {
            float temp = tag.getFloat("TemperatureC");
            String statusKey = temp >= 38.5f ? "clipboard.temperature.fever"
                    : (temp <= 35.5f ? "clipboard.temperature.hypothermia"
                    : "clipboard.temperature.normal");
            String unstable = tag.getBoolean("TempUnstable") ? " (?)" : "";
            Component reading = Component.literal(String.format("%.1f°C (", temp))
                    .append(Component.translatable(statusKey)).append(")").append(unstable);
            tooltip.add(Component.translatable("clipboard.entry.temperature",
                            reading)
                    .withStyle(ChatFormatting.WHITE));
        } else {
            tooltip.add(Component.translatable("clipboard.no_data").withStyle(ChatFormatting.DARK_GRAY));
        }

        if (tag.contains("HeartRate")) {
            String rate = Component.translatable("clipboard.stethoscope." + tag.getString("HeartRate").toLowerCase()).getString();
            String unstable = tag.getBoolean("HeartUnstable") ? " (?)" : "";
            tooltip.add(Component.translatable("clipboard.entry.heart", rate + unstable)
                    .withStyle(ChatFormatting.WHITE));
        } else {
            tooltip.add(Component.translatable("clipboard.no_data").withStyle(ChatFormatting.DARK_GRAY));
        }

        if (tag.contains("OxygenSaturation")) {
            float o2 = tag.getFloat("OxygenSaturation");
            float pi = tag.contains("PerfusionIndex") ? tag.getFloat("PerfusionIndex") : 0.7f;
            String piKey = pi > 0.7f ? "clipboard.pi.strong"
                    : (pi > 0.3f ? "clipboard.pi.moderate" : "clipboard.pi.weak");
            String unstable = tag.getBoolean("O2Unstable") ? " (?)" : "";
            Component perfusion = Component.translatable(piKey).copy().append(unstable);
            tooltip.add(Component.translatable("clipboard.entry.oxygen",
                            String.format("%.0f%%", o2 * 100f), perfusion)
                    .withStyle(ChatFormatting.WHITE));
        } else {
            tooltip.add(Component.translatable("clipboard.no_data").withStyle(ChatFormatting.DARK_GRAY));
        }

        tooltip.add(Component.translatable("clipboard.section.respiratory").withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA));

        if (tag.contains("LungSound")) {
            String sound = Component.translatable("clipboard.stethoscope." + tag.getString("LungSound").toLowerCase()).getString();
            String unstable = tag.getBoolean("LungUnstable") ? " (?)" : "";
            tooltip.add(Component.translatable("clipboard.entry.lungs", sound + unstable)
                    .withStyle(ChatFormatting.WHITE));
        } else {
            tooltip.add(Component.translatable("clipboard.no_data").withStyle(ChatFormatting.DARK_GRAY));
        }

        tooltip.add(Component.translatable("clipboard.section.neurological").withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA));

        if (tag.contains("ReflexDelay")) {
            String delay = Component.translatable("clipboard.reflex." + tag.getString("ReflexDelay").toLowerCase()).getString();
            String strength = Component.translatable("clipboard.reflex." + tag.getString("ReflexStrength").toLowerCase()).getString();
            String unstable = tag.getBoolean("ReflexUnstable") ? " (?)" : "";
            tooltip.add(Component.translatable("clipboard.entry.reflex", delay, strength, unstable)
                    .withStyle(ChatFormatting.WHITE));
        } else {
            tooltip.add(Component.translatable("clipboard.no_data").withStyle(ChatFormatting.DARK_GRAY));
        }

        tooltip.add(Component.translatable("clipboard.section.visual").withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA));

        if (tag.contains("Redness")) {
            String unstable = tag.getBoolean("VisualUnstable") ? " (?)" : "";
            tooltip.add(Component.translatable("clipboard.entry.redness",
                            describeVisual(tag.getFloat("Redness")) + unstable)
                    .withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("clipboard.entry.lesions",
                            describeVisual(tag.getFloat("Lesions")) + unstable)
                    .withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("clipboard.entry.secretion",
                            describeVisual(tag.getFloat("Secretion")) + unstable)
                    .withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("clipboard.entry.swelling",
                            describeVisual(tag.getFloat("Swelling")) + unstable)
                    .withStyle(ChatFormatting.WHITE));
        } else {
            tooltip.add(Component.translatable("clipboard.no_data").withStyle(ChatFormatting.DARK_GRAY));
        }

        tooltip.add(Component.translatable("clipboard.section.blood").withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA));

        boolean hasAntiA = tag.contains("AntiA");
        boolean hasAntiB = tag.contains("AntiB");
        boolean hasAntiD = tag.contains("AntiD");

        if (!hasAntiA || !hasAntiB || !hasAntiD) {
            tooltip.add(Component.translatable("clipboard.blood.incomplete")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (tag.contains("BloodType")) {
            tooltip.add(Component.translatable("clipboard.entry.blood_group",
                            net.jenkimods.bioforge.blood.BloodType.displayNameComponent(
                                    tag.getString("BloodType")))
                    .withStyle(ChatFormatting.WHITE));

            tooltip.add(Component.translatable("clipboard.entry.anti_a",
                            tag.getBoolean("AntiA") ? "+" : "-")
                    .withStyle(ChatFormatting.WHITE));

            tooltip.add(Component.translatable("clipboard.entry.anti_b",
                            tag.getBoolean("AntiB") ? "+" : "-")
                    .withStyle(ChatFormatting.WHITE));

            tooltip.add(Component.translatable("clipboard.entry.anti_d",
                            tag.getBoolean("AntiD") ? "+" : "-")
                    .withStyle(ChatFormatting.WHITE));
        }

        tooltip.add(Component.literal(""));
        tooltip.add(Component.translatable("item.bioforge.medical_report.hint_copy")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.medical_report.hint_book")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.medical_report.hint_vaccine_maker")
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    private String describeVisual(float val) {
        if (val > 0.7f) return "High";
        if (val > 0.3f) return "Moderate";
        if (val > 0f) return "Low";
        return "None";
    }

    public static boolean hasCompleteRecord(CompoundTag tag) {
        return tag.contains("TemperatureC")
                && tag.contains("HeartRate")
                && tag.contains("OxygenSaturation")
                && tag.contains("LungSound")
                && tag.contains("ReflexDelay")
                && tag.contains("Redness")
                && tag.contains("AntiA")
                && tag.contains("AntiB")
                && tag.contains("AntiD");
    }
}
