package net.jenkimods.bioforge.infection.symptoms;

public final class SymptomKey<T> {
    private final String id;
    private final Class<T> type;
    private final T defaultValue;

    private SymptomKey(String id, Class<T> type, T defaultValue) {
        this.id = id;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    public static <T> SymptomKey<T> create(String id, Class<T> type, T defaultValue) {
        return new SymptomKey<>(id, type, defaultValue);
    }

    public String getId() { return id; }
    public Class<T> getType() { return type; }
    public T getDefaultValue() { return defaultValue; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SymptomKey<?> other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() { return "SymptomKey[" + id + "]"; }
}
