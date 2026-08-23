package net.jenkimods.bioforge.world.microscope;

import net.minecraft.network.FriendlyByteBuf;

public record CalibrationSlider(String nameKey, float target, float rangeMin, float rangeMax, boolean randomTarget) {

    public CalibrationSlider(String nameKey, float target) {
        this(nameKey, target, 0.0f, 1.0f, false);
    }

    public CalibrationSlider(String nameKey, float target, float rangeMin, float rangeMax) {
        this(nameKey, target, rangeMin, rangeMax, false);
    }

    public boolean isWithinTolerance(float value) {
        float range = rangeMax - rangeMin;
        float tolerance = range * 0.05f;
        return Math.abs(value - target) <= tolerance;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nameKey);
        buf.writeFloat(target);
        buf.writeFloat(rangeMin);
        buf.writeFloat(rangeMax);
        buf.writeBoolean(randomTarget);
    }

    public static CalibrationSlider decode(FriendlyByteBuf buf) {
        return new CalibrationSlider(buf.readUtf(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }
}