package net.jenkimods.bioforge.definition;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.infection.InfectionEventHandler;
import net.jenkimods.bioforge.infection.InfectionInvulnerability;
import net.jenkimods.bioforge.infection.InfectionStore;
import net.jenkimods.bioforge.infection.InfectionType;
import net.jenkimods.bioforge.infection.PathogenType;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BioForgeDefinitionCommand {
    private BioForgeDefinitionCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bioforge")
                .then(Commands.literal("definition")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("infect")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("pathogen", ResourceLocationArgument.id())
                                                .suggests((context, builder) -> suggest(
                                                        BioForgeDefinitionManager.PATHOGENS.ids(), builder))
                                                .then(Commands.argument("transmissions", StringArgumentType.string())
                                                        .suggests((context, builder) -> suggest(
                                                                BioForgeDefinitionManager.TRANSMISSIONS.ids(), builder))
                                                        .executes(context -> infect(context.getSource(),
                                                                EntityArgument.getEntities(context, "targets"),
                                                                ResourceLocationArgument.getId(context, "pathogen"),
                                                                StringArgumentType.getString(context, "transmissions"), false))
                                                        .then(Commands.argument("persistent", BoolArgumentType.bool())
                                                                .executes(context -> infect(context.getSource(),
                                                                        EntityArgument.getEntities(context, "targets"),
                                                                        ResourceLocationArgument.getId(context, "pathogen"),
                                                                        StringArgumentType.getString(context, "transmissions"),
                                                                        BoolArgumentType.getBool(context, "persistent"))))))))));
    }

    private static int infect(CommandSourceStack source, Collection<? extends Entity> targets,
                              ResourceLocation pathogenId, String transmissionList,
                              boolean persistent) {
        pathogenId = BioForgeDefinitionManager.PATHOGENS.canonicalId(pathogenId);
        if (BioForgeDefinitionManager.PATHOGENS.get(pathogenId).isEmpty()) {
            source.sendFailure(Component.translatable(
                    "command.bioforge.definition.unknown_pathogen", pathogenId));
            return 0;
        }
        Set<ResourceLocation> transmissions = parseTransmissions(transmissionList);
        for (ResourceLocation transmission : transmissions) {
            if (BioForgeDefinitionManager.TRANSMISSIONS.get(transmission).isEmpty()) {
                source.sendFailure(Component.translatable(
                        "command.bioforge.definition.unknown_transmission", transmission));
                return 0;
            }
            if (!BioForgeDefinitionManager.allowsTransmission(pathogenId, transmission)) {
                source.sendFailure(Component.translatable(
                        "command.bioforge.definition.disallowed_transmission",
                        pathogenId, transmission));
                return 0;
            }
        }
        int changed = 0;
        for (Entity entity : targets) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (InfectionInvulnerability.isEnabled(living)) {
                if (living instanceof ServerPlayer player) InfectionInvulnerability.ensureCured(player);
                source.sendFailure(Component.translatable("command.bioforge.infectionimmune.blocked",
                        living.getDisplayName()));
                continue;
            }
            InfectionData data = InfectionCapability.get(living);
            if (data == null) continue;
            data.clearInfection();
            data.setInfected(true);
            data.setPathogenId(pathogenId);
            transmissions.forEach(data::addTransmissionId);
            BioForgeSymptoms.applyDefaultSymptoms(data);
            if (living instanceof ServerPlayer player) {
                if (persistent) persist(player, data);
                else InfectionStore.get(player.serverLevel()).clearInfection(player.getUUID());
                InfectionEventHandler.syncToClient(player, data);
            }
            changed++;
        }
        int result = changed;
        ResourceLocation finalPathogenId = pathogenId;
        source.sendSuccess(() -> Component.translatable(
                "command.bioforge.definition.applied", finalPathogenId, result), true);
        return changed;
    }

    private static void persist(ServerPlayer player, InfectionData data) {
        Map<String, Object> symptoms = new LinkedHashMap<>();
        BioForgeSymptoms.getEnabledSymptomKeys().forEach((id, key) ->
                symptoms.put(id, data.getSymptoms().get(key)));
        PathogenType legacyPathogen = data.getPathogenType();
        List<InfectionType> legacyTransmissions = new ArrayList<>(data.getInfectionTypes());
        InfectionStore.get(player.serverLevel()).setInfection(player.getUUID(),
                new InfectionStore.InfectionRecord(true, true, legacyPathogen,
                        legacyTransmissions, symptoms,
                        new ArrayList<>(data.getSymptoms().getMutations()), data.getPathogenId(),
                        new ArrayList<>(data.getTransmissionIds())));
    }

    private static Set<ResourceLocation> parseTransmissions(String value) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (String part : value.split(",")) {
            if (part.isBlank()) continue;
            ResourceLocation id = BioForgeIds.parse(part);
            result.add(BioForgeDefinitionManager.TRANSMISSIONS.canonicalId(id));
        }
        return result;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(
            Collection<ResourceLocation> ids, SuggestionsBuilder builder) {
        ids.stream().map(ResourceLocation::toString).sorted().forEach(builder::suggest);
        return builder.buildFuture();
    }
}
