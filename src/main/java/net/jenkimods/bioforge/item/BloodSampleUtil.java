package net.jenkimods.bioforge.item;

import net.jenkimods.bioforge.blood.BloodType;
import net.jenkimods.bioforge.blood.knowledge.BloodKnowledge;
import net.jenkimods.bioforge.blood.knowledge.BloodKnowledgeStore;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.jenkimods.bioforge.util.NbtObfuscator.ObfuscatedData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class BloodSampleUtil {

    private BloodSampleUtil() {}

    public static boolean hasBlood(ItemStack stack) {
        return NbtObfuscator.hasData(stack);
    }

    @Nullable
    public static ObfuscatedData getData(ItemStack stack) {
        return NbtObfuscator.read(stack);
    }

    public static void setData(ItemStack stack, int amount, BloodType type, String sourceName, @Nullable UUID subjectUUID) {
        NbtObfuscator.write(stack, amount, type.name(), sourceName, subjectUUID);
    }

    public static void clear(ItemStack stack) {
        NbtObfuscator.clear(stack);
    }

    public static void copy(ItemStack from, ItemStack to) {
        ObfuscatedData data = getData(from);
        if (data == null) {
            clear(to);
            return;
        }
        BloodType type = BloodType.fromName(data.typeName());
        setData(to, data.amount(), type, data.sourceName(), data.subjectUUID());
    }

    public static void appendSampleTooltip(ItemStack stack, List<Component> tooltip,
                                           String emptyKey, String filledKey,
                                           String sourceKey, @Nullable String extraKey) {
        if (!hasBlood(stack)) {
            tooltip.add(Component.translatable(emptyKey).withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        ObfuscatedData raw = getData(stack);
        if (raw == null) return;

        tooltip.add(Component.translatable(filledKey).withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable(sourceKey, raw.sourceName()).withStyle(ChatFormatting.RED));
        if (extraKey != null) {
            tooltip.add(Component.translatable(extraKey).withStyle(ChatFormatting.DARK_GRAY));
        }
        appendKnowledgeLines(raw, tooltip);
    }

    @OnlyIn(Dist.CLIENT)
    private static void appendKnowledgeLines(ObfuscatedData raw, List<Component> tooltip) {
        if (raw.subjectUUID() == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return;

        BloodKnowledgeStore store = BloodKnowledgeStore.get(server);
        Optional<BloodKnowledge> knowledge = store.find(mc.player.getUUID(), raw.subjectUUID());
        if (knowledge.isEmpty()) return;

        BloodKnowledge k = knowledge.get();
        if (k.getAntiA() != null && k.getAntiB() != null && k.getAntiD() != null) {
            BloodType type = BloodType.fromName(raw.typeName());
            tooltip.add(Component.translatable("item.bioforge.needle.tooltip.blood_type",
                    type.getDisplayNameComponent()).withStyle(ChatFormatting.DARK_RED));
        }

        tooltip.add(Component.translatable("item.bioforge.needle.tooltip.reactions",
                raw.sourceName()).withStyle(ChatFormatting.DARK_GREEN));

        if (k.getAntiA() != null) {
            tooltip.add(Component.translatable(
                    "item.bioforge.needle.tooltip.anti_a",
                    Component.translatable(k.getAntiA()
                            ? "+"
                            : "-")
            ).withStyle(k.getAntiA() ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
        if (k.getAntiB() != null) {
            tooltip.add(Component.translatable(
                    "item.bioforge.needle.tooltip.anti_b",
                    Component.translatable(k.getAntiB()
                            ? "+"
                            : "-")
            ).withStyle(k.getAntiB() ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
        if (k.getAntiD() != null) {
            tooltip.add(Component.translatable(
                    "item.bioforge.needle.tooltip.anti_d",
                    Component.translatable(k.getAntiD()
                            ? "+"
                            : "-")
            ).withStyle(k.getAntiD() ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
    }
}
