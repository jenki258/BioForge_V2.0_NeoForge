package net.jenkimods.bioforge.api.behavior;

import com.google.gson.JsonObject;

public interface MutationEffectHandler {
    default void validate(JsonObject parameters) {}
    void execute(MutationEffectContext context);
    default Object state(MutationEffectContext context) { return null; }
    default int defaultInterval() { return 20; }
}
