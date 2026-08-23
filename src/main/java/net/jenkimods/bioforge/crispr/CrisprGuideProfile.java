package net.jenkimods.bioforge.crispr;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.jenkimods.bioforge.infection.StrainData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

public record CrisprGuideProfile(
        ResourceLocation id,
        String alphabet,
        int cartridgeSize,
        int cartridgesPerGuide,
        List<GuideRole> guides
) {
    public static final int GUIDE_COUNT = 3;
    public static final int CARTRIDGE_COUNT = 15;
    public static final int BASE_COUNT = 60;

    public record GuideRole(String name, String salt, List<String> sources) {}

    public CrisprGuideProfile {
        if (alphabet == null || alphabet.length() < 2) {
            throw new IllegalArgumentException("CRISPR alphabet must contain at least two bases");
        }
        alphabet = alphabet.toUpperCase(Locale.ROOT);
        if (alphabet.chars().distinct().count() != alphabet.length()) {
            throw new IllegalArgumentException("CRISPR alphabet cannot contain duplicate bases");
        }
        if (cartridgeSize != 4 || cartridgesPerGuide != 5) {
            throw new IllegalArgumentException(
                    "BioForge 2 GUI currently requires 4 bases per cartridge and 5 cartridges per guide");
        }
        if (guides.size() != GUIDE_COUNT) {
            throw new IllegalArgumentException("A CRISPR profile must define exactly three guide roles");
        }
        guides = List.copyOf(guides);
    }

    public static CrisprGuideProfile fromJson(ResourceLocation id, JsonObject json) {
        String alphabet = GsonHelper.getAsString(json, "alphabet", "ACGU");
        int cartridgeSize = GsonHelper.getAsInt(json, "cartridge_size", 4);
        int cartridgesPerGuide = GsonHelper.getAsInt(json, "cartridges_per_guide", 5);
        JsonArray guideArray = GsonHelper.getAsJsonArray(json, "guides");
        List<GuideRole> roles = new ArrayList<>();
        for (JsonElement element : guideArray) {
            JsonObject guide = GsonHelper.convertToJsonObject(element, "guide");
            String name = GsonHelper.getAsString(guide, "name");
            String salt = GsonHelper.getAsString(guide, "salt", name);
            JsonArray sourceArray = GsonHelper.getAsJsonArray(guide, "sources");
            List<String> sources = new ArrayList<>();
            for (JsonElement source : sourceArray) {
                String selector = source.getAsString().trim().toLowerCase(Locale.ROOT);
                if (!Set.of("pathogen", "transmission", "symptoms", "mutations", "core")
                        .contains(selector)) {
                    throw new IllegalArgumentException("Unknown CRISPR guide source: " + selector);
                }
                sources.add(selector);
            }
            if (sources.isEmpty()) {
                throw new IllegalArgumentException("CRISPR guide role " + name + " has no sources");
            }
            roles.add(new GuideRole(name, salt, List.copyOf(sources)));
        }
        return new CrisprGuideProfile(id, alphabet, cartridgeSize, cartridgesPerGuide, roles);
    }

    public String deriveSequence(StrainData strain) {
        if (strain == null || strain.getPathogenId() == null) return "";
        StringBuilder result = new StringBuilder(BASE_COUNT);
        for (GuideRole role : guides) {
            String canonical = canonicalize(strain, role.sources());
            result.append(hashToBases(role.salt() + "|" + canonical,
                    cartridgeSize * cartridgesPerGuide));
        }
        return result.toString();
    }

    public String expectedCartridge(StrainData strain, int cartridgeIndex) {
        if (cartridgeIndex < 0 || cartridgeIndex >= CARTRIDGE_COUNT) return "";
        String sequence = deriveSequence(strain);
        int start = cartridgeIndex * cartridgeSize;
        return sequence.length() >= start + cartridgeSize
                ? sequence.substring(start, start + cartridgeSize) : "";
    }

    private String hashToBases(String input, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder bases = new StringBuilder(length);
            int round = 0;
            while (bases.length() < length) {
                byte[] bytes = digest.digest((input + "#" + round++)
                        .getBytes(StandardCharsets.UTF_8));
                for (byte value : bytes) {
                    bases.append(alphabet.charAt(Byte.toUnsignedInt(value) % alphabet.length()));
                    if (bases.length() == length) break;
                }
            }
            return bases.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String canonicalize(StrainData strain, List<String> selectors) {
        StringJoiner result = new StringJoiner("|");
        for (String selector : selectors) {
            switch (selector) {
                case "pathogen" -> result.add("pathogen="
                        + (strain.getPathogenId() == null ? "UNKNOWN"
                        : BioForgeIds.legacyCompatible(strain.getPathogenId())));
                case "transmission" -> result.add("transmission=" + strain.getTransmissionIds()
                        .stream().map(BioForgeIds::legacyCompatible).sorted()
                        .reduce((a, b) -> a + "," + b).orElse(""));
                case "symptoms" -> result.add("symptoms=" + strain.getSymptoms().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .reduce((a, b) -> a + "," + b).orElse(""));
                case "mutations" -> result.add("mutations=" + strain.getMutationIds().stream()
                        .sorted(Comparator.naturalOrder()).reduce((a, b) -> a + "," + b).orElse(""));
                case "core" -> result.add("core="
                        + (strain.getPathogenId() == null ? "UNKNOWN"
                        : BioForgeIds.legacyCompatible(strain.getPathogenId()))
                        + ";" + strain.getSymptoms().entrySet().stream()
                        .filter(entry -> entry.getKey().equals("infection_strength")
                                || entry.getKey().equals("colony_radius")
                                || entry.getKey().equals("max_infested_blocks"))
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .reduce((a, b) -> a + "," + b).orElse(""));
                default -> throw new IllegalStateException("Unhandled CRISPR selector " + selector);
            }
        }
        return result.toString();
    }
}
