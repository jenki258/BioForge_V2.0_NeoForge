package net.jenkimods.bioforge.item.clipboard;

import net.jenkimods.bioforge.blood.BloodType;
import net.jenkimods.bioforge.util.WritableBookPages;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

public class ClipboardAppendToBookHelper {
    private static final int MAX_CHARS_PER_PAGE = 130;

    public static void appendToBook(CompoundTag tag, Player player, ItemStack bookStack) {
        if (player == null || tag == null) return;
        if (!bookStack.is(Items.WRITABLE_BOOK)) return;

        String reportText = buildPlainTextReport(tag, player);
        WritableBookPages.appendAll(bookStack,
                splitIntoPages(reportText, MAX_CHARS_PER_PAGE));
    }

    private static List<String> splitIntoPages(String text, int maxChars) {
        List<String> pages = new ArrayList<>();
        if (text == null || text.isEmpty()) return pages;

        String[] lines = text.split("\n", -1);
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            while (line.length() > maxChars) {
                if (current.length() > 0) {
                    pages.add(current.toString());
                    current = new StringBuilder();
                }
                pages.add(line.substring(0, maxChars));
                line = line.substring(maxChars);
            }

            int addLength = line.length() + (current.length() > 0 ? 1 : 0);
            if (current.length() + addLength > maxChars && current.length() > 0) {
                pages.add(current.toString());
                current = new StringBuilder();
            }

            if (current.length() > 0) {
                current.append("\n");
            }
            current.append(line);
        }

