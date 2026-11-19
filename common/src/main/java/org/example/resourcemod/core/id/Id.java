package org.example.resourcemod.core.id;

import java.util.Objects;

/**
 * Namespaced identifier ("namespace:path"). Without a namespace the mod's own namespace applies.
 * Namespaces contain only a-z, 0-9, '_' and '-'; paths additionally allow '/'. Dots are never allowed
 * (a dot would make translation keys ambiguous).
 */
public record Id(String namespace, String path) implements Comparable<Id> {
    public static final String DEFAULT_NAMESPACE = ModIds.MOD_ID;

    public Id {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!isValidNamespace(namespace)) {
            throw new IllegalArgumentException("Invalid namespace '" + namespace + "' (allowed: a-z 0-9 _ -)");
        }
        if (!isValidPath(path)) {
            throw new IllegalArgumentException("Invalid path '" + path + "' (allowed: a-z 0-9 _ - /)");
        }
    }

    /** Id in the mod's own namespace. */
    public static Id of(String path) {
        return new Id(DEFAULT_NAMESPACE, path);
    }

    /** Parses "namespace:path" or just "path" (own namespace). Throws IllegalArgumentException on invalid input. */
    public static Id parse(String text) {
        Objects.requireNonNull(text, "text");
        int colon = text.indexOf(':');
        if (colon < 0) {
            return of(text);
        }
        return new Id(text.substring(0, colon), text.substring(colon + 1));
    }

    public static boolean isValidNamespace(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidPath(String s) {
        if (s.isEmpty() || s.startsWith("/") || s.endsWith("/") || s.contains("//")) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '/')) {
                return false;
            }
        }
        return true;
    }

    public boolean isDefaultNamespace() {
        return DEFAULT_NAMESPACE.equals(namespace);
    }

    /** Form used in files: ids of the mod itself are written without namespace, so renaming the mod id does not break files. */
    public String toFileString() {
        return isDefaultNamespace() ? path : toString();
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }

    @Override
    public int compareTo(Id o) {
        int c = namespace.compareTo(o.namespace);
        return c != 0 ? c : path.compareTo(o.path);
    }
}
