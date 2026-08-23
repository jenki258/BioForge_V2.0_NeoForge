package net.jenkimods.bioforge.infection.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.jenkimods.bioforge.infection.InfectionType;
import net.jenkimods.bioforge.infection.spread.AirborneReservoirManager;
import net.jenkimods.bioforge.infection.spread.SurfaceContaminationData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public final class DecontaminationCommand {
    private DecontaminationCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bioforge")
                .then(Commands.literal("decontaminate")
                        .requires(source -> source.hasPermission(2))
                        .then(route("airborne", InfectionType.AIR_BORNE))
                        .then(route("contact", InfectionType.CONTACT_BASED))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> route(String name,
                                                                     InfectionType type) {
        return Commands.literal(name)
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                        .executes(context -> clean(context.getSource(),
                                BlockPos.containing(context.getSource().getPosition()),
                                IntegerArgumentType.getInteger(context, "radius"), type)))
                .then(Commands.literal("at")
                        .then(Commands.argument("center", BlockPosArgument.blockPos())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                        .executes(context -> clean(context.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context, "center"),
                                                IntegerArgumentType.getInteger(context, "radius"), type)))));
    }

    private static int clean(CommandSourceStack source, BlockPos center, int radius,
                             InfectionType type) {
        ServerLevel level = source.getLevel();
        int cleaned = SurfaceContaminationData.get(level).cleanTransmission(center, radius,
                type, level.getGameTime());
        if (type == InfectionType.AIR_BORNE) {
            AirborneReservoirManager.reduce(level, center, radius, 1.0F);
        }
        source.sendSuccess(() -> Component.translatable(
                "command.bioforge.decontaminate.success",
                type.name().toLowerCase(java.util.Locale.ROOT),
                cleaned, radius, center.getX(), center.getY(), center.getZ()), true);
        return Math.max(1, cleaned);
    }
}
