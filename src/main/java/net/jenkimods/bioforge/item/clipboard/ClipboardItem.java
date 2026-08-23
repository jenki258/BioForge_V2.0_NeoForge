package net.jenkimods.bioforge.item.clipboard;

import net.jenkimods.bioforge.util.HitResultUtil;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ClipboardItem extends Item {

    public ClipboardItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack clipboard = player.getItemInHand(hand);
        InteractionHand other = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(other);

        if (otherStack.is(Items.WRITABLE_BOOK)) {
            CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
            if (!level.isClientSide() && tag.hasUUID("SessionId")) {
                ClipboardAppendToBookHelper.appendToBook(tag, player, otherStack);
                player.setItemInHand(other, otherStack);
            }
            return InteractionResultHolder.sidedSuccess(clipboard, level.isClientSide());
        }

        if (otherStack.is(Items.PAPER)) {
            CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
            if (!level.isClientSide() && tag.hasUUID("SessionId")) {
                ClipboardCreateReportHelper.createReport(tag, player, otherStack);
                player.setItemInHand(other, otherStack);
            }
            return InteractionResultHolder.sidedSuccess(clipboard, level.isClientSide());
        }

        if (level.isClientSide()) {
            return super.use(level, player, hand);
        }

        if (player.isShiftKeyDown()) {
            EntityHitResult hitResult = HitResultUtil.getHitResult(player);
            Entity target = hitResult == null ? null : hitResult.getEntity();
            if (target == null) {
                ClipboardHelper.clearClipboardItem(clipboard);
                player.sendSystemMessage(Component.translatable("item.bioforge.clipboard.cleared"));
                return InteractionResultHolder.success(clipboard);
            }
        }

        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        if (NbtObfuscator.readString(tag) != null) {
            boolean reactivated = ClipboardHelper.reactivateSession(player, clipboard);
            if (reactivated) {
                player.sendSystemMessage(Component.translatable("item.bioforge.clipboard.now_inspecting",
                        ClipboardHelper.getSubjectName(clipboard)));
            }
            return InteractionResultHolder.success(clipboard);
        }

        EntityHitResult hitResult = HitResultUtil.getHitResult(player);
        Entity target = hitResult == null ? null : hitResult.getEntity();
        LivingEntity subject;
        if (target instanceof LivingEntity living) {
            subject = living;
            player.sendSystemMessage(Component.translatable("item.bioforge.clipboard.patient_set", living.getDisplayName()));
        } else {
            subject = player;
            player.sendSystemMessage(Component.translatable("item.bioforge.clipboard.patient_set_self"));
        }
        ClipboardHelper.assignSubject(
                player,
                subject,
                clipboard
        );
        return InteractionResultHolder.success(clipboard);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack clipboard, Player player, LivingEntity living, InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ClipboardHelper.assignSubject(
                player,
                living,
                clipboard
        );
        player.setItemInHand(hand, clipboard);
        player.sendSystemMessage(Component.translatable("item.bioforge.clipboard.patient_set", living.getDisplayName()));
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(stack);
        if (tag == null || !tag.hasUUID("SessionId")) {
            tooltip.add(Component.translatable("item.bioforge.clipboard.no_patient").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.bioforge.clipboard.hint_assign").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        String subjectName = tag.contains("SubjectName") ? tag.getString("SubjectName") : "???";
        tooltip.add(Component.translatable("item.bioforge.clipboard.current_patient", subjectName)
                .withStyle(ChatFormatting.AQUA));
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
                    reading).withStyle(ChatFormatting.WHITE));
        } else {
            tooltip.add(Component.translatable("clipboard.no_data").withStyle(ChatFormatting.DARK_GRAY));
        }

        if (tag.contains("HeartRate")) {
            String rate = Component.translatable("clipboard.stethoscope." + tag.getString("HeartRate").toLowerCase()).getString();
            String unstable = tag.getBoolean("HeartUnstable") ? " (?)" : "";
            tooltip.add(Component.translatable("clipboard.entry.heart", rate + unstable).withStyle(ChatFormatting.WHITE));
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
                    String.format("%.0f%%", o2 * 100f), perfusion).withStyle(ChatFormatting.WHITE));
        } else {
            tooltip.add(Component.translatable("clipboard.no_data").withStyle(ChatFormatting.DARK_GRAY));
        }

        tooltip.add(Component.translatable("clipboard.section.respiratory").withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA));

        if (tag.contains("LungSound")) {
            String sound = Component.translatable("clipboard.stethoscope." + tag.getString("LungSound").toLowerCase()).getString();
            String unstable = tag.getBoolean("LungUnstable") ? " (?)" : "";
            tooltip.add(Component.translatable("clipboard.entry.lungs", sound + unstable).withStyle(ChatFormatting.WHITE));
        } else {
            tooltip.add(Component.translatable("clipboard.no_data").withStyle(ChatFormatting.DARK_GRAY));
        }

        tooltip.add(Component.translatable("clipboard.section.neurological").withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA));

        if (tag.contains("ReflexDelay")) {
            String delay = Component.translatable("clipboard.reflex." + tag.getString("ReflexDelay").toLowerCase()).getString();
            String strength = Component.translatable("clipboard.reflex." + tag.getString("ReflexStrength").toLowerCase()).getString();
            String unstable = tag.getBoolean("ReflexUnstable") ? " (?)" : "";
            tooltip.add(Component.translatable("clipboard.entry.reflex", delay, strength, unstable).withStyle(ChatFormatting.WHITE));
        } else {
            tooltip.add(Component.translatable("clipboard.no_data").withStyle(ChatFormatting.DARK_GRAY));
        }

        tooltip.add(Component.translatable("clipboard.section.visual").withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA));

        if (tag.contains("Redness")) {
            String unstable = tag.getBoolean("VisualUnstable") ? " (?)" : "";
            tooltip.add(Component.translatable("clipboard.entry.redness",
                    describeVisual(tag.getFloat("Redness")) + unstable).withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("clipboard.entry.lesions",
                    describeVisual(tag.getFloat("Lesions")) + unstable).withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("clipboard.entry.secretion",
                    describeVisual(tag.getFloat("Secretion")) + unstable).withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("clipboard.entry.swelling",
                    describeVisual(tag.getFloat("Swelling")) + unstable).withStyle(ChatFormatting.WHITE));
        } else {
            tooltip.add(Component.translatable("clipboard.no_data").withStyle(ChatFormatting.DARK_GRAY));
        }

        tooltip.add(Component.translatable("clipboard.section.blood").withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA));

        boolean reagentA = tag.getBoolean("ReagentA");
        boolean reagentB = tag.getBoolean("ReagentB");
        boolean reagentD = tag.getBoolean("ReagentD");

        if (!reagentA || !reagentB || !reagentD) {
            tooltip.add(Component.translatable("clipboard.blood.incomplete").withStyle(ChatFormatting.DARK_GRAY));
        } else if (tag.contains("BloodType")) {
            tooltip.add(Component.translatable("clipboard.entry.blood_group",
                    net.jenkimods.bioforge.blood.BloodType.displayNameComponent(
                            tag.getString("BloodType"))).withStyle(ChatFormatting.WHITE));

            if (tag.contains("AntiA")) {
                tooltip.add(Component.translatable("clipboard.entry.anti_a",
                        tag.getBoolean("AntiA") ? "+" : "-").withStyle(ChatFormatting.WHITE));
            }

            if (tag.contains("AntiB")) {
                tooltip.add(Component.translatable("clipboard.entry.anti_b",
                        tag.getBoolean("AntiB") ? "+" : "-").withStyle(ChatFormatting.WHITE));
            }

            if (tag.contains("AntiD")) {
                tooltip.add(Component.translatable("clipboard.entry.anti_d",
                        tag.getBoolean("AntiD") ? "+" : "-").withStyle(ChatFormatting.WHITE));
            }
        }

        tooltip.add(Component.translatable("item.bioforge.clipboard.hint_resume").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.clipboard.hint_print").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.clipboard.hint_print_book").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.clipboard.hint_clear").withStyle(ChatFormatting.DARK_GRAY));
    }

    private String describeVisual(float val) {
        if (val > 0.7f) return "High";
        if (val > 0.3f) return "Moderate";
        if (val > 0f) return "Low";
        return "None";
    }

    public static float getFilledModel(ItemStack stack) {
        return NbtObfuscator.readString(stack) != null ? 1.0f : 0.0f;
    }
}
