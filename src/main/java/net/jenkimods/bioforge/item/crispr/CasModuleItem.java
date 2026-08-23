package net.jenkimods.bioforge.item.crispr;

import net.jenkimods.bioforge.crispr.BioForgeResearchData;
import net.jenkimods.bioforge.crispr.CrisprCasModuleDefinition;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class CasModuleItem extends Item {
    private static final String MODULE_TAG = "BioForgeCasModule";
    private static final String PAM_TAG = "BioForgeCasPam";
    private static final String EFFICIENCY_TAG = "BioForgeCasEfficiency";
    private static final String PATHOGENS_TAG = "BioForgeCasPathogens";
    private static final String CHANNEL = "cas_module";
    private static final ResourceLocation DEFAULT_MODULE =
            ResourceLocation.tryBuild("bioforge", "spcas9");

    public record DisplayData(String pam, float efficiency, String pathogens) {}

    public CasModuleItem() {
        super(new Properties().stacksTo(1));
    }

    public static ResourceLocation getModuleId(ItemStack stack) {
        CompoundTag data = data(stack);
        if (data.contains(MODULE_TAG)) {
            ResourceLocation parsed =
                    ResourceLocation.tryParse(data.getString(MODULE_TAG));
            if (parsed != null) return parsed;
        }
        return DEFAULT_MODULE;
    }

    public static void setModuleId(ItemStack stack, ResourceLocation id) {
        CompoundTag tag = data(stack);
        tag.putString(MODULE_TAG, id.toString());
        BioForgeResearchData.casModule(id).ifPresent(definition -> {
            tag.putString(PAM_TAG, definition.pam());
            tag.putFloat(EFFICIENCY_TAG, definition.efficiency());
            tag.putString(PATHOGENS_TAG, definition.compatiblePathogens().stream()
                    .map(Enum::name).sorted()
                    .reduce((first, next) -> first + ", " + next).orElse(""));
        });
        writeData(stack, tag);
    }

    public static Component getModuleName(ItemStack stack) {
        ResourceLocation id = getModuleId(stack);
        return BioForgeResearchData.casModule(id)
                .map(definition -> Component.translatable(definition.translationKey()))
                .orElseGet(() -> Component.translatable(
                        "crispr.cas_module." + id.getNamespace() + "." + id.getPath()));
    }

    @Nullable
    public static DisplayData getDisplayData(ItemStack stack) {
        CrisprCasModuleDefinition definition = BioForgeResearchData
                .casModule(getModuleId(stack)).orElse(null);
        if (definition != null) {
            return new DisplayData(definition.pam(), definition.efficiency(),
                    definition.compatiblePathogens().stream().map(Enum::name).sorted()
                            .reduce((first, next) -> first + ", " + next).orElse(""));
        }
        CompoundTag tag = data(stack);
        if (!tag.contains(PAM_TAG)) return null;
        return new DisplayData(tag.getString(PAM_TAG), tag.getFloat(EFFICIENCY_TAG),
                tag.getString(PATHOGENS_TAG));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                   InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        List<ResourceLocation> modules = BioForgeResearchData.casModuleIds().stream()
                .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
        if (modules.isEmpty()) return InteractionResultHolder.pass(stack);
        int current = modules.indexOf(getModuleId(stack));
        ResourceLocation next = modules.get(Math.floorMod(current + 1, modules.size()));
        setModuleId(stack, next);
        player.displayClientMessage(Component.translatable(
                "message.bioforge.cas_module.selected", getModuleName(stack)), true);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bioforge.cas_module.module",
                getModuleName(stack)).withStyle(ChatFormatting.AQUA));
        DisplayData display = getDisplayData(stack);
        if (display != null) {
            tooltip.add(Component.translatable("item.bioforge.cas_module.pam",
                    display.pam()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.bioforge.cas_module.efficiency",
                    String.format(Locale.ROOT, "%.0f%%", display.efficiency() * 100.0f))
                    .withStyle(ChatFormatting.GRAY));
            String pathogens = display.pathogens().isBlank()
                    ? Component.translatable("item.bioforge.cas_module.pathogens.any").getString()
                    : display.pathogens();
            tooltip.add(Component.translatable("item.bioforge.cas_module.pathogens", pathogens)
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable("item.bioforge.cas_module.tooltip")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.cas_module.cycle")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static CompoundTag data(ItemStack stack) {
        if (!net.jenkimods.bioforge.util.StackData.has(stack)) return new CompoundTag();
        CompoundTag root = net.jenkimods.bioforge.util.StackData.copy(stack);
        CompoundTag hidden = NbtObfuscator.readCompound(root, CHANNEL);
        if (hidden != null) return hidden;
        CompoundTag legacy = new CompoundTag();
        if (root.contains(MODULE_TAG)) legacy.putString(MODULE_TAG, root.getString(MODULE_TAG));
        if (root.contains(PAM_TAG)) legacy.putString(PAM_TAG, root.getString(PAM_TAG));
        if (root.contains(EFFICIENCY_TAG)) {
            legacy.putFloat(EFFICIENCY_TAG, root.getFloat(EFFICIENCY_TAG));
        }
        if (root.contains(PATHOGENS_TAG)) {
            legacy.putString(PATHOGENS_TAG, root.getString(PATHOGENS_TAG));
        }
        return legacy;
    }

    private static void writeData(ItemStack stack, CompoundTag data) {
        net.jenkimods.bioforge.util.StackData.update(stack, root -> {
            root.remove(MODULE_TAG);
            root.remove(PAM_TAG);
            root.remove(EFFICIENCY_TAG);
            root.remove(PATHOGENS_TAG);
        });
        NbtObfuscator.writeCompound(stack, CHANNEL, data);
    }
}
