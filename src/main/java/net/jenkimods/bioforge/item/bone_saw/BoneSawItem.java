package net.jenkimods.bioforge.item.bone_saw;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.blood.BloodCapability;
import net.jenkimods.bioforge.blood.BloodData;
import net.jenkimods.bioforge.item.BloodSampleUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class BoneSawItem extends Item {

    public static final TagKey<EntityType<?>> SKELETON_SOURCES = TagKey.create(
            net.minecraft.core.registries.Registries.ENTITY_TYPE,
            ResourceLocation.tryBuild(BioForge.MODID, "skeleton_blood_sources"));

    public static final TagKey<EntityType<?>> WITHERED_SKELETON_SOURCES = TagKey.create(
            net.minecraft.core.registries.Registries.ENTITY_TYPE,
            ResourceLocation.tryBuild(BioForge.MODID, "withered_skeleton_blood_sources"));

    private static final int COOLDOWN_TICKS = 80;
    private static final int SAMPLE_DRAIN = 10;

    public BoneSawItem() {
        super(new Properties().stacksTo(1).durability(256));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bioforge.bone_saw.tooltip.use")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.bone_saw.tooltip.withered")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    public boolean tryCollectSplitBone(ItemStack saw, LivingEntity target, ServerPlayer attacker) {
        if (attacker.getCooldowns().isOnCooldown(this)) return false;

        boolean isWithered = target.getType().is(WITHERED_SKELETON_SOURCES);
        boolean isNormal = !isWithered && target.getType().is(SKELETON_SOURCES);

        if (!isWithered && !isNormal) return false;

        BloodData data = BloodCapability.get(target);
        if (data == null || data.getBlood() <= 0) return false;

        Item boneType = isWithered ? BioForge.WITHERED_SPLIT_BONE.get() : BioForge.SPLIT_BONE.get();
        ItemStack splitBone = new ItemStack(boneType);
        BloodSampleUtil.setData(splitBone, data.getBlood(), data.getBloodType(), target.getName().getString(), target.getUUID());

        int newBlood = Math.max(0, data.getBlood() - SAMPLE_DRAIN);
        data.setBlood(newBlood);

        if (newBlood <= 0) {
            return false;
        }

        saw.hurtAndBreak(1, attacker, LivingEntity.getSlotForHand(InteractionHand.MAIN_HAND));
        attacker.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

        if (!attacker.getInventory().add(splitBone)) {
            attacker.drop(splitBone, false);
        }

        attacker.displayClientMessage(Component.translatable("item.bioforge.bone_saw.sampled"), true);
        return true;
    }
}
