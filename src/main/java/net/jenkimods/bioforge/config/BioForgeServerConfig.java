package net.jenkimods.bioforge.config;

import net.jenkimods.bioforge.infection.InfectionType;
import net.jenkimods.bioforge.vaccine.VaccineRules;
import net.jenkimods.bioforge.world.laboratory.LaboratoryStation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class BioForgeServerConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.DoubleValue MINIMUM_SIMILARITY;
    private static final ModConfigSpec.DoubleValue SIMILARITY_CURVE_FLOOR;
    private static final ModConfigSpec.DoubleValue BASE_POTENCY;
    private static final ModConfigSpec.DoubleValue MAXIMUM_CURE_CHANCE;
    private static final ModConfigSpec.DoubleValue STRENGTH_RESISTANCE;
    private static final ModConfigSpec.DoubleValue DEFENSE_CURE_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue DEFENSE_RISK_STRENGTH_SCALE;
    private static final ModConfigSpec.DoubleValue DEFENSE_RISK_MISMATCH_SCALE;
    private static final ModConfigSpec.DoubleValue EXACT_BLOOD_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue SAME_RH_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue RH_MISMATCH_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue UNKNOWN_HOST_MULTIPLIER;
    private static final ModConfigSpec.ConfigValue<String> DEFENSE_MUTATION;
    private static final ModConfigSpec.IntValue STRAIN_IMMUNITY_DURATION;
    private static final ModConfigSpec.BooleanValue SPREADING_ENABLED;
    private static final ModConfigSpec.BooleanValue NATURAL_INFECTIONS_ENABLED;
    private static final ModConfigSpec.BooleanValue SYMPTOMS_ENABLED;
    private static final ModConfigSpec.BooleanValue MUTATIONS_ENABLED;
    private static final EnumMap<InfectionType, ModConfigSpec.BooleanValue> TRANSMISSION_TYPES =
            new EnumMap<>(InfectionType.class);
    private static final Map<String, ModConfigSpec.BooleanValue> SYMPTOM_TYPES =
            new LinkedHashMap<>();
    private static final Map<String, ModConfigSpec.BooleanValue> BUILT_IN_MUTATIONS =
            new LinkedHashMap<>();
    private static final ModConfigSpec.IntValue AIR_ROOM_MAX_VOLUME;
    private static final ModConfigSpec.IntValue AIR_ROOM_MAX_RADIUS;
    private static final ModConfigSpec.IntValue AIR_ROOM_CACHE_TICKS;
    private static final ModConfigSpec.IntValue AIRBORNE_WORK_BUDGET;
    private static final ModConfigSpec.DoubleValue TRANSMISSION_CHANCE_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue AIR_EXPOSURE_CHANCE;
    private static final ModConfigSpec.DoubleValue OUTDOOR_AIR_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue MEDICAL_MASK_INCOMING_AIR_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue HAZCURE_INCOMING_AIR_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue HAZCURE_INCOMING_CONTACT_MULTIPLIER;
    private static final ModConfigSpec.IntValue AIRBORNE_RESERVOIR_LIFETIME_TICKS;
    private static final ModConfigSpec.IntValue SURFACE_LIFETIME_TICKS;
    private static final ModConfigSpec.IntValue SURFACE_CLEANUP_BUDGET;
    private static final ModConfigSpec.DoubleValue SURFACE_EXPOSURE_CHANCE;
    private static final ModConfigSpec.DoubleValue SURFACE_DEPOSIT_CHANCE;
    private static final ModConfigSpec.DoubleValue ATTACK_EXPOSURE_CHANCE;
    private static final ModConfigSpec.DoubleValue FOOD_EXPOSURE_CHANCE;
    private static final ModConfigSpec.DoubleValue WATER_EXPOSURE_CHANCE;
    private static final ModConfigSpec.DoubleValue BLOOD_EXPOSURE_CHANCE;
    private static final ModConfigSpec.IntValue DECONTAMINATION_RADIUS;
    private static final ModConfigSpec.DoubleValue CENTRIFUGE_SPEED;
    private static final ModConfigSpec.DoubleValue INCUBATOR_SPEED;
    private static final ModConfigSpec.DoubleValue VACCINE_MAKER_SPEED;
    private static final ModConfigSpec.DoubleValue BARREL_PRESS_SPEED;
    private static final ModConfigSpec.DoubleValue CHEMICAL_SYNTHESIZER_SPEED;
    private static final ModConfigSpec.DoubleValue STERILIZATION_CHAMBER_SPEED;
    private static final ModConfigSpec.DoubleValue PHARMA_MIXER_SPEED;

    private static final String[] ORIGINAL_SYMPTOMS = {
            "heart_rate", "lung_sound", "temperature_plus", "temperature_minus",
            "otoscope_redness", "otoscope_lesions", "otoscope_secretion",
            "otoscope_swelling", "reflex_delay", "reflex_strength", "neural_damage",
            "oxygen_saturation", "perfusion_index", "infection_strength",
            "colony_radius", "max_infested_blocks", "microscope_visibility"
    };

    private static final String[] BUILT_IN_MUTATION_IDS = {
            "bloodborne", "hypervirulence", "necrotic_fever", "neural_decay",
            "reinforced_vaccine_defense", "spore_cloud", "vaccine_defense",
            "hypoxic_drift", "respiratory_shedding", "hemorrhagic_lesions",
            "thermal_instability", "gastrointestinal_shedding", "zoonotic_adaptation",
            "surface_persistence", "metabolic_overdrive", "analgesic_resistance",
            "protective_coating"
    };

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment(
                "BioForge per-world server balance.",
                "This file is created in the world's serverconfig directory.")
                .push("vaccines");

        MINIMUM_SIMILARITY = probability(builder,
                "minimumSimilarity", 0.55,
                "Minimum strain similarity required for a full vaccine to have any cure chance.");
        SIMILARITY_CURVE_FLOOR = probability(builder,
                "similarityCurveFloor", 0.40,
                "Similarity value treated as the bottom of the quadratic cure curve.");
        BASE_POTENCY = nonNegative(builder,
                "basePotency", 1.00,
                "Base full-vaccine potency before quality, similarity, strength and blood modifiers.");
        MAXIMUM_CURE_CHANCE = probability(builder,
                "maximumCureChance", 0.88,
                "Hard cap for full-vaccine cure chance after every modifier.");
        STRENGTH_RESISTANCE = nonNegative(builder,
                "strengthResistance", 1.80,
                "How strongly infection_strength reduces full and directed vaccine success.");
        DEFENSE_CURE_MULTIPLIER = probability(builder,
                "defenseMutationCureMultiplier", 0.40,
                "Cure-chance multiplier while a vaccine-defense/immune-escape mutation is active.");
        DEFENSE_RISK_STRENGTH_SCALE = nonNegative(builder,
                "defenseRiskStrengthScale", 0.65,
                "How infection strength increases the chance of selecting vaccine defense.");
        DEFENSE_RISK_MISMATCH_SCALE = nonNegative(builder,
                "defenseRiskMismatchScale", 0.40,
                "How strain mismatch increases the chance of selecting vaccine defense.");
        STRAIN_IMMUNITY_DURATION = builder.comment(
                        "Duration in ticks of exact-strain immunity granted by a successful full vaccine.",
                        "20 ticks = 1 second; the default is 10 minutes of loaded game time.")
                .defineInRange("strainImmunityDurationTicks", 12_000, 20, 2_147_483_647);

        builder.pop().push("bloodCompatibility");
        EXACT_BLOOD_MULTIPLIER = nonNegative(builder,
                "exactBloodTypeMultiplier", 1.10,
                "Multiplier when the vaccine's researched ABO/Rh profile exactly matches the host.");
        SAME_RH_MULTIPLIER = nonNegative(builder,
                "sameRhMultiplier", 0.95,
                "Multiplier for a different ABO group with the same Rh sign.");
        RH_MISMATCH_MULTIPLIER = nonNegative(builder,
                "rhMismatchMultiplier", 0.65,
                "Multiplier for an Rh/category mismatch.");
        UNKNOWN_HOST_MULTIPLIER = nonNegative(builder,
                "unknownHostMultiplier", 0.85,
                "Multiplier when either the host or vaccine has no verified blood profile.");

        builder.pop().push("machineProcessing");
        CENTRIFUGE_SPEED = processingSpeed(builder, "centrifugeSpeedMultiplier",
                "Processing speed multiplier for the Centrifuge.");
        INCUBATOR_SPEED = processingSpeed(builder, "incubatorSpeedMultiplier",
                "Processing speed multiplier for the Incubator.");
        VACCINE_MAKER_SPEED = processingSpeed(builder, "vaccineMakerSpeedMultiplier",
                "Processing speed multiplier for the Vaccine Maker.");
        BARREL_PRESS_SPEED = processingSpeed(builder, "barrelPressSpeedMultiplier",
                "Processing speed multiplier for the Barrel Press.");
        CHEMICAL_SYNTHESIZER_SPEED = processingSpeed(builder,
                "chemicalSynthesizerSpeedMultiplier",
                "Processing speed multiplier for the Chemical Synthesizer.");
        STERILIZATION_CHAMBER_SPEED = processingSpeed(builder,
                "sterilizationChamberSpeedMultiplier",
                "Processing speed multiplier for the Sterilization Chamber.");
        PHARMA_MIXER_SPEED = processingSpeed(builder, "pharmaMixerSpeedMultiplier",
                "Processing speed multiplier for the Pharma Mixer.");

        builder.pop().push("mutations");
        DEFENSE_MUTATION = builder
                .comment("Mutation ID selected after a failed vaccine when possible.")
                .define("defenseMutation", VaccineRules.DEFAULT_DEFENSE_MUTATION_ID,
                        value -> value instanceof String string && !string.isBlank());
        builder.pop();

        builder.comment(
                "Feature switches for modpack authors.",
                "A disabled built-in feature is ignored by generation, machines and runtime logic.")
                .push("features");

        builder.push("spreading");
        SPREADING_ENABLED = builder.comment(
                        "Master switch for every automatic BioForge infection-spreading mechanic.")
                .define("enabled", true);
        NATURAL_INFECTIONS_ENABLED = builder.comment(
                        "Allow JSON/Java natural host rules to seed infections in newly spawned creatures.")
                .define("naturalHostInfections", true);
        builder.push("transmissionTypes");
        for (InfectionType type : InfectionType.values()) {
            TRANSMISSION_TYPES.put(type, builder.comment(
                            "Whether the " + type.name() + " transmission route exists in generated strains and runtime spreading.")
                    .define(type.name().toLowerCase(Locale.ROOT), true));
        }
        builder.pop();
        builder.push("balance");
        AIR_ROOM_MAX_VOLUME = builder.comment(
                        "Maximum number of connected air blocks scanned for one sealed-room calculation.")
                .defineInRange("airRoomMaxVolume", 2048, 64, 16384);
        AIR_ROOM_MAX_RADIUS = builder.comment(
                        "Maximum block radius of a sealed-room calculation around an infectious host.")
                .defineInRange("airRoomMaxRadius", 12, 3, 32);
        AIR_ROOM_CACHE_TICKS = builder.comment(
                        "How long an unchanged room scan stays cached. Block changes invalidate the cache early.")
                .defineInRange("airRoomCacheTicks", 200, 20, 1200);
        AIRBORNE_WORK_BUDGET = builder.comment(
                        "Maximum airborne reservoirs processed per world each second.")
                .defineInRange("airborneWorkBudget", 128, 1, 4096);
        TRANSMISSION_CHANCE_MULTIPLIER = builder.comment(
                        "Global multiplier for automatic infection chances across every transmission route.",
                        "The default 0.85 makes incidental spread 15% less frequent without changing strain data.")
                .defineInRange("globalTransmissionChanceMultiplier", 0.85, 0.0, 10.0);
        AIR_EXPOSURE_CHANCE = probability(builder, "airExposureChancePerSecond", 0.12,
                "Base infection chance per second at full indoor airborne concentration.");
        OUTDOOR_AIR_MULTIPLIER = probability(builder, "outdoorAirMultiplier", 0.15,
                "Multiplier applied to airborne concentration in open or oversized spaces.");
        MEDICAL_MASK_INCOMING_AIR_MULTIPLIER = probability(builder,
                "medicalMaskIncomingAirMultiplier", 0.20,
                "Incoming airborne exposure multiplier while wearing ordinary reducing equipment such as a medical mask.");
        HAZCURE_INCOMING_AIR_MULTIPLIER = probability(builder,
                "hazcureIncomingAirMultiplier", 0.0,
                "Incoming airborne exposure multiplier for a complete HazCure suit. Zero makes the suit fully immune.");
        HAZCURE_INCOMING_CONTACT_MULTIPLIER = probability(builder,
                "hazcureIncomingContactMultiplier", 0.0,
                "Incoming contact and attack exposure multiplier for a complete HazCure suit. Zero makes the suit fully immune.");
        AIRBORNE_RESERVOIR_LIFETIME_TICKS = builder.comment(
                        "Maximum lifetime of airborne contamination after its last emission, in ticks.",
                        "20 ticks = 1 second; the default is 30 minutes of loaded game time.")
                .defineInRange("airborneReservoirLifetimeTicks", 36_000, 200, 2_147_483_647);
        SURFACE_LIFETIME_TICKS = builder.comment(
                        "Base lifetime of contamination on an ordinary block, in ticks.")
                .defineInRange("surfaceLifetimeTicks", 6000, 20, 2_147_483_647);
        SURFACE_CLEANUP_BUDGET = builder.comment(
                        "Maximum expired surface entries removed per world cleanup pass.")
                .defineInRange("surfaceCleanupBudget", 2048, 1, 65536);
        SURFACE_EXPOSURE_CHANCE = probability(builder, "surfaceExposureChance", 0.14,
                "Base infection chance when stepping on, mining or using a contaminated block.");
        SURFACE_DEPOSIT_CHANCE = probability(builder, "surfaceDepositChancePerSecond", 0.28,
                "Chance per second for a contact-spreading host to contaminate the block below them.");
        ATTACK_EXPOSURE_CHANCE = probability(builder, "attackExposureChance", 0.40,
                "Base infection chance for an ATTACK_BASED hit before strain strength and protection.");
        FOOD_EXPOSURE_CHANCE = probability(builder, "foodExposureChance", 0.70,
                "Base infection chance from consuming FOOD_BORNE contaminated food.");
        WATER_EXPOSURE_CHANCE = probability(builder, "waterExposureChance", 0.65,
                "Base infection chance from consuming WATER_BORNE contaminated drinks.");
        BLOOD_EXPOSURE_CHANCE = probability(builder, "bloodExposureChance", 0.90,
                "Base infection chance from direct infected-blood exposure.");
        DECONTAMINATION_RADIUS = builder.comment(
                        "Radius cleaned by decontamination splash fluid. Radius 2 produces a 5x5x5 cube.")
                .defineInRange("decontaminationRadius", 2, 0, 16);
        builder.pop().pop();

        builder.push("symptoms");
        SYMPTOMS_ENABLED = builder.comment(
                        "Master switch for built-in symptom gameplay effects and generated symptom data.")
                .define("enabled", true);
        builder.push("builtIns");
        for (String id : ORIGINAL_SYMPTOMS) {
            SYMPTOM_TYPES.put(id, builder.comment(
                            "Whether the built-in symptom '" + id + "' is generated, displayed and applied.")
                    .define(id, true));
        }
        builder.pop().pop();

        builder.push("mutations");
        MUTATIONS_ENABLED = builder.comment(
                        "Master switch for all mutation selection, interaction and gameplay effects.")
                .define("enabled", true);
        builder.push("builtIns");
        for (String id : BUILT_IN_MUTATION_IDS) {
            BUILT_IN_MUTATIONS.put(id, builder.comment(
                            "Whether the built-in mutation '" + id + "' is available.")
                    .define(id, true));
        }
        builder.pop().pop().pop();
        SPEC = builder.build();
    }

    private BioForgeServerConfig() {}

    public static VaccineRules vaccineRules() {
        return new VaccineRules(
                MINIMUM_SIMILARITY.get().floatValue(),
                SIMILARITY_CURVE_FLOOR.get().floatValue(),
                BASE_POTENCY.get().floatValue(),
                MAXIMUM_CURE_CHANCE.get().floatValue(),
                STRENGTH_RESISTANCE.get().floatValue(),
                DEFENSE_CURE_MULTIPLIER.get().floatValue(),
                DEFENSE_RISK_STRENGTH_SCALE.get().floatValue(),
                DEFENSE_RISK_MISMATCH_SCALE.get().floatValue(),
                EXACT_BLOOD_MULTIPLIER.get().floatValue(),
                SAME_RH_MULTIPLIER.get().floatValue(),
                RH_MISMATCH_MULTIPLIER.get().floatValue(),
                UNKNOWN_HOST_MULTIPLIER.get().floatValue(),
                DEFENSE_MUTATION.get());
    }

    public static int strainImmunityDurationTicks() {
        return STRAIN_IMMUNITY_DURATION.get();
    }

    public static boolean spreadingEnabled() {
        return SPREADING_ENABLED.get();
    }

    public static boolean naturalInfectionsEnabled() {
        return NATURAL_INFECTIONS_ENABLED.get();
    }

    public static boolean isTransmissionEnabled(InfectionType type) {
        if (type == null || !spreadingEnabled()) return false;
        ModConfigSpec.BooleanValue value = TRANSMISSION_TYPES.get(type);
        return value == null || value.get();
    }

    public static boolean symptomsEnabled() {
        return SYMPTOMS_ENABLED.get();
    }

    public static boolean isSymptomEnabled(String id) {
        if (id == null || !symptomsEnabled()) return false;
        ModConfigSpec.BooleanValue value = SYMPTOM_TYPES.get(normalizeId(id));
        return value == null || value.get();
    }

    public static boolean mutationsEnabled() {
        return MUTATIONS_ENABLED.get();
    }

    public static boolean isMutationEnabled(String id) {
        if (id == null || !mutationsEnabled()) return false;
        ModConfigSpec.BooleanValue value = BUILT_IN_MUTATIONS.get(normalizeId(id));
        return value == null || value.get();
    }

    public static int airRoomMaxVolume() { return AIR_ROOM_MAX_VOLUME.get(); }
    public static int airRoomMaxRadius() { return AIR_ROOM_MAX_RADIUS.get(); }
    public static int airRoomCacheTicks() { return AIR_ROOM_CACHE_TICKS.get(); }
    public static int airborneWorkBudget() { return AIRBORNE_WORK_BUDGET.get(); }
    public static float transmissionChanceMultiplier() {
        return TRANSMISSION_CHANCE_MULTIPLIER.get().floatValue();
    }
    public static float airExposureChance() {
        return AIR_EXPOSURE_CHANCE.get().floatValue() * transmissionChanceMultiplier();
    }
    public static float outdoorAirMultiplier() { return OUTDOOR_AIR_MULTIPLIER.get().floatValue(); }
    public static float medicalMaskIncomingAirMultiplier() { return MEDICAL_MASK_INCOMING_AIR_MULTIPLIER.get().floatValue(); }
    public static float hazcureIncomingAirMultiplier() { return HAZCURE_INCOMING_AIR_MULTIPLIER.get().floatValue(); }
    public static float hazcureIncomingContactMultiplier() { return HAZCURE_INCOMING_CONTACT_MULTIPLIER.get().floatValue(); }
    public static int airborneReservoirLifetimeTicks() { return AIRBORNE_RESERVOIR_LIFETIME_TICKS.get(); }
    public static int surfaceLifetimeTicks() { return SURFACE_LIFETIME_TICKS.get(); }
    public static int surfaceCleanupBudget() { return SURFACE_CLEANUP_BUDGET.get(); }
    public static float surfaceExposureChance() {
        return SURFACE_EXPOSURE_CHANCE.get().floatValue() * transmissionChanceMultiplier();
    }
    public static float surfaceDepositChance() {
        return SURFACE_DEPOSIT_CHANCE.get().floatValue() * transmissionChanceMultiplier();
    }
    public static float attackExposureChance() {
        return ATTACK_EXPOSURE_CHANCE.get().floatValue() * transmissionChanceMultiplier();
    }
    public static float foodExposureChance() {
        return FOOD_EXPOSURE_CHANCE.get().floatValue() * transmissionChanceMultiplier();
    }
    public static float waterExposureChance() {
        return WATER_EXPOSURE_CHANCE.get().floatValue() * transmissionChanceMultiplier();
    }
    public static float bloodExposureChance() {
        return BLOOD_EXPOSURE_CHANCE.get().floatValue() * transmissionChanceMultiplier();
    }
    public static int decontaminationRadius() { return DECONTAMINATION_RADIUS.get(); }

    public static int centrifugeProcessingTime(int baseTicks) {
        return scaledProcessingTime(baseTicks, CENTRIFUGE_SPEED.get());
    }

    public static int incubatorProcessingTime(int baseTicks) {
        return scaledProcessingTime(baseTicks, INCUBATOR_SPEED.get());
    }

    public static int vaccineMakerProcessingTime(int baseTicks) {
        return scaledProcessingTime(baseTicks, VACCINE_MAKER_SPEED.get());
    }

    public static int laboratoryProcessingTime(LaboratoryStation station, int baseTicks) {
        double speed = switch (station) {
            case BARREL_PRESS -> BARREL_PRESS_SPEED.get();
            case CHEMICAL_SYNTHESIZER -> CHEMICAL_SYNTHESIZER_SPEED.get();
            case STERILIZATION_CHAMBER -> STERILIZATION_CHAMBER_SPEED.get();
            case PHARMA_MIXER -> PHARMA_MIXER_SPEED.get();
        };
        return scaledProcessingTime(baseTicks, speed);
    }

    private static String normalizeId(String id) {
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(':');
        return separator >= 0 ? normalized.substring(separator + 1) : normalized;
    }

    private static ModConfigSpec.DoubleValue probability(
            ModConfigSpec.Builder builder, String name, double fallback, String comment) {
        return builder.comment(comment).defineInRange(name, fallback, 0.0, 1.0);
    }

    private static ModConfigSpec.DoubleValue nonNegative(
            ModConfigSpec.Builder builder, String name, double fallback, String comment) {
        return builder.comment(comment).defineInRange(name, fallback, 0.0, 100.0);
    }

    private static ModConfigSpec.DoubleValue processingSpeed(
            ModConfigSpec.Builder builder, String name, String comment) {
        return builder.comment(comment,
                        "1.0 keeps JSON recipe time, 2.0 is twice as fast, and 0.5 is twice as slow.")
                .defineInRange(name, 1.0, 0.05, 100.0);
    }

    private static int scaledProcessingTime(int baseTicks, double speed) {
        return Math.max(1, (int) Math.ceil(Math.max(1, baseTicks) / speed));
    }
}
