package net.jenkimods.bioforge.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class BlackSteelBlock extends Block {
    public BlackSteelBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(7.0F, 9.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops());
    }
}
