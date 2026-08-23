package net.jenkimods.bioforge.item.samples;

import net.jenkimods.bioforge.blood.BloodType;
import net.jenkimods.bioforge.blood.knowledge.BloodKnowledge;
import net.jenkimods.bioforge.blood.knowledge.BloodKnowledgeStore;
import net.jenkimods.bioforge.item.BloodSampleUtil;
import net.jenkimods.bioforge.util.NbtObfuscator.ObfuscatedData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class CellPelletItem extends Item {

    public CellPelletItem() {
        super(new Properties().stacksTo(1));
    }

    public static boolean hasBlood(ItemStack stack) {
        return BloodSampleUtil.hasBlood(stack);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (!hasBlood(stack)) {
            tooltip.add(Component.translatable("item.bioforge.cell_pellet.empty").withStyle(ChatFormatting.GRAY));
            return;
        }

        ObfuscatedData data = BloodSampleUtil.getData(stack);
        if (data == null) return;

        tooltip.add(Component.translatable("item.bioforge.cell_pellet.filled").withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("item.bioforge.cell_pellet.source", data.sourceName()).withStyle(ChatFormatting.WHITE));
        BloodType type = BloodType.fromName(data.typeName());
        tooltip.add(Component.translatable("item.bioforge.cell_pellet.blood_type", type.getDisplayNameComponent()).withStyle(ChatFormatting.DARK_RED));

        appendKnowledgeLines(data, tooltip);
    }

    @OnlyIn(Dist.CLIENT)
    private static void appendKnowledgeLines(ObfuscatedData data, List<Component> tooltip) {
        if (data.subjectUUID() == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return;
        BloodKnowledgeStore store = BloodKnowledgeStore.get(server);
        Optional<BloodKnowledge> knowledge = store.find(mc.player.getUUID(), data.subjectUUID());
        if (knowledge.isEmpty()) return;
        BloodKnowledge k = knowledge.get();
        if (k.getAntiA() != null && k.getAntiB() != null && k.getAntiD() != null) {
            tooltip.add(Component.translatable("item.bioforge.cell_pellet.reactions").withStyle(ChatFormatting.DARK_GREEN));
            tooltip.add(Component.translatable("item.bioforge.cell_pellet.anti_a", k.getAntiA() ? "+" : "-").withStyle(k.getAntiA() ? ChatFormatting.RED : ChatFormatting.GREEN));
            tooltip.add(Component.translatable("item.bioforge.cell_pellet.anti_b", k.getAntiB() ? "+" : "-").withStyle(k.getAntiB() ? ChatFormatting.RED : ChatFormatting.GREEN));
            tooltip.add(Component.translatable("item.bioforge.cell_pellet.anti_d", k.getAntiD() ? "+" : "-").withStyle(k.getAntiD() ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
    }
}
