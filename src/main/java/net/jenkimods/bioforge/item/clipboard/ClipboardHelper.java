package net.jenkimods.bioforge.item.clipboard;

import net.jenkimods.bioforge.vaccine.MedicalReportStrainBinding;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.UUID;

public class ClipboardHelper {

    private static UUID getSessionId(Player player) {
        Session.SessionCapability cap = Session.getData(player);
        if (cap != null) {
            return cap.getId();
        }
        return null;
    }

    private static ItemStack findAppropriateClipboard(Player player, UUID subjectUUID) {
        if (player == null || subjectUUID == null) {
            return ItemStack.EMPTY;
        }
        UUID sessionId = getSessionId(player);
        if (sessionId == null) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ClipboardItem) {
                CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(stack);
                if (tag.contains("SessionId") && tag.getUUID("SessionId").equals(sessionId) && Objects.equals(getSubjectUUID(stack), subjectUUID)) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static void assignSubject(Player player, LivingEntity subject, ItemStack clipboardStack) {
        setSubjectName(clipboardStack, subject.getDisplayName().getString());
        setSubjectUUID(clipboardStack, subject.getUUID());
        MedicalReportStrainBinding.capture(clipboardStack, subject);

        startSession(player, clipboardStack);
    }

    public static void startSession(Player player, ItemStack clipboardStack) {
        UUID sessionId;
        if (net.jenkimods.bioforge.util.StackData.copy(clipboardStack).hasUUID("SessionId")) {
            sessionId = net.jenkimods.bioforge.util.StackData.copy(clipboardStack).getUUID("SessionId");
        } else {
            sessionId = UUID.randomUUID();
        }
        net.jenkimods.bioforge.util.StackData.update(clipboardStack,
                tag -> tag.putUUID("SessionId", sessionId));
        Session.set(player, sessionId);
    }

    private static void setSubjectName(ItemStack clipboardStack, String name) {
        net.jenkimods.bioforge.util.StackData.update(clipboardStack,
                tag -> tag.putString("SubjectName", name));
    }

    private static void setSubjectUUID(ItemStack clipboardStack, UUID uuid) {
        net.jenkimods.bioforge.util.StackData.update(clipboardStack,
                tag -> tag.putUUID("SubjectUUID", uuid));
    }

    public static Boolean reactivateSession(Player player, ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);

        if (!tag.hasUUID("SessionId")) {
            return false;
        }

        UUID sessionId = tag.getUUID("SessionId");

        if (sessionId.equals(Session.get(player))) {
            return false;
        }

        startSession(player, clipboard);
        return true;
    }

    public static void clearClipboardItem(ItemStack clipboard) {
        if (clipboard != null && !clipboard.isEmpty()) {
            UUID sessionId = net.jenkimods.bioforge.util.StackData.copy(clipboard).getUUID("SessionId");
            CompoundTag tag = new CompoundTag();
            if (sessionId != null) {
                tag.putUUID("SessionId", sessionId);
            }
            net.jenkimods.bioforge.util.StackData.set(clipboard, tag);
        }
    }

    public static String getSubjectName(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("SubjectName") ? tag.getString("SubjectName") : null;
    }

    public static Integer getPatientId(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("PatientId") ? tag.getInt("PatientId") : null;
    }

    public static Float getTemperatureC(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("TemperatureC") ? tag.getFloat("TemperatureC") : null;
    }

    public static String getHeartRate(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("HeartRate") ? tag.getString("HeartRate") : null;
    }

    public static String getLungSound(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("LungSound") ? tag.getString("LungSound") : null;
    }

    public static Float getOxygenSaturation(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("OxygenSaturation") ? tag.getFloat("OxygenSaturation") : null;
    }

    public static Float getPerfusionIndex(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("PerfusionIndex") ? tag.getFloat("PerfusionIndex") : null;
    }

    public static Float getRedness(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("Redness") ? tag.getFloat("Redness") : null;
    }

    public static Float getLesions(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("Lesions") ? tag.getFloat("Lesions") : null;
    }

    public static Float getSecretion(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("Secretion") ? tag.getFloat("Secretion") : null;
    }

    public static Float getSwelling(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("Swelling") ? tag.getFloat("Swelling") : null;
    }

    public static String getReflexDelay(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("ReflexDelay") ? tag.getString("ReflexDelay") : null;
    }

    public static String getReflexStrength(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("ReflexStrength") ? tag.getString("ReflexStrength") : null;
    }

    public static String getBloodType(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("BloodType") ? tag.getString("BloodType") : null;
    }

    public static Boolean getAntiA(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("AntiA") ? tag.getBoolean("AntiA") : null;
    }

    public static Boolean getAntiB(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("AntiB") ? tag.getBoolean("AntiB") : null;
    }

    public static Boolean getAntiD(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("AntiD") ? tag.getBoolean("AntiD") : null;
    }

    public static Boolean isReagentA(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("ReagentA") ? tag.getBoolean("ReagentA") : null;
    }

    public static Boolean isReagentB(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("ReagentB") ? tag.getBoolean("ReagentB") : null;
    }

    public static Boolean isReagentD(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("ReagentD") ? tag.getBoolean("ReagentD") : null;
    }

    public static boolean isBloodComplete(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);

