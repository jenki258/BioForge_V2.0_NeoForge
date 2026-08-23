package net.jenkimods.bioforge.world.microscope;

import com.google.gson.*;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.infection.MicroscopeVisibility;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.*;

@EventBusSubscriber(modid = BioForge.MODID)
public class MicroscopeSymptomConfig extends SimpleJsonResourceReloadListener {

    public static final MicroscopeSymptomConfig INSTANCE = new MicroscopeSymptomConfig();
    private Map<Item, List<MicroscopeSymptomEntry>> itemEntries = new HashMap<>();
    private Map<Item, List<CalibrationSlider>> calibrationMap = new HashMap<>();
    private final Map<Item, List<MicroscopeSymptomEntry>> javaItemEntries = new LinkedHashMap<>();
    private final Map<Item, List<CalibrationSlider>> javaCalibrationMap = new LinkedHashMap<>();
    private boolean javaRegistrationsFrozen;

    private MicroscopeSymptomConfig() {
        super(new Gson(), "microscope");
    }

    public List<MicroscopeSymptomEntry> getEntriesFor(ItemStack stack) {
        return itemEntries.getOrDefault(stack.getItem(), Collections.emptyList()).stream()
                .filter(entry -> !"strain".equals(entry.source())
                        || BioForgeServerConfig.isSymptomEnabled(entry.symptomKey()))
                .toList();
    }

    public List<CalibrationSlider> getCalibrationFor(ItemStack stack) {
        return calibrationMap.getOrDefault(stack.getItem(), Collections.emptyList());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
        Map<Item, List<MicroscopeSymptomEntry>> entriesMap = new HashMap<>();
        Map<Item, List<CalibrationSlider>> calibMap = new HashMap<>();

        objects.forEach((id, element) -> {
            try {
                JsonObject root = element.getAsJsonObject();

                if (root.has("items")) {
                    JsonObject itemsObj = root.getAsJsonObject("items");
                    for (String itemId : itemsObj.keySet()) {
                        ResourceLocation itemKey = ResourceLocation.tryParse(itemId);
                        Item item = itemKey == null ? null
                                : BuiltInRegistries.ITEM.getOptional(itemKey).orElse(null);
                        if (item == null) {
                            BioForge.LOGGER.warn("Unknown item in microscope config: {}", itemId);
                            continue;
                        }
                        JsonElement itemElement = itemsObj.get(itemId);
                        List<MicroscopeSymptomEntry> entries = new ArrayList<>();
                        List<CalibrationSlider> sliders = new ArrayList<>();

                        if (itemElement.isJsonObject()) {
                            JsonObject itemObj = itemElement.getAsJsonObject();

                            if (itemObj.has("entries")) {
                                JsonArray arr = itemObj.getAsJsonArray("entries");
                                for (JsonElement e : arr) {
                                    entries.add(parseEntry(e.getAsJsonObject()));
                                }
                            }
                            if (itemObj.has("calibration")) {
                                JsonObject calibObj = itemObj.getAsJsonObject("calibration");
                                if (calibObj.has("sliders")) {
                                    JsonArray sliderArr = calibObj.getAsJsonArray("sliders");
                                    for (JsonElement e : sliderArr) {
                                        JsonObject s = e.getAsJsonObject();
                                        String nameKey = s.get("name").getAsString();
                                        float target = s.get("target").getAsFloat();
                                        float min = s.has("range_min") ? s.get("range_min").getAsFloat() : 0.0f;
                                        float max = s.has("range_max") ? s.get("range_max").getAsFloat() : 1.0f;
                                        boolean randomTarget = s.has("random_target") && s.get("random_target").getAsBoolean();
                                        sliders.add(new CalibrationSlider(nameKey, target, min, max, randomTarget));
                                    }
                                }
                            }
                        } else if (itemElement.isJsonArray()) {
                            JsonArray arr = itemElement.getAsJsonArray();
                            for (JsonElement e : arr) {
                                entries.add(parseEntry(e.getAsJsonObject()));
                            }
                        }

                        entriesMap.put(item, entries);
                        calibMap.put(item, sliders);
                    }
                }
                else if (root.has("entries")) {
                    List<MicroscopeSymptomEntry> entries = new ArrayList<>();
                    JsonArray arr = root.getAsJsonArray("entries");
                    for (JsonElement e : arr) {
                        entries.add(parseEntry(e.getAsJsonObject()));
                    }
                    for (Item item : BuiltInRegistries.ITEM) {
                        entriesMap.put(item, entries);
                        calibMap.put(item, List.of());
                    }
                }
            } catch (Exception e) {
                BioForge.LOGGER.error("Invalid microscope config {}: {}", id, e.getMessage());
            }
        });

        javaItemEntries.forEach(entriesMap::putIfAbsent);
        javaCalibrationMap.forEach(calibMap::putIfAbsent);

        this.itemEntries = entriesMap;
        this.calibrationMap = calibMap;
    }

    public synchronized void registerJava(Item item, List<MicroscopeSymptomEntry> entries,
                                          List<CalibrationSlider> calibration) {
        if (javaRegistrationsFrozen) throw new IllegalStateException("Microscope config registry is frozen");
        if (item == null) throw new IllegalArgumentException("Microscope item cannot be null");
        if (javaItemEntries.containsKey(item)) {
            throw new IllegalArgumentException("Duplicate Java microscope config for " + item);
        }
        javaItemEntries.put(item, List.copyOf(entries == null ? List.of() : entries));
        javaCalibrationMap.put(item, List.copyOf(calibration == null ? List.of() : calibration));
    }

    public synchronized void freezeJavaRegistrations() {
        javaRegistrationsFrozen = true;
    }

    private MicroscopeSymptomEntry parseEntry(JsonObject json) {
        String key = json.get("symptom").getAsString();
        String icon = json.get("icon").getAsString();
        String type = json.get("type").getAsString();
        MicroscopeVisibility minVis = parseVisibility(json);

        boolean isBool = type.equals("boolean");
        boolean isEnum = type.equals("enum");

        String source = json.has("source") ? json.get("source").getAsString() : "strain";
        String nbtKey = json.has("nbt_key") ? json.get("nbt_key").getAsString() : null;
        String condition = json.has("condition") ? json.get("condition").getAsString() : null;
        boolean displayPercentage = json.has("display_percentage") ? json.get("display_percentage").getAsBoolean() : true;

        if (isEnum) {
            Map<String, ResourceLocation> stateIcons = new LinkedHashMap<>();
            if (json.has("states")) {
                JsonObject states = json.getAsJsonObject("states");
                for (String stateName : states.keySet()) {
                    stateIcons.put(stateName, ResourceLocation.tryParse(states.get(stateName).getAsString()));
                }
            }
            return new MicroscopeSymptomEntry(key, ResourceLocation.tryParse(icon), stateIcons, minVis,
                    source, nbtKey, condition, displayPercentage);
        } else {
            return new MicroscopeSymptomEntry(key, ResourceLocation.tryParse(icon), isBool, minVis,
                    source, nbtKey, condition, displayPercentage);
        }
    }

    private MicroscopeVisibility parseVisibility(JsonObject json) {
        if (json.has("min_visibility")) {
            return MicroscopeVisibility.fromName(json.get("min_visibility").getAsString());
        }
        return MicroscopeVisibility.NONE;
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }
}
