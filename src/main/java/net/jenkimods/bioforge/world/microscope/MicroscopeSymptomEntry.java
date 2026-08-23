package net.jenkimods.bioforge.world.microscope;

import net.jenkimods.bioforge.infection.MicroscopeVisibility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MicroscopeSymptomEntry(
        String symptomKey,
        ResourceLocation icon,
        @Nullable Map<String, ResourceLocation> stateIcons,
        boolean isBoolean,
        boolean isEnum,
        MicroscopeVisibility minVisibility,
        String source,
        @Nullable String nbtKey,
        @Nullable String condition,
        boolean displayPercentage
) {
    private static final Pattern CONDITION_PATTERN =
            Pattern.compile("([><]=?|==|!=)\\s*(-?\\d+)");

    public MicroscopeSymptomEntry(String symptomKey, ResourceLocation icon, boolean isBoolean,
                                  MicroscopeVisibility minVisibility) {
        this(symptomKey, icon, null, isBoolean, false, minVisibility, "strain", null, null, true);
    }
    public MicroscopeSymptomEntry(String symptomKey, ResourceLocation icon,
                                  Map<String, ResourceLocation> stateIcons,
                                  MicroscopeVisibility minVisibility) {
        this(symptomKey, icon, stateIcons, false, true, minVisibility, "strain", null, null, true);
    }
    public MicroscopeSymptomEntry(String symptomKey, ResourceLocation icon, boolean isBoolean,
                                  MicroscopeVisibility minVisibility, String source,
                                  @Nullable String nbtKey, @Nullable String condition,
                                  boolean displayPercentage) {
        this(symptomKey, icon, null, isBoolean, false, minVisibility, source, nbtKey, condition, displayPercentage);
    }
    public MicroscopeSymptomEntry(String symptomKey, ResourceLocation icon,
                                  Map<String, ResourceLocation> stateIcons,
                                  MicroscopeVisibility minVisibility, String source,
                                  @Nullable String nbtKey, @Nullable String condition,
                                  boolean displayPercentage) {
        this(symptomKey, icon, stateIcons, false, true, minVisibility, source, nbtKey, condition, displayPercentage);
    }

    public boolean matchesCondition(int nbtValue) {
        if (condition == null || condition.isEmpty()) return true;
        Matcher m = CONDITION_PATTERN.matcher(condition);
        if (!m.matches()) return true;
        String op = m.group(1);
        int threshold = Integer.parseInt(m.group(2));
        return switch (op) {
            case ">"  -> nbtValue >  threshold;
            case "<"  -> nbtValue <  threshold;
            case ">=" -> nbtValue >= threshold;
            case "<=" -> nbtValue <= threshold;
            case "==" -> nbtValue == threshold;
            case "!=" -> nbtValue != threshold;
            default   -> true;
        };
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(symptomKey);
        buf.writeUtf(icon.toString());
        buf.writeBoolean(isBoolean);
        buf.writeBoolean(isEnum);
        buf.writeEnum(minVisibility);
        buf.writeUtf(source);
        buf.writeBoolean(nbtKey != null);
        if (nbtKey != null) buf.writeUtf(nbtKey);
        buf.writeBoolean(condition != null);
        if (condition != null) buf.writeUtf(condition);
        buf.writeBoolean(displayPercentage);
        if (stateIcons != null) {
            buf.writeBoolean(true);
            buf.writeMap(stateIcons, FriendlyByteBuf::writeUtf, (b, rl) -> b.writeUtf(rl.toString()));
        } else {
            buf.writeBoolean(false);
        }
    }

    public static MicroscopeSymptomEntry decode(FriendlyByteBuf buf) {
        String key = buf.readUtf();
        ResourceLocation icon = ResourceLocation.tryParse(buf.readUtf());
        boolean isBool = buf.readBoolean();
        boolean isEnum = buf.readBoolean();
        MicroscopeVisibility vis = buf.readEnum(MicroscopeVisibility.class);
        String source = buf.readUtf();
        String nbtKey = buf.readBoolean() ? buf.readUtf() : null;
        String condition = buf.readBoolean() ? buf.readUtf() : null;
        boolean displayPercentage = buf.readBoolean();
        Map<String, ResourceLocation> states = null;
        if (buf.readBoolean()) {
            states = buf.readMap(FriendlyByteBuf::readUtf, b -> ResourceLocation.tryParse(b.readUtf()));
        }
        return new MicroscopeSymptomEntry(key, icon, states, isBool, isEnum, vis, source, nbtKey, condition, displayPercentage);
    }
}