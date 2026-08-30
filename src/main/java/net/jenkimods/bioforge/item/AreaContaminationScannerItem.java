package net.jenkimods.bioforge.item;

import net.jenkimods.bioforge.infection.spread.AirborneReservoirManager;
import net.jenkimods.bioforge.infection.spread.SurfaceContaminationData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.List;

public final class AreaContaminationScannerItem extends Item {
    private static final int RANGE = 12;

    public AreaContaminationScannerItem() {
        super(new Properties().stacksTo(1).durability(384));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        SurfaceContaminationData.ScanResult surface = SurfaceContaminationData.get(serverLevel)
                .scan(player.blockPosition(), RANGE, serverLevel.getGameTime());
        AirborneReservoirManager.ScanResult air = AirborneReservoirManager.scan(
                serverLevel, player.blockPosition());
        float hazard = Math.max(surface.maximumStrength(), air.maximumConcentration());
        String levelKey = hazard < 0.005F ? "clean"
                : hazard < 0.2F ? "low"
                : hazard < 0.5F ? "medium"
                : hazard < 0.8F ? "high" : "critical";
        serverPlayer.displayClientMessage(Component.translatable(
                "item.bioforge.area_contamination_scanner.result",
                Component.translatable("item.bioforge.area_contamination_scanner.level." + levelKey),
                surface.contaminatedSurfaces(), air.reservoirs()).append(Component.translatable(
                "item.bioforge.area_contamination_scanner.coated_result",
                surface.ethanolCoatedMarkers().size())), true);
        showContamination(serverLevel, serverPlayer, surface, air);
        serverPlayer.getCooldowns().addCooldown(this, 30);
        stack.hurtAndBreak(1, serverPlayer, LivingEntity.getSlotForHand(hand));
        return InteractionResultHolder.success(stack);
    }

    private static void showContamination(ServerLevel level, ServerPlayer player,
                                          SurfaceContaminationData.ScanResult surface,
                                          AirborneReservoirManager.ScanResult air) {
        for (SurfaceContaminationData.ScanMarker marker : surface.markers()) {
            float strength = marker.strength();
            DustParticleOptions color = new DustParticleOptions(new Vector3f(
                    1.0F,
                    Math.max(0.05F, 0.85F - strength * 0.75F),
                    0.05F), 1.15F);
            var pos = marker.position();
            level.sendParticles(player, color, true,
                    pos.getX() + 0.5D, pos.getY() + 0.65D, pos.getZ() + 0.5D,
                    6, 0.38D, 0.38D, 0.38D, 0.0D);
        }
        DustParticleOptions coated = new DustParticleOptions(
                new Vector3f(0.10F, 1.0F, 0.55F), 1.0F);
        for (var pos : surface.ethanolCoatedMarkers()) {
            level.sendParticles(player, coated, true,
                    pos.getX() + 0.5D, pos.getY() + 0.65D, pos.getZ() + 0.5D,
                    8, 0.38D, 0.38D, 0.38D, 0.0D);
        }
        DustParticleOptions airborne = new DustParticleOptions(
                new Vector3f(0.10F, 0.75F, 1.0F), 0.8F);
        for (var pos : air.visualizationCells()) {
            level.sendParticles(player, airborne, true,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    1, 0.15D, 0.15D, 0.15D, 0.0D);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bioforge.area_contamination_scanner.tooltip", RANGE)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.bioforge.area_contamination_scanner.visualization")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable(
                        "item.bioforge.area_contamination_scanner.ethanol_visualization")
                .withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("item.bioforge.area_contamination_scanner.privacy")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