        return tag.getBoolean("ReagentA")
                && tag.getBoolean("ReagentB")
                && tag.getBoolean("ReagentD");
    }

    public static Boolean isTempUnstable(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("TempUnstable") ? tag.getBoolean("TempUnstable") : null;
    }

    public static Boolean isHeartUnstable(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("HeartUnstable") ? tag.getBoolean("HeartUnstable") : null;
    }

    public static Boolean isLungUnstable(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("LungUnstable") ? tag.getBoolean("LungUnstable") : null;
    }

    public static Boolean isO2Unstable(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("O2Unstable") ? tag.getBoolean("O2Unstable") : null;
    }

    public static Boolean isVisualUnstable(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("VisualUnstable") ? tag.getBoolean("VisualUnstable") : null;
    }

    public static Boolean isReflexUnstable(ItemStack clipboard) {
        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        return tag.contains("ReflexUnstable") ? tag.getBoolean("ReflexUnstable") : null;
    }

    public static int getRecordedFindingsCount(ItemStack clipboard) {
        int count = 0;
        if (getTemperatureC(clipboard) != null) count++;
        if (getHeartRate(clipboard) != null) count++;
        if (getLungSound(clipboard) != null) count++;
        if (getOxygenSaturation(clipboard) != null) count++;
        if (getRedness(clipboard) != null) count++;
        if (getReflexDelay(clipboard) != null) count++;
        if (isBloodComplete(clipboard)) count++;
        return count;
    }

    public static void recordTemperature(float celsius, boolean unstable, Player player, UUID subjectUUID) {
        ItemStack clipboard = findAppropriateClipboard(player, subjectUUID);
        if (clipboard.isEmpty()) return;

        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        tag.putFloat("TemperatureC", celsius);
        tag.putBoolean("TempUnstable", unstable);
        net.jenkimods.bioforge.util.StackData.set(clipboard, tag);
    }

    public static void recordHeart(String rate, boolean unstable, Player player, UUID subjectUUID) {
        ItemStack clipboard = findAppropriateClipboard(player, subjectUUID);
        if (clipboard.isEmpty()) return;

        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        tag.putString("HeartRate", rate);
        tag.putBoolean("HeartUnstable", unstable);
        net.jenkimods.bioforge.util.StackData.set(clipboard, tag);
    }

    public static void recordLungs(String sound, boolean unstable, Player player, UUID subjectUUID) {
        ItemStack clipboard = findAppropriateClipboard(player, subjectUUID);
        if (clipboard.isEmpty()) return;

        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        tag.putString("LungSound", sound);
        tag.putBoolean("LungUnstable", unstable);
        net.jenkimods.bioforge.util.StackData.set(clipboard, tag);
    }

    public static void recordOxygenSat(float o2, float perf, boolean unstable, Player player, UUID subjectUUID) {
        ItemStack clipboard = findAppropriateClipboard(player, subjectUUID);
        if (clipboard.isEmpty()) return;

        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        tag.putFloat("OxygenSaturation", o2);
        tag.putFloat("PerfusionIndex", perf);
        tag.putBoolean("O2Unstable", unstable);
        net.jenkimods.bioforge.util.StackData.set(clipboard, tag);
    }

    public static void recordVisual(float red, float les, float sec, float swe, boolean unstable, Player player, UUID subjectUUID) {
        ItemStack clipboard = findAppropriateClipboard(player, subjectUUID);
        if (clipboard.isEmpty()) return;

        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        tag.putFloat("Redness", red);
        tag.putFloat("Lesions", les);
        tag.putFloat("Secretion", sec);
        tag.putFloat("Swelling", swe);
        tag.putBoolean("VisualUnstable", unstable);
        net.jenkimods.bioforge.util.StackData.set(clipboard, tag);
    }

    public static void recordReflex(String delay, String strength, boolean unstable, Player player, UUID subjectUUID) {
        ItemStack clipboard = findAppropriateClipboard(player, subjectUUID);
        if (clipboard.isEmpty()) return;

        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);
        tag.putString("ReflexDelay", delay);
        tag.putString("ReflexStrength", strength);
        tag.putBoolean("ReflexUnstable", unstable);
        net.jenkimods.bioforge.util.StackData.set(clipboard, tag);
    }

    public static void recordBloodData(String type, Boolean a, Boolean b, Boolean d, Player player, UUID subjectUUID) {
        ItemStack clipboard = findAppropriateClipboard(player, subjectUUID);
        if (clipboard.isEmpty()) return;

        CompoundTag tag = net.jenkimods.bioforge.util.StackData.copy(clipboard);

        if (type != null) {
            tag.putString("BloodType", type);
        }

        if (a != null) {
            tag.putBoolean("AntiA", a);
            tag.putBoolean("ReagentA", true);
        }

        if (b != null) {
            tag.putBoolean("AntiB", b);
            tag.putBoolean("ReagentB", true);
        }

        if (d != null) {
            tag.putBoolean("AntiD", d);
            tag.putBoolean("ReagentD", true);
        }
        net.jenkimods.bioforge.util.StackData.set(clipboard, tag);
    }

    public static UUID getSubjectUUID(ItemStack clipboard) {
        return net.jenkimods.bioforge.util.StackData.copy(clipboard).getUUID("SubjectUUID");
    }
}
