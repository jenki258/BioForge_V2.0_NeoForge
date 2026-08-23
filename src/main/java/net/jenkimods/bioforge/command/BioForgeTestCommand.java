package net.jenkimods.bioforge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.api.guide.ResearchJournalRegistry;
import net.jenkimods.bioforge.blood.BloodType;
import net.jenkimods.bioforge.crispr.BioForgeResearchData;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.item.BloodSampleUtil;
import net.jenkimods.bioforge.mutation.MutationLoader;
import net.jenkimods.bioforge.world.centrifuge.CentrifugeRecipeManager;
import net.jenkimods.bioforge.world.decalcification.DecalcificationRecipeManager;
import net.jenkimods.bioforge.world.laboratory.LaboratoryProcessRecipeManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class BioForgeTestCommand {
    private static final int DEFAULT_ITERATIONS = 10_000;

    private BioForgeTestCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bioforge")
                .then(Commands.literal("test")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("report")
                                .executes(context -> report(context.getSource())))
                        .then(Commands.literal("stress")
                                .executes(context -> stress(context.getSource(),
                                        DEFAULT_ITERATIONS))
                                .then(Commands.argument("iterations",
                                                IntegerArgumentType.integer(100, 50_000))
                                        .executes(context -> stress(context.getSource(),
                                                IntegerArgumentType.getInteger(
                                                        context, "iterations")))))
                        .then(Commands.literal("blood_tubes")
                                .executes(context -> giveBloodTubes(context.getSource(),
                                        context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> giveBloodTubes(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("all")
                                .executes(context -> runAll(context.getSource())))));
    }

    private static int runAll(CommandSourceStack source) {
        int report = report(source);
        int stress = stress(source, 5_000);
        source.sendSuccess(() -> Component.translatable(report > 0 && stress > 0
                ? "command.bioforge.test.full_passed"
                : "command.bioforge.test.full_failed"), false);
        return report > 0 && stress > 0 ? 1 : 0;
    }

    private static int report(CommandSourceStack source) {
        List<String> issues = BioForgeDefinitionManager.validateCurrent();
        int pathogens = BioForgeDefinitionManager.PATHOGENS.ids().size();
        int transmissions = BioForgeDefinitionManager.TRANSMISSIONS.ids().size();
        int symptoms = BioForgeDefinitionManager.SYMPTOMS.ids().size();
        int mutations = MutationLoader.INSTANCE.getAllMutations().size();
        int recipes = BioForgeResearchData.recipes().size()
                + LaboratoryProcessRecipeManager.INSTANCE.recipes().size()
                + CentrifugeRecipeManager.INSTANCE.getRecipes().size()
                + DecalcificationRecipeManager.INSTANCE.getRecipes().size();
        int pages = ResearchJournalRegistry.pages().size();
        source.sendSuccess(() -> Component.translatable(
                "command.bioforge.test.data", pathogens, transmissions, symptoms,
                mutations, recipes, pages), false);
        if (!issues.isEmpty() || !BioForgeDefinitionManager.lastReloadSuccessful()) {
            source.sendFailure(Component.translatable(
                    "command.bioforge.test.validation_failed", issues.size()));
            issues.stream().limit(10).forEach(issue ->
                    source.sendFailure(Component.translatable(
                            "command.bioforge.validate.issue", issue)));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "command.bioforge.test.registries_consistent"), false);
        return 1;
    }

    private static int stress(CommandSourceStack source, int iterations) {
        BloodType[] types = BloodType.values();
        long started = System.nanoTime();
        long checksum = 0L;
        int failures = 0;
        for (int index = 0; index < iterations; index++) {
            BloodType donor = types[index % types.length];
            BloodType recipient = types[(index * 7 + 3) % types.length];
            if (donor.isCompatibleWith(recipient)) checksum += index + 1L;

            ItemStack tube = new ItemStack(BioForge.TUBE.get());
            BloodSampleUtil.setData(tube, 1, donor, "Stress Test", null);
            var stored = BloodSampleUtil.getData(tube);
            if (stored == null || stored.amount() != 1
                    || BloodType.findByName(stored.typeName()) != donor) {
                failures++;
            }
        }
        for (var pathogenId : BioForgeDefinitionManager.PATHOGENS.ids()) {
            checksum = 31L * checksum + pathogenId.hashCode();
        }
        for (var mutation : MutationLoader.INSTANCE.getAllMutations()) {
            checksum = 31L * checksum + mutation.id().hashCode();
        }
        double elapsedMs = (System.nanoTime() - started) / 1_000_000.0D;
        int finalFailures = failures;
        long finalChecksum = checksum;
        source.sendSuccess(() -> Component.translatable(
                "command.bioforge.test.stress",
                String.format("%,d", iterations), String.format("%.2f", elapsedMs),
                String.format("%.3f", elapsedMs * 1000.0D / iterations),
                finalFailures, String.format("%016X", finalChecksum)), false);
        if (failures > 0) {
            source.sendFailure(Component.translatable(
                    "command.bioforge.test.nbt_failed"));
            return 0;
        }
        return 1;
    }

    private static int giveBloodTubes(CommandSourceStack source, ServerPlayer player) {
        int given = 0;
        for (BloodType type : BloodType.values()) {
            ItemStack tube = new ItemStack(BioForge.TUBE.get());
            BloodSampleUtil.setData(tube, 1, type,
                    "BioForge QA - " + type.getDisplayName(), null);
            if (!player.getInventory().add(tube)) player.drop(tube, false);
            given++;
        }
        int finalGiven = given;
        source.sendSuccess(() -> Component.translatable(
                "command.bioforge.test.gave_tubes",
                player.getDisplayName(), finalGiven), true);
        return given;
    }
}
