package net.jenkimods.bioforge.blood;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

public enum BloodType {

    A_POSITIVE ("A+",  Category.HUMAN),
    A_NEGATIVE ("A-",  Category.HUMAN),
    B_POSITIVE ("B+",  Category.HUMAN),
    B_NEGATIVE ("B-",  Category.HUMAN),
    AB_POSITIVE("AB+", Category.HUMAN),
    AB_NEGATIVE("AB-", Category.HUMAN),
    O_POSITIVE ("O+",  Category.HUMAN),
    O_NEGATIVE ("O-",  Category.HUMAN),

    ANIMAL_BLOOD ("Animal", Category.NON_HUMAN);

    public enum Category {
        HUMAN,
        NON_HUMAN
    }

    private final String displayName;
    private final Category category;

    BloodType(String displayName, Category category) {
        this.displayName = displayName;
        this.category = category;
    }

    public String getDisplayName() { return displayName; }
    public Component getDisplayNameComponent() {
        return this == ANIMAL_BLOOD
                ? Component.translatable("blood.type.animal")
                : Component.literal(displayName);
    }

    public static Component displayNameComponent(String storedName) {
        BloodType type = findByName(storedName);
        return type == null ? Component.literal(storedName == null ? "" : storedName)
                : type.getDisplayNameComponent();
    }
    public Category getCategory() { return category; }

    public boolean isRhPositive() {
        return category == Category.HUMAN && displayName.endsWith("+");
    }

    public boolean isRhNegative() {
        return category == Category.HUMAN && displayName.endsWith("-");
    }

    public boolean isCompatibleWith(BloodType other) {

        if (this.category != other.category) return false;

        if (this.category == Category.HUMAN) {
            return humanCompatible(this, other);
        }

        return this == other;
    }

    private static boolean humanCompatible(BloodType donor, BloodType recipient) {

        if (donor == O_NEGATIVE) return true;

        if (recipient == AB_POSITIVE) return true;

        boolean donorPositive = donor.displayName.contains("+");
        boolean recipientPositive = recipient.displayName.contains("+");

        if (donorPositive && !recipientPositive) return false;

        String donorABO = donor.displayName.replace("+","").replace("-","");
        String recipientABO = recipient.displayName.replace("+","").replace("-","");

        return switch (donorABO) {
            case "O" -> true;
            case "A" -> recipientABO.equals("A") || recipientABO.equals("AB");
            case "B" -> recipientABO.equals("B") || recipientABO.equals("AB");
            case "AB" -> recipientABO.equals("AB");
            default -> false;
        };
    }

    private static final BloodType[] HUMAN_TYPES = {
            A_POSITIVE, A_NEGATIVE, B_POSITIVE, B_NEGATIVE,
            AB_POSITIVE, AB_NEGATIVE, O_POSITIVE, O_NEGATIVE
    };

    private static final BloodType[] NON_HUMAN_TYPES = {
            ANIMAL_BLOOD
    };

    public static BloodType randomHuman(Random rng) {
        return HUMAN_TYPES[rng.nextInt(HUMAN_TYPES.length)];
    }

    public static BloodType randomNonHuman(Random rng) {
        return NON_HUMAN_TYPES[rng.nextInt(NON_HUMAN_TYPES.length)];
    }

    public static BloodType fromName(String name) {
        BloodType found = findByName(name);
        return found == null ? O_POSITIVE : found;
    }

    @Nullable
    public static BloodType findByName(String name) {
        if (name == null) return null;
        for (BloodType t : values()) {
            if (t.name().equalsIgnoreCase(name)
                    || t.displayName.equalsIgnoreCase(name.trim())) {
                return t;
            }
        }
        return null;
    }
}
