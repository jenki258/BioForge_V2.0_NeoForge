package net.jenkimods.bioforge.crispr.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.crispr.BioForgeResearchData;
import net.jenkimods.bioforge.crispr.CrisprGuideProfile;
import net.jenkimods.bioforge.crispr.VaccineTargetCategory;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.StrainData;
import net.jenkimods.bioforge.item.crispr.CasModuleItem;
import net.jenkimods.bioforge.item.crispr.CrisprCartridgeItem;
import net.jenkimods.bioforge.vaccine.DirectedVaccineAction;
import net.jenkimods.bioforge.vaccine.DirectedVaccineProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.UUID;

public final class CrisprCommand {
    private CrisprCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bioforge")
                .then(Commands.literal("crispr")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("definitions")
                                .executes(context -> definitions(context.getSource())))
                        .then(Commands.literal("cartridge")
                                .then(Commands.argument("slot",
                                                IntegerArgumentType.integer(1, 15))
                                        .then(Commands.argument("sequence",
                                                        StringArgumentType.word())
                                                .executes(context -> cartridge(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(
                                                                context, "slot"),
                                                        StringArgumentType.getString(
                                                                context, "sequence"),
                                                        ResourceLocation.tryBuild(
                                                                BioForge.MODID, "default")))
                                                .then(Commands.argument("profile",
                                                                ResourceLocationArgument.id())
                                                        .suggests((context, builder) ->
                                                                SharedSuggestionProvider
                                                                        .suggestResource(
                                                                                BioForgeResearchData
                                                                                        .guideProfileIds(),
                                                                                builder))
                                                        .executes(context -> cartridge(
                                                                context.getSource(),
                                                                IntegerArgumentType.getInteger(
                                                                        context, "slot"),
                                                                StringArgumentType.getString(
                                                                        context, "sequence"),
                                                                ResourceLocationArgument.getId(
                                                                        context, "profile")))))))
                        .then(Commands.literal("casmake")
                                .then(Commands.argument("module",
                                                ResourceLocationArgument.id())
                                        .suggests((context, builder) ->
                                                SharedSuggestionProvider.suggestResource(
                                                        BioForgeResearchData.casModuleIds(), builder))
                                        .executes(context -> casmake(
                                                context.getSource(),
                                                ResourceLocationArgument.getId(
                                                        context, "module")))))
                        .then(Commands.literal("directedmake")
                                .then(Commands.argument("source", EntityArgument.entity())
                                        .then(Commands.argument("category",
                                                        StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    for (VaccineTargetCategory category
                                                            : VaccineTargetCategory.values()) {
                                                        builder.suggest(category.serializedName());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("target",
                                                                StringArgumentType.word())
                                                        .then(Commands.argument("action",
                                                                        ResourceLocationArgument.id())
                                                                .suggests((context, builder) ->
                                                                        SharedSuggestionProvider
                                                                                .suggestResource(
                                                                                        BioForgeResearchData
                                                                                                .actionIds(),
                                                                                        builder))
                                                                .executes(context ->
                                                                        directedmake(
                                                                                context.getSource(),
                                                                                EntityArgument
                                                                                        .getEntity(
                                                                                                context,
                                                                                                "source"),
                                                                                StringArgumentType
                                                                                        .getString(
                                                                                                context,
                                                                                                "category"),
                                                                                StringArgumentType
                                                                                        .getString(
                                                                                                context,
                                                                                                "target"),
                                                                                ResourceLocationArgument
                                                                                        .getId(
                                                                                                context,
                                                                                                "action"),
                                                                                0.75f))
                                                                .then(Commands.argument("quality",
                                                                                FloatArgumentType
                                                                                        .floatArg(
                                                                                                0.0f,
                                                                                                1.0f))
                                                                        .executes(context ->
                                                                                directedmake(
                                                                                        context.getSource(),
                                                                                        EntityArgument
                                                                                                .getEntity(
                                                                                                        context,
                                                                                                        "source"),
                                                                                        StringArgumentType
                                                                                                .getString(
                                                                                                        context,
                                                                                                        "category"),
                                                                                        StringArgumentType
                                                                                                .getString(
                                                                                                        context,
                                                                                                        "target"),
                                                                                        ResourceLocationArgument
                                                                                                .getId(
                                                                                                        context,
                                                                                                        "action"),
                                                                                        FloatArgumentType
                                                                                                .getFloat(
                                                                                                        context,
                                                                                                        "quality")))))))))));
    }

    private static int definitions(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(
                "command.bioforge.crispr.definitions",
                BioForgeResearchData.guideProfileIds().size(),
                BioForgeResearchData.casModuleIds().size(),
                BioForgeResearchData.actionIds().size(),
                BioForgeResearchData.recipes().size()), false);
        return BioForgeResearchData.recipes().size();
    }

    private static int cartridge(CommandSourceStack source, int slot, String sequence,
                                 ResourceLocation profileId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CrisprGuideProfile profile =
                BioForgeResearchData.guideProfile(profileId).orElse(null);
        String normalized = sequence.toUpperCase(Locale.ROOT);
        if (profile == null || normalized.length() != 4
                || normalized.chars().anyMatch(value ->
                value != 'N' && profile.alphabet().indexOf(value) < 0)) {
            source.sendFailure(Component.translatable(
                    "command.bioforge.crispr.invalid_cartridge", sequence, profileId));
            return 0;
        }
        ItemStack stack = new ItemStack(BioForge.CRISPR_CARTRIDGE.get());
        CrisprCartridgeItem.setSequence(stack, normalized);
        CrisprCartridgeItem.assign(stack, slot - 1, profileId);
        give(player, stack);
        source.sendSuccess(() -> Component.translatable(
                "command.bioforge.crispr.cartridge_success",
                slot, normalized, profileId), false);
        return 1;
    }

    private static int casmake(CommandSourceStack source, ResourceLocation moduleId)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (BioForgeResearchData.casModule(moduleId).isEmpty()) {
            source.sendFailure(Component.translatable(
                    "command.bioforge.crispr.unknown_module", moduleId));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = new ItemStack(BioForge.CAS_MODULE.get());
        CasModuleItem.setModuleId(stack, moduleId);
        give(player, stack);
        source.sendSuccess(() -> Component.translatable(
                "command.bioforge.crispr.cas_success", moduleId), false);
        return 1;
    }

    private static int directedmake(CommandSourceStack source, Entity sourceEntity,
                                    String categoryName, String target,
                                    ResourceLocation actionId, float quality)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer recipient = source.getPlayerOrException();
        if (!(sourceEntity instanceof LivingEntity living)) {
            source.sendFailure(Component.translatable(
                    "command.bioforge.vaccinemake.not_living",
                    sourceEntity.getDisplayName()));
            return 0;
        }
        InfectionData infection = InfectionCapability.get(living);
        if (infection == null || !infection.isInfected()
                || infection.getPathogenId() == null) {
            source.sendFailure(Component.translatable(
                    "command.bioforge.vaccinemake.not_infected",
                    living.getDisplayName()));
            return 0;
        }
        VaccineTargetCategory category =
                VaccineTargetCategory.fromName(categoryName);
        DirectedVaccineAction action =
                BioForgeResearchData.action(actionId).orElse(null);
        if (category == null || action == null || !action.supports(category)) {
            source.sendFailure(Component.translatable(
                    "command.bioforge.crispr.invalid_action",
                    categoryName, actionId));
            return 0;
        }
        Item item = switch (category) {
            case MUTATION -> BioForge.MUTATION_VACCINE.get();
            case TRANSMISSION -> BioForge.TRANSMISSION_VACCINE.get();
            case SYMPTOM -> BioForge.SYMPTOM_VACCINE.get();
        };
        String effectiveTarget = action.targetOverride().isBlank()
                ? target : action.targetOverride();
        ItemStack stack = new ItemStack(item);
        new DirectedVaccineProfile(
                StrainData.buildFrom(infection).toPayload(),
                category, effectiveTarget, actionId, quality, 1, 0.18f,
                UUID.randomUUID(), living.level().getGameTime()).write(stack);
        give(recipient, stack);
        source.sendSuccess(() -> Component.translatable(
                "command.bioforge.crispr.directed_success",
                category.serializedName(), effectiveTarget, actionId), true);
        return 1;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}
