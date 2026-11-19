package org.example.resourcemod.core.registry;

import org.example.resourcemod.core.id.Id;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple registry with a fixed iteration order (registration order). Registries are plain instances, not
 * global singletons, so tests and addons can build their own.
 */
public final class Registry<T extends Registered> {
    private final String name;
    private final Map<Id, T> entries = new LinkedHashMap<>();

    public Registry(String name) {
        this.name = name;
    }

    public <E extends T> E register(E entry) {
        if (entries.putIfAbsent(entry.id(), entry) != null) {
            throw new IllegalArgumentException("Duplicate " + name + " id: " + entry.id());
        }
        return entry;
    }

    public T get(Id id) {
        return entries.get(id);
    }

    public boolean contains(Id id) {
        return entries.containsKey(id);
    }

    public T require(Id id) {
        T t = entries.get(id);
        if (t == null) {
            throw new IllegalArgumentException("Unknown " + name + ": " + id);
        }
        return t;
    }

    public List<T> values() {
        return Collections.unmodifiableList(new ArrayList<>(entries.values()));
    }

    public int size() {
        return entries.size();
    }

    public String name() {
        return name;
    }
}
