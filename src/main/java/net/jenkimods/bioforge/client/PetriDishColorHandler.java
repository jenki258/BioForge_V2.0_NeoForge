package net.jenkimods.bioforge.client;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.infection.PathogenType;
import net.jenkimods.bioforge.item.infection.ColonyCoreBlockEntity;
import net.jenkimods.bioforge.item.infection.InfestedBlockEntity;
import net.jenkimods.bioforge.item.infection.MicrobialMatBlockEntity;
import net.jenkimods.bioforge.item.infection.PetriDishBlockEntity;
import net.jenkimods.bioforge.item.infection.SporocarpBlockEntity;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@EventBusSubscriber(modid = BioForge.MODID, value = Dist.CLIENT)
public class PetriDishColorHandler {

    private static final int VARIATION_RANGE = 50;

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, reader, pos, tintIndex) -> {
                    if (tintIndex == 1 && pos != null && reader != null) {
                        BlockEntity be = reader.getBlockEntity(pos);
                        if (be instanceof PetriDishBlockEntity dish) {
                            return applyVariation(getBaseColor(dish.pathogen), pos, null);
                        }
                    }
                    return 0xFFFFFFFF;
                }, BioForge.PETRI_DISH_BLOCK.get()
        );

        event.register(
                (state, reader, pos, tintIndex) -> {
                    if (tintIndex == 1 && pos != null && reader != null) {
                        BlockEntity be = reader.getBlockEntity(pos);
                        if (be instanceof MicrobialMatBlockEntity mat) {
                            return applyVariation(getBaseColor(mat.pathogen), pos, mat.colonyId);
                        }
                    }
                    return 0xFFFFFFFF;
                }, BioForge.MICROBIAL_MAT.get()
        );

        event.register(
                (state, reader, pos, tintIndex) -> {
                    if (tintIndex == 0 && pos != null && reader != null) {
                        BlockEntity be = reader.getBlockEntity(pos);
                        if (be instanceof InfestedBlockEntity infested) {
                            return applyVariation(getBaseColor(infested.pathogen), pos, infested.colonyId);
                        }
                    }
                    return 0xFFFFFFFF;
                }, BioForge.INFESTED_BLOCK.get()
        );

        event.register(
                (state, reader, pos, tintIndex) -> {
                    if (tintIndex == 1 && pos != null && reader != null) {
                        BlockEntity be = reader.getBlockEntity(pos);
                        if (be instanceof SporocarpBlockEntity spore) {
                            return applyVariation(getBaseColor(spore.pathogen), pos, spore.colonyId);
                        }
                    }
                    return 0xFFFFFFFF;
                }, BioForge.SPOROCARP.get()
        );

        event.register(
                (state, reader, pos, tintIndex) -> {
                    if (tintIndex == 1 && pos != null && reader != null) {
                        BlockEntity be = reader.getBlockEntity(pos);
                        if (be instanceof ColonyCoreBlockEntity core) {
                            return applyVariation(getBaseColor(core.pathogen), pos, core.colonyId);
                        }
                    }
                    return 0xFFFFFFFF;
                }, BioForge.COLONY_CORE.get()
        );
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> {
                    if (tintIndex == 1) {
                        String data = NbtObfuscator.readString(stack);
                        if (data != null && !data.equals("CLEAN")) {
                            String[] parts = data.split(";");
                            String[] header = parts[0].split("\\|");

                            PathogenType pathogen;
                            if (header.length >= 3) {
                                pathogen = PathogenType.fromName(header[1]);
                            }
                            else {
                                pathogen = null;
                            }

                            return getBaseColor(pathogen);
                        }
                    }
                    return 0xFFFFFFFF;
                }, BioForge.PETRI_DISH.get()
        );
    }

    public static int getBaseColor(@Nullable PathogenType pathogen) {
        if (pathogen == null) return 0xFFAAAAAA;
        return switch (pathogen) {
            case VIRUS    -> 0xFFCC6666;
            case BACTERIA -> 0xFF66CC66;
            case FUNGI    -> 0xFFCCCC66;
            case PARASITE -> 0xFFCC66CC;
            case PRION    -> 0xFFCCCCCC;
            default      -> 0xFFAAAAAA;
        };
    }

    public static int applyVariation(int baseColor, BlockPos pos, @Nullable UUID colonyId) {
        long seed;
        if (colonyId != null) {
            seed = colonyId.getMostSignificantBits() ^ colonyId.getLeastSignificantBits();
        } else {
            seed = (long) pos.getX() * 31L + (long) pos.getY() * 17L + (long) pos.getZ() * 13L;
        }

        int rOff = (int) (Math.abs(seed) % (VARIATION_RANGE + 1));
        int gOff = (int) (Math.abs(seed >> 8) % (VARIATION_RANGE + 1));
        int bOff = (int) (Math.abs(seed >> 16) % (VARIATION_RANGE + 1));

        int a = (baseColor >> 24) & 0xFF;
        int r = ((baseColor >> 16) & 0xFF) + rOff;
        int g = ((baseColor >> 8) & 0xFF) + gOff;
        int b = (baseColor & 0xFF) + bOff;

        r = Math.min(255, Math.max(0, r));
        g = Math.min(255, Math.max(0, g));
        b = Math.min(255, Math.max(0, b));

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
