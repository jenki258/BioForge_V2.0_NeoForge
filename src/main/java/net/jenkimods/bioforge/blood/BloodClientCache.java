package net.jenkimods.bioforge.blood;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


public final class BloodClientCache {

    private BloodClientCache() {}

    private static final AtomicInteger bloodAmount = new AtomicInteger(BloodData.MAX_BLOOD);
    private static final AtomicReference<BloodType> bloodType =
            new AtomicReference<>(BloodType.O_POSITIVE);

    public static void set(int amount, BloodType type) {
        bloodAmount.set(amount);
        bloodType.set(type);
    }

    public static int getBlood() { return bloodAmount.get(); }
    public static BloodType getBloodType() { return bloodType.get(); }
}
