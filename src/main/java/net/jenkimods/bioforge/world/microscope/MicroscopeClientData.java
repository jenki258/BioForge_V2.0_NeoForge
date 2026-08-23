package net.jenkimods.bioforge.world.microscope;

import java.util.*;

public class MicroscopeClientData {
    private static Map<String, Object> symptoms = new LinkedHashMap<>();
    private static String visibility = "NONE";
    private static List<MicroscopeSymptomEntry> entries = new ArrayList<>();
    private static List<CalibrationSlider> calibrationSliders = new ArrayList<>();
    private static float[] sliderValues = new float[0];
    private static int assayResultPermille = -1;

    public static void set(Map<String, Object> sym, String vis,
                           List<MicroscopeSymptomEntry> ent,
                           List<CalibrationSlider> calib,
                           float[] syncedValues,
                           int assayResult) {
        float[] previousValues = calibrationSliders.equals(calib)
                ? sliderValues : null;
        symptoms = sym;
        visibility = vis;
        entries = ent;
        calibrationSliders = calib;
        assayResultPermille = assayResult;
        if (syncedValues != null && syncedValues.length == calib.size()) {
            sliderValues = new float[calib.size()];
            for (int i = 0; i < calib.size(); i++) {
                CalibrationSlider slider = calib.get(i);
                float value = syncedValues[i];
                if (!Float.isFinite(value)) {
                    value = (slider.rangeMin() + slider.rangeMax()) / 2.0F;
                }
                sliderValues[i] = Math.max(slider.rangeMin(),
                        Math.min(slider.rangeMax(), value));
            }
        } else if (previousValues != null
                && previousValues.length == calib.size()) {
            sliderValues = previousValues;
        } else {
            sliderValues = new float[calib.size()];
            for (int i = 0; i < calib.size(); i++) {
                CalibrationSlider slider = calib.get(i);
                sliderValues[i] = (slider.rangeMin() + slider.rangeMax()) / 2f;
            }
        }
    }

    public static Map<String, Object> getSymptoms() { return symptoms; }
    public static String getVisibility() { return visibility; }
    public static List<MicroscopeSymptomEntry> getEntries() { return entries; }
    public static List<CalibrationSlider> getCalibrationSliders() { return calibrationSliders; }
    public static float[] getSliderValues() { return sliderValues; }
    public static int getAssayResultPermille() { return assayResultPermille; }

    public static void setSliderValue(int index, float value) {
        if (index >= 0 && index < sliderValues.length) {
            sliderValues[index] = Math.max(calibrationSliders.get(index).rangeMin(),
                    Math.min(calibrationSliders.get(index).rangeMax(), value));
        }
    }

    public static boolean isCalibrated() {
        if (calibrationSliders.isEmpty()) return true;
        for (int i = 0; i < sliderValues.length; i++) {
            if (!calibrationSliders.get(i).isWithinTolerance(sliderValues[i])) {
                return false;
            }
        }
        return true;
    }

    public static void clear() {
        symptoms.clear();
        visibility = "NONE";
        entries.clear();
        calibrationSliders.clear();
        sliderValues = new float[0];
        assayResultPermille = -1;
    }
}
