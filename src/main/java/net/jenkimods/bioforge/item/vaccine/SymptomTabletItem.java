package net.jenkimods.bioforge.item.vaccine;

import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.crispr.CrisprDisplayNames;
import net.jenkimods.bioforge.crispr.VaccineTargetCategory;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.infection.symptoms.SymptomSuppression;
import net.jenkimods.bioforge.vaccine.SymptomTabletProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SymptomTabletItem extends Item {
    public SymptomTabletItem() {
        super(new Properties().stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        SymptomTabletProfile profile = SymptomTabletProfile.read(stack);
        if (profile == null || !BioForgeServerConfig.isSymptomEnabled(profile.symptomId())) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable(
                        "message.bioforge.symptom_tablet.blank")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level,
                                     LivingEntity entity) {
        SymptomTabletProfile profile = SymptomTabletProfile.read(stack);
        if (!level.isClientSide() && profile != null) {
            InfectionData infection = InfectionCapability.get(entity);
            float strength = infection == null || !infection.isInfected() ? 0.0F
                    : Math.max(0.0F, infection.getSymptom(BioForgeSymptoms.INFECTION_STRENGTH));
            float resistance = infection != null
                    && infection.getSymptoms().hasMutation("analgesic_resistance")
                    ? 0.35F : 1.0F;
            int duration = Math.max(20, Math.round(profile.durationTicks()
                    * (0.55F + profile.quality() * 0.45F) * resistance
                    / (1.0F + strength * 0.65F)));
            SymptomSuppression.suppress(entity, profile.symptomId(), duration);
            level.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_EAT,
                    SoundSource.PLAYERS, 0.7F, 1.25F);
            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable(
                        "message.bioforge.symptom_tablet.applied",
                        CrisprDisplayNames.target(VaccineTargetCategory.SYMPTOM,
                                profile.symptomId()), StringUtil.formatTickDuration(duration, 20.0F))
                        .withStyle(ChatFormatting.AQUA), true);
                player.getCooldowns().addCooldown(this, 40);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
        }
        return stack;
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 24; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.EAT; }
    @Override public boolean isFoil(ItemStack stack) {
        return SymptomTabletProfile.read(stack) != null || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        SymptomTabletProfile profile = SymptomTabletProfile.read(stack);
        if (profile == null) {
            tooltip.add(Component.translatable("item.bioforge.symptom_tablet.blank")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.bioforge.symptom_tablet.vaccine_maker")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(CrisprDisplayNames.target(VaccineTargetCategory.SYMPTOM,
                profile.symptomId()).copy().withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.bioforge.symptom_tablet.duration",
                StringUtil.formatTickDuration(profile.durationTicks(), 20.0F))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.bioforge.symptom_tablet.not_cure")
                .withStyle(ChatFormatting.DARK_RED));
    }
}
