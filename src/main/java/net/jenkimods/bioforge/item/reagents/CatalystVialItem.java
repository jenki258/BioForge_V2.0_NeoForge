package net.jenkimods.bioforge.item.reagents;

import net.jenkimods.bioforge.infection.PathogenType;
import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.jenkimods.bioforge.definition.BioForgeClientDefinitionCache;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.world.incubator.CatalystMappingManager;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class CatalystVialItem extends Item {

    private static final int MAX_CHARGES = 1;
    private static final String CHANNEL = "catalyst_vial";
    private static final String PATHOGEN_KEY = "Pathogen";
    private static final String CHARGES_KEY = "Charges";

    public CatalystVialItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.pass(stack);

        if (isSet(stack)) {
            player.sendSystemMessage(Component.translatable("item.bioforge.catalyst_vial.already_set"));
            return InteractionResultHolder.fail(stack);
        }

        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack reagent = player.getItemInHand(other);
        if (reagent.isEmpty()) return InteractionResultHolder.fail(stack);

        ResourceLocation pathogen = CatalystMappingManager.INSTANCE.getPathogenId(reagent.getItem());
        String pathogenName;

        if (reagent.getItem() == net.minecraft.world.item.Items.NETHER_STAR) {
            pathogenName = "RANDOM";
            if (!player.isCreative()) reagent.shrink(1);
            player.sendSystemMessage(Component.translatable("item.bioforge.catalyst_vial.set_random"));
        } else if (pathogen != null) {
            pathogenName = BioForgeIds.legacyCompatible(pathogen);
            if (!player.isCreative()) reagent.shrink(1);
            player.sendSystemMessage(Component.translatable("item.bioforge.catalyst_vial.set", pathogenName));
        } else {
            player.sendSystemMessage(Component.translatable("item.bioforge.catalyst_vial.invalid_reagent"));
            return InteractionResultHolder.fail(stack);
        }

        set(stack, pathogenName, MAX_CHARGES);
        return InteractionResultHolder.success(stack);
    }

    public static boolean isSet(ItemStack stack) {
        CompoundTag data = data(stack);
        return data != null && data.contains(PATHOGEN_KEY);
    }

    @Nullable
    public static PathogenType getPathogen(ItemStack stack) {
        ResourceLocation id = getPathogenId(stack);
        if (id == null) return null;
        PathogenType legacy = BioForgeIds.legacyPathogen(id);
        return legacy == null ? PathogenType.UNIVERSAL : legacy;
    }

    @Nullable
    public static ResourceLocation getPathogenId(ItemStack stack) {
        CompoundTag data = data(stack);
        if (data == null || !data.contains(PATHOGEN_KEY)) return null;
        String name = data.getString(PATHOGEN_KEY);
        if ("RANDOM".equals(name)) return null;
        try {
            return BioForgeIds.parse(name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @Nullable
    public static PathogenType getPathogenOrRandom(ItemStack stack) {
        ResourceLocation id = getPathogenIdOrRandom(stack);
        if (id == null) return null;
        PathogenType legacy = BioForgeIds.legacyPathogen(id);
        return legacy == null ? PathogenType.UNIVERSAL : legacy;
    }

    @Nullable
    public static ResourceLocation getPathogenIdOrRandom(ItemStack stack) {
        CompoundTag data = data(stack);
        if (data != null && data.contains(PATHOGEN_KEY)) {
            String name = data.getString(PATHOGEN_KEY);
            if ("RANDOM".equals(name)) {
                return CatalystMappingManager.INSTANCE.getRandomPathogenId();
            }
            try {
                return BioForgeIds.parse(name);
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
        return null;
    }

    public static int getCharges(ItemStack stack) {
        CompoundTag data = data(stack);
        return data == null ? 0 : data.getInt(CHARGES_KEY);
    }

    public static void consumeCharge(ItemStack stack) {
        CompoundTag data = data(stack);
        if (data != null) {
            int charges = data.getInt(CHARGES_KEY) - 1;
            if (charges <= 0) {
                stack.shrink(1);
            } else {
                set(stack, data.getString(PATHOGEN_KEY), charges);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (!isSet(stack)) {
            tooltip.add(Component.translatable("item.bioforge.catalyst_vial.empty").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal(" "));
            tooltip.add(Component.translatable("item.bioforge.catalyst_vial.tooltip").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal(" "));
            tooltip.add(Component.translatable("item.bioforge.catalyst_vial.mappings_header").withStyle(ChatFormatting.GOLD));

            Map<Item, ResourceLocation> mappings = CatalystMappingManager.INSTANCE.getAllMappingIds();
            for (Map.Entry<Item, ResourceLocation> entry : mappings.entrySet()) {
                String itemName = Component.translatable(entry.getKey().getDescriptionId()).getString();
                String pathogenName = pathogenName(entry.getValue()).getString();
                tooltip.add(Component.literal("  " + itemName + " → " + pathogenName).withStyle(ChatFormatting.DARK_GRAY));
            }
            tooltip.add(Component.translatable("item.bioforge.catalyst_vial.nether_star_hint").withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }

        CompoundTag data = data(stack);
        String pathogenName = data == null ? "" : data.getString(PATHOGEN_KEY);
        int charges = getCharges(stack);
        if ("RANDOM".equals(pathogenName)) {
            tooltip.add(Component.translatable("item.bioforge.catalyst_vial.pathogen_random").withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            ResourceLocation pathogenId = getPathogenId(stack);
            tooltip.add(Component.translatable("item.bioforge.catalyst_vial.pathogen",
                    pathogenId == null ? Component.literal(pathogenName)
                            : pathogenName(pathogenId)).withStyle(ChatFormatting.DARK_PURPLE));
        }
        tooltip.add(Component.literal(" "));
        tooltip.add(Component.translatable("item.bioforge.catalyst_vial.place_in_incubator").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component pathogenName(ResourceLocation id) {
        BioForgeClientDefinitionCache.PathogenView client =
                BioForgeClientDefinitionCache.snapshot().pathogens().get(id);
        String key = client != null ? client.translationKey()
                : BioForgeDefinitionManager.pathogen(id)
                .map(definition -> definition.translationKey())
                .orElse("pathogen." + id.getNamespace() + "." + id.getPath());
        return Component.translatable(key);
    }

    public static void setPathogen(ItemStack stack, ResourceLocation pathogen) {
        if (pathogen != null) set(stack, pathogen.toString(), MAX_CHARGES);
    }

    public static void setRandom(ItemStack stack) {
        set(stack, "RANDOM", MAX_CHARGES);
    }

    @Nullable
    private static CompoundTag data(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !net.jenkimods.bioforge.util.StackData.has(stack)) return null;
        CompoundTag root = net.jenkimods.bioforge.util.StackData.copy(stack);
        CompoundTag hidden = NbtObfuscator.readCompound(root, CHANNEL);
        if (hidden != null) return hidden;
        if (!root.contains(PATHOGEN_KEY)) return null;
        CompoundTag legacy = new CompoundTag();
        legacy.putString(PATHOGEN_KEY, root.getString(PATHOGEN_KEY));
        legacy.putInt(CHARGES_KEY, root.getInt(CHARGES_KEY));
        return legacy;
    }

    private static void set(ItemStack stack, String pathogen, int charges) {
        CompoundTag data = new CompoundTag();
        data.putString(PATHOGEN_KEY, pathogen);
        data.putInt(CHARGES_KEY, Math.max(0, charges));
        net.jenkimods.bioforge.util.StackData.update(stack, root -> {
            root.remove(PATHOGEN_KEY);
            root.remove(CHARGES_KEY);
        });
        NbtObfuscator.writeCompoundDeterministic(stack, CHANNEL, data);
    }
}
