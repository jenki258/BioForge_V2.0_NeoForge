package net.jenkimods.bioforge.blood.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.blood.BloodCapability;
import net.jenkimods.bioforge.blood.BloodData;
import net.jenkimods.bioforge.blood.BloodType;
import net.jenkimods.bioforge.blood.knowledge.BloodKnowledge;
import net.jenkimods.bioforge.blood.knowledge.BloodKnowledgeStore;
import net.jenkimods.bioforge.world.data.ReagentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Collection;
import java.util.UUID;

@EventBusSubscriber(modid = BioForge.MODID)
public class BloodKnowledgeCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("bioforge")
                        .then(Commands.literal("bloodknowledge_list")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeList(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("bloodknowledge_get")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("entities", EntityArgument.entities())
                                                .executes(ctx -> executeGetFromEntities(
                                                        ctx,
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        EntityArgument.getEntities(ctx, "entities"))))))
                        .then(Commands.literal("bloodknowledge_clearall")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeClearAll(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("bloodknowledge_clear")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("entry", StringArgumentType.greedyString())
                                                .suggests((ctx, builder) -> {
                                                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                    BloodKnowledgeStore store = BloodKnowledgeStore.get(ctx.getSource().getServer());
                                                    for (BloodKnowledge knowledge : store.getAllForPlayer(player.getUUID())) {
                                                        builder.suggest(knowledge.getSubjectName() + " | " + knowledge.getSubjectUUID());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> executeClearSubject(
                                                        ctx,
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "entry"))))))
        );
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        BloodKnowledgeStore store = BloodKnowledgeStore.get(ctx.getSource().getServer());
        Collection<BloodKnowledge> entries = store.getAllForPlayer(player.getUUID());
        if (entries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "command.bioforge.blood_knowledge.empty", player.getDisplayName()), false);
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.bioforge.blood_knowledge.header",
                player.getDisplayName(), entries.size()), false);

        for (BloodKnowledge entry : entries) {
            String antiA = reaction(entry.getAntiA());
            String antiB = reaction(entry.getAntiB());
            String antiD = reaction(entry.getAntiD());

            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "command.bioforge.blood_knowledge.entry",
                    entry.getSubjectName(), entry.getSubjectType(), entry.getSubjectUUID(),
                    antiA, antiB, antiD), false);
        }
        return entries.size();
    }

    private static int executeGetFromEntities(CommandContext<CommandSourceStack> ctx,
                                              ServerPlayer player,
                                              Collection<? extends Entity> entities) {
        BloodKnowledgeStore store = BloodKnowledgeStore.get(ctx.getSource().getServer());
        int updated = 0;

        for (Entity entity : entities) {
            if (!(entity.level() instanceof ServerLevel)) continue;

            BloodData bloodData = BloodCapability.get(entity);
            if (bloodData == null) continue;

            BloodType type = bloodData.getBloodType();
            if (type == null) continue;

            String subjectType = String.valueOf(entity.getType());
            boolean isSubjectPlayer = entity instanceof Player;

            store.recordReagent(player.getUUID(), entity.getUUID(),
                    entity.getName().getString(), subjectType, isSubjectPlayer,
                    type, ReagentType.ANTI_A, reactsToAntiA(type));
            store.recordReagent(player.getUUID(), entity.getUUID(),
                    entity.getName().getString(), subjectType, isSubjectPlayer,
                    type, ReagentType.ANTI_B, reactsToAntiB(type));
            store.recordReagent(player.getUUID(), entity.getUUID(),
                    entity.getName().getString(), subjectType, isSubjectPlayer,
                    type, ReagentType.ANTI_D, reactsToAntiD(type));
            updated++;
        }

        if (updated == 0) {
            ctx.getSource().sendFailure(Component.translatable(
                    "command.bioforge.blood_knowledge.no_valid_entities"));
            return 0;
        }

        int finalUpdated = updated;
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.bioforge.blood_knowledge.updated",
                finalUpdated, player.getDisplayName()), true);
        return updated;
    }

    private static int executeClearAll(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        BloodKnowledgeStore store = BloodKnowledgeStore.get(ctx.getSource().getServer());
        int removed = store.clearAllForPlayer(player.getUUID());

        if (removed == 0) {
            ctx.getSource().sendFailure(Component.translatable(
                    "command.bioforge.blood_knowledge.no_data", player.getDisplayName()));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.bioforge.blood_knowledge.cleared",
                removed, player.getDisplayName()), true);
        return removed;
    }

    private static int executeClearSubject(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String entryText) {
        BloodKnowledgeStore store = BloodKnowledgeStore.get(ctx.getSource().getServer());
        int sep = entryText.lastIndexOf('|');
        if (sep < 0) {
            ctx.getSource().sendFailure(Component.translatable(
                    "command.bioforge.blood_knowledge.invalid_format"));
            return 0;
        }

        String subjectName = entryText.substring(0, sep).trim();
        String uuidText = entryText.substring(sep + 1).trim();

        UUID subjectUUID;
        try {
            subjectUUID = UUID.fromString(uuidText);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.translatable(
                    "command.bioforge.blood_knowledge.invalid_uuid", uuidText));
            return 0;
        }

        boolean removed = store.removeForPlayer(player.getUUID(), subjectUUID);

        if (!removed) {
            ctx.getSource().sendFailure(Component.translatable(
                    "command.bioforge.blood_knowledge.missing_entry",
                    subjectName, player.getDisplayName()));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.bioforge.blood_knowledge.removed_entry",
                subjectName, player.getDisplayName()), true);
        return 1;
    }

    private static String reaction(Boolean value) {
        if (value == null) return "?";
        return value ? "+" : "-";
    }

    private static boolean reactsToAntiA(BloodType type) {
        return switch (type) {
            case A_POSITIVE, A_NEGATIVE, AB_POSITIVE, AB_NEGATIVE -> true;
            default -> false;
        };
    }

    private static boolean reactsToAntiB(BloodType type) {
        return switch (type) {
            case B_POSITIVE, B_NEGATIVE, AB_POSITIVE, AB_NEGATIVE -> true;
            default -> false;
        };
    }

    private static boolean reactsToAntiD(BloodType type) {
        return switch (type) {
            case A_POSITIVE, B_POSITIVE, AB_POSITIVE, O_POSITIVE -> true;
            default -> false;
        };
    }
}
