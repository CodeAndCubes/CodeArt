package com.mrleonardos.codeart.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ArtRegistry {

    private final Map<String, ArtDefinition> byName = new LinkedHashMap<>();
    private final Map<String, List<ArtDefinition>> byHash = new HashMap<>();

    public synchronized ArtDefinition get(String name) {
        return name == null ? null : byName.get(name);
    }

    public synchronized boolean containsHash(String sha256) {
        return sha256 != null && byHash.containsKey(sha256);
    }

    public synchronized ArtDefinition anyByHash(String sha256) {
        List<ArtDefinition> definitions = sha256 == null ? null : byHash.get(sha256);
        return definitions == null || definitions.isEmpty() ? null : definitions.get(0);
    }

    public synchronized ArtDefinition put(ArtDefinition definition) {
        ArtDefinition previous = byName.put(definition.name(), definition);
        if (previous != null) {
            unlink(previous);
        }
        link(definition);
        return previous;
    }

    public synchronized ArtDefinition remove(String name) {
        ArtDefinition removed = byName.remove(name);
        if (removed != null) {
            unlink(removed);
        }
        return removed;
    }

    public synchronized void replaceAll(Collection<ArtDefinition> definitions) {
        byName.clear();
        byHash.clear();
        for (ArtDefinition definition : definitions) {
            byName.put(definition.name(), definition);
            link(definition);
        }
    }

    public synchronized void addAll(Collection<ArtDefinition> definitions) {
        for (ArtDefinition definition : definitions) {
            put(definition);
        }
    }

    public synchronized void clear() {
        byName.clear();
        byHash.clear();
    }

    public synchronized List<ArtDefinition> all() {
        return Collections.unmodifiableList(new ArrayList<>(byName.values()));
    }

    public synchronized int size() {
        return byName.size();
    }

    private void link(ArtDefinition definition) {
        List<ArtDefinition> definitions = byHash.get(definition.sha256());
        if (definitions == null) {
            definitions = new ArrayList<>(1);
            byHash.put(definition.sha256(), definitions);
        }
        definitions.add(definition);
    }

    private void unlink(ArtDefinition definition) {
        List<ArtDefinition> definitions = byHash.get(definition.sha256());
        if (definitions == null) {
            return;
        }
        definitions.remove(definition);
        if (definitions.isEmpty()) {
            byHash.remove(definition.sha256());
        }
    }
}
