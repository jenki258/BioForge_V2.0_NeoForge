package net.jenkimods.bioforge.item.vaccine;

import net.jenkimods.bioforge.vaccine.DirectedVaccineManager;
import net.jenkimods.bioforge.vaccine.DirectedVaccineProfile;
import net.jenkimods.bioforge.vaccine.VaccineManager;
import net.jenkimods.bioforge.vaccine.VaccineHostProfile;
import net.jenkimods.bioforge.vaccine.VaccineBloodAssay;
import net.jenkimods.bioforge.vaccine.VaccineProfile;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.mutation.MutationManager;
import net.jenkimods.bioforge.mutation.MutationDefinition;
import net.jenkimods.bioforge.mutation.MutationLoader;
import net.jenkimods.bioforge.mutation.network.MutationNetworkHandler;
import net.jenkimods.bioforge.mutation.network.MutationSlotPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;





public class VaccineItem extends Item {
    public enum Kind {
        FULL,
        MUTATION,
        TRANSMISSION,
        SYMPTOM,
        RANDOM_MUTATION,
        RANDOM_MUTATION_UPGRADE
    }

    private final Kind kind;

    public VaccineItem() {
        this(Kind.FULL);
    }

    public VaccineItem(Kind kind) {
        super(new Properties().stacksTo(1));
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public static Kind kindOf(ItemStack stack) {
        if (stack.getItem() instanceof VaccineItem vaccine) return vaccine.getKind();
        return null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isConfigured(stack)) return InteractionResultHolder.pass(stack);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(otherHand);
        if (otherStack.getItem()
                instanceof net.jenkimods.bioforge.item.samples.TubeItem
                && net.jenkimods.bioforge.item.samples.TubeItem.hasBlood(otherStack)) {
            if (!VaccineBloodAssay.canCreate(otherStack, stack)) {
                if (!level.isClientSide()) {
                    player.displayClientMessage(Component.translatable(
                            "message.bioforge.vaccine.assay_invalid")
                            .withStyle(ChatFormatting.RED), true);
                }
                return InteractionResultHolder.fail(stack);
            }
            if (!level.isClientSide()) {
                VaccineBloodAssay.createAndConsume(player, otherStack, stack);
                player.level().playSound(null, player.blockPosition(),
                        SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS,
                        0.8F, 1.15F);
                player.displayClientMessage(Component.translatable(
                        "message.bioforge.vaccine.assay_created")
                        .withStyle(ChatFormatting.AQUA), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (!level.isClientSide()) {
            applyDose(player, player, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                   LivingEntity target, InteractionHand hand) {
        if (!isConfigured(stack)) return InteractionResult.PASS;
        if (!player.level().isClientSide()) {
            applyDose(player, target, stack);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide());
    }

    private void applyDose(Player practitioner, LivingEntity target, ItemStack stack) {
        if (kind == Kind.RANDOM_MUTATION) {
            applyRandomMutationDose(practitioner, target, stack);
            return;
        }
        if (kind == Kind.RANDOM_MUTATION_UPGRADE) {
            applyRandomMutationUpgradeDose(practitioner, target, stack);
            return;
        }
        DirectedVaccineProfile directed = DirectedVaccineProfile.read(stack);
        if (directed != null) {
            applyDirectedDose(practitioner, target, stack, directed);
            return;
        }
        VaccineProfile profile = VaccineProfile.read(stack);
        VaccineManager.AttemptResult result =
                VaccineManager.attemptVaccination(
                        target, profile, VaccineHostProfile.read(stack));

        Component feedback = switch (result.outcome()) {
            case CURED -> Component.translatable("message.bioforge.vaccine.cured_hidden",
                    target.getDisplayName()).withStyle(ChatFormatting.GREEN);
            case IMMUNIZED -> Component.translatable("message.bioforge.vaccine.immunized_hidden",
                    target.getDisplayName()).withStyle(ChatFormatting.AQUA);
            case RESISTED -> Component.translatable("message.bioforge.vaccine.resisted_hidden",
                    target.getDisplayName()).withStyle(ChatFormatting.GOLD);
            case MISMATCH -> Component.translatable("message.bioforge.vaccine.mismatch_hidden",
                    target.getDisplayName()).withStyle(ChatFormatting.RED);
            case NO_INFECTION -> Component.translatable("message.bioforge.vaccine.no_infection",
                    target.getDisplayName()).withStyle(ChatFormatting.GRAY);
            case INVALID_VACCINE -> Component.translatable("message.bioforge.vaccine.invalid")
                    .withStyle(ChatFormatting.RED);
        };
        practitioner.displayClientMessage(feedback, true);

        if (result.defenseMutationApplied()) {
            practitioner.sendSystemMessage(Component.translatable(
                    "message.bioforge.vaccine.defense_mutation", target.getDisplayName())
                    .withStyle(ChatFormatting.DARK_RED));
        }

        if (!result.consumesDose()) return;
        practitioner.getCooldowns().addCooldown(this, 20);
        practitioner.level().playSound(null, target.blockPosition(),
                result.outcome() == VaccineManager.Outcome.CURED
                        || result.outcome() == VaccineManager.Outcome.IMMUNIZED
                        ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.BOTTLE_EMPTY,
                SoundSource.PLAYERS, 0.8f,
                result.outcome() == VaccineManager.Outcome.CURED
                        || result.outcome() == VaccineManager.Outcome.IMMUNIZED ? 1.25f : 0.8f);
        if (!practitioner.getAbilities().instabuild) {
            VaccineProfile.consumeDose(stack);
        }
    }

    private void applyRandomMutationDose(Player practitioner, LivingEntity target,
                                         ItemStack stack) {
        VaccineProfile profile = VaccineProfile.read(stack);
        InfectionData infection = InfectionCapability.get(target);
        if (profile == null || infection == null || !infection.isInfected()
                || infection.getPathogenId() == null
                || !Objects.equals(profile.strain().getPathogenId(), infection.getPathogenId())) {
            practitioner.displayClientMessage(Component.translatable(
                    "message.bioforge.random_mutation_vaccine.mismatch",
                    target.getDisplayName()).withStyle(ChatFormatting.RED), true);
            return;
        }
        String mutationId = MutationManager.getRandomMutationId(
                infection, new Random(target.getRandom().nextLong()));
        MutationManager.ApplyResult result = mutationId == null
                ? MutationManager.ApplyResult.UNKNOWN
                : MutationManager.applyMutation(infection, target, mutationId);
        if (result != MutationManager.ApplyResult.APPLIED) {
            practitioner.displayClientMessage(Component.translatable(
                    "message.bioforge.random_mutation_vaccine.no_target",
                    target.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
            return;
        }
        ServerPlayer practitionerPlayer = practitioner instanceof ServerPlayer serverPlayer
                ? serverPlayer : null;
        if (practitionerPlayer != null) {
            MutationNetworkHandler.sendToPlayer(
                    MutationSlotPacket.forMutation(mutationId), practitionerPlayer);
        }
        if (target instanceof ServerPlayer targetPlayer && targetPlayer != practitionerPlayer) {
            MutationNetworkHandler.sendToPlayer(
                    MutationSlotPacket.forMutation(mutationId), targetPlayer);
        }
        practitioner.displayClientMessage(Component.translatable(
                "message.bioforge.random_mutation_vaccine.applied",
                target.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE), true);
        practitioner.getCooldowns().addCooldown(this, 20);
        practitioner.level().playSound(null, target.blockPosition(),
                SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS,
                0.8f, 0.75f);
        if (!practitioner.getAbilities().instabuild) VaccineProfile.consumeDose(stack);
    }

    private void applyRandomMutationUpgradeDose(Player practitioner, LivingEntity target,
                                                ItemStack stack) {
        VaccineProfile profile = VaccineProfile.read(stack);
        InfectionData infection = InfectionCapability.get(target);
        if (profile == null || infection == null || !infection.isInfected()
                || infection.getPathogenId() == null
                || !Objects.equals(profile.strain().getPathogenId(), infection.getPathogenId())) {
            practitioner.displayClientMessage(Component.translatable(
                    "message.bioforge.mutation_upgrade_vaccine.mismatch",
                    target.getDisplayName()).withStyle(ChatFormatting.RED), true);
            return;
        }

        List<MutationDefinition> candidates = new ArrayList<>();
        for (String mutationId : new ArrayList<>(infection.getSymptoms().getMutations())) {
            MutationDefinition current = MutationLoader.INSTANCE
                    .getMutation(mutationId).orElse(null);
            if (current == null || current.upgradeTo().isEmpty()) continue;
            MutationDefinition upgrade = MutationLoader.INSTANCE
                    .getMutation(current.upgradeTo()).orElse(null);
            if (upgrade == null || !upgrade.enabled()
                    || !upgrade.isCompatible(infection.getPathogenId())
                    || MutationManager.hasMutation(infection, upgrade.id())) continue;
            candidates.add(current);
        }
        if (candidates.isEmpty()) {
            practitioner.displayClientMessage(Component.translatable(
                    "message.bioforge.mutation_upgrade_vaccine.no_target",
                    target.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
            return;
        }

        MutationDefinition current = candidates.get(
                target.getRandom().nextInt(candidates.size()));
        MutationDefinition upgrade = MutationLoader.INSTANCE
                .getMutation(current.upgradeTo()).orElse(null);
        if (upgrade == null || MutationManager.applyMutation(
                upgrade, infection, target, true) != MutationManager.ApplyResult.APPLIED) {
            practitioner.displayClientMessage(Component.translatable(
                    "message.bioforge.mutation_upgrade_vaccine.failed",
                    target.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
            return;
        }
        if (MutationManager.hasMutation(infection, current.id())) {
            MutationManager.removeMutation(infection, target, current.id());
        }
        ServerPlayer practitionerPlayer = practitioner instanceof ServerPlayer serverPlayer
                ? serverPlayer : null;
        if (practitionerPlayer != null) {
            MutationNetworkHandler.sendToPlayer(
                    MutationSlotPacket.forMutation(upgrade.id()), practitionerPlayer);
        }
        if (target instanceof ServerPlayer targetPlayer && targetPlayer != practitionerPlayer) {
            MutationNetworkHandler.sendToPlayer(
                    MutationSlotPacket.forMutation(upgrade.id()), targetPlayer);
        }
        practitioner.displayClientMessage(Component.translatable(
                "message.bioforge.mutation_upgrade_vaccine.applied",
                target.getDisplayName(), Component.translatable(current.nameKey()),
                Component.translatable(upgrade.nameKey()))
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        practitioner.getCooldowns().addCooldown(this, 20);
        practitioner.level().playSound(null, target.blockPosition(),
                SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS,
                0.9F, 1.2F);
        if (!practitioner.getAbilities().instabuild) VaccineProfile.consumeDose(stack);
    }

    private void applyDirectedDose(Player practitioner, LivingEntity target, ItemStack stack,
                                   DirectedVaccineProfile profile) {
        DirectedVaccineManager.AttemptResult result =
                DirectedVaccineManager.attempt(
                        target, profile, VaccineHostProfile.read(stack));
        Component feedback = switch (result.outcome()) {
            case APPLIED -> Component.translatable(
                    "message.bioforge.directed_vaccine.applied_hidden",
                    target.getDisplayName())
                    .withStyle(ChatFormatting.GREEN);
            case RESISTED -> Component.translatable(
                    "message.bioforge.directed_vaccine.resisted_hidden",
                    target.getDisplayName())
                    .withStyle(ChatFormatting.GOLD);
            case MISMATCH -> Component.translatable(
                    "message.bioforge.directed_vaccine.mismatch_hidden",
                    target.getDisplayName())
                    .withStyle(ChatFormatting.RED);
            case NO_TARGET -> Component.translatable(
                    "message.bioforge.directed_vaccine.no_target_hidden")
                    .withStyle(ChatFormatting.GRAY);
            case NO_INFECTION -> Component.translatable("message.bioforge.vaccine.no_infection",
                    target.getDisplayName()).withStyle(ChatFormatting.GRAY);
            case INVALID_VACCINE -> Component.translatable("message.bioforge.vaccine.invalid")
                    .withStyle(ChatFormatting.RED);
        };
        practitioner.displayClientMessage(feedback, true);
        if (result.defenseMutationApplied()) {
            practitioner.sendSystemMessage(Component.translatable(
                    "message.bioforge.vaccine.defense_mutation", target.getDisplayName())
                    .withStyle(ChatFormatting.DARK_RED));
        }
        if (!result.consumesDose()) return;
        practitioner.getCooldowns().addCooldown(this, 20);
        practitioner.level().playSound(null, target.blockPosition(),
                result.outcome() == DirectedVaccineManager.Outcome.APPLIED
                        ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.BOTTLE_EMPTY,
                SoundSource.PLAYERS, 0.8f,
                result.outcome() == DirectedVaccineManager.Outcome.APPLIED ? 1.2f : 0.75f);
        if (!practitioner.getAbilities().instabuild) {
            DirectedVaccineProfile.consumeDose(stack);
        }
    }

    private boolean isConfigured(ItemStack stack) {
        return VaccineProfile.read(stack) != null || DirectedVaccineProfile.read(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        VaccineProfile profile = VaccineProfile.read(stack);
        DirectedVaccineProfile directed = DirectedVaccineProfile.read(stack);
        VaccineHostProfile host = VaccineHostProfile.read(stack);
        if ((kind == Kind.RANDOM_MUTATION || kind == Kind.RANDOM_MUTATION_UPGRADE)
                && profile != null) {
            tooltip.add(Component.translatable(
                    kind == Kind.RANDOM_MUTATION
                            ? "item.bioforge.random_mutation_vaccine.encoded"
                            : "item.bioforge.mutation_upgrade_vaccine.encoded")
                    .withStyle(ChatFormatting.DARK_PURPLE));
            tooltip.add(Component.translatable("item.bioforge.vaccine.uses",
                    profile.remainingUses()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.bioforge.vaccine.assay_hint")
                    .withStyle(ChatFormatting.DARK_AQUA));
            tooltip.add(Component.translatable(
                    kind == Kind.RANDOM_MUTATION
                            ? "item.bioforge.random_mutation_vaccine.warning"
                            : "item.bioforge.mutation_upgrade_vaccine.warning")
                    .withStyle(ChatFormatting.DARK_RED));
            return;
        }
        if (directed != null) {
            tooltip.add(Component.translatable("item.bioforge.directed_vaccine.encoded")
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("item.bioforge.vaccine.uses",
                    directed.remainingUses()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.bioforge.vaccine.assay_hint")
                    .withStyle(ChatFormatting.DARK_AQUA));
            appendHostProfile(tooltip, host);
            tooltip.add(Component.translatable("item.bioforge.vaccine.failure_hint")
                    .withStyle(ChatFormatting.DARK_RED));
            return;
        }
        if (profile == null) {
            tooltip.add(Component.translatable("item.bioforge.vaccine.empty")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.bioforge.vaccine.empty_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        tooltip.add(Component.translatable("item.bioforge.vaccine.encoded")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.bioforge.vaccine.uses",
                        profile.remainingUses())
                .withStyle(ChatFormatting.GRAY));
        appendHostProfile(tooltip, host);
        tooltip.add(Component.translatable("item.bioforge.vaccine.use")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.vaccine.match_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.vaccine.assay_hint")
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("item.bioforge.vaccine.immunity_hint")
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("item.bioforge.vaccine.strength_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.bioforge.vaccine.failure_hint")
                .withStyle(ChatFormatting.DARK_RED));
    }

    private static void appendHostProfile(List<Component> tooltip,
                                          @Nullable VaccineHostProfile host) {
        if (host == null || !host.bloodVerified() || host.bloodType() == null) {
            tooltip.add(Component.translatable("item.bioforge.vaccine.host.unknown")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.bioforge.vaccine.host.profile",
                host.bloodType().getDisplayNameComponent()).withStyle(ChatFormatting.DARK_AQUA));
    }
}