        if (current.length() > 0) {
            pages.add(current.toString());
        }
        return pages;
    }
    private static String buildPlainTextReport(CompoundTag tag, Player player) {
        StringBuilder sb = new StringBuilder();

        String patient = tag.contains("SubjectName") ? tag.getString("SubjectName") : "???";
        sb.append(translate(player, "clipboard.entry.patient", patient)).append("\n\n");

        sb.append(translate(player, "clipboard.section.vital")).append("\n");

        if (tag.contains("TemperatureC")) {
            float temp = tag.getFloat("TemperatureC");
            String statusKey = temp >= 38.5f
                    ? "clipboard.temperature.fever"
                    : (temp <= 35.5f
                       ? "clipboard.temperature.hypothermia"
                       : "clipboard.temperature.normal");
            String status = translate(player, statusKey);
            String unstable = tag.getBoolean("TempUnstable") ? " (?)" : "";
            String tempStr = translate(player, "clipboard.entry.temperature",
                    String.format("%.1f°C", temp) + " (" + status + ")" + unstable);
            sb.append(tempStr).append("\n");
        } else {
            sb.append(translate(player, "clipboard.entry.temperature", "--")).append("\n");
        }

        if (tag.contains("HeartRate")) {
            String rateKey = "clipboard.stethoscope." + tag.getString("HeartRate").toLowerCase();
            String rate = translate(player, rateKey);
            String unstable = tag.getBoolean("HeartUnstable") ? " (?)" : "";
            String heartStr = translate(player, "clipboard.entry.heart", rate + unstable);
            sb.append(heartStr).append("\n");
        } else {
            sb.append(translate(player, "clipboard.entry.heart", "--")).append("\n");
        }

        if (tag.contains("OxygenSaturation")) {
            float o2 = tag.getFloat("OxygenSaturation");
            float pi = tag.contains("PerfusionIndex") ? tag.getFloat("PerfusionIndex") : 0.7f;
            String piKey = pi > 0.7f
                    ? "clipboard.pi.strong"
                    : (pi > 0.3f
                       ? "clipboard.pi.moderate"
                       : "clipboard.pi.weak");
            String piDesc = translate(player, piKey);
            String unstable = tag.getBoolean("O2Unstable") ? " (?)" : "";
            String o2Str = translate(player, "clipboard.entry.oxygen",
                    String.format("%.0f%%", o2 * 100), piDesc + unstable);
            sb.append(o2Str).append("\n");
        } else {
            sb.append(translate(player, "clipboard.entry.oxygen", "--", "--")).append("\n");
        }

        sb.append("\n").append(translate(player, "clipboard.section.respiratory")).append("\n");

        if (tag.contains("LungSound")) {
            String soundKey = "clipboard.stethoscope." + tag.getString("LungSound").toLowerCase();
            String sound = translate(player, soundKey);
            String unstable = tag.getBoolean("LungUnstable") ? " (?)" : "";
            String lungStr = translate(player, "clipboard.entry.lungs", sound + unstable);
            sb.append(lungStr).append("\n");
        } else {
            sb.append(translate(player, "clipboard.entry.lungs", "--")).append("\n");
        }

        sb.append("\n").append(translate(player, "clipboard.section.neurological")).append("\n");

        if (tag.contains("ReflexDelay")) {
            String delayKey = "clipboard.reflex." + tag.getString("ReflexDelay").toLowerCase();
            String strengthKey = "clipboard.reflex." +
                    (tag.contains("ReflexStrength") ? tag.getString("ReflexStrength") : "moderate").toLowerCase();
            String delay = translate(player, delayKey);
            String strength = translate(player, strengthKey);
            String unstable = tag.getBoolean("ReflexUnstable") ? " (?)" : "";
            String reflexStr = translate(player, "clipboard.entry.reflex", delay, strength, unstable);
            sb.append(reflexStr).append("\n");
        } else {
            sb.append(translate(player, "clipboard.entry.reflex", "--", "--", "")).append("\n");
        }

        sb.append("\n").append(translate(player, "clipboard.section.visual")).append("\n");

        if (tag.contains("Redness")) {
            String unstable = tag.getBoolean("VisualUnstable") ? " (?)" : "";
            sb.append(translate(player, "clipboard.entry.redness",
                    visualDesc(tag.getFloat("Redness")) + unstable)).append("\n");
            sb.append(translate(player, "clipboard.entry.lesions",
                    visualDesc(tag.getFloat("Lesions")) + unstable)).append("\n");
            sb.append(translate(player, "clipboard.entry.secretion",
                    visualDesc(tag.getFloat("Secretion")) + unstable)).append("\n");
            sb.append(translate(player, "clipboard.entry.swelling",
                    visualDesc(tag.getFloat("Swelling")) + unstable)).append("\n");
        } else {
            sb.append(translate(player, "clipboard.entry.redness", "--")).append("\n");
            sb.append(translate(player, "clipboard.entry.lesions", "--")).append("\n");
            sb.append(translate(player, "clipboard.entry.secretion", "--")).append("\n");
            sb.append(translate(player, "clipboard.entry.swelling", "--")).append("\n");
        }

        sb.append("\n").append(translate(player, "clipboard.section.blood")).append("\n");

        boolean reagentA = tag.getBoolean("ReagentA");
        boolean reagentB = tag.getBoolean("ReagentB");
        boolean reagentD = tag.getBoolean("ReagentD");

        if (!reagentA || !reagentB || !reagentD) {
            sb.append(translate(player, "clipboard.blood.incomplete")).append("\n");
        } else if (tag.contains("BloodType")) {
            sb.append(translate(player, "clipboard.entry.blood_group",
                    localizedBloodType(player, tag.getString("BloodType")))).append("\n");

            String antiA = tag.getBoolean("AntiA") ? "+" : "-";
            String antiB = tag.getBoolean("AntiB") ? "+" : "-";
            String antiD = tag.getBoolean("AntiD") ? "+" : "-";

            sb.append(translate(player, "clipboard.entry.anti_a", antiA)).append("\n");
            sb.append(translate(player, "clipboard.entry.anti_b", antiB)).append("\n");
            sb.append(translate(player, "clipboard.entry.anti_d", antiD)).append("\n");
        }

        return sb.toString();
    }

    private static String localizedBloodType(Player player, String storedName) {
        BloodType type = BloodType.findByName(storedName);
        if (type == null) return storedName;
        return type == BloodType.ANIMAL_BLOOD
                ? translate(player, "blood.type.animal") : type.getDisplayName();
    }

    private static String translate(Player player, String key, String... args) {
        Language language = Language.getInstance();
        String pattern = language.getOrDefault(key);
        if (args.length > 0) {
            String result = pattern;
            for (String arg : args) {
                result = result.replaceFirst("%s", arg);
            }
            return result;
        }
        return pattern;
    }

    private static String visualDesc(Float value) {
        try {
            if (value > 0.7f) return translate(null, "clipboard.visual.high");
            if (value > 0.3f) return translate(null, "clipboard.visual.moderate");
            if (value > 0.0f) return translate(null, "clipboard.visual.low");
            return translate(null, "clipboard.visual.none");
        } catch (NumberFormatException e) {
            return "";
        }
    }
}
