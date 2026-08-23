package net.jenkimods.bioforge.vaccine.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.jenkimods.bioforge.infection.InfectionCapability;
import net.jenkimods.bioforge.infection.InfectionData;
import net.jenkimods.bioforge.vaccine.VaccineProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;











public final class VaccineMakeCommand {
    public static final float DEFAULT_QUALITY = 0.75f;
    public static final int DEFAULT_USES = 1;
    public static final float DEFAULT_DEFENSE_RISK = 0.18f;

    private VaccineMakeCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                Supplier<? extends Item> vaccineItem) {
        Objects.requireNonNull(vaccineItem, "vaccineItem");
        dispatcher.register(Commands.literal("bioforge")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("vaccinemake")
                        .executes(context -> make(
                                context.getSource(),
                                context.getSource().getPlayerOrException(),
                                DEFAULT_QUALITY,
                                DEFAULT_USES,
                                DEFAULT_DEFENSE_RISK,
                                vaccineItem))
                        .then(Commands.argument("source", EntityArgument.entity())
                                .executes(context -> make(
                                        context.getSource(),
                                        EntityArgument.getEntity(context, "source"),
                                        DEFAULT_QUALITY,
                                        DEFAULT_USES,
                                        DEFAULT_DEFENSE_RISK,
                                        vaccineItem))
                                .then(Commands.argument("quality",
                                                FloatArgumentType.floatArg(0.0f, 1.0f))
                                        .executes(context -> make(
                                                context.getSource(),
                                                EntityArgument.getEntity(context, "source"),
                                                FloatArgumentType.getFloat(context, "quality"),
                                                DEFAULT_USES,
                                                DEFAULT_DEFENSE_RISK,
                                                vaccineItem))
                                        .then(Commands.argument("uses",
                                                        IntegerArgumentType.integer(1, VaccineProfile.MAX_USES))
                                                .executes(context -> make(
                                                        context.getSource(),
                                                        EntityArgument.getEntity(context, "source"),
                                                        FloatArgumentType.getFloat(context, "quality"),
                                                        IntegerArgumentType.getInteger(context, "uses"),
                                                        DEFAULT_DEFENSE_RISK,
                                                        vaccineItem))
                                                .then(Commands.argument("defense_risk",
                                                                FloatArgumentType.floatArg(0.0f, 1.0f))
                                                        .executes(context -> make(
                                                                context.getSource(),
                                                                EntityArgument.getEntity(context, "source"),
                                                                FloatArgumentType.getFloat(context, "quality"),
                                                                IntegerArgumentType.getInteger(context, "uses"),
                                                                FloatArgumentType.getFloat(context, "defense_risk"),
                                                                vaccineItem))))))));
    }

    private static int make(CommandSourceStack commandSource, Entity sourceEntity,
                            float quality, int uses, float defenseRisk,
                            Supplier<? extends Item> vaccineItem) throws CommandSyntaxException {
        ServerPlayer recipient = commandSource.getPlayerOrException();
        if (!(sourceEntity instanceof LivingEntity living)) {
            commandSource.sendFailure(Component.translatable(
                    "command.bioforge.vaccinemake.not_living", sourceEntity.getDisplayName()));
            return 0;
        }

        InfectionData infection = InfectionCapability.get(living);
        if (infection == null || !infection.isInfected() || infection.getPathogenId() == null) {
            commandSource.sendFailure(Component.translatable(
                    "command.bioforge.vaccinemake.not_infected", living.getDisplayName()));
            return 0;
        }

        Item registeredItem = vaccineItem.get();
        if (registeredItem == null) {
            commandSource.sendFailure(Component.translatable(
                    "command.bioforge.vaccinemake.unavailable"));
            return 0;
        }

        VaccineProfile profile = VaccineProfile.capture(
                infection, quality, uses, defenseRisk, living.level().getGameTime());
        ItemStack vaccine = new ItemStack(registeredItem);
        profile.write(vaccine);
        if (!recipient.getInventory().add(vaccine)) {
            recipient.drop(vaccine, false);
        }

        String qualityDisplay = String.format(Locale.ROOT, "%.0f%%", quality * 100.0f);
        commandSource.sendSuccess(() -> Component.translatable(
                "command.bioforge.vaccinemake.success",
                living.getDisplayName(),
                net.jenkimods.bioforge.api.definition.BioForgeIds
                        .legacyCompatible(infection.getPathogenId()),
                qualityDisplay,
                uses), true);
        return 1;
    }
}
