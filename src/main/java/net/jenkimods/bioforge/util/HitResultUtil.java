package net.jenkimods.bioforge.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class HitResultUtil {
    public static EntityHitResult getHitResult(Player player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(5.0));

        return ProjectileUtil.getEntityHitResult(
                player.level(),
                player,
                start,
                end,
                player.getBoundingBox().expandTowards(player.getLookAngle().scale(5)).inflate(1.0),
                e -> !e.isSpectator() && e.isPickable()
        );
    }
}
